package org.example.project.ui.modules

import kotlin.math.abs
import kotlin.math.roundToInt
import org.example.project.model.Fmt
import uz.sadora.contract.InsightFinding
import uz.sadora.contract.InsightKeys
import uz.sadora.contract.MetricTrend
import uz.sadora.contract.TrendMetric

/**
 * How a measured series becomes something on screen.
 *
 * The server states what it measured and never how to say it — the same split the cycle
 * prediction uses for its reasons — so every sentence in Tahlillar is written here, and
 * every one of them is phrased as co-occurrence. Nothing in this file may claim a cause.
 */

/** The bar values, with a day that has nothing recorded left as null. */
fun MetricTrend.barValues(): List<Float?> = points.map { it.value?.toFloat() }

/** "Du", "Se" … under a short window; nothing under a long one, where they would not fit. */
fun MetricTrend.barLabels(): List<String> =
    if (points.size > 10) {
        emptyList()
    } else {
        points.map { Fmt.weekdays[it.date.dayOfWeek.ordinal].take(2).replaceFirstChar { c -> c.uppercase() } }
    }

/** The window's own dates, for the caption under a long series. */
fun MetricTrend.rangeLabel(): String? {
    val first = points.firstOrNull()?.date ?: return null
    val last = points.lastOrNull()?.date ?: return null
    return "${Fmt.dayMonth(first)} — ${Fmt.dayMonth(last)}"
}

/** The average, in the metric's own words. Null when there was nothing to average. */
fun MetricTrend.averageLabel(): String? {
    val value = average ?: return null
    return when (metric) {
        TrendMetric.SLEEP_MINUTES -> minutesLabel(value.roundToInt())
        TrendMetric.STEPS -> "${Fmt.int(value.roundToInt())} qadam"
        TrendMetric.WATER_ML -> "${Fmt.oneDecimal(value.toFloat() / 1000)} l"
        TrendMetric.KCAL -> "${Fmt.int(value.roundToInt())} kkal"
        TrendMetric.MOOD, TrendMetric.ENERGY, TrendMetric.STRESS -> "${decimal(value)} / 5"
    }
}

/**
 * "+12 daqiqa" against the window before this one.
 *
 * Null when either window has no data — the screen then shows nothing rather than a
 * confident-looking zero, which is the whole reason this replaced a hardcoded "+12".
 */
fun MetricTrend.changeLabel(): String? {
    val delta = change ?: return null
    val rounded = when (metric) {
        TrendMetric.SLEEP_MINUTES, TrendMetric.STEPS, TrendMetric.WATER_ML, TrendMetric.KCAL ->
            delta.roundToInt().toDouble()
        else -> delta
    }
    if (abs(rounded) < changeFloor()) return null

    val sign = if (rounded > 0) "+" else "−"
    val size = abs(rounded)
    val body = when (metric) {
        TrendMetric.SLEEP_MINUTES -> minutesLabel(size.roundToInt())
        TrendMetric.STEPS -> "${Fmt.int(size.roundToInt())} qadam"
        TrendMetric.WATER_ML -> "${Fmt.oneDecimal(size.toFloat() / 1000)} l"
        TrendMetric.KCAL -> "${Fmt.int(size.roundToInt())} kkal"
        TrendMetric.MOOD, TrendMetric.ENERGY, TrendMetric.STRESS -> decimal(size)
    }
    return "$sign$body"
}

/** True when the change is worth colouring green — more sleep is good, more stress is not. */
fun MetricTrend.changeIsGood(): Boolean? {
    val delta = change ?: return null
    return when (metric) {
        TrendMetric.STRESS -> delta < 0
        TrendMetric.KCAL -> null
        else -> delta > 0
    }
}

/**
 * A movement smaller than this is noise and is not reported: a minute of sleep or a
 * hundredth of a mood point is not a trend, it is rounding.
 */
private fun MetricTrend.changeFloor(): Double = when (metric) {
    TrendMetric.SLEEP_MINUTES -> 5.0
    TrendMetric.STEPS -> 100.0
    TrendMetric.WATER_ML -> 50.0
    TrendMetric.KCAL -> 25.0
    else -> 0.1
}

/**
 * The wording for one observation.
 *
 * Both numbers are stated and neither is called a cause; an unknown key produces null
 * rather than a guess, so a server that grows a new finding shows nothing until the app
 * learns how to say it.
 */
fun InsightFinding.sentence(): String? = when (key) {
    InsightKeys.SLEEP_AND_ENERGY ->
        "Ko'proq uxlagan kunlarda energiya o'rtacha ${decimal(high)}, kamroq uxlagan kunlarda " +
            "${decimal(low)} bo'lgan."
    InsightKeys.STEPS_AND_MOOD ->
        "Ko'proq yurgan kunlarda kayfiyat o'rtacha ${decimal(high)}, kamroq yurgan kunlarda " +
            "${decimal(low)} bo'lgan."
    InsightKeys.WATER_AND_HEADACHE ->
        "Ko'proq suv ichgan kunlarning ${percent(high)}ida bosh og'rig'i qayd etilgan, " +
            "kamroq ichgan kunlarning ${percent(low)}ida."
    else -> null
}

/** "14 kun asosida" — the count is part of the claim, not a footnote. */
fun InsightFinding.basisLabel(): String = "$daysConsidered kun asosida · birga kuzatilgan"

/** "6s 40d", the app's duration format, from a count of minutes. */
fun minutesLabel(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60}s ${minutes % 60}d" else "$minutes daqiqa"

private fun decimal(value: Double): String = Fmt.oneDecimal(value.toFloat())

private fun percent(value: Double): String = "${value.roundToInt()}%"
