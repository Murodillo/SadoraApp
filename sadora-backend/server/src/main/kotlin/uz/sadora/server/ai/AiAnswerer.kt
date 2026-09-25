package uz.sadora.server.ai

import uz.sadora.contract.CyclePhase
import uz.sadora.contract.Language

/**
 * What an answer may draw on. Every field is optional because every source can be
 * absent — no wearable, no meals logged today, a stage with no cycle.
 */
data class AiContext(
    val cycleDay: Int? = null,
    val phase: CyclePhase? = null,
    val daysUntilNextPeriod: Int? = null,
    val sleepMinutes: Int? = null,
    val steps: Int? = null,
    val waterMl: Int? = null,
    val waterGoalMl: Int? = null,
    val kcal: Int? = null,
    val kcalGoal: Int? = null,
) {
    /**
     * "Sikl 14-kun (follikulyar faza), uyqu 6s 40d, suv 1,2 l" — what the answer used,
     * written in the language it will be shown in. The app prints this under the reply,
     * so a Russian answer with an Uzbek basis line reads like a bug.
     */
    fun summary(phrases: AiPhrases): String = buildList {
        if (cycleDay != null) add(phrases.summaryCycle(cycleDay, phase))
        sleepMinutes?.let { add(phrases.summarySleep(it)) }
        waterMl?.let { add(phrases.summaryWater(it)) }
        if (kcal != null && kcalGoal != null) add(phrases.summaryCalories(kcal, kcalGoal))
        steps?.let { add(phrases.summarySteps(it)) }
    }.joinToString(", ")

    val isEmpty: Boolean
        get() = cycleDay == null && sleepMinutes == null && waterMl == null && kcal == null && steps == null
}

/** Produces the text of a reply. The rule engine is the first implementation; a model is the next. */
fun interface AiAnswerer {
    fun answer(question: String, context: AiContext?, language: Language): String
}

/**
 * The rule-based answerer.
 *
 * Deliberately narrow: it keys on the words a question in each area actually contains,
 * reads only the numbers in [AiContext], names which of them it used, and never
 * diagnoses. Without a context — no consent to AI insights — it answers in general
 * terms and says so, rather than pretending to know her.
 *
 * The words it keys on are per language ([AiPhrases.stems]): a Russian question about
 * tiredness contains none of the Uzbek stems, and before that was true every non-Uzbek
 * question fell through to the generic answer.
 */
object RuleBasedAnswerer : AiAnswerer {

    override fun answer(question: String, context: AiContext?, language: Language): String {
        val phrases = AiPhrases.of(language)
        val stems = phrases.stems
        val q = question.lowercase()
        val body = when {
            matches(q, stems.energy) -> energyAnswer(context, phrases)
            matches(q, stems.food) -> phrases.food(context?.kcal, context?.kcalGoal)
            matches(q, stems.skin) -> phrases.skin()
            matches(q, stems.sleep) -> phrases.sleep(context?.sleepMinutes)
            matches(q, stems.cycle) -> cycleAnswer(context, phrases)
            matches(q, stems.water) -> phrases.water(context.waterRemaining())
            matches(q, stems.stress) -> phrases.stress()
            else -> phrases.fallback()
        }
        val basis = context?.takeUnless { it.isEmpty }?.summary(phrases)
        val footer = if (basis != null) {
            "${phrases.basedOn(basis)} ${phrases.disclaimer}"
        } else {
            "${phrases.generalNote} ${phrases.disclaimer}"
        }
        return "$body\n\n$footer"
    }

    private fun matches(question: String, stems: List<String>) = stems.any { it in question }

    private fun energyAnswer(context: AiContext?, phrases: AiPhrases): String =
        when (context?.phase) {
            CyclePhase.LUTEAL, CyclePhase.PERIOD -> phrases.energyLuteal()
            CyclePhase.FOLLICULAR, CyclePhase.FERTILE -> phrases.energyFollicular(context.waterRemaining())
            null -> phrases.energyUnknown()
        }

    private fun cycleAnswer(context: AiContext?, phrases: AiPhrases): String {
        val day = context?.cycleDay
        val phase = context?.phase
        if (day == null || phase == null) return phrases.cycleUnknown()
        return phrases.cycle(day, phase, context.daysUntilNextPeriod)
    }

    private fun AiContext?.waterRemaining(): Int? {
        val drunk = this?.waterMl ?: return null
        val goal = this.waterGoalMl ?: return null
        return (goal - drunk).coerceAtLeast(0)
    }
}
