package uz.sadora.server.insights

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * The arithmetic behind an observation, with no database in it.
 *
 * Separated from [InsightsService] so the promises the screen makes can be tested
 * directly: that a gap is never counted as a zero, that a handful of days never
 * produces a sentence, and that a window where nothing varied produces nothing at all.
 */
object InsightMath {

    /** The two halves of a split, and how many days went into it. */
    data class Comparison(val high: Double, val low: Double, val days: Int)

    /** Null for an empty list — a mean of nothing is not zero. */
    fun averageOrNull(values: List<Double>): Double? =
        if (values.isEmpty()) null else round(values.average())

    /**
     * The middle value; for an even count, the mean of the two middle ones.
     *
     * Median rather than mean because one enormous step count would drag a mean past
     * every ordinary day and put the whole window in the "low" half.
     */
    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }

    /**
     * Splits [pairs] by the median of the first value and compares the mean of the second
     * across the halves.
     *
     * Returns null — meaning "nothing worth saying" — when there are fewer than
     * [minimumDays] days, when every day falls on one side of the median, or when the two
     * halves differ by less than [minimumDifference]. Each of those is a case where a
     * sentence would sound more certain than the data is.
     */
    fun split(
        pairs: List<Pair<Double, Double>>,
        minimumDays: Int,
        minimumDifference: Double,
    ): Comparison? {
        if (pairs.size < minimumDays) return null
        val median = median(pairs.map { it.first }) ?: return null

        val high = pairs.filter { it.first >= median }.map { it.second }
        val low = pairs.filter { it.first < median }.map { it.second }
        if (high.isEmpty() || low.isEmpty()) return null

        val highMean = high.average()
        val lowMean = low.average()
        if (abs(highMean - lowMean) < minimumDifference) return null

        return Comparison(round(highMean), round(lowMean), pairs.size)
    }

    /**
     * One decimal place, half away from zero.
     *
     * Through `round(value * 10) / 10` this was not consistent at a half: 2.25 came back
     * as 2.3 and 4.25 as 4.2, because multiplying by ten lands one of them just under the
     * boundary in binary. Two averages of the same shape rounding in opposite directions
     * is the kind of detail someone eventually reports as a bug, so the decimal rounding
     * is done in decimal.
     */
    fun round(value: Double): Double =
        BigDecimal(value).setScale(1, RoundingMode.HALF_UP).toDouble()
}
