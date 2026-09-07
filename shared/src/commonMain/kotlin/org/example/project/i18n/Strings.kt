package org.example.project.i18n

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.example.project.model.ConceptionWindow
import org.example.project.model.CyclePhase
import org.example.project.model.Goal
import org.example.project.model.LifeStage
import org.example.project.model.Mood
import uz.sadora.contract.DoseStatus
import uz.sadora.contract.FetalMovement
import uz.sadora.contract.FoodRelation
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.MealSlot
import uz.sadora.contract.ScheduleKind
import uz.sadora.contract.SymptomCategory

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
    val dates: DateStrings
    val today: TodayStrings
    val mind: MindStrings
    val nutrition: NutritionStrings
    val journey: JourneyStrings
    val modules: ModuleStrings
    val ai: AiStrings
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

    fun goal(goal: Goal): String
    fun conceptionWindow(window: ConceptionWindow): String

    val save: String
    /** The save button while the request is in flight. */
    val saving: String
    val cancel: String
    val delete: String
    val close: String
    val add: String
    val edit: String
    val done: String
    val yes: String
    val no: String
    val back: String
    val loading: String
    val retry: String
    val optional: String

    /** "1,2 l" — the unit, not the number, which [org.example.project.model.Fmt] makes. */
    val litres: String
    val millilitres: String
    val kcal: String
    val steps: String
    val minutesShort: String
    fun days(count: Int): String

    /** "6s 40d" — a duration in hours and minutes, abbreviated per language. */
    fun hoursMinutes(hours: Int, minutes: Int): String
}

/**
 * Dates in words.
 *
 * The bucketing of "how long ago" is the same in every language, so it stays here and
 * only the words are answered per language. Months are given twice where a language
 * needs it: Russian names a month on its own in the nominative ("Сентябрь") and a day
 * inside it in the genitive ("4 сентября").
 */
interface DateStrings {
    /** Month names as a month is named on its own, January first. */
    val months: List<String>

    /** Weekday names, Monday first, as they read in the middle of a sentence. */
    val weekdays: List<String>

    /** The same seven, short enough to head a calendar column. */
    val weekdaysShort: List<String>

    /** "4-sentabr" — the day inside its month. */
    fun dayMonth(date: LocalDate): String

    /** "4-sentabr, payshanba" */
    fun dayMonthWeekday(date: LocalDate): String =
        "${dayMonth(date)}, ${weekdays[date.dayOfWeek.ordinal]}"

    /** "Sentabr 2026" — a calendar header. */
    fun monthYear(year: Int, month: Int): String = "${months[month - 1]} $year"

    val today: String
    val yesterday: String
    val tomorrow: String

    val justNow: String
    fun minutesAgo(minutes: Int): String
    fun hoursAgo(hours: Int): String
    fun daysAgo(days: Int): String

    /** How old something is, the way a feed reads it. Past a month it says the date. */
    fun ago(at: Instant, now: Instant): String {
        val seconds = (now - at).inWholeSeconds
        return when {
            seconds < 60 -> justNow
            seconds < 3600 -> minutesAgo((seconds / 60).toInt())
            seconds < 86_400 -> hoursAgo((seconds / 3600).toInt())
            seconds < 2 * 86_400 -> yesterday
            seconds < 30 * 86_400 -> daysAgo((seconds / 86_400).toInt())
            else -> dayMonth(at.toLocalDateTime(TimeZone.currentSystemDefault()).date)
        }
    }

    /** "Bugun", "Kecha", then the date — the way a diary is read. */
    fun relativeDay(date: LocalDate, today: LocalDate): String = when (date.daysUntil(today)) {
        0 -> this.today
        1 -> yesterday
        -1 -> tomorrow
        else -> dayMonth(date)
    }
}

/** The AI chat, its free-plan preview, and the one line that must be on every answer. */
interface AiStrings {
    val title: String
    val subtitle: String
    val menu: String
    val back: String
    val send: String
    val inputHint: String
    val emptyPrompt: String
    fun basis(cycleDay: Int, sleep: String, water: String): String
    fun questionsLeft(left: Int, limit: Int): String
    val answerFailed: String
    val sessionOnly: String
    val clearChat: String
    val medicalDisclaimer: String

    /** The chips under the chat. Each opens a question in that area. */
    val topics: List<Pair<String, String>>

    // ---- the free-plan preview
    val freeBadge: String
    val howCanIHelp: String
    val readsYourData: String
    val sampleAnswer: String
    val sampleAnswerBody: String
    val sampleAnswerAdvice: String
    val freeFeatures: List<String>
    val freeKeeps: String
    val seePremium: String
    val notNow: String
}

interface TodayStrings {
    /** The header line: the greeting, then the app's own sentence about the day. */
    fun greetingLine(greeting: String): String

    /** "Salom, Malika!", or just the greeting when the name is not known yet. */
    fun hello(name: String): String

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

    /** The line under the title: what that stage is about, in a few words. */
    fun subtitle(stage: LifeStage): String
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
    /** "6-sentabr 2027-yilgacha" — when the plan ends. */
    fun premiumUntil(date: String): String
    /** The same date when the plan renews itself instead of ending. */
    fun premiumRenewsOn(date: String): String
    /** A plan with no end date at all — a grant, not a purchase. */
    val premiumNoExpiry: String
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

    // ---- personal details
    val personalTitle: String
    val name: String
    val birthDate: String
    val height: String
    val weight: String
    val centimetres: String
    val kilograms: String
    /** Said next to the weight field, because it is the one people hesitate over. */
    val weightNote: String

    // ---- goals
    val goalsTitle: String
    fun goalsChosen(count: Int): String

    // ---- life stage
    val lifeStageTitle: String
    val lifeStageNote: String

    // ---- notifications
    val notificationsTitle: String
    val medReminder: String
    val medReminderNote: String
    val cycleReminder: String
    val cycleReminderNote: String
    val waterReminder: String
    val waterReminderNote: String
    val aiSummary: String
    val aiSummaryNote: String

    // ---- privacy
    val privacyTitle: String
    val consentHealth: String
    val consentHealthNote: String
    val consentAi: String
    val consentAiNote: String
    val consentAnalytics: String
    val consentAnalyticsNote: String
    val saveConsents: String
    val legalDocuments: String
    val terms: String
    val privacyPolicy: String
    val yourData: String
    val exportData: String
    val deleteAccount: String
    val deleteAccountConfirm: String
    val deleteAccountBody: String

    /** The line the app repeats wherever it says anything about health. */
    val medicalDisclaimer: String
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

    /** Which meal of the day a log belongs to. The slot is the enum; this is its name. */
    fun mealSlot(slot: MealSlot): String

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

    // ---- calendar
    val calendarTitle: String
    val history: String
    val predictedNote: String
    val markPeriodDay: String
    val phaseNotColouredYet: String
    val previousMonth: String
    val nextMonth: String
    val keyPeriod: String
    val keyFertile: String
    val keyPredicted: String
    val dayCaps: String
    fun symptomsAndMood(symptoms: String, mood: String): String
    fun noSymptomsAndMood(mood: String): String
    val statsNote: String
    val regularity: String
    val regularSteady: String
    val regularVaries: String
    val cycleLength: String
    fun lastNCycles(count: Int): String
    val previousCycles: String
    val noHistoryYet: String
    val noHistoryYetBody: String
    fun periodOfDays(days: Int): String
    val currentCycle: String

    // ---- one day
    fun cycleDayOrdinal(day: Int): String
    val cycleDayCaps: String
    val loggedToday: String
    val logged: String
    val noSymptomsLogged: String
    val nothingLoggedForDay: String
    fun moodLine(mood: String): String
    fun energyLine(level: Int): String
    fun sleepAndSteps(sleep: String, steps: String): String
    val fromDevice: String
    val editEntry: String

    // ---- symptom sheet
    val symptomSheetTitle: String
    val catalogueLoading: String
    val severity: String
    val severityWords: List<String>
    val notePlaceholder: String
    fun categoryName(category: SymptomCategory): String

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

    // ---- appointments
    val appointmentsTitle: String
    val filterUpcoming: String
    val filterPast: String
    val filterAll: String
    val listEmpty: String
    val nothingInThisFilter: String
    val appointmentsEmptyBody: String
    val addAppointment: String
    val nextCaps: String
    val todayCaps: String
    val tomorrowCaps: String
    fun inDaysCaps(days: Int): String
    val appointmentsNote: String
    val appointmentDone: String
    fun reminderSet(offset: String): String
    fun reminderOffset(hours: Int): String
    val noReminder: String
    val editAppointment: String
    val appointmentName: String
    val appointmentNameHint: String
    val appointmentDate: String
    val appointmentDateHint: String
    val appointmentDateInvalid: String
    val appointmentTime: String
    val appointmentPlace: String
    val appointmentPlaceHint: String
    val reminder: String
    val appointmentDateNote: String

    // ---- pregnancy check-in
    val checkInTitle: String
    val todaysSymptomsLabel: String
    val babyMovement: String
    fun movement(movement: FetalMovement): String
    val movementWarning: String
    val privateNote: String
    val privateNoteHint: String
    val checkInSaved: String

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

    // ---- stage detail: symptoms
    val stageSymptomsTitle: String
    val noRecordsYet: String
    val noRecordsYetBody: String
    fun windowDays(days: Int): String
    fun weekNumber(week: Int): String
    fun recordedOnDays(window: Int, days: Int): String
    val logToday2: String
    val mostFrequent: String
    val symptomsDisclaimer: String

    // ---- stage detail: sleep and mood
    val sleepMoodTitle: String
    val notEnoughData: String
    val notEnoughDataBody: String
    val scoreCaps: String
    fun sleepGoal(hours: String): String
    val moodWeek7: String
    val noticed: String
    val breathingCard: String
    val breathingCardNote: String
    val journalCard: String
    val journalCardNote: String

    // ---- badges, in the caps the design draws them in
    val estimatedCaps: String
    val balanceCaps: String
    val premiumCaps: String
    val libraryCaps: String
    val predictionDisclaimer: String
}

/**
 * The screens reached from a tab rather than being one: sleep, insights, the library,
 * medications, the paywall and balance.
 */
interface ModuleStrings {
    // ---- sleep
    val sleepTitle: String
    val sleepEmptyTitle: String
    val sleepEmptyBody: String
    val sleepWeek: String
    fun average(value: String): String
    fun daysRecorded(withData: Int, total: Int): String
    val sleepManual: String
    fun goalFrom(hours: Int): String
    val lastNight: String
    fun restingPulse(bpm: Int): String
    val deep: String
    val light: String
    val stages: String

    // ---- insights
    val insightsTitle: String
    fun windowDays(days: Int): String
    val windowPremium: String
    val insightsEmptyTitle: String
    val loadFailed: String
    val noRecordsInWindow: String
    val noRecordsBody: String
    val sleepTrend: String
    val activityTrend: String
    val moodTrend: String
    val notEnoughForChart: String
    val notEnoughForChartBody: String
    val correlations: String
    val correlationsPremium: String
    val noCorrelation: String
    val noCorrelationBody: String
    val averagePrefix: String
    /** The app never states causation — only co-occurrence. */
    val correlationDisclaimer: String

    // ---- knowledge
    val all: String
    val knowledgeTitle: String
    val search: String
    val libraryFailed: String
    val libraryEmpty: String
    val libraryEmptyBody: String
    val nothingFound: String
    val nothingFoundBody: String
    val clearFilters: String
    fun readMinutes(minutes: Int): String

    // ---- medications
    val medsTitle: String
    val today: String
    val history: String
    val nextDose: String
    fun oneTabletWith(note: String): String
    val take: String
    val later: String
    val skip: String
    val medsEmpty: String
    val medsEmptyBody: String
    val addMedication: String
    val medsDisclaimer: String
    fun stockLeft(name: String, days: Int): String
    fun stockDays(days: Int): String
    val pending: String
    val skipped: String

    // ---- paywall
    val featureCycleMood: String
    val featureFoodDiary: String
    val featureAiChat: String
    val featureScanner: String
    val featureLongInsights: String
    val premiumTitle: String
    val premiumBody: String
    val plansFailed: String
    val plansFailedBody: String
    val paymentAccepted: String
    val paymentPending: String
    val noPaymentMethod: String
    val cancelAnytime: String
    val restorePurchase: String
    fun priceFor(sum: String, monthly: Boolean): String
    fun perMonth(sum: String): String
    fun saving(percent: Int): String
    val payWithPayme: String
    val payWithClick: String
    val payWithAppStore: String
    val payWithGooglePlay: String

    // ---- add a medication
    val addMedTitle: String
    val medName: String
    val medNameHint: String
    val medDose: String
    val medUnit: String
    val medTime: String
    val medTimeInvalid: String
    val addTime: String
    val medDays: String
    val medFoodRelation: String
    fun foodRelation(relation: FoodRelation): String
    fun scheduleKind(kind: ScheduleKind): String
    /** The caption under a dose: her own note, or how it sits with food. */
    fun doseCaption(note: String?, relation: FoodRelation): String
    val medStock: String
    val medStockUnit: String
    val medEndDate: String
    val medNone: String

    // ---- dose history
    val doseHistoryTitle: String
    val takenCount: String
    val skippedCount: String
    fun adherenceOver(days: Int): String
    fun lastDays(days: Int): String
    val noDoseHistory: String
    val noDoseHistoryBody: String
    fun doseStatus(status: DoseStatus): String

    // ---- food scanner
    val scannerTitle: String
    val scannerFrameHint: String
    val scannerLightHint: String
    val scannerGallery: String
    val scannerShutter: String
    val scannerManual: String
    val scannerPremium: String
    val scannerUnavailable: String
    val scannerUnavailableBody: String
    val analysing: String
    val analysingWait: String
    val scanFailed: String
    val scanFailedBody: String
    val notFood: String
    val scanResult: String
    fun scanConfidence(percent: Int): String
    fun portionAndKcal(portion: String, kcal: String): String
    val nutrients: String
    val fibre: String
    val sugar: String
    val sodium: String
    val portion: String
    val portionHint: String
    val didYouEatIt: String
    val yesIAte: String
    val planningToEat: String

    // ---- journal
    val journalTitle: String
    val journalPrivate: String
    val journalLabel: String
    val journalPrompt: String
    val journalEmpty: String
    val journalEmptyBody: String
    val journalDeleteTitle: String
    val journalDeleteBody: String
    val journalDeleteAction: String

    // ---- data sources
    val sourcesTitle: String
    fun sourcesConnected(count: Int): String
    fun lastSample(ago: String): String
    val noSampleYet: String
    val sourcesEmpty: String
    val sourcesEmptyBody: String
    val sourcesNote: String
    val connected: String
    val notConnected: String
    fun samples(count: String): String
    fun metric(metric: HealthMetric): String

    // ---- balance
    val balanceTitle: String
    val fourDirections: String
    val balanceDisclaimer: String
    val balanced: String
    fun someRoomIn(direction: String): String
    fun fallingBehind(direction: String): String
    val food: String
    val water: String
    val activity: String
    val sleep: String
    fun ofKcal(eaten: String, goal: String): String
    fun ofLitres(drunk: String, goal: String): String
    fun ofSteps(walked: String, goal: String): String
    fun ofSleep(slept: String): String
}
