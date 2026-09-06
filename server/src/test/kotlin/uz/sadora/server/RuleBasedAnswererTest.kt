package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uz.sadora.contract.CyclePhase
import uz.sadora.server.ai.AiContext
import uz.sadora.server.ai.RuleBasedAnswerer

/**
 * The answerer is what stands in for a model, so the promises the product makes about
 * it are pinned here: it names what it read, it reads nothing without consent, and every
 * answer says it is not a diagnosis.
 */
class RuleBasedAnswererTest {

    private val context = AiContext(
        cycleDay = 14,
        phase = CyclePhase.FERTILE,
        daysUntilNextPeriod = 15,
        sleepMinutes = 400,
        waterMl = 1200,
        waterGoalMl = 2000,
        kcal = 1240,
        kcalGoal = 1850,
    )

    @Test
    fun `every answer carries the disclaimer`() {
        listOf("charchadim", "nima yeyin", "terim", "uyqu", "hayz", "suv", "stress", "salom").forEach { question ->
            assertTrue(RuleBasedAnswerer.DISCLAIMER in RuleBasedAnswerer.answer(question, context), question)
            assertTrue(RuleBasedAnswerer.DISCLAIMER in RuleBasedAnswerer.answer(question, null), question)
        }
    }

    @Test
    fun `with a context the answer names the numbers it used`() {
        val answer = RuleBasedAnswerer.answer("Nega charchayapman?", context)
        assertTrue("Sikl 14-kun" in answer, answer)
        assertTrue("uyqu 6s 40d" in answer, answer)
        assertTrue("suv 1,2 l" in answer, answer)
    }

    @Test
    fun `without consent nothing personal appears and the answer says so`() {
        val answer = RuleBasedAnswerer.answer("Nega charchayapman?", null)
        assertFalse("14-kun" in answer, answer)
        assertFalse("6s 40d" in answer, answer)
        assertTrue(RuleBasedAnswerer.GENERAL_NOTE in answer, answer)
    }

    @Test
    fun `an empty context reads as no context rather than as zeros`() {
        val answer = RuleBasedAnswerer.answer("Bugun nima yeyin?", AiContext())
        assertFalse("/ " in answer.substringBefore("\n"), answer)
        assertTrue(RuleBasedAnswerer.GENERAL_NOTE in answer, answer)
    }

    @Test
    fun `the cycle answer follows the phase in the context`() {
        val answer = RuleBasedAnswerer.answer("Hayzim qachon?", context)
        assertTrue("14-kuni" in answer, answer)
        assertTrue("ovulyatsiya davri" in answer, answer)
        assertTrue("15 kundan keyin" in answer, answer)
    }

    @Test
    fun `the water answer does the arithmetic from the context`() {
        val answer = RuleBasedAnswerer.answer("Suv ichishim kerakmi?", context)
        assertTrue("800 ml" in answer, answer)
    }
}
