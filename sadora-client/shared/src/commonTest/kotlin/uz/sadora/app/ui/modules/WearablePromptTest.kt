package uz.sadora.app.ui.modules

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import uz.sadora.app.data.PromptPrefs

class WearablePromptTest {
    private val joined = LocalDate(2026, 10, 1)

    private suspend fun due(
        prompts: PromptPrefs = PromptPrefs.InMemory(),
        hasWearable: Boolean? = null,
        deviceConnected: Boolean = false,
        today: LocalDate,
    ) = wearableQuestionDue(prompts, "u1", hasWearable, joined, deviceConnected, today)

    @Test
    fun `waits a few days after sign-up before asking`() = runTest {
        assertFalse(due(today = joined))
        assertFalse(due(today = LocalDate(2026, 10, 3)))
        assertTrue(due(today = LocalDate(2026, 10, 4)))
    }

    @Test
    fun `an answer or a connected device means it is never asked`() = runTest {
        val later = LocalDate(2026, 10, 20)
        assertFalse(due(hasWearable = true, today = later))
        assertFalse(due(hasWearable = false, today = later))
        assertFalse(due(deviceConnected = true, today = later))
    }

    @Test
    fun `a later holds it back for a week — for that account only`() = runTest {
        val prompts = PromptPrefs.InMemory()
        val asked = LocalDate(2026, 10, 5)
        prompts.setWearableAskAfter("u1", wearableSnoozedUntil(asked))
        assertFalse(due(prompts, today = LocalDate(2026, 10, 11)))
        assertTrue(due(prompts, today = LocalDate(2026, 10, 12)))
        assertTrue(wearableQuestionDue(prompts, "u2", null, joined, false, LocalDate(2026, 10, 6)))
    }
}
