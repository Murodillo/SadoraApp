package uz.sadora.contract

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * "Wrapped": a short recap when a cycle ends, and a long one for the year in December.
 *
 * The server counts; the app tells the story in her language and draws the slides. Every
 * number is something she logged or did — the persona at the end is a game, never a reading
 * of her body.
 */
@Serializable
enum class WrappedKind {
    @SerialName("cycle") CYCLE,
    @SerialName("year") YEAR,
}

/** The playful flower she was this time. Not medical: chosen from how she used the app. */
@Serializable
enum class WrappedPersona {
    /** Logged most days: the dedicated one. */
    @SerialName("atirgul") ATIRGUL,
    /** Kegel, breathing and water most days: the energetic one. */
    @SerialName("lola") LOLA,
    /** A steady cycle and a good mood: the calm one. */
    @SerialName("nilufar") NILUFAR,
    /** Wrote and noted her mood often: the reflective one. */
    @SerialName("binafsha") BINAFSHA,
    /** Slept well and breathed slowly: the rest lover. */
    @SerialName("moychechak") MOYCHECHAK,
    /** Still finding her way around: the explorer. */
    @SerialName("yasmin") YASMIN,
}

/** One recap on the list. [key] is `cycle-2026-09-14` (the cycle's first day) or `year-2026`. */
@Serializable
data class WrappedSummary(
    val kind: WrappedKind,
    val key: String,
    val from: LocalDate,
    val to: LocalDate,
    /** New and not opened yet: Today shows a card for it. */
    val fresh: Boolean = false,
)

@Serializable
data class WrappedIndex(
    /** Newest first: the year (in December and January), then the latest cycles. */
    val items: List<WrappedSummary> = emptyList(),
) {
    val fresh: WrappedSummary? get() = items.firstOrNull { it.fresh }
}

@Serializable
data class WrappedSymptom(val key: String, val label: String, val days: Int)

/** Her average mood (1–5) on the days of one phase she logged it. */
@Serializable
data class PhaseMood(val phase: CyclePhase, val average: Double, val days: Int)

@Serializable
data class WrappedCycles(
    val count: Int,
    val averageLength: Int? = null,
    val averagePeriod: Int? = null,
    val shortest: Int? = null,
    val longest: Int? = null,
    /** How far, on average, the forecast was from the day the period came. */
    val forecastErrorDays: Double? = null,
    val lengths: List<Int> = emptyList(),
)

@Serializable
data class WrappedHabits(
    /** Days she opened SADORA. */
    val daysWithApp: Int = 0,
    val longestRun: Int = 0,
    val loggedDays: Int = 0,
    val waterGoalDays: Int = 0,
    val waterLitres: Int = 0,
    val sleepAverageMinutes: Int? = null,
    val kegelSessions: Int = 0,
    val practiceMinutes: Int = 0,
    val journalEntries: Int = 0,
    val mealsLogged: Int = 0,
    val gulEarned: Int = 0,
    /** Badge keys she earned a tier of in the window. */
    val badges: List<String> = emptyList(),
)

@Serializable
data class Wrapped(
    val kind: WrappedKind,
    val key: String,
    val from: LocalDate,
    val to: LocalDate,
    /** A cycle recap: this cycle and her usual one before it. */
    val cycleLength: Int? = null,
    val periodLength: Int? = null,
    val usualLength: Int? = null,
    /** Days the period came after (+) or before (−) the forecast; null without earlier cycles. */
    val forecastErrorDays: Int? = null,
    /** A year recap: all the cycles that ended in it. */
    val cycles: WrappedCycles? = null,
    val topSymptoms: List<WrappedSymptom> = emptyList(),
    val moodByPhase: List<PhaseMood> = emptyList(),
    val averageMood: Double? = null,
    val bestMoodDay: LocalDate? = null,
    /** The year's month (1–12) with the best average mood. */
    val bestMonth: Int? = null,
    val habits: WrappedHabits = WrappedHabits(),
    val persona: WrappedPersona = WrappedPersona.YASMIN,
)
