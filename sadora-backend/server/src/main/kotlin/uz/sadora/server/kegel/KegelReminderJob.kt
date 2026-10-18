package uz.sadora.server.kegel

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.slf4j.LoggerFactory
import uz.sadora.contract.AccountStatus
import uz.sadora.contract.Language
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.NotificationStatus
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.notify.DeliveryDecision
import uz.sadora.server.notify.NotificationPolicy
import uz.sadora.server.notify.NotificationRepository
import uz.sadora.server.user.UserRepository

/**
 * Her Kegel reminders, at the times she picked. A reminder is skipped once she has already
 * done as many sessions today as reminders have fallen due, so a morning session done early
 * does not get a nudge at nine.
 *
 * The push never says "Kegel": it is read on a lock screen.
 */
class KegelReminderJob(
    private val kegel: KegelRepository,
    private val users: UserRepository,
    private val notifications: NotificationRepository,
    private val tickInterval: Duration = 2.minutes,
) {
    private val logger = LoggerFactory.getLogger(KegelReminderJob::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        job = scope.launch {
            logger.info("Kegel reminder job started, ticking every {}", tickInterval)
            while (isActive) {
                runCatching { tick() }.onFailure { logger.error("Kegel reminder tick failed", it) }
                delay(tickInterval)
            }
        }
    }

    fun stop() {
        job?.cancel()
        scope.cancel()
    }

    suspend fun tick(at: Instant = now()) {
        val settings = kegel.withReminders()
        if (settings.isEmpty()) return
        val people = users.findByIds(settings.map { it.userId })
        settings.forEach { row ->
            runCatching {
                val user = people[row.userId]?.takeIf { it.status == AccountStatus.ACTIVE } ?: return@runCatching
                val zone = TimeZone.of(user.timezone)
                val today = at.dayIn(user.timezone)
                row.reminders.forEachIndexed { index, time ->
                    val due = LocalDateTime(today, time).toInstant(zone)
                    if (at < due || at > due + WINDOW) return@forEachIndexed
                    if (kegel.countOn(user.id, today) > index) return@forEachIndexed
                    val words = words(user.language)
                    val decision = NotificationPolicy.decide(
                        category = CATEGORY,
                        localTime = time,
                        settings = notifications.settingsOf(user.id),
                        sentToday = notifications.sentCount(user.id, at - 1.days),
                        sentThisWeek = notifications.sentCount(user.id, at - 7.days),
                        caps = notifications.caps(),
                        hasDevice = true,
                    )
                    notifications.enqueue(
                        userId = user.id,
                        category = CATEGORY,
                        title = words.first,
                        body = words.second,
                        scheduledFor = due,
                        dedupeKey = "kegel:${user.id}:$today:$time",
                        status = if (decision is DeliveryDecision.Send) NotificationStatus.QUEUED else NotificationStatus.SUPPRESSED,
                        suppressedReason = (decision as? DeliveryDecision.Suppress)?.reason,
                        link = "sadora://open/kegel",
                    )
                }
            }.onFailure { logger.warn("Kegel reminders failed for a user", it) }
        }
    }

    companion object {
        /** A tick that ran late still reminds within this long; the dedupe key keeps it to once. */
        private val WINDOW = 15.minutes
        /** A reminder she set herself at her own time, like a medicine's: not capped away. */
        private val CATEGORY = NotificationCategory.SYSTEM

        fun words(language: Language): Pair<String, String> = when (language) {
            Language.UZ -> "Kunlik mashq vaqti" to "Bir necha daqiqalik mashq sizni kutmoqda 🌸"
            Language.RU -> "Время упражнения" to "Вас ждёт короткая тренировка на несколько минут 🌸"
            Language.EN -> "Time for your exercise" to "A few minutes of practice are waiting for you 🌸"
        }
    }
}
