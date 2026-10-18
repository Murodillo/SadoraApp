package uz.sadora.server.health

import kotlin.uuid.Uuid
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import uz.sadora.contract.CyclePhase
import uz.sadora.contract.CyclePrediction
import uz.sadora.contract.LifeStage
import uz.sadora.contract.PredictionConfidence
import uz.sadora.contract.PredictionReasons

data class RecordedPeriod(
    val id: Uuid,
    val startedOn: LocalDate,
    val endedOn: LocalDate?,
    /** Only carried so the API can report it; the forecast never looks at it. */
    val createdAt: kotlin.time.Instant = kotlin.time.Instant.DISTANT_PAST,
)

/** The onboarding answers. Used when there is not enough recorded history yet. */
data class CycleBaselineData(
    val lastPeriodStart: LocalDate?,
    val averageCycleLength: Int,
    val averagePeriodLength: Int,
    val isRegular: Boolean,
)

/**
 * Turns recorded periods into a forecast.
 *
 * Pure and deterministic — no clock, no database — because this is the one piece of the
 * product whose mistakes are invisible: a wrong prediction still looks like a prediction.
 * `today` is passed in so every case can be tested exactly.
 *
 * Two rules from the specification are enforced here rather than in the UI. Stages that
 * do not predict a cycle get no forecast at all, not a disabled one. And "not enough
 * data" is a real answer: with one logged period and no baseline the honest output is
 * [PredictionConfidence.NONE], not twenty-eight days invented on the user's behalf.
 */
object CyclePredictor {

    /** Cycles outside this range are almost always a missed log, not a real cycle. */
    private val PLAUSIBLE_CYCLE = 15..60
    private const val MAX_CYCLES_CONSIDERED = 6

    /** Spread across the observed cycles beyond which the user counts as irregular. */
    private const val IRREGULAR_SPREAD_DAYS = 8

    /** Each side of the date when the cycle is irregular but its spread not yet seen. */
    private const val UNKNOWN_SPREAD_DAYS = 7

    /** What a gynaecologist calls a long cycle, and how long without a period is worth a visit. */
    private const val LONG_CYCLE_DAYS = 35
    private const val NO_PERIOD_DAYS = 90

    /** The luteal phase is the stable half, so ovulation is counted back from the next period. */
    private const val LUTEAL_PHASE_DAYS = 14
    private const val FERTILE_DAYS_BEFORE_OVULATION = 5
    private const val FERTILE_DAYS_AFTER_OVULATION = 1

    /** Rolling a stale anchor forward this many cycles or more makes the estimate weak. */
    private const val STALE_AFTER_CYCLES = 2

    fun predict(
        lifeStage: LifeStage,
        periods: List<RecordedPeriod>,
        baseline: CycleBaselineData?,
        today: LocalDate,
        /**
         * A condition that makes cycles unpredictable (PCOS): the forecast is a range even
         * before her own history shows the spread.
         */
        irregularByCondition: Boolean = false,
    ): CyclePrediction {
        if (!lifeStage.predictsCycle) {
            return CyclePrediction(
                confidence = PredictionConfidence.NONE,
                reason = PredictionReasons.STAGE_DOES_NOT_PREDICT,
            )
        }

        val starts = periods.map { it.startedOn }.distinct().sorted()
        val observed = observedCycleLengths(starts)
        val anchor = starts.lastOrNull() ?: baseline?.lastPeriodStart

        if (anchor == null) {
            return CyclePrediction(
                confidence = PredictionConfidence.NONE,
                reason = PredictionReasons.NO_DATA,
            )
        }

        val cycleLength = observed.averageOrNull() ?: baseline?.averageCycleLength
        if (cycleLength == null) {
            // An anchor with no idea how long a cycle runs is not a forecast.
            return CyclePrediction(
                confidence = PredictionConfidence.NONE,
                reason = PredictionReasons.NO_DATA,
            )
        }

        val periodLength = recordedPeriodLengths(periods).averageOrNull()
            ?: baseline?.averagePeriodLength
            ?: DEFAULT_PERIOD_LENGTH

        val spread = if (observed.size >= 2) observed.max() - observed.min() else null
        val (nextStart, cyclesRolled) = rollForward(anchor, cycleLength, today)

        var confidence = confidenceFor(observed.size, spread, baseline)
        var reason = reasonFor(observed.size, spread, baseline)

        // A user who stopped logging months ago gets a date, but not a confident one.
        if (cyclesRolled >= STALE_AFTER_CYCLES && confidence > PredictionConfidence.LOW) {
            confidence = PredictionConfidence.LOW
            reason = PredictionReasons.FEW_CYCLES
        }

        val ovulation = nextStart.minus(LUTEAL_PHASE_DAYS, DateTimeUnit.DAY)

        // An irregular cycle gets the window her own shortest and longest cycles allow; one
        // with too little history to show a spread, but known to be irregular, a fortnight.
        val window = when {
            spread != null && spread >= IRREGULAR_SPREAD_DAYS ->
                nextStart.minus(cycleLength - observed.min(), DateTimeUnit.DAY) to
                    nextStart.plus(observed.max() - cycleLength, DateTimeUnit.DAY)
            irregularByCondition || (observed.size < 2 && baseline?.isRegular == false) ->
                nextStart.minus(UNKNOWN_SPREAD_DAYS, DateTimeUnit.DAY) to nextStart.plus(UNKNOWN_SPREAD_DAYS, DateTimeUnit.DAY)
            else -> null
        }?.let { (earliest, latest) -> maxOf(earliest, today) to maxOf(latest, today) }

        return CyclePrediction(
            confidence = confidence,
            reason = reason,
            nextPeriodStart = nextStart,
            nextPeriodEnd = nextStart.plus(periodLength - 1, DateTimeUnit.DAY),
            ovulationOn = ovulation,
            fertileFrom = ovulation.minus(FERTILE_DAYS_BEFORE_OVULATION, DateTimeUnit.DAY),
            fertileUntil = ovulation.plus(FERTILE_DAYS_AFTER_OVULATION, DateTimeUnit.DAY),
            averageCycleLength = cycleLength,
            averagePeriodLength = periodLength,
            variationDays = spread,
            basedOnCycles = observed.size,
            nextPeriodEarliest = window?.first,
            nextPeriodLatest = window?.second,
        )
    }

    /**
     * Whether the cycle is worth a doctor's look: no period for 90 days, or cycles that run
     * longer than 35 days on average. Null for every stage without a cycle, and while a
     * period is under way.
     */
    fun attention(
        lifeStage: LifeStage,
        periods: List<RecordedPeriod>,
        prediction: CyclePrediction,
        today: LocalDate,
    ): uz.sadora.contract.CycleAttention? {
        if (!lifeStage.predictsCycle) return null
        if (periods.any { it.endedOn == null && it.startedOn <= today }) return null
        val last = periods.maxOfOrNull { it.startedOn } ?: return null
        return when {
            last.daysUntil(today) >= NO_PERIOD_DAYS -> uz.sadora.contract.CycleAttention.NO_PERIOD_90
            prediction.basedOnCycles >= 2 && (prediction.averageCycleLength ?: 0) > LONG_CYCLE_DAYS ->
                uz.sadora.contract.CycleAttention.LONG_CYCLES
            else -> null
        }
    }

    /** Gaps between consecutive period starts, with implausible ones dropped. */
    fun observedCycleLengths(sortedStarts: List<LocalDate>): List<Int> =
        sortedStarts
            .zipWithNext { earlier, later -> earlier.daysUntil(later) }
            .filter { it in PLAUSIBLE_CYCLE }
            .takeLast(MAX_CYCLES_CONSIDERED)

    fun recordedPeriodLengths(periods: List<RecordedPeriod>): List<Int> =
        periods
            .mapNotNull { period -> period.endedOn?.let { period.startedOn.daysUntil(it) + 1 } }
            .filter { it in 1..15 }
            .takeLast(MAX_CYCLES_CONSIDERED)

    /**
     * The phase a date falls in, and whether that is recorded fact or inference.
     *
     * Only days covered by a period the user actually logged count as recorded. The
     * calendar draws those filled and everything else outlined, so this distinction is
     * what keeps colour from being the only indicator.
     */
    fun phaseOn(
        date: LocalDate,
        periods: List<RecordedPeriod>,
        prediction: CyclePrediction,
        today: LocalDate,
    ): Pair<CyclePhase, Boolean>? {
        periods.firstOrNull { it.covers(date, today) }?.let { return CyclePhase.PERIOD to false }
        if (!prediction.hasPrediction) return null

        val cycleLength = prediction.averageCycleLength ?: return null
        val periodLength = prediction.averagePeriodLength ?: DEFAULT_PERIOD_LENGTH
        val cycleStart = cycleStartFor(date, periods, prediction) ?: return null

        val dayInCycle = cycleStart.daysUntil(date) + 1
        val ovulationDay = cycleLength - LUTEAL_PHASE_DAYS
        val fertileFrom = ovulationDay - FERTILE_DAYS_BEFORE_OVULATION
        val fertileUntil = ovulationDay + FERTILE_DAYS_AFTER_OVULATION

        val phase = when {
            dayInCycle <= periodLength -> CyclePhase.PERIOD
            dayInCycle in fertileFrom..fertileUntil -> CyclePhase.FERTILE
            dayInCycle < fertileFrom -> CyclePhase.FOLLICULAR
            else -> CyclePhase.LUTEAL
        }
        return phase to true
    }

    /**
     * Which cycle a date belongs to.
     *
     * A logged period start anchors its own cycle. Once a date is a full cycle past the
     * last one logged, the anchor moves to the predicted grid instead — otherwise the
     * cycle day and the phase disagree, and the app ends up saying "day 31" beside a
     * period that its own forecast says started two days ago.
     */
    fun cycleStartFor(
        date: LocalDate,
        periods: List<RecordedPeriod>,
        prediction: CyclePrediction,
    ): LocalDate? {
        val cycleLength = prediction.averageCycleLength ?: return null

        val recorded = periods.map { it.startedOn }.filter { it <= date }.maxOrNull()
        if (recorded != null && recorded.daysUntil(date) < cycleLength) return recorded

        val nextStart = prediction.nextPeriodStart ?: return null
        var cycleStart = nextStart
        while (date < cycleStart) cycleStart = cycleStart.minus(cycleLength, DateTimeUnit.DAY)
        while (date >= cycleStart.plus(cycleLength, DateTimeUnit.DAY)) {
            cycleStart = cycleStart.plus(cycleLength, DateTimeUnit.DAY)
        }
        return cycleStart
    }

    /** Day 1 is the first day of the cycle [cycleStartFor] puts this date in. */
    fun cycleDayOn(date: LocalDate, periods: List<RecordedPeriod>, prediction: CyclePrediction): Int? =
        cycleStartFor(date, periods, prediction)?.let { it.daysUntil(date) + 1 }

    /**
     * An ongoing period runs to today, but no further and not forever.
     *
     * A user who starts a period and never closes it is common, and without the cap the
     * open row would paint every day since as bleeding — the calendar would fill red and
     * the phase would never advance. Ten days is past the top of the normal range, so
     * capping there loses nothing real.
     */
    private fun RecordedPeriod.covers(date: LocalDate, today: LocalDate): Boolean {
        if (date < startedOn) return false
        val last = endedOn ?: minOf(today, startedOn.plus(MAX_OPEN_PERIOD_DAYS - 1, DateTimeUnit.DAY))
        return date <= last
    }

    private fun rollForward(anchor: LocalDate, cycleLength: Int, today: LocalDate): Pair<LocalDate, Int> {
        var next = anchor.plus(cycleLength, DateTimeUnit.DAY)
        var rolled = 0
        while (next < today) {
            next = next.plus(cycleLength, DateTimeUnit.DAY)
            rolled++
        }
        return next to rolled
    }

    private fun confidenceFor(
        cycleCount: Int,
        spread: Int?,
        baseline: CycleBaselineData?,
    ): PredictionConfidence = when {
        cycleCount == 0 -> if (baseline != null) PredictionConfidence.LOW else PredictionConfidence.NONE
        spread != null && spread > IRREGULAR_SPREAD_DAYS ->
            if (cycleCount >= 3) PredictionConfidence.MEDIUM else PredictionConfidence.LOW

        cycleCount >= 3 -> PredictionConfidence.HIGH
        else -> PredictionConfidence.MEDIUM
    }

    private fun reasonFor(cycleCount: Int, spread: Int?, baseline: CycleBaselineData?): String = when {
        cycleCount == 0 -> if (baseline != null) PredictionReasons.ONLY_BASELINE else PredictionReasons.NO_DATA
        spread != null && spread > IRREGULAR_SPREAD_DAYS -> PredictionReasons.IRREGULAR
        cycleCount >= 3 -> PredictionReasons.SUFFICIENT
        else -> PredictionReasons.FEW_CYCLES
    }

    private fun List<Int>.averageOrNull(): Int? =
        if (isEmpty()) null else (sum().toDouble() / size).roundHalfUp()

    private fun Double.roundHalfUp(): Int = kotlin.math.floor(this + 0.5).toInt()

    private const val DEFAULT_PERIOD_LENGTH = 5
    private const val MAX_OPEN_PERIOD_DAYS = 10
}
