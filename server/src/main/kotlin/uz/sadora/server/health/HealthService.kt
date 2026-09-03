package uz.sadora.server.health

import kotlin.uuid.Uuid
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import uz.sadora.contract.CycleCalendar
import uz.sadora.contract.CycleDay
import uz.sadora.contract.CycleHistory
import uz.sadora.contract.CycleHistoryEntry
import uz.sadora.contract.CyclePrediction
import uz.sadora.contract.CycleStatus
import uz.sadora.contract.DailyLog
import uz.sadora.contract.DailyLogRange
import uz.sadora.contract.FeatureKeys
import uz.sadora.contract.LifeStage
import uz.sadora.contract.LogPeriodRequest
import uz.sadora.contract.PeriodEntry
import uz.sadora.contract.SaveDailyLogRequest
import uz.sadora.contract.SymptomDefinition
import uz.sadora.contract.UpdatePeriodRequest
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.user.UserRecord

/**
 * Cycle and daily logging.
 *
 * Two gates sit in front of every write. The user must have granted `store_health` —
 * recorded at onboarding and revocable from the privacy screen — and the feature must be
 * switched on for her tier. Reads are not gated on consent: withdrawing permission to
 * store data does not withdraw her right to see what is already there.
 */
class HealthService(
    private val repository: HealthRepository,
    private val access: HealthAccess,
) {
    // ---------------------------------------------------------------- reads

    suspend fun status(userId: Uuid): CycleStatus {
        val user = access.requireUser(userId)
        val today = now().dayIn(user.timezone)
        val periods = repository.periodsOf(userId)
        val prediction = predictionFor(user, periods, today)

        val currentPeriod = periods.lastOrNull { it.endedOn == null && it.startedOn <= today }
        val phase = CyclePredictor.phaseOn(today, periods, prediction, today)

        return CycleStatus(
            cycleDay = CyclePredictor.cycleDayOn(today, periods, prediction),
            phase = phase?.first,
            phaseIsPredicted = phase?.second ?: false,
            currentPeriod = currentPeriod?.toEntry(),
            lastPeriodStart = periods.maxOfOrNull { it.startedOn },
            daysUntilNextPeriod = prediction.nextPeriodStart?.let { today.daysUntil(it) },
            prediction = prediction,
            today = today,
        )
    }

    suspend fun calendar(userId: Uuid, from: LocalDate, to: LocalDate): CycleCalendar {
        val user = access.requireUser(userId)
        if (from > to) throw ValidationException("from", "Boshlanish sanasi tugashdan keyin bo'lishi mumkin emas")
        val span = from.daysUntil(to)
        if (span > MAX_CALENDAR_DAYS) {
            throw ValidationException("to", "Eng ko'pi $MAX_CALENDAR_DAYS kunlik oraliq")
        }

        val today = now().dayIn(user.timezone)
        val periods = repository.periodsOf(userId)
        val prediction = predictionFor(user, periods, today)
        val logs = repository.logsBetween(userId, from, to).associateBy { it.date }

        val days = (0..span).map { offset ->
            val date = from.plus(offset, DateTimeUnit.DAY)
            val phase = CyclePredictor.phaseOn(date, periods, prediction, today)
            val log = logs[date]
            // A day she recorded bleeding on is a recorded period day, even if she never
            // opened a period entry for it — the calendar draws it filled rather than
            // outlined, because it is her own observation and not our inference. Flow
            // outside a period phase (spotting mid-cycle) does not make the phase a fact.
            val recordedByFlow = log?.flow != null && phase?.first == uz.sadora.contract.CyclePhase.PERIOD
            CycleDay(
                date = date,
                phase = phase?.first,
                isPredicted = if (recordedByFlow) false else phase?.second ?: true,
                flow = log?.flow,
                hasNote = !log?.note.isNullOrBlank(),
                symptomCount = log?.symptoms?.size ?: 0,
            )
        }
        return CycleCalendar(from = from, to = to, days = days, prediction = prediction)
    }

    suspend fun history(userId: Uuid): CycleHistory {
        val user = access.requireUser(userId)
        val today = now().dayIn(user.timezone)
        val periods = repository.periodsOf(userId)
        val prediction = predictionFor(user, periods, today)

        // A cycle runs from one period start to the next, so the most recent period has
        // not completed a cycle yet and is deliberately absent from the history.
        val cycles = periods.zipWithNext { earlier, later ->
            CycleHistoryEntry(
                startedOn = earlier.startedOn,
                endedOn = later.startedOn,
                cycleLength = earlier.startedOn.daysUntil(later.startedOn),
                periodLength = earlier.endedOn?.let { earlier.startedOn.daysUntil(it) + 1 },
            )
        }

        val lengths = cycles.map { it.cycleLength }
        return CycleHistory(
            cycles = cycles.reversed(),
            averageCycleLength = prediction.averageCycleLength,
            averagePeriodLength = prediction.averagePeriodLength,
            shortestCycle = lengths.minOrNull(),
            longestCycle = lengths.maxOrNull(),
            prediction = prediction,
        )
    }

    suspend fun periods(userId: Uuid): List<PeriodEntry> =
        repository.periodsOf(userId).map { it.toEntry() }.sortedByDescending { it.startedOn }

    suspend fun log(userId: Uuid, date: LocalDate): DailyLog =
        repository.logOn(userId, date) ?: DailyLog(date = date)

    suspend fun logs(userId: Uuid, from: LocalDate, to: LocalDate): DailyLogRange {
        if (from > to) throw ValidationException("from", "Boshlanish sanasi tugashdan keyin bo'lishi mumkin emas")
        if (from.daysUntil(to) > MAX_CALENDAR_DAYS) {
            throw ValidationException("to", "Eng ko'pi $MAX_CALENDAR_DAYS kunlik oraliq")
        }
        return DailyLogRange(from, to, repository.logsBetween(userId, from, to))
    }

    suspend fun symptomCatalogue(userId: Uuid, lifeStage: LifeStage?): List<SymptomDefinition> {
        val stage = lifeStage ?: access.requireUser(userId).lifeStage
        return repository.symptomCatalogue(stage)
    }

    // ---------------------------------------------------------------- writes

    suspend fun addPeriod(userId: Uuid, request: LogPeriodRequest): PeriodEntry {
        val user = access.requireWritable(userId, FeatureKeys.CYCLE_PREDICTION)
        val today = now().dayIn(user.timezone)
        validatePeriod(request.startedOn, request.endedOn, today)

        val id = repository.addPeriod(userId, request.startedOn, request.endedOn)
        return repository.periodById(userId, id)?.toEntry()
            ?: throw NotFoundException("Hayz yozuvi topilmadi")
    }

    suspend fun updatePeriod(userId: Uuid, id: Uuid, request: UpdatePeriodRequest): PeriodEntry {
        val user = access.requireWritable(userId, FeatureKeys.CYCLE_PREDICTION)
        val today = now().dayIn(user.timezone)
        val existing = repository.periodById(userId, id)
            ?: throw NotFoundException("Hayz yozuvi topilmadi")

        val startedOn = request.startedOn ?: existing.startedOn
        val endedOn = if (request.clearEnd) null else request.endedOn ?: existing.endedOn
        validatePeriod(startedOn, endedOn, today)

        repository.updatePeriod(userId, id, request.startedOn, request.endedOn, request.clearEnd)
        return repository.periodById(userId, id)?.toEntry()
            ?: throw NotFoundException("Hayz yozuvi topilmadi")
    }

    suspend fun deletePeriod(userId: Uuid, id: Uuid) {
        access.requireWritable(userId, FeatureKeys.CYCLE_PREDICTION)
        if (!repository.deletePeriod(userId, id)) throw NotFoundException("Hayz yozuvi topilmadi")
    }

    suspend fun saveLog(userId: Uuid, date: LocalDate, request: SaveDailyLogRequest): DailyLog {
        val user = access.requireWritable(userId, FeatureKeys.CYCLE_PREDICTION)
        val today = now().dayIn(user.timezone)
        if (date > today) throw ValidationException("date", "Kelajakdagi kun uchun yozuv qo'shib bo'lmaydi")

        request.energy?.let {
            if (it !in 1..5) throw ValidationException("energy", "1–5 oralig'ida bo'lishi kerak")
        }
        request.note?.let {
            if (it.length > MAX_NOTE_LENGTH) {
                throw ValidationException("note", "Eng ko'pi $MAX_NOTE_LENGTH belgi")
            }
        }

        val known = repository.knownSymptomKeys()
        val unknown = request.symptoms.map { it.key }.filterNot { it in known }
        if (unknown.isNotEmpty()) {
            throw ValidationException("symptoms", "Noma'lum simptom: ${unknown.joinToString()}")
        }

        // An empty day is a removal, not a row full of nulls.
        if (request.toDailyLog(date).isEmpty) {
            repository.deleteLog(userId, date)
            return DailyLog(date = date)
        }

        repository.saveLog(userId, date, request)
        return repository.logOn(userId, date) ?: DailyLog(date = date)
    }

    suspend fun deleteLog(userId: Uuid, date: LocalDate) {
        access.requireWritable(userId, FeatureKeys.CYCLE_PREDICTION)
        repository.deleteLog(userId, date)
    }

    // ---------------------------------------------------------------- plumbing

    private suspend fun predictionFor(
        user: UserRecord,
        periods: List<RecordedPeriod>,
        today: LocalDate,
    ): CyclePrediction = CyclePredictor.predict(
        lifeStage = user.lifeStage,
        periods = periods,
        baseline = repository.baselineOf(user.id),
        today = today,
    )

    private fun validatePeriod(startedOn: LocalDate, endedOn: LocalDate?, today: LocalDate) {
        if (startedOn > today) {
            throw ValidationException("startedOn", "Kelajakdagi sana bo'lishi mumkin emas")
        }
        if (endedOn != null) {
            if (endedOn < startedOn) {
                throw ValidationException("endedOn", "Boshlanishdan oldin tugashi mumkin emas")
            }
            if (endedOn > today) {
                throw ValidationException("endedOn", "Kelajakdagi sana bo'lishi mumkin emas")
            }
            if (startedOn.daysUntil(endedOn) + 1 > MAX_PERIOD_DAYS) {
                throw ValidationException("endedOn", "Eng ko'pi $MAX_PERIOD_DAYS kun")
            }
        }
    }

    private fun RecordedPeriod.toEntry() = PeriodEntry(
        id = id.toString(),
        startedOn = startedOn,
        endedOn = endedOn,
        createdAt = createdAt,
    )

    private fun SaveDailyLogRequest.toDailyLog(date: LocalDate) = DailyLog(
        date = date,
        flow = flow,
        mood = mood,
        energy = energy,
        symptoms = symptoms,
        note = note,
    )

    private companion object {
        const val MAX_CALENDAR_DAYS = 400
        const val MAX_PERIOD_DAYS = 15
        const val MAX_NOTE_LENGTH = 1000
    }
}
