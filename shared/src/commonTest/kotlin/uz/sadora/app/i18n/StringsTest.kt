package uz.sadora.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import uz.sadora.app.model.AppLanguage
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
import uz.sadora.contract.ScheduleKind
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.MealSlot
import uz.sadora.contract.SymptomCategory

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
        add(t.tabs.premium)
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
            addAll(
                listOf(
                    languageTitle, languageSubtitle, continueLabel, skipTheseQuestions,
                    nameTitle, nameSubtitle, nameLabel, nameHint, nameNote, birthYearTitle,
                    birthYearSubtitle, goalsTitle, goalsSubtitle, stageTitle, stageSubtitle,
                    doctorTitle, no, cycleLengthTitle, cycleLengthDerived(28), cycleLengthHint,
                    periodLengthTitle, feelingTitle("Malika"), feelingTitle(""), feelingSubtitle,
                    bodyTitle, bodySubtitle, height, weight, permissionsTitle,
                    permissionsSubtitle, permissionReminders, permissionRemindersNote,
                    permissionHealth, permissionHealthNote, permissionCamera,
                    permissionCameraNote, phoneTitle, phoneSubtitle, sending, sendCode,
                    haveAccount, phoneLabel, phoneNote, codeTitle, codeSubtitle("90 123 45 67"),
                    checking, confirm, resendIn(45), resend, codeSecrecy, periodsTitle,
                    periodsSubtitle(5), markMore, markMoreBody(2), iWillMark,
                    markedWithAverage(2, 3, 28), markedMoreNeeded(1, 3), markAPeriodStart,
                    regularityTitle, regularitySubtitle, regularYes, regularYesNote, regularNo,
                    regularNoNote, regularUnknown, regularUnknownNote, sensitiveTitle,
                    sensitiveBody, birthControlTitle, birthControlSubtitle, conceptionTitle,
                    dueDateTitle, dueDateSubtitle, birthDateTitle, birthDateSubtitle,
                    symptomsTitle("Malika"), symptomsTitle(""), symptomsSubtitle, saveSymptoms,
                    skip, back, notAloneTitle, analysingTitle, readyTitle("Malika"), readyTitle(""),
                    readyBody, saving, startSadora, cycleSummary(28, 5), remindersOn,
                    healthDataOn, goalsChosen(3), signInTitle, signInSubtitle, noAccount, signUp,
                    consentTitle, consentBody, consentHealth, consentHealthMore,
                    consentTermsPrefix, terms, and, privacyPolicy, consentAnalytics, consentAll,
                ),
            )
            LifeStage.entries.forEach { add(stagePromise(it)) }
            BirthControl.entries.forEach { add(birthControl(it)) }
            addAll(BirthControl.entries.mapNotNull { birthControlNote(it) })
            addAll(ConceptionWindow.entries.mapNotNull { conceptionNote(it) })
            starterSymptoms.forEach { add(it.label) }
            feelings.forEach { add(it.label); add(it.reply) }
            proofs.forEach { add(it.headline); add(it.body) }
            addAll(analysisSteps)
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
                    upgradeTitle, upgradeSubtitle, shareProfile, shareProfileNote, devices,
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
                    privacyPolicy, yourData, exportData, exportReady, exportFailed, deleteAccount,
                    deleteAccountConfirm, deleteAccountBody, medicalDisclaimer,
                ),
            )
        }
        with(t.errors) {
            addAll(
                listOf(
                    phoneInvalid, nameRequired, tooLong(60), outOfRange(80, 250), dateFormat,
                    dateInFuture, timeFormat, wholeNumber, network, validation, sessionExpired,
                    blocked, premiumRequired, monthlyLimit, dailyLimit, retryAfter(30), retrySoon,
                    otpInvalid, featureClosed, consentRequired, paymentFailed, unexpected,
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
                    ofLitres("1", "2"), ofSteps("1", "2"), ofSleep("6s"), balanceCapsWord,
                    featureCaps, freeCaps, premiumCapsBadge, journalCardTitle, moodLabel,
                    allDoneToday,
                    searchFood, searchTabAll, searchTabFrequent, searchTabRecipes,
                    typeADishName, nothingFoundFor("x"), catalogueNote, portionLabel, pieces,
                    grams, bowls(2), total, addToDiary, perPiece, perHundredGrams,
                    proteinInitial, fatInitial, carbsInitial, articleFailed, articleFailedBody,
                    readMinutesCaps(4), premiumCaps, author, reviewed, restIsPremium,
                    stepsValue("1 000"), litresValue("1,2"), kcalValue("450"), outOfFive("3,8"),
                    sleepEnergyFinding("4,1", "3,2"), activityMoodFinding("4,1", "3,2"),
                    waterHeadacheFinding("12%", "31%"), basedOnDays(14), minutesOnly(45),
                    addMedTitle, medName, medNameHint, medDose, medUnit, medTime, medTimeInvalid,
                    addTime, medDays, medFoodRelation, medStock, medStockUnit, medEndDate,
                    medNone, doseHistoryTitle, takenCount, skippedCount, adherenceOver(14),
                    lastDays(14), noDoseHistory, noDoseHistoryBody, correlationDisclaimer,
                    scannerTitle, scannerFrameHint, scannerLightHint, scannerGallery,
                    scannerShutter, scannerManual, scannerPremium, scannerUnavailable,
                    scannerUnavailableBody, analysing, analysingWait, scanFailed,
                    scanFailedBody, notFood, scanResult, scanConfidence(82),
                    portionAndKcal("1,0", "450"), nutrients, fibre, sugar, sodium, portion,
                    portionHint, didYouEatIt, yesIAte, planningToEat,
                    journalTitle, journalPrivate, journalLabel, journalPrompt, journalEmpty,
                    journalEmptyBody, journalDeleteTitle, journalDeleteBody, journalDeleteAction,
                    sourcesTitle, sourcesConnected(2), lastSample("x"), noSampleYet, sourcesEmpty,
                    sourcesEmptyBody, sourcesNote, connected, notConnected, samples("12"),
                    sleepManualBody, sleepHours, sleepMinutesLabel, sleepSaved, bodySignalsTitle,
                    bodySignalsNote, vsLastWeek("+3"), strain, recovery,
                ),
            )
            HealthMetric.entries.forEach { add(t.modules.metric(it)) }
            FoodRelation.entries.forEach { add(foodRelation(it)); add(doseCaption(null, it)) }
            ScheduleKind.entries.forEach { add(scheduleKind(it)) }
            DoseStatus.entries.forEach { add(doseStatus(it)) }
            ArticleKind.entries.forEach { add(articleKind(it)) }
            add(doseCaption("her own note", FoodRelation.ANY))
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
                    stageSymptomsTitle, noRecordsYet, noRecordsYetBody, windowDays(28),
                    weekNumber(2), recordedOnDays(28, 9), logToday2, mostFrequent,
                    symptomsDisclaimer, sleepMoodTitle, notEnoughData, notEnoughDataBody,
                    scoreCaps, sleepGoal("8s"), moodWeek7, noticed, breathingCard,
                    breathingCardNote, journalCard, journalCardNote,
                    estimatedCaps, balanceCaps, premiumCaps, libraryCaps, predictionDisclaimer,
                    appointmentsTitle, filterUpcoming, filterPast, filterAll, listEmpty,
                    nothingInThisFilter, appointmentsEmptyBody, addAppointment, nextCaps,
                    todayCaps, tomorrowCaps, inDaysCaps(8), appointmentsNote, appointmentDone,
                    reminderSet("x"), reminderOffset(2), reminderOffset(24), reminderOffset(48),
                    noReminder, editAppointment, appointmentName, appointmentNameHint,
                    appointmentDate, appointmentDateHint, appointmentDateInvalid, appointmentTime,
                    appointmentPlace, appointmentPlaceHint, reminder, appointmentDateNote,
                    checkInTitle, todaysSymptomsLabel, babyMovement, movementWarning,
                    privateNote, privateNoteHint, checkInSaved,
                    calendarTitle, history, predictedNote, markPeriodDay, phaseNotColouredYet,
                    previousMonth, nextMonth, keyPeriod, keyFertile, keyPredicted, dayCaps,
                    symptomsAndMood("x", "y"), noSymptomsAndMood("y"), statsNote, regularity,
                    regularSteady, regularVaries, cycleLength, lastNCycles(6), previousCycles,
                    noHistoryYet, noHistoryYetBody, periodOfDays(5), currentCycle,
                    cycleDayOrdinal(11), cycleDayCaps, loggedToday, logged, noSymptomsLogged,
                    nothingLoggedForDay, moodLine("x"), energyLine(3), sleepAndSteps("6s", "1 000"),
                    fromDevice, editEntry, symptomSheetTitle, catalogueLoading, severity,
                    notePlaceholder,
                ),
            )
            addAll(severityWords)
            SymptomCategory.entries.forEach { add(categoryName(it)) }
            FetalMovement.entries.forEach { add(movement(it)) }
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
                    addWaterTitle, waterAdded(250), undo,
                    kcal(250), grams(12),
                ),
            )
            MealSlot.entries.forEach { add(mealSlot(it)) }
        }
        with(t.ai) {
            addAll(
                listOf(
                    title, subtitle, menu, back, send, inputHint, emptyPrompt,
                    basis(12, "6s 40d", "1,2"), questionsLeft(3, 20), answerFailed,
                    sessionOnly, clearChat, medicalDisclaimer, freeBadge, howCanIHelp,
                    readsYourData, sampleAnswer, sampleAnswerBody, sampleAnswerAdvice,
                    freeKeeps, seePremium, notNow,
                ),
            )
            addAll(freeFeatures)
            topics.forEach { (label, question) -> add(label); add(question) }
        }
        with(t.community) {
            addAll(
                listOf(
                    title, compose, more, saved, nothingSaved, nothingHere, nothingSavedBody,
                    nothingHereBody, write, you, youParenthesised("X"), noComments, commentHint,
                    send, whatIsOnYourMind, postsAs("X"), postsAnonymously, yourOwnPost,
                    deletePost, postDeleted, newPost, postSent, comments, reportPost,
                    reportReasonTitle, reportNote, sendReport,
                    reportSent, shareSuffix,
                ),
            )
            CommunityTopic.entries.forEach { add(topic(it)) }
            CommunityFilter.entries.forEach { add(filter(it)) }
            ReportReason.entries.forEach { add(reportReason(it)) }
        }
        with(t.rewards) {
            addAll(
                listOf(
                    coinName, coins("50"), coinsGained("50"), streakDays(7), streakStarted, streakSubtitle,
                    milestoneReached(30), daysToMilestone(4, 30), streakBeyondMilestones, walletTitle,
                    balance, earned, spent, currentStreak, longestStreak, days(3), history, historyEmpty,
                    howToEarn, perDay(3), openShop, inviteFriends, referralTitle, referralSubtitle,
                    yourCode, copyCode, codeCopied, shareLink, shareMessage("x"), invitedCount(2),
                    referralEarned("200"), rewardPerJoin("200"), welcomeReward("100"), referralHowTitle,
                    referralFairUse,
                ),
            )
            addAll(referralSteps)
        }
        with(t.shop) {
            addAll(
                listOf(
                    title, subtitle, empty, loading, discount(15), saving("x"), premiumDays(7), outOfStock,
                    stockLeft(3), notEnough, shortBy("50"), redeem, redeeming, confirmTitle("x"),
                    confirmBody("50"), confirmPremiumBody, cancel, issuedTitle, issuedPremiumTitle, issuedBody,
                    issuedPremiumBody, yourCode, copyCode, codeCopied, validUntil("x"), myCodes, myCodesEmpty,
                    statusIssued, statusUsed, statusExpired, statusCancelled, partnerNote,
                ),
            )
            uz.sadora.contract.ShopKind.entries.forEach { add(tab(it)) }
        }
        with(t.homeLayout) {
            addAll(listOf(title, subtitle, visible, hidden, moveUp, moveDown, reset, alwaysOn))
            uz.sadora.contract.HomeWidgets.defaults.forEach { add(widget(it.key)); add(widgetNote(it.key)) }
        }
        with(t.share) {
            addAll(
                listOf(
                    title, subtitle, intro, create, creating, regenerate, revoke, revoked, copyLink, linkCopied,
                    shareLink, shareMessage("x"), showToDoctor, validFor, hours(6), days(3), expiresAt("x"),
                    expired, viewedTimes(2), neverViewed, lastViewed("x"), includesTitle, excludesTitle,
                    privacyNote, offline, failed,
                ),
            )
            addAll(includes)
            addAll(excludes)
        }
        with(t.premium) {
            addAll(
                listOf(
                    tab, title, activeTitle, activeBody, inactiveTitle, inactiveBody, benefitsTitle,
                    benefitAiTitle, benefitAiBody, benefitScannerTitle, benefitScannerBody, benefitInsightsTitle,
                    benefitInsightsBody, benefitLibraryTitle, benefitLibraryBody, benefitDevicesTitle,
                    benefitDevicesBody, compareTitle, seePlans, manage, buyWithCoins("Gul"), faqTitle, freeStays,
                ),
            )
            faq.forEach { (question, answer) -> add(question); add(answer) }
        }
        with(t.devices) {
            addAll(
                listOf(
                    title, subtitle, connectedSection, availableSection, plannedSection, connect, connecting,
                    disconnect, disconnectConfirmTitle, disconnectConfirmBody, syncNow, syncing, synced,
                    lastSync("x"), neverSynced, statusActive, statusExpired, statusError, reconnect,
                    unavailable("not_configured"), unavailable("planned"), givesTitle, usedInTitle,
                    openBrowserNote, returnedOk, returnedError, noStepsNote, manualTitle, manualBody, note,
                ),
            )
            uz.sadora.contract.HealthProvider.entries.forEach { add(provider(it)); add(providerTagline(it)); addAll(usedIn(it)) }
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
            val labels = listOf(t.tabs.today, t.tabs.mind, t.tabs.nutrition, t.tabs.premium) +
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
            assertNotEquals(StringsUz.onboarding.nameNote, t.onboarding.nameNote)
            assertNotEquals(StringsUz.onboarding.sensitiveBody, t.onboarding.sensitiveBody)
            assertNotEquals(StringsUz.onboarding.codeSecrecy, t.onboarding.codeSecrecy)
            assertNotEquals(StringsUz.onboarding.consentBody, t.onboarding.consentBody)
            assertNotEquals(StringsUz.onboarding.readyBody, t.onboarding.readyBody)
            assertNotEquals(StringsUz.onboarding.proofs.first().body, t.onboarding.proofs.first().body)
            assertNotEquals(
                StringsUz.onboarding.stagePromise(LifeStage.Pregnancy),
                t.onboarding.stagePromise(LifeStage.Pregnancy),
            )
            assertNotEquals(
                StringsUz.onboarding.starterSymptoms.first().label,
                t.onboarding.starterSymptoms.first().label,
            )
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
            assertNotEquals(StringsUz.journey.predictedNote, t.journey.predictedNote)
            assertNotEquals(StringsUz.journey.noHistoryYetBody, t.journey.noHistoryYetBody)
            assertNotEquals(StringsUz.journey.severityWords[2], t.journey.severityWords[2])
            assertNotEquals(StringsUz.journey.movementWarning, t.journey.movementWarning)
            assertNotEquals(StringsUz.journey.appointmentsNote, t.journey.appointmentsNote)
            assertNotEquals(StringsUz.journey.notEnoughDataBody, t.journey.notEnoughDataBody)
            assertNotEquals(StringsUz.modules.medsDisclaimer, t.modules.medsDisclaimer)
            assertNotEquals(StringsUz.modules.noCorrelationBody, t.modules.noCorrelationBody)
            assertNotEquals(StringsUz.modules.balanced, t.modules.balanced)
            assertNotEquals(StringsUz.today.hello("X"), t.today.hello("X"))
            assertNotEquals(StringsUz.stages.subtitle(LifeStage.Cycle), t.stages.subtitle(LifeStage.Cycle))
            assertNotEquals(StringsUz.common.hoursMinutes(6, 40), t.common.hoursMinutes(6, 40))
            assertNotEquals(StringsUz.errors.phoneInvalid, t.errors.phoneInvalid)
            assertNotEquals(StringsUz.errors.network, t.errors.network)
            assertNotEquals(StringsUz.common.goal(Goal.SleepBetter), t.common.goal(Goal.SleepBetter))
            assertNotEquals(StringsUz.settings.weightNote, t.settings.weightNote)
            assertNotEquals(StringsUz.settings.consentHealthNote, t.settings.consentHealthNote)
            assertNotEquals(StringsUz.settings.medicalDisclaimer, t.settings.medicalDisclaimer)
            assertNotEquals(StringsUz.modules.journalEmptyBody, t.modules.journalEmptyBody)
            assertNotEquals(StringsUz.modules.sourcesNote, t.modules.sourcesNote)
            assertNotEquals(StringsUz.modules.portionHint, t.modules.portionHint)
            assertNotEquals(StringsUz.modules.scanFailedBody, t.modules.scanFailedBody)
            assertNotEquals(StringsUz.modules.noDoseHistoryBody, t.modules.noDoseHistoryBody)
            assertNotEquals(StringsUz.modules.correlationDisclaimer, t.modules.correlationDisclaimer)
            assertNotEquals(StringsUz.ai.emptyPrompt, t.ai.emptyPrompt)
            assertNotEquals(StringsUz.ai.medicalDisclaimer, t.ai.medicalDisclaimer)
            assertNotEquals(StringsUz.ai.sampleAnswerAdvice, t.ai.sampleAnswerAdvice)
            assertNotEquals(StringsUz.community.nothingHereBody, t.community.nothingHereBody)
            assertNotEquals(StringsUz.community.reportNote, t.community.reportNote)
            assertNotEquals(
                StringsUz.modules.sleepEnergyFinding("1", "2"),
                t.modules.sleepEnergyFinding("1", "2"),
            )
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

    /**
     * The onboarding symptom tiles pair a word with a glyph by position, so the two
     * lists have to stay the same length in every language.
     */
    @Test
    fun `every starter symptom has a tile to sit in`() {
        languages.forEach { t ->
            assertEquals(6, t.onboarding.starterSymptoms.size)
            // The key is the catalogue's and must not drift between languages.
            assertEquals(
                StringsUz.onboarding.starterSymptoms.map { it.key },
                t.onboarding.starterSymptoms.map { it.key },
            )
            assertEquals(4, t.onboarding.feelings.size)
            // The analysing ring steps through exactly four captions.
            assertEquals(4, t.onboarding.analysisSteps.size)
            assertEquals(3, t.onboarding.proofs.size)
            t.onboarding.feelings.forEach { assertTrue(it.mood in 1..5) }
        }
    }

    /** The severity scale is always read as words, so all five rungs must exist. */
    @Test
    fun `the severity scale is worded on every rung`() {
        languages.forEach { t ->
            assertEquals(5, t.journey.severityWords.size)
            assertTrue(t.journey.severityWords.none { it.isBlank() })
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

    /**
     * The chat offers four ready questions; a chip that asks nothing is a dead tap.
     */
    @Test
    fun `every AI topic chip carries a question`() {
        languages.forEach { t ->
            assertEquals(4, t.ai.topics.size)
            t.ai.topics.forEach { (label, question) ->
                assertTrue(label.isNotBlank() && question.endsWith("?"), "not a question: $label")
            }
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
