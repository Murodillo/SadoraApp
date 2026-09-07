package org.example.project.i18n

import org.example.project.model.CyclePhase
import org.example.project.model.LifeStage
import org.example.project.model.Mood

/**
 * Русский. Translated from [StringsUz], not from English.
 *
 * The tab labels are the tightest constraint on the screen — five of them share one
 * row — so a long word is shortened here rather than in the layout: "Этап" for the
 * perimenopause tab, while the settings row spells "Перименопауза" in full.
 */
object StringsRu : Strings {
    override val languageName = object : LanguageNames {}

    override val tabs = object : TabStrings {
        override val today = "Сегодня"
        override val mind = "Разум"
        override val nutrition = "Питание"
        override val profile = "Профиль"
        override fun journey(stage: LifeStage) = when (stage) {
            LifeStage.Cycle -> "Цикл"
            LifeStage.TryingToConceive -> "План"
            LifeStage.Pregnancy -> "Беременна"
            LifeStage.Postpartum -> "После родов"
            LifeStage.Perimenopause -> "Этап"
            LifeStage.Menopause -> "Здоровье"
        }
    }

    override val stages = object : LifeStageStrings {
        override fun title(stage: LifeStage) = when (stage) {
            LifeStage.Cycle -> "Отслеживание цикла"
            LifeStage.TryingToConceive -> "Планирование беременности"
            LifeStage.Pregnancy -> "Беременность"
            LifeStage.Postpartum -> "После родов"
            LifeStage.Perimenopause -> "Перименопауза"
            LifeStage.Menopause -> "Менопауза"
        }
    }

    override val welcome = object : WelcomeStrings {
        override val title = "Добро пожаловать в SADORA"
        override val subtitle = "Ваш личный помощник по здоровью, циклу, питанию и настроению."
        override val featureCycle = "Цикл"
        override val featureMood = "Настроение"
        override val featureNutrition = "Питание"
        override val featureMeds = "Витамины и лекарства"
        override val featureAi = "SADORA AI"
        override val featureInsights = "Аналитика и советы"
        override val privacyPromise = "Ваши данные принадлежат вам — удалите их в любой момент"
        override val start = "Начать"
        override val haveAccount = "У меня есть аккаунт →"
        override val signIn = "Войти"
    }

    override val onboarding = object : OnboardingStrings {
        override val languageTitle = "Выберите язык"
        override val languageSubtitle = "Позже это можно изменить в настройках."
        override val continueLabel = "Продолжить"
    }

    override val profile = object : ProfileStrings {
        override val title = "Профиль"
        override val unnamed = "Пользователь"

        override val sleep = "Сон"
        override val medications = "Лекарства"
        override val secretChat = "Секретный чат"
        override val insights = "Аналитика"
        override val knowledge = "Знания"

        override val personalDetails = "Личные данные"
        override val goals = "Цели"
        override val lifeStage = "Этап жизни"
        override val connectedDevices = "Подключённые устройства"
        override val notifications = "Уведомления"
        override val privacyAndSecurity = "Приватность и безопасность"

        override val language = "Язык"
        override val theme = "Тема"
        override val themeDark = "Тёмная"
        override val themeLight = "Светлая"
        override val about = "О SADORA"

        override val signOut = "Выйти"
        override val signingOut = "Выходим…"

        override val premiumBadge = "SADORA PREMIUM"
        override val premiumActive = "Активна"
        override val premiumYearly = "Годовая подписка"
        override val premiumFeatureAi = "AI-чат"
        override val premiumFeatureScanner = "Сканер еды"
        override val premiumFeatureInsights = "Расширенная аналитика"
        override val upgradeTitle = "SADORA Premium"
        override val upgradeSubtitle = "AI-чат, сканер еды и расширенная аналитика"
    }

    override val settings = object : SettingsStrings {
        override val aboutTitle = "О SADORA"
        override fun version(number: String) = "Версия $number"
        override val languageTitle = "Язык"
        override val languageNote = "Язык приложения меняется сразу. Ответы AI пока только " +
            "на узбекском."
        override val languageSaveFailed = "Язык не сохранён — попробуйте позже."
    }

    override val common = object : CommonStrings {
        override fun greeting(hour: Int) = when (hour) {
            in 5..11 -> "Доброе утро"
            in 12..17 -> "Добрый день"
            else -> "Добрый вечер"
        }

        override fun mood(mood: Mood) = when (mood) {
            Mood.Bad -> "Тяжело"
            Mood.Low -> "Вяло"
            Mood.Ok -> "Нормально"
            Mood.Good -> "Спокойно"
            Mood.Great -> "Отлично"
        }

        override fun moodCaption(mood: Mood) = when (mood) {
            Mood.Bad -> "Будьте сегодня добрее к себе."
            Mood.Low -> "День помедленнее — это тоже нормально."
            Mood.Ok -> "Обычный день для равновесия."
            Mood.Good -> "Хороший день для равновесия."
            Mood.Great -> "Энергии много — воспользуйтесь этим!"
        }

        override fun phase(phase: CyclePhase) = when (phase) {
            CyclePhase.Period -> "Менструация"
            CyclePhase.Follicular -> "Фолликулярная"
            CyclePhase.Fertile -> "Овуляция"
            CyclePhase.Luteal -> "Лютеиновая"
        }

        override fun phaseFertility(phase: CyclePhase) = when (phase) {
            CyclePhase.Period -> "Вероятность зачатия низкая"
            CyclePhase.Follicular -> "Вероятность зачатия растёт"
            CyclePhase.Fertile -> "Вероятность зачатия высокая"
            CyclePhase.Luteal -> "Вероятность зачатия снижается"
        }

        override fun phaseEnergy(phase: CyclePhase) = when (phase) {
            CyclePhase.Period -> "Тело отдыхает — будьте к себе мягче."
            CyclePhase.Follicular -> "Энергия растёт — удачное время для нового."
            CyclePhase.Fertile -> "Энергия на пике — используйте активные дни."
            CyclePhase.Luteal -> "Энергия постепенно спадает — оставьте место для отдыха."
        }

        override val save = "Сохранить"
        override val cancel = "Отмена"
        override val delete = "Удалить"
        override val close = "Закрыть"
        override val add = "Добавить"
        override val edit = "Изменить"
        override val done = "Готово"

        override val litres = "л"
        override val millilitres = "мл"
        override val kcal = "ккал"
        override val steps = "шагов"
        override val minutesShort = "мин"
        override fun days(count: Int) = "$count дн."
    }

    override val today = object : TodayStrings {
        override fun greetingLine(greeting: String) =
            "$greeting — отличный день, чтобы позаботиться о себе 🌸"

        override val aiFootnote = "На основе ваших данных · создано ИИ"
        override val aiFreePrompt = "Задайте любой вопрос о здоровье и самочувствии"

        override val cycleCard = "Цикл"
        override val notEnoughForPrediction = "Данных для прогноза недостаточно"
        override fun cycleDayOf(day: Int, length: Int) = "День $day / $length"
        override fun pregnancyWeek(week: Int) = "$week-я неделя"

        override val quickActions = "Быстрые действия"
        override val journal = "Дневник"
        override val meditation = "Медитация"
        override val breathing = "Дыхание"
        override val reminders = "Напоминания"

        override val summary = "Итог дня"
        override fun phaseSentence(day: Int, phase: String) = "$day-й день цикла — $phase."
        override fun waterRemaining(ml: Int) = "Вода: осталось выпить $ml мл."
        override val waterGoalMet = "Цель по воде достигнута."
        override fun doseDue(name: String, time: String) = "$name — в $time."
        override fun sleptAndEnergy(sleep: String, energyIsHigh: Boolean) =
            "Вы спали $sleep, энергия " + (if (energyIsHigh) "хорошая" else "пониже") + "."
        override val generalAdvice = "Сегодня пейте больше воды и запланируйте лёгкую прогулку."

        override val plan = "План на сегодня"
        override val taken = "Приняла"
        override val water = "Вода"
        override fun waterLeft(ml: Int) = "ещё $ml мл"
        override fun addWater(ml: Int) = "+$ml мл"

        override val healthScore = "Показатель здоровья"
        override val sleep = "Сон"
        override val mood = "Настроение"
        override val steps = "Шаги"
        override fun scoreWord(score: Int) = when {
            score >= 80 -> "Отлично"
            score >= 60 -> "Хорошо"
            score >= 40 -> "Средне"
            else -> "Низко"
        }

        override val emptySummaryTitle = "Итог дня"
        override val emptySummaryBody =
            "Пока данных нет. Добавьте первую отметку — и здесь появятся дневной итог " +
                "и графики."
        override val startTitle = "Начнём с сегодняшнего дня?"
        override val startBody = "Настроение, вода или еда — с чего вам удобнее начать."
        override val startAction = "Добавить первую отметку"
    }

    override val mind = object : MindStrings {
        override val title = "Состояние и настроение"
        override fun todayIs(date: String) = "Сегодня · $date"

        override val stress = "Стресс"
        override val energy = "Энергия"
        override val levels = listOf("Очень низкий", "Низкий", "Средний", "Высокий", "Очень высокий")

        override val journal = "Дневник"
        override val journalPrompt = "Как вы себя чувствуете?"
        override val journalHint = "Запишите мысли и чувства"

        override val moodWeek = "Настроение за 7 дней"
        override fun weekAverage(value: String) = "В среднем $value"

        override val assistant = "Помощник по состоянию"
        override val assistantPremium = "Поговорите о связи настроения и сна"
        override val assistantFree = "В Premium: поддерживающий разговор — не терапевт"
        override val mood = "Настроение"

        override val breathing = "Дыхание"
        override val breathingPurpose = "Снизить стресс"
        override val meditation = "Медитация"
        override val meditationSubtitle = "Спокойный ум"
        override val meditationPurpose = "Отдых"
        override val fourSevenEight = "4-7-8"
        override fun practiceMeta(minutes: Int, purpose: String) = "$minutes мин • $purpose"
        override val start = "Начать"

        override val breathIn = "Вдохните"
        override val breathHold = "Задержите"
        override val breathOut = "Выдохните"
        override val breathingHint = "4 секунды вдох · 7 задержка · 8 выдох"
        override val meditationHint = "Закройте глаза и следите за дыханием"
        override val finish = "Завершить"
        override val close = "Закрыть"
    }

    override val nutrition = object : NutritionStrings {
        override val title = "Питание"
        override val insights = "Аналитика"
        override val meals = "Приёмы пищи"
        override val addMeal = "Добавить приём пищи"
        override val emptyTitle = "Сегодня приёмов пищи ещё нет"
        override val emptyBody = "Добавьте первое блюдо — калории и макросы соберутся здесь."

        override val water = "Вода"
        override fun waterOfGoal(drunk: String, goal: String) = "$drunk л / $goal л"
        override fun addWater(ml: Int) = "+$ml мл"

        override val aiAnalysis = "Анализ ИИ"
        override val aiBasis = "Рассчитано по вашим сегодняшним данным"
        override val scanner = "Сканер еды"
        override val scannerHint = "Наведите камеру — блюдо, порция и макросы определятся примерно"
        override val balance = "Баланс"
        override val balanceHint = "Еда, вода, активность и сон — четыре направления"

        override val today = "Сегодня"
        override val protein = "Белки"
        override val fat = "Жиры"
        override val carbs = "Углеводы"
        override val proteinInline = "белка"
        override val fatInline = "жиров"
        override val carbsInline = "углеводов"

        override fun balanced(kcalLeft: Int) =
            "Макросы сегодня в балансе. На оставшиеся $kcalLeft ккал хватит лёгкого блюда."
        override fun shortOf(macro: String) =
            "Сегодня больше всего не хватает $macro. Обратите на это внимание в следующем приёме."
        override fun kcal(value: Int) = "$value ккал"
        override fun grams(value: Int) = "$value г"
    }
}
