package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import uz.sadora.server.insights.InsightMath

/**
 * The Insights screen used to draw invented numbers. These are the rules that keep the
 * replacement honest: a mean of nothing is not zero, a handful of days is not a trend,
 * and a window where nothing varied has nothing to say.
 */
class InsightMathTest {

    private val minimumDays = 8
    private val minimumDifference = 0.4

    @Test
    fun `an empty series has no average, rather than an average of zero`() {
        assertNull(InsightMath.averageOrNull(emptyList()))
        assertEquals(3.5, InsightMath.averageOrNull(listOf(3.0, 4.0)))
    }

    @Test
    fun `the median is the middle value, and the mean of the middle two when even`() {
        assertEquals(3.0, InsightMath.median(listOf(5.0, 1.0, 3.0)))
        assertEquals(2.5, InsightMath.median(listOf(1.0, 2.0, 3.0, 4.0)))
        assertNull(InsightMath.median(emptyList()))
    }

    /** One huge day must not drag the split point past every ordinary one. */
    @Test
    fun `the split point is the median, so an outlier cannot swallow the window`() {
        val steps = listOf(2000.0, 2200.0, 2400.0, 2600.0, 2800.0, 3000.0, 3200.0, 40000.0)
        assertEquals(2700.0, InsightMath.median(steps))
    }

    @Test
    fun `fewer than the minimum days produces nothing`() {
        val pairs = (1..7).map { it.toDouble() to it.toDouble() }
        assertNull(InsightMath.split(pairs, minimumDays, minimumDifference))
    }

    @Test
    fun `a window where every day is identical produces nothing`() {
        // Every value equals the median, so the "below" half is empty — there is no
        // comparison to make, and inventing one would be the old fabricated number again.
        val pairs = (1..10).map { 400.0 to 3.0 }
        assertNull(InsightMath.split(pairs, minimumDays, minimumDifference))
    }

    @Test
    fun `a difference smaller than the threshold produces nothing`() {
        val pairs = listOf(
            300.0 to 3.0, 320.0 to 3.1, 340.0 to 3.0, 360.0 to 3.1,
            500.0 to 3.2, 520.0 to 3.2, 540.0 to 3.3, 560.0 to 3.2,
        )
        assertNull(InsightMath.split(pairs, minimumDays, minimumDifference))
    }

    @Test
    fun `a real difference comes back as the two halves and the day count`() {
        val pairs = listOf(
            300.0 to 2.0, 320.0 to 2.0, 340.0 to 3.0, 360.0 to 2.0,
            500.0 to 4.0, 520.0 to 4.0, 540.0 to 5.0, 560.0 to 4.0,
        )
        val comparison = assertNotNull(InsightMath.split(pairs, minimumDays, minimumDifference))
        assertEquals(4.3, comparison.high)
        assertEquals(2.3, comparison.low)
        assertEquals(8, comparison.days)
    }

    /** A finding may run the other way; the caller words it, so the sign is not hidden here. */
    @Test
    fun `the comparison keeps its direction when the high half is lower`() {
        val pairs = listOf(
            300.0 to 5.0, 320.0 to 5.0, 340.0 to 4.0, 360.0 to 5.0,
            500.0 to 2.0, 520.0 to 2.0, 540.0 to 1.0, 560.0 to 2.0,
        )
        val comparison = assertNotNull(InsightMath.split(pairs, minimumDays, minimumDifference))
        assertEquals(1.8, comparison.high)
        assertEquals(4.8, comparison.low)
    }

    @Test
    fun `values are rounded to one decimal, the way the screen reads them`() {
        assertEquals(3.3, InsightMath.round(3.333333))
        assertEquals(-2.7, InsightMath.round(-2.66))
    }
}
