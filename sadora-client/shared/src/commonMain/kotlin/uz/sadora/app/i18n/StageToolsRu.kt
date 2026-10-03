package uz.sadora.app.i18n

import uz.sadora.contract.FeedingSide
import uz.sadora.contract.GoalsBasis
import uz.sadora.contract.HotFlushTrigger

object StageToolsRu : StageToolsStrings {

    override val fertileTodayTitle = "Сегодня фертильные дни"
    override fun fertileTodayBody(ovulation: String) =
        "Овуляция примерно $ovulation. В эти дни вероятность забеременеть самая высокая."
    override fun fertileInDays(days: Int) = if (days == 1) "Фертильное окно завтра" else "Фертильное окно через $days дн."
    override fun fertileWindow(from: String, to: String, ovulation: String) =
        "$from – $to, овуляция примерно $ovulation"
    override val fertilePassedTitle = "Фертильное окно прошло"
    override fun fertilePassedBody(nextPeriod: String) =
        "Следующие месячные ожидаются около $nextPeriod. Если задержатся — сделайте тест на беременность."
    override fun periodLate(days: Int) = "Задержка $days дн."
    override val periodLateBody =
        "Время сделать тест на беременность — по первой утренней моче результат точнее."
    override val periodLateCycleBody =
        "Задержка бывает из-за стресса, болезни или беременности. Если больше недели — сделайте тест или обратитесь к врачу."
    override val pregnantButton = "Я беременна"
    override val periodStartedButton = "Месячные начались"
    override fun dueFromPeriod(date: String) = "Рассчитать по последним месячным: $date"

    override val birthPromptTitle = "Малыш родился?"
    override val birthPromptBody =
        "Перейдите на этап после родов — откроются восстановление, кормление и наблюдение за настроением."
    override val birthPromptButton = "Перейти на этап после родов"
    override val cycleBackTitle = "Вернуться к отслеживанию цикла?"
    override val cycleBackBody =
        "Если месячные вернулись, отслеживание цикла рассчитает следующие месячные и фертильные дни."
    override val cycleBackButton = "Перейти к отслеживанию цикла"

    override val flagsTitle = "Стоит обсудить с врачом"
    override fun shortCycles(days: Int) = "Ваш цикл в среднем $days дн. — короче 21 дня."
    override fun longCycles(days: Int) = "Ваш цикл в среднем $days дн. — длиннее 35 дней."
    override fun irregularCycles(spread: Int) = "Длина ваших циклов различается до $spread дн."
    override fun longPeriods(days: Int) = "Месячные длятся в среднем $days дн. — дольше 7 дней."
    override val bleedingAfterMenopause =
        "За последние 12 месяцев отмечено кровотечение. В менопаузе любое кровотечение должен оценить врач."
    override val flagsNote = "Это не диагноз — лишь признак в ваших записях."
    override val menopauseBleedingTitle = "Кровотечение"
    override val menopauseBleedingBody =
        "Если после менопаузы появилось кровотечение, даже небольшое, обратитесь к врачу. Отметьте этот день."
    override val menopauseBleedingButton = "Отметить кровотечение"

    override fun goalsBasis(basis: GoalsBasis) = when (basis) {
        GoalsBasis.PREGNANCY_FIRST_TRIMESTER -> "Цели подобраны для 1-го триместра беременности"
        GoalsBasis.PREGNANCY_SECOND_TRIMESTER -> "Цели подобраны для 2-го триместра беременности"
        GoalsBasis.PREGNANCY_THIRD_TRIMESTER -> "Цели подобраны для 3-го триместра беременности"
        GoalsBasis.BREASTFEEDING -> "Цели подобраны для грудного вскармливания — если вы не кормите грудью, измените их"
    }

    override val feedingTitle = "Кормление"
    override val feedingIntro = "Выберите, какой грудью кормите, — время посчитается само."
    override fun side(side: FeedingSide) = when (side) {
        FeedingSide.LEFT -> "Левая"
        FeedingSide.RIGHT -> "Правая"
        FeedingSide.BOTTLE -> "Бутылочка"
        FeedingSide.PUMP -> "Сцеженное"
    }
    override fun lastFeed(ago: String, side: String) = "Последнее $ago назад · $side"
    override fun feedsToday(count: Int) = "Сегодня: $count"
    override val noFeedsToday = "Сегодня ещё не отмечено"
    override val startFeed = "Начать"
    override val stopFeed = "Закончить"
    override fun feedRunning(side: String) = "Кормление: $side"
    override val bottleTitle = "Бутылочка или сцеженное молоко"
    override val millilitres = "мл"
    override val saveBottle = "Сохранить"
    override val nothingToday = "Сегодня пока ничего."

    override val screenTitle = "Опросник настроения"
    override val screenCardTitle = "Проверить настроение"
    override val screenCardBody =
        "10 вопросов, 2 минуты. Помогает рано заметить послеродовую депрессию."
    override fun lastScreen(date: String, score: Int) = "Последний раз $date · балл $score"
    override val screenIntro =
        "Как вы себя чувствовали последние 7 дней? Выберите ответ, который ближе всего."
    override val questions = listOf(
        "Я могла смеяться и видеть забавную сторону вещей",
        "Я с удовольствием ждала чего-то",
        "Я без причины винила себя, когда что-то шло не так",
        "Я тревожилась или беспокоилась без повода",
        "Я чувствовала страх или панику без серьёзной причины",
        "Дела наваливались на меня",
        "Я была настолько несчастна, что мне было трудно уснуть",
        "Мне было грустно или тоскливо",
        "Я была настолько несчастна, что плакала",
        "Мне приходила мысль причинить себе вред",
    )
    override val options = listOf(
        listOf("Как и всегда", "Не совсем так, как раньше", "Определённо меньше", "Совсем нет"),
        listOf("Как и раньше", "Несколько меньше, чем раньше", "Определённо меньше", "Почти нет"),
        listOf("Да, большую часть времени", "Да, иногда", "Не очень часто", "Нет, никогда"),
        listOf("Нет, совсем нет", "Почти никогда", "Да, иногда", "Да, очень часто"),
        listOf("Да, довольно часто", "Да, иногда", "Нет, не особенно", "Нет, совсем нет"),
        listOf(
            "Да, большую часть времени я совсем не справлялась",
            "Да, иногда я справлялась хуже обычного",
            "Нет, большую часть времени справлялась хорошо",
            "Нет, справлялась как всегда",
        ),
        listOf("Да, большую часть времени", "Да, иногда", "Не очень часто", "Нет, совсем нет"),
        listOf("Да, большую часть времени", "Да, довольно часто", "Не очень часто", "Нет, совсем нет"),
        listOf("Да, большую часть времени", "Да, довольно часто", "Только иногда", "Нет, никогда"),
        listOf("Да, довольно часто", "Иногда", "Почти никогда", "Никогда"),
    )
    override val screenSubmit = "Посмотреть результат"
    override fun screenScore(score: Int) = "Балл: $score / 30"
    override val screenLow =
        "Признаков депрессии пока мало. Если настроение изменится, пройдите опросник снова через 2–4 недели."
    override val screenPossible =
        "Возможны признаки депрессии. Поговорите с врачом и пройдите опросник снова через 2 недели."
    override val screenLikely =
        "Высокая вероятность послеродовой депрессии. Как можно скорее обратитесь к врачу или психологу — это лечится."
    override val screenSelfHarm =
        "Вы ответили, что у вас бывают мысли причинить себе вред. Это серьёзно: прямо сейчас расскажите кому-то из близких и обратитесь к врачу. Если есть опасность, звоните 103."
    override val askDoctor = "Спросить врача"
    override val screenSource =
        "Эдинбургская шкала послеродовой депрессии (EPDS; Cox, Holden, Sagovsky, 1987). Не диагноз — инструмент скрининга."
    override fun answeredOf(answered: Int, total: Int) = "$answered / $total"

    override val kicksTitle = "Подсчёт шевелений"
    override val kicksCardBody = "С 28-й недели раз в день: за сколько времени вы почувствуете 10 шевелений."
    override val kicksIntro =
        "Когда малыш активен, лягте на бок и нажимайте кнопку при каждом шевелении. Обычно 10 шевелений ощущаются за 2 часа."
    override val kickTap = "Шевеление"
    override fun kicksCount(count: Int, goal: Int) = "$count / $goal"
    override val kicksFinish = "Закончить и сохранить"
    override fun kicksResult(count: Int, duration: String) = "Шевелений: $count · $duration"
    override val kicksSlow =
        "10 шевелений не набралось за 2 часа. Без промедления обратитесь к врачу или в роддом."
    override val previousCounts = "Прошлые подсчёты"

    override val contractionsTitle = "Таймер схваток"
    override val contractionsCardBody = "Считает, сколько длятся схватки и как часто они приходят."
    override val contractionsIntro =
        "Нажмите «Началась», когда схватка начинается, и «Закончилась», когда проходит."
    override val contractionStart = "Началась"
    override val contractionStop = "Закончилась"
    override fun contractionLasted(duration: String) = "длилась $duration"
    override fun contractionApart(interval: String) = "через $interval после предыдущей"
    override fun contractionsSummary(count: Int, duration: String, interval: String) =
        "За последний час $count: в среднем $duration, каждые $interval"
    override val contractionsGo =
        "Схватки чаще чем раз в 5 минут, длятся дольше минуты и продолжаются уже час — пора в роддом."
    override val contractionsUrgent =
        "Если отошли воды, началось кровотечение или малыш стал меньше шевелиться — сразу в роддом, не дожидаясь схваток."
    override fun minutesSeconds(minutes: Int, seconds: Int) =
        if (minutes == 0) "$seconds с" else "$minutes мин ${seconds.toString().padStart(2, '0')} с"

    override val hotFlushTitle = "Приливы"
    override val hotFlushLog = "Отметить"
    override fun intensity(level: Int) = when (level) {
        1 -> "Слабый"
        2 -> "Средний"
        else -> "Сильный"
    }
    override fun trigger(trigger: HotFlushTrigger) = when (trigger) {
        HotFlushTrigger.HEAT -> "Жаркое помещение"
        HotFlushTrigger.HOT_DRINK -> "Горячий напиток"
        HotFlushTrigger.SPICY_FOOD -> "Острая еда"
        HotFlushTrigger.CAFFEINE -> "Кофеин"
        HotFlushTrigger.ALCOHOL -> "Алкоголь"
        HotFlushTrigger.STRESS -> "Стресс"
        HotFlushTrigger.NIGHT -> "Ночью"
    }
    override val triggerQuestion = "Что могло его вызвать?"
    override fun hotFlushCounts(today: Int, week: Int) = "Сегодня $today · за 7 дней $week"
    override fun commonTrigger(name: String) = "Чаще всего: $name"
    override val hotFlushSaved = "Отмечено"
    override fun ago(hours: Int, minutes: Int) = if (hours == 0) "$minutes мин" else "$hours ч $minutes мин"
}
