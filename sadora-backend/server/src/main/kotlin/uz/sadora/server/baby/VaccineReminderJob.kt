package uz.sadora.server.baby

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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory
import uz.sadora.contract.AccountStatus
import uz.sadora.contract.Language
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.NotificationStatus
import uz.sadora.contract.VaccineStatus
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.notify.DeliveryDecision
import uz.sadora.server.notify.NotificationPolicy
import uz.sadora.server.notify.NotificationRepository
import uz.sadora.server.user.UserRepository

/**
 * "Emlash yaqinlashmoqda": a push to the mother three days before a dose falls due, and
 * on the day itself, for every dose of the calendar she has not marked given.
 *
 * Runs every half hour and sends only in her daytime, so a server that was down at nine
 * still reminds her at ten; the dedupe key makes every repeat after the first free.
 */
class VaccineReminderJob(
    private val babies: BabyRepository,
    private val users: UserRepository,
    private val notifications: NotificationRepository,
    private val tickInterval: Duration = 30.minutes,
) {
    private val logger = LoggerFactory.getLogger(VaccineReminderJob::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        job = scope.launch {
            logger.info("Vaccine reminder job started, ticking every {}", tickInterval)
            while (isActive) {
                runCatching { tick() }.onFailure { logger.error("Vaccine reminder tick failed", it) }
                delay(tickInterval)
            }
        }
    }

    fun stop() {
        job?.cancel()
        scope.cancel()
    }

    suspend fun tick(at: Instant = now()) {
        val earliest = at.dayIn("UTC").minus(BabyService.MAX_AGE_YEARS + 1, DateTimeUnit.YEAR)
        val all = babies.bornSince(earliest)
        if (all.isEmpty()) return
        val owners = users.findByIds(all.map { it.userId }.distinct())
        val given = babies.vaccinationsOf(all.map { it.id })
        all.forEach { record ->
            runCatching {
                val mother = owners[record.userId]?.takeIf { it.status == AccountStatus.ACTIVE } ?: return@runCatching
                if (at.toLocalDateTime(TimeZone.of(mother.timezone)).hour !in HOURS) return@runCatching
                val today = at.dayIn(mother.timezone)
                BabyService.vaccines(record, given[record.id].orEmpty(), today, mother.language)
                    .filter { it.status != VaccineStatus.DONE }
                    .forEach { dose ->
                        val daysLeft = today.daysUntil(dose.dueOn)
                        if (daysLeft != NOTICE_DAYS && daysLeft != 0) return@forEach
                        val words = words(record.baby.name, dose.name, daysLeft, mother.language)
                        val decision = NotificationPolicy.decide(
                            category = CATEGORY,
                            localTime = at.toLocalDateTime(TimeZone.of(mother.timezone)).time,
                            settings = notifications.settingsOf(mother.id),
                            sentToday = notifications.sentCount(mother.id, at - 1.days),
                            sentThisWeek = notifications.sentCount(mother.id, at - 7.days),
                            caps = notifications.caps(),
                            hasDevice = true,
                        )
                        notifications.enqueue(
                            userId = mother.id,
                            category = CATEGORY,
                            title = words.first,
                            body = words.second,
                            scheduledFor = at,
                            dedupeKey = "vaccine:${record.id}:${dose.code}:$daysLeft",
                            status = if (decision is DeliveryDecision.Send) NotificationStatus.QUEUED else NotificationStatus.SUPPRESSED,
                            suppressedReason = (decision as? DeliveryDecision.Suppress)?.reason,
                            link = "sadora://baby/${record.id}/vaccines",
                        )
                    }
            }.onFailure { logger.warn("Vaccine reminders failed for a baby", it) }
        }
    }

    companion object {
        const val NOTICE_DAYS = 3
        private val HOURS = 9..20
        /** A vaccine is health care she asked the app to keep, not a nudge to be capped away. */
        private val CATEGORY = NotificationCategory.SYSTEM

        fun words(baby: String, vaccine: String, daysLeft: Int, language: Language): Pair<String, String> = when (language) {
            Language.UZ -> (if (daysLeft == 0) "Bugun emlash kuni" else "Emlash yaqinlashmoqda") to
                (if (daysLeft == 0) "$baby: $vaccine — bugun." else "$baby: $vaccine — $daysLeft kundan keyin.")
            Language.RU -> (if (daysLeft == 0) "Сегодня день прививки" else "Скоро прививка") to
                (if (daysLeft == 0) "$baby: $vaccine — сегодня." else "$baby: $vaccine — через $daysLeft дн.")
            Language.EN -> (if (daysLeft == 0) "Vaccine day" else "A vaccine is coming up") to
                (if (daysLeft == 0) "$baby: $vaccine — today." else "$baby: $vaccine — in $daysLeft days.")
        }
    }
}
