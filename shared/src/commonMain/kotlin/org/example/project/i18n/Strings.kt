package org.example.project.i18n

import org.example.project.model.CyclePhase
import org.example.project.model.LifeStage
import org.example.project.model.Mood

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
    val common: CommonStrings
    val today: TodayStrings
    val mind: MindStrings
    val nutrition: NutritionStrings
    val journey: JourneyStrings
}

/**
 * Words several screens share, and the labels the domain enums used to carry.
 *
 * A label on an enum cannot be translated — the enum is one object for the whole
 * process, and the language is a property of the screen reading it. So the enums keep
 * their identity and the words live here, looked up by the value.
 */
interface CommonStrings {
    /** "Xayrli tong" / "Xayrli kun" / "Xayrli kech", by the hour of the day. */
    fun greeting(hour: Int): String

    fun mood(mood: Mood): String

    /** The line under the face: what the app says back about that mood. */
    fun moodCaption(mood: Mood): String

    fun phase(phase: CyclePhase): String
    fun phaseFertility(phase: CyclePhase): String
    fun phaseEnergy(phase: CyclePhase): String

    val save: String
    val cancel: String
    val delete: String
    val close: String
    val add: String
    val edit: String
    val done: String

    /** "1,2 l" — the unit, not the number, which [org.example.project.model.Fmt] makes. */
    val litres: String
    val millilitres: String
    val kcal: String
    val steps: String
    val minutesShort: String
    fun days(count: Int): String
}

interface TodayStrings {
    /** The header line: the greeting, then the app's own sentence about the day. */
    fun greetingLine(greeting: String): String

    val aiFootnote: String
    val aiFreePrompt: String

    val cycleCard: String
    val notEnoughForPrediction: String
    fun cycleDayOf(day: Int, length: Int): String
    fun pregnancyWeek(week: Int): String

    val quickActions: String
    val journal: String
    val meditation: String
    val breathing: String
    val reminders: String

    val summary: String
    fun phaseSentence(day: Int, phase: String): String
    fun waterRemaining(ml: Int): String
    val waterGoalMet: String
    fun doseDue(name: String, time: String): String
    fun sleptAndEnergy(sleep: String, energyIsHigh: Boolean): String
    val generalAdvice: String

    val plan: String
    val taken: String
    val water: String
    fun waterLeft(ml: Int): String
    fun addWater(ml: Int): String

    val healthScore: String
    val sleep: String
    val mood: String
    val steps: String
    fun scoreWord(score: Int): String

    val emptySummaryTitle: String
    val emptySummaryBody: String
    val startTitle: String
    val startBody: String
    val startAction: String
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

interface MindStrings {
    val title: String
    fun todayIs(date: String): String

    val stress: String
    val energy: String
    /** The five rungs of a dial, from lowest to highest. */
    val levels: List<String>

    val journal: String
    val journalPrompt: String
    val journalHint: String

    val moodWeek: String
    fun weekAverage(value: String): String

    val assistant: String
    val assistantPremium: String
    val assistantFree: String
    val mood: String

    val breathing: String
    val breathingPurpose: String
    val meditation: String
    val meditationSubtitle: String
    val meditationPurpose: String
    val fourSevenEight: String
    fun practiceMeta(minutes: Int, purpose: String): String
    val start: String

    /** The three phases of 4-7-8, in order. */
    val breathIn: String
    val breathHold: String
    val breathOut: String
    val breathingHint: String
    val meditationHint: String
    val finish: String
    val close: String
}

interface NutritionStrings {
    val title: String
    val insights: String
    val meals: String
    val addMeal: String
    val emptyTitle: String
    val emptyBody: String

    val water: String
    fun waterOfGoal(drunk: String, goal: String): String
    fun addWater(ml: Int): String

    val aiAnalysis: String
    val aiBasis: String
    val scanner: String
    val scannerHint: String
    val balance: String
    val balanceHint: String

    val today: String
    val protein: String
    val fat: String
    val carbs: String
    /** The macro named inside a sentence, which several languages inflect. */
    val proteinInline: String
    val fatInline: String
    val carbsInline: String

    fun balanced(kcalLeft: Int): String
    fun shortOf(macro: String): String
    fun kcal(value: Int): String
    fun grams(value: Int): String
}

/**
 * The "Yo'l" tab, which is five screens rather than one: the stage decides which of
 * them she sees, and each has its own vocabulary.
 */
interface JourneyStrings {
    // ---- cycle
    val cycleTitle: String
    val info: String
    val calendar: String
    val noPredictionTitle: String
    val noPredictionBody: String
    val markPeriod: String
    val today: String
    fun daysToNextPeriod(days: Int): String
    val symptoms: String
    val change: String
    val averageCycle: String
    val averagePeriod: String
    val day: String
    fun daysValue(days: Int): String

    // ---- pregnancy
    val pregnancyTitle: String
    fun trimester(week: Int): String
    val weekCaps: String
    fun weekAndDay(week: Int, day: Int): String
    fun weekOnly(week: Int): String
    fun dueOn(date: String, daysLeft: Int): String
    fun dueOnPast(date: String): String
    val babyDevelopment: String
    val babyDevelopmentBody: String
    val todaysSymptoms: String
    val addSymptom: String
    val upcomingAppointments: String
    val all: String
    val noAppointments: String
    val noAppointmentsBody: String
    val logToday: String
    val aiAdvice: String
    val aiBadge: String

    // ---- postpartum
    val postpartumTitle: String
    val recoveryWeeks: String
    val recoveryNote: String
    val mood: String
    val sleep: String
    val brokenSleep: String
    val feedingAndWater: String
    val water: String
    val calories: String
    val moodWatch: String
    val moodWatchBody: String
    val postpartumLibrary: String
    val postpartumLibraryBody: String

    // ---- perimenopause
    val perimenopauseTitle: String
    val cycleRegularity: String
    val noData: String
    fun lastCycles(count: Int): String
    val regularityEmpty: String
    fun regularitySpread(shortest: Int, longest: Int): String
    fun regularitySteady(shortest: Int, longest: Int): String
    val energy: String
    val observation: String
    val observationBody: String
    val seeSymptoms: String

    // ---- menopause
    val menopauseTitle: String
    val scoreNote: String
    val activity: String
    val weeklyGoals: String
    val strengthTraining: String
    val calciumAndD: String
}
