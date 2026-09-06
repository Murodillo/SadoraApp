package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import uz.sadora.contract.CyclePhase
import uz.sadora.contract.LifeStage
import uz.sadora.contract.PredictionConfidence
import uz.sadora.contract.PredictionReasons
import uz.sadora.server.health.CycleBaselineData
import uz.sadora.server.health.CyclePredictor
import uz.sadora.server.health.RecordedPeriod

/**
 * The prediction is the one piece whose mistakes are invisible — a wrong forecast still
 * looks like a forecast — so the cases that must not silently guess are pinned here.
 */
class CyclePredictorTest {

    private fun date(text: String) = LocalDate.parse(text)

    private fun period(start: String, end: String? = null) =
        RecordedPeriod(Uuid.random(), date(start), end?.let { date(it) })

    private val baseline = CycleBaselineData(
        lastPeriodStart = date("2026-08-06"),
        averageCycleLength = 28,
        averagePeriodLength = 5,
        isRegular = true,
    )

    // ---------------------------------------------------------------- refusals

    @Test
    fun `stages that do not predict a cycle get no forecast at all`() {
        listOf(
            LifeStage.PREGNANCY,
            LifeStage.POSTPARTUM,
            LifeStage.PERIMENOPAUSE,
            LifeStage.MENOPAUSE,
        ).forEach { stage ->
            val prediction = CyclePredictor.predict(
                lifeStage = stage,
                periods = listOf(period("2026-08-06", "2026-08-10")),
                baseline = baseline,
                today = date("2026-08-20"),
            )
            assertEquals(PredictionConfidence.NONE, prediction.confidence, "$stage")
            assertEquals(PredictionReasons.STAGE_DOES_NOT_PREDICT, prediction.reason)
            assertNull(prediction.nextPeriodStart, "$stage must not receive a date")
        }
    }

    /** The specified behaviour is to say so, not to invent twenty-eight days. */
    @Test
    fun `no periods and no baseline produces no prediction rather than a guess`() {
        val prediction = CyclePredictor.predict(
            lifeStage = LifeStage.CYCLE,
            periods = emptyList(),
            baseline = null,
            today = date("2026-08-20"),
        )
        assertEquals(PredictionConfidence.NONE, prediction.confidence)
        assertEquals(PredictionReasons.NO_DATA, prediction.reason)
        assertNull(prediction.nextPeriodStart)
        assertTrue(!prediction.hasPrediction)
    }

    // ---------------------------------------------------------------- baseline only

    @Test
    fun `the onboarding baseline alone predicts, but only weakly`() {
        val prediction = CyclePredictor.predict(
            lifeStage = LifeStage.CYCLE,
            periods = emptyList(),
            baseline = baseline,
            today = date("2026-08-20"),
        )
        assertEquals(PredictionConfidence.LOW, prediction.confidence)
        assertEquals(PredictionReasons.ONLY_BASELINE, prediction.reason)
        assertEquals(date("2026-09-03"), prediction.nextPeriodStart)
        assertEquals(0, prediction.basedOnCycles)
    }

    @Test
    fun `one logged period anchors the forecast but is still only one data point`() {
        val prediction = CyclePredictor.predict(
            lifeStage = LifeStage.CYCLE,
            periods = listOf(period("2026-08-10", "2026-08-14")),
            baseline = baseline,
            today = date("2026-08-20"),
        )
        assertEquals(PredictionConfidence.LOW, prediction.confidence)
        assertEquals(PredictionReasons.ONLY_BASELINE, prediction.reason)
        // Anchored on the logged period, not on the older baseline date.
        assertEquals(date("2026-09-07"), prediction.nextPeriodStart)
    }

    // ---------------------------------------------------------------- history

    @Test
    fun `three regular cycles give a confident forecast`() {
        val prediction = CyclePredictor.predict(
            lifeStage = LifeStage.CYCLE,
            periods = listOf(
                period("2026-05-04", "2026-05-08"),
                period("2026-06-01", "2026-06-05"),
                period("2026-06-29", "2026-07-03"),
                period("2026-07-27", "2026-07-31"),
            ),
            baseline = baseline,
            today = date("2026-08-10"),
        )
        assertEquals(PredictionConfidence.HIGH, prediction.confidence)
        assertEquals(PredictionReasons.SUFFICIENT, prediction.reason)
        assertEquals(3, prediction.basedOnCycles)
        assertEquals(28, prediction.averageCycleLength)
        assertEquals(5, prediction.averagePeriodLength)
        assertEquals(0, prediction.variationDays)
        assertEquals(date("2026-08-24"), prediction.nextPeriodStart)
        assertEquals(date("2026-08-28"), prediction.nextPeriodEnd)
    }

    @Test
    fun `ovulation is counted back from the next period, and the fertile window brackets it`() {
        val prediction = CyclePredictor.predict(
            lifeStage = LifeStage.CYCLE,
            periods = listOf(period("2026-07-01", "2026-07-05"), period("2026-07-29", "2026-08-02")),
            baseline = baseline,
            today = date("2026-08-10"),
        )
        val nextStart = prediction.nextPeriodStart!!
        assertEquals(date("2026-08-26"), nextStart)
        assertEquals(date("2026-08-12"), prediction.ovulationOn)
        assertEquals(date("2026-08-07"), prediction.fertileFrom)
        assertEquals(date("2026-08-13"), prediction.fertileUntil)
    }

    @Test
    fun `a wide spread across cycles is reported as irregular`() {
        val prediction = CyclePredictor.predict(
            lifeStage = LifeStage.CYCLE,
            periods = listOf(
                period("2026-04-01"),
                period("2026-05-02"), // 31
                period("2026-05-23"), // 21
                period("2026-06-27"), // 35
            ),
            baseline = baseline,
            today = date("2026-07-01"),
        )
        assertEquals(PredictionReasons.IRREGULAR, prediction.reason)
        assertEquals(PredictionConfidence.MEDIUM, prediction.confidence)
        assertEquals(14, prediction.variationDays)
    }

    /**
     * A three-month gap is a user who stopped logging, not a ninety-day cycle. Letting it
     * into the average would push every future prediction weeks out.
     */
    @Test
    fun `an implausible gap is treated as a missed log and dropped`() {
        val starts = listOf(
            date("2026-01-05"),
            date("2026-02-02"), // 28
            date("2026-05-04"), // 91 — dropped
            date("2026-06-01"), // 28
        )
        assertEquals(listOf(28, 28), CyclePredictor.observedCycleLengths(starts))
    }

    @Test
    fun `a stale anchor rolls forward to a future date and loses confidence`() {
        val prediction = CyclePredictor.predict(
            lifeStage = LifeStage.CYCLE,
            periods = listOf(
                period("2026-01-05"),
                period("2026-02-02"),
                period("2026-03-02"),
                period("2026-03-30"),
            ),
            baseline = baseline,
            today = date("2026-08-20"),
        )
        val nextStart = prediction.nextPeriodStart!!
        assertTrue(nextStart >= date("2026-08-20"), "prediction must not be in the past: $nextStart")
        assertEquals(PredictionConfidence.LOW, prediction.confidence)
    }

    // ---------------------------------------------------------------- phases

    @Test
    fun `a day inside a logged period is recorded, not predicted`() {
        val periods = listOf(period("2026-08-06", "2026-08-10"))
        val prediction = CyclePredictor.predict(LifeStage.CYCLE, periods, baseline, date("2026-08-20"))
        val (phase, predicted) = CyclePredictor.phaseOn(date("2026-08-08"), periods, prediction, date("2026-08-20"))!!
        assertEquals(CyclePhase.PERIOD, phase)
        assertTrue(!predicted, "a logged day must be drawn as recorded")
    }

    @Test
    fun `an ongoing period covers up to today and no further`() {
        val periods = listOf(period("2026-08-18"))
        val today = date("2026-08-20")
        val prediction = CyclePredictor.predict(LifeStage.CYCLE, periods, baseline, today)

        assertEquals(false, CyclePredictor.phaseOn(today, periods, prediction, today)!!.second)
        val tomorrow = CyclePredictor.phaseOn(date("2026-08-21"), periods, prediction, today)
        assertTrue(tomorrow!!.second, "tomorrow cannot be a recorded fact")
    }

    @Test
    fun `the fertile window and the luteal phase land where the forecast says`() {
        val periods = listOf(period("2026-07-01", "2026-07-05"), period("2026-07-29", "2026-08-02"))
        val today = date("2026-08-10")
        val prediction = CyclePredictor.predict(LifeStage.CYCLE, periods, baseline, today)

        fun phaseOn(text: String) = CyclePredictor.phaseOn(date(text), periods, prediction, today)?.first

        assertEquals(CyclePhase.FERTILE, phaseOn("2026-08-12"))
        assertEquals(CyclePhase.FOLLICULAR, phaseOn("2026-08-05"))
        assertEquals(CyclePhase.LUTEAL, phaseOn("2026-08-20"))
        assertEquals(CyclePhase.PERIOD, phaseOn("2026-08-27"))
    }

    /**
     * Forgetting to close a period is ordinary. Without a cap the open row would paint
     * every day since as bleeding and the phase would never move on.
     */
    @Test
    fun `a period left open does not bleed across the whole calendar`() {
        val periods = listOf(period("2026-08-01"))
        val today = date("2026-08-25")
        val prediction = CyclePredictor.predict(LifeStage.CYCLE, periods, baseline, today)

        assertEquals(CyclePhase.PERIOD, CyclePredictor.phaseOn(date("2026-08-05"), periods, prediction, today)?.first)
        val later = CyclePredictor.phaseOn(date("2026-08-15"), periods, prediction, today)
        assertTrue(later?.first != CyclePhase.PERIOD, "day 15 of an unclosed period is not still bleeding")
        assertTrue(later?.second == true, "and it is inference, not a recorded fact")
    }

    @Test
    fun `the cycle day counts from the most recent period start`() {
        val periods = listOf(period("2026-08-06", "2026-08-10"))
        val prediction = CyclePredictor.predict(LifeStage.CYCLE, periods, baseline, date("2026-08-19"))
        assertEquals(14, CyclePredictor.cycleDayOn(date("2026-08-19"), periods, prediction))
    }

    /**
     * The regression this was written for: the cycle day was counted from the last logged
     * period while the phase came off the predicted grid, so the app could show "day 31"
     * beside a period its own forecast said had started two days earlier.
     */
    @Test
    fun `the cycle day and the phase agree once the last log is a full cycle behind`() {
        val periods = listOf(
            period("2026-06-08", "2026-06-12"),
            period("2026-07-06", "2026-07-10"),
            period("2026-08-03", "2026-08-07"),
        )
        val today = date("2026-09-02")
        val prediction = CyclePredictor.predict(LifeStage.CYCLE, periods, baseline, today)

        // The forecast puts a period on 31 August, so 2 September is day 3 of it.
        assertEquals(3, CyclePredictor.cycleDayOn(today, periods, prediction))
        val (phase, predicted) = CyclePredictor.phaseOn(today, periods, prediction, today)!!
        assertEquals(CyclePhase.PERIOD, phase)
        assertTrue(predicted, "an unlogged period is inference and must say so")
    }

    @Test
    fun `a logged period anchors its own cycle rather than the predicted grid`() {
        val periods = listOf(period("2026-08-06", "2026-08-10"))
        val prediction = CyclePredictor.predict(LifeStage.CYCLE, periods, baseline, date("2026-08-19"))
        assertEquals(date("2026-08-06"), CyclePredictor.cycleStartFor(date("2026-08-19"), periods, prediction))
    }
}
