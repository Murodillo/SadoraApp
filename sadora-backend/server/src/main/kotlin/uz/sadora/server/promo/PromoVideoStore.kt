package uz.sadora.server.promo

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uz.sadora.server.core.ConflictException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.now

/** `current.json`, as the page at sadora.app/video reads it. */
@Serializable
data class PromoVideoManifest(
    val file: String,
    val mimeType: String,
    val sizeBytes: Long,
    val updatedAt: Instant,
)

/**
 * The promo video, on disk.
 *
 * `<media>/video` holds one video and `current.json` naming it. The landing page's nginx
 * serves that directory as `/video/media/`, so the page plays the file without this
 * process in the way; the manifest is the whole state, and a directory copied to another
 * server brings its video with it.
 *
 * A video is tens of megabytes and the gateway in front of the API was never asked how
 * large a request it lets through, so an upload arrives in pieces of [CHUNK_BYTES] and
 * only becomes the video when the last one is in. One upload at a time: starting another
 * drops the unfinished one.
 */
class PromoVideoStore(mediaDir: File, private val clock: () -> Instant = ::now) {

    private val dir = File(mediaDir, "video")
    private val manifestFile = File(dir, MANIFEST)
    private val lock = Mutex()
    private var upload: Upload? = null

    private class Upload(val id: String, val part: File, val kind: Kind, val sizeBytes: Long, var received: Long = 0)

    private enum class Kind(val extension: String, val mimeType: String) {
        MP4("mp4", "video/mp4"),
        WEBM("webm", "video/webm"),
    }

    suspend fun current(): PromoVideoManifest? = io { readManifest() }

    /** The video itself, for the API's own copy of the link. */
    suspend fun currentFile(): Pair<File, PromoVideoManifest>? = io {
        readManifest()?.let { File(dir, it.file) to it }
    }

    suspend fun start(fileName: String, sizeBytes: Long): String = io {
        val kind = Kind.entries.firstOrNull { fileName.substringAfterLast('.', "").equals(it.extension, ignoreCase = true) }
            ?: throw ValidationException("file", "Faqat MP4 yoki WebM video")
        if (sizeBytes <= 0) throw ValidationException("file", "Video bo'sh")
        if (sizeBytes > MAX_BYTES) throw ValidationException("file", "Video juda katta — eng ko'pi ${MAX_BYTES / MEGABYTE} MB")
        dir.mkdirs()
        dropParts()
        val id = Uuid.random().toString()
        upload = Upload(id, File(dir, "$id$PART"), kind, sizeBytes)
        id
    }

    /**
     * Writes one piece at [offset]. A piece sent twice — its answer was lost on the way
     * back — lands where it did the first time; a piece from further on than what has
     * arrived is refused, because the file would have a hole in it.
     */
    suspend fun append(id: String, offset: Long, bytes: ByteArray): Long = io {
        val upload = uploadOf(id)
        if (offset < 0 || offset > upload.received) throw ConflictException("Yuklash tartibi buzildi — qaytadan boshlang")
        if (bytes.isEmpty() || offset + bytes.size > upload.sizeBytes) throw ConflictException("Yuklash tartibi buzildi — qaytadan boshlang")
        RandomAccessFile(upload.part, "rw").use { file ->
            file.seek(offset)
            file.write(bytes)
            file.setLength(offset + bytes.size)
        }
        upload.received = offset + bytes.size
        upload.received
    }

    suspend fun complete(id: String): PromoVideoManifest = io {
        val upload = uploadOf(id)
        if (upload.received != upload.sizeBytes) throw ConflictException("Video to'liq yuklanmadi — qaytadan urinib ko'ring")
        if (!upload.part.looksLike(upload.kind)) {
            dropParts()
            throw ValidationException("file", "Bu fayl MP4 yoki WebM video emas")
        }
        val at = clock()
        // A new name every time: the page and every cache in front of it see a new URL.
        val manifest = PromoVideoManifest("sadora-${at.toEpochMilliseconds()}.${upload.kind.extension}", upload.kind.mimeType, upload.sizeBytes, at)
        Files.move(upload.part.toPath(), File(dir, manifest.file).toPath(), StandardCopyOption.ATOMIC_MOVE)
        this.upload = null
        writeManifest(manifest)
        dir.listFiles().orEmpty()
            .filter { it.isFile && it.name != manifest.file && it.name != MANIFEST }
            .forEach { it.delete() }
        manifest
    }

    /** Takes the video down. Answers whether there was one. */
    suspend fun remove(): Boolean = io {
        val had = readManifest() != null
        upload = null
        // The manifest first: a page that loads in between says "no video", not a dead link.
        manifestFile.delete()
        dir.listFiles().orEmpty().filter { it.isFile }.forEach { it.delete() }
        had
    }

    private fun uploadOf(id: String): Upload =
        upload?.takeIf { it.id == id } ?: throw NotFoundException("Yuklash topilmadi — qaytadan boshlang")

    private fun dropParts() {
        upload = null
        dir.listFiles().orEmpty().filter { it.name.endsWith(PART) }.forEach { it.delete() }
    }

    private fun readManifest(): PromoVideoManifest? {
        if (!manifestFile.isFile) return null
        val manifest = runCatching { json.decodeFromString<PromoVideoManifest>(manifestFile.readText()) }.getOrNull() ?: return null
        // The name is joined to a path here and to a URL on the page; only ours is believed.
        if (!FILE_NAME.matches(manifest.file) || !File(dir, manifest.file).isFile) return null
        return manifest
    }

    private fun writeManifest(manifest: PromoVideoManifest) {
        val next = File(dir, "$MANIFEST.next")
        next.writeText(json.encodeToString(manifest))
        Files.move(next.toPath(), manifestFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    /** The container's own first bytes: a renamed file of another kind is not a video. */
    private fun File.looksLike(kind: Kind): Boolean {
        val head = ByteArray(12)
        val read = inputStream().use { it.readNBytes(head, 0, head.size) }
        if (read < head.size) return false
        return when (kind) {
            Kind.MP4 -> String(head, 4, 4, Charsets.US_ASCII) == "ftyp"
            Kind.WEBM -> head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() && head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte()
        }
    }

    private suspend fun <T> io(block: () -> T): T = lock.withLock { withContext(Dispatchers.IO) { block() } }

    companion object {
        const val MEGABYTE = 1024L * 1024
        const val MAX_BYTES = 300 * MEGABYTE
        /** One megabyte: what nginx lets through when nobody configured it otherwise. */
        const val CHUNK_BYTES = 1024 * 1024
        private const val MANIFEST = "current.json"
        private const val PART = ".part"
        private val FILE_NAME = Regex("""sadora-\d+\.(mp4|webm)""")
        private val json = Json { ignoreUnknownKeys = true }
    }
}
