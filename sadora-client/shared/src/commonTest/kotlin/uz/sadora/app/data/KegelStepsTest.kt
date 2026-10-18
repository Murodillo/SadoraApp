package uz.sadora.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import uz.sadora.app.ui.modules.KegelStep
import uz.sadora.app.ui.modules.steps
import uz.sadora.contract.KegelLevel

class KegelStepsTest {
    @Test
    fun `a session is ready then holds and flicks per set with a rest between sets`() {
        val level = KegelLevel(level = 2, holdSeconds = 5, relaxSeconds = 5, reps = 10, sets = 2, quickFlicks = 5)
        val steps = level.steps()
        assertEquals(KegelStep.Kind.Ready, steps.first().kind)
        assertEquals(20, steps.count { it.kind == KegelStep.Kind.Squeeze })
        assertEquals(10, steps.count { it.kind == KegelStep.Kind.Flick })
        assertEquals(1, steps.count { it.kind == KegelStep.Kind.Rest })
        // The plan the server shows is the clock the screen runs, give or take the three seconds to get ready.
        assertEquals(level.durationSeconds + 3, steps.sumOf { it.seconds })
    }
}
