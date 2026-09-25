package uz.sadora.server.ai

import uz.sadora.contract.CyclePhase

/** Russian, translated from [AiPhrasesUz] — the same claims, the same boundaries. */
object AiPhrasesRu : AiPhrases {

    override val modelName = "Russian"

    override val disclaimer = "Это общая информация — не диагноз."
    override val generalNote = "Общий ответ: без согласия на AI insights ваши данные не читаются."

    override fun basedOn(summary: String) = "На основе: $summary."

    override fun energyLuteal() =
        "Перед менструацией и во время неё изменение прогестерона может влиять на сон и энергию. " +
            "Попробуйте продукты, богатые магнием, лёгкую ходьбу и дыхательные упражнения."

    override fun energyFollicular(waterRemainingMl: Int?) =
        "В этой фазе энергия обычно растёт. Если воды и сна меньше обычного, усталость может быть " +
            "связана с этим" +
            (waterRemainingMl?.let { " — сегодня осталось выпить ещё $it мл." } ?: ".")

    override fun energyUnknown() =
        "Самые частые причины усталости — нехватка сна, мало воды и нерегулярное питание. " +
            "Понаблюдайте за сном и водой три-четыре дня; фаза цикла тоже влияет на энергию."

    override fun food(kcal: Int?, goal: Int?): String {
        val progress = if (kcal != null && goal != null) " Сегодня записано $kcal / $goal ккал." else ""
        return "Для ровной энергии сочетайте белок и сложные углеводы: яйца, йогурт, крупы, овощи." +
            "$progress Составить план питания?"
    }

    override fun skin() =
        "В течение цикла гормоны меняют выработку кожного сала: высыпания перед менструацией — " +
            "обычное дело. Помогают мягкое очищение, достаточно воды и сна. Если держится долго, " +
            "покажитесь дерматологу."

    override fun sleep(minutes: Int?): String {
        val slept = minutes?.let { " Прошлой ночью вы спали ${it / 60} ч ${it % 60} мин." }.orEmpty()
        return "Меньше экрана вечером и один и тот же час отхода ко сну улучшают качество сна." +
            "$slept Продолжайте отслеживать сон."
    }

    override fun cycleUnknown() =
        "Фазы цикла по-разному влияют на энергию, настроение и кожу. После нескольких отслеженных " +
            "циклов приложение сможет показать вашу личную картину."

    override fun cycle(day: Int, phase: CyclePhase, daysUntilNextPeriod: Int?): String {
        val next = daysUntilNextPeriod?.let { " Следующая менструация примерно через $it дн." }.orEmpty()
        return "Сейчас $day-й день цикла — ${phase.label()}. ${phase.energyNote()}$next"
    }

    override fun water(remainingMl: Int?) =
        if (remainingMl != null) {
            "Сегодня до цели осталось ещё $remainingMl мл воды. Пить понемногу в течение дня полезнее, " +
                "чем много за один раз."
        } else {
            "1,5–2 литра воды в день достаточно большинству; в жару и при нагрузке нужно больше."
        }

    override fun stress() =
        "Дыхание четыре-семь-восемь — вдох на четыре секунды, задержка на семь, выдох на восемь — " +
            "успокаивает за несколько минут. Сон и движение тоже заметно снижают стресс. Если состояние " +
            "держится долго, поговорите со специалистом."

    override fun fallback() =
        "Я поняла ваш вопрос. Могу ответить, опираясь на ваши данные о цикле, питании, настроении, сне " +
            "и лекарствах — спросите конкретнее, и я объясню подробнее."

    override fun summaryCycle(day: Int, phase: CyclePhase?) =
        "Цикл, день $day" + (phase?.let { " (${it.label()})" } ?: "")

    override fun summarySleep(minutes: Int) = "сон ${minutes / 60} ч ${minutes % 60} мин"

    override fun summaryWater(millilitres: Int) = "вода ${litres(millilitres, ',')} л"

    override fun summaryCalories(kcal: Int, goal: Int) = "$kcal / $goal ккал"

    override fun summarySteps(steps: Int) = "$steps шагов"

    override fun instruction() = """
        Ты помощница в приложении SADORA. Пользователь — женщина, пишущая по-русски.

        Правила:
        - Отвечай только по-русски, простым и тёплым тоном.
        - Не ставь диагноз, не назначай лекарства и не называй дозировки. На вопрос о
          рецептурном препарате — направь к врачу.
        - Опирайся только на переданные числа. Не выдумывай числа, которых нет, и не
          заявляй «по твоим данным» ни о чём другом.
        - Не утверждай причинно-следственную связь: говори «может быть», «часто связано».
        - Пиши коротко: максимум четыре-пять предложений или короткий список.
        - Если слышишь тревожные признаки (сильная боль, обильное кровотечение, обморок) —
          скажи обратиться к врачу без промедления.
    """.trimIndent()

    override fun userTurn(question: String, summary: String?) =
        if (summary != null) {
            "Её сегодняшние данные: $summary\n\nВопрос: $question"
        } else {
            "Её данных нет — дай общий ответ и скажи об этом.\n\nВопрос: $question"
        }

    override val stems = AiPhrases.Stems(
        energy = listOf("устал", "энерг", "сил нет", "вял", "слаб", "утомл"),
        food = listOf("ест", "еда", "пита", "съест", "блюд", "перекус"),
        skin = listOf("кож", "акне", "прыщ", "высыпан"),
        sleep = listOf("сон", "спать", "сплю", "бессонн", "высып"),
        cycle = listOf("цикл", "месячн", "менструа", "овуляц", "задержк"),
        water = listOf("вод", "пить", "пью"),
        stress = listOf("стресс", "нерв", "тревог", "беспоко", "паник"),
    )

    private fun CyclePhase.label(): String = when (this) {
        CyclePhase.PERIOD -> "менструация"
        CyclePhase.FOLLICULAR -> "фолликулярная фаза"
        CyclePhase.FERTILE -> "период овуляции"
        CyclePhase.LUTEAL -> "лютеиновая фаза"
    }

    private fun CyclePhase.energyNote(): String = when (this) {
        CyclePhase.PERIOD -> "Тело отдыхает — будьте к себе мягче."
        CyclePhase.FOLLICULAR -> "Энергия растёт — отличное время для новых начинаний."
        CyclePhase.FERTILE -> "Энергия на пике — используйте её для активных дней."
        CyclePhase.LUTEAL -> "Энергия постепенно снижается — оставьте время на отдых."
    }
}
