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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
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

    /**
     * One delivery pass at a time. The tick and [deliverSoon] both read the queued rows
     * before marking them; without the lock two passes could read the same row and ring
     * the phone twice.
     */
    private val deliveryLock = Mutex()

    /**
     * Her companion, when her plan has one: its reminders then speak in its voice. Set
     * once the pet service exists, which is built after this scheduler.
     */
    var companionOf: (suspend (Uuid) -> uz.sadora.contract.PetKind?)? = null

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
        deliveryLock.withLock { deliverDue() }
    }

    /**
     * Sends what is due now instead of at the next tick, off the caller's thread — for a
     * message someone on the other phone is waiting to see. Safe to call often: a pass with
     * nothing queued is one query, and passes never overlap. Reads [userId]'s rows only.
     */
    fun deliverSoon(userId: Uuid) {
        scope.launch {
            // Only hers: behind a backlog of other people's rows, a whole-queue pass could
            // fill its batch before reaching the one just written.
            runCatching { deliveryLock.withLock { deliver(notifications.dueFor(userId, DELIVERY_BATCH)) } }
                .onFailure { logger.warn("Immediate delivery failed", it) }
        }
    }

    private suspend fun queueMedicationReminders() {
        val currentTime = now()
        val horizon = currentTime + lookAhead
        // One row for everybody; re-reading it per dose was the largest share of the
        // tick's queries.
        val caps = notifications.caps()

        // Her row once for all her medicines, and everyone's in one query: reading it per
        // medication made the tick one query per course, every minute.
        val courses = medications.withRemindersEnabled().groupBy({ it.first }, { it.second })
        val people = users.findByIds(courses.keys)
        courses.forEach { (userId, list) ->
            val user = people[userId] ?: return@forEach
            list.forEach { medication ->
                // One medication that cannot be scheduled — a template that fails to render,
                // a row the database refuses — must not stop the loop before the next user's
                // reminders; it used to abort the whole tick, every minute, until fixed.
                runCatching { queueRemindersFor(user, medication, currentTime, horizon, caps) }
                    .onFailure { logger.error("Could not queue reminders for medication {}", medication.id, it) }
            }
        }
    }

    private suspend fun queueRemindersFor(
        user: uz.sadora.server.user.UserRecord,
        medication: uz.sadora.server.health.MedicationRecord,
        currentTime: kotlin.time.Instant,
        horizon: kotlin.time.Instant,
        caps: uz.sadora.contract.FrequencyCaps,
    ) {
        val userId = user.id
        val zone = resolveTimeZone(user.timezone)
        val today = currentTime.dayIn(user.timezone)

        // Yesterday and tomorrow too: a dose at 00:02 is inside the 23:58 look-ahead but on
        // tomorrow's schedule, and one the last tick missed may sit on yesterday's.
        val candidates = listOf(today.minus(1, DateTimeUnit.DAY), today, today.plus(1, DateTimeUnit.DAY))
            .flatMap { day -> DoseSchedule.dosesOn(medication, day).map { day to it } }

        candidates.forEach { (day, dueAt) ->
                val dueInstant = LocalDateTime(day, dueAt).toInstant(zone)
                // A dose whose moment passed while a tick ran late, or the server was
                // restarting, is still sent a little late rather than never. The dedupe key
                // keeps it to once.
                if (dueInstant < currentTime - CATCH_UP || dueInstant > horizon) return@forEach
                // Not a dose she missed: one that fell due before she added the medicine.
                if (dueInstant < currentTime && dueInstant < medication.createdAt) return@forEach

                val dedupeKey = "med:${medication.id}:$day:$dueAt"
                val settings = notifications.settingsOf(userId)
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
                val pet = runCatching { companionOf?.invoke(userId) }.getOrNull()
                val title = if (pet != null) {
                    uz.sadora.server.pet.PetPhrases.name(user.language, pet)
                } else {
                    template?.title.orEmpty().render(medication.name, dueAt.toString())
                }
                val body = if (pet != null) {
                    uz.sadora.server.pet.PetPhrases.medReminder(user.language, pet, dueAt.toString())
                } else {
                    template?.body.orEmpty().render(medication.name, dueAt.toString())
                }

                val queued = notifications.enqueue(
                    userId = userId,
                    category = NotificationCategory.MED_REMINDER,
                    // Never the medicine's name: a push is read on the lock screen.
                    title = title.ifBlank { MedReminderFallback.title(user.language) },
                    body = body.ifBlank { MedReminderFallback.body(user.language, dueAt.toString()) },
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

    private suspend fun deliverDue() = deliver(notifications.due(DELIVERY_BATCH))

    private suspend fun deliver(records: List<OutboxRecord>) {
        records.forEach { record ->
            val tokens = pushTokens(record.userId, record.targetApp)
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

    private suspend fun hasPushToken(userId: Uuid): Boolean = pushTokens(userId, TARGET_CLIENT).isNotEmpty()

    /** The account's devices of one app: a doctor's patients ring her doctor app, not her own. */
    private suspend fun pushTokens(userId: Uuid, app: String): List<String> = dbQuery {
        Devices.selectAll()
            .where { (Devices.userId eq userId) and Devices.pushToken.isNotNull() and (Devices.app eq app) }
            .mapNotNull { it[Devices.pushToken] }
    }

    /** `{{name}}` and `{{time}}` are the only variables the medication template uses. */
    private fun String.render(name: String, time: String): String =
        replace("{{name}}", name).replace("{{time}}", time)

    private companion object {
        const val DELIVERY_BATCH = 100

        /** How late a missed dose reminder may still go out; later than this it is noise. */
        val CATCH_UP = 30.minutes
    }
}

/** What a medication reminder says when its template row is missing or blank. */
internal object MedReminderFallback {
    fun title(language: uz.sadora.contract.Language): String = when (language) {
        uz.sadora.contract.Language.UZ -> "Dori vaqti"
        uz.sadora.contract.Language.RU -> "Время лекарства"
        uz.sadora.contract.Language.EN -> "Medication time"
    }

    fun body(language: uz.sadora.contract.Language, time: String): String = when (language) {
        uz.sadora.contract.Language.UZ -> "Qabul vaqti — $time"
        uz.sadora.contract.Language.RU -> "Время приёма — $time"
        uz.sadora.contract.Language.EN -> "Time to take it — $time"
    }
}
