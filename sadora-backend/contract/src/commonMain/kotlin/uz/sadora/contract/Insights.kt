package uz.sadora.contract

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a trend line can be drawn for.
 *
 * Each one comes from a different place — sleep and steps from the wearable layer,
 * mood and energy from the daily log, water and calories from the nutrition tables —
 * and they are named here so a screen can ask for a series without knowing that.
 */
@Serializable
enum class TrendMetric(val unit: String) {
    @SerialName("sleep_minutes") SLEEP_MINUTES("min"),
    @SerialName("steps") STEPS("count"),
    @SerialName("water_ml") WATER_ML("ml"),
    @SerialName("kcal") KCAL("kcal"),
    @SerialName("mood") MOOD("1-5"),
    @SerialName("energy") ENERGY("1-5"),
    @SerialName("stress") STRESS("1-5"),
}

/**
 * One day of a series. [value] is null for a day with nothing recorded — a gap, which
 * the chart draws as a gap rather than as a zero.
 */
@Serializable
data class TrendPoint(val date: LocalDate, val value: Double? = null)

/**
 * One metric over the window.
 *
 * [previousAverage] is the same average over the window immediately before this one, so
 * a screen can say "+12 daqiqa" without doing its own arithmetic — and gets null, rather
 * than a made-up zero, when there is nothing to compare against.
 */
@Serializable
data class MetricTrend(
    val metric: TrendMetric,
    val points: List<TrendPoint> = emptyList(),
    val average: Double? = null,
    val previousAverage: Double? = null,
    val daysWithData: Int = 0,
) {
    val unit: String get() = metric.unit

    /** Change against the previous window, or null when either side has no data. */
    val change: Double?
        get() = if (average != null && previousAverage != null) average - previousAverage else null

    val hasData: Boolean get() = daysWithData > 0
}

/**
 * Stable keys for the observations. The client supplies the wording, the same way it
 * does for a prediction's reason — the server states what it measured, not how to say it.
 */
object InsightKeys {
    /** Energy was higher on the nights she slept longer. */
    const val SLEEP_AND_ENERGY = "sleep_and_energy"
    /** Mood was higher on the days she moved more. */
    const val STEPS_AND_MOOD = "steps_and_mood"
    /** Headaches were recorded less often on the days she drank more. */
    const val WATER_AND_HEADACHE = "water_and_headache"
}

/**
 * One observation, as two numbers and the count behind them.
 *
 * Deliberately not a sentence and deliberately not a cause: [high] and [low] are the
 * same measure on the two halves of the window, and the client is expected to word it
 * as co-occurrence. A finding is only produced when [daysConsidered] clears the
 * server's minimum, so nothing here is drawn from two days.
 */
@Serializable
data class InsightFinding(
    val key: String,
    /** Days where both sides of the comparison were recorded. */
    val daysConsidered: Int,
    /** The measure on the days above the median. */
    val high: Double,
    /** The measure on the days below it. */
    val low: Double,
    val unit: String = "",
)

/**
 * Everything the Tahlillar screen draws, for one window.
 *
 * [findingsAvailable] is false when the narrative is not part of her plan; [findings] is
 * then empty and the screen shows the locked block rather than an empty list that looks
 * like a bug.
 */
@Serializable
data class InsightsSummary(
    val from: LocalDate,
    val to: LocalDate,
    val days: Int,
    val trends: List<MetricTrend> = emptyList(),
    val findings: List<InsightFinding> = emptyList(),
    val findingsAvailable: Boolean = false,
    /** Days in the window carrying any entry at all — the screen's empty state reads this. */
    val daysLogged: Int = 0,
) {
    fun trend(metric: TrendMetric): MetricTrend? = trends.firstOrNull { it.metric == metric }

    val isEmpty: Boolean get() = daysLogged == 0
}
