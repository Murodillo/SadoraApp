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
}
