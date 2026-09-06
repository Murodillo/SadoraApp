package uz.sadora.server.notify

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid
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
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.slf4j.LoggerFactory
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.NotificationStatus
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.core.resolveTimeZone
import uz.sadora.server.db.Devices
import uz.sadora.server.db.dbQuery
import uz.sadora.server.health.DoseSchedule
import uz.sadora.server.health.MedicationRepository
import uz.sadora.server.user.UserRepository

/**
 * The background loop that turns schedules into notifications.
 *
 * It ticks, looks a short way ahead, and writes what it decides into the outbox —
 * including what it decides to suppress and why. Two properties make that safe to run
 * every minute and safe to restart mid-tick: every candidate carries a dedupe key, so
 * re-queuing is a no-op rather than a second buzz; and the decision is recorded even
 * when the answer is no, which is the only way to answer "why didn't she get it".
 */
class NotificationScheduler(
    private val notifications: NotificationRepository,
    private val medications: MedicationRepository,
    private val users: UserRepository,
    private val sender: PushSender,
    private val tickInterval: Duration = 1.minutes,
    /** How far ahead of its due time a reminder is queued. */
    private val lookAhead: Duration = 5.minutes,
) {
    private val logger = LoggerFactory.getLogger(NotificationScheduler::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        job = scope.launch {
            logger.info("Notification scheduler started, ticking every {}", tickInterval)
            while (isActive) {
                // One bad tick must not stop the loop for everyone.
                runCatching { tick() }.onFailure { logger.error("Scheduler tick failed", it) }
                delay(tickInterval)
            }
        }
    }

    fun stop() {
        job?.cancel()
        scope.cancel()
    }

    /** Exposed for tests and for an operator-triggered run. */
    suspend fun tick() {
        queueMedicationReminders()
        deliverDue()
    }

    private suspend fun queueMedicationReminders() {
        val currentTime = now()
        val horizon = currentTime + lookAhead

        medications.withRemindersEnabled().forEach { (userId, medication) ->
            val user = users.findById(userId) ?: return@forEach
            val zone = resolveTimeZone(user.timezone)
            val today = currentTime.dayIn(user.timezone)

            DoseSchedule.dosesOn(medication, today).forEach { dueAt ->
                val dueInstant = LocalDateTime(today, dueAt).toInstant(zone)
                if (dueInstant < currentTime || dueInstant > horizon) return@forEach

                val dedupeKey = "med:${medication.id}:$today:$dueAt"
                val settings = notifications.settingsOf(userId)
                val caps = notifications.caps()
                val localTime = currentTime.toLocalDateTime(zone).time

                val decision = NotificationPolicy.decide(
                    category = NotificationCategory.MED_REMINDER,
                    localTime = localTime,
                    settings = settings,
                    sentToday = notifications.sentCount(userId, currentTime - 1.days),
                    sentThisWeek = notifications.sentCount(userId, currentTime - 7.days),
                    caps = caps,
                    hasDevice = hasPushToken(userId),
                )

                val template = notifications.template("med_reminder", user.language.name.lowercase())
                val title = template?.title.orEmpty().render(medication.name, dueAt.toString())
                val body = template?.body.orEmpty().render(medication.name, dueAt.toString())

                val queued = notifications.enqueue(
                    userId = userId,
                    category = NotificationCategory.MED_REMINDER,
                    title = title.ifBlank { medication.name },
                    body = body.ifBlank { "Qabul vaqti — $dueAt" },
                    scheduledFor = dueInstant,
                    dedupeKey = dedupeKey,
                    status = if (decision is DeliveryDecision.Send) {
                        NotificationStatus.QUEUED
                    } else {
                        NotificationStatus.SUPPRESSED
                    },
                    suppressedReason = (decision as? DeliveryDecision.Suppress)?.reason,
                )
                if (queued && decision is DeliveryDecision.Suppress) {
                    logger.debug("Suppressed reminder for {}: {}", medication.name, decision.reason)
                }
            }
        }
    }

    private suspend fun deliverDue() {
        notifications.due(DELIVERY_BATCH).forEach { record ->
            val tokens = pushTokens(record.userId)
            if (tokens.isEmpty()) {
                notifications.markFailed(record.id, uz.sadora.contract.SuppressionReasons.NO_DEVICE)
                return@forEach
            }
            val delivered = runCatching { sender.send(record, tokens) }.getOrElse { failure ->
                logger.warn("Push delivery failed for {}", record.id, failure)
                false
            }
            if (delivered) notifications.markSent(record.id)
            else notifications.markFailed(record.id, "delivery_failed")
        }
    }

    private suspend fun hasPushToken(userId: Uuid): Boolean = pushTokens(userId).isNotEmpty()

    private suspend fun pushTokens(userId: Uuid): List<String> = dbQuery {
        Devices.selectAll()
            .where { (Devices.userId eq userId) and Devices.pushToken.isNotNull() }
            .mapNotNull { it[Devices.pushToken] }
    }

    /** `{{name}}` and `{{time}}` are the only variables the medication template uses. */
    private fun String.render(name: String, time: String): String =
        replace("{{name}}", name).replace("{{time}}", time)

    private companion object {
        const val DELIVERY_BATCH = 100
    }
}
