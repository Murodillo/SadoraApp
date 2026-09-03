package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The four phases the calendar colours. Mirrors `CyclePhase` in the app. */
@Serializable
enum class CyclePhase {
    @SerialName("period") PERIOD,
    @SerialName("follicular") FOLLICULAR,
    @SerialName("fertile") FERTILE,
    @SerialName("luteal") LUTEAL,
}

/** How much bleeding the user recorded for a day. */
@Serializable
enum class FlowLevel {
    @SerialName("spotting") SPOTTING,
    @SerialName("light") LIGHT,
    @SerialName("medium") MEDIUM,
    @SerialName("heavy") HEAVY,
}

/**
 * How much the prediction is worth.
 *
 * [NONE] is a first-class answer, not a failure: the app is specified to say "prognoz
 * uchun ma'lumot yetarli emas" rather than show a confident-looking guess built from one
 * data point. [reasonFor] explains which case applies so the client can word it.
 */
@Serializable
enum class PredictionConfidence {
    @SerialName("none") NONE,
    @SerialName("low") LOW,
    @SerialName("medium") MEDIUM,
    @SerialName("high") HIGH,
}

/** Why the confidence is what it is. Stable keys; the client supplies the wording. */
object PredictionReasons {
    /** The life stage does not predict a cycle at all — pregnancy, menopause and so on. */
    const val STAGE_DOES_NOT_PREDICT = "stage_does_not_predict"
    const val NO_DATA = "no_data"
    const val ONLY_BASELINE = "only_baseline"
    const val FEW_CYCLES = "few_cycles"
    const val IRREGULAR = "irregular"
    const val SUFFICIENT = "sufficient"
}

/** One recorded period. [endedOn] is null while it is still going. */
@Serializable
data class PeriodEntry(
    val id: String,
    val startedOn: LocalDate,
    val endedOn: LocalDate? = null,
    val createdAt: Instant,
) {
    val isOngoing: Boolean get() = endedOn == null
}

@Serializable
data class LogPeriodRequest(
    val startedOn: LocalDate,
    val endedOn: LocalDate? = null,
)

@Serializable
data class UpdatePeriodRequest(
    val startedOn: LocalDate? = null,
    val endedOn: LocalDate? = null,
    /** Set true to reopen a period that was ended by mistake. */
    val clearEnd: Boolean = false,
)

/**
 * The prediction itself. Every date here is an estimate — the app badges each one
 * "Taxminiy", which is why they are grouped in their own type rather than mixed in with
 * recorded facts.
 */
@Serializable
data class CyclePrediction(
    val confidence: PredictionConfidence,
    val reason: String,
    val nextPeriodStart: LocalDate? = null,
    val nextPeriodEnd: LocalDate? = null,
    val ovulationOn: LocalDate? = null,
    val fertileFrom: LocalDate? = null,
    val fertileUntil: LocalDate? = null,
    val averageCycleLength: Int? = null,
    val averagePeriodLength: Int? = null,
    /** Spread of the observed cycle lengths, in days. Higher means less regular. */
    val variationDays: Int? = null,
    /** How many completed cycles the estimate is built from. */
    val basedOnCycles: Int = 0,
) {
    val hasPrediction: Boolean get() = confidence != PredictionConfidence.NONE
}

/** What Today and the Journey tab need in one call. */
@Serializable
data class CycleStatus(
    /** Day 1 is the first day of the current period. Null when it cannot be known. */
    val cycleDay: Int? = null,
    val phase: CyclePhase? = null,
    /** True when [phase] comes from a prediction rather than a recorded period. */
    val phaseIsPredicted: Boolean = false,
    val currentPeriod: PeriodEntry? = null,
    val lastPeriodStart: LocalDate? = null,
    val daysUntilNextPeriod: Int? = null,
    val prediction: CyclePrediction,
    val today: LocalDate,
)

/** One day in the calendar. */
@Serializable
data class CycleDay(
    val date: LocalDate,
    val phase: CyclePhase? = null,
    /**
     * Recorded days are drawn filled and predicted days outlined — colour is never the
     * only indicator, so the client needs this separately from [phase].
     */
    val isPredicted: Boolean,
    val flow: FlowLevel? = null,
    val hasNote: Boolean = false,
    val symptomCount: Int = 0,
)

@Serializable
data class CycleCalendar(
    val from: LocalDate,
    val to: LocalDate,
    val days: List<CycleDay>,
    val prediction: CyclePrediction,
)

/** One completed cycle, for the history and statistics screen. */
@Serializable
data class CycleHistoryEntry(
    val startedOn: LocalDate,
    val endedOn: LocalDate,
    val cycleLength: Int,
    val periodLength: Int?,
)

@Serializable
data class CycleHistory(
    val cycles: List<CycleHistoryEntry>,
    val averageCycleLength: Int? = null,
    val averagePeriodLength: Int? = null,
    val shortestCycle: Int? = null,
    val longestCycle: Int? = null,
    val prediction: CyclePrediction,
)
