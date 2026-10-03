package uz.sadora.app.ui.journey

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import uz.sadora.app.i18n.StageToolsEn
import uz.sadora.app.model.LifeStage
import uz.sadora.contract.CyclePrediction
import uz.sadora.contract.CycleStatus
import uz.sadora.contract.PredictionConfidence
import uz.sadora.contract.StageEvent
import uz.sadora.contract.StageEventKind

/**
 * The decisions the stage tools make on their own: whether a period is late, what is
 * worth a doctor's look, and when contractions mean it is time to go.
 */
class StageToolsTest {

    private val today = LocalDate(2026, 10, 4)

    private fun status(
        lastPeriod: LocalDate? = LocalDate(2026, 9, 1),
        cycle: Int? = 28,
        period: Int? = 5,
        spread: Int? = 0,
        cycles: Int = 3,
    ) = CycleStatus(
        lastPeriodStart = lastPeriod,
        prediction = CyclePrediction(
            confidence = PredictionConfidence.HIGH,
            reason = "sufficient",
            averageCycleLength = cycle,
            averagePeriodLength = period,
            variationDays = spread,
            basedOnCycles = cycles,
        ),
        today = today,
    )

    @Test
    fun `a period is late from the day after it was due, as the forecast cannot say`() {
        // 1 September + 28 days = 29 September: five days late on 4 October.
        assertEquals(5, periodLateDays(status()))
        assertNull(periodLateDays(status(lastPeriod = LocalDate(2026, 9, 10))), "not due yet")
        assertNull(periodLateDays(status(lastPeriod = LocalDate(2026, 6, 1))), "months ago is a stopped record")
        assertNull(periodLateDays(status(cycle = null)), "nothing to be late against")
    }

    @Test
    fun `a doctor flag needs two measured cycles, and then names what is out of range`() {
        assertTrue(doctorFlags(LifeStage.Cycle, status(cycle = 19, cycles = 1), StageToolsEn).isEmpty())
        assertEquals(1, doctorFlags(LifeStage.Cycle, status(cycle = 19), StageToolsEn).size)
        assertEquals(1, doctorFlags(LifeStage.TryingToConceive, status(cycle = 40), StageToolsEn).size)
        assertEquals(2, doctorFlags(LifeStage.Cycle, status(spread = 9, period = 9), StageToolsEn).size)
        assertTrue(doctorFlags(LifeStage.Cycle, status(), StageToolsEn).isEmpty())
        // Perimenopause is irregular by nature; it is not flagged for it.
        assertTrue(doctorFlags(LifeStage.Perimenopause, status(spread = 15), StageToolsEn).isEmpty())
    }

    @Test
    fun `any bleeding in the last year is flagged in menopause`() {
        assertEquals(listOf(StageToolsEn.bleedingAfterMenopause), doctorFlags(LifeStage.Menopause, status(), StageToolsEn))
        assertTrue(doctorFlags(LifeStage.Menopause, status(lastPeriod = LocalDate(2025, 6, 1)), StageToolsEn).isEmpty())
    }

    private fun contraction(minute: Int, seconds: Int) = StageEvent(
        id = "c$minute",
        kind = StageEventKind.CONTRACTION,
        startedAt = Start + minute.minutes,
        durationSeconds = seconds,
        createdAt = Start,
    )

    @Test
    fun `five-one-one means close, long and an hour of them`() {
        val anHour = (0..60 step 4).map { contraction(it, 70) }
        assertTrue(contractionSummary(anHour)!!.timeToGo)

        val tooShort = (0..60 step 4).map { contraction(it, 40) }
        assertFalse(contractionSummary(tooShort)!!.timeToGo)

        val tooFarApart = (0..60 step 8).map { contraction(it, 70) }
        assertFalse(contractionSummary(tooFarApart)!!.timeToGo)

        val notLongEnough = (0..20 step 4).map { contraction(it, 70) }
        assertFalse(contractionSummary(notLongEnough)!!.timeToGo)

        assertNull(contractionSummary(listOf(contraction(0, 60))))
    }

    private companion object {
        val Start = Instant.fromEpochSeconds(1_790_000_000)
    }
}
