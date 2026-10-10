package uz.sadora.app.i18n

import kotlinx.datetime.LocalDate
import uz.sadora.app.model.BirthControl
import uz.sadora.app.model.CommunityBadge
import uz.sadora.app.model.CommunityFilter
import uz.sadora.app.model.CommunitySort
import uz.sadora.app.model.CommunityTopic
import uz.sadora.app.model.ConceptionWindow
import uz.sadora.app.model.CyclePhase
import uz.sadora.app.model.Goal
import uz.sadora.app.model.LifeStage
import uz.sadora.app.model.Mood
import uz.sadora.app.model.ReportReason
import uz.sadora.contract.ArticleKind
import uz.sadora.contract.CoinReasons
import uz.sadora.contract.HomeWidgets
import uz.sadora.contract.ShopKind
import uz.sadora.contract.DoseStatus
import uz.sadora.contract.FetalMovement
import uz.sadora.contract.FoodRelation
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.MealSlot
import uz.sadora.contract.ScheduleKind
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
        override val mindAndNutrition = "Mind · Food"
        override val secretChat = "Chat"
        override val nutrition = "Food"
        override val premium = "Premium"
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
            LifeStage.Pregnancy -> "Week, growth, visits"
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
        override fun otpEntered(entered: Int, length: Int) = "Verification code: $entered of $length digits entered"
        override val deleteDigit = "Delete last digit"
        override val languageTitle = "Choose your language"
        override val languageSubtitle = "You can change this later in settings."
        override val continueLabel = "Continue"
        override val skip = "Skip"
        override val back = "Back"
        override val skipTheseQuestions = "Skip these questions"

        override val nameTitle = "What should we call you?"
        override val nameSubtitle = "Let's get acquainted. You can change this name later."
        override val nameLabel = "Name"
        override val nameHint = "Your name"
        override val nameNote = "We don't sell your data or share it for advertising. " +
            "You can delete it whenever you like."

        override val birthYearTitle = "What year were you born?"
        override val birthYearSubtitle = "Your age makes the forecasts more accurate."

        override val goalsTitle = "What would you like help with?"
        override val goalsSubtitle = "Choose as many as you like."

        override val stageTitle = "Which stage of life are you in?"
        override val stageSubtitle = "The next questions, and the app itself, follow this choice."
        override fun stagePromise(stage: LifeStage) = when (stage) {
            LifeStage.Cycle -> "We'll follow your cycle and tell you when the next period is due."
            LifeStage.TryingToConceive -> "We'll mark your fertile days and be with you through the preparation."
            LifeStage.Pregnancy -> "We'll follow each week's changes and your check-ups."
            LifeStage.Postpartum -> "Recovery, feeding and mood get particular attention."
            LifeStage.Perimenopause -> "We'll follow symptoms, sleep and energy together."
            LifeStage.Menopause -> "Everyday support aimed at your health goals."
        }

        override val doctorTitle = "Did a doctor recommend SADORA to you?"
        override val no = "No"

        override val cycleLengthTitle = "How many days does your cycle usually last?"
        override fun cycleLengthDerived(days: Int) =
            "Your dates work out to $days ${en(days, "day", "days")}. Correct it if that's wrong."
        override val cycleLengthHint = "If you're not sure, a rough number is enough — the app will refine it later."
        override val periodLengthTitle = "How many days does your period last?"

        override fun feelingTitle(name: String) =
            if (name.isBlank()) "How are you feeling?" else "$name, how are you feeling?"
        override val feelingSubtitle = "Say it honestly — where we start depends on your answer."
        override val feelings = listOf(
            FeelingOption("Good — all is well 🙂", 4, "Wonderful. We'll help you keep it that way."),
            FeelingOption("Tired 😴", 2, "We'll put sleep and energy first."),
            FeelingOption("Anxious 😟", 2, "We'll start slowly. You only ever write what you want to."),
            FeelingOption("I want to understand my body ✨", 3, "That's exactly what SADORA is for."),
        )

        override val bodyTitle = "Your height and weight"
        override val bodySubtitle = "Optional. Never shown to anyone, and you can delete it whenever you like."
        override val height = "Height"
        override val weight = "Weight"

        override val inviteTitle = "Do you have an invite code?"
        override val inviteSubtitle = "If not, just skip this step."
        override val inviteLabel = "Invite code"
        override val inviteHint = "For example, K7M2QP"
        override fun inviteReward(coins: String) = "With a code you start with $coins Gul"
        override val inviteFromLink = "Taken from your link"

        override val permissionsTitle = "What will you allow?"
        override val permissionsSubtitle = "Each of these can be changed later in Profile."
        override val permissionReminders = "Reminders"
        override val permissionRemindersNote = "We'll remind you about periods, medication and check-ups."
        override val permissionHealth = "Health data"
        override val permissionHealthNote = "We read steps and sleep from your watch."
        override val permissionCamera = "Camera"
        override val permissionCameraNote = "To photograph food and work out what is in it."

        override val phoneTitle = "Enter your number"
        override val phoneSubtitle = "We'll send a one-time code so your answers are saved."
        override val sending = "Sending…"
        override val sendCode = "Send code"
        override val haveAccount = "I have an account · Sign in"
        override val phoneLabel = "Phone number"
        override val phoneNote = "The number is only used to sign in and is never sold for advertising."
        override val codeTitle = "Enter the code"
        override fun codeSubtitle(phone: String) = "We sent a 6-digit code to +998 $phone."
        override val checking = "Checking…"
        override val confirm = "Confirm"
        override fun resendIn(seconds: Int) = "Send again · $seconds s"
        override val resend = "Send code again"
        override val codeSecrecy = "Never share the code. SADORA staff won't ask for it."

        override val periodsTitle = "When were your last periods?"
        override fun periodsSubtitle(periodLength: Int) =
            "Tap the day it started — the other $periodLength ${en(periodLength, "day", "days")} fill themselves in. " +
                "After that you can add or remove days one at a time."
        override val markMore = "Mark another?"
        override fun markMoreBody(marked: Int) =
            "You have marked $marked so far. With three we can measure the length of your " +
                "cycle, and the forecast gets a great deal more accurate."
        override val iWillMark = "I will mark one"
        override fun markedWithAverage(filled: Int, total: Int, averageCycle: Int) =
            "$filled/$total marked · average cycle $averageCycle ${en(averageCycle, "day", "days")}"
        override fun markedMoreNeeded(filled: Int, total: Int) = "$filled/$total marked · mark another"
        override val markAPeriodStart = "Mark the day a period started"

        override val regularityTitle = "Is your cycle regular?"
        override val regularitySubtitle = "Does it arrive on roughly the same day each month?"
        override val regularYes = "Yes, regular"
        override val regularYesNote = "Good — the forecasts will be more accurate from the start."
        override val regularNo = "No, it varies"
        override val regularNoNote = "We'll take that into account and show how sure a forecast is."
        override val regularUnknown = "I don't know"
        override val regularUnknownNote = "That's fine. After a couple of months it becomes clear on its own."

        override val sensitiveTitle = "The next questions are personal"
        override val sensitiveBody = "We'll ask about contraception and trying to conceive. " +
            "They make the forecasts more accurate, but answering is entirely optional."

        override val birthControlTitle = "Have you used contraception in the last 6 months?"
        override val birthControlSubtitle = "Some methods affect the cycle, which is why we ask."
        override fun birthControl(option: BirthControl) = when (option) {
            BirthControl.None -> "No"
            BirthControl.StillUsing -> "I'm still using it"
            BirthControl.Pill -> "Yes, the pill"
            BirthControl.Iud -> "Yes, an IUD"
            BirthControl.Barrier -> "Yes, condoms or another non-hormonal method"
            BirthControl.Other -> "Yes, another method"
            BirthControl.Undisclosed -> "I would rather not say"
        }
        override fun birthControlNote(option: BirthControl) = when (option) {
            BirthControl.Pill, BirthControl.Iud ->
                "After the pill or an IUD a cycle can take a few months to settle — we'll be careful with forecasts."
            BirthControl.StillUsing ->
                "Fertile-day forecasts aren't reliable while you use contraception, so we don't show them."
            else -> null
        }

        override val conceptionTitle = "How long have you been trying to conceive?"
        override fun conceptionNote(window: ConceptionWindow) = when (window) {
            ConceptionWindow.JustStarted ->
                "The beginning of the road — there will be many questions, and we'll be there for each."
            ConceptionWindow.OverAYear ->
                "After a year (or after 6 months if you're over 35) it's worth seeing a doctor. We'll remind you of that too."
            else -> null
        }

        override val dueDateTitle = "When is the baby due?"
        override val dueDateSubtitle = "Mark the approximate date your doctor gave you."
        override val birthDateTitle = "When was your child born?"
        override val birthDateSubtitle = "We count the recovery stages from that date."

        override fun symptomsTitle(name: String) =
            if (name.isBlank()) "What are you noticing today?" else "$name, what are you noticing today?"
        override val symptomsSubtitle = "You can pick several. If there is nothing — skip it."
        override val saveSymptoms = "Save symptoms"
        override val notAloneTitle = "You're not alone"
        override val proofs = listOf(
            Proof("Chosen by women", "Thousands of women in Uzbekistan follow their cycle with SADORA."),
            Proof("With doctors", "The questions and the articles are prepared with gynaecologists."),
            Proof("The data is yours", "Export it whenever you like, or delete all of it."),
        )
        override val analysingTitle = "Setting things up for you"
        override val analysisSteps = listOf(
            "Reading your answers…",
            "Working out your cycle…",
            "Setting up your Today screen…",
            "Almost ready…",
        )

        override fun readyTitle(name: String) =
            if (name.isBlank()) "Done! Your profile is ready" else "Done, $name!"
        override val readyBody = "Your Today screen is set up from your answers. " +
            "You can change any of it later in Profile."
        override val saving = "Saving…"
        override val startSadora = "Start with SADORA"
        override fun cycleSummary(cycleLength: Int, periodLength: Int) =
            "Cycle $cycleLength ${en(cycleLength, "day", "days")} · period $periodLength ${en(periodLength, "day", "days")}"
        override val remindersOn = "Reminders are on"
        override val healthDataOn = "Health data will be connected"
        override fun goalsChosen(count: Int) = "$count ${en(count, "goal", "goals")} chosen"

        override val signInTitle = "Welcome back"
        override val signInSubtitle = "We'll send a code to your number"
        override val noAccount = "No account yet? "
        override val signUp = "Sign up"
        override val notRegisteredTitle = "This number isn't registered"
        override fun notRegisteredBody(phone: String) = "There is no SADORA account for +998 $phone yet. Sign up and make the app yours in a few questions — your number is already confirmed, so no second SMS."
        override val registerNow = "Sign up"
        override val otherNumber = "Another number"

        override val consentTitle = "Your body. Your data."
        override val consentBody = "We don't sell your health data or share it for " +
            "advertising. You can delete it whenever you like."
        override val consentHealth = "I agree to my health data being processed so the app can work. "
        override val consentHealthMore = "More in the "
        override val consentTermsPrefix = "I accept the "
        override val consentTermsSuffix = ""
        override val terms = "Terms of use"
        override val and = " and the "
        override val privacyPolicy = "Privacy policy"
        override val consentAnalytics = "I agree to anonymous analysis of what I do in the " +
            "app. This is optional and is used to make SADORA better."
        override val consentAll = "Agree to all"

        override val starterSymptoms = listOf(
            StarterSymptom("cramps", "Cramps"),
            StarterSymptom("fatigue", "Fatigue"),
            StarterSymptom("swelling", "Bloating"),
            StarterSymptom("breast_tender", "Breast tenderness"),
            StarterSymptom("back_pain", "Lower back pain"),
            StarterSymptom("headache", "Headache"),
        )
    }

    override val profile = object : ProfileStrings {
        override val title = "Profile"
        override val unnamed = "User"

        override val sleep = "Sleep"
        override val medications = "Medications"
        override val secretChat = "Chat"
        override val insights = "Insights"
        override val knowledge = "Knowledge"

        override val rewards = "Gul and streak"
        override val shop = "Gul shop"
        override val referral = "Invite friends"
        override val homeLayout = "Home screen layout"

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
        override val shareProfile = "Show my doctor"
        override val shareProfileNote = "A QR code your doctor scans to see your records"
        override val devices = "Devices"

        override val photoTitle = "Profile photo"
        override val photoChange = "Change photo"
        override val photoGallery = "Choose from gallery"
        override val photoGalleryNote = "From the photos on your phone"
        override val photoCamera = "Take a photo"
        override val photoCameraNote = "With the front camera, right here"
        override val photoRemove = "Remove photo"
        override val photoRemoveNote = "Your initial takes its place"
        override val photoPrivacy = "Only you and a doctor you write to can see your photo. " +
            "In the Chat you stay under your alias."
        override val photoPreviewTitle = "New photo"
        override val photoSave = "Save"
        override val photoSaving = "Uploading…"
        override val photoRemoving = "Removing…"
    }

    override val settings = object : SettingsStrings {
        override val aboutTitle = "About SADORA"
        override fun version(number: String) = "Version $number"
        override val languageTitle = "Language"
        override val languageNote = "The app changes language straight away. AI answers, " +
            "insights and the food scanner reply in it too."
        override val languageSaveFailed = "The language wasn't saved — try again later."
        override val personalTitle = "Personal details"
        override val name = "Name"
        override val birthDate = "Date of birth"
        override val height = "Height"
        override val weight = "Weight"
        override val centimetres = "cm"
        override val kilograms = "kg"
        override val weightNote = "Weight is optional, and it's never shown to anyone else."

        override val goalsTitle = "Goals"
        override fun goalsChosen(count: Int) = "$count chosen"

        override val lifeStageTitle = "Life stage"
        override fun chosenDate(date: String) = "Chosen date: $date"
        override val lifeStageNote = "If you change the stage, the stage tab updates. " +
            "Everything you've recorded stays."

        override val notificationsTitle = "Notifications"
        override val inboxDueToday = "Due today"
        override val inboxEarlier = "Earlier"
        override val inboxEmpty = "No notifications yet"
        override val inboxEmptyBody = "Reminders and summaries will show up here. Choose which ones you get in settings."
        override val inboxSettings = "Notification settings"
        override fun inboxDoseDue(name: String, time: String) = "$name · $time"
        override val inboxDoseAction = "Open medication plan"
        override val notificationSettingsTitle = "Notification settings"
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
        override val legalEffectiveDate = "Effective date"
        override val legal = LegalTextsEn
        override val terms = "Terms of use"
        override val privacyPolicy = "Privacy policy"
        override val yourData = "Your data"
        override val exportData = "Export my data"
        override val exportReady = "Export ready — choose where to send it"
        override val exportFailed = "Could not export. Try again later."
        override val deleteAccount = "Delete account"
        override val deleteAccountConfirm = "Delete your account?"
        override val deleteAccountBody = "Your data will be deleted for good. " +
            "We suggest exporting it first."

        override val medicalDisclaimer = "SADORA doesn't diagnose. If something worries " +
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
            Mood.Low -> "A slower day — that's normal too."
            Mood.Ok -> "An ordinary day — that's good too."
            Mood.Good -> "A good day — hold on to it."
            Mood.Great -> "Your energy is high — make the most of it!"
        }

        override fun phase(phase: CyclePhase) = when (phase) {
            CyclePhase.Period -> "Period"
            CyclePhase.Follicular -> "Follicular"
            CyclePhase.Fertile -> "Fertile window"
            CyclePhase.Luteal -> "Luteal"
        }

        override fun phaseFertility(phase: CyclePhase) = when (phase) {
            CyclePhase.Period -> "Chance of conceiving is lower (not zero)"

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

        override fun hoursMinutes(hours: Int, minutes: Int) = "$hours h $minutes min"
        override val litres = "l"
        override val millilitres = "ml"
        override val kcal = "kcal"
        override val steps = "steps"
        override val minutesShort = "min"
        override val daysWord = "days"
        override fun days(count: Int) = "$count ${en(count, "day", "days")}"
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

    override val ai = object : AiStrings {
        override val title = "SADORA AI"
        override val subtitle = "Your own assistant"
        override val menu = "More"
        override val back = "Back"
        override val send = "Send"
        override val inputHint = "Ask anything…"
        override val emptyPrompt = "Ask about your cycle, food, mood or medication — the " +
            "answer is built from your own data."
        override fun basis(cycleDay: Int, sleep: String, water: String) =
            "Based on: cycle day $cycleDay · slept $sleep · water $water l"
        override fun questionsLeft(left: Int, limit: Int) = " · $left/$limit ${en(limit, "question", "questions")} left"
        override val answerFailed = "That could not be answered. Try again."
        override val sessionOnly = "The conversation lives in this session only and is never " +
            "written to the server. It's gone when you leave the app."
        override val clearChat = "Clear conversation"
        override val medicalDisclaimer =
            "SADORA is a health companion. It doesn't diagnose or prescribe."

        override val topics = listOf(
            "Energy" to "How do I keep my energy steady?",
            "Food" to "What would be good to eat today?",
            "Cycle" to "Why do I feel tired before my period?",
            "Skin" to "Why does my skin change through the cycle?",
        )

        override val freeBadge = "FREE PLAN"
        override val howCanIHelp = "How can I help?"
        override val readsYourData = "It reads your data and answers you personally"
        override val sampleAnswer = "A sample answer"
        override val sampleAnswerBody = "Over the last three days sleep has been shorter than " +
            "usual and you have been drinking less."
        override val sampleAnswerAdvice = "Energy was logged low on the same days. Two steps " +
            "for today: 700 ml of water before lunch, and lights out by 23:00."
        override val freeFeaturesHeading = "Premium unlocks"
        override val freeFeatures = listOf(
            "20 questions a day, with your data in mind",
            "A personal AI summary every day",
            "The food scanner",
            "30- and 90-day insights",
        )
        override val freeKeeps = "Everything on the free plan stays: cycle, mood, water, the " +
            "food diary, medication, and 7-day insights."
        override val seePremium = "See Premium"
        override val notNow = "Not now"
    }

    override val doctors: DoctorStrings = DoctorStringsEn
    override val healthGate: HealthGateStrings = HealthGateStringsEn

    override val community = object : CommunityStrings {
        override val title = "Chat"
        override val compose = "Write"
        override val more = "More"
        override val saved = "Saved"
        override fun topic(topic: CommunityTopic) = when (topic) {
            CommunityTopic.All -> "All"
            CommunityTopic.Cycle -> "Cycle"
            CommunityTopic.Pregnancy -> "Pregnancy"
            CommunityTopic.Wellbeing -> "Wellbeing"
            CommunityTopic.Body -> "Body"
        }
        override fun filter(filter: CommunityFilter) = when (filter) {
            CommunityFilter.Feed -> "Posts"
            CommunityFilter.Saved -> "Saved"
            CommunityFilter.Mine -> "Mine"
        }
        override fun sort(sort: CommunitySort) = when (sort) {
            CommunitySort.Newest -> "New"
            CommunitySort.Active -> "Active"
        }
        override fun anonymousAs(alias: String) = "Under your alias · you: $alias"
        override val anonymous = "Under your alias — your name isn't shown"
        override val rulesTitle = "How the chat works"
        override val rulesIntro = "Everyone here writes under an alias. Others see your posts under your alias only — your profile, phone number and name are never shown."
        override val rules = listOf(
            "Be kind — everyone here came with a question of her own.",
            "Leave no personal details: names, numbers, addresses, photos.",
            "This isn't medical advice. If you have severe pain, heavy bleeding or a fever, see a doctor.",
            "No advertising or selling.",
            "Flag a post that breaks a rule — it will be reviewed.",
        )
        override val rulesButton = "Got it"
        override val filtersTitle = "Filter"
        override val filtersOn = "Filter on"
        override val like = "Like"
        override val save = "Save"
        override val sharePost = "Share"
        override val filtersShow = "Show"
        override val filtersTopic = "Topic"
        override val filtersAuthor = "From"
        override val filtersSort = "Order"
        override val filtersReset = "Reset"
        override val filtersDone = "Done"
        override fun reportReason(reason: ReportReason) = when (reason) {
            ReportReason.Spam -> "Spam or advertising"
            ReportReason.Abuse -> "Abuse or a threat"
            ReportReason.Misinformation -> "Dangerous medical advice"
            ReportReason.PersonalData -> "Personal details revealed"
            ReportReason.Other -> "Something else"
        }

        override val nothingSaved = "Nothing saved yet"
        override val nothingHere = "No posts here yet"
        override val nothingSavedBody = "Save a post that speaks to you and it will wait here."
        override val nothingHereBody = "Be the first — your question goes out under an alias."
        override val nothingMine = "You haven't posted yet"
        override val nothingMineBody = "Posts you write collect here. Others only ever see the alias."
        override val readMore = "…more"
        override fun viewsShort(count: Int) = compactCount(count, thousand = "K", million = "M", decimal = '.')
        override fun viewsSpoken(count: Int) = if (count == 1) "1 view" else "$count views"
        override val postTitle = "Post"
        override fun commentsCount(count: Int) = when (count) {
            0 -> "Comments"
            1 -> "1 comment"
            else -> "$count comments"
        }
        override fun badge(badge: CommunityBadge) = when (badge) {
            CommunityBadge.Newcomer -> "New"
            CommunityBadge.Early -> "Early member"
            CommunityBadge.Writer -> "Writer"
            CommunityBadge.Helper -> "Helper"
            CommunityBadge.Loved -> "Loved"
            CommunityBadge.Veteran -> "Veteran"
        }
        override fun badgeHint(badge: CommunityBadge) = when (badge) {
            CommunityBadge.Newcomer -> "Joined this week"
            CommunityBadge.Early -> "One of the chat's first 500 members"
            CommunityBadge.Writer -> "Has written 5 or more posts"
            CommunityBadge.Helper -> "Has answered with 20 or more comments"
            CommunityBadge.Loved -> "Her posts have earned over 50 likes"
            CommunityBadge.Veteran -> "In the chat for 3 months or more"
        }
        override val profileTitle = "Profile"
        override val myProfileTitle = "My alias"
        override val noBio = "Nothing written about herself yet"
        override val editBio = "About me"
        override val bioHint = "One line about you — age, stage, what you care about. No names or numbers."
        override val acceptMessages = "Accept messages"
        override val acceptMessagesHint = "Off, and nobody can send you a private message"
        override val saveProfile = "Save"
        override val profileSaved = "Profile saved"
        override val statPosts = "Posts"
        override val statComments = "Comments"
        override val statLikes = "Likes"
        override fun memberSince(date: String) = "Since $date"
        override val badgesTitle = "Badges"
        override val noBadges = "No badges yet — write, answer, and they come on their own"
        override val herPosts = "Posts"
        override val noPostsYet = "No posts yet"
        override val messageButton = "Message"
        override val messagesClosed = "Not accepting messages"
        override val block = "Block"
        override val unblock = "Unblock"
        override val blockConfirmTitle = "Block this alias?"
        override val blockConfirmBody = "Neither of you can message the other. Her posts stay in the chat. You can unblock at any time."
        override val blocked = "Blocked"
        override val unblocked = "Unblocked"
        override val viewProfile = "View profile"
        override val doctorsLink = "Doctors"
        override val rulesLink = "Rules"
        override val messagesTitle = "Messages"
        override val messagesSubtitle = "Between the two of you only — under an alias, or with a doctor"
        override val noMessages = "No messages yet"
        override val noMessagesBody = "Tap a post's author in the chat — you can message her from her profile."
        override val messageHint = "Write a message"
        override val conversationBlocked = "This conversation is closed — messages can't be sent"
        override val conversationMenu = "Conversation"
        override val reportConversation = "Report conversation"
        override val newConversation = "New conversation"
        override fun unreadCount(count: Int) = if (count == 1) "1 unread" else "$count unread"
        override val photo = "Photo"
        override val photoNote = "Choose from your gallery"
        override val record = "Patient record"
        override val attach = "Attach"
        override val photoCaptionHint = "Add a caption (optional)"
        override val sendPhoto = "Send photo"
        override val photoFailed = "Could not open the photo"
        override val sending = "Sending…"
        override val tickSent = "Sent"
        override val tickRead = "Read"
        override fun typing(name: String) = "$name is typing…"
        override val typingShort = "typing…"
        override val recordAttached = "Patient record attached"
        override val recordAttachedNote = "The doctor can see it while the consultation is open"
        override val attachRecord = "Attach my patient record"
        override val attachRecordNote = "Cycle, symptoms, medications — the last 90 days"
        override val attachRecordConfirmTitle = "Send your patient record?"
        override val attachRecordConfirmBody = "While the consultation is open, the doctor can see your record: cycle, symptoms, mood, medications and the last 90 days of entries. Once it closes, the doctor can no longer open it."
        override val attachRecordConfirm = "Yes, attach it"
        override val recordTitle = "The record you sent"
        override val recordNote = "This is exactly what the doctor sees — and only while the consultation is open."
        override fun recordAge(age: Int) = "Age $age"
        override fun recordDays(days: Int) = "Last 90 days: entries on $days ${en(days, "day", "days")}"
        override fun recordCycleDay(day: Int) = "Cycle day $day"
        override fun recordPregnancyWeek(week: Int) = "Pregnancy: week $week"
        override val recordSymptoms = "Most frequent symptoms"
        override val recordMedications = "Medications"
        override val recordEmpty = "No entries yet"
        override val write = "Write"
        override val you = "you"
        override fun youParenthesised(alias: String) = "$alias (you)"

        override val noComments = "No replies yet. Be the first."
        override val commentHint = "Write a reply"
        override val send = "Send"
        override val whatIsOnYourMind = "What would you like to ask?"
        override fun postsAs(alias: String) = "This goes out as “$alias” — your name is never shown."
        override val postsAnonymously = "This goes out under an alias — your name is never shown."
        override val yourOwnPost = "This is your post."
        override val deletePost = "Delete this post"
        override val deletePostConfirm = "Yes, delete it for good"
        override val newPost = "New post"
        override val postSent = "Your post was sent"
        override val comments = "Comments"
        override val reportPost = "Report this"
        override val postDeleted = "The post was deleted"
        override val reportReasonTitle = "Why are you reporting this?"
        override val reportNote = "The report goes to a moderator. Who sent it is never shown."
        override val sendReport = "Send report"
        override val reportSent = "The report was sent"
        override val shareSuffix = "SADORA — Chat"
    }

    override val errors = object : ErrorStrings {
        override val phoneInvalid = "That number is incomplete, or no operator uses that code"
        override val nameRequired = "A name can't be empty"
        override fun tooLong(max: Int) = "At most $max characters"
        override fun outOfRange(min: Int, max: Int) = "Must be between $min and $max"
        override val dateFormat = "Enter the date as day.month.year, e.g. 27.08.2026"
        override val dateInFuture = "The date can't be in the future"
        override val timeFormat = "Enter the time as 20:00"
        override val wholeNumber = "Digits only"

        override val network = "Could not reach the internet. Try again."
        override val validation = "Please check what you entered and try again."
        override val sessionExpired = "Your session has ended. Please sign in again."
        override val blocked = "This account is blocked. Contact us from Profile → About SADORA."
        override val forbidden = "This action isn't available for your account."
        override val premiumRequired = "This opens with Premium."
        override val monthlyLimit = "This month's limit is used up. It resets on the 1st."
        override val dailyLimit = "Today's limit is used up. You can continue tomorrow."
        override fun retryAfter(seconds: Int) = "Too many attempts. Try again in $seconds s."
        override val retrySoon = "Too many attempts. Try again shortly."
        override val otpInvalid = "That code is wrong or has expired."
        override val featureClosed = "This section is closed for now."
        override val consentRequired = "Give consent in Privacy to use this."
        override val paymentFailed = "The payment did not go through. Try again."
        override val paymentRequired = "This consultation is paid. Pay to start the chat."
        override val unexpected = "Something went wrong. Try again."
    }

    override val today = object : TodayStrings {
        override fun greetingLine(greeting: String) =
            "$greeting! Take a minute for yourself 🌸"

        override fun hello(name: String) = if (name.isBlank()) "Hello!" else "Hello, $name!"
        override val aiFootnote = "Based on your data · written by AI"
        override val aiFreePrompt = "Ask anything about your health and how you feel"

        override val cycleCard = "Cycle"
        override val notEnoughForPrediction = "Not enough data for a forecast yet"
        override fun cycleDayOf(day: Int, length: Int) = "Day $day / $length"
        override fun pregnancyWeek(week: Int) = "Week $week"

        override val customise = "Customise home screen"
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
            else -> "A quiet day"
        }
        override val scoreNeedsData =
            "Log two things — water, mood, sleep or steps — to see your day here."

        override val emptySummaryTitle = "Today in short"
        override val emptySummaryBody =
            "Nothing recorded yet. Log how you are today and the daily summary and charts " +
                "will appear here."
        override val startTitle = "Shall we start with today?"
        override val startBody = "Mood, water or food — whichever is easiest to begin with."
        override val startAction = "Log today"
    }

    override val mind = object : MindStrings {
        override val title = "Mind and mood"
        override fun todayIs(date: String) = "Today · $date"

        override val stress = "Stress"
        override val energy = "Energy"
        override val levels = listOf("Very low", "Low", "Moderate", "High", "Very high")
        override val moodNotLogged = "How are you feeling today?"
        override val moodNotLoggedCaption = "Pick one of the faces below."
        override val dialNotSet = "Not set"

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
        override fun practiceMeta(minutes: Int, purpose: String) = "$minutes min · $purpose"
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
        override val title = "Food"
        override val insights = "Insights"
        override val meals = "Meals"
        override val addMeal = "Add a meal"
        override val emptyTitle = "No meals logged today"
        override val emptyBody = "Add your first dish — calories, protein, fat and carbs will add up here."

        override val deleteMealTitle = "Delete this meal?"
        override val deleteMealBody = "It will be taken out of today's totals."
        override val nothingLoggedNote = "Nothing logged yet today. After the first meal, what is running short will show here."

        override val water = "Water"
        override fun waterOfGoal(drunk: String, goal: String) = "$drunk l / $goal l"
        override val addWaterTitle = "Add water"
        override val undo = "Undo"
        override fun waterAdded(ml: Int) = "$ml ml added"
        override fun addWater(ml: Int) = "+$ml ml"

        override val aiAnalysis = "AI analysis"
        override val aiBasis = "Worked out from today's numbers"
        override val scanner = "Food scanner"
        override val scannerHint = "Point the camera — the dish, portion, protein, fat and carbs are estimated"
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
            "Protein, fat and carbs are balanced today. A light dish covers the remaining $kcalLeft kcal."
        override fun shortOf(macro: String) =
            "You're lowest on $macro today. Keep that in mind at your next meal."
        override fun kcal(value: Int) = "$value kcal"
        override fun grams(value: Int) = "$value g"
    }

    override val journey = object : JourneyStrings {
        override val cycleTitle = "My cycle"
        override val info = "Info"
        override val calendar = "Calendar"
        override val noPredictionTitle = "Not enough data for a forecast yet"
        override val noPredictionBody =
            "Once two periods are recorded, the phases of your cycle and an estimate " +
                "for the next one appear here."
        override val markPeriod = "Mark a period"
        override val today = "Today"
        override fun daysToNextPeriod(days: Int) = "Next period in $days ${en(days, "day", "days")}"
        override val symptoms = "Symptoms"
        override val change = "Change"
        override val averageCycle = "Average cycle"
        override val averagePeriod = "Average period"
        override val day = "Day"
        override fun daysValue(days: Int) = "$days ${en(days, "day", "days")}"

        override val calendarTitle = "Calendar"
        override val history = "History"
        override val predictedNote = "Outlined days are a calculation, not a medical guarantee."
        override val markPeriodDay = "Mark a period day"
        override val periodCardTitle = "Period"
        override val periodCardBodyNoForecast = "Mark it if your period started on this day — it shows how long your cycles run."
        override val periodCardBody = "Mark it if your period started on this day — the next cycle's forecast is counted from it."
        override fun periodRunningSince(date: String) = "Your period started on $date and is still running."
        override val periodStartedThisDay = "My period started this day"
        override val periodEndedThisDay = "My period ended this day"
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
            "there are, the more accurate they get."
        override val regularity = "Regularity"
        override val regularSteady = "Regular"
        override val regularVaries = "Varies"
        override val cycleLength = "Cycle length"
        override fun lastNCycles(count: Int) = "last $count ${en(count, "cycle", "cycles")}"
        override val previousCycles = "Previous cycles"
        override val noHistoryYet = "No cycle history yet"
        override val noHistoryYetBody = "Once a second period date is in, the length and the " +
            "regularity are worked out here."
        override fun periodOfDays(days: Int) = "period $days ${en(days, "day", "days")}"
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
            "Mild — it doesn't get in the way",
            "Moderate — distracting at times",
            "Strong — it makes work hard",
            "Very strong — I can't do my usual things",
        )
        override val notePlaceholder = "Add a note…"
        override fun categoryName(category: SymptomCategory) = when (category) {
            SymptomCategory.PAIN -> "Pain"
            SymptomCategory.BLEEDING -> "Bleeding & discharge"
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
        override val weekCaps = "WEEK"
        override fun weekAndDay(week: Int, day: Int) =
            "$week ${en(week, "week", "weeks")} $day ${en(day, "day", "days")}"
        override fun weekOnly(week: Int) = "$week ${en(week, "week", "weeks")}"
        override fun dueOn(date: String, daysLeft: Int) = "Due $date · $daysLeft ${en(daysLeft, "day", "days")} to go"
        override fun dueOnPast(date: String) = "Due $date"
        override val babyDevelopment = "Your baby's development"
        override val todaysSymptoms = "Today's symptoms"
        override val addSymptom = "+ Add"
        override val upcomingAppointments = "Upcoming visits"
        override val all = "All"
        override val noAppointments = "No visits yet"
        override val noAppointmentsBody =
            "Write down the date of a check-up or a test — a reminder will follow."
        override val logToday = "Record how today feels"
        override fun aiAdvice(week: Int) = when {
            week <= 13 ->
                "In the first trimester, take folic acid as your doctor advised. If you feel sick, " +
                    "eat small meals more often. This is general health information."
            week <= 27 ->
                "In the second trimester, food rich in iron and calcium and gentle movement — " +
                    "walking, stretching — may help. This is general health information."
            else ->
                "In the third trimester, resting on your side may be more comfortable. Notice your " +
                    "baby's movements every day and see a doctor if they slow down. This is general health information."
        }
        override val aiBadge = "General advice"

        override val appointmentsTitle = "Visits"
        override val filterUpcoming = "Upcoming"
        override val filterPast = "Past"
        override val filterAll = "All"
        override val listEmpty = "No visits yet — add your first one."
        override val nothingInThisFilter = "No visits in this filter"
        override val appointmentsEmptyBody = "Write down the date of a check-up, a scan or a " +
            "test — the reminder is set from here too."
        override val addAppointment = "Add visit"
        override val nextCaps = "NEXT"
        override val todayCaps = "TODAY"
        override val tomorrowCaps = "TOMORROW"
        override fun inDaysCaps(days: Int) = "IN $days DAYS"
        override val appointmentsNote = "You fill this list in yourself. SADORA doesn't " +
            "prescribe a screening schedule."
        override val appointmentDone = "Done"
        override fun reminderSet(offset: String) = "Reminder $offset"
        override fun reminderOffset(hours: Int) = when (hours) {
            in 0..2 -> "2 hours before"
            in 3..24 -> "a day before"
            else -> "2 days before"
        }
        override val noReminder = "No reminder"
        override val editAppointment = "Edit visit"
        override val appointmentName = "Name"
        override val appointmentNameHint = "Screening scan"
        override val appointmentDate = "Date"
        override val appointmentDateHint = "27.08.2026"
        override val appointmentDateInvalid = "Enter the date as day.month.year, e.g. 27.08.2026"
        override val appointmentTime = "Time (optional)"
        override val appointmentPlace = "Place (optional)"
        override val appointmentPlaceHint = "City maternity clinic"
        override val reminder = "Reminder"
        override val appointmentDateNote = "Write the date as day.month.year, for example 27.08.2026."

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

        override val postpartumTitle = "Recovery"
        override val recoveryWeeks = "wk · recovery"
        override val recoveryNote =
            "Recovery goes differently for every woman. This scale is only a guide."
        override val mood = "Mood"
        override val sleep = "Sleep"
        override val brokenSleep = "Broken sleep"
        override val waterAndFood = "Water and food"
        override val periodReturnTitle = "Has your period come back?"
        override val periodReturnBody =
            "Mark the first period after the birth — it can return even while breastfeeding."
        override val water = "Water"
        override val calories = "Calories"
        override val moodWatch = "Keeping an eye on mood"
        override val moodWatchBody =
            "If low mood or anxiety lasts more than two weeks, see a doctor or a psychologist. " +
                "If you have thoughts of harming yourself, call 103 straight away. " +
                "SADORA doesn't diagnose."
        override val postpartumLibrary = "Knowledge — after birth"
        override val postpartumLibraryBody = "Reading on the postpartum period"

        override val perimenopauseTitle = "Perimenopause"
        override val cycleRegularity = "Cycle regularity"
        override val noData = "no data"
        override fun lastCycles(count: Int) = "last $count ${en(count, "cycle", "cycles")}"
        override val regularityEmpty =
            "Once you start marking periods, your cycle length appears here. No " +
                "forecast is shown at this stage."
        override fun regularitySpread(shortest: Int, longest: Int) =
            "Your cycle has run between $shortest and $longest ${en(longest, "day", "days")} — expected at this " +
                "stage. No forecast is shown."
        override fun regularitySteady(shortest: Int, longest: Int) =
            "Your cycle runs between $shortest and $longest ${en(longest, "day", "days")}. No forecast is " +
                "shown at this stage."
        override val energy = "Energy"
        override val observation = "Observation"
        override val observationBody =
            "See how sleep, mood and symptoms line up."
        override val seeSymptoms = "See symptoms"

        override val menopauseTitle = "Health"
        override val balanceEmpty = "Log water, food, sleep or steps and your score appears here."
        override val scoreNote =
            "Based on sleep, activity, food and mood. This score isn't a medical " +
                "measure."
        override val activity = "Activity"

        override val stageSymptomsTitle = "Symptoms"
        override val noRecordsYet = "Nothing recorded yet"
        override val noRecordsYetBody = "Pick today's symptoms below. After a few days you'll " +
            "see here which of them comes up most often."
        override fun windowDays(days: Int) = "$days ${en(days, "day", "days")}"
        override fun weekNumber(week: Int) = "Week $week"
        override fun recordedOnDays(window: Int, days: Int) = "Recorded on $days of $window ${en(window, "day", "days")}."
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
        override val predictionDisclaimer = "The forecast rests on what you have entered " +
            "and isn't a medical conclusion."
    }

    override val modules = object : ModuleStrings {
        override val sleepTitle = "Sleep"
        override val sleepEmptyTitle = "No sleep data"
        override val sleepEmptyBody =
            "Once a watch or phone syncs, your sleep length and its stages appear here."
        override val sleepWeek = "Length over 7 days"
        override fun average(value: String) = "Average $value"
        override fun daysRecorded(withData: Int, total: Int) = "$withData / $total ${en(total, "day", "days")} recorded"
        override val sleepManual = "Enter sleep by hand"
        override val sleepManualBody = "No watch? Enter last night by hand — Balance and Insights will use it."
        override val sleepHours = "Hours"
        override val sleepMinutesLabel = "Minutes"
        override val sleepSaved = "Sleep saved"
        override val bodySignalsTitle = "Body signals"
        override val bodySignalsNote = "What your device measured. In the luteal phase temperature and pulse often sit a little higher — an observation, not a diagnosis."
        override fun vsLastWeek(delta: String) = "$delta vs last week"
        override val strain = "Strain"
        override val recovery = "Recovery"
        override fun goalFrom(hours: Int) = "from $hours ${en(hours, "hour", "hours")}"
        override val lastNight = "Last night"
        override fun restingPulse(bpm: Int) = "Resting pulse $bpm bpm"
        override val deep = "Deep"
        override val light = "Light"
        override val stages = "Stages"

        override val insightsTitle = "Insights"
        override fun windowDays(days: Int) = "$days ${en(days, "day", "days")}"
        override val windowPremium = "This window opens with Premium"
        override val insightsEmptyTitle = "No insights yet"
        override val loadFailed = "Could not load. Check your connection and try again."
        override val noRecordsInWindow = "Nothing recorded in this window"
        override val noRecordsBody =
            "Record sleep, mood, water or food and the trends are drawn here. We don't " +
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
        override val correlationDisclaimer =
            "A link isn't a cause. It means “often seen together”."

        override val all = "All"
        override val knowledgeTitle = "Knowledge"
        override val search = "Search"
        override val libraryFailed = "The library did not open"
        override val libraryEmpty = "The library is empty for now"
        override val libraryEmptyBody = "New articles will appear here as they are written."
        override val nothingFound = "Nothing found"
        override val nothingFoundBody = "Try another word or a different category."
        override val clearFilters = "Clear filters"
        override fun readMinutes(minutes: Int) = "$minutes MIN"

        override val medsTitle = "My medications"
        override val today = "Today"
        override val history = "History"
        override val nextDose = "Next dose"
        override fun oneTabletWith(note: String) = "1 tablet · $note"
        override val take = "Mark as taken"
        override val later = "Later"
        override val skip = "Skip"
        override val medsEmpty = "No medications added yet"
        override val medsEmptyBody = "Add one and its times and stock will show here."
        override val addMedication = "Add a medication"
        override val medsDisclaimer =
            "SADORA gives no instruction about a missed dose. Follow the medicine's own " +
                "instructions, or your doctor's or pharmacist's advice."
        override fun stockLeft(name: String, days: Int) = "$name stock lasts $days more ${en(days, "day", "days")}"
        override fun stockDays(days: Int) = "Stock $days ${en(days, "day", "days")}"
        override val pending = "Pending"
        override val skipped = "Skipped"

        override val featureCycleMood = "Cycle and mood"
        override val featureFoodDiary = "Food diary"
        override val featureAiChat = "AI chat"
        override val featureScanner = "Food scanner"
        override val featureLongInsights = "30/90-day insights"
        override fun perDayCount(count: Int) = "$count/day"
        override fun perMonthCount(count: Int) = "$count/month"
        override fun soum(amount: String) = "$amount UZS"
        override val premiumTitle = "SADORA Premium"
        override val premiumBody =
            "AI chat, the food scanner and longer insights. Everything on the free plan " +
                "stays."
        override val plansFailed = "Plans did not load"
        override val plansFailedBody = "Check your connection and try again."
        override val paymentAccepted = "Payment received. Premium is open."
        override val paymentPending = "Waiting for payment…"
        override val noPaymentMethod = "No payment method is available yet."
        override val subscribe = "Subscribe"
        override val storePending = "Waiting for the payment to clear. Premium turns on by itself once it does."
        override fun storeRenewalTerms(store: String) =
            "The subscription renews automatically at the end of each period and is charged to your $store account. " +
                "Cancel any time in your $store settings."
        override val nothingToRestore = "No purchases to restore on this account"
        override fun storePlansUnavailable(store: String) = "The plans aren't available in $store yet. Try again a little later."
        override val cancelAnytime = "Cancel at any time"
        override val restorePurchase = "Restore purchase"
        override fun priceFor(sum: String, monthly: Boolean) =
            "$sum UZS / " + (if (monthly) "month" else "year")
        override fun perMonth(sum: String) = "$sum UZS/month"
        override fun saving(percent: Int) = "−$percent%"
        override val payWithPayme = "Pay with Payme"
        override val payWithClick = "Pay with Click"
        override val payWithAppStore = "Through the App Store"
        override val payWithGooglePlay = "Through Google Play"

        override val searchFood = "Search for a dish"
        override val searchTabAll = "All"
        override val searchTabFrequent = "Frequent"
        override val searchTabRecipes = "Recipes"
        override val typeADishName = "Type a dish name"
        override fun nothingFoundFor(query: String) = "Nothing found for “$query”"
        override val catalogueNote = "Uzbek dishes are at the top of the list."
        override val portionLabel = "Portion"
        override val pieces = "pieces"
        override val grams = "grams"
        override fun bowls(count: Int) = "$count ${en(count, "bowl", "bowls")}"
        override val total = "Total"
        override val addToDiary = "Add to the diary"
        override val perPiece = "piece"
        override val perHundredGrams = "100 g"
        override val proteinInitial = "P"
        override val fatInitial = "F"
        override val carbsInitial = "C"

        override val articleFailed = "The article did not open"
        override val articleFailedBody = "It could not be loaded. Check your connection and try again."
        override fun readMinutesCaps(minutes: Int) = "$minutes MIN"
        override val premiumCaps = "PREMIUM"
        override val author = "Author"
        override val reviewed = "✓ Reviewed by a doctor"
        override val restIsPremium = "The rest of the article opens with Premium"

        override fun stepsValue(steps: String) = "$steps steps"
        override fun litresValue(litres: String) = "$litres l"
        override fun kcalValue(kcal: String) = "$kcal kcal"
        override fun outOfFive(value: String) = "$value / 5"
        override fun sleepEnergyFinding(high: String, low: String) =
            "On days with more sleep, energy averaged $high; on days with less, $low."
        override fun activityMoodFinding(high: String, low: String) =
            "On days with more walking, mood averaged $high; on days with less, $low."
        override fun waterHeadacheFinding(high: String, low: String) =
            "On days with more water, a headache was logged $high of the time; with less, $low."
        override fun basedOnDays(days: Int) = "Based on $days ${en(days, "day", "days")} · seen together"
        override fun minutesOnly(minutes: Int) = "$minutes ${en(minutes, "minute", "minutes")}"

        override val addMedTitle = "Add a medication"
        override val medName = "Name"
        override val medNameHint = "Iron"
        override val medDose = "Dose"
        override val medUnit = "Unit"
        override val medTime = "Time"
        override val medTimeInvalid = "Enter the time as 20:00"
        override val addTime = "+ Time"
        override val medDays = "Days"
        override val medFoodRelation = "With food"
        override fun foodRelation(relation: FoodRelation) = when (relation) {
            FoodRelation.ANY -> "Doesn't matter"
            FoodRelation.BEFORE -> "Before"
            FoodRelation.WITH -> "With"
            FoodRelation.AFTER -> "After"
        }
        override fun scheduleKind(kind: ScheduleKind) = when (kind) {
            ScheduleKind.DAILY -> "Every day"
            ScheduleKind.WEEKDAYS -> "On chosen days"
            ScheduleKind.INTERVAL -> "Every few days"
        }

        override fun doseCaption(note: String?, relation: FoodRelation) =
            note?.takeIf { it.isNotBlank() } ?: when (relation) {
                FoodRelation.ANY -> "With or without food"
                FoodRelation.BEFORE -> "Before food"
                FoodRelation.WITH -> "With food"
                FoodRelation.AFTER -> "After food"
            }

        override val medStock = "Stock"
        override val medStockUnit = "units"
        override val medEndDate = "End date"
        override val medNone = "None"
        override val editMedTitle = "Edit medication"
        override val deleteMedication = "Delete medication"
        override val deleteMedTitle = "Delete this medication?"
        override val deleteMedBody = "Reminders will stop. The dose history stays."
        override val removeTime = "Remove time"
        override val medTimesRepeat = "Times must not repeat"

        override val doseHistoryTitle = "Dose history"
        override val takenCount = "Taken"
        override val skippedCount = "Skipped"
        override fun adherenceOver(days: Int) = "$days ${en(days, "day", "days")}"
        override fun lastDays(days: Int) = "Last $days ${en(days, "day", "days")}"
        override val noDoseHistory = "No history yet"
        override val noDoseHistoryBody = "Add a medication and start marking doses — this is " +
            "where you'll see how many were on time."
        override fun doseStatus(status: DoseStatus) = when (status) {
            DoseStatus.TAKEN -> "Taken"
            DoseStatus.PENDING -> "Pending"
            DoseStatus.SKIPPED -> "Skipped"
        }

        override val scannerTitle = "Food scanner"
        override val scannerFrameHint = "Put the dish inside the frame"
        override val scannerLightHint = "Good light makes the estimate more accurate"
        override val scannerGallery = "Gallery"
        override val scannerShutter = "Take a photo"
        override val scannerManual = "By hand"
        override val cameraDenied = "No access to the camera"
        override val cameraDeniedBody = "To photograph a dish right here, turn the camera on in settings. Picking a photo from the gallery or entering it by hand works without it."
        override val cameraOpenSettings = "Open settings"
        override val cameraMissing = "The camera did not open. Pick a photo from the gallery or enter it by hand."
        override val scannerPremium = "The scanner works on a Premium subscription."
        override val scannerUnavailable = "The scanner isn't available"
        override val scannerUnavailableBody = "You can search for the dish and add it by hand."
        override val analysing = "Reading the photo…"
        override val analysingWait = "This usually takes a few seconds"
        override val scanFailed = "The photo could not be read"
        override val scanFailedBody = "Try again, or add the dish by hand."
        override val notFood = "No food in the photo"
        override val scanResult = "Scan result"
        override fun scanConfidence(percent: Int) = "Confidence $percent%"
        override fun portionAndKcal(portion: String, kcal: String) =
            "$portion portion · $kcal kcal · estimate"
        override val nutrients = "Nutrition"
        override val fibre = "Fibre"
        override val sugar = "Sugar"
        override val sodium = "Sodium"
        override val portion = "Portion"
        override val portionHint = "The AI estimate is approximate — correct it yourself"
        override val portionLess = "Smaller portion"
        override val portionMore = "Larger portion"
        override val didYouEatIt = "Did you eat this?"
        override val yesIAte = "Yes, I ate it"
        override val planningToEat = "I'll eat it later"

        override val journalTitle = "Journal and exercises"
        override val journalPrivate = "ONLY YOU SEE THIS"
        override val journalLabel = "Journal"
        override val journalPrompt = "How are you feeling today?"
        override val journalEmpty = "The journal is still empty"
        override val journalEmptyBody = "Write your first entry. Nobody but you will see it."
        override val journalDeleteTitle = "Delete this entry?"
        override val journalDeleteBody = "The entry will be deleted for good."
        override val journalDeleteAction = "Delete this entry"

        override val sourcesTitle = "Data sources"
        override fun sourcesConnected(count: Int) = "$count ${en(count, "source", "sources")} connected"
        override fun lastSample(ago: String) = "Last data $ago"
        override val noSampleYet = "No data yet"
        override val sourcesEmpty = "Nothing connected"
        override val sourcesEmptyBody = "Once you allow Apple Health or Health Connect, the " +
            "data that arrives and its time will show up here."
        override val sourcesNote = "Every figure carries its source and a timestamp. When the " +
            "same figure arrives from more than one source, each shows where it came from."
        override val connected = "Connected"
        override val notConnected = "Not connected"
        override fun samples(count: Int) = "$count ${en(count, "entry", "entries")}"
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
            HealthMetric.SLEEP_LIGHT -> "Light sleep"
            HealthMetric.SLEEP_AWAKE -> "Awake"
            HealthMetric.SLEEP_PERFORMANCE -> "Sleep performance"
            HealthMetric.SLEEP_EFFICIENCY -> "Sleep efficiency"
            HealthMetric.RECOVERY -> "Recovery"
            HealthMetric.STRAIN -> "Strain"
            HealthMetric.SPO2 -> "SpO₂"
            HealthMetric.SKIN_TEMPERATURE -> "Skin temperature"
            HealthMetric.WEIGHT -> "Weight"
            HealthMetric.TOTAL_ENERGY -> "Total energy"
            HealthMetric.BASAL_METABOLIC_RATE -> "Basal metabolic rate"
            HealthMetric.FLOORS -> "Floors"
            HealthMetric.ELEVATION_GAINED -> "Elevation gained"
            HealthMetric.EXERCISE_MINUTES -> "Exercise"
            HealthMetric.MINDFULNESS_MINUTES -> "Mindfulness"
            HealthMetric.WHEELCHAIR_PUSHES -> "Wheelchair pushes"
            HealthMetric.SPEED -> "Speed"
            HealthMetric.POWER -> "Power"
            HealthMetric.STEPS_CADENCE -> "Step cadence"
            HealthMetric.CYCLING_CADENCE -> "Cycling cadence"
            HealthMetric.VO2_MAX -> "VO₂max"
            HealthMetric.BLOOD_GLUCOSE -> "Blood glucose"
            HealthMetric.BLOOD_PRESSURE_SYSTOLIC -> "Systolic pressure"
            HealthMetric.BLOOD_PRESSURE_DIASTOLIC -> "Diastolic pressure"
            HealthMetric.HEIGHT -> "Height"
            HealthMetric.BODY_FAT -> "Body fat"
            HealthMetric.LEAN_BODY_MASS -> "Lean body mass"
            HealthMetric.BODY_WATER_MASS -> "Body water"
            HealthMetric.BONE_MASS -> "Bone mass"
            HealthMetric.HYDRATION -> "Hydration"
            HealthMetric.DIETARY_ENERGY -> "Dietary energy"
            HealthMetric.PROTEIN -> "Protein"
            HealthMetric.CARBOHYDRATES -> "Carbohydrates"
            HealthMetric.FAT -> "Fat"
            HealthMetric.OVULATION_TEST -> "Ovulation test"
            HealthMetric.CERVICAL_MUCUS -> "Cervical mucus"
            HealthMetric.INTERMENSTRUAL_BLEEDING -> "Spotting"
            HealthMetric.SEXUAL_ACTIVITY -> "Sexual activity"
        }

        override val balanceTitle = "Balance"
        override val fourDirections = "Four directions"
        override val balanceDisclaimer =
            "The balance score is measured against your own goals. It isn't a medical " +
                "measure."
        override val balanced =
            "All four directions are in balance today. Food isn't a debt to be burned off."
        override fun someRoomIn(direction: String) =
            "The day is going well. You could add a little to “$direction” — give it a " +
                "thought if you like."
        override fun fallingBehind(direction: String) =
            "“$direction” is behind today. The day isn't over — no rush."
        override val food = "Food"
        override val water = "Water"
        override val activity = "Activity"
        override val sleep = "Sleep"
        override fun ofKcal(eaten: String, goal: String) = "$eaten / $goal kcal"
        override fun ofLitres(drunk: String, goal: String) = "$drunk / $goal l"
        override fun ofSteps(walked: String, goal: String) = "$walked / $goal steps"
        override fun ofSleep(slept: String) = "$slept / 8 h"
        override val balanceCapsWord = "BALANCE"
        override fun articleKind(kind: ArticleKind) = when (kind) {
            ArticleKind.ARTICLE -> "ARTICLE"
            ArticleKind.COURSE -> "COURSE"
            ArticleKind.VIDEO -> "VIDEO"
        }
        override val featureCaps = "FEATURE"
        override val freeCaps = "FREE"
        override val premiumCapsBadge = "PREMIUM"
        override val journalCardTitle = "Journal"
        override val moodLabel = "Mood"
        override val allDoneToday = "Everything for today is done 🌸"
    }

    override val pregnancyWeeks: PregnancyWeekStrings = PregnancyWeeksEn
    override val tools: StageToolsStrings = StageToolsEn
    override val partner: PartnerStrings = PartnerEn
    override val badges: BadgeStrings = BadgesEn
    override val prescriptions: PrescriptionStrings = PrescriptionStringsEn
    override val pet: PetStrings = PetEn
    override val frames: FrameStrings = FramesEn

    override val rewards = object : RewardStrings {
        override val coinName = "Gul"
        override fun coins(amount: String) = "$amount Gul"
        override fun coinsGained(amount: String) = "+$amount Gul"

        override fun streakDays(days: Int) = "$days ${en(days, "day", "days")} running"
        override val streakStarted = "Streak started"
        override val streakSubtitle = "You came back today 🌸"
        override fun milestoneReached(days: Int) = "$days ${en(days, "day", "days")}! 🎉"
        override fun daysToMilestone(days: Int, milestone: Int) =
            "$days more ${en(days, "day", "days")} — the $milestone-day mark"
        override val streakBeyondMilestones = "Every milestone is behind you"

        override val walletTitle = "Gul wallet"
        override val balance = "Balance"
        override val earned = "Earned"
        override val spent = "Spent"
        override val currentStreak = "Current streak"
        override val longestStreak = "Longest"
        override fun days(count: Int) = "$count ${en(count, "day", "days")}"
        override val history = "Activity"
        override val historyEmpty = "Nothing yet. Use the app and Gul will add up."
        override val howToEarn = "How Gul is earned"
        override fun perDay(times: Int) = "up to $times ${en(times, "time", "times")} a day"
        override fun earnReason(reason: String) = when (reason) {
            CoinReasons.DAILY_OPEN -> "First open of the day"
            CoinReasons.STREAK_MILESTONE -> "Streak milestone"
            CoinReasons.CHECK_IN -> "Log your mood"
            CoinReasons.WATER_GOAL -> "Reach the water goal"
            CoinReasons.DOSE_TAKEN -> "Confirm a dose"
            CoinReasons.MEAL_LOGGED -> "Log a meal"
            CoinReasons.JOURNAL_ENTRY -> "Write in the journal"
            CoinReasons.PRACTICE -> "Breathing or meditation"
            CoinReasons.ARTICLE_READ -> "Read an article"
            CoinReasons.REFERRAL_JOINED -> "A friend joined"
            CoinReasons.REFERRAL_WELCOME -> "Arriving with an invite code"
            CoinReasons.REDEMPTION -> "Shop purchase"
            CoinReasons.ADMIN_ADJUSTMENT -> "Manual adjustment"
            CoinReasons.PARTNER_LINKED -> "Yaqinim connected"

            CoinReasons.BADGE_EARNED -> "New badge"
            else -> reason
        }
        override val openShop = "Gul shop"
        override val inviteFriends = "Invite"

        override val referralTitle = "Invite your friends"
        override val referralSubtitle =
            "For every friend who joins through your link, you both get Gul."
        override val yourCode = "Your code"
        override val copyCode = "Copy"
        override val codeCopied = "Code copied"
        override val shareLink = "Share link"
        override fun shareMessage(link: String) =
            "SADORA — a women's health app. Join through my link: $link"
        override fun invitedCount(count: Int) = "$count ${en(count, "friend", "friends")} joined"
        override fun referralEarned(amount: String) = "$amount Gul from invites"
        override fun rewardPerJoin(amount: String) = "$amount Gul per friend"
        override fun welcomeReward(amount: String) = "Your friend starts with $amount Gul"
        override val referralHowTitle = "How it works"
        override val referralSteps = listOf(
            "Send the link to a friend",
            "She installs the app and signs up",
            "Gul reaches you both",
        )
        override val referralFairUse =
            "A code counts once, and only for a new account. Your own code never pays you."
    }

    override val shop = object : ShopStrings {
        override val title = "Gul shop"
        override val subtitle = "Turn the Gul you've earned into Premium, vitamins and devices"
        override fun tab(kind: ShopKind) = when (kind) {
            ShopKind.PREMIUM -> "Premium"
            ShopKind.VITAMIN -> "Vitamins"
            ShopKind.DEVICE -> "Devices"
        }
        override val empty = "Nothing here yet"
        override val loading = "Loading the shop…"

        override fun discount(percent: Int) = "$percent% off"
        override fun priceWas(price: String) = price
        override fun priceNow(price: String) = price
        override fun saving(amount: String) = "saves $amount"
        override fun premiumDays(days: Int) = "$days ${en(days, "day", "days")} of Premium"
        override val outOfStock = "Out of stock"
        override fun stockLeft(count: Int) = "$count left"
        override val notEnough = "Not enough Gul"
        override fun shortBy(amount: String) = "$amount Gul short"

        override val redeem = "Redeem"
        override val redeeming = "Redeeming…"
        override fun confirmTitle(product: String) = product
        override fun confirmBody(cost: String) =
            "$cost Gul will be taken and you'll get a discount code."
        override val confirmPremiumBody = "The Gul is taken and Premium opens straight away."
        override val cancel = "Cancel"

        override val issuedTitle = "Your code is ready"
        override val issuedPremiumTitle = "Premium unlocked 🎉"
        override val issuedBody = "Show the code at the counter — the discount is applied there."
        override val issuedPremiumBody = "Your subscription is extended. Everything is open now."
        override val yourCode = "Discount code"
        override val copyCode = "Copy"
        override val codeCopied = "Code copied"
        override fun validUntil(date: String) = "Valid until $date"
        override val myCodes = "My codes"
        override val myCodesEmpty = "No codes yet"
        override val statusIssued = "Active"
        override val statusUsed = "Used"
        override val statusExpired = "Expired"
        override val statusCancelled = "Cancelled"

        override val partnerNote =
            "Vitamins and devices are sold by partners. SADORA issues the discount code; it does " +
                "not sell or deliver the product. Talk to a doctor or pharmacist before taking a " +
                "supplement."
    }

    override val homeLayout = object : HomeLayoutStrings {
        override val title = "Home screen"
        override val subtitle = "Choose which blocks appear, and in what order."
        override val visible = "Shown"
        override val hidden = "Hidden"
        override val moveUp = "Up"
        override val moveDown = "Down"
        override val reset = "Restore default order"
        override val alwaysOn = "Always shown"
        override fun widget(key: String) = when (key) {
            HomeWidgets.AI -> "AI summary"
            HomeWidgets.SCORE -> "Health score"
            HomeWidgets.STREAK -> "Streak and Gul"
            HomeWidgets.STAGE -> "Cycle / stage"
            HomeWidgets.PLAN -> "Today's plan"
            HomeWidgets.SLEEP -> "Sleep"
            HomeWidgets.MEDICATIONS -> "Medications"
            HomeWidgets.INSIGHTS -> "Insights"
            HomeWidgets.KNOWLEDGE -> "Knowledge"
            HomeWidgets.QUICK_ACTIONS -> "Quick actions"
            HomeWidgets.SUMMARY -> "Day summary"
            else -> key
        }
        override fun widgetNote(key: String) = when (key) {
            HomeWidgets.AI -> "A short read on the day"
            HomeWidgets.SCORE -> "Sleep, mood, water and steps"
            HomeWidgets.STREAK -> "Days running and your balance"
            HomeWidgets.STAGE -> "Cycle day or week"
            HomeWidgets.PLAN -> "Doses and water"
            HomeWidgets.SLEEP -> "Last night"
            HomeWidgets.MEDICATIONS -> "Today's doses"
            HomeWidgets.INSIGHTS -> "The latest finding"
            HomeWidgets.KNOWLEDGE -> "An article for you"
            HomeWidgets.QUICK_ACTIONS -> "The four shortcuts"
            HomeWidgets.SUMMARY -> "The day in numbers"
            else -> ""
        }
    }

    override val share = object : ShareStrings {
        override val title = "Show my doctor"
        override val subtitle = "A QR code opens your records on the doctor's screen"
        override val intro = "At your visit, show the QR code on your phone. The doctor scans it with a camera and sees " +
            "your cycle, symptoms, mood, medications, visits and device readings on one page. The link is " +
            "temporary and disappears after the time you choose."
        override val create = "Create QR code"
        override val creating = "Preparing…"
        override val regenerate = "Create new code"
        override val revoke = "Revoke link"
        override val revoked = "Link revoked"
        override val copyLink = "Copy link"
        override val linkCopied = "Link copied"
        override val shareLink = "Send link"
        override fun shareMessage(link: String) = "SADORA — my health records (temporary link): $link"
        override val showToDoctor = "Show this code to your doctor"
        override val validFor = "Valid for"
        override fun hours(count: Int) = "$count h"
        override fun days(count: Int) = "$count ${en(count, "day", "days")}"
        override fun expiresAt(at: String) = "Valid until $at"
        override val expired = "Expired"
        override fun viewedTimes(count: Int) = "Opened $count ${en(count, "time", "times")}"
        override val neverViewed = "Not opened yet"
        override fun lastViewed(ago: String) = "Last opened $ago"
        override val includesTitle = "What the page shows"
        override val includes = listOf(
            "Age, height, weight and life stage",
            "Cycle history, last period and the forecast",
            "Symptoms, mood and energy over 90 days",
            "Medications, schedule and adherence",
            "Appointments and tests",
            "Sleep, heart rate, HRV and other device readings",
        )
        override val excludesTitle = "What it never shows"
        override val excludes = listOf(
            "The text of your journal entries",
            "Private notes on pregnancy check-ins",
            "Chat conversations",
        )
        override val privacyNote = "The link carries no name or phone number — only a random code. Make a new code and the old one stops at once."
        override val offline = "Creating a QR code needs an internet connection"
        override val failed = "Could not create the link. Try again."
    }

    override val premium = object : PremiumStrings {
        override val tab = "Premium"
        override val title = "SADORA Premium"
        override val activeTitle = "Premium is active"
        override val activeBody = "Everything is open: AI chat, the food scanner, long-range insights and the whole library."
        override val inactiveTitle = "To understand more"
        override val inactiveBody = "Nothing is taken from the free plan. Premium adds deeper insights and AI chat."
        override val benefitsTitle = "What Premium gives you"
        override val benefitAiTitle = "AI chat"
        override val benefitAiBody = "Up to 20 questions a day, answered with your cycle, sleep and food in mind."
        override val benefitScannerTitle = "Food scanner"
        override val benefitScannerBody = "30 photos a month: snap a dish for an estimate of its calories and nutrients, which you can correct."
        override val benefitInsightsTitle = "Insights history"
        override val benefitInsightsBody = "30- and 90-day windows and observations: see what tends to come together."
        override val benefitLibraryTitle = "The whole Knowledge library"
        override val benefitLibraryBody = "Every article our specialists prepared, matched to your stage."
        override val benefitDevicesTitle = "Device insights"
        override val benefitDevicesBody = "WHOOP and other device readings set against your cycle phases."
        override val compareTitle = "Free and Premium"
        override val seePlans = "See plans"
        override val manage = "Manage subscription"
        override fun buyWithCoins(coinName: String) = "Get it with $coinName"
        override fun fromPerMonth(sum: String) = "From $sum UZS a month"
        override fun coinsFor(cost: String, days: Int, balance: String) = "$cost = $days ${en(days, "day", "days")} of Premium · you have $balance"
        override val faqTitle = "Common questions"
        override fun faq(appStore: Boolean) = listOf(
            "What stays free?" to "Everything: cycle, mood, food diary, medications, visits and 7-day insights. Premium only adds.",
            "Can I cancel any time?" to "Yes. Premium stays open until the paid period ends, then you return to the free plan — your data stays.",
            "How do I pay?" to
                if (appStore) "Through the App Store, with your Apple account. Once the payment is confirmed, Premium opens straight away."
                else "Through Google Play, or with Payme or Click. Once the payment is confirmed, Premium opens straight away.",
            "Can I get it with Gul?" to "Yes — the shop offers 7 and 30 days of Premium for Gul. Gul is earned by opening the app and logging.",
        )
        override val freeStays = "Everything on the free plan stays"
    }

    override val devices = object : DeviceStrings {
        override val title = "Devices"
        override val subtitle = "Connect a watch or band — sleep, heart rate and recovery arrive on their own"
        override val connectedSection = "Connected"
        override val availableSection = "Available to connect"
        override val plannedSection = "Planned"
        override fun provider(provider: HealthProvider) = when (provider) {
            HealthProvider.APPLE_HEALTH -> "Apple Health"
            HealthProvider.HEALTH_CONNECT -> "Health Connect"
            HealthProvider.OURA -> "Oura"
            HealthProvider.GARMIN -> "Garmin"
            HealthProvider.WHOOP -> "WHOOP"
            HealthProvider.FITBIT -> "Fitbit"
            HealthProvider.SAMSUNG_HEALTH -> "Samsung Health"
            HealthProvider.MANUAL -> "Entered by hand"
        }
        override fun providerTagline(provider: HealthProvider) = when (provider) {
            HealthProvider.WHOOP -> "Recovery, strain, HRV and skin temperature"
            HealthProvider.APPLE_HEALTH -> "iPhone and Apple Watch"
            HealthProvider.HEALTH_CONNECT -> "Android watches and bands"
            HealthProvider.OURA -> "A ring: sleep and recovery"
            HealthProvider.GARMIN -> "Sports watches"
            HealthProvider.FITBIT -> "Bands and watches"
            HealthProvider.SAMSUNG_HEALTH -> "Galaxy Watch"
            HealthProvider.MANUAL -> "Readings you typed yourself"
        }
        override val connect = "Connect"
        override val connecting = "Connecting…"
        override val disconnect = "Disconnect"
        override val disconnectConfirmTitle = "Disconnect this device?"
        override val disconnectConfirmBody = "New data stops arriving. What has already arrived is kept."
        override val syncNow = "Sync now"
        override val syncing = "Syncing…"
        override val synced = "Synced"
        override fun lastSync(ago: String) = "Last sync: $ago"
        override val neverSynced = "Not synced yet"
        override val statusActive = "Active"
        override val statusExpired = "Needs reconnecting"
        override val statusError = "Error"
        override val reconnect = "Reconnect"
        override fun unavailable(reason: String) = when (reason) {
            "not_configured" -> "Not available yet"
            "ios_only" -> "iPhone only"
            "android_only" -> "Android only"
            "unsupported" -> "Not supported on this phone"
            else -> "Coming soon"
        }
        override val givesTitle = "What it brings"
        override val usedInTitle = "Where it's used"
        override fun usedIn(provider: HealthProvider) = when (provider) {
            HealthProvider.WHOOP -> listOf("Sleep screen", "Today — health score", "Balance", "Cycle — body signals", "Doctor page")
            HealthProvider.APPLE_HEALTH, HealthProvider.HEALTH_CONNECT ->
                listOf("Sleep screen", "Today — health score", "Balance", "Cycle — period days and body temperature", "Insights")
            else -> listOf("Sleep screen", "Today — health score", "Balance", "Insights")
        }
        override val openBrowserNote = "WHOOP's page opens in the browser. Once you allow access you return to the app — the first pull covers 30 days and takes a few minutes."
        override val returnedOk = "WHOOP connected — data is on its way"
        override val returnedError = "WHOOP did not connect. Try again."
        override val noStepsNote = "WHOOP doesn't count steps — strain is shown instead."
        override val manualTitle = "No watch?"
        override val manualBody = "Enter sleep by hand on the Sleep screen — Balance and Insights will use it."
        override val note = "SADORA takes only the readings listed from a device and never sells them. You can disconnect at any time."
        override fun onDeviceNote(provider: HealthProvider) = when (provider) {
            HealthProvider.APPLE_HEALTH ->
                "The Health sheet opens: choose what SADORA may read. Data refreshes when you open the app — the first read brings 30 days of readings and 6 months of period days."
            else ->
                "Health Connect opens: allow access. Whatever Samsung Health, Mi Fitness, Zepp and other apps write to Health Connect comes through. It refreshes when you open the app."
        }
        override val installHealthConnect = "Install Health Connect"
        override val healthConnectMissing = "Health Connect is missing or out of date on this phone. Install it from Google Play, then come back here."
        override val samsungSyncHint = "Let Samsung Health write to Health Connect: on the page that opens, turn on “Allow all”."
        override val openSamsungHealth = "Samsung permissions"
        override fun deviceConnected(name: String) = "$name connected — data is on its way"
        override val accessDenied = "Access wasn't given — nothing was read"
        override fun periodsImported(count: Int) = "$count ${en(count, "period", "periods")} added"
        override val appleHealthManage = "Change what is read in the Health app: Profile → Apps → SADORA."

        override val askTitle = "Do you wear a smart watch or band?"
        override val askBody =
            "If you do, connect it once — sleep, heart rate and steps then arrive on their own every day, with nothing to type."
        override val askReadings = listOf("Sleep", "Heart rate", "Steps")
        override val askBrands = "Apple Watch, Galaxy Watch, Mi Band, Garmin, Fitbit, WHOOP, Oura and more"
        override val askYes = "Yes — let's connect it"
        override val askNo = "No, I don't"
        override val askLater = "Remind me later"

    }
}

/**
 * English plural form for [n]: "1 day", "2 days", "0 days". The number is left to the
 * caller, so `"$n ${en(n, "day", "days")}"` — a fixed "days" read "1 days".
 */
internal fun en(n: Int, one: String, other: String): String = if (n == 1) one else other
