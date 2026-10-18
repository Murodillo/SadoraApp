package uz.sadora.server.wrapped

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import uz.sadora.contract.CycleHistoryEntry
import uz.sadora.contract.CyclePhase
import uz.sadora.contract.DailyHealth
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.PhaseMood
import uz.sadora.contract.Wrapped
import uz.sadora.contract.WrappedCycles
import uz.sadora.contract.WrappedHabits
import uz.sadora.contract.WrappedIndex
import uz.sadora.contract.WrappedKind
import uz.sadora.contract.WrappedPersona
import uz.sadora.contract.WrappedSummary
import uz.sadora.contract.WrappedSymptom
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.health.HealthAccess
import uz.sadora.server.health.HealthService
import uz.sadora.server.user.UserRecord

/**
 * Cycle Wrapped: a recap for each finished cycle and one for the year.
 *
 * Counted on read from what is already stored — logs, periods, the coin ledger, sessions —
 * so a period corrected later corrects every recap too. Free for everyone.
 */
class WrappedService(
    private val wrapped: WrappedRepository,
    private val access: HealthAccess,
    private val health: HealthService,
    /** Her nights, for the average sleep; null in tests without wearables. */
    private val daily: (suspend (Uuid, LocalDate, LocalDate) -> List<DailyHealth>)? = null,
) {

    suspend fun index(userId: Uuid): WrappedIndex {
        val user = access.requireUser(userId)
        val today = now().dayIn(user.timezone)
        val seen = wrapped.seen(userId)
        val items = ArrayList<WrappedSummary>()

        // The year's own recap opens on the first of December and stays fresh through January.
        val joined = user.createdAt.dayIn(user.timezone).year
        val latestYear = if (today.month == Month.DECEMBER) today.year else today.year - 1
        val freshYear = when (today.month) {
            Month.DECEMBER -> today.year
            Month.JANUARY -> today.year - 1
            else -> null
        }
        (latestYear downTo maxOf(joined, latestYear - MAX_YEARS + 1)).forEach { year ->
            val key = "year-$year"
            items += WrappedSummary(
                kind = WrappedKind.YEAR,
                key = key,
                from = LocalDate(year, 1, 1),
                to = minOf(LocalDate(year, 12, 31), today),
                fresh = year == freshYear && key !in seen,
            )
        }

        health.history(userId).cycles.take(MAX_CYCLES).forEachIndexed { i, cycle ->
            val key = "cycle-${cycle.startedOn}"
            val last = cycle.endedOn.minus(1, DateTimeUnit.DAY)
            items += WrappedSummary(
                kind = WrappedKind.CYCLE,
                key = key,
                from = cycle.startedOn,
                to = last,
                fresh = i == 0 && last.daysUntil(today) <= FRESH_DAYS && key !in seen,
            )
        }
        return WrappedIndex(items)
    }

    suspend fun get(userId: Uuid, key: String): Wrapped {
        if (!KEY.matches(key)) throw ValidationException("key", "Xulosa topilmadi")
        val user = access.requireUser(userId)
        val today = now().dayIn(user.timezone)
        val history = health.history(userId).cycles
        return if (key.startsWith("cycle-")) {
            val start = runCatching { LocalDate.parse(key.removePrefix("cycle-")) }.getOrNull()
            val index = history.indexOfFirst { it.startedOn == start }
            if (index < 0) throw NotFoundException("Xulosa topilmadi")
            cycleRecap(user, key, history, index)
        } else {
            val year = key.removePrefix("year-").toInt()
            if (year > today.year || year < MIN_YEAR) throw NotFoundException("Xulosa topilmadi")
            yearRecap(user, key, history, year, today)
        }
    }

    suspend fun markSeen(userId: Uuid, key: String) {
        if (!KEY.matches(key)) throw ValidationException("key", "Xulosa topilmadi")
        access.requireUser(userId)
        wrapped.markSeen(userId, key)
    }

    // ---------------------------------------------------------------- recaps

    private suspend fun cycleRecap(user: UserRecord, key: String, history: List<CycleHistoryEntry>, index: Int): Wrapped {
        val cycle = history[index]
        val earlier = history.drop(index + 1).take(FORECAST_CYCLES)
        val usual = earlier.takeIf { it.isNotEmpty() }?.map { it.cycleLength }?.average()?.roundToInt()
        val from = cycle.startedOn
        val to = cycle.endedOn.minus(1, DateTimeUnit.DAY)
        val days = Days(this, user, from, to)
        val phases = Phases(listOf(Phases.Segment(cycle.startedOn, cycle.cycleLength, cycle.periodLength ?: DEFAULT_PERIOD)))
        val parts = days.load(phases)
        return Wrapped(
            kind = WrappedKind.CYCLE,
            key = key,
            from = from,
            to = to,
            cycleLength = cycle.cycleLength,
            periodLength = cycle.periodLength,
            usualLength = usual,
            forecastErrorDays = usual?.let { cycle.cycleLength - it },
            topSymptoms = parts.symptoms,
            moodByPhase = parts.moodByPhase,
            averageMood = parts.averageMood,
            bestMoodDay = parts.bestMoodDay,
            habits = parts.habits,
            persona = persona(
                days = from.daysUntil(to) + 1,
                habits = parts.habits,
                moodDays = parts.moodDays,
                averageMood = parts.averageMood,
                variation = usual?.let { abs(cycle.cycleLength - it) },
            ),
        )
    }

    private suspend fun yearRecap(user: UserRecord, key: String, history: List<CycleHistoryEntry>, year: Int, today: LocalDate): Wrapped {
        val from = LocalDate(year, 1, 1)
        val to = minOf(LocalDate(year, 12, 31), today)
        val inYear = history.withIndex().filter { (_, c) -> c.startedOn in from..to }
        val lengths = inYear.map { it.value.cycleLength }
        val errors = inYear.mapNotNull { (i, c) ->
            val earlier = history.drop(i + 1).take(FORECAST_CYCLES)
            if (earlier.isEmpty()) null else abs(c.cycleLength - earlier.map { it.cycleLength }.average())
        }
        val cycles = WrappedCycles(
            count = lengths.size,
            averageLength = lengths.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            averagePeriod = inYear.mapNotNull { it.value.periodLength }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            shortest = lengths.minOrNull(),
            longest = lengths.maxOrNull(),
            forecastErrorDays = errors.takeIf { it.isNotEmpty() }?.average()?.let { (it * 10).roundToInt() / 10.0 },
            lengths = inYear.reversed().map { it.value.cycleLength },
        )
        // The phases of every day of the year: each finished cycle, then the open one at her usual length.
        val periods = health.periods(user.id).sortedBy { it.startedOn }
        val usual = history.map { it.cycleLength }.takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: DEFAULT_CYCLE
        val segments = periods.mapIndexed { i, p ->
            val next = periods.getOrNull(i + 1)?.startedOn
            Phases.Segment(
                start = p.startedOn,
                length = next?.let { p.startedOn.daysUntil(it) } ?: usual,
                period = p.endedOn?.let { p.startedOn.daysUntil(it) + 1 } ?: DEFAULT_PERIOD,
            )
        }
        val parts = Days(this, user, from, to).load(Phases(segments))
        return Wrapped(
            kind = WrappedKind.YEAR,
            key = key,
            from = from,
            to = to,
            cycles = cycles,
            topSymptoms = parts.symptoms,
            moodByPhase = parts.moodByPhase,
            averageMood = parts.averageMood,
            bestMoodDay = parts.bestMoodDay,
            bestMonth = parts.bestMonth,
            habits = parts.habits,
            persona = persona(
                days = from.daysUntil(to) + 1,
                habits = parts.habits,
                moodDays = parts.moodDays,
                averageMood = parts.averageMood,
                variation = if (lengths.size >= 2) lengths.max() - lengths.min() else null,
            ),
        )
    }

    /** The logs, moods and habit counts of one window. */
    private class Days(val service: WrappedService, val user: UserRecord, val from: LocalDate, val to: LocalDate) {
        class Parts(
            val symptoms: List<WrappedSymptom>,
            val moodByPhase: List<PhaseMood>,
            val averageMood: Double?,
            val moodDays: Int,
            val bestMoodDay: LocalDate?,
            val bestMonth: Int?,
            val habits: WrappedHabits,
        )

        suspend fun load(phases: Phases): Parts {
            val logs = service.health.logs(user.id, from, to).logs.filterNot { it.isEmpty }
            val labels = service.health.symptomLabels(user.language).associateBy { it.key }
            val symptoms = logs.flatMap { log -> log.symptoms.map { it.key } }
                .groupingBy { it }.eachCount()
                .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(TOP_SYMPTOMS)
                .map { (key, n) -> WrappedSymptom(key, labels[key]?.label ?: key, n) }
            val moods = logs.mapNotNull { log -> log.mood?.let { log.date to it.score } }
            val byPhase = moods.mapNotNull { (date, score) -> phases.of(date)?.let { it to score } }
                .groupBy({ it.first }, { it.second })
            val moodByPhase = CyclePhase.entries.mapNotNull { phase ->
                byPhase[phase]?.let { PhaseMood(phase, one(it.average()), it.size) }
            }
            val bestMonth = moods.groupBy { it.first.month.ordinal + 1 }
                .filterValues { it.size >= MONTH_MIN_DAYS }
                .maxByOrNull { (_, v) -> v.map { it.second }.average() }?.key
            val counts = service.wrapped.counts(user.id, from, to)
            val sleep = runCatching { service.daily?.invoke(user.id, from, to) }.getOrNull()
                ?.mapNotNull { it.value(HealthMetric.SLEEP_DURATION)?.takeIf { v -> v > 0 } }
                ?.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
            return Parts(
                symptoms = symptoms,
                moodByPhase = moodByPhase,
                averageMood = moods.takeIf { it.isNotEmpty() }?.map { it.second }?.average()?.let(::one),
                moodDays = moods.size,
                bestMoodDay = moods.maxByOrNull { it.second }?.first,
                bestMonth = bestMonth,
                habits = WrappedHabits(
                    daysWithApp = counts.openedOn.size,
                    longestRun = longestRun(counts.openedOn),
                    loggedDays = logs.size,
                    waterGoalDays = counts.waterGoalDays,
                    waterLitres = counts.waterLitres,
                    sleepAverageMinutes = sleep,
                    kegelSessions = counts.kegelSessions,
                    practiceMinutes = counts.practiceMinutes,
                    journalEntries = counts.journalEntries,
                    mealsLogged = counts.mealsLogged,
                    gulEarned = counts.gulEarned,
                    badges = counts.badges,
                ),
            )
        }
    }

    /** Which phase a day fell in, from the cycles around it. Beyond the last known cycle: none. */
    class Phases(private val segments: List<Segment>) {
        data class Segment(val start: LocalDate, val length: Int, val period: Int)

        fun of(date: LocalDate): CyclePhase? {
            val segment = segments.lastOrNull { it.start <= date } ?: return null
            val day = segment.start.daysUntil(date) + 1
            if (day > segment.length) return null
            val ovulation = segment.length - LUTEAL_DAYS
            return when {
                day <= segment.period -> CyclePhase.PERIOD
                day in (ovulation - 5)..(ovulation + 1) -> CyclePhase.FERTILE
                day < ovulation - 5 -> CyclePhase.FOLLICULAR
                else -> CyclePhase.LUTEAL
            }
        }
    }

    companion object {
        val KEY = Regex("""^(cycle-\d{4}-\d{2}-\d{2}|year-\d{4})$""")
        const val MAX_YEARS = 3
        const val MAX_CYCLES = 6
        const val FRESH_DAYS = 14
        const val FORECAST_CYCLES = 6
        const val TOP_SYMPTOMS = 3
        const val MONTH_MIN_DAYS = 3
        const val LUTEAL_DAYS = 14
        const val DEFAULT_CYCLE = 28
        const val DEFAULT_PERIOD = 5
        const val MIN_YEAR = 2020

        private fun one(value: Double) = (value * 10).roundToInt() / 10.0

        /** The longest run of consecutive days in [days]. */
        fun longestRun(days: List<LocalDate>): Int {
            var best = 0
            var run = 0
            var previous: LocalDate? = null
            days.sorted().forEach { day ->
                run = if (previous?.plus(1, DateTimeUnit.DAY) == day) run + 1 else 1
                best = maxOf(best, run)
                previous = day
            }
            return best
        }

        /**
         * The playful flower. Checked in order, so a woman who logged nearly every day is the
         * rose whatever else she did; nothing here reads a number her body produced as good or bad
         * except the steady-and-content lotus, which needs both.
         */
        fun persona(days: Int, habits: WrappedHabits, moodDays: Int, averageMood: Double?, variation: Int?): WrappedPersona {
            val d = days.coerceAtLeast(1)
            return when {
                habits.loggedDays >= d * 0.6 -> WrappedPersona.ATIRGUL
                habits.kegelSessions + habits.practiceMinutes / 5 + habits.waterGoalDays >= d * 0.5 -> WrappedPersona.LOLA
                variation != null && variation <= 2 && (averageMood ?: 0.0) >= 3.5 -> WrappedPersona.NILUFAR
                habits.journalEntries >= 3 || moodDays >= d * 0.3 -> WrappedPersona.BINAFSHA
                (habits.sleepAverageMinutes ?: 0) >= 420 || habits.practiceMinutes >= 30 -> WrappedPersona.MOYCHECHAK
                else -> WrappedPersona.YASMIN
            }
        }
    }
}
