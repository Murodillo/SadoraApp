package uz.sadora.server.wearable

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Pulls from every connected cloud wearable on a schedule.
 *
 * Webhooks are the fast path; this is the floor under them. WHOOP retries a webhook
 * for an hour and then stops, and a server that was down for two would otherwise never
 * see that night. Every tick takes the connections that have not synced in a while,
 * oldest first, and pulls a bounded batch; one failing connection is marked and skipped,
 * not allowed to stall the rest.
 */
class WearableSyncJob(
    private val service: WearableConnectService,
    private val tickInterval: Duration = 5.minutes,
    private val batchSize: Int = 25,
) {
    private val logger = LoggerFactory.getLogger(WearableSyncJob::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        job = scope.launch {
            logger.info("Wearable sync job started, ticking every {}", tickInterval)
            while (isActive) {
                runCatching { tick() }.onFailure { logger.error("Wearable sync tick failed", it) }
                delay(tickInterval)
            }
        }
    }

    fun stop() {
        job?.cancel()
        scope.cancel()
    }

    /** Runs one connection's sync off the request thread — after a connect, after a webhook. */
    fun syncInBackground(block: suspend () -> Unit) {
        scope.launch { runCatching { block() }.onFailure { logger.warn("Background wearable sync failed", it) } }
    }

    /** Exposed for tests and for an operator-triggered run. Returns how many were pulled. */
    suspend fun tick(): Int {
        service.sweepStates()
        val due = service.dueForSync(batchSize)
        var synced = 0
        due.forEach { record ->
            runCatching { service.sync(record) }
                .onSuccess { synced++ }
                .onFailure { logger.warn("Sync failed for {} / {}: {}", record.userId, record.provider, it.message) }
        }
        return synced
    }
}
