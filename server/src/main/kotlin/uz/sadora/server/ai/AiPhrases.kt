package uz.sadora.server.ai

import uz.sadora.contract.CyclePhase
import uz.sadora.contract.Language

/**
 * Everything the answer layer says, in the language she chose.
 *
 * The same shape as the app's `Strings`: an interface, so a phrase added here is a
 * compile error in the two languages that have not answered it yet, and a question can
 * never come back half-translated. It lives on the server rather than in the app because
 * the rule engine writes its own sentences — the app only displays them.
 *
 * [stems] carries the other half of the problem. The rule engine picks a topic by
 * looking for the words a question actually contains, and those words differ per
 * language: "charch" finds a tired Uzbek question and nothing at all in Russian.
 */
interface AiPhrases {

    /** How the language is named to the model. */
    val modelName: String

    // ------------------------------------------------------------ boundaries

    val disclaimer: String
    val generalNote: String

    /** "<numbers> asosida." — what the answer was allowed to read. */
    fun basedOn(summary: String): String

    // ------------------------------------------------------------ topics

    fun energyLuteal(): String
    fun energyFollicular(waterRemainingMl: Int?): String
    fun energyUnknown(): String
    fun food(kcal: Int?, goal: Int?): String
    fun skin(): String
    fun sleep(minutes: Int?): String
    fun cycleUnknown(): String
    fun cycle(day: Int, phase: CyclePhase, daysUntilNextPeriod: Int?): String
    fun water(remainingMl: Int?): String
    fun stress(): String
    fun fallback(): String

    // ------------------------------------------------------------ the summary line

    fun summaryCycle(day: Int, phase: CyclePhase?): String
    fun summarySleep(minutes: Int): String
    fun summaryWater(millilitres: Int): String
    fun summaryCalories(kcal: Int, goal: Int): String
    fun summarySteps(steps: Int): String

    // ------------------------------------------------------------ the model prompt

    /** The system instruction: the product's rules, stated in the answering language. */
    fun instruction(): String

    /** The user turn, with today's numbers folded in when consent allowed reading them. */
    fun userTurn(question: String, summary: String?): String

    // ------------------------------------------------------------ topic detection

    /** Word stems that route a question to a topic, lowercased. */
    val stems: Stems

    data class Stems(
        val energy: List<String>,
        val food: List<String>,
        val skin: List<String>,
        val sleep: List<String>,
        val cycle: List<String>,
        val water: List<String>,
        val stress: List<String>,
    )

    companion object {
        fun of(language: Language): AiPhrases = when (language) {
            Language.UZ -> AiPhrasesUz
            Language.RU -> AiPhrasesRu
            Language.EN -> AiPhrasesEn
        }
    }
}

/** 1200 → "1,2" — the same one-decimal litre the app shows. */
internal fun litres(ml: Int, separator: Char): String {
    val tenths = (ml + 50) / 100
    return "${tenths / 10}$separator${tenths % 10}"
}
