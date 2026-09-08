package uz.sadora.app.i18n

import kotlinx.datetime.LocalDate
import uz.sadora.app.model.BirthControl
import uz.sadora.app.model.CommunityFilter
import uz.sadora.app.model.CommunityTopic
import uz.sadora.app.model.ConceptionWindow
import uz.sadora.app.model.CyclePhase
import uz.sadora.app.model.Goal
import uz.sadora.app.model.LifeStage
import uz.sadora.app.model.Mood
import uz.sadora.app.model.ReportReason
import uz.sadora.contract.ArticleKind
import uz.sadora.contract.DoseStatus
import uz.sadora.contract.FetalMovement
import uz.sadora.contract.FoodRelation
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.MealSlot
import uz.sadora.contract.ScheduleKind
import uz.sadora.contract.SymptomCategory

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

        override fun subtitle(stage: LifeStage) = when (stage) {
            LifeStage.Cycle -> "Менструация, овуляция, симптомы"
            LifeStage.TryingToConceive -> "Фертильные дни, подготовка"
            LifeStage.Pregnancy -> "Неделя, рост, визиты"
            LifeStage.Postpartum -> "Восстановление, сон, настроение"
            LifeStage.Perimenopause -> "Регулярность, симптомы"
            LifeStage.Menopause -> "Здоровье и настроение"
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
        override val skip = "Пропустить"
        override val back = "Назад"
        override val skipTheseQuestions = "Пропустить эти вопросы"

        override val nameTitle = "Как к вам обращаться?"
        override val nameSubtitle = "Давайте познакомимся. Имя можно будет изменить."
        override val nameLabel = "Имя"
        override val nameHint = "Ваше имя"
        override val nameNote = "Ваши данные хранятся только внутри SADORA. Третьим лицам " +
            "они не передаются, и вы можете удалить их в любой момент."

        override val birthYearTitle = "В каком году вы родились?"
        override val birthYearSubtitle = "Возраст делает прогнозы точнее."

        override val goalsTitle = "В чём вам помочь?"
        override val goalsSubtitle = "Выберите сколько хотите."

        override val stageTitle = "На каком вы этапе сейчас?"
        override val stageSubtitle = "Следующие вопросы и само приложение подстроятся под ваш выбор."
        override fun stagePromise(stage: LifeStage) = when (stage) {
            LifeStage.Cycle -> "Будем следить за циклом и заранее говорить о следующих месячных."
            LifeStage.TryingToConceive -> "Отметим фертильные дни и будем рядом в подготовке."
            LifeStage.Pregnancy -> "Будем отслеживать изменения каждой недели и обследования."
            LifeStage.Postpartum -> "Особое внимание — восстановлению, кормлению и настроению."
            LifeStage.Perimenopause -> "Вместе будем следить за симптомами, сном и энергией."
            LifeStage.Menopause -> "Ежедневная поддержка, нацеленная на цели здоровья."
        }

        override val doctorTitle = "SADORA вам порекомендовал врач?"
        override val no = "Нет"

        override val cycleLengthTitle = "Сколько дней обычно длится ваш цикл?"
        override fun cycleLengthDerived(days: Int) =
            "По вашим датам получилось $days дн. Если это неверно, поправьте."
        override val cycleLengthHint = "Если точно не знаете, достаточно примерной цифры — дальше уточнится само."
        override val periodLengthTitle = "Сколько дней идут месячные?"

        override fun feelingTitle(name: String) =
            if (name.isBlank()) "Как вы себя чувствуете?" else "$name, как вы себя чувствуете?"
        override val feelingSubtitle = "Ответьте честно — от этого зависит, с чего мы начнём."
        override val feelings = listOf(
            FeelingOption("Хорошо — всё в порядке 🙂", 4, "Отлично. Поможем удержать это состояние."),
            FeelingOption("Устала 😴", 2, "Поставим сон и энергию на первое место."),
            FeelingOption("Тревожно 😟", 2, "Начнём медленно. Записывать будете только то, что захотите."),
            FeelingOption("Хочу лучше понимать своё тело ✨", 3, "Именно для этого SADORA и существует."),
        )

        override val bodyTitle = "Рост и вес"
        override val bodySubtitle = "По желанию. Никому не показывается и удаляется в любой момент."
        override val height = "Рост"
        override val weight = "Вес"

        override val permissionsTitle = "Что вы разрешите?"
        override val permissionsSubtitle = "Каждое можно изменить позже в разделе «Профиль»."
        override val permissionReminders = "Напоминания"
        override val permissionRemindersNote = "Напомним о месячных, приёме препаратов и обследованиях."
        override val permissionHealth = "Данные о здоровье"
        override val permissionHealthNote = "Прочитаем шаги и сон с ваших часов."
        override val permissionCamera = "Камера"
        override val permissionCameraNote = "Чтобы сфотографировать еду и определить её состав."

        override val phoneTitle = "Введите свой номер"
        override val phoneSubtitle = "Отправим одноразовый код, чтобы сохранить ваши ответы."
        override val sending = "Отправляем…"
        override val sendCode = "Отправить код"
        override val haveAccount = "У меня есть аккаунт · Войти"
        override val phoneLabel = "Номер телефона"
        override val phoneNote = "Номер нужен только для входа и не передаётся для рекламы."
        override val codeTitle = "Введите код"
        override fun codeSubtitle(phone: String) = "Отправили 6-значный код на +998 $phone."
        override val checking = "Проверяем…"
        override val confirm = "Подтвердить"
        override fun resendIn(seconds: Int) = "Отправить снова · ${seconds} с"
        override val resend = "Отправить код снова"
        override val codeSecrecy = "Не сообщайте код никому. Сотрудники SADORA его не спрашивают."

        override val periodsTitle = "Когда были ваши последние месячные?"
        override fun periodsSubtitle(periodLength: Int) =
            "Нажмите на день начала — остальные $periodLength дн. отметятся сами. " +
                "Потом дни можно добавлять и убирать по одному."
        override val markMore = "Отметите ещё?"
        override fun markMoreBody(marked: Int) =
            "Пока отмечено циклов: $marked. Если будет три, мы сможем измерить длину вашего " +
                "цикла, и прогноз станет заметно точнее."
        override val iWillMark = "Отмечу"
        override fun markedWithAverage(filled: Int, total: Int, averageCycle: Int) =
            "Отмечено $filled/$total · средний цикл $averageCycle дн."
        override fun markedMoreNeeded(filled: Int, total: Int) = "Отмечено $filled/$total · отметьте ещё"
        override val markAPeriodStart = "Отметьте день начала месячных"

        override val regularityTitle = "Ваш цикл регулярный?"
        override val regularitySubtitle = "Приходит примерно в один и тот же день каждый месяц?"
        override val regularYes = "Да, регулярный"
        override val regularYesNote = "Хорошо — прогнозы будут точнее с самого начала."
        override val regularNo = "Нет, меняется"
        override val regularNoNote = "Учтём это и будем показывать, насколько уверен прогноз."
        override val regularUnknown = "Не знаю"
        override val regularUnknownNote = "Ничего страшного. Через пару месяцев наблюдений станет понятно."

        override val sensitiveTitle = "Следующие вопросы личные"
        override val sensitiveBody = "Спросим о контрацепции и планировании беременности. " +
            "Эти вопросы делают прогнозы точнее, но отвечать необязательно."

        override val birthControlTitle = "Пользовались ли вы контрацепцией последние 6 месяцев?"
        override val birthControlSubtitle = "Некоторые методы влияют на цикл, поэтому и спрашиваем."
        override fun birthControl(option: BirthControl) = when (option) {
            BirthControl.None -> "Нет"
            BirthControl.StillUsing -> "Пользуюсь и сейчас"
            BirthControl.Pill -> "Да, таблетки"
            BirthControl.Iud -> "Да, спираль (ВМС)"
            BirthControl.Barrier -> "Да, презерватив или другой негормональный метод"
            BirthControl.Other -> "Да, другой метод"
            BirthControl.Undisclosed -> "Не хочу говорить"
        }
        override fun birthControlNote(option: BirthControl) = when (option) {
            BirthControl.Pill, BirthControl.Iud ->
                "После гормонального метода цикл восстанавливается несколько месяцев — прогнозы будем давать осторожно."
            BirthControl.StillUsing ->
                "Во время гормонального метода овуляции нет, поэтому фертильные дни мы не показываем."
            else -> null
        }

        override val conceptionTitle = "Как давно вы стараетесь забеременеть?"
        override fun conceptionNote(window: ConceptionWindow) = when (window) {
            ConceptionWindow.JustStarted ->
                "Начало пути — вопросов будет много, и мы будем рядом с каждым из них."
            ConceptionWindow.OverAYear ->
                "Если прошло больше года, рекомендуется обратиться к врачу. Мы будем об этом напоминать."
            else -> null
        }

        override val dueDateTitle = "Когда ожидается рождение?"
        override val dueDateSubtitle = "Отметьте примерную дату, которую назвал врач."
        override val birthDateTitle = "Когда родился ваш ребёнок?"
        override val birthDateSubtitle = "От этой даты будем считать этапы восстановления."

        override fun symptomsTitle(name: String) =
            if (name.isBlank()) "Что вы чувствуете сегодня?" else "$name, что вы чувствуете сегодня?"
        override val symptomsSubtitle = "Можно выбрать несколько. Если ничего нет — пропустите."
        override val saveSymptoms = "Сохранить симптомы"
        override val notAloneTitle = "Вы не одна"
        override val proofs = listOf(
            Proof("Выбор женщин", "Тысячи женщин в Узбекистане следят за циклом вместе с SADORA."),
            Proof("Вместе с врачами", "Вопросы и статьи готовятся вместе с гинекологами."),
            Proof("Данные — ваши", "Экспортируйте их когда угодно или удалите полностью."),
        )
        override val analysingTitle = "Настраиваем под вас"
        override val analysisSteps = listOf(
            "Читаем ваши ответы…",
            "Считаем ваш цикл…",
            "Настраиваем экран «Сегодня»…",
            "Почти готово…",
        )

        override fun readyTitle(name: String) =
            if (name.isBlank()) "Готово! Профиль создан" else "Готово, $name!"
        override val readyBody = "Экран «Сегодня» настроен по вашим ответам. " +
            "Всё это можно изменить позже в разделе «Профиль»."
        override val saving = "Сохраняем…"
        override val startSadora = "Начать с SADORA"
        override fun cycleSummary(cycleLength: Int, periodLength: Int) =
            "Цикл $cycleLength дн. · менструация $periodLength дн."
        override val remindersOn = "Напоминания включены"
        override val healthDataOn = "Данные о здоровье подключаются"
        override fun goalsChosen(count: Int) = "Выбрано целей: $count"

        override val signInTitle = "Добро пожаловать"
        override val signInSubtitle = "Отправим код на ваш номер"
        override val noAccount = "Нет аккаунта? "
        override val signUp = "Зарегистрироваться"

        override val consentTitle = "Ваше тело. Ваши данные."
        override val consentBody = "Данные о вашем здоровье не передаются никому за пределы " +
            "SADORA, и вы можете удалить их в любой момент."
        override val consentHealth = "Согласна на обработку данных о здоровье для работы приложения. "
        override val consentHealthMore = "Подробнее — "
        override val consentTermsPrefix = "Я принимаю "
        override val terms = "Условия использования"
        override val and = " и "
        override val privacyPolicy = "Политику конфиденциальности"
        override val consentAnalytics = "Согласна на анонимный анализ моих действий в " +
            "приложении. Это по желанию и нужно, чтобы улучшать SADORA."
        override val consentAll = "Согласиться со всем"

        override val starterSymptoms = listOf(
            StarterSymptom("cramps", "Боль внизу живота"),
            StarterSymptom("fatigue", "Усталость"),
            StarterSymptom("swelling", "Отёчность"),
            StarterSymptom("breast_tender", "Болит грудь"),
            StarterSymptom("back_pain", "Боль в пояснице"),
            StarterSymptom("headache", "Головная боль"),
        )
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
        override fun premiumUntil(date: String) = "до $date"
        override fun premiumRenewsOn(date: String) = "продлится $date"
        override val premiumNoExpiry = "Бессрочно"
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
        override val languageNote = "Язык приложения меняется сразу. Ответы AI, аналитика " +
            "и сканер еды тоже отвечают на этом языке."
        override val languageSaveFailed = "Язык не сохранён — попробуйте позже."
        override val personalTitle = "Личные данные"
        override val name = "Имя"
        override val birthDate = "Дата рождения"
        override val height = "Рост"
        override val weight = "Вес"
        override val centimetres = "см"
        override val kilograms = "кг"
        override val weightNote = "Вес указывать необязательно, и он никогда никому не показывается."

        override val goalsTitle = "Цели"
        override fun goalsChosen(count: Int) = "Выбрано: $count"

        override val lifeStageTitle = "Этап жизни"
        override val lifeStageNote = "Если сменить этап, раздел «Путь» и связанные экраны " +
            "обновятся полностью. Записанные данные сохранятся."

        override val notificationsTitle = "Уведомления"
        override val medReminder = "Напоминания о приёме"
        override val medReminderNote = "За 10 минут до времени приёма"
        override val cycleReminder = "Напоминание о цикле"
        override val cycleReminderNote = "Когда приближается ожидаемая дата"
        override val waterReminder = "Напоминание о воде"
        override val waterReminderNote = "Три раза в день"
        override val aiSummary = "Ежедневная сводка ИИ"
        override val aiSummaryNote = "Утром в 08:00"

        override val privacyTitle = "Приватность и безопасность"
        override val consentHealth = "Хранение данных о здоровье"
        override val consentHealthNote = "Нужно для работы приложения. Данные хранятся в зашифрованном виде."
        override val consentAi = "Использовать для выводов ИИ"
        override val consentAiNote = "Чтобы готовить персональные выводы и советы."
        override val consentAnalytics = "Анонимная аналитика"
        override val consentAnalyticsNote = "По желанию. Помогает улучшать приложение."
        override val saveConsents = "Сохранить согласия"
        override val legalDocuments = "Правовые документы"
        override val legalEffectiveDate = "Дата вступления в силу"
        override val legal = LegalTextsRu
        override val terms = "Условия использования"
        override val privacyPolicy = "Политика конфиденциальности"
        override val yourData = "Ваши данные"
        override val exportData = "Экспортировать данные"
        override val deleteAccount = "Удалить аккаунт"
        override val deleteAccountConfirm = "Удалить аккаунт?"
        override val deleteAccountBody = "Ваши данные будут удалены безвозвратно. " +
            "Советуем сначала их экспортировать."

        override val medicalDisclaimer = "SADORA не ставит диагнозов. При сомнениях " +
            "обратитесь к врачу."
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

        override fun goal(goal: Goal) = when (goal) {
            Goal.UnderstandCycle -> "Понять свой цикл"
            Goal.SleepBetter -> "Лучше спать"
            Goal.MoreEnergy -> "Больше энергии"
            Goal.LessStress -> "Меньше стресса"
            Goal.EatBalanced -> "Сбалансированное питание"
            Goal.DrinkWater -> "Пить больше воды"
            Goal.BeActive -> "Быть активнее"
            Goal.RememberMeds -> "Не забывать о препаратах"
        }

        override fun conceptionWindow(window: ConceptionWindow) = when (window) {
            ConceptionWindow.JustStarted -> "Только начала"
            ConceptionWindow.UnderThreeMonths -> "До 3 месяцев"
            ConceptionWindow.ThreeToSix -> "3–6 месяцев"
            ConceptionWindow.SixToTwelve -> "6–12 месяцев"
            ConceptionWindow.OverAYear -> "Больше года"
        }

        override val saving = "Сохранение…"
        override val yes = "Да"
        override val no = "Нет"
        override val back = "Назад"
        override val loading = "Загрузка…"
        override val retry = "Повторить"
        override val optional = "Необязательно"
        override val save = "Сохранить"
        override val cancel = "Отмена"
        override val delete = "Удалить"
        override val close = "Закрыть"
        override val add = "Добавить"
        override val edit = "Изменить"
        override val done = "Готово"

        override fun hoursMinutes(hours: Int, minutes: Int) = "${hours}ч ${minutes}м"
        override val litres = "л"
        override val millilitres = "мл"
        override val kcal = "ккал"
        override val steps = "шагов"
        override val minutesShort = "мин"
        override fun days(count: Int) = "$count дн."
    }

    override val dates = object : DateStrings {
        override val months = listOf(
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
        )

        /** The genitive: a day is "4 сентября", never "4 Сентябрь". */
        private val monthsOfDay = listOf(
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря",
        )

        override val weekdays = listOf(
            "понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье",
        )

        override val weekdaysShort = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

        override fun dayMonth(date: LocalDate) = "${date.day} ${monthsOfDay[date.month.ordinal]}"

        override val today = "Сегодня"
        override val yesterday = "Вчера"
        override val tomorrow = "Завтра"

        override val justNow = "только что"
        override fun minutesAgo(minutes: Int) = "$minutes мин. назад"
        override fun hoursAgo(hours: Int) = "$hours ч. назад"
        override fun daysAgo(days: Int) = "$days дн. назад"
    }

    override val ai = object : AiStrings {
        override val title = "SADORA AI"
        override val subtitle = "Ваш личный помощник"
        override val menu = "Ещё"
        override val back = "Назад"
        override val send = "Отправить"
        override val inputHint = "Задайте любой вопрос…"
        override val emptyPrompt = "Спросите о цикле, питании, настроении или препаратах — " +
            "ответ будет по вашим данным."
        override fun basis(cycleDay: Int, sleep: String, water: String) =
            "По данным: $cycleDay-й день цикла · сон $sleep · вода $water л"
        override fun questionsLeft(left: Int, limit: Int) = " · осталось вопросов: $left/$limit"
        override val answerFailed = "Не удалось ответить. Попробуйте ещё раз."
        override val sessionOnly = "Переписка хранится только в этом сеансе и на сервер не " +
            "записывается. При выходе из приложения она исчезнет."
        override val clearChat = "Очистить переписку"
        override val medicalDisclaimer =
            "SADORA — помощник по здоровью. Не ставит диагнозов и не назначает лечение."

        override val topics = listOf(
            "Энергия" to "Как удержать энергию на ровном уровне?",
            "Питание" to "Что мне лучше съесть сегодня?",
            "Цикл" to "Почему перед месячными я чувствую усталость?",
            "Кожа" to "Почему кожа меняется в течение цикла?",
        )

        override val freeBadge = "БЕСПЛАТНЫЙ ПЛАН"
        override val howCanIHelp = "Чем я могу помочь?"
        override val readsYourData = "Читает ваши данные и отвечает лично вам"
        override val sampleAnswer = "Пример ответа"
        override val sampleAnswerBody = "Последние три дня сон был короче обычного, а воды " +
            "стало меньше."
        override val sampleAnswerAdvice = "В эти же дни отмечалась низкая энергия. Два шага " +
            "на сегодня: 700 мл воды до обеда и лечь до 23:00."
        override val freeFeatures = listOf(
            "20 вопросов в день, с учётом ваших данных",
            "Ежедневная персональная сводка ИИ",
            "Сканер еды",
            "Анализ за 30 и 90 дней",
        )
        override val freeKeeps = "Всё из бесплатного плана остаётся: цикл, настроение, вода, " +
            "дневник питания, препараты, анализ за 7 дней."
        override val seePremium = "Посмотреть Premium"
        override val notNow = "Не сейчас"
    }

    override val community = object : CommunityStrings {
        override val title = "Секретный чат"
        override val compose = "Написать"
        override val more = "Ещё"
        override val saved = "Сохранённые"
        override fun topic(topic: CommunityTopic) = when (topic) {
            CommunityTopic.All -> "Все"
            CommunityTopic.Cycle -> "Цикл"
            CommunityTopic.Pregnancy -> "Беременность"
            CommunityTopic.Wellbeing -> "Настроение"
            CommunityTopic.Body -> "Тело"
        }
        override fun filter(filter: CommunityFilter) = when (filter) {
            CommunityFilter.Feed -> "Лента"
            CommunityFilter.Saved -> "Сохранённые"
        }
        override fun reportReason(reason: ReportReason) = when (reason) {
            ReportReason.Spam -> "Спам или реклама"
            ReportReason.Abuse -> "Оскорбление или угроза"
            ReportReason.Misinformation -> "Опасный медицинский совет"
            ReportReason.PersonalData -> "Раскрыты личные данные"
            ReportReason.Other -> "Другая причина"
        }

        override val nothingSaved = "Сохранённых постов нет"
        override val nothingHere = "Здесь пока нет постов"
        override val nothingSavedBody = "Отметьте пост, который вам близок — он останется здесь."
        override val nothingHereBody = "Напишите первой — ваш вопрос выйдет под псевдонимом."
        override val write = "Написать"
        override val you = "вы"
        override fun youParenthesised(alias: String) = "$alias (вы)"

        override val noComments = "Комментариев пока нет. Ответьте первой."
        override val commentHint = "Напишите комментарий"
        override val send = "Отправить"
        override val whatIsOnYourMind = "О чём вы хотите спросить?"
        override fun postsAs(alias: String) = "Пост выйдет от имени «$alias» — вашего имени не будет видно."
        override val postsAnonymously = "Пост выйдет под псевдонимом — вашего имени не будет видно."
        override val yourOwnPost = "Это ваш пост."
        override val deletePost = "Удалить пост"
        override val newPost = "Новый пост"
        override val postSent = "Пост отправлен"
        override val comments = "Комментарии"
        override val reportPost = "Пожаловаться"
        override val postDeleted = "Пост удалён"
        override val reportReasonTitle = "Причина жалобы"
        override val reportNote = "Жалоба уйдёт модератору. Кто её отправил, не видно."
        override val sendReport = "Отправить жалобу"
        override val reportSent = "Жалоба отправлена"
        override val shareSuffix = "SADORA — Секретный чат"
    }

    override val errors = object : ErrorStrings {
        override val phoneInvalid = "Номер неполный или такого кода оператора нет"
        override val nameRequired = "Имя не может быть пустым"
        override fun tooLong(max: Int) = "Не больше $max символов"
        override fun outOfRange(min: Int, max: Int) = "Должно быть от $min до $max"
        override val dateFormat = "Дата в виде день.месяц.год"
        override val dateInFuture = "Дата не может быть в будущем"
        override val timeFormat = "Время в виде 20:00"
        override val wholeNumber = "Только цифры"

        override val network = "Не удалось подключиться к интернету. Попробуйте ещё раз."
        override val validation = "Введённые данные неверны."
        override val sessionExpired = "Сеанс закончился. Войдите снова."
        override val blocked = "Аккаунт заблокирован. Свяжитесь с поддержкой."
        override val premiumRequired = "Эта возможность открывается в Premium."
        override val monthlyLimit = "Месячный лимит исчерпан."
        override val dailyLimit = "Дневной лимит исчерпан."
        override fun retryAfter(seconds: Int) =
            "Слишком много попыток. Повторите через $seconds сек."
        override val retrySoon = "Слишком много попыток. Повторите чуть позже."
        override val otpInvalid = "Код неверный или устарел."
        override val featureClosed = "Этот раздел пока закрыт."
        override val consentRequired = "Для этого дайте согласие в разделе «Приватность»."
        override val paymentFailed = "Платёж не прошёл. Попробуйте ещё раз."
        override val unexpected = "Что-то пошло не так. Попробуйте ещё раз."
    }

    override val today = object : TodayStrings {
        override fun greetingLine(greeting: String) =
            "$greeting — отличный день, чтобы позаботиться о себе 🌸"

        override fun hello(name: String) = if (name.isBlank()) "Здравствуйте!" else "Здравствуйте, $name!"
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
        override val addWaterTitle = "Добавить воду"
        override val undo = "Отменить"
        override fun waterAdded(ml: Int) = "Добавлено $ml мл"
        override fun addWater(ml: Int) = "+$ml мл"

        override val aiAnalysis = "Анализ ИИ"
        override val aiBasis = "Рассчитано по вашим сегодняшним данным"
        override val scanner = "Сканер еды"
        override val scannerHint = "Наведите камеру — блюдо, порция и макросы определятся примерно"
        override val balance = "Баланс"
        override val balanceHint = "Еда, вода, активность и сон — четыре направления"

        override fun mealSlot(slot: MealSlot) = when (slot) {
            MealSlot.BREAKFAST -> "Завтрак"
            MealSlot.LUNCH -> "Обед"
            MealSlot.DINNER -> "Ужин"
            MealSlot.SNACK -> "Перекус"
        }

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

    override val journey = object : JourneyStrings {
        override val cycleTitle = "Мой цикл"
        override val info = "Информация"
        override val calendar = "Календарь"
        override val noPredictionTitle = "Данных для прогноза недостаточно"
        override val noPredictionBody =
            "После двух отмеченных менструаций здесь появятся фазы цикла и примерная " +
                "дата следующей."
        override val markPeriod = "Отметить менструацию"
        override val today = "Сегодня"
        override fun daysToNextPeriod(days: Int) = "Следующая менструация — через $days дн."
        override val symptoms = "Симптомы"
        override val change = "Изменить"
        override val averageCycle = "Средний цикл"
        override val averagePeriod = "Средняя менструация"
        override val day = "День"
        override fun daysValue(days: Int) = "$days дн."

        override val calendarTitle = "Календарь"
        override val history = "История"
        override val predictedNote = "Дни в контуре — это расчёт, а не медицинская гарантия."
        override val markPeriodDay = "Отметить менструацию"
        override val phaseNotColouredYet = "Как только появятся даты менструации, фазы здесь окрасятся."
        override val previousMonth = "Предыдущий месяц"
        override val nextMonth = "Следующий месяц"
        override val keyPeriod = "Менструация"
        override val keyFertile = "Фертильные дни"
        override val keyPredicted = "Прогноз"
        override val dayCaps = "ДЕНЬ"
        override fun symptomsAndMood(symptoms: String, mood: String) = "$symptoms · настроение $mood"
        override fun noSymptomsAndMood(mood: String) = "Симптомы не отмечены · настроение $mood"
        override val statsNote = "Статистика построена на введённых циклах. Чем больше " +
            "данных, тем точнее расчёт."
        override val regularity = "Регулярность"
        override val regularSteady = "Стабильно"
        override val regularVaries = "Меняется"
        override val cycleLength = "Длина цикла"
        override fun lastNCycles(count: Int) = "последние $count цикла"
        override val previousCycles = "Прошлые циклы"
        override val noHistoryYet = "Истории циклов пока нет"
        override val noHistoryYetBody = "Как только появится вторая дата менструации, здесь " +
            "будут длина и регулярность."
        override fun periodOfDays(days: Int) = "менструация $days дн."
        override val currentCycle = "Текущий"

        override fun cycleDayOrdinal(day: Int) = "$day-й день цикла"
        override val cycleDayCaps = "ДЕНЬ ЦИКЛА"
        override val loggedToday = "Отмечено сегодня"
        override val logged = "Отмечено"
        override val noSymptomsLogged = "Симптомы не отмечены"
        override val nothingLoggedForDay = "За этот день записей нет."
        override fun moodLine(mood: String) = "Настроение — $mood"
        override fun energyLine(level: Int) = "Энергия — $level / 5"
        override fun sleepAndSteps(sleep: String, steps: String) = "Сон $sleep · $steps шагов"
        override val fromDevice = "С устройства"
        override val editEntry = "Изменить"

        override val symptomSheetTitle = "Добавить симптом"
        override val catalogueLoading = "Список симптомов загружается…"
        override val severity = "Насколько сильно"
        override val severityWords = listOf(
            "Не чувствуется",
            "Слабо — делам не мешает",
            "Умеренно — иногда отвлекает",
            "Сильно — работать тяжело",
            "Очень сильно — обычные дела не даются",
        )
        override val notePlaceholder = "Добавить заметку…"
        override fun categoryName(category: SymptomCategory) = when (category) {
            SymptomCategory.PAIN -> "Боль"
            SymptomCategory.BLEEDING -> "Выделения"
            SymptomCategory.MOOD -> "Настроение"
            SymptomCategory.SLEEP -> "Сон"
            SymptomCategory.ENERGY -> "Энергия"
            SymptomCategory.DIGESTION -> "Пищеварение"
            SymptomCategory.SKIN -> "Кожа"
            SymptomCategory.OTHER -> "Другое"
        }

        override val pregnancyTitle = "Беременность"
        override fun trimester(week: Int) = when {
            week <= 13 -> "1-й триместр"
            week <= 27 -> "2-й триместр"
            else -> "3-й триместр"
        }
        override val weekCaps = "  НЕДЕЛЯ"
        override fun weekAndDay(week: Int, day: Int) = "$week-я неделя, $day-й день"
        override fun weekOnly(week: Int) = "$week-я неделя"
        override fun dueOn(date: String, daysLeft: Int) = "Дата родов — $date · осталось $daysLeft дн."
        override fun dueOnPast(date: String) = "Дата родов — $date"
        override val babyDevelopment = "Развитие ребёнка"
        override val babyDevelopmentBody =
            "О том, что меняется на этой неделе, читайте в библиотеке «Знания»."
        override val todaysSymptoms = "Симптомы сегодня"
        override val addSymptom = "+ Добавить"
        override val upcomingAppointments = "Ближайшие визиты"
        override val all = "Все"
        override val noAppointments = "Визитов пока нет"
        override val noAppointmentsBody =
            "Запишите дату осмотра или анализа — придёт напоминание."
        override val logToday = "Отметить самочувствие"
        override val aiAdvice =
            "На этой неделе полезны продукты, богатые железом, и лёгкая растяжка. " +
                "Это общая информация о здоровье."
        override val aiBadge = "SADORA AI · СОВЕТ"

        override val appointmentsTitle = "События"
        override val filterUpcoming = "Ближайшие"
        override val filterPast = "Прошедшие"
        override val filterAll = "Все"
        override val listEmpty = "Список пуст"
        override val nothingInThisFilter = "В этом разделе ничего нет"
        override val appointmentsEmptyBody = "Запишите дату приёма, УЗИ или анализа — " +
            "напоминание настраивается здесь же."
        override val addAppointment = "Добавить событие"
        override val nextCaps = "БЛИЖАЙШЕЕ"
        override val todayCaps = "СЕГОДНЯ"
        override val tomorrowCaps = "ЗАВТРА"
        override fun inDaysCaps(days: Int) = "ЧЕРЕЗ $days ДН."
        override val appointmentsNote = "Список событий вы заполняете сами. " +
            "SADORA не назначает график обследований."
        override val appointmentDone = "Состоялось"
        override fun reminderSet(offset: String) = "Напоминание $offset"
        override fun reminderOffset(hours: Int) = when (hours) {
            in 0..2 -> "за 2 часа"
            in 3..24 -> "за день"
            else -> "за 2 дня"
        }
        override val noReminder = "Не нужно"
        override val editAppointment = "Изменить событие"
        override val appointmentName = "Название"
        override val appointmentNameHint = "Скрининговое УЗИ"
        override val appointmentDate = "Дата"
        override val appointmentDateHint = "27.8.2026"
        override val appointmentDateInvalid = "Дата в виде день.месяц.год"
        override val appointmentTime = "Время (необязательно)"
        override val appointmentPlace = "Место (необязательно)"
        override val appointmentPlaceHint = "Республиканский центр"
        override val reminder = "Напоминание"
        override val appointmentDateNote = "Дата пишется как день.месяц.год, например 27.8.2026."

        override val checkInTitle = "Как вы себя чувствуете?"
        override val todaysSymptomsLabel = "Симптомы сегодня"
        override val babyMovement = "Шевеления малыша"
        override fun movement(movement: FetalMovement) = when (movement) {
            FetalMovement.USUAL -> "Как обычно"
            FetalMovement.LESS -> "Меньше"
            FetalMovement.MORE -> "Больше"
        }
        override val movementWarning = "Если шевелений стало заметно меньше или их совсем " +
            "нет, обратитесь к врачу без промедления."
        override val privateNote = "Заметка — видите только вы"
        override val privateNoteHint = "Запишите…"
        override val checkInSaved = "Сегодняшнее состояние сохранено"

        override val postpartumTitle = "После родов"
        override val recoveryWeeks = "  нед. · период восстановления"
        override val recoveryNote =
            "Восстановление у каждой женщины идёт по-своему. Эта шкала только ориентир."
        override val mood = "Настроение"
        override val sleep = "Сон"
        override val brokenSleep = "Прерывистый сон"
        override val feedingAndWater = "Кормление и вода"
        override val water = "Вода"
        override val calories = "Калории"
        override val moodWatch = "Наблюдение за настроением"
        override val moodWatchBody =
            "Если подавленность или тревога держатся долго, стоит обратиться к специалисту. " +
                "SADORA не ставит диагноз."
        override val postpartumLibrary = "Знания — после родов"
        override val postpartumLibraryBody = "Материалы о послеродовом периоде"

        override val perimenopauseTitle = "Перименопауза"
        override val cycleRegularity = "Регулярность цикла"
        override val noData = "нет данных"
        override fun lastCycles(count: Int) = "последние $count цикл."
        override val regularityEmpty =
            "Когда вы начнёте отмечать менструации, длина цикла появится здесь. " +
                "На этом этапе прогноз не показывается."
        override fun regularitySpread(shortest: Int, longest: Int) =
            "Длина цикла менялась от $shortest до $longest дней — для этого этапа это " +
                "ожидаемо. Прогноз не показывается."
        override fun regularitySteady(shortest: Int, longest: Int) =
            "Длина цикла от $shortest до $longest дней. На этом этапе прогноз не " +
                "показывается."
        override val energy = "Энергия"
        override val observation = "Наблюдение"
        override val observationBody =
            "Посмотреть связи между сном, настроением и симптомами."
        override val seeSymptoms = "Посмотреть симптомы"

        override val menopauseTitle = "Здоровье"
        override val scoreNote =
            "По сну, активности, питанию и настроению. Этот балл не является " +
                "медицинским показателем."
        override val activity = "Активность"

        override val stageSymptomsTitle = "Симптомы"
        override val noRecordsYet = "Записей пока нет"
        override val noRecordsYetBody = "Отметьте сегодняшние признаки ниже. Через " +
            "несколько дней здесь будет видно, что встречается чаще всего."
        override fun windowDays(days: Int) = "$days дн."
        override fun weekNumber(week: Int) = "$week-я нед."
        override fun recordedOnDays(window: Int, days: Int) =
            "За $window дн. отмечено в $days дн."
        override val logToday2 = "Отметить сегодня"
        override val mostFrequent = "Чаще всего"
        override val symptomsDisclaimer = "Список симптомов нужен для наблюдения. " +
            "Если появляются новые или усиливающиеся признаки, обсудите их с врачом."

        override val sleepMoodTitle = "Сон и настроение"
        override val notEnoughData = "Данных пока мало"
        override val notEnoughDataBody = "Сон приходит с часов или телефона, а настроение — " +
            "из ежедневного чек-ина. Через несколько дней они появятся здесь рядом."
        override val scoreCaps = "БАЛЛ"
        override fun sleepGoal(hours: String) = "Цель · $hours"
        override val moodWeek7 = "Настроение за 7 дней"
        override val noticed = "Наблюдение"
        override val breathingCard = "Дыхательная практика"
        override val breathingCardNote = "Перед сном · 4 мин."
        override val journalCard = "Дневник"
        override val journalCardNote = "Видите только вы"

        override val estimatedCaps = "ПРОГНОЗ"
        override val balanceCaps = "БАЛАНС"
        override val premiumCaps = "PREMIUM"
        override val libraryCaps = "БИБЛИОТЕКА"
        override val predictionDisclaimer = "Прогноз строится на введённых " +
            "данных и не является медицинским заключением."
    }

    override val modules = object : ModuleStrings {
        override val sleepTitle = "Сон"
        override val sleepEmptyTitle = "Нет данных о сне"
        override val sleepEmptyBody =
            "Когда часы или телефон синхронизируются, длительность и фазы сна появятся здесь."
        override val sleepWeek = "Длительность за 7 дней"
        override fun average(value: String) = "В среднем $value"
        override fun daysRecorded(withData: Int, total: Int) = "$withData / $total дн. записано"
        override val sleepManual = "Ввести сон вручную"
        override fun goalFrom(hours: Int) = "от $hours часов"
        override val lastNight = "Прошлая ночь"
        override fun restingPulse(bpm: Int) = "Пульс покоя $bpm уд/мин"
        override val deep = "Глубокий"
        override val light = "Лёгкий"
        override val stages = "Фазы"

        override val insightsTitle = "Аналитика"
        override fun windowDays(days: Int) = "$days дн."
        override val windowPremium = "Этот период открывается с Premium"
        override val insightsEmptyTitle = "Аналитики пока нет"
        override val loadFailed = "Данные не загрузились. Проверьте интернет и попробуйте снова."
        override val noRecordsInWindow = "За этот период записей нет"
        override val noRecordsBody =
            "Отмечайте сон, настроение, воду или еду — и здесь появятся тренды. " +
                "Неизмеренные числа мы не показываем."
        override val sleepTrend = "Тренд сна"
        override val activityTrend = "Активность"
        override val moodTrend = "Настроение"
        override val notEnoughForChart = "Данных для графика недостаточно"
        override val notEnoughForChartBody =
            "За этот период нет записей о сне, шагах и настроении."
        override val correlations = "Замеченные связи"
        override val correlationsPremium = "Связи открываются с Premium"
        override val noCorrelation = "За этот период надёжных связей не нашлось."
        override val noCorrelationBody =
            "Нужно минимум восемь дней записей, и разница должна быть заметной — иначе " +
                "мы ничего не пишем."
        override val averagePrefix = "В среднем — "
        override val correlationDisclaimer =
            "Связь — это не причина. Она означает «часто встречалось вместе»."

        override val all = "Все"
        override val knowledgeTitle = "Знания"
        override val search = "Поиск"
        override val libraryFailed = "Библиотека не открылась"
        override val libraryEmpty = "Библиотека пока пуста"
        override val libraryEmptyBody = "Новые статьи появятся здесь."
        override val nothingFound = "Ничего не найдено"
        override val nothingFoundBody = "Попробуйте другое слово или категорию."
        override val clearFilters = "Сбросить фильтры"
        override fun readMinutes(minutes: Int) = "$minutes МИН"

        override val medsTitle = "Лекарства"
        override val today = "Сегодня"
        override val history = "История"
        override val nextDose = "Следующий приём"
        override fun oneTabletWith(note: String) = "1 таблетка · $note"
        override val take = "Приняла"
        override val later = "Позже"
        override val skip = "Пропустить"
        override val medsEmpty = "Лекарства ещё не добавлены"
        override val medsEmptyBody = "Добавьте лекарство — время приёма и запас появятся здесь."
        override val addMedication = "Добавить лекарство"
        override val medsDisclaimer =
            "SADORA не даёт указаний по пропущенному приёму. Следуйте инструкции к " +
                "препарату или рекомендации врача либо фармацевта."
        override fun stockLeft(name: String, days: Int) = "Запаса $name осталось на $days дн."
        override fun stockDays(days: Int) = "Запас $days дн."
        override val pending = "Ожидается"
        override val skipped = "Пропущено"

        override val featureCycleMood = "Цикл и настроение"
        override val featureFoodDiary = "Дневник питания"
        override val featureAiChat = "Разговор с ИИ"
        override val featureScanner = "Сканер еды"
        override val featureLongInsights = "Аналитика за 30/90 дней"
        override val premiumTitle = "SADORA Premium"
        override val premiumBody =
            "Разговор с ИИ, сканер еды и расширенная аналитика. Всё из бесплатного " +
                "плана остаётся."
        override val plansFailed = "Тарифы не загрузились"
        override val plansFailedBody = "Проверьте интернет и попробуйте снова."
        override val paymentAccepted = "Оплата принята. Premium открыт."
        override val paymentPending = "Ожидаем оплату…"
        override val noPaymentMethod = "Способов оплаты пока нет."
        override val cancelAnytime = "Отменить можно в любой момент"
        override val restorePurchase = "Восстановить покупку"
        override fun priceFor(sum: String, monthly: Boolean) =
            "$sum сум / " + (if (monthly) "мес." else "год")
        override fun perMonth(sum: String) = "$sum сум/мес."
        override fun saving(percent: Int) = "−$percent%"
        override val payWithPayme = "Оплатить через Payme"
        override val payWithClick = "Оплатить через Click"
        override val payWithAppStore = "Через App Store"
        override val payWithGooglePlay = "Через Google Play"

        override val searchFood = "Поиск блюда"
        override val searchTabAll = "Все"
        override val searchTabFrequent = "Частые"
        override val searchTabRecipes = "Рецепты"
        override val typeADishName = "Введите название блюда"
        override fun nothingFoundFor(query: String) = "По запросу «$query» ничего не найдено"
        override val catalogueNote = "Каталог приходит с сервера — узбекские блюда идут первыми."
        override val portionLabel = "Порция"
        override val pieces = "шт. × 100"
        override val grams = "граммов"
        override fun bowls(count: Int) = "$count миски"
        override val total = "Итого"
        override val addToDiary = "Добавить в дневник"
        override val perPiece = "шт."
        override val perHundredGrams = "100 г"
        override val proteinInitial = "Б"
        override val fatInitial = "Ж"
        override val carbsInitial = "У"

        override val articleFailed = "Статья не открылась"
        override val articleFailedBody = "Данные не загрузились. Проверьте интернет и попробуйте снова."
        override fun readMinutesCaps(minutes: Int) = "$minutes МИН."
        override val premiumCaps = "PREMIUM"
        override val author = "Автор"
        override val reviewed = "✓ Проверено"
        override val restIsPremium = "Продолжение статьи открывается с Premium"

        override fun stepsValue(steps: String) = "$steps шагов"
        override fun litresValue(litres: String) = "$litres л"
        override fun kcalValue(kcal: String) = "$kcal ккал"
        override fun outOfFive(value: String) = "$value / 5"
        override fun sleepEnergyFinding(high: String, low: String) =
            "В дни, когда сна было больше, энергия в среднем $high, а когда меньше — $low."
        override fun activityMoodFinding(high: String, low: String) =
            "В дни с большей активностью настроение в среднем $high, с меньшей — $low."
        override fun waterHeadacheFinding(high: String, low: String) =
            "В дни с большим количеством воды головная боль отмечена в $high случаев, с меньшим — в $low."
        override fun basedOnDays(days: Int) = "По $days дн. · встречалось вместе"
        override fun minutesOnly(minutes: Int) = "$minutes мин."

        override val addMedTitle = "Добавить препарат"
        override val medName = "Название"
        override val medNameHint = "Железо"
        override val medDose = "Доза"
        override val medUnit = "Единица"
        override val medTime = "Время приёма"
        override val medTimeInvalid = "Время в виде 20:00"
        override val addTime = "+ Время"
        override val medDays = "Дни"
        override val medFoodRelation = "Относительно еды"
        override fun foodRelation(relation: FoodRelation) = when (relation) {
            FoodRelation.ANY -> "Неважно"
            FoodRelation.BEFORE -> "До"
            FoodRelation.WITH -> "Во время"
            FoodRelation.AFTER -> "После"
        }
        override fun scheduleKind(kind: ScheduleKind) = when (kind) {
            ScheduleKind.DAILY -> "Каждый день"
            ScheduleKind.WEEKDAYS -> "По выбранным дням"
            ScheduleKind.INTERVAL -> "Через несколько дней"
        }

        override fun doseCaption(note: String?, relation: FoodRelation) =
            note?.takeIf { it.isNotBlank() } ?: when (relation) {
                FoodRelation.ANY -> "В любое время"
                FoodRelation.BEFORE -> "До еды"
                FoodRelation.WITH -> "Во время еды"
                FoodRelation.AFTER -> "После еды"
            }

        override val medStock = "Запас"
        override val medStockUnit = "шт."
        override val medEndDate = "Дата окончания"
        override val medNone = "Нет"

        override val doseHistoryTitle = "История приёма"
        override val takenCount = "Принято"
        override val skippedCount = "Пропущено"
        override fun adherenceOver(days: Int) = "$days дн."
        override fun lastDays(days: Int) = "Последние $days дн."
        override val noDoseHistory = "История пока пуста"
        override val noDoseHistoryBody = "Добавьте препарат и начните отмечать приём — " +
            "здесь будет видно, сколько раз всё было вовремя."
        override fun doseStatus(status: DoseStatus) = when (status) {
            DoseStatus.TAKEN -> "Принято"
            DoseStatus.PENDING -> "Отложено"
            DoseStatus.SKIPPED -> "Пропущено"
        }

        override val scannerTitle = "Сканер еды"
        override val scannerFrameHint = "Поместите блюдо в рамку"
        override val scannerLightHint = "При хорошем свете результат точнее"
        override val scannerGallery = "Галерея"
        override val scannerShutter = "Снять"
        override val scannerManual = "Вручную"
        override val scannerPremium = "Сканер работает в подписке Premium."
        override val scannerUnavailable = "Сканер сейчас недоступен"
        override val scannerUnavailableBody = "Блюдо можно найти и добавить вручную."
        override val analysing = "Анализируем…"
        override val analysingWait = "Обычно это занимает несколько секунд"
        override val scanFailed = "Не удалось распознать фото"
        override val scanFailedBody = "Попробуйте ещё раз или добавьте блюдо вручную."
        override val notFood = "На фото не видно еды"
        override val scanResult = "Результат сканирования"
        override fun scanConfidence(percent: Int) = "Уверенность $percent%"
        override fun portionAndKcal(portion: String, kcal: String) =
            "$portion порции • $kcal ккал • приблизительно"
        override val nutrients = "Пищевая ценность"
        override val fibre = "Клетчатка"
        override val sugar = "Сахар"
        override val sodium = "Натрий"
        override val portion = "Порция"
        override val portionHint = "Оценка ИИ приблизительна — поправьте сами"
        override val didYouEatIt = "Вы это съели?"
        override val yesIAte = "Да, съела"
        override val planningToEat = "Собираюсь съесть"

        override val journalTitle = "Дневник и практика"
        override val journalPrivate = "ВИДИТЕ ТОЛЬКО ВЫ"
        override val journalLabel = "Дневник"
        override val journalPrompt = "Как вы себя чувствуете сегодня?"
        override val journalEmpty = "В дневнике пока пусто"
        override val journalEmptyBody = "Напишите первую запись. Кроме вас её никто не увидит."
        override val journalDeleteTitle = "Удалить запись"
        override val journalDeleteBody = "Запись будет удалена безвозвратно."
        override val journalDeleteAction = "Удалить запись"

        override val sourcesTitle = "Источники данных"
        override fun sourcesConnected(count: Int) = "Подключено источников: $count"
        override fun lastSample(ago: String) = "Последние данные $ago"
        override val noSampleYet = "Данных пока не было"
        override val sourcesEmpty = "Нет подключённых источников"
        override val sourcesEmptyBody = "Как только вы дадите доступ к HealthKit или Health " +
            "Connect, здесь появятся полученные данные и их время."
        override val sourcesNote = "У каждого показателя видно, откуда он и когда получен. " +
            "Если один показатель приходит из нескольких источников, применяется приоритет."
        override val connected = "Подключено"
        override val notConnected = "Не подключено"
        override fun samples(count: String) = "$count записей"
        override fun metric(metric: HealthMetric) = when (metric) {
            HealthMetric.STEPS -> "Шаги"
            HealthMetric.ACTIVE_ENERGY -> "Активные калории"
            HealthMetric.DISTANCE -> "Расстояние"
            HealthMetric.HEART_RATE -> "Пульс"
            HealthMetric.RESTING_HEART_RATE -> "Пульс покоя"
            HealthMetric.HRV -> "ВСР"
            HealthMetric.RESPIRATORY_RATE -> "Дыхание"
            HealthMetric.BODY_TEMPERATURE -> "Температура"
            HealthMetric.SLEEP_DURATION -> "Сон"
            HealthMetric.SLEEP_DEEP -> "Глубокий сон"
            HealthMetric.SLEEP_REM -> "Фаза REM"
            HealthMetric.WEIGHT -> "Вес"
        }

        override val balanceTitle = "Баланс"
        override val fourDirections = "Четыре направления"
        override val balanceDisclaimer =
            "Балл баланса считается относительно ваших собственных целей. Это не " +
                "медицинский показатель."
        override val balanced =
            "Сегодня все четыре направления в балансе. Еда — не долг, который нужно " +
                "«отработать»."
        override fun someRoomIn(direction: String) =
            "День идёт хорошо. По направлению «$direction» есть небольшой запас — если " +
                "захотите, обратите на него внимание."
        override fun fallingBehind(direction: String) =
            "Сегодня «$direction» отстаёт. День ещё не кончился, не торопитесь."
        override val food = "Питание"
        override val water = "Вода"
        override val activity = "Активность"
        override val sleep = "Сон"
        override fun ofKcal(eaten: String, goal: String) = "$eaten / $goal ккал"
        override fun ofLitres(drunk: String, goal: String) = "$drunk / $goal л"
        override fun ofSteps(walked: String, goal: String) = "$walked / $goal шагов"
        override fun ofSleep(slept: String) = "$slept / 8ч"
        override val balanceCapsWord = "БАЛАНС"
        override fun articleKind(kind: ArticleKind) = when (kind) {
            ArticleKind.ARTICLE -> "СТАТЬЯ"
            ArticleKind.COURSE -> "КУРС"
            ArticleKind.VIDEO -> "ВИДЕО"
        }
        override val featureCaps = "ВОЗМОЖНОСТЬ"
        override val freeCaps = "БЕСПЛАТНО"
        override val premiumCapsBadge = "PREMIUM"
        override val journalCardTitle = "Дневник"
        override val moodLabel = "Настроение"
        override val allDoneToday = "На сегодня всё выполнено 🌸"
    }
}
