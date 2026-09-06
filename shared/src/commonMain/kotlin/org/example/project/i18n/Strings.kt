package org.example.project.i18n

import org.example.project.model.LifeStage

/**
 * Every string the app shows, grouped by the screen that shows it.
 *
 * It is an interface rather than a map of keys because a missing translation should be
 * a build error, not a blank line on a screen: adding a string here breaks the two
 * languages that have not answered it yet, which is exactly when it is cheapest to fix.
 *
 * Strings that take a number or a name are functions, so each language decides where
 * the value goes — Uzbek and Russian do not agree on word order, and neither agrees
 * with English.
 *
 * The app is being moved onto this layer screen by screen; what is not here yet is
 * still Uzbek in the composable that draws it.
 */
interface Strings {
    val languageName: LanguageNames
    val tabs: TabStrings
    val stages: LifeStageStrings
    val welcome: WelcomeStrings
    val onboarding: OnboardingStrings
    val profile: ProfileStrings
    val settings: SettingsStrings
}

/**
 * A language is always named in itself — "Русский", never "Rus tili" — because the
 * person reading the list is looking for the one she can read.
 */
interface LanguageNames {
    val uz: String get() = "O'zbekcha"
    val ru: String get() = "Русский"
    val en: String get() = "English"
}

interface TabStrings {
    val today: String
    val mind: String
    val nutrition: String
    val profile: String

    /**
     * The second tab is named after the stage she is in — a pregnant user reads
     * "Homilador", not "Sikl" — so the label is a question about the stage, not a
     * constant.
     */
    fun journey(stage: LifeStage): String
}

/** The stage names, as the app says them back to her in a settings row or a header. */
interface LifeStageStrings {
    fun title(stage: LifeStage): String
}

interface WelcomeStrings {
    val title: String
    val subtitle: String
    val featureCycle: String
    val featureMood: String
    val featureNutrition: String
    val featureMeds: String
    val featureAi: String
    val featureInsights: String
    val privacyPromise: String
    val start: String
    val haveAccount: String
    val signIn: String
}

/**
 * The parts of the question flow that must answer in the language being picked: the
 * language question itself changes under her hand as she taps.
 */
interface OnboardingStrings {
    val languageTitle: String
    val languageSubtitle: String
    val continueLabel: String
}

interface ProfileStrings {
    val title: String
    val unnamed: String

    val sleep: String
    val medications: String
    val secretChat: String
    val insights: String
    val knowledge: String

    val personalDetails: String
    val goals: String
    val lifeStage: String
    val connectedDevices: String
    val notifications: String
    val privacyAndSecurity: String

    val language: String
    val theme: String
    val themeDark: String
    val themeLight: String
    val about: String

    val signOut: String
    val signingOut: String

    val premiumBadge: String
    val premiumActive: String
    val premiumYearly: String
    val premiumFeatureAi: String
    val premiumFeatureScanner: String
    val premiumFeatureInsights: String
    val upgradeTitle: String
    val upgradeSubtitle: String
}

interface SettingsStrings {
    val aboutTitle: String
    fun version(number: String): String
    val languageTitle: String
    /** Said once above the list: the app changes language immediately, nothing else does. */
    val languageNote: String
    val languageSaveFailed: String
}
