package uz.sadora.server.partner

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
 * Runs Yaqinim's daily pass. Every quarter hour, because "nine in the morning" is a
 * different instant for every person on it; the dedupe keys make the repeats free.
 */
class PartnerAlertJob(
    private val partners: PartnerService,
    private val tickInterval: Duration = 15.minutes,
) {
    private val logger = LoggerFactory.getLogger(PartnerAlertJob::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        job = scope.launch {
            logger.info("Partner alert job started, ticking every {}", tickInterval)
            while (isActive) {
                runCatching { partners.dailyAlerts() }.onFailure { logger.error("Partner alert tick failed", it) }
                delay(tickInterval)
            }
        }
    }

    fun stop() {
        job?.cancel()
        scope.cancel()
    }
}
