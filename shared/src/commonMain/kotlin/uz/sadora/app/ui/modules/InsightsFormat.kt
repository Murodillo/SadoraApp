package uz.sadora.app.ui.modules

import kotlin.math.abs
import kotlin.math.roundToInt
import uz.sadora.app.model.Fmt
import uz.sadora.contract.InsightFinding
import uz.sadora.contract.InsightKeys
import uz.sadora.contract.MetricTrend
import uz.sadora.contract.TrendMetric
import uz.sadora.app.i18n.CommonStrings
import uz.sadora.app.i18n.DateStrings
import uz.sadora.app.i18n.ModuleStrings

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
fun MetricTrend.barLabels(dates: DateStrings): List<String> =
    if (points.size > 10) {
        emptyList()
    } else {
        points.map { dates.weekdays[it.date.dayOfWeek.ordinal].take(2).replaceFirstChar { c -> c.uppercase() } }
    }

/** The window's own dates, for the caption under a long series. */
fun MetricTrend.rangeLabel(dates: DateStrings): String? {
    val first = points.firstOrNull()?.date ?: return null
    val last = points.lastOrNull()?.date ?: return null
    return "${dates.dayMonth(first)} — ${dates.dayMonth(last)}"
}

/** The average, in the metric's own words. Null when there was nothing to average. */
fun MetricTrend.averageLabel(t: ModuleStrings, common: CommonStrings): String? {
    val value = average ?: return null
    return when (metric) {
        TrendMetric.SLEEP_MINUTES -> minutesLabel(value.roundToInt(), t, common)
        TrendMetric.STEPS -> t.stepsValue(Fmt.int(value.roundToInt()))
        TrendMetric.WATER_ML -> t.litresValue(Fmt.oneDecimal(value.toFloat() / 1000))
        TrendMetric.KCAL -> t.kcalValue(Fmt.int(value.roundToInt()))
        TrendMetric.MOOD, TrendMetric.ENERGY, TrendMetric.STRESS -> t.outOfFive(decimal(value))
    }
}

/**
 * "+12 daqiqa" against the window before this one.
 *
 * Null when either window has no data — the screen then shows nothing rather than a
 * confident-looking zero, which is the whole reason this replaced a hardcoded "+12".
 */
fun MetricTrend.changeLabel(t: ModuleStrings, common: CommonStrings): String? {
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
        TrendMetric.SLEEP_MINUTES -> minutesLabel(size.roundToInt(), t, common)
        TrendMetric.STEPS -> t.stepsValue(Fmt.int(size.roundToInt()))
        TrendMetric.WATER_ML -> t.litresValue(Fmt.oneDecimal(size.toFloat() / 1000))
        TrendMetric.KCAL -> t.kcalValue(Fmt.int(size.roundToInt()))
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
fun InsightFinding.sentence(t: ModuleStrings): String? = when (key) {
    InsightKeys.SLEEP_AND_ENERGY -> t.sleepEnergyFinding(decimal(high), decimal(low))
    InsightKeys.STEPS_AND_MOOD -> t.activityMoodFinding(decimal(high), decimal(low))
    InsightKeys.WATER_AND_HEADACHE -> t.waterHeadacheFinding(percent(high), percent(low))
    else -> null
}

/** "14 kun asosida" — the count is part of the claim, not a footnote. */
fun InsightFinding.basisLabel(t: ModuleStrings): String = t.basedOnDays(daysConsidered)

/** "6s 40d", the app's duration format, from a count of minutes. */
fun minutesLabel(minutes: Int, t: ModuleStrings, common: CommonStrings): String =
    if (minutes >= 60) common.hoursMinutes(minutes / 60, minutes % 60) else t.minutesOnly(minutes)

private fun decimal(value: Double): String = Fmt.oneDecimal(value.toFloat())

private fun percent(value: Double): String = "${value.roundToInt()}%"
