package uz.sadora.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import uz.sadora.app.ui.modules.WrappedSlide
import uz.sadora.app.ui.modules.slides
import uz.sadora.contract.Wrapped
import uz.sadora.contract.WrappedCycles
import uz.sadora.contract.WrappedHabits
import uz.sadora.contract.WrappedKind

class WrappedSlidesTest {
    private val day = LocalDate(2026, 9, 1)

    @Test
    fun `a first cycle has no forecast slide and a later one does`() {
        val first = Wrapped(WrappedKind.CYCLE, "cycle-2026-09-01", day, day, cycleLength = 28)
        assertEquals(
            listOf(WrappedSlide.CycleIntro, WrappedSlide.Feelings, WrappedSlide.Habits, WrappedSlide.Persona),
            first.slides(),
        )
        assertEquals(WrappedSlide.Forecast, first.copy(forecastErrorDays = 1).slides()[1])
    }

    @Test
    fun `a year without cycles skips the cycle slides but keeps habits and the persona`() {
        val pregnant = Wrapped(WrappedKind.YEAR, "year-2026", day, day, cycles = WrappedCycles(count = 0), habits = WrappedHabits(longestRun = 3))
        assertEquals(
            listOf(WrappedSlide.YearIntro, WrappedSlide.Streak, WrappedSlide.Body, WrappedSlide.Mind, WrappedSlide.Persona, WrappedSlide.Outro),
            pregnant.slides(),
        )
    }
}
