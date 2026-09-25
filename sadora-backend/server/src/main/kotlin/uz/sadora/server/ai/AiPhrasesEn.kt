package uz.sadora.server.ai

import uz.sadora.contract.CyclePhase

/** English, translated from [AiPhrasesUz] — the same claims, the same boundaries. */
object AiPhrasesEn : AiPhrases {

    override val modelName = "English"

    override val disclaimer = "This is general information — not a diagnosis."
    override val generalNote = "General answer: without the AI insights consent your data is not read."

    override fun basedOn(summary: String) = "Based on $summary."

    override fun energyLuteal() =
        "Before and during your period, shifting progesterone can affect sleep and energy. " +
            "Try magnesium-rich food, a gentle walk and breathing exercises."

    override fun energyFollicular(waterRemainingMl: Int?) =
        "Energy usually rises in this phase. If water and sleep are lower than usual, the tiredness " +
            "may be coming from there" +
            (waterRemainingMl?.let { " — $it ml of water left to drink today." } ?: ".")

    override fun energyUnknown() =
        "The most common causes of tiredness are too little sleep, too little water and irregular " +
            "meals. Watch your sleep and water for three or four days; the cycle phase affects energy too."

    override fun food(kcal: Int?, goal: Int?): String {
        val progress = if (kcal != null && goal != null) " $kcal / $goal kcal logged today." else ""
        return "For steady energy, pair protein with complex carbohydrates: eggs, yoghurt, grains, " +
            "vegetables.$progress Shall I put a meal plan together?"
    }

    override fun skin() =
        "Hormones change how much oil your skin produces across the cycle: breakouts before a period " +
            "are ordinary. Gentle cleansing, enough water and enough sleep help. If it lasts, see a " +
            "dermatologist."

    override fun sleep(minutes: Int?): String {
        val slept = minutes?.let { " You slept ${it / 60}h ${it % 60}m last night." }.orEmpty()
        return "Less screen in the evening and the same bedtime every day improve sleep quality." +
            "$slept Keep tracking your sleep."
    }

    override fun cycleUnknown() =
        "Cycle phases affect energy, mood and skin differently. After a few tracked cycles the app " +
            "can show you your own pattern."

    override fun cycle(day: Int, phase: CyclePhase, daysUntilNextPeriod: Int?): String {
        val next = daysUntilNextPeriod?.let { " Your next period is roughly $it days away." }.orEmpty()
        return "You are on day $day of your cycle — ${phase.label()}. ${phase.energyNote()}$next"
    }

    override fun water(remainingMl: Int?) =
        if (remainingMl != null) {
            "$remainingMl ml of water left to reach today's goal. Small amounts across the day work " +
                "better than a lot at once."
        } else {
            "1.5–2 litres of water a day is enough for most people; hot days and activity need more."
        }

    override fun stress() =
        "The four-seven-eight breath — in for four seconds, hold for seven, out for eight — settles " +
            "you within minutes. Sleep and movement lower stress noticeably too. If it lasts, talk to " +
            "a professional."

    override fun fallback() =
        "I understand your question. I can answer from your cycle, nutrition, mood, sleep and " +
            "medication data — ask more specifically and I will go into detail."

    override fun summaryCycle(day: Int, phase: CyclePhase?) =
        "cycle day $day" + (phase?.let { " (${it.label()})" } ?: "")

    override fun summarySleep(minutes: Int) = "sleep ${minutes / 60}h ${minutes % 60}m"

    override fun summaryWater(millilitres: Int) = "water ${litres(millilitres, '.')} l"

    override fun summaryCalories(kcal: Int, goal: Int) = "$kcal / $goal kcal"

    override fun summarySteps(steps: Int) = "$steps steps"

    override fun instruction() = """
        You are the assistant inside the SADORA app. The user is a woman writing in English.

        Rules:
        - Answer only in English, in plain and warm language.
        - Do not diagnose, do not prescribe, do not name a dose. For a question about a
          prescription medicine, point her to her doctor.
        - Rely only on the numbers you are given. Do not invent a number you were not
          given, and do not claim anything else "from her data".
        - Do not assert causation: say "may be", "is often linked to".
        - Keep it short: four or five sentences at most, or a short list.
        - If you hear a red flag (severe pain, heavy bleeding, fainting) — tell her to
          see a doctor without delay.
    """.trimIndent()

    override fun userTurn(question: String, summary: String?) =
        if (summary != null) {
            "Her data today: $summary\n\nQuestion: $question"
        } else {
            "She has no data — answer in general terms and say so.\n\nQuestion: $question"
        }

    override val stems = AiPhrases.Stems(
        energy = listOf("tired", "energy", "exhaust", "fatigue", "worn out"),
        food = listOf("eat", "food", "meal", "diet", "snack"),
        skin = listOf("skin", "acne", "breakout", "pimple"),
        sleep = listOf("sleep", "slept", "insomnia", "rest"),
        cycle = listOf("cycle", "period", "menstrua", "ovulat", "late"),
        water = listOf("water", "drink", "hydrat"),
        stress = listOf("stress", "anxious", "anxiety", "worry", "panic", "nerv"),
    )

    private fun CyclePhase.label(): String = when (this) {
        CyclePhase.PERIOD -> "your period"
        CyclePhase.FOLLICULAR -> "the follicular phase"
        CyclePhase.FERTILE -> "the fertile window"
        CyclePhase.LUTEAL -> "the luteal phase"
    }

    private fun CyclePhase.energyNote(): String = when (this) {
        CyclePhase.PERIOD -> "Your body is resting — be gentle with yourself."
        CyclePhase.FOLLICULAR -> "Energy is climbing — a good time for new starts."
        CyclePhase.FERTILE -> "Energy is at its peak — use it for the active days."
        CyclePhase.LUTEAL -> "Energy tapers off — leave yourself time to rest."
    }
}
