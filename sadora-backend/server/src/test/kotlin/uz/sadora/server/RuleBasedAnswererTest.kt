package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uz.sadora.contract.CyclePhase
import uz.sadora.contract.Language
import uz.sadora.server.ai.AiContext
import uz.sadora.server.ai.AiPhrases
import uz.sadora.server.ai.RuleBasedAnswerer

/**
 * The answerer is what stands in for a model, so the promises the product makes about
 * it are pinned here: it names what it read, it reads nothing without consent, and every
 * answer says it is not a diagnosis — in each of the three languages, because a question
 * asked in Russian used to come back in Uzbek and match none of the topic words.
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
    fun `every answer carries the disclaimer, in every language`() {
        val questions = mapOf(
            Language.UZ to listOf("charchadim", "nima yeyin", "terim", "uyqu", "hayz", "suv", "stress", "salom"),
            Language.RU to listOf("устала", "что поесть", "кожа", "сон", "месячные", "вода", "стресс", "привет"),
            Language.EN to listOf("tired", "what to eat", "skin", "sleep", "period", "water", "stress", "hello"),
        )
        questions.forEach { (language, asked) ->
            val disclaimer = AiPhrases.of(language).disclaimer
            asked.forEach { question ->
                assertTrue(disclaimer in RuleBasedAnswerer.answer(question, context, language), "$language: $question")
                assertTrue(disclaimer in RuleBasedAnswerer.answer(question, null, language), "$language: $question")
            }
        }
    }

    @Test
    fun `with a context the answer names the numbers it used`() {
        val answer = RuleBasedAnswerer.answer("Nega charchayapman?", context, Language.UZ)
        assertTrue("Sikl 14-kun" in answer, answer)
        assertTrue("uyqu 6s 40d" in answer, answer)
        assertTrue("suv 1,2 l" in answer, answer)
    }

    @Test
    fun `the basis line is written in the answering language`() {
        val russian = RuleBasedAnswerer.answer("Почему я устала?", context, Language.RU)
        assertTrue("Цикл, день 14" in russian, russian)
        assertTrue("сон 6 ч 40 мин" in russian, russian)
        assertTrue("вода 1,2 л" in russian, russian)

        val english = RuleBasedAnswerer.answer("Why am I tired?", context, Language.EN)
        assertTrue("cycle day 14" in english, english)
        assertTrue("sleep 6h 40m" in english, english)
        assertTrue("water 1.2 l" in english, english)
    }

    /** The whole point of per-language stems: the topic has to be found, not fallen through. */
    @Test
    fun `a question finds its topic in each language`() {
        assertTrue("800" in RuleBasedAnswerer.answer("Suv ichishim kerakmi?", context, Language.UZ))
        assertTrue("800" in RuleBasedAnswerer.answer("Сколько воды мне пить?", context, Language.RU))
        assertTrue("800" in RuleBasedAnswerer.answer("Should I drink more water?", context, Language.EN))

        assertTrue("овуляц" in RuleBasedAnswerer.answer("Когда месячные?", context, Language.RU))
        assertTrue("fertile window" in RuleBasedAnswerer.answer("When is my period?", context, Language.EN))
    }

    @Test
    fun `without consent nothing personal appears and the answer says so`() {
        Language.entries.forEach { language ->
            val answer = RuleBasedAnswerer.answer("Nega charchayapman?", null, language)
            assertFalse("14" in answer, answer)
            assertTrue(AiPhrases.of(language).generalNote in answer, answer)
        }
    }

    @Test
    fun `an empty context reads as no context rather than as zeros`() {
        val answer = RuleBasedAnswerer.answer("Bugun nima yeyin?", AiContext(), Language.UZ)
        assertFalse("/ " in answer.substringBefore("\n"), answer)
        assertTrue(AiPhrases.of(Language.UZ).generalNote in answer, answer)
    }

    @Test
    fun `the cycle answer follows the phase in the context`() {
        val answer = RuleBasedAnswerer.answer("Hayzim qachon?", context, Language.UZ)
        assertTrue("14-kuni" in answer, answer)
        assertTrue("ovulyatsiya davri" in answer, answer)
        assertTrue("15 kundan keyin" in answer, answer)
    }

    @Test
    fun `the water answer does the arithmetic from the context`() {
        val answer = RuleBasedAnswerer.answer("Suv ichishim kerakmi?", context, Language.UZ)
        assertTrue("800 ml" in answer, answer)
    }
}
