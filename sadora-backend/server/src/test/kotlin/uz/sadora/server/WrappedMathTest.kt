package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import uz.sadora.contract.CyclePhase
import uz.sadora.contract.WrappedHabits
import uz.sadora.contract.WrappedPersona
import uz.sadora.server.wrapped.WrappedService

class WrappedMathTest {
    @Test
    fun `a 28-day cycle runs period then follicular then fertile then luteal`() {
        val phases = WrappedService.Phases(listOf(WrappedService.Phases.Segment(LocalDate(2026, 9, 1), 28, 5)))
        assertEquals(CyclePhase.PERIOD, phases.of(LocalDate(2026, 9, 5)))
        assertEquals(CyclePhase.FOLLICULAR, phases.of(LocalDate(2026, 9, 6)))
        assertEquals(CyclePhase.FERTILE, phases.of(LocalDate(2026, 9, 14)))
        assertEquals(CyclePhase.LUTEAL, phases.of(LocalDate(2026, 9, 28)))
        assertNull(phases.of(LocalDate(2026, 9, 29)), "past the cycle's end nothing is guessed")
        assertNull(phases.of(LocalDate(2026, 8, 31)))
    }

    @Test
    fun `the longest run counts consecutive days only`() {
        val days = listOf(1, 2, 3, 5, 6, 10).map { LocalDate(2026, 3, it) }
        assertEquals(3, WrappedService.longestRun(days))
        assertEquals(0, WrappedService.longestRun(emptyList()))
    }

    @Test
    fun `the flower is chosen in order from how she used the app`() {
        val none = WrappedHabits()
        assertEquals(WrappedPersona.ATIRGUL, WrappedService.persona(28, none.copy(loggedDays = 20), 0, null, null))
        assertEquals(WrappedPersona.LOLA, WrappedService.persona(28, none.copy(kegelSessions = 10, waterGoalDays = 5), 0, null, null))
        assertEquals(WrappedPersona.NILUFAR, WrappedService.persona(28, none, 2, 4.0, 1))
        assertEquals(WrappedPersona.BINAFSHA, WrappedService.persona(28, none.copy(journalEntries = 3), 0, null, 5))
        assertEquals(WrappedPersona.MOYCHECHAK, WrappedService.persona(28, none.copy(sleepAverageMinutes = 450), 0, null, null))
        assertEquals(WrappedPersona.YASMIN, WrappedService.persona(28, none, 0, null, null))
    }
}
