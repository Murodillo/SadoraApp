package uz.sadora.server.media

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Moves the photos sent before V69 into the media store, as `media` lines of one photo
 * each. Each is decoded and written again like a new upload, so the old ones lose their
 * camera notes too. The old bytes stay in `community_message_images` — and so in the
 * database's dumps — until a later migration drops the table, so a media disk lost
 * before its backup runs loses no photo sent before this.
 *
 * Runs once at start, in batches, until nothing is left. A picture that cannot be read
 * stays where it is, an `image` line the old route still serves, and is not tried again
 * until the next start.
 */
class LegacyImageMigration(
    private val repository: MediaRepository,
    private val store: MediaStore,
    private val keys: MediaKeys?,
    private val clock: Clock = Clock.System,
) {
    private val logger = LoggerFactory.getLogger(LegacyImageMigration::class.java)
    private val failed = HashSet<Uuid>()

    /** Moves one batch; answers how many photos moved. */
    suspend fun runOnce(batch: Int = 25): Int {
        val keys = keys ?: return 0
        val images = repository.legacyImages(batch, failed.toSet())
        var moved = 0
        images.forEach { image ->
            val result = runCatching {
                val processed = withContext(Dispatchers.Default) { MediaImages.process(image.bytes) }
                val (cipher, wrapped) = keys.newKey()
                val id = Uuid.random()
                withContext(Dispatchers.IO) {
                    store.write(id, MediaVariant.FULL, cipher, processed.full)
                    store.write(id, MediaVariant.THUMB, cipher, processed.thumb)
                }
                val at = clock.now()
                repository.adoptLegacy(
                    image,
                    MediaRecord(
                        id = id,
                        ownerId = image.senderId,
                        messageId = image.messageId,
                        position = 0,
                        kind = uz.sadora.contract.MediaKind.PHOTO,
                        status = MediaStatus.READY,
                        mimeType = "image/jpeg",
                        sizeBytes = processed.full.size.toLong(),
                        thumbBytes = processed.thumb.size.toLong(),
                        width = processed.width,
                        height = processed.height,
                        durationMs = null,
                        wrappedKey = wrapped,
                        createdAt = at,
                    ),
                    at,
                )
            }
            if (result.isSuccess) {
                moved++
            } else {
                failed += image.messageId
                logger.warn("Could not move the photo of message {}", image.messageId, result.exceptionOrNull())
            }
        }
        return moved
    }

    /** Every batch, one after another; the files a crash left half-written are the sweeper's. */
    suspend fun runAll(): Int {
        var total = 0
        while (true) {
            val moved = runOnce()
            total += moved
            if (moved == 0) break
        }
        return total
    }
}

/**
 * The media's housekeeping, hourly: uploads nobody sent, the files of rows gone with an
 * account or a thread, and — once, at start — the photos from before the media store.
 */
class MediaJob(
    private val media: MediaService,
    private val migration: LegacyImageMigration,
    private val tickInterval: Duration = 1.hours,
) {
    private val logger = LoggerFactory.getLogger(MediaJob::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        if (!media.configured) logger.warn("MEDIA_MASTER_KEY is not set: photo and video uploads are refused")
        job = scope.launch {
            runCatching { migration.runAll() }
                .onSuccess { moved -> if (moved > 0) logger.info("Moved {} older photos into the media store", moved) }
                .onFailure { logger.error("Moving older photos failed", it) }
            while (isActive) {
                runCatching { media.collectGarbage() }
                    .onSuccess { removed -> if (removed > 0) logger.info("Removed {} unsent or orphaned media items", removed) }
                    .onFailure { logger.warn("Media housekeeping failed", it) }
                delay(tickInterval)
            }
        }
    }

    fun stop() {
        job?.cancel()
        scope.cancel()
    }
}
