package org.example.project.i18n

import kotlinx.datetime.LocalDate
import org.example.project.model.ConceptionWindow
import org.example.project.model.CyclePhase
import org.example.project.model.Goal
import org.example.project.model.LifeStage
import org.example.project.model.Mood
import uz.sadora.contract.FetalMovement
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.MealSlot
import uz.sadora.contract.SymptomCategory

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

        override fun subtitle(stage: LifeStage) = when (stage) {
            LifeStage.Cycle -> "Periods, ovulation, symptoms"
            LifeStage.TryingToConceive -> "Fertile days, getting ready"
            LifeStage.Pregnancy -> "Week, growth, appointments"
            LifeStage.Postpartum -> "Recovery, sleep, mood"
            LifeStage.Perimenopause -> "Regularity, symptoms"
            LifeStage.Menopause -> "Health and mood"
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
        override fun premiumUntil(date: String) = "until $date"
        override fun premiumRenewsOn(date: String) = "renews on $date"
        override val premiumNoExpiry = "No end date"
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
        override val personalTitle = "Personal details"
        override val name = "Name"
        override val birthDate = "Date of birth"
        override val height = "Height"
        override val weight = "Weight"
        override val centimetres = "cm"
        override val kilograms = "kg"
        override val weightNote = "Weight is optional, and it is never shown to anyone else."

        override val goalsTitle = "Goals"
        override fun goalsChosen(count: Int) = "$count chosen"

        override val lifeStageTitle = "Life stage"
        override val lifeStageNote = "Changing the stage rebuilds the Journey tab and the " +
            "screens that belong to it. Everything you have recorded stays."

        override val notificationsTitle = "Notifications"
        override val medReminder = "Medication reminders"
        override val medReminderNote = "10 minutes before each dose"
        override val cycleReminder = "Period reminder"
        override val cycleReminderNote = "As the expected date approaches"
        override val waterReminder = "Water reminder"
        override val waterReminderNote = "Three times a day"
        override val aiSummary = "Daily AI summary"
        override val aiSummaryNote = "At 08:00"

        override val privacyTitle = "Privacy and security"
        override val consentHealth = "Store health data"
        override val consentHealthNote = "Required for the app to work. Stored encrypted."
        override val consentAi = "Use it for AI insights"
        override val consentAiNote = "To prepare insights and suggestions for you."
        override val consentAnalytics = "Anonymous analytics"
        override val consentAnalyticsNote = "Optional. It helps make the app better."
        override val saveConsents = "Save consents"
        override val legalDocuments = "Legal documents"
        override val terms = "Terms of use"
        override val privacyPolicy = "Privacy policy"
        override val yourData = "Your data"
        override val exportData = "Export my data"
        override val deleteAccount = "Delete account"
        override val deleteAccountConfirm = "Delete your account?"
        override val deleteAccountBody = "Your data will be deleted for good. " +
            "We suggest exporting it first."

        override val medicalDisclaimer = "SADORA does not diagnose. If something worries " +
            "you, see a doctor."
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

        override fun goal(goal: Goal) = when (goal) {
            Goal.UnderstandCycle -> "Understand my cycle"
            Goal.SleepBetter -> "Sleep better"
            Goal.MoreEnergy -> "More energy"
            Goal.LessStress -> "Less stress"
            Goal.EatBalanced -> "Eat in balance"
            Goal.DrinkWater -> "Drink more water"
            Goal.BeActive -> "Be more active"
            Goal.RememberMeds -> "Remember my medication"
        }

        override fun conceptionWindow(window: ConceptionWindow) = when (window) {
            ConceptionWindow.JustStarted -> "Just started"
            ConceptionWindow.UnderThreeMonths -> "Under 3 months"
            ConceptionWindow.ThreeToSix -> "3–6 months"
            ConceptionWindow.SixToTwelve -> "6–12 months"
            ConceptionWindow.OverAYear -> "Over a year"
        }

        override val saving = "Saving…"
        override val yes = "Yes"
        override val no = "No"
        override val back = "Back"
        override val loading = "Loading…"
        override val retry = "Try again"
        override val optional = "Optional"
        override val save = "Save"
        override val cancel = "Cancel"
        override val delete = "Delete"
        override val close = "Close"
        override val add = "Add"
        override val edit = "Edit"
        override val done = "Done"

        override fun hoursMinutes(hours: Int, minutes: Int) = "${hours}h ${minutes}m"
        override val litres = "l"
        override val millilitres = "ml"
        override val kcal = "kcal"
        override val steps = "steps"
        override val minutesShort = "min"
        override fun days(count: Int) = if (count == 1) "1 day" else "$count days"
    }

    override val dates = object : DateStrings {
        override val months = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )

        override val weekdays = listOf(
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
        )

        override val weekdaysShort = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        override fun dayMonth(date: LocalDate) = "${date.day} ${months[date.month.ordinal]}"

        override val today = "Today"
        override val yesterday = "Yesterday"
        override val tomorrow = "Tomorrow"

        override val justNow = "just now"
        override fun minutesAgo(minutes: Int) = "$minutes min ago"
        override fun hoursAgo(hours: Int) = "$hours h ago"
        override fun daysAgo(days: Int) = "$days d ago"
    }

    override val today = object : TodayStrings {
        override fun greetingLine(greeting: String) =
            "$greeting — a lovely day to take care of yourself 🌸"

        override fun hello(name: String) = if (name.isBlank()) "Hello!" else "Hello, $name!"
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

        override fun mealSlot(slot: MealSlot) = when (slot) {
            MealSlot.BREAKFAST -> "Breakfast"
            MealSlot.LUNCH -> "Lunch"
            MealSlot.DINNER -> "Dinner"
            MealSlot.SNACK -> "Snack"
        }

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

        override val calendarTitle = "Calendar"
        override val history = "History"
        override val predictedNote = "Outlined days are a calculation, not a medical guarantee."
        override val markPeriodDay = "Mark a period day"
        override val phaseNotColouredYet = "Once there are period dates, the phases here get their colour."
        override val previousMonth = "Previous month"
        override val nextMonth = "Next month"
        override val keyPeriod = "Period"
        override val keyFertile = "Fertile"
        override val keyPredicted = "Predicted"
        override val dayCaps = "DAY"
        override fun symptomsAndMood(symptoms: String, mood: String) = "$symptoms · mood $mood"
        override fun noSymptomsAndMood(mood: String) = "No symptoms logged · mood $mood"
        override val statsNote = "These figures rest on the cycles you have entered. The more " +
            "there are, the closer they get."
        override val regularity = "Regularity"
        override val regularSteady = "Steady"
        override val regularVaries = "Varies"
        override val cycleLength = "Cycle length"
        override fun lastNCycles(count: Int) = "last $count cycles"
        override val previousCycles = "Previous cycles"
        override val noHistoryYet = "No cycle history yet"
        override val noHistoryYetBody = "Once a second period date is in, the length and the " +
            "regularity are worked out here."
        override fun periodOfDays(days: Int) = "period $days days"
        override val currentCycle = "Current"

        override fun cycleDayOrdinal(day: Int) = "Cycle day $day"
        override val cycleDayCaps = "CYCLE DAY"
        override val loggedToday = "Logged today"
        override val logged = "Logged"
        override val noSymptomsLogged = "No symptoms logged"
        override val nothingLoggedForDay = "Nothing was logged for this day."
        override fun moodLine(mood: String) = "Mood — $mood"
        override fun energyLine(level: Int) = "Energy — $level / 5"
        override fun sleepAndSteps(sleep: String, steps: String) = "Slept $sleep · $steps steps"
        override val fromDevice = "From your device"
        override val editEntry = "Edit"

        override val symptomSheetTitle = "Add a symptom"
        override val catalogueLoading = "Loading the symptom list…"
        override val severity = "How strong"
        override val severityWords = listOf(
            "Barely there",
            "Mild — it does not get in the way",
            "Moderate — distracting at times",
            "Strong — it makes work hard",
            "Very strong — I cannot do my usual things",
        )
        override val notePlaceholder = "Add a note…"
        override fun categoryName(category: SymptomCategory) = when (category) {
            SymptomCategory.PAIN -> "Pain"
            SymptomCategory.BLEEDING -> "Discharge"
            SymptomCategory.MOOD -> "Mood"
            SymptomCategory.SLEEP -> "Sleep"
            SymptomCategory.ENERGY -> "Energy"
            SymptomCategory.DIGESTION -> "Digestion"
            SymptomCategory.SKIN -> "Skin"
            SymptomCategory.OTHER -> "Other"
        }

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

        override val appointmentsTitle = "Appointments"
        override val filterUpcoming = "Upcoming"
        override val filterPast = "Past"
        override val filterAll = "All"
        override val listEmpty = "Nothing here yet"
        override val nothingInThisFilter = "Nothing in this filter"
        override val appointmentsEmptyBody = "Write down the date of a check-up, a scan or a " +
            "test — the reminder is set from here too."
        override val addAppointment = "Add an appointment"
        override val nextCaps = "NEXT"
        override val todayCaps = "TODAY"
        override val tomorrowCaps = "TOMORROW"
        override fun inDaysCaps(days: Int) = "IN $days DAYS"
        override val appointmentsNote = "You fill this list in yourself. SADORA does not " +
            "prescribe a screening schedule."
        override val appointmentDone = "Done"
        override fun reminderSet(offset: String) = "Reminder $offset"
        override fun reminderOffset(hours: Int) = when (hours) {
            in 0..2 -> "2 hours before"
            in 3..24 -> "a day before"
            else -> "2 days before"
        }
        override val noReminder = "None"
        override val editAppointment = "Edit appointment"
        override val appointmentName = "Name"
        override val appointmentNameHint = "Screening scan"
        override val appointmentDate = "Date"
        override val appointmentDateHint = "27.8.2026"
        override val appointmentDateInvalid = "Date as day.month.year"
        override val appointmentTime = "Time (optional)"
        override val appointmentPlace = "Place (optional)"
        override val appointmentPlaceHint = "Republican centre"
        override val reminder = "Reminder"
        override val appointmentDateNote = "Write the date as day.month.year, for example 27.8.2026."

        override val checkInTitle = "How are you feeling?"
        override val todaysSymptomsLabel = "Symptoms today"
        override val babyMovement = "Baby's movement"
        override fun movement(movement: FetalMovement) = when (movement) {
            FetalMovement.USUAL -> "As usual"
            FetalMovement.LESS -> "Less"
            FetalMovement.MORE -> "More"
        }
        override val movementWarning = "If movement drops noticeably or stops, see a doctor " +
            "without delay."
        override val privateNote = "Note — only you see this"
        override val privateNoteHint = "Write it down…"
        override val checkInSaved = "Today's check-in is saved"

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

        override val stageSymptomsTitle = "Symptoms"
        override val noRecordsYet = "Nothing recorded yet"
        override val noRecordsYetBody = "Mark today's signs below. After a few days you will " +
            "see here which of them comes up most often."
        override fun windowDays(days: Int) = "$days days"
        override fun weekNumber(week: Int) = "Week $week"
        override fun recordedOnDays(window: Int, days: Int) = "Recorded on $days of $window days."
        override val logToday2 = "Log today"
        override val mostFrequent = "Most frequent"
        override val symptomsDisclaimer = "This list is for keeping track. If something is new " +
            "or getting stronger, talk it over with a doctor."

        override val sleepMoodTitle = "Sleep and mood"
        override val notEnoughData = "Not enough data yet"
        override val notEnoughDataBody = "Sleep comes from a watch or a phone, and mood from " +
            "the daily check-in. After a few days the two will show here together."
        override val scoreCaps = "SCORE"
        override fun sleepGoal(hours: String) = "Goal · $hours"
        override val moodWeek7 = "Mood over 7 days"
        override val noticed = "Noticed"
        override val breathingCard = "Breathing practice"
        override val breathingCardNote = "Before bed · 4 minutes"
        override val journalCard = "Journal"
        override val journalCardNote = "Only you see it"

        override val estimatedCaps = "ESTIMATE"
        override val balanceCaps = "BALANCE"
        override val premiumCaps = "PREMIUM"
        override val libraryCaps = "LIBRARY"
        override val predictionDisclaimer = "The prediction rests on what you have entered " +
            "and is not a medical conclusion."
    }

    override val modules = object : ModuleStrings {
        override val sleepTitle = "Sleep"
        override val sleepEmptyTitle = "No sleep data"
        override val sleepEmptyBody =
            "Once a watch or phone syncs, your sleep length and its stages appear here."
        override val sleepWeek = "Length over 7 days"
        override fun average(value: String) = "Average $value"
        override fun daysRecorded(withData: Int, total: Int) = "$withData / $total days recorded"
        override val sleepManual = "Enter sleep by hand"
        override fun goalFrom(hours: Int) = "from $hours hours"
        override val lastNight = "Last night"
        override fun restingPulse(bpm: Int) = "Resting pulse $bpm bpm"
        override val deep = "Deep"
        override val light = "Light"
        override val stages = "Stages"

        override val insightsTitle = "Insights"
        override fun windowDays(days: Int) = "$days days"
        override val windowPremium = "This window opens with Premium"
        override val insightsEmptyTitle = "No insights yet"
        override val loadFailed = "Could not load. Check your connection and try again."
        override val noRecordsInWindow = "Nothing recorded in this window"
        override val noRecordsBody =
            "Record sleep, mood, water or food and the trends are drawn here. We do not " +
                "show a number nobody measured."
        override val sleepTrend = "Sleep trend"
        override val activityTrend = "Activity"
        override val moodTrend = "Mood"
        override val notEnoughForChart = "Not enough data for a chart"
        override val notEnoughForChartBody =
            "No sleep, step or mood records were found in this window."
        override val correlations = "What was noticed"
        override val correlationsPremium = "Correlations open with Premium"
        override val noCorrelation = "No dependable correlation was found in this window."
        override val noCorrelationBody =
            "At least eight days of records are needed, and the difference has to be " +
                "clear — otherwise we write nothing."
        override val averagePrefix = "Average — "

        override val all = "All"
        override val knowledgeTitle = "Knowledge"
        override val search = "Search"
        override val libraryFailed = "The library did not open"
        override val libraryEmpty = "The library is empty for now"
        override val libraryEmptyBody = "New articles will appear here as they are written."
        override val nothingFound = "Nothing found"
        override val nothingFoundBody = "Try another word or a different category."
        override val clearFilters = "Clear the filters"
        override fun readMinutes(minutes: Int) = "$minutes MIN"

        override val medsTitle = "Medications"
        override val today = "Today"
        override val history = "History"
        override val nextDose = "Next dose"
        override fun oneTabletWith(note: String) = "1 tablet · $note"
        override val take = "Taken"
        override val later = "Later"
        override val skip = "Skip"
        override val medsEmpty = "No medications added yet"
        override val medsEmptyBody = "Add one and its times and stock will show here."
        override val addMedication = "Add a medication"
        override val medsDisclaimer =
            "SADORA gives no instruction about a missed dose. Follow the medicine's own " +
                "instructions, or your doctor's or pharmacist's advice."
        override fun stockLeft(name: String, days: Int) = "$name stock lasts $days more days"
        override fun stockDays(days: Int) = "Stock $days days"
        override val pending = "Pending"
        override val skipped = "Skipped"

        override val featureCycleMood = "Cycle and mood"
        override val featureFoodDiary = "Food diary"
        override val featureAiChat = "AI chat"
        override val featureScanner = "Food scanner"
        override val featureLongInsights = "30/90-day insights"
        override val premiumTitle = "SADORA Premium"
        override val premiumBody =
            "AI chat, the food scanner and longer insights. Everything on the free plan " +
                "stays."
        override val plansFailed = "Plans did not load"
        override val plansFailedBody = "Check your connection and try again."
        override val paymentAccepted = "Payment received. Premium is open."
        override val paymentPending = "Waiting for payment…"
        override val noPaymentMethod = "No payment method is available yet."
        override val cancelAnytime = "Cancel at any time"
        override val restorePurchase = "Restore a purchase"
        override fun priceFor(sum: String, monthly: Boolean) =
            "$sum UZS / " + (if (monthly) "month" else "year")
        override fun perMonth(sum: String) = "$sum UZS/month"
        override fun saving(percent: Int) = "−$percent%"
        override val payWithPayme = "Pay with Payme"
        override val payWithClick = "Pay with Click"
        override val payWithAppStore = "Through the App Store"
        override val payWithGooglePlay = "Through Google Play"

        override val journalTitle = "Journal and practice"
        override val journalPrivate = "ONLY YOU SEE THIS"
        override val journalLabel = "Journal"
        override val journalPrompt = "How are you feeling today?"
        override val journalEmpty = "The journal is still empty"
        override val journalEmptyBody = "Write your first entry. Nobody but you will see it."
        override val journalDeleteTitle = "Delete this entry"
        override val journalDeleteBody = "The entry will be deleted for good."
        override val journalDeleteAction = "Delete this entry"

        override val sourcesTitle = "Data sources"
        override fun sourcesConnected(count: Int) = "$count sources connected"
        override fun lastSample(ago: String) = "Last sample $ago"
        override val noSampleYet = "No samples yet"
        override val sourcesEmpty = "Nothing connected"
        override val sourcesEmptyBody = "Once you allow HealthKit or Health Connect, the " +
            "samples that arrive and their times will show up here."
        override val sourcesNote = "Every figure carries its source and a timestamp. When the " +
            "same figure arrives from more than one source, your priority settings decide."
        override val connected = "Connected"
        override val notConnected = "Not connected"
        override fun samples(count: String) = "$count samples"
        override fun metric(metric: HealthMetric) = when (metric) {
            HealthMetric.STEPS -> "Steps"
            HealthMetric.ACTIVE_ENERGY -> "Active calories"
            HealthMetric.DISTANCE -> "Distance"
            HealthMetric.HEART_RATE -> "Heart rate"
            HealthMetric.RESTING_HEART_RATE -> "Resting heart rate"
            HealthMetric.HRV -> "HRV"
            HealthMetric.RESPIRATORY_RATE -> "Respiratory rate"
            HealthMetric.BODY_TEMPERATURE -> "Temperature"
            HealthMetric.SLEEP_DURATION -> "Sleep"
            HealthMetric.SLEEP_DEEP -> "Deep sleep"
            HealthMetric.SLEEP_REM -> "REM"
            HealthMetric.WEIGHT -> "Weight"
        }

        override val balanceTitle = "Balance"
        override val fourDirections = "Four directions"
        override val balanceDisclaimer =
            "The balance score is measured against your own goals. It is not a medical " +
                "measure."
        override val balanced =
            "All four directions are in balance today. Food is not a debt to be burned off."
        override fun someRoomIn(direction: String) =
            "The day is going well. There is some room in \"$direction\" — give it a " +
                "thought if you like."
        override fun fallingBehind(direction: String) =
            "\"$direction\" is behind today. The day is not over — no rush."
        override val food = "Food"
        override val water = "Water"
        override val activity = "Activity"
        override val sleep = "Sleep"
        override fun ofKcal(eaten: String, goal: String) = "$eaten / $goal kcal"
        override fun ofLitres(drunk: String, goal: String) = "$drunk / $goal l"
        override fun ofSteps(walked: String, goal: String) = "$walked / $goal steps"
        override fun ofSleep(slept: String) = "$slept / 8h"
    }
}
