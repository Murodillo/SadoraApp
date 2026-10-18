package uz.sadora.server.telegram

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory
import uz.sadora.contract.AccountStatus
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.NotificationStatus
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.health.HealthService
import uz.sadora.server.notify.NotificationRepository
import uz.sadora.server.notify.TARGET_TELEGRAM
import uz.sadora.server.user.UserRepository

/**
 * Reads the bot's updates by long polling and hands each to [TelegramService.handle]. The
 * offset is saved after every update, so a restart neither drops nor repeats one.
 */
class TelegramPoller(
    private val gateway: TelegramGateway,
    private val telegram: TelegramRepository,
    private val service: TelegramService,
) {
    private val logger = LoggerFactory.getLogger(TelegramPoller::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        job = scope.launch {
            logger.info("Telegram poller started")
            while (isActive) {
                runCatching { poll() }.onFailure {
                    logger.warn("Telegram poll failed", it)
                    delay(5.seconds)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        scope.cancel()
    }

    suspend fun poll() {
        val offset = telegram.offset()
        gateway.updates(offset, POLL_SECONDS).forEach { update ->
            runCatching { service.handle(update) }.onFailure { logger.warn("Telegram update {} failed", update.updateId, it) }
            telegram.saveOffset(update.updateId + 1)
        }
    }

    private companion object {
        /** Under the HTTP client's own timeout. */
        const val POLL_SECONDS = 10
    }
}

/**
 * "Hayzingiz 2 kundan keyin": a Telegram-only reminder for the women who linked the bot. The
 * app has its own cycle screens; the phone is not rung for this.
 */
class TelegramPeriodJob(
    private val telegram: TelegramRepository,
    private val users: UserRepository,
    private val health: HealthService,
    private val notifications: NotificationRepository,
    private val tickInterval: Duration = 30.minutes,
) {
    private val logger = LoggerFactory.getLogger(TelegramPeriodJob::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        job = scope.launch {
            while (isActive) {
                runCatching { tick() }.onFailure { logger.error("Telegram period tick failed", it) }
                delay(tickInterval)
            }
        }
    }

    fun stop() {
        job?.cancel()
        scope.cancel()
    }

    suspend fun tick(at: Instant = now()) {
        val links = telegram.linkedUsers()
        if (links.isEmpty()) return
        val people = users.findByIds(links.map { it.userId })
        links.forEach { link ->
            runCatching {
                val user = people[link.userId]?.takeIf { it.status == AccountStatus.ACTIVE && it.lifeStage.predictsCycle } ?: return@runCatching
                if (at.toLocalDateTime(TimeZone.of(user.timezone)).hour !in HOURS) return@runCatching
                val today = at.dayIn(user.timezone)
                val start = health.status(user.id).prediction.nextPeriodStart ?: return@runCatching
                if (today.daysUntil(start) != NOTICE_DAYS) return@runCatching
                notifications.enqueue(
                    userId = user.id,
                    category = NotificationCategory.CYCLE,
                    title = "Sadora",
                    body = TelegramWords(user.language).periodSoon(NOTICE_DAYS),
                    scheduledFor = at,
                    dedupeKey = "tg-period:${user.id}:$start",
                    status = NotificationStatus.QUEUED,
                    suppressedReason = null,
                    targetApp = TARGET_TELEGRAM,
                )
            }.onFailure { logger.warn("Telegram period reminder failed for a user", it) }
        }
    }

    companion object {
        const val NOTICE_DAYS = 2
        private val HOURS = 9..20
    }
}
