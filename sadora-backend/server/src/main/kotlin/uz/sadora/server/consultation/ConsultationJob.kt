package uz.sadora.server.consultation

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
 * Ends consultation windows whose 24 hours are up. A window has no one to close it
 * when the doctor never does, and until it is closed a paid one that went unanswered
 * is not yet owed back, nor an answered one asked for its rating.
 */
class ConsultationJob(
    private val service: ConsultationService,
    private val tickInterval: Duration = 5.minutes,
) {
    private val logger = LoggerFactory.getLogger(ConsultationJob::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        job = scope.launch {
            logger.info("Consultation job started, ticking every {}", tickInterval)
            while (isActive) {
                runCatching { service.expireDue() }
                    .onSuccess { closed -> if (closed > 0) logger.info("Closed {} consultation windows", closed) }
                    .onFailure { logger.warn("Consultation tick failed", it) }
                delay(tickInterval)
            }
        }
    }

    fun stop() {
        job?.cancel()
        scope.cancel()
    }
}
