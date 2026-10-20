package uz.sadora.doctor.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
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
    /** The file [bytes] are read from, when they are one: what a restart opens again. */
    val path: String? = null,
)

/** Why a picked file could not be made ready on the phone. */
enum class PrepareProblem { TooLong, TooLarge, Unreadable }

class MediaPrepareException(val problem: PrepareProblem) : Exception(problem.name)

/** How a pick is found again after the app was closed: its kind, where its file is, its length. */
data class PickRef(val kind: MediaKind, val source: String, val durationMs: Long?)

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

    /**
     * Makes the pick findable after a restart — a file of the app's own, or a lasting grant
     * on the system's — and answers how; null when it cannot be, and a restart before it is
     * prepared then loses it.
     */
    suspend fun keep(): PickRef? = null

    /** Deletes whatever the pick copied or produced. */
    fun discard()
}

/**
 * Where unsent albums wait while the app is closed: a small list and the files it names,
 * in the app's own storage — never the gallery, never a backup the platform makes of
 * documents. Built by the platform; without one the albums live in memory only.
 */
interface OutgoingFiles {
    suspend fun readManifest(): String?

    /** Replaces the list; null removes it. */
    suspend fun writeManifest(text: String?)

    /** Writes [bytes] to a new file of the app's own; answers its path. */
    suspend fun write(bytes: ByteArray): String

    /** The file at [path], read a piece at a time and deleted on release; null when it is gone. */
    fun open(path: String): MediaBytes?

    fun delete(path: String)

    /** The pick behind [ref] again, or null when its file is gone. */
    fun restore(ref: PickRef): PickedMedia?

    /**
     * Deletes every file of the store but the list and those [keep] names — by path, or by
     * a pick's source — such as a video the app was compressing when it was closed.
     */
    suspend fun sweep(keep: Set<String>)
}

sealed interface OutgoingState {
    /** Being prepared or sent; [OutgoingAlbum.progress] says how far. */
    data object Working : OutgoingState

    /**
     * Stopped: by the server or the network ([failure]), or on the phone ([problem]). Both
     * null: the app was closed while it went, and it came back from the list on disk.
     */
    data class Failed(val failure: ApiFailure? = null, val problem: PrepareProblem? = null) : OutgoingState
}

/** One of an album's files and how far it has got. */
class OutgoingItem internal constructor(picked: PickedMedia) {
    var picked: PickedMedia = picked
        internal set

    /** What the bubble shows until the server's thumbnail exists. */
    var preview by mutableStateOf<ByteArray?>(null)
        internal set
    internal var ref: PickRef? = null
    internal var prepared: PreparedMedia? = null
    internal var posterPath: String? = null
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
    internal val createdAtMillis: Long,
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
 * the album still goes — but not the app. With [files], what a closed app was sending
 * is written down as it goes — what she picked, each prepared file, each upload — and
 * [restore] brings it back as an unsent bubble; [retry] then asks the server how much of
 * each file it already has and carries on from there. [cancel] takes an album's unsent
 * uploads off the server, and [discardAll] drops everything when she signs out.
 */
class MediaUploader(
    private val transport: MediaTransport,
    private val scope: CoroutineScope,
    private val files: OutgoingFiles? = null,
    private val clock: Clock = Clock.System,
    /** Sends the message itself once every file is up; the controller appends what comes back. */
    private val sendMessage: suspend (album: OutgoingAlbum, mediaIds: List<String>) -> ApiResult<Unit>,
) {
    private val albums = mutableStateListOf<OutgoingAlbum>()
    private val jobs = mutableMapOf<String, Job>()
    private val saving = Mutex()
    private var next = 0
    private var owner: String? = null
    private var restored = false

    val outgoing: List<OutgoingAlbum> get() = albums

    fun outgoingFor(conversationId: String): List<OutgoingAlbum> = albums.filter { it.conversationId == conversationId }

    fun send(conversationId: String, caption: String, picked: List<PickedMedia>): OutgoingAlbum {
        val now = clock.now().toEpochMilliseconds()
        val album = OutgoingAlbum("local-$now-${++next}", conversationId, caption.trim(), picked.map(::OutgoingItem), now)
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
                release(album)
                persist()
            }
        }
    }

    /**
     * Brings back what was being sent when the app closed, for [owner] — the account now
     * signed in. Another account's albums, or any older than the server keeps an unsent
     * upload, are deleted instead. Once per uploader.
     */
    suspend fun restore(owner: String?) {
        this.owner = owner
        val files = files ?: return
        if (restored) return
        restored = true
        val text = files.readManifest()
        val saved = text?.let { runCatching { Manifest.parse(it) }.getOrNull() }
        if (saved == null) {
            if (text != null) files.writeManifest(null)
            files.sweep(emptySet())
            return
        }
        val now = clock.now().toEpochMilliseconds()
        val (kept, dropped) = if (saved.owner != owner) {
            emptyList<SavedAlbum>() to saved.albums
        } else {
            saved.albums.partition { now - it.createdAtMillis < UNSENT_TTL.inWholeMilliseconds }
        }
        dropped.forEach { forget(it, files) }
        kept.forEach { albums += revive(it, files) }
        persist()
        // Nothing of this process is sending yet, so whatever the kept albums do not name is a leftover.
        files.sweep(
            kept.flatMap { album ->
                album.items.flatMap { item -> listOfNotNull(item.prepared?.path, item.prepared?.posterPath, item.ref?.source) }
            }.toSet(),
        )
    }

    /**
     * She signed out: everything waiting goes, from the server where it can and from the
     * phone, and the next account's [restore] reads the list afresh.
     */
    fun discardAll() {
        val all = albums.toList()
        albums.clear()
        restored = false
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        scope.launch {
            withContext(NonCancellable) {
                all.forEach { album ->
                    album.items.mapNotNull { it.uploadId }.forEach { runCatching { transport.cancel(it) } }
                    release(album)
                }
                persist()
            }
        }
    }

    private fun launch(album: OutgoingAlbum) {
        album.state = OutgoingState.Working
        jobs[album.localId] = scope.launch { run(album) }
    }

    private suspend fun run(album: OutgoingAlbum) {
        try {
            // Written down before anything slow starts: a close during the compressing finds it.
            album.items.forEach { item ->
                if (item.ref == null && item.prepared == null) item.ref = runCatching { item.picked.keep() }.getOrNull()
            }
            persist()
            album.items.forEach { item ->
                if (item.media != null) return@forEach
                val prepared = item.prepared ?: durable(
                    item,
                    item.picked.prepare { fraction ->
                        item.fraction = fraction * prepareShare(item.picked.kind)
                        album.progress = album.items.map { it.fraction }.average().toFloat()
                    },
                ).also {
                    item.prepared = it
                    persist()
                }
                item.media = upload(album, item, prepared)
                persist()
            }
            when (val sent = sendMessage(album, album.items.map { it.media!!.id })) {
                is ApiResult.Success -> {
                    albums.remove(album)
                    jobs.remove(album.localId)
                    release(album)
                    persist()
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
            persist()
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

    /**
     * The prepared file as a file of the app's own, so a restart can send the very bytes an
     * upload already holds the start of. A photo comes out of preparing in memory and is
     * written down; a video already is a file.
     */
    private suspend fun durable(item: OutgoingItem, prepared: PreparedMedia): PreparedMedia {
        val files = files ?: return prepared
        val path = prepared.path ?: files.write(prepared.bytes.read(0, prepared.bytes.size.toInt())).also { prepared.bytes.release() }
        item.posterPath = prepared.poster?.let { files.write(it) }
        val bytes = files.open(path) ?: return prepared
        return PreparedMedia(prepared.kind, prepared.mimeType, bytes, prepared.width, prepared.height, prepared.durationMs, prepared.poster, path)
    }

    private fun release(album: OutgoingAlbum) {
        album.items.forEach { item ->
            item.prepared?.bytes?.release()
            item.posterPath?.let { files?.delete(it) }
            item.picked.discard()
        }
    }

    // ---------------------------------------------------------------- what a restart finds

    /** Writes the list down as it is now, or removes it when nothing is waiting. */
    private suspend fun persist() {
        val files = files ?: return
        saving.withLock {
            val waiting = albums.toList()
            files.writeManifest(if (waiting.isEmpty()) null else Manifest.write(owner, waiting))
        }
    }

    private suspend fun revive(saved: SavedAlbum, files: OutgoingFiles): OutgoingAlbum {
        val items = saved.items.map { record ->
            val prepared = record.prepared?.let { p -> files.open(p.path)?.let { bytes -> p.with(bytes, files) } }
            val origin = record.ref?.let { files.restore(it) }
            OutgoingItem(origin ?: StandIn(record.kind, record.ref?.durationMs, prepared)).apply {
                ref = record.ref
                this.prepared = prepared
                posterPath = record.prepared?.posterPath
                uploadId = record.uploadId
                media = record.media
            }
        }
        val album = OutgoingAlbum(saved.localId, saved.conversationId, saved.caption, items, saved.createdAtMillis)
        // Something that can be neither found nor sent again: she can only delete it.
        val lost = items.any { it.media == null && it.prepared == null && it.picked is StandIn }
        album.state = OutgoingState.Failed(problem = if (lost) PrepareProblem.Unreadable else null)
        scope.launch {
            items.forEach { item ->
                item.preview = runCatching { item.picked.preview() }.getOrNull()
                    ?: item.prepared?.poster
                    ?: item.prepared?.takeIf { it.kind == MediaKind.PHOTO }?.let { runCatching { it.bytes.read(0, it.bytes.size.toInt()) }.getOrNull() }
            }
        }
        return album
    }

    /** An album she will never see again: its files go, and so do the picks it kept. */
    private fun forget(saved: SavedAlbum, files: OutgoingFiles) {
        saved.items.forEach { record ->
            record.prepared?.let { p ->
                files.delete(p.path)
                p.posterPath?.let(files::delete)
            }
            record.ref?.let { files.restore(it)?.discard() }
        }
    }

    /** What stands for a pick whose file is gone, when its prepared file is all there is. */
    private class StandIn(override val kind: MediaKind, override val durationMs: Long?, private val prepared: PreparedMedia?) : PickedMedia {
        override suspend fun preview(): ByteArray? = null
        override suspend fun prepare(onProgress: (Float) -> Unit): PreparedMedia =
            prepared ?: throw MediaPrepareException(PrepareProblem.Unreadable)
        override fun discard() {}
    }

    /** How much of a file's bar its preparing takes: compressing a video is most of the wait. */
    private fun prepareShare(kind: MediaKind): Float = if (kind == MediaKind.VIDEO) 0.4f else 0.1f

    private class UploadRefused(val failure: ApiFailure) : Exception(failure.message)

    private fun <T> ApiResult<T>.orThrow(): T = when (this) {
        is ApiResult.Success -> value
        is ApiResult.Failure -> throw UploadRefused(failure)
    }

    private companion object {
        /** The server deletes an upload nobody sent after a day; a restart past that has nothing to resume. */
        val UNSENT_TTL = 24.hours
    }
}

/** One unsent album as the list keeps it. */
private class SavedAlbum(
    val localId: String,
    val conversationId: String,
    val caption: String,
    val createdAtMillis: Long,
    val items: List<SavedItem>,
)

private class SavedItem(
    val kind: MediaKind,
    val ref: PickRef?,
    val prepared: SavedPrepared?,
    val uploadId: String?,
    val media: MessageMedia?,
)

private class SavedPrepared(
    val kind: MediaKind,
    val mimeType: String,
    val path: String,
    val width: Int,
    val height: Int,
    val durationMs: Long?,
    val posterPath: String?,
) {
    /** The prepared file again, over [bytes]; its poster is read back whole — a few kilobytes. */
    suspend fun with(bytes: MediaBytes, files: OutgoingFiles): PreparedMedia {
        val poster = posterPath?.let { files.open(it) }?.let { it.read(0, it.size.toInt()) }
        return PreparedMedia(kind, mimeType, bytes, width, height, durationMs, poster, path)
    }
}

/** The list of unsent albums as JSON, read and written by hand: the app has no serialization plugin. */
private object Manifest {
    private val json = Json { ignoreUnknownKeys = true }

    class Saved(val owner: String?, val albums: List<SavedAlbum>)

    fun parse(text: String): Saved {
        val root = json.parseToJsonElement(text).jsonObject
        val albums = root["albums"]?.jsonArray.orEmpty().map { element ->
            val a = element.jsonObject
            SavedAlbum(
                localId = a.str("id").orEmpty(),
                conversationId = a.str("conversation").orEmpty(),
                caption = a.str("caption").orEmpty(),
                createdAtMillis = a["createdAt"]?.jsonPrimitive?.longOrNull ?: 0,
                items = a["items"]?.jsonArray.orEmpty().map { item(it.jsonObject) },
            )
        }
        return Saved(root.str("owner"), albums)
    }

    fun write(owner: String?, albums: List<OutgoingAlbum>): String = buildJsonObject {
        owner?.let { put("owner", it) }
        put(
            "albums",
            buildJsonArray {
                albums.forEach { album ->
                    add(
                        buildJsonObject {
                            put("id", album.localId)
                            put("conversation", album.conversationId)
                            put("caption", album.caption)
                            put("createdAt", album.createdAtMillis)
                            put("items", buildJsonArray { album.items.forEach { add(item(it)) } })
                        },
                    )
                }
            },
        )
    }.toString()

    private fun item(item: OutgoingItem): JsonObject = buildJsonObject {
        put("kind", item.picked.kind.name)
        item.ref?.let { ref ->
            put("source", ref.source)
            ref.durationMs?.let { put("duration", it) }
        }
        // Only a prepared file a restart can open is worth naming; one in memory is prepared again.
        val prepared = item.prepared
        val path = prepared?.path
        if (prepared != null && path != null) {
            put(
                "prepared",
                buildJsonObject {
                    put("mime", prepared.mimeType)
                    put("path", path)
                    put("width", prepared.width)
                    put("height", prepared.height)
                    prepared.durationMs?.let { put("duration", it) }
                    item.posterPath?.let { put("poster", it) }
                },
            )
        }
        item.uploadId?.let { put("upload", it) }
        item.media?.let { put("media", json.encodeToJsonElement(MessageMedia.serializer(), it)) }
    }

    private fun item(o: JsonObject): SavedItem {
        val kind = runCatching { MediaKind.valueOf(o.str("kind").orEmpty()) }.getOrDefault(MediaKind.PHOTO)
        val ref = o.str("source")?.let { PickRef(kind, it, o["duration"]?.jsonPrimitive?.longOrNull) }
        val prepared = (o["prepared"] as? JsonObject)?.let { p ->
            val path = p.str("path") ?: return@let null
            SavedPrepared(
                kind = kind,
                mimeType = p.str("mime").orEmpty(),
                path = path,
                width = p["width"]?.jsonPrimitive?.intOrNull ?: 0,
                height = p["height"]?.jsonPrimitive?.intOrNull ?: 0,
                durationMs = p["duration"]?.jsonPrimitive?.longOrNull,
                posterPath = p.str("poster"),
            )
        }
        val media = o["media"]?.let { runCatching { json.decodeFromJsonElement(MessageMedia.serializer(), it) }.getOrNull() }
        return SavedItem(kind, ref, prepared, o.str("upload"), media)
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
