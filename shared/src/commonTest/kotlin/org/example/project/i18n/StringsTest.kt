package org.example.project.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import org.example.project.model.AppLanguage
import org.example.project.model.ConceptionWindow
import org.example.project.model.CyclePhase
import org.example.project.model.Goal
import org.example.project.model.LifeStage
import org.example.project.model.Mood
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.MealSlot

/**
 * The interface already guarantees that every language answers every string — that is
 * why it is an interface. What it cannot guarantee is that the answer is a translation:
 * a blank, or the Uzbek pasted across, compiles perfectly well.
 */
class StringsTest {

    private val languages = listOf(StringsUz, StringsRu, StringsEn)

    /** A Friday, so the weekday and the month both have something to say. */
    private val SampleDate = LocalDate.parse("2026-09-04")

    /** Everything a language says, in one list, so a test can look at all of it. */
    private fun everything(t: Strings): List<String> = buildList {
        add(t.languageName.uz)
        add(t.languageName.ru)
        add(t.languageName.en)

        add(t.tabs.today)
        add(t.tabs.mind)
        add(t.tabs.nutrition)
        add(t.tabs.profile)
        LifeStage.entries.forEach { add(t.tabs.journey(it)) }
        LifeStage.entries.forEach { add(t.stages.title(it)); add(t.stages.subtitle(it)) }

        with(t.welcome) {
            addAll(
                listOf(
                    title, subtitle, featureCycle, featureMood, featureNutrition, featureMeds,
                    featureAi, featureInsights, privacyPromise, start, haveAccount, signIn,
                ),
            )
        }
        with(t.onboarding) {
            addAll(listOf(languageTitle, languageSubtitle, continueLabel))
        }
        with(t.profile) {
            addAll(
                listOf(
                    premiumUntil("x"), premiumRenewsOn("x"), premiumNoExpiry,
                    title, unnamed, sleep, medications, secretChat, insights, knowledge,
                    personalDetails, goals, lifeStage, connectedDevices, notifications,
                    privacyAndSecurity, language, theme, themeDark, themeLight, about,
                    signOut, signingOut, premiumBadge, premiumActive, premiumYearly,
                    premiumFeatureAi, premiumFeatureScanner, premiumFeatureInsights,
                    upgradeTitle, upgradeSubtitle,
                ),
            )
        }
        with(t.settings) {
            addAll(
                listOf(
                    aboutTitle, version("1.0.0"), languageTitle, languageNote, languageSaveFailed,
                    personalTitle, name, birthDate, height, weight, centimetres, kilograms,
                    weightNote, goalsTitle, goalsChosen(3), lifeStageTitle, lifeStageNote,
                    notificationsTitle, medReminder, medReminderNote, cycleReminder,
                    cycleReminderNote, waterReminder, waterReminderNote, aiSummary, aiSummaryNote,
                    privacyTitle, consentHealth, consentHealthNote, consentAi, consentAiNote,
                    consentAnalytics, consentAnalyticsNote, saveConsents, legalDocuments, terms,
                    privacyPolicy, yourData, exportData, deleteAccount, deleteAccountConfirm,
                    deleteAccountBody, medicalDisclaimer,
                ),
            )
        }
        with(t.common) {
            addAll(listOf(0, 9, 13, 21).map { greeting(it) })
            Mood.entries.forEach { add(mood(it)); add(moodCaption(it)) }
            CyclePhase.entries.forEach { add(phase(it)); add(phaseFertility(it)); add(phaseEnergy(it)) }
            Goal.entries.forEach { add(goal(it)) }
            ConceptionWindow.entries.forEach { add(conceptionWindow(it)) }
            addAll(listOf(save, saving, cancel, delete, close, add, edit, done))
            addAll(listOf(yes, no, back, loading, retry, optional))
            addAll(listOf(litres, millilitres, kcal, steps, minutesShort, days(3), hoursMinutes(6, 40)))
        }
        with(t.dates) {
            addAll(months)
            addAll(weekdays)
            addAll(listOf(today, yesterday, tomorrow, justNow))
            addAll(listOf(minutesAgo(20), hoursAgo(3), daysAgo(5)))
            add(dayMonth(SampleDate))
            add(dayMonthWeekday(SampleDate))
            add(monthYear(2026, 9))
        }
        with(t.modules) {
            addAll(
                listOf(
                    sleepTitle, sleepEmptyTitle, sleepEmptyBody, sleepWeek, average("6s"),
                    daysRecorded(5, 7), sleepManual, goalFrom(8), lastNight, restingPulse(58),
                    deep, light, stages, insightsTitle, windowDays(30), windowPremium,
                    insightsEmptyTitle, loadFailed, noRecordsInWindow, noRecordsBody, sleepTrend,
                    activityTrend, moodTrend, notEnoughForChart, notEnoughForChartBody,
                    correlations, correlationsPremium, noCorrelation, noCorrelationBody,
                    averagePrefix, all, knowledgeTitle, search, libraryFailed, libraryEmpty,
                    libraryEmptyBody, nothingFound, nothingFoundBody, clearFilters,
                    readMinutes(4), medsTitle, today, history, nextDose, oneTabletWith("x"),
                    take, later, skip, medsEmpty, medsEmptyBody, addMedication, medsDisclaimer,
                    stockLeft("X", 5), stockDays(5), pending, skipped, featureCycleMood,
                    featureFoodDiary, featureAiChat, featureScanner, featureLongInsights,
                    premiumTitle, premiumBody, plansFailed, plansFailedBody, paymentAccepted,
                    paymentPending, noPaymentMethod, cancelAnytime, restorePurchase,
                    priceFor("299 000", true), priceFor("299 000", false), perMonth("24 900"),
                    saving(38), payWithPayme, payWithClick, payWithAppStore, payWithGooglePlay,
                    balanceTitle, fourDirections, balanceDisclaimer, balanced, someRoomIn("x"),
                    fallingBehind("x"), food, water, activity, sleep, ofKcal("1", "2"),
                    ofLitres("1", "2"), ofSteps("1", "2"), ofSleep("6s"),
                    journalTitle, journalPrivate, journalLabel, journalPrompt, journalEmpty,
                    journalEmptyBody, journalDeleteTitle, journalDeleteBody, journalDeleteAction,
                    sourcesTitle, sourcesConnected(2), lastSample("x"), noSampleYet, sourcesEmpty,
                    sourcesEmptyBody, sourcesNote, connected, notConnected, samples("12"),
                ),
            )
            HealthMetric.entries.forEach { add(t.modules.metric(it)) }
        }
        with(t.journey) {
            addAll(
                listOf(
                    cycleTitle, info, calendar, noPredictionTitle, noPredictionBody, markPeriod,
                    today, daysToNextPeriod(5), symptoms, change, averageCycle, averagePeriod,
                    day, daysValue(28), pregnancyTitle, trimester(8), trimester(20), trimester(35),
                    weekCaps, weekAndDay(26, 3), weekOnly(26), dueOn("12-dekabr", 112),
                    dueOnPast("12-dekabr"), babyDevelopment, babyDevelopmentBody, todaysSymptoms,
                    addSymptom, upcomingAppointments, all, noAppointments, noAppointmentsBody,
                    logToday, aiAdvice, aiBadge, postpartumTitle, recoveryWeeks, recoveryNote,
                    mood, sleep, brokenSleep, feedingAndWater, water, calories, moodWatch,
                    moodWatchBody, postpartumLibrary, postpartumLibraryBody, perimenopauseTitle,
                    cycleRegularity, noData, lastCycles(6), regularityEmpty,
                    regularitySpread(24, 38), regularitySteady(27, 29), energy, observation,
                    observationBody, seeSymptoms, menopauseTitle, scoreNote, activity,
                    weeklyGoals, strengthTraining, calciumAndD,
                ),
            )
        }
        with(t.mind) {
            addAll(
                listOf(
                    title, todayIs("1-may"), stress, energy, journal, journalPrompt, journalHint,
                    moodWeek, weekAverage("3,8"), assistant, assistantPremium, assistantFree, mood,
                    breathing, breathingPurpose, meditation, meditationSubtitle, meditationPurpose,
                    fourSevenEight, practiceMeta(5, "x"), start, breathIn, breathHold, breathOut,
                    breathingHint, meditationHint, finish, close,
                ),
            )
            addAll(levels)
        }
        with(t.nutrition) {
            addAll(
                listOf(
                    title, insights, meals, addMeal, emptyTitle, emptyBody, water,
                    waterOfGoal("1,2", "2,0"), addWater(250), aiAnalysis, aiBasis, scanner,
                    scannerHint, balance, balanceHint, today, protein, fat, carbs,
                    proteinInline, fatInline, carbsInline, balanced(400), shortOf("x"),
                    kcal(250), grams(12),
                ),
            )
            MealSlot.entries.forEach { add(mealSlot(it)) }
        }
        with(t.today) {
            addAll(
                listOf(
                    greetingLine("X"), hello("Malika"), hello(""), aiFootnote, aiFreePrompt, cycleCard, notEnoughForPrediction,
                    cycleDayOf(10, 28), pregnancyWeek(26), quickActions, journal, meditation,
                    breathing, reminders, summary, phaseSentence(10, "x"), waterRemaining(250),
                    waterGoalMet, doseDue("X", "09:00"), sleptAndEnergy("6s", true),
                    sleptAndEnergy("6s", false), generalAdvice, plan, taken, water,
                    waterLeft(250), addWater(250), healthScore, sleep, mood, steps,
                    scoreWord(90), scoreWord(70), scoreWord(50), scoreWord(10),
                    emptySummaryTitle, emptySummaryBody, startTitle, startBody, startAction,
                ),
            )
        }
    }

    @Test
    fun `no language leaves a string blank`() {
        languages.forEach { language ->
            assertTrue(everything(language).none { it.isBlank() })
        }
    }

    @Test
    fun `a language is named in itself everywhere`() {
        languages.forEach {
            assertEquals("O'zbekcha", it.languageName.uz)
            assertEquals("Русский", it.languageName.ru)
            assertEquals("English", it.languageName.en)
        }
    }

    /**
     * Five tabs share one row, so a label that reads well in a file can still break the
     * bar. Twelve characters is what the narrowest supported width fits.
     */
    @Test
    fun `a tab label stays short enough for the bar`() {
        languages.forEach { t ->
            val labels = listOf(t.tabs.today, t.tabs.mind, t.tabs.nutrition, t.tabs.profile) +
                LifeStage.entries.map { t.tabs.journey(it) }
            labels.forEach { assertTrue(it.length <= 12, "too long for the tab bar: $it") }
        }
    }

    /**
     * The sentences, at least, must have been translated. Product names are excluded
     * on purpose: "SADORA AI" is the same word in all three.
     */
    @Test
    fun `the prose is actually translated and not the Uzbek pasted across`() {
        listOf(StringsRu, StringsEn).forEach { t ->
            assertNotEquals(StringsUz.welcome.title, t.welcome.title)
            assertNotEquals(StringsUz.welcome.subtitle, t.welcome.subtitle)
            assertNotEquals(StringsUz.welcome.privacyPromise, t.welcome.privacyPromise)
            assertNotEquals(StringsUz.profile.signOut, t.profile.signOut)
            assertNotEquals(StringsUz.settings.languageNote, t.settings.languageNote)
            assertNotEquals(StringsUz.onboarding.languageTitle, t.onboarding.languageTitle)
            assertNotEquals(StringsUz.stages.title(LifeStage.Cycle), t.stages.title(LifeStage.Cycle))
            assertNotEquals(StringsUz.common.greeting(9), t.common.greeting(9))
            assertNotEquals(StringsUz.common.moodCaption(Mood.Great), t.common.moodCaption(Mood.Great))
            assertNotEquals(StringsUz.today.generalAdvice, t.today.generalAdvice)
            assertNotEquals(StringsUz.today.startBody, t.today.startBody)
            assertNotEquals(StringsUz.mind.journalPrompt, t.mind.journalPrompt)
            assertNotEquals(StringsUz.mind.breathingHint, t.mind.breathingHint)
            assertNotEquals(StringsUz.nutrition.emptyBody, t.nutrition.emptyBody)
            assertNotEquals(StringsUz.nutrition.scannerHint, t.nutrition.scannerHint)
            assertNotEquals(StringsUz.journey.noPredictionBody, t.journey.noPredictionBody)
            assertNotEquals(StringsUz.journey.recoveryNote, t.journey.recoveryNote)
            assertNotEquals(StringsUz.journey.moodWatchBody, t.journey.moodWatchBody)
            assertNotEquals(StringsUz.modules.medsDisclaimer, t.modules.medsDisclaimer)
            assertNotEquals(StringsUz.modules.noCorrelationBody, t.modules.noCorrelationBody)
            assertNotEquals(StringsUz.modules.balanced, t.modules.balanced)
            assertNotEquals(StringsUz.today.hello("X"), t.today.hello("X"))
            assertNotEquals(StringsUz.stages.subtitle(LifeStage.Cycle), t.stages.subtitle(LifeStage.Cycle))
            assertNotEquals(StringsUz.common.hoursMinutes(6, 40), t.common.hoursMinutes(6, 40))
            assertNotEquals(StringsUz.common.goal(Goal.SleepBetter), t.common.goal(Goal.SleepBetter))
            assertNotEquals(StringsUz.settings.weightNote, t.settings.weightNote)
            assertNotEquals(StringsUz.settings.consentHealthNote, t.settings.consentHealthNote)
            assertNotEquals(StringsUz.settings.medicalDisclaimer, t.settings.medicalDisclaimer)
            assertNotEquals(StringsUz.modules.journalEmptyBody, t.modules.journalEmptyBody)
            assertNotEquals(StringsUz.modules.sourcesNote, t.modules.sourcesNote)
            assertNotEquals(StringsUz.dates.months.first(), t.dates.months.first())
            assertNotEquals(StringsUz.dates.weekdays.first(), t.dates.weekdays.first())
            assertNotEquals(StringsUz.dates.hoursAgo(3), t.dates.hoursAgo(3))
            assertNotEquals(StringsUz.nutrition.mealSlot(MealSlot.LUNCH), t.nutrition.mealSlot(MealSlot.LUNCH))
        }
    }

    /**
     * A month named on its own and a day inside it are different words in Russian, and
     * writing "4 Сентябрь" is the mistake this catches.
     */
    @Test
    fun `a day inside a month reads as a date rather than a month name`() {
        assertEquals("4-sentabr", StringsUz.dates.dayMonth(SampleDate))
        assertEquals("4 сентября", StringsRu.dates.dayMonth(SampleDate))
        assertEquals("4 September", StringsEn.dates.dayMonth(SampleDate))
        languages.forEach { t ->
            assertEquals(12, t.dates.months.size)
            assertEquals(7, t.dates.weekdays.size)
        }
    }

    /** Five rungs share one dial; a long word there wraps and breaks the row. */
    @Test
    fun `a dial rung stays short enough for its row`() {
        languages.forEach { t ->
            t.mind.levels.forEach { assertTrue(it.length <= 16, "too long for the dial: $it") }
            assertEquals(5, t.mind.levels.size)
        }
    }

    @Test
    fun `every language the app offers has strings behind it`() {
        assertEquals(StringsUz, stringsFor(AppLanguage.Uz))
        assertEquals(StringsRu, stringsFor(AppLanguage.Ru))
        assertEquals(StringsEn, stringsFor(AppLanguage.En))
        // Whatever is added to the enum next needs a file here; this is what says so.
        assertEquals(3, AppLanguage.entries.size)
    }

    /**
     * The second tab is named after the stage, not after the cycle: a pregnant user
     * never reads "Sikl" — in any language.
     */
    @Test
    fun `the journey tab is named after the stage`() {
        languages.forEach { t ->
            assertNotEquals(t.tabs.journey(LifeStage.Cycle), t.tabs.journey(LifeStage.Pregnancy))
            assertNotEquals(t.tabs.journey(LifeStage.Cycle), t.tabs.journey(LifeStage.Menopause))
        }
    }
}
