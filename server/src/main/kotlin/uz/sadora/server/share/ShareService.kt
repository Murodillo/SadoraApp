package uz.sadora.server.share

import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import uz.sadora.contract.CreateShareRequest
import uz.sadora.contract.DailyHealth
import uz.sadora.contract.DailyMetric
import uz.sadora.contract.DoctorSummary
import uz.sadora.contract.FetalMovement
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.Language
import uz.sadora.contract.LifeStage
import uz.sadora.contract.Limits
import uz.sadora.contract.MedicationSchedule
import uz.sadora.contract.ProfileShare
import uz.sadora.contract.SharedCycle
import uz.sadora.contract.SharedDay
import uz.sadora.contract.SharedMedication
import uz.sadora.contract.SharedMind
import uz.sadora.contract.SharedNutrition
import uz.sadora.contract.SharedPerson
import uz.sadora.contract.SharedPregnancy
import uz.sadora.contract.SharedSymptom
import uz.sadora.contract.SharedSymptomCount
import uz.sadora.contract.SharedWearable
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.core.parseUuid
import uz.sadora.server.core.randomToken
import uz.sadora.server.core.sha256
import uz.sadora.server.health.AppointmentRepository
import uz.sadora.server.health.HealthService
import uz.sadora.server.health.MedicationService
import uz.sadora.server.health.MindRepository
import uz.sadora.server.health.NutritionRepository
import uz.sadora.server.user.UserRepository
import uz.sadora.server.wearable.WearableService

/**
 * The QR code she shows a doctor, and the page it opens.
 *
 * Three rules shape it. The token is random and stored hashed, so a link is only ever
 * held by the phone that made it. Every link expires — a day by default — and can be
 * revoked before that, because "I showed it to one doctor" must not mean "anyone with
 * the photo can read it next month". And the page is assembled from her records at the
 * moment it is opened, never stored: there is no copy to leak, and nothing on it is older
 * than the request.
 *
 * What the page leaves out is as deliberate as what it shows. The journal and the private
 * note on a pregnancy check-in are hers alone — the app promised so on the screen that
 * takes them — and the secret chat is nobody's business at all. A doctor gets what a
 * doctor can use: the record, the numbers, the medicines, the visits.
 */
class ShareService(
    private val shares: ShareRepository,
    private val users: UserRepository,
    private val health: HealthService,
    private val mind: MindRepository,
    private val medications: MedicationService,
    private val appointments: AppointmentRepository,
    private val nutrition: NutritionRepository,
    private val wearables: WearableService,
    private val audit: AuditService,
    private val publicBaseUrl: String,
) {

    // ---------------------------------------------------------------- hers

    suspend fun create(userId: Uuid, request: CreateShareRequest, ip: String?): ProfileShare {
        users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")
        if (request.ttlHours !in Limits.SHARE_TTL_HOURS) {
            throw ValidationException("ttlHours", "1–${Limits.SHARE_MAX_HOURS} soat oralig'ida")
        }
        // One live link at a time. A new QR code is what she makes when the old one is
        // out of her hands, so making one is also what takes the old one back.
        shares.revokeAll(userId, now())

        val token = randomToken(TOKEN_BYTES)
        val created = shares.create(userId, sha256(token), now() + request.ttlHours.hours)
        audit.record(
            AuditEntry(
                actorType = ActorType.USER,
                actorId = userId,
                action = AuditActions.SHARE_CREATED,
                entityType = "profile_share",
                entityId = created.id,
                metadata = mapOf("ttlHours" to request.ttlHours.toString()),
                ip = ip,
            ),
        )
        return created.copy(url = urlFor(token))
    }

    suspend fun list(userId: Uuid): List<ProfileShare> = shares.listOf(userId, limit = 10)

    suspend fun revoke(userId: Uuid, id: String, ip: String?) {
        if (!shares.revoke(userId, parseUuid(id), now())) throw NotFoundException("Havola topilmadi")
        audit.record(
            AuditEntry(
                actorType = ActorType.USER,
                actorId = userId,
                action = AuditActions.SHARE_REVOKED,
                entityType = "profile_share",
                entityId = id,
                ip = ip,
            ),
        )
    }

    fun urlFor(token: String): String = "$publicBaseUrl/share/$token"

    // ---------------------------------------------------------------- the doctor's

    /**
     * Resolves a scanned token to the summary, or null when there is nothing to show.
     *
     * Null covers every failure alike — unknown, expired, revoked — so the page cannot be
     * used to tell whether a token ever existed. The view is counted and audited: she
     * can see on her phone that the link was opened, and when.
     */
    suspend fun open(token: String, language: Language?, ip: String?, userAgent: String?): DoctorSummary? {
        if (token.length !in TOKEN_LENGTH_RANGE) return null
        val record = shares.byTokenHash(sha256(token)) ?: return null
        val at = now()
        if (!record.share.isActive(at)) return null

        val user = users.findById(record.userId) ?: return null
        shares.recordView(Uuid.parse(record.share.id), at)
        audit.record(
            AuditEntry(
                actorType = ActorType.SYSTEM,
                actorId = record.userId,
                action = AuditActions.SHARE_VIEWED,
                entityType = "profile_share",
                entityId = record.share.id,
                ip = ip,
                userAgent = userAgent,
            ),
        )
        return summaryFor(user.id, language ?: user.language)
    }

    /** The same document she can download for herself — the data-export button. */
    suspend fun summaryFor(userId: Uuid, language: Language): DoctorSummary {
        val user = users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")
        val today = now().dayIn(user.timezone)
        val from = today.minus(DoctorSummary.SHARE_WINDOW_DAYS - 1, DateTimeUnit.DAY)
        val stage = users.stageBaselineOf(userId)

        val catalogue = health.symptomCatalogue(userId, lifeStage = null).associateBy { it.key }
        val logs = health.logs(userId, from, today).logs.sortedByDescending { it.date }
        val days = logs.filterNot { it.isEmpty }.map { log ->
            SharedDay(
                date = log.date,
                flow = log.flow,
                mood = log.mood,
                energy = log.energy,
                stress = log.stress,
                symptoms = log.symptoms.map { entry ->
                    SharedSymptom(entry.key, catalogue[entry.key]?.label ?: entry.key, entry.severity)
                },
                fetalMovement = log.fetalMovement,
            )
        }
        val symptomCounts = days.flatMap { day -> day.symptoms.map { it.key } }
            .groupingBy { it }
            .eachCount()
            .map { (key, count) ->
                val definition = catalogue[key]
                SharedSymptomCount(
                    key = key,
                    label = definition?.label ?: key,
                    category = definition?.category ?: uz.sadora.contract.SymptomCategory.OTHER,
                    days = count,
                )
            }
            .sortedByDescending { it.days }

        val cycle = if (user.lifeStage.predictsCycle) {
            val status = health.status(userId)
            SharedCycle(
                today = status.today,
                cycleDay = status.cycleDay,
                phase = status.phase,
                lastPeriodStart = status.lastPeriodStart,
                history = health.history(userId),
                periods = health.periods(userId).filter { it.startedOn >= from },
            )
        } else {
            null
        }

        val pregnancy = when (user.lifeStage) {
            LifeStage.PREGNANCY, LifeStage.POSTPARTUM -> SharedPregnancy(
                dueDate = stage?.dueDate,
                week = stage?.dueDate?.let { due -> ((PREGNANCY_DAYS - today.daysUntil(due)) / 7).coerceIn(1, 42) },
                childBirthDate = stage?.birthDate,
                lessMovementDays = days.filter { it.fetalMovement == FetalMovement.LESS }.map { it.date },
            )
            else -> null
        }

        val zone = TimeZone.of(user.timezone)
        val checkIns = logs.filter { it.mood != null || it.energy != null || it.stress != null }
        val practices = mind.recentPractices(userId, limit = 500)
            .filter { it.completedAt.toLocalDateTime(zone).date >= from }
        val mindSummary = SharedMind(
            windowDays = DoctorSummary.SHARE_WINDOW_DAYS,
            daysLogged = checkIns.size,
            averageMood = checkIns.mapNotNull { it.mood?.score }.averageOrNull(),
            averageEnergy = checkIns.mapNotNull { it.energy }.averageOrNull(),
            averageStress = checkIns.mapNotNull { it.stress }.averageOrNull(),
            journalEntries = mind.entriesBetween(userId, from, today).size,
            practiceMinutes = practices.sumOf { it.durationSeconds } / 60,
        )

        val courses = medications.list(userId, includeArchived = true)
            .filter { it.endedOn == null || it.endedOn!! >= from }
            .map { medication ->
                val history = medications.history(userId, Uuid.parse(medication.id), DoctorSummary.SHARE_WINDOW_DAYS)
                val decided = history.takenCount + history.skippedCount
                SharedMedication(
                    name = medication.name,
                    dosage = medication.dosage,
                    unit = medication.unit,
                    schedule = medication.schedule,
                    foodRelation = medication.foodRelation,
                    startedOn = medication.startedOn,
                    endedOn = medication.endedOn,
                    active = medication.active,
                    takenCount = history.takenCount,
                    skippedCount = history.skippedCount,
                    adherencePercent = if (decided > 0) history.takenCount * 100 / decided else null,
                )
            }

        val kcal = nutrition.kcalBetween(userId, from, today).filterValues { it > 0 }
        val water = nutrition.waterBetween(userId, from, today).filterValues { it > 0 }
        val nutritionSummary = SharedNutrition(
            windowDays = DoctorSummary.SHARE_WINDOW_DAYS,
            daysLogged = (kcal.keys + water.keys).size,
            averageKcal = kcal.values.averageOrNull()?.toInt(),
            averageWaterMl = water.values.averageOrNull()?.toInt(),
            goals = nutrition.goalsOf(userId),
        )

        val wearableDays = wearables.daily(userId, from, today).days.filter { it.metrics.isNotEmpty() }
        val wearable = if (wearableDays.isEmpty()) {
            null
        } else {
            SharedWearable(
                providers = wearableDays.flatMap { day -> day.metrics.flatMap { it.providers } }.distinct(),
                days = wearableDays.sortedByDescending { it.date },
                averages = averagesOf(wearableDays),
            )
        }

        return DoctorSummary(
            generatedAt = now(),
            language = language,
            person = SharedPerson(
                name = user.name,
                age = user.birthDate?.let { ageOn(it, today) },
                birthDate = user.birthDate,
                heightCm = user.heightCm,
                weightKg = user.weightKg,
                lifeStage = user.lifeStage,
                goals = users.goalsOf(userId),
                memberSince = user.createdAt.dayIn(user.timezone),
            ),
            cycle = cycle,
            pregnancy = pregnancy,
            days = days,
            symptomCounts = symptomCounts,
            mind = mindSummary,
            medications = courses,
            appointments = appointments.list(userId).filter { it.scheduledOn >= from },
            nutrition = nutritionSummary,
            wearable = wearable,
        )
    }

    private fun averagesOf(days: List<DailyHealth>): List<DailyMetric> =
        days.flatMap { it.metrics }
            .groupBy { it.metric }
            .map { (metric, values) ->
                DailyMetric(
                    metric = metric,
                    value = values.map { it.value }.average(),
                    unit = metric.canonicalUnit,
                    sampleCount = values.size,
                    providers = values.flatMap { it.providers }.distinct(),
                )
            }
            .sortedBy { HealthMetric.entries.indexOf(it.metric) }

    private fun Collection<Number>.averageOrNull(): Double? =
        takeIf { it.isNotEmpty() }?.map { it.toDouble() }?.average()

    private fun ageOn(birthDate: LocalDate, today: LocalDate): Int {
        var age = today.year - birthDate.year
        val todayMonth = today.month.ordinal
        val birthMonth = birthDate.month.ordinal
        if (todayMonth < birthMonth || (todayMonth == birthMonth && today.day < birthDate.day)) {
            age--
        }
        return age.coerceAtLeast(0)
    }

    private companion object {
        const val TOKEN_BYTES = 32
        /** 32 random bytes are 43 base64url characters; anything else is not ours. */
        val TOKEN_LENGTH_RANGE = 40..48
        const val PREGNANCY_DAYS = 280
    }
}

/** Kept here so the page and the export agree on one schedule wording. */
internal fun MedicationSchedule.describe(): String = buildString {
    append(times.joinToString(", ") { time -> "%02d:%02d".format(time.hour, time.minute) })
    if (weekdays.isNotEmpty()) append(" · ").append(weekdays.joinToString("/") { it.name.take(3).lowercase() })
    intervalDays?.let { append(" · ").append(it).append("d") }
}
