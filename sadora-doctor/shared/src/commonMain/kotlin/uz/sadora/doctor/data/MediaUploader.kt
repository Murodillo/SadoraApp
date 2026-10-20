package uz.sadora.doctor.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.sadora.contract.Ack
import uz.sadora.contract.MediaKind
import uz.sadora.contract.MediaUpload
import uz.sadora.contract.MessageMedia
import uz.sadora.contract.StartMediaUploadRequest

/** What the uploader needs from the server: [MediaApi], or a fake in tests. */
interface MediaTransport {
    suspend fun start(request: StartMediaUploadRequest): ApiResult<MediaUpload>
    suspend fun progress(uploadId: String): ApiResult<MediaUpload>
    suspend fun put(uploadId: String, offset: Long, bytes: ByteArray): ApiResult<MediaUpload>
    suspend fun complete(uploadId: String, posterBase64: String?): ApiResult<MessageMedia>
    suspend fun cancel(uploadId: String): ApiResult<Ack>
}

/** A prepared file's bytes, read a piece at a time: a video is never held in memory whole. */
interface MediaBytes {
    val size: Long
    suspend fun read(offset: Long, length: Int): ByteArray

    /** Deletes the temporary file behind it, if there is one. */
    fun release() {}

    companion object {
        fun of(bytes: ByteArray): MediaBytes = object : MediaBytes {
            override val size = bytes.size.toLong()
            override suspend fun read(offset: Long, length: Int): ByteArray =
                bytes.copyOfRange(offset.toInt(), minOf(bytes.size.toLong(), offset + length).toInt())
        }
    }
}

/** A photo resized, or a video compressed, ready to go up as it is. */
class PreparedMedia(
    val kind: MediaKind,
    val mimeType: String,
    val bytes: MediaBytes,
    val width: Int,
    val height: Int,
    val durationMs: Long?,
    /** A video's poster frame, a JPEG; sent with the upload's last call. */
    val poster: ByteArray?,
)

/** Why a picked file could not be made ready on the phone. */
enum class PrepareProblem { TooLong, TooLarge, Unreadable }

class MediaPrepareException(val problem: PrepareProblem) : Exception(problem.name)

/**
 * A photo or video she picked, prepared only when its turn comes: compressing a video is
 * the slow part, and it happens behind a bubble she can already see, not before it.
 */
interface PickedMedia {
    val kind: MediaKind
    /** A video's length, read without compressing it; null for a photo. */
    val durationMs: Long?

    /** A small JPEG to show before anything is sent; null when none can be made. */
    suspend fun preview(): ByteArray?

    /** Resizes or compresses; throws [MediaPrepareException] when it cannot. */
    suspend fun prepare(onProgress: (Float) -> Unit): PreparedMedia

    /** Deletes whatever the pick copied or produced. */
    fun discard()
}

sealed interface OutgoingState {
    /** Being prepared or sent; [OutgoingAlbum.progress] says how far. */
    data object Working : OutgoingState

    /** Stopped: by the server or the network ([failure]), or on the phone ([problem]). */
    data class Failed(val failure: ApiFailure? = null, val problem: PrepareProblem? = null) : OutgoingState
}

/** One of an album's files and how far it has got. */
class OutgoingItem internal constructor(val picked: PickedMedia) {
    /** What the bubble shows until the server's thumbnail exists. */
    var preview by mutableStateOf<ByteArray?>(null)
        internal set
    internal var prepared: PreparedMedia? = null
    internal var uploadId: String? = null
    internal var media: MessageMedia? = null
    internal var fraction = 0f
}

/** An album on its way into a thread: drawn as her bubble at once, with its progress. */
class OutgoingAlbum internal constructor(
    val localId: String,
    val conversationId: String,
    val caption: String,
    val items: List<OutgoingItem>,
) {
    var state by mutableStateOf<OutgoingState>(OutgoingState.Working)
        internal set
    var progress by mutableFloatStateOf(0f)
        internal set
}

/**
 * Sends photos and videos into a thread: each file is prepared, sent in the server's
 * pieces and finished, and only then does the message that names them go.
 *
 * It runs in a scope that outlives the thread's screen — she may go back to the list and
 * the album still goes — but not the app: a closed app leaves the album failed, and the
 * next try asks the server how much of each file it already has. A failure stops the
 * album where it is; [retry] carries on from there, [cancel] takes its unsent uploads
 * off the server.
 */
class MediaUploader(
    private val transport: MediaTransport,
    private val scope: CoroutineScope,
    /** Sends the message itself once every file is up; the controller appends what comes back. */
    private val sendMessage: suspend (album: OutgoingAlbum, mediaIds: List<String>) -> ApiResult<Unit>,
) {
    private val albums = mutableStateListOf<OutgoingAlbum>()
    private val jobs = mutableMapOf<String, Job>()
    private var next = 0

    val outgoing: List<OutgoingAlbum> get() = albums

    fun outgoingFor(conversationId: String): List<OutgoingAlbum> = albums.filter { it.conversationId == conversationId }

    fun send(conversationId: String, caption: String, picked: List<PickedMedia>): OutgoingAlbum {
        val album = OutgoingAlbum("local-${++next}", conversationId, caption.trim(), picked.map(::OutgoingItem))
        albums += album
        scope.launch { album.items.forEach { item -> item.preview = runCatching { item.picked.preview() }.getOrNull() } }
        launch(album)
        return album
    }

    fun retry(localId: String) {
        val album = albums.firstOrNull { it.localId == localId } ?: return
        if (jobs[localId]?.isActive == true) return
        launch(album)
    }

    fun cancel(localId: String) {
        val album = albums.firstOrNull { it.localId == localId } ?: return
        jobs.remove(localId)?.cancel()
        albums.remove(album)
        // Not hers to wait for: the uploads come off the server whether or not she stays.
        scope.launch {
            withContext(NonCancellable) {
                album.items.mapNotNull { it.uploadId }.forEach { transport.cancel(it) }
            }
            release(album)
        }
    }

    private fun launch(album: OutgoingAlbum) {
        album.state = OutgoingState.Working
        jobs[album.localId] = scope.launch { run(album) }
    }

    private suspend fun run(album: OutgoingAlbum) {
        try {
            album.items.forEach { item ->
                if (item.media != null) return@forEach
                val prepared = item.prepared ?: item.picked.prepare { fraction ->
                    item.fraction = fraction * prepareShare(item.picked.kind)
                    album.progress = album.items.map { it.fraction }.average().toFloat()
                }.also { item.prepared = it }
                item.media = upload(album, item, prepared)
            }
            when (val sent = sendMessage(album, album.items.map { it.media!!.id })) {
                is ApiResult.Success -> {
                    albums.remove(album)
                    jobs.remove(album.localId)
                    release(album)
                }
                is ApiResult.Failure -> album.state = OutgoingState.Failed(sent.failure)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (refused: UploadRefused) {
            album.state = OutgoingState.Failed(refused.failure)
        } catch (problem: MediaPrepareException) {
            album.state = OutgoingState.Failed(problem = problem.problem)
        } catch (unexpected: Throwable) {
            album.state = OutgoingState.Failed(ApiFailure.Network(unexpected.message ?: "upload failed"))
        }
    }

    /**
     * One file up. An upload that already started is asked where it stands and carried on
     * from there; one the server no longer knows starts again from nothing.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun upload(album: OutgoingAlbum, item: OutgoingItem, prepared: PreparedMedia): MessageMedia {
        val size = prepared.bytes.size
        var current: MediaUpload? = item.uploadId?.let { id ->
            when (val known = transport.progress(id)) {
                is ApiResult.Success -> known.value
                is ApiResult.Failure -> if (known.failure is ApiFailure.Network) throw UploadRefused(known.failure) else null
            }
        }
        if (current == null) {
            current = transport.start(StartMediaUploadRequest(prepared.kind, prepared.mimeType, size)).orThrow()
            item.uploadId = current.id
        }
        val share = prepareShare(prepared.kind)
        var offset = current.receivedBytes
        while (offset < size) {
            val bytes = prepared.bytes.read(offset, minOf(current.chunkBytes.toLong(), size - offset).toInt())
            offset = transport.put(current.id, offset, bytes).orThrow().receivedBytes
            item.fraction = share + (1 - share) * (offset.toFloat() / size)
            album.progress = album.items.map { it.fraction }.average().toFloat()
        }
        return transport.complete(current.id, prepared.poster?.let { Base64.encode(it) }).orThrow()
    }

    private fun release(album: OutgoingAlbum) {
        album.items.forEach { item ->
            item.prepared?.bytes?.release()
            item.picked.discard()
        }
    }

    /** How much of a file's bar its preparing takes: compressing a video is most of the wait. */
    private fun prepareShare(kind: MediaKind): Float = if (kind == MediaKind.VIDEO) 0.4f else 0.1f

    private class UploadRefused(val failure: ApiFailure) : Exception(failure.message)

    private fun <T> ApiResult<T>.orThrow(): T = when (this) {
        is ApiResult.Success -> value
        is ApiResult.Failure -> throw UploadRefused(failure)
    }
}
