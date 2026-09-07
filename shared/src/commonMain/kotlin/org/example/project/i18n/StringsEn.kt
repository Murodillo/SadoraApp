package org.example.project.i18n

import org.example.project.model.CyclePhase
import org.example.project.model.LifeStage
import org.example.project.model.Mood

/**
 * English. Translated from [StringsUz], and kept plain: this is the language most of
 * its readers will have as their second or third, so a shorter word beats a smarter one.
 */
object StringsEn : Strings {
    override val languageName = object : LanguageNames {}

    override val tabs = object : TabStrings {
        override val today = "Today"
        override val mind = "Mind"
        override val nutrition = "Food"
        override val profile = "Profile"
        override fun journey(stage: LifeStage) = when (stage) {
            LifeStage.Cycle -> "Cycle"
            LifeStage.TryingToConceive -> "Plan"
            LifeStage.Pregnancy -> "Pregnant"
            LifeStage.Postpartum -> "Recovery"
            LifeStage.Perimenopause -> "Stage"
            LifeStage.Menopause -> "Health"
        }
    }

    override val stages = object : LifeStageStrings {
        override fun title(stage: LifeStage) = when (stage) {
            LifeStage.Cycle -> "Cycle tracking"
            LifeStage.TryingToConceive -> "Planning a pregnancy"
            LifeStage.Pregnancy -> "Pregnancy"
            LifeStage.Postpartum -> "After birth"
            LifeStage.Perimenopause -> "Perimenopause"
            LifeStage.Menopause -> "Menopause"
        }
    }

    override val welcome = object : WelcomeStrings {
        override val title = "Welcome to SADORA"
        override val subtitle = "Your personal companion for health, cycle, nutrition and mood."
        override val featureCycle = "Cycle"
        override val featureMood = "Mood"
        override val featureNutrition = "Nutrition"
        override val featureMeds = "Vitamins and medicines"
        override val featureAi = "SADORA AI"
        override val featureInsights = "Insights and tips"
        override val privacyPromise = "Your data is yours — delete it whenever you like"
        override val start = "Get started"
        override val haveAccount = "I have an account →"
        override val signIn = "Sign in"
    }

    override val onboarding = object : OnboardingStrings {
        override val languageTitle = "Choose your language"
        override val languageSubtitle = "You can change this later in settings."
        override val continueLabel = "Continue"
    }

    override val profile = object : ProfileStrings {
        override val title = "Profile"
        override val unnamed = "User"

        override val sleep = "Sleep"
        override val medications = "Medications"
        override val secretChat = "Secret chat"
        override val insights = "Insights"
        override val knowledge = "Knowledge"

        override val personalDetails = "Personal details"
        override val goals = "Goals"
        override val lifeStage = "Life stage"
        override val connectedDevices = "Connected devices"
        override val notifications = "Notifications"
        override val privacyAndSecurity = "Privacy and security"

        override val language = "Language"
        override val theme = "Theme"
        override val themeDark = "Dark"
        override val themeLight = "Light"
        override val about = "About SADORA"

        override val signOut = "Sign out"
        override val signingOut = "Signing out…"

        override val premiumBadge = "SADORA PREMIUM"
        override val premiumActive = "Active"
        override val premiumYearly = "Yearly plan"
        override val premiumFeatureAi = "AI chat"
        override val premiumFeatureScanner = "Food scanner"
        override val premiumFeatureInsights = "Advanced insights"
        override val upgradeTitle = "SADORA Premium"
        override val upgradeSubtitle = "AI chat, food scanner and advanced insights"
    }

    override val settings = object : SettingsStrings {
        override val aboutTitle = "About SADORA"
        override fun version(number: String) = "Version $number"
        override val languageTitle = "Language"
        override val languageNote = "The app changes language straight away. AI answers are " +
            "in Uzbek for now."
        override val languageSaveFailed = "The language was not saved — try again later."
    }

    override val common = object : CommonStrings {
        override fun greeting(hour: Int) = when (hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }

        override fun mood(mood: Mood) = when (mood) {
            Mood.Bad -> "Hard"
            Mood.Low -> "Low"
            Mood.Ok -> "Okay"
            Mood.Good -> "Calm"
            Mood.Great -> "Great"
        }

        override fun moodCaption(mood: Mood) = when (mood) {
            Mood.Bad -> "Be kind to yourself today."
            Mood.Low -> "A slower day — that is normal too."
            Mood.Ok -> "An ordinary day for balance."
            Mood.Good -> "A good day for balance."
            Mood.Great -> "Your energy is high — make the most of it!"
        }

        override fun phase(phase: CyclePhase) = when (phase) {
            CyclePhase.Period -> "Period"
            CyclePhase.Follicular -> "Follicular"
            CyclePhase.Fertile -> "Ovulation"
            CyclePhase.Luteal -> "Luteal"
        }

        override fun phaseFertility(phase: CyclePhase) = when (phase) {
            CyclePhase.Period -> "Chance of conceiving is low"
            CyclePhase.Follicular -> "Chance of conceiving is rising"
            CyclePhase.Fertile -> "Chance of conceiving is high"
            CyclePhase.Luteal -> "Chance of conceiving is falling"
        }

        override fun phaseEnergy(phase: CyclePhase) = when (phase) {
            CyclePhase.Period -> "Your body is resting — go gently with yourself."
            CyclePhase.Follicular -> "Energy is building — a good stretch for new things."
            CyclePhase.Fertile -> "Energy is at its peak — use it for the active days."
            CyclePhase.Luteal -> "Energy tapers off — leave room to rest."
        }

        override val save = "Save"
        override val cancel = "Cancel"
        override val delete = "Delete"
        override val close = "Close"
        override val add = "Add"
        override val edit = "Edit"
        override val done = "Done"

        override val litres = "l"
        override val millilitres = "ml"
        override val kcal = "kcal"
        override val steps = "steps"
        override val minutesShort = "min"
        override fun days(count: Int) = if (count == 1) "1 day" else "$count days"
    }

    override val today = object : TodayStrings {
        override fun greetingLine(greeting: String) =
            "$greeting — a lovely day to take care of yourself 🌸"

        override val aiFootnote = "Based on your data · written by AI"
        override val aiFreePrompt = "Ask anything about your health and how you feel"

        override val cycleCard = "Cycle"
        override val notEnoughForPrediction = "Not enough data to predict yet"
        override fun cycleDayOf(day: Int, length: Int) = "Day $day / $length"
        override fun pregnancyWeek(week: Int) = "Week $week"

        override val quickActions = "Quick actions"
        override val journal = "Journal"
        override val meditation = "Meditation"
        override val breathing = "Breathing"
        override val reminders = "Reminders"

        override val summary = "Today in short"
        override fun phaseSentence(day: Int, phase: String) = "Cycle day $day — $phase."
        override fun waterRemaining(ml: Int) = "Water: $ml ml still to drink."
        override val waterGoalMet = "Water goal reached."
        override fun doseDue(name: String, time: String) = "$name — at $time."
        override fun sleptAndEnergy(sleep: String, energyIsHigh: Boolean) =
            "You slept $sleep and your energy is " + (if (energyIsHigh) "good" else "lower") + "."
        override val generalAdvice = "Drink a little more water today, and plan an easy walk."

        override val plan = "Today's plan"
        override val taken = "Taken"
        override val water = "Water"
        override fun waterLeft(ml: Int) = "$ml ml to go"
        override fun addWater(ml: Int) = "+$ml ml"

        override val healthScore = "Health score"
        override val sleep = "Sleep"
        override val mood = "Mood"
        override val steps = "Steps"
        override fun scoreWord(score: Int) = when {
            score >= 80 -> "Great"
            score >= 60 -> "Good"
            score >= 40 -> "Fair"
            else -> "Low"
        }

        override val emptySummaryTitle = "Today in short"
        override val emptySummaryBody =
            "Nothing recorded yet. Add your first entry and the daily summary and charts " +
                "will appear here."
        override val startTitle = "Shall we start with today?"
        override val startBody = "Mood, water or food — whichever is easiest to begin with."
        override val startAction = "Add your first entry"
    }

    override val mind = object : MindStrings {
        override val title = "Mind and mood"
        override fun todayIs(date: String) = "Today · $date"

        override val stress = "Stress"
        override val energy = "Energy"
        override val levels = listOf("Very low", "Low", "Moderate", "High", "Very high")

        override val journal = "Journal"
        override val journalPrompt = "How are you feeling?"
        override val journalHint = "Write down your thoughts and feelings"

        override val moodWeek = "Mood over 7 days"
        override fun weekAverage(value: String) = "Average $value"

        override val assistant = "Mind assistant"
        override val assistantPremium = "Talk through how your mood and sleep connect"
        override val assistantFree = "With Premium: a supportive conversation — not a therapist"
        override val mood = "Mood"

        override val breathing = "Breathing"
        override val breathingPurpose = "Lower stress"
        override val meditation = "Meditation"
        override val meditationSubtitle = "A quiet mind"
        override val meditationPurpose = "Rest"
        override val fourSevenEight = "4-7-8"
        override fun practiceMeta(minutes: Int, purpose: String) = "$minutes min • $purpose"
        override val start = "Start"

        override val breathIn = "Breathe in"
        override val breathHold = "Hold"
        override val breathOut = "Breathe out"
        override val breathingHint = "In for 4 · hold for 7 · out for 8"
        override val meditationHint = "Close your eyes and follow your breath"
        override val finish = "Finish"
        override val close = "Close"
    }

    override val nutrition = object : NutritionStrings {
        override val title = "Nutrition"
        override val insights = "Insights"
        override val meals = "Meals"
        override val addMeal = "Add a meal"
        override val emptyTitle = "Nothing eaten recorded today"
        override val emptyBody = "Add your first dish — calories and macros will add up here."

        override val water = "Water"
        override fun waterOfGoal(drunk: String, goal: String) = "$drunk l / $goal l"
        override fun addWater(ml: Int) = "+$ml ml"

        override val aiAnalysis = "AI analysis"
        override val aiBasis = "Worked out from today's numbers"
        override val scanner = "Food scanner"
        override val scannerHint = "Point the camera — the dish, portion and macros are estimated"
        override val balance = "Balance"
        override val balanceHint = "Food, water, activity and sleep — four directions"

        override val today = "Today"
        override val protein = "Protein"
        override val fat = "Fat"
        override val carbs = "Carbs"
        override val proteinInline = "protein"
        override val fatInline = "fat"
        override val carbsInline = "carbohydrate"

        override fun balanced(kcalLeft: Int) =
            "Macros are balanced today. A light dish covers the remaining $kcalLeft kcal."
        override fun shortOf(macro: String) =
            "Today is shortest on $macro. Keep that in mind at your next meal."
        override fun kcal(value: Int) = "$value kcal"
        override fun grams(value: Int) = "$value g"
    }

    override val journey = object : JourneyStrings {
        override val cycleTitle = "My cycle"
        override val info = "Info"
        override val calendar = "Calendar"
        override val noPredictionTitle = "Not enough data to predict yet"
        override val noPredictionBody =
            "Once two periods are recorded, the phases of your cycle and an estimate " +
                "for the next one appear here."
        override val markPeriod = "Mark a period"
        override val today = "Today"
        override fun daysToNextPeriod(days: Int) = "Next period in $days days"
        override val symptoms = "Symptoms"
        override val change = "Change"
        override val averageCycle = "Average cycle"
        override val averagePeriod = "Average period"
        override val day = "Day"
        override fun daysValue(days: Int) = if (days == 1) "1 day" else "$days days"

        override val pregnancyTitle = "Pregnancy"
        override fun trimester(week: Int) = when {
            week <= 13 -> "1st trimester"
            week <= 27 -> "2nd trimester"
            else -> "3rd trimester"
        }
        override val weekCaps = "  WEEK"
        override fun weekAndDay(week: Int, day: Int) = "Week $week, day $day"
        override fun weekOnly(week: Int) = "Week $week"
        override fun dueOn(date: String, daysLeft: Int) = "Due $date · $daysLeft days to go"
        override fun dueOnPast(date: String) = "Due $date"
        override val babyDevelopment = "Your baby's development"
        override val babyDevelopmentBody =
            "Read about what changes this week in the Knowledge library."
        override val todaysSymptoms = "Today's symptoms"
        override val addSymptom = "+ Add"
        override val upcomingAppointments = "Upcoming appointments"
        override val all = "All"
        override val noAppointments = "No appointments yet"
        override val noAppointmentsBody =
            "Write down the date of a check-up or a test — a reminder will follow."
        override val logToday = "Record how today feels"
        override val aiAdvice =
            "Iron-rich food and gentle stretching may help this week. This is general " +
                "health information."
        override val aiBadge = "SADORA AI · SUGGESTION"

        override val postpartumTitle = "After birth"
        override val recoveryWeeks = "  weeks · recovery"
        override val recoveryNote =
            "Recovery goes differently for every woman. This scale is only a guide."
        override val mood = "Mood"
        override val sleep = "Sleep"
        override val brokenSleep = "Broken sleep"
        override val feedingAndWater = "Feeding and water"
        override val water = "Water"
        override val calories = "Calories"
        override val moodWatch = "Keeping an eye on mood"
        override val moodWatchBody =
            "If low mood or anxiety lasts, it is worth seeing a professional. SADORA " +
                "does not diagnose."
        override val postpartumLibrary = "Knowledge — after birth"
        override val postpartumLibraryBody = "Reading on the postpartum period"

        override val perimenopauseTitle = "Perimenopause"
        override val cycleRegularity = "Cycle regularity"
        override val noData = "no data"
        override fun lastCycles(count: Int) = "last $count cycles"
        override val regularityEmpty =
            "Once you start marking periods, your cycle length appears here. No " +
                "prediction is shown at this stage."
        override fun regularitySpread(shortest: Int, longest: Int) =
            "Your cycle has run between $shortest and $longest days — expected at this " +
                "stage. No prediction is shown."
        override fun regularitySteady(shortest: Int, longest: Int) =
            "Your cycle runs between $shortest and $longest days. No prediction is " +
                "shown at this stage."
        override val energy = "Energy"
        override val observation = "Observation"
        override val observationBody =
            "See how sleep, mood and symptoms line up."
        override val seeSymptoms = "See symptoms"

        override val menopauseTitle = "Wellbeing"
        override val scoreNote =
            "Based on sleep, activity, food and mood. This score is not a medical " +
                "measure."
        override val activity = "Activity"
        override val weeklyGoals = "Goals for the week"
        override val strengthTraining = "Strength training"
        override val calciumAndD = "Calcium and vitamin D"
    }
}
