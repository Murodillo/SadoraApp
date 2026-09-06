package org.example.project.i18n

import org.example.project.model.LifeStage

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
}
