package uz.sadora.server.media

import java.io.File
import java.io.InputStream
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import uz.sadora.contract.FeatureKeys
import uz.sadora.contract.FlagKeys
import uz.sadora.contract.Limits
import uz.sadora.contract.MediaKind
import uz.sadora.contract.MediaLink
import uz.sadora.contract.MediaUpload
import uz.sadora.contract.MessageMedia
import uz.sadora.contract.StartMediaUploadRequest
import uz.sadora.server.core.ConflictException
import uz.sadora.server.core.FeatureDisabledException
import uz.sadora.server.core.ForbiddenException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.UpstreamUnavailableException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.db.ContentStatus
import uz.sadora.server.entitlement.EntitlementService

/** What a signed address opens: the bytes, by range, and what they are. */
class MediaContent(
    val sizeBytes: Long,
    val mimeType: String,
    val open: (LongRange) -> InputStream,
)

/** The media disk as the system page shows it. */
data class MediaHealthFacts(
    val keyConfigured: Boolean,
    val disk: MediaDisk,
    val lastBackupAt: Instant?,
    /** Where new uploads wait; 1.0 when the guard is off. */
    val pauseAt: Double,
)

/**
 * Photos and videos in messages: their uploads, who may open them, and how long the
 * files are kept.
 *
 * An item belongs to whoever uploaded it until a message carries it; from then on the
 * two people of that thread may open it, for as long as the line is up. A moderator
 * reads it only through a report on that line ([CommunityModerationService]).
 *
 * The day's allowance is spent when an upload starts — the file count and its megabytes
 * — so an upload abandoned halfway still counted: otherwise starting and dropping
 * uploads would be a free way to fill the disk.
 */
class MediaService(
    private val repository: MediaRepository,
    private val store: MediaStore,
    /** Null where MEDIA_MASTER_KEY is not set outside development: uploads are refused. */
    private val keys: MediaKeys?,
    private val entitlements: EntitlementService,
    /** Her timezone, for the day the allowance is counted in. */
    private val timezoneOf: suspend (Uuid) -> String,
    /** Whether videos and albums are switched on for her ([FlagKeys.CHAT_MEDIA]). */
    private val chatMediaOn: suspend (Uuid) -> Boolean,
    private val mediaDir: File,
    /** The share of the disk past which new uploads wait; 1.0 never. */
    private val diskPause: Double = DISK_PAUSE,
    private val clock: Clock = Clock.System,
) {
    private val logger = LoggerFactory.getLogger(MediaService::class.java)
    private val locks = Array(32) { Mutex() }
    private fun lockFor(id: Uuid) = locks[(id.hashCode() and 0x7FFFFFFF) % locks.size]

    val configured: Boolean get() = keys != null

    private fun requireKeys(): MediaKeys = keys ?: throw UpstreamUnavailableException(UNAVAILABLE)

    // ---------------------------------------------------------------- uploads

    suspend fun start(userId: Uuid, request: StartMediaUploadRequest): MediaUpload {
        val keys = requireKeys()
        val mime = request.mimeType.trim().lowercase()
        when (request.kind) {
            MediaKind.PHOTO -> {
                if (mime !in PHOTO_MIME) throw ValidationException("mimeType", "Faqat JPEG yoki PNG rasm")
                if (request.sizeBytes > Limits.MEDIA_PHOTO_MAX_BYTES) throw ValidationException("sizeBytes", "Rasm juda katta — kichikroq qilib yuboring")
            }
            MediaKind.VIDEO -> {
                if (mime !in VIDEO_MIME) throw ValidationException("mimeType", "Faqat MP4 yoki MOV video")
                if (request.sizeBytes > Limits.MEDIA_VIDEO_MAX_BYTES) {
                    throw ValidationException("sizeBytes", "Video juda katta — eng ko'pi ${Limits.MEDIA_VIDEO_MAX_BYTES / MEGABYTE} MB")
                }
                if (!chatMediaOn(userId)) throw FeatureDisabledException(FlagKeys.CHAT_MEDIA)
            }
        }
        if (request.sizeBytes <= 0) throw ValidationException("sizeBytes", "Fayl bo'sh")
        if (diskFull()) throw UpstreamUnavailableException(DISK_FULL)

        val timezone = timezoneOf(userId)
        entitlements.consume(userId, FeatureKeys.CHAT_MEDIA, timezone)
        val megabytes = ((request.sizeBytes + MEGABYTE - 1) / MEGABYTE).toInt()
        try {
            entitlements.consume(userId, FeatureKeys.CHAT_MEDIA_MB, timezone, amount = megabytes)
        } catch (refused: Exception) {
            entitlements.release(userId, FeatureKeys.CHAT_MEDIA, timezone)
            throw refused
        }

        val id = Uuid.random()
        val (_, wrapped) = keys.newKey()
        repository.insertUpload(id, userId, request.kind, mime, request.sizeBytes, wrapped, clock.now())
        return MediaUpload(id.toString(), request.kind, request.sizeBytes, 0, MediaStore.CHUNK_BYTES)
    }

    suspend fun progress(userId: Uuid, id: Uuid): MediaUpload {
        val record = ownUpload(userId, id)
        val received = if (record.status == MediaStatus.READY) record.sizeBytes else io { store.received(id) }
        return MediaUpload(id.toString(), record.kind, record.sizeBytes, received, MediaStore.CHUNK_BYTES)
    }

    suspend fun append(userId: Uuid, id: Uuid, offset: Long, bytes: ByteArray): MediaUpload {
        val record = ownUpload(userId, id)
        if (record.status != MediaStatus.UPLOADING) throw ConflictException(MediaStore.OUT_OF_ORDER)
        val cipher = requireKeys().cipher(record.wrappedKey)
        val received = io { store.append(id, cipher, offset, bytes, record.sizeBytes) }
        return MediaUpload(id.toString(), record.kind, record.sizeBytes, received, MediaStore.CHUNK_BYTES)
    }

    /**
     * Checks what arrived and makes it the item. A photo is decoded and written again
     * ([MediaImages]); a video is kept as it came, once its own boxes say it is a video
     * no longer than [Limits.MEDIA_VIDEO_MAX_SECONDS]. Finishing twice — the answer was
     * lost — answers the same item.
     */
    suspend fun complete(userId: Uuid, id: Uuid, posterBase64: String?): MessageMedia = lockFor(id).withLock {
        val record = ownUpload(userId, id)
        if (record.status == MediaStatus.READY) return@withLock record.dto()
        val cipher = requireKeys().cipher(record.wrappedKey)
        val part = io { store.part(id, cipher, record.sizeBytes) }
        val at = clock.now()
        when (record.kind) {
            MediaKind.PHOTO -> {
                val bytes = io { part.open(0 until record.sizeBytes).use { it.readBytes() } }
                val processed = withContext(Dispatchers.Default) { MediaImages.process(bytes) }
                io {
                    store.write(id, MediaVariant.FULL, cipher, processed.full)
                    store.write(id, MediaVariant.THUMB, cipher, processed.thumb)
                    store.dropPart(id)
                }
                repository.markReady(id, JPEG, processed.full.size.toLong(), processed.thumb.size.toLong(), processed.width, processed.height, null, at)
            }
            MediaKind.VIDEO -> {
                val info = io { Mp4Info.read(ByteSource.of(part, record.sizeBytes)) }
                    ?: throw ValidationException("file", "Bu fayl video emas yoki buzilgan")
                if (info.durationMs > Limits.MEDIA_VIDEO_MAX_MS) {
                    throw ValidationException("file", "Video ${Limits.MEDIA_VIDEO_MAX_SECONDS / 60} daqiqadan uzun bo'lmasin")
                }
                val poster = posterBase64?.trim()?.takeIf { it.isNotEmpty() }?.let { decodePoster(it) }
                io {
                    // A video is kept as it came, but not its notes: where it was filmed and
                    // on which phone are blanked in place, as a photo's are by re-encoding.
                    val notes = info.metadataBoxes
                    store.editPart(id, cipher, record.sizeBytes, notes.map { it.edited }) { offset ->
                        notes.firstNotNullOf { it.blankedAt(offset) }
                    }
                    if (poster != null) store.write(id, MediaVariant.THUMB, cipher, poster)
                    store.promotePart(id)
                }
                repository.markReady(id, record.mimeType, record.sizeBytes, poster?.size?.toLong() ?: 0, info.width, info.height, info.durationMs, at)
            }
        }
        repository.byId(id)!!.dto()
    }

    /** She dropped it before sending: the row and its files go now. */
    suspend fun cancel(userId: Uuid, id: Uuid) {
        if (!repository.deleteUnattached(id, userId)) throw NotFoundException(NOT_FOUND)
        io { store.delete(id) }
    }

    private suspend fun ownUpload(userId: Uuid, id: Uuid): MediaRecord {
        val record = repository.byId(id)
        // Another account's upload reads as no upload: an id is not a thing to probe.
        if (record == null || record.ownerId != userId || record.messageId != null) throw NotFoundException(UPLOAD_NOT_FOUND)
        return record
    }

    private suspend fun decodePoster(base64: String): ByteArray {
        if (base64.length > Limits.MEDIA_POSTER_MAX_BYTES / 3 * 4 + 4) throw ValidationException("posterBase64", "Muqova rasmi juda katta")
        val bytes = runCatching { Base64.decode(base64) }.getOrElse { throw ValidationException("posterBase64", MediaImages.UNREADABLE) }
        return withContext(Dispatchers.Default) { MediaImages.thumbnail(bytes) }
    }

    // ---------------------------------------------------------------- sending

    /**
     * The finished uploads a message is about to carry, checked: hers, finished, and in
     * no message yet. Answers them in the order given.
     */
    suspend fun forMessage(userId: Uuid, ids: List<String>): List<MediaRecord> {
        val parsed = ids.map { raw -> runCatching { Uuid.parse(raw) }.getOrNull() ?: throw ValidationException("mediaIds", NOT_SENDABLE) }
        if (parsed.toSet().size != parsed.size) throw ValidationException("mediaIds", NOT_SENDABLE)
        val records = repository.byIds(parsed).associateBy { it.id }
        return parsed.map { id ->
            records[id]?.takeIf { it.ownerId == userId && it.status == MediaStatus.READY && it.messageId == null }
                ?: throw ValidationException("mediaIds", NOT_SENDABLE)
        }
    }

    /**
     * The message [ids] already went out in, when they are all of that message's items, in
     * its order, and hers: a send repeated because the app closed before it heard the answer.
     * Null for anything else, which [forMessage] then refuses as usual.
     */
    suspend fun sentIn(userId: Uuid, ids: List<String>): Uuid? {
        val parsed = ids.map { raw -> runCatching { Uuid.parse(raw) }.getOrNull() ?: return null }
        val records = repository.byIds(parsed).associateBy { it.id }
        val messageId = records[parsed.firstOrNull()]?.messageId ?: return null
        if (parsed.any { id -> records[id]?.let { it.ownerId == userId && it.messageId == messageId } != true }) return null
        val carried = repository.ofMessages(listOf(messageId))[messageId].orEmpty().map { it.id }
        return messageId.takeIf { carried == parsed }
    }

    suspend fun isChatMediaOn(userId: Uuid): Boolean = chatMediaOn(userId)

    /** Each message's items, as the apps read them. */
    suspend fun ofMessages(messageIds: Collection<Uuid>): Map<Uuid, List<MessageMedia>> =
        repository.ofMessages(messageIds).mapValues { (_, items) -> items.map { it.dto() } }

    // ---------------------------------------------------------------- reading

    /** A thumbnail, for someone who may see the item. */
    suspend fun thumb(viewer: Uuid, id: Uuid): ByteArray = thumbForModerator(viewable(viewer, id))

    /** A signed address for the photo or the video itself, for someone who may see it. */
    suspend fun link(viewer: Uuid, id: Uuid): MediaLink = linkForModerator(viewable(viewer, id))

    /**
     * The thumbnail without asking who is looking: the moderator's door, whose caller has
     * checked the item belongs to the reported line. Everyone else comes through [thumb].
     */
    suspend fun thumbForModerator(record: MediaRecord): ByteArray {
        if (record.thumbBytes <= 0) throw NotFoundException(NOT_FOUND)
        val cipher = requireKeys().cipher(record.wrappedKey)
        return io { store.open(record.id, MediaVariant.THUMB, cipher, record.thumbBytes, 0 until record.thumbBytes).use { it.readBytes() } }
    }

    /** A signed address without asking who is looking; see [thumbForModerator]. */
    fun linkForModerator(record: MediaRecord): MediaLink {
        val keys = requireKeys()
        val seconds = (clock.now() + LINK_TTL).epochSeconds
        val signature = keys.sign(record.id, MediaVariant.FULL, seconds)
        return MediaLink("/v1/media/${record.id}/file?exp=$seconds&sig=$signature", Instant.fromEpochSeconds(seconds))
    }

    /** Opens what a signed address points at, once its signature and its time are checked. */
    suspend fun openSigned(id: Uuid, expiresAtEpochSeconds: Long, signature: String): MediaContent {
        val keys = requireKeys()
        if (expiresAtEpochSeconds < clock.now().epochSeconds || !keys.verify(id, MediaVariant.FULL, expiresAtEpochSeconds, signature)) {
            throw ForbiddenException(message = LINK_EXPIRED)
        }
        val record = repository.byId(id)?.takeIf { it.status == MediaStatus.READY } ?: throw NotFoundException(NOT_FOUND)
        val cipher = keys.cipher(record.wrappedKey)
        // A MOV of H.264 plays as MP4 everywhere; Chrome refuses the QuickTime type itself.
        val mime = if (record.kind == MediaKind.VIDEO) "video/mp4" else record.mimeType
        return MediaContent(record.sizeBytes, mime) { range -> store.open(id, MediaVariant.FULL, cipher, record.sizeBytes, range) }
    }

    /**
     * The item, when [viewer] may see it: her own upload not sent yet, or an item of a
     * visible line in a thread she is part of. Anything else reads as not there.
     */
    private suspend fun viewable(viewer: Uuid, id: Uuid): MediaRecord {
        val record = repository.byId(id)?.takeIf { it.status == MediaStatus.READY } ?: throw NotFoundException(NOT_FOUND)
        val messageId = record.messageId
        if (messageId == null) {
            if (record.ownerId != viewer) throw NotFoundException(NOT_FOUND)
            return record
        }
        val place = repository.placeOf(messageId) ?: throw NotFoundException(NOT_FOUND)
        if (place.messageStatus != ContentStatus.VISIBLE || (viewer != place.userA && viewer != place.userB)) throw NotFoundException(NOT_FOUND)
        return record
    }

    // ---------------------------------------------------------------- housekeeping

    /**
     * Uploads nobody sent within a day go, and then every file whose row is gone — the
     * rows an erased account or a deleted thread took with them by cascade. Answers how
     * many uploads and orphaned items went.
     */
    suspend fun collectGarbage(at: Instant = clock.now()): Int {
        var removed = 0
        while (true) {
            val stale = repository.staleUnattached(at - UNSENT_TTL, GC_BATCH)
            if (stale.isEmpty()) break
            repository.deleteRows(stale)
            io { stale.forEach(store::delete) }
            removed += stale.size
            if (stale.size < GC_BATCH) break
        }
        removed += withContext(Dispatchers.IO) { store.sweep(at - SWEEP_GRACE) { ids -> repository.known(ids) } }
        return removed
    }

    private var diskCheckedAt: Instant? = null
    private var diskWasFull = false

    /** Past [diskPause] of the disk, new uploads wait. Read at most once a minute. */
    private suspend fun diskFull(): Boolean {
        val now = clock.now()
        val checked = diskCheckedAt
        if (checked == null || now - checked > DISK_CHECK_EVERY) {
            diskWasFull = diskPause < 1.0 && io { store.usedFraction() } >= diskPause
            diskCheckedAt = now
        }
        return diskWasFull
    }

    suspend fun health(): MediaHealthFacts = io {
        val marker = File(mediaDir, BACKUP_MARKER)
        val lastBackup = if (marker.isFile) runCatching { Instant.parse(marker.readText().trim()) }.getOrNull() else null
        MediaHealthFacts(keys != null, store.disk(), lastBackup, diskPause)
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        private const val MEGABYTE = 1024L * 1024
        private const val JPEG = "image/jpeg"
        private const val GC_BATCH = 200
        val PHOTO_MIME = setOf("image/jpeg", "image/png")
        val VIDEO_MIME = setOf("video/mp4", "video/quicktime")
        val LINK_TTL = 15.minutes
        val UNSENT_TTL = 24.hours
        /** A file younger than this may belong to an upload whose row is being written. */
        val SWEEP_GRACE = 1.hours
        private val DISK_CHECK_EVERY = 60.seconds
        const val DISK_WARN = 0.80
        const val DISK_PAUSE = 0.95
        /** Written by sadora-backend/deploy/media_backup.sh after each good run: an ISO instant. */
        const val BACKUP_MARKER = ".last-backup"

        const val UNAVAILABLE = "Rasm va video yuborish hozircha ishlamayapti"
        const val DISK_FULL = "Server xotirasi to'lgan — birozdan keyin urinib ko'ring"
        const val NOT_FOUND = MediaStore.NOT_FOUND
        const val UPLOAD_NOT_FOUND = MediaStore.UPLOAD_NOT_FOUND
        const val NOT_SENDABLE = "Rasm yoki video topilmadi yoki allaqachon yuborilgan"
        const val LINK_EXPIRED = "Havolaning muddati o'tgan"
    }
}
