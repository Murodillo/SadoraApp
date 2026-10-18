package uz.sadora.server.condition

import kotlin.uuid.Uuid
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import uz.sadora.contract.ConditionReport
import uz.sadora.contract.ConditionStatus
import uz.sadora.contract.ConditionsState
import uz.sadora.contract.DailyHealth
import uz.sadora.contract.FeatureKeys
import uz.sadora.contract.HealthCondition
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.Limits
import uz.sadora.contract.PainLog
import uz.sadora.contract.PainSummary
import uz.sadora.contract.ReportCycle
import uz.sadora.contract.ReportMonth
import uz.sadora.contract.ReportSign
import uz.sadora.contract.ReportWeight
import uz.sadora.contract.SavePainLogRequest
import uz.sadora.contract.SetConditionRequest
import uz.sadora.contract.SharedConditions
import uz.sadora.server.core.ConsentRequiredException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.health.HealthAccess
import uz.sadora.server.health.HealthService
import uz.sadora.server.user.UserRecord

/**
 * PCOS and endometriosis: what she marked, her pain diary, and the report she takes to
 * her doctor.
 *
 * Marking a condition and keeping the diary are free; the report — months of cycles,
 * pain and signs side by side — is the Premium part. The doctor's QR page carries the
 * conditions and 90 days of pain for everyone, because that page is how she gets help.
 */
class ConditionService(
    private val conditions: ConditionRepository,
    private val access: HealthAccess,
    private val health: HealthService,
    /** Her weighings for the report; null in tests without wearables. */
    private val dailyHealth: (suspend (Uuid, LocalDate, LocalDate) -> List<DailyHealth>)? = null,
) {

    suspend fun state(userId: Uuid): ConditionsState {
        val user = access.requireUser(userId)
        return ConditionsState(
            conditions = conditions.conditionsOf(userId),
            catalog = HealthCondition.entries.map { ConditionWords.info(it, user.language) },
        )
    }

    suspend fun set(userId: Uuid, condition: HealthCondition, request: SetConditionRequest): ConditionsState {
        val user = requireConsent(userId)
        val diagnosedOn = request.diagnosedOn?.takeIf { request.status == ConditionStatus.DIAGNOSED }
        if (diagnosedOn != null && (diagnosedOn > now().dayIn(user.timezone) || diagnosedOn < EARLIEST)) {
            throw ValidationException("diagnosedOn", "Sana noto'g'ri")
        }
        conditions.set(userId, condition, request.status, diagnosedOn)
        return state(userId)
    }

    suspend fun remove(userId: Uuid, condition: HealthCondition): ConditionsState {
        access.requireUser(userId)
        conditions.remove(userId, condition)
        return state(userId)
    }

    // ---------------------------------------------------------------- the pain diary

    suspend fun pain(userId: Uuid, days: Int): List<PainLog> {
        val user = access.requireUser(userId)
        val today = now().dayIn(user.timezone)
        return conditions.pain(userId, today.minus(days.coerceIn(1, MAX_PAIN_DAYS) - 1, DateTimeUnit.DAY), today)
    }

    suspend fun savePain(userId: Uuid, day: LocalDate, request: SavePainLogRequest): PainLog {
        val user = requireConsent(userId)
        val today = now().dayIn(user.timezone)
        if (day > today || day < today.minus(MAX_PAIN_DAYS, DateTimeUnit.DAY)) throw ValidationException("date", "Sana noto'g'ri")
        if (request.level !in Limits.PAIN_LEVEL) {
            throw ValidationException("level", "${Limits.PAIN_LEVEL.first}–${Limits.PAIN_LEVEL.last} oralig'ida bo'lishi kerak")
        }
        val name = request.painkillerName?.trim()?.takeIf { it.isNotEmpty() && request.painkiller }
        if (name != null && name.length > Limits.PAINKILLER_NAME_MAX) {
            throw ValidationException("painkillerName", "Dori nomi eng ko'pi ${Limits.PAINKILLER_NAME_MAX} belgi bo'lsin")
        }
        val note = request.note?.trim()?.takeIf(String::isNotEmpty)
        if (note != null && note.length > Limits.PAIN_NOTE_MAX) {
            throw ValidationException("note", "Izoh eng ko'pi ${Limits.PAIN_NOTE_MAX} belgi bo'lsin")
        }
        conditions.savePain(userId, day, request.copy(painkillerName = name, note = note))
        return conditions.pain(userId, day, day).single()
    }

    suspend fun deletePain(userId: Uuid, day: LocalDate) {
        access.requireUser(userId)
        if (!conditions.deletePain(userId, day)) throw NotFoundException("Yozuv topilmadi")
    }

    // ---------------------------------------------------------------- the report

    /** Three or six months, month by month. Premium. */
    suspend fun report(userId: Uuid, months: Int): ConditionReport {
        val user = access.requireFeature(userId, FeatureKeys.CONDITION_REPORT)
        if (months !in Limits.CONDITION_REPORT_MONTHS) throw ValidationException("months", "Noto'g'ri format")
        val today = now().dayIn(user.timezone)
        val from = today.minus(months, DateTimeUnit.MONTH).plus(1, DateTimeUnit.DAY)

        val starts = health.periods(userId).sortedBy { it.startedOn }
        val cycles = starts.withIndex()
            .filter { (_, p) -> p.startedOn >= from }
            .map { (index, p) ->
                ReportCycle(
                    startedOn = p.startedOn,
                    length = starts.getOrNull(index + 1)?.let { next -> p.startedOn.daysUntil(next.startedOn) },
                    periodDays = p.endedOn?.let { p.startedOn.daysUntil(it) + 1 },
                )
            }

        val pain = conditions.pain(userId, from, today)
        val logs = health.logs(userId, from, today).logs
        val labels = health.symptomCatalogue(userId, lifeStage = null).associate { it.key to it.label }
        val signDays = logs.associate { log -> log.date to log.symptoms.map { it.key }.filter { it in SIGNS } }

        val monthly = generateSequence(LocalDate(from.year, from.month, 1)) { it.plus(1, DateTimeUnit.MONTH) }
            .takeWhile { it <= today }
            .map { first ->
                val inMonth = pain.filter { it.date.year == first.year && it.date.month == first.month }
                val hurting = inMonth.filter { it.level > 0 }
                ReportMonth(
                    month = "${first.year}-${(first.month.ordinal + 1).toString().padStart(2, '0')}",
                    painDays = hurting.size,
                    averagePain = hurting.takeIf { it.isNotEmpty() }?.map { it.level }?.average(),
                    maxPain = inMonth.maxOfOrNull { it.level } ?: 0,
                    missedDays = inMonth.count { it.missedActivities },
                    signDays = signDays.count { (day, keys) -> keys.isNotEmpty() && day.year == first.year && day.month == first.month },
                )
            }
            .toList()

        val weights = dailyHealth?.invoke(userId, from, today).orEmpty()
            .mapNotNull { day -> day.value(HealthMetric.WEIGHT)?.let { ReportWeight(day.date, it) } }
            .sortedBy { it.date }

        return ConditionReport(
            months = months,
            from = from,
            to = today,
            conditions = conditions.conditionsOf(userId),
            cycles = cycles,
            pain = summary(pain, from.daysUntil(today) + 1).takeIf { it.daysLogged > 0 },
            monthly = monthly,
            signs = SIGNS.map { key -> ReportSign(key, labels[key] ?: key, signDays.count { key in it.value }) }.filter { it.days > 0 },
            weights = weights,
        )
    }

    /** For the doctor's page: her conditions and 90 days of pain; null when she marked none. */
    suspend fun shared(userId: Uuid, today: LocalDate): SharedConditions? {
        val marked = conditions.conditionsOf(userId)
        if (marked.isEmpty()) return null
        val from = today.minus(SHARED_PAIN_DAYS - 1, DateTimeUnit.DAY)
        val pain = conditions.pain(userId, from, today)
        return SharedConditions(marked, summary(pain, SHARED_PAIN_DAYS).takeIf { it.daysLogged > 0 })
    }

    suspend fun hasPcos(userId: Uuid): Boolean = conditions.has(userId, HealthCondition.PCOS)

    private suspend fun requireConsent(userId: Uuid): UserRecord = access.requireUser(userId).also {
        if (!access.hasStorageConsent(userId)) throw ConsentRequiredException("store_health")
    }

    companion object {
        const val MAX_PAIN_DAYS = 400
        const val SHARED_PAIN_DAYS = 90
        val EARLIEST = LocalDate(1960, 1, 1)
        /** The PCOS signs the report counts: acne was already in the catalogue. */
        val SIGNS = listOf("acne", "excess_hair", "hair_loss")

        fun summary(logs: List<PainLog>, days: Int): PainSummary {
            val hurting = logs.filter { it.level > 0 }
            return PainSummary(
                days = days,
                daysLogged = logs.size,
                painDays = hurting.size,
                averageLevel = hurting.takeIf { it.isNotEmpty() }?.map { it.level }?.average(),
                maxLevel = logs.maxOfOrNull { it.level } ?: 0,
                painkillerDays = logs.count { it.painkiller },
                missedDays = logs.count { it.missedActivities },
                byKind = hurting.flatMap { it.kinds }.groupingBy { it }.eachCount(),
                byArea = hurting.flatMap { it.areas }.groupingBy { it }.eachCount(),
            )
        }
    }
}
