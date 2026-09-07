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
 * O'zbekcha — the language the app was written in, and the reference the other two are
 * translated from. When a wording question comes up, this file is the answer.
 */
object StringsUz : Strings {
    override val languageName = object : LanguageNames {}

    override val tabs = object : TabStrings {
        override val today = "Bugun"
        override val mind = "Ong"
        override val nutrition = "Ovqat"
        override val profile = "Profil"
        override fun journey(stage: LifeStage) = when (stage) {
            LifeStage.Cycle -> "Sikl"
            LifeStage.TryingToConceive -> "Reja"
            LifeStage.Pregnancy -> "Homilador"
            LifeStage.Postpartum -> "Tiklanish"
            LifeStage.Perimenopause -> "Bosqich"
            LifeStage.Menopause -> "Salomatlik"
        }
    }

    override val stages = object : LifeStageStrings {
        override fun title(stage: LifeStage) = when (stage) {
            LifeStage.Cycle -> "Sikl kuzatuvi"
            LifeStage.TryingToConceive -> "Homiladorlikni rejalashtirish"
            LifeStage.Pregnancy -> "Homiladorlik"
            LifeStage.Postpartum -> "Tug'ruqdan keyin"
            LifeStage.Perimenopause -> "Perimenopauza"
            LifeStage.Menopause -> "Menopauza"
        }

        override fun subtitle(stage: LifeStage) = when (stage) {
            LifeStage.Cycle -> "Hayz, ovulyatsiya, simptomlar"
            LifeStage.TryingToConceive -> "Unumdor kunlar, tayyorgarlik"
            LifeStage.Pregnancy -> "Hafta, o'sish, tadbirlar"
            LifeStage.Postpartum -> "Tiklanish, uyqu, kayfiyat"
            LifeStage.Perimenopause -> "Muntazamlik, simptomlar"
            LifeStage.Menopause -> "Salomatlik va kayfiyat"
        }
    }

    override val welcome = object : WelcomeStrings {
        override val title = "SADORA'ga xush kelibsiz"
        override val subtitle = "Salomatlik, sikl, ovqatlanish va kayfiyat uchun shaxsiy yordamchingiz."
        override val featureCycle = "Sikl"
        override val featureMood = "Kayfiyat"
        override val featureNutrition = "Ovqatlanish"
        override val featureMeds = "Vitamin va dorilar"
        override val featureAi = "SADORA AI"
        override val featureInsights = "Tahlil va tavsiyalar"
        override val privacyPromise = "Ma'lumotlaringiz sizniki — istalgan vaqtda o'chirasiz"
        override val start = "Boshlash"
        override val haveAccount = "Hisobim bor →"
        override val signIn = "Kirish"
    }

    override val onboarding = object : OnboardingStrings {
        override val languageTitle = "Tilni tanlang"
        override val languageSubtitle = "Keyin sozlamalardan o'zgartira olasiz."
        override val continueLabel = "Davom etish"
    }

    override val profile = object : ProfileStrings {
        override val title = "Profil"
        override val unnamed = "Foydalanuvchi"

        override val sleep = "Uyqu"
        override val medications = "Dorilar"
        override val secretChat = "Maxfiy chat"
        override val insights = "Tahlillar"
        override val knowledge = "Bilim"

        override val personalDetails = "Shaxsiy ma'lumotlar"
        override val goals = "Maqsadlar"
        override val lifeStage = "Hayot bosqichi"
        override val connectedDevices = "Ulangan qurilmalar"
        override val notifications = "Bildirishnomalar"
        override val privacyAndSecurity = "Maxfiylik va xavfsizlik"

        override val language = "Til"
        override val theme = "Mavzu"
        override val themeDark = "Qorong'i"
        override val themeLight = "Yorug'"
        override val about = "SADORA haqida"

        override val signOut = "Chiqish"
        override val signingOut = "Chiqilmoqda…"

        override val premiumBadge = "SADORA PREMIUM"
        override val premiumActive = "Faol"
        override val premiumYearly = "Yillik obuna"
        override fun premiumUntil(date: String) = "$date-gacha"
        override fun premiumRenewsOn(date: String) = "$date-da yangilanadi"
        override val premiumNoExpiry = "Muddatsiz"
        override val premiumFeatureAi = "AI chat"
        override val premiumFeatureScanner = "Ovqat skaneri"
        override val premiumFeatureInsights = "Kengaytirilgan tahlil"
        override val upgradeTitle = "SADORA Premium"
        override val upgradeSubtitle = "AI suhbat, ovqat skaneri va kengaytirilgan tahlillar"
    }

    override val settings = object : SettingsStrings {
        override val aboutTitle = "SADORA haqida"
        override fun version(number: String) = "Versiya $number"
        override val languageTitle = "Til"
        override val languageNote = "Ilova tili darhol o'zgaradi. AI javoblari hozircha " +
            "faqat o'zbekcha."
        override val languageSaveFailed = "Til saqlanmadi — keyinroq qayta urinib ko'ring."
        override val personalTitle = "Shaxsiy ma'lumotlar"
        override val name = "Ism"
        override val birthDate = "Tug'ilgan sana"
        override val height = "Bo'y"
        override val weight = "Vazn"
        override val centimetres = "sm"
        override val kilograms = "kg"
        override val weightNote = "Vazn ixtiyoriy va hech qachon boshqalarga ko'rsatilmaydi."

        override val goalsTitle = "Maqsadlar"
        override fun goalsChosen(count: Int) = "$count tanlandi"

        override val lifeStageTitle = "Hayot bosqichi"
        override val lifeStageNote = "Bosqichni o'zgartirsangiz \"Yo'l\" bo'limi va tegishli " +
            "ekranlar butunlay yangilanadi. Yozilgan ma'lumotlaringiz saqlanadi."

        override val notificationsTitle = "Bildirishnomalar"
        override val medReminder = "Dori eslatmalari"
        override val medReminderNote = "Qabul vaqtidan 10 daqiqa oldin"
        override val cycleReminder = "Hayz eslatmasi"
        override val cycleReminderNote = "Taxminiy sana yaqinlashganda"
        override val waterReminder = "Suv eslatmasi"
        override val waterReminderNote = "Kuniga uch marta"
        override val aiSummary = "Kunlik AI xulosasi"
        override val aiSummaryNote = "Ertalab 08:00"

        override val privacyTitle = "Maxfiylik va xavfsizlik"
        override val consentHealth = "Salomatlik ma'lumotlarini saqlash"
        override val consentHealthNote = "Ilova ishlashi uchun zarur. Ma'lumot shifrlangan holda saqlanadi."
        override val consentAi = "AI xulosalar uchun ishlatish"
        override val consentAiNote = "Shaxsiy xulosa va tavsiyalar tayyorlash uchun."
        override val consentAnalytics = "Anonim analitika"
        override val consentAnalyticsNote = "Ixtiyoriy. Ilovani yaxshilashga yordam beradi."
        override val saveConsents = "Roziliklarni saqlash"
        override val legalDocuments = "Huquqiy hujjatlar"
        override val terms = "Foydalanish shartlari"
        override val privacyPolicy = "Maxfiylik siyosati"
        override val yourData = "Ma'lumotlaringiz"
        override val exportData = "Ma'lumotlarni eksport qilish"
        override val deleteAccount = "Hisobni o'chirish"
        override val deleteAccountConfirm = "Hisobni o'chirish?"
        override val deleteAccountBody = "Ma'lumotlaringiz butunlay o'chiriladi. " +
            "Avval eksport qilishni tavsiya qilamiz."

        override val medicalDisclaimer = "SADORA tibbiy tashxis qo'ymaydi. Shubha tug'ilsa " +
            "shifokorga murojaat qiling."
    }

    override val common = object : CommonStrings {
        override fun greeting(hour: Int) = when (hour) {
            in 5..11 -> "Xayrli tong"
            in 12..17 -> "Xayrli kun"
            else -> "Xayrli kech"
        }

        override fun mood(mood: Mood) = when (mood) {
            Mood.Bad -> "Og'ir"
            Mood.Low -> "So'lg'in"
            Mood.Ok -> "O'rtacha"
            Mood.Good -> "Xotirjam"
            Mood.Great -> "Ajoyib"
        }

        override fun moodCaption(mood: Mood) = when (mood) {
            Mood.Bad -> "Bugun o'zingizga mehribon bo'ling."
            Mood.Low -> "Sekinroq kun — bu ham normal."
            Mood.Ok -> "Muvozanat uchun oddiy kun."
            Mood.Good -> "Muvozanat uchun yaxshi kun."
            Mood.Great -> "Energiyangiz yuqori — foydalaning!"
        }

        override fun phase(phase: CyclePhase) = when (phase) {
            CyclePhase.Period -> "Hayz"
            CyclePhase.Follicular -> "Follikulyar"
            CyclePhase.Fertile -> "Ovulyatsiya davri"
            CyclePhase.Luteal -> "Lyuteal"
        }

        override fun phaseFertility(phase: CyclePhase) = when (phase) {
            CyclePhase.Period -> "Homiladorlik ehtimoli past"
            CyclePhase.Follicular -> "Homiladorlik ehtimoli o'sib boradi"
            CyclePhase.Fertile -> "Homiladorlik ehtimoli yuqori"
            CyclePhase.Luteal -> "Homiladorlik ehtimoli pasayadi"
        }

        override fun phaseEnergy(phase: CyclePhase) = when (phase) {
            CyclePhase.Period -> "Tanangiz dam olmoqda — o'zingizga yumshoq bo'ling."
            CyclePhase.Follicular -> "Energiya ortib boradi — yangi ishlar uchun qulay davr."
            CyclePhase.Fertile -> "Energiya cho'qqisida — faol kunlar uchun foydalaning."
            CyclePhase.Luteal -> "Energiya sekin pasayadi — dam olishga joy qoldiring."
        }

        override fun goal(goal: Goal) = when (goal) {
            Goal.UnderstandCycle -> "Siklni tushunish"
            Goal.SleepBetter -> "Yaxshi uxlash"
            Goal.MoreEnergy -> "Energiyani oshirish"
            Goal.LessStress -> "Stressni kamaytirish"
            Goal.EatBalanced -> "Muvozanatli ovqatlanish"
            Goal.DrinkWater -> "Ko'proq suv ichish"
            Goal.BeActive -> "Faolroq bo'lish"
            Goal.RememberMeds -> "Dorilarni eslab qolish"
        }

        override fun conceptionWindow(window: ConceptionWindow) = when (window) {
            ConceptionWindow.JustStarted -> "Endi boshladim"
            ConceptionWindow.UnderThreeMonths -> "3 oygacha"
            ConceptionWindow.ThreeToSix -> "3–6 oy"
            ConceptionWindow.SixToTwelve -> "6–12 oy"
            ConceptionWindow.OverAYear -> "Bir yildan ko'p"
        }

        override val saving = "Saqlanmoqda…"
        override val yes = "Ha"
        override val no = "Yo'q"
        override val back = "Orqaga"
        override val loading = "Yuklanmoqda…"
        override val retry = "Qayta urinish"
        override val optional = "Ixtiyoriy"
        override val save = "Saqlash"
        override val cancel = "Bekor"
        override val delete = "O'chirish"
        override val close = "Yopish"
        override val add = "Qo'shish"
        override val edit = "Tahrirlash"
        override val done = "Tayyor"

        override fun hoursMinutes(hours: Int, minutes: Int) = "${hours}s ${minutes}d"
        override val litres = "l"
        override val millilitres = "ml"
        override val kcal = "kkal"
        override val steps = "qadam"
        override val minutesShort = "daq"
        override fun days(count: Int) = "$count kun"
    }

    override val dates = object : DateStrings {
        override val months = listOf(
            "Yanvar", "Fevral", "Mart", "Aprel", "May", "Iyun",
            "Iyul", "Avgust", "Sentabr", "Oktabr", "Noyabr", "Dekabr",
        )

        override val weekdays = listOf(
            "dushanba", "seshanba", "chorshanba", "payshanba", "juma", "shanba", "yakshanba",
        )

        override val weekdaysShort = listOf("Du", "Se", "Ch", "Pa", "Ju", "Sh", "Ya")

        override fun dayMonth(date: LocalDate) =
            "${date.day}-${months[date.month.ordinal].lowercase()}"

        override val today = "Bugun"
        override val yesterday = "Kecha"
        override val tomorrow = "Ertaga"

        override val justNow = "hozir"
        override fun minutesAgo(minutes: Int) = "$minutes daqiqa oldin"
        override fun hoursAgo(hours: Int) = "$hours soat oldin"
        override fun daysAgo(days: Int) = "$days kun oldin"
    }

    override val today = object : TodayStrings {
        override fun greetingLine(greeting: String) =
            "$greeting — bugun o'zingizga g'amxo'rlik qilish uchun ajoyib kun 🌸"

        override fun hello(name: String) = if (name.isBlank()) "Salom!" else "Salom, $name!"
        override val aiFootnote = "Ma'lumotlaringiz asosida · AI tomonidan yaratilgan"
        override val aiFreePrompt = "Salomatlik va kayfiyat haqida istalgan savolingizni bering"

        override val cycleCard = "Sikl"
        override val notEnoughForPrediction = "Prognoz uchun ma'lumot yetarli emas"
        override fun cycleDayOf(day: Int, length: Int) = "Kun $day / $length"
        override fun pregnancyWeek(week: Int) = "$week-hafta"

        override val quickActions = "Tezkor amallar"
        override val journal = "Jurnal"
        override val meditation = "Meditatsiya"
        override val breathing = "Nafas"
        override val reminders = "Eslatmalar"

        override val summary = "Bugungi xulosa"
        override fun phaseSentence(day: Int, phase: String) = "Sikl $day-kuni — $phase."
        override fun waterRemaining(ml: Int) = "Suv: yana $ml ml ichish kerak."
        override val waterGoalMet = "Suv maqsadi bajarildi."
        override fun doseDue(name: String, time: String) = "$name — $time da."
        override fun sleptAndEnergy(sleep: String, energyIsHigh: Boolean) =
            "Kecha $sleep uxlagansiz va energiyangiz " +
                (if (energyIsHigh) "yaxshi" else "pastroq") + "."
        override val generalAdvice = "Bugun suvni ko'proq iching va yengil yurishni rejalashtiring."

        override val plan = "Bugungi reja"
        override val taken = "Qabul qildim"
        override val water = "Suv"
        override fun waterLeft(ml: Int) = "yana $ml ml"
        override fun addWater(ml: Int) = "+$ml ml"

        override val healthScore = "Salomatlik ko'rsatkichi"
        override val sleep = "Uyqu"
        override val mood = "Kayfiyat"
        override val steps = "Qadam"
        override fun scoreWord(score: Int) = when {
            score >= 80 -> "Ajoyib"
            score >= 60 -> "Yaxshi"
            score >= 40 -> "O'rtacha"
            else -> "Past"
        }

        override val emptySummaryTitle = "Bugungi xulosa"
        override val emptySummaryBody =
            "Hozircha ma'lumot yo'q. Birinchi belgini qo'shsangiz, bu yerda kunlik " +
                "xulosa va grafiklar paydo bo'ladi."
        override val startTitle = "Bugundan boshlaymizmi?"
        override val startBody = "Kayfiyat, suv yoki ovqat — qaysi biridan boshlash sizga qulay bo'lsa."
        override val startAction = "Birinchi belgini qo'shish"
    }

    override val mind = object : MindStrings {
        override val title = "Ong va kayfiyat"
        override fun todayIs(date: String) = "Bugun · $date"

        override val stress = "Stress"
        override val energy = "Energiya"
        override val levels = listOf("Juda past", "Past", "O'rtacha", "Yuqori", "Juda yuqori")

        override val journal = "Jurnal"
        override val journalPrompt = "O'zingizni qanday his qilyapsiz?"
        override val journalHint = "Fikr va his-tuyg'ularingizni yozing"

        override val moodWeek = "7 kunlik kayfiyat"
        override fun weekAverage(value: String) = "O'rtacha $value"

        override val assistant = "Ong yordamchisi"
        override val assistantPremium = "Kayfiyat va uyqu bog'liqliklari haqida suhbatlashing"
        override val assistantFree = "Premium'da: qo'llab-quvvatlovchi suhbat — terapevt emas"
        override val mood = "Kayfiyat"

        override val breathing = "Nafas"
        override val breathingPurpose = "Stressni kamaytirish"
        override val meditation = "Meditatsiya"
        override val meditationSubtitle = "Xotirjam ong"
        override val meditationPurpose = "Dam olish"
        override val fourSevenEight = "4-7-8"
        override fun practiceMeta(minutes: Int, purpose: String) = "$minutes daq • $purpose"
        override val start = "Boshlash"

        override val breathIn = "Nafas oling"
        override val breathHold = "Ushlab turing"
        override val breathOut = "Chiqaring"
        override val breathingHint = "4 soniya oling · 7 soniya ushlang · 8 soniya chiqaring"
        override val meditationHint = "Ko'zingizni yuming va nafasingizni kuzating"
        override val finish = "Tugatish"
        override val close = "Yopish"
    }

    override val nutrition = object : NutritionStrings {
        override val title = "Ovqatlanish"
        override val insights = "Tahlillar"
        override val meals = "Ovqatlar"
        override val addMeal = "Ovqat qo'shish"
        override val emptyTitle = "Bugun hali ovqat qayd etilmagan"
        override val emptyBody = "Birinchi taomni qo'shing — kaloriya va makrolar shu yerda yig'iladi."

        override val water = "Suv"
        override fun waterOfGoal(drunk: String, goal: String) = "$drunk l / $goal l"
        override fun addWater(ml: Int) = "+$ml ml"

        override val aiAnalysis = "AI tahlili"
        override val aiBasis = "Bugungi ko'rsatkichlaringiz asosida hisoblandi"
        override val scanner = "Ovqat skaneri"
        override val scannerHint = "Kamerani yo'naltiring — taom, porsiya va makrolar taxminan aniqlanadi"
        override val balance = "Balans"
        override val balanceHint = "Ovqat, suv, faollik va uyqu — to'rt yo'nalish"

        override fun mealSlot(slot: MealSlot) = when (slot) {
            MealSlot.BREAKFAST -> "Nonushta"
            MealSlot.LUNCH -> "Tushlik"
            MealSlot.DINNER -> "Kechki ovqat"
            MealSlot.SNACK -> "Gazak"
        }

        override val today = "Bugun"
        override val protein = "Oqsil"
        override val fat = "Yog'"
        override val carbs = "Uglevod"
        override val proteinInline = "oqsil"
        override val fatInline = "yog'"
        override val carbsInline = "uglevod"

        override fun balanced(kcalLeft: Int) =
            "Makrolar bugun muvozanatda. Qolgan $kcalLeft kkal uchun yengil taom yetarli."
        override fun shortOf(macro: String) =
            "Bugun eng ko'p yetishmayotgani — $macro. Keyingi taomda shunga e'tibor bering."
        override fun kcal(value: Int) = "$value kkal"
        override fun grams(value: Int) = "$value g"
    }

    override val journey = object : JourneyStrings {
        override val cycleTitle = "Mening siklim"
        override val info = "Ma'lumot"
        override val calendar = "Kalendar"
        override val noPredictionTitle = "Prognoz uchun ma'lumot yetarli emas"
        override val noPredictionBody =
            "Kamida ikkita hayz sanasi kiritilgach, sikl fazalari va keyingi hayz " +
                "taxmini shu yerda ko'rinadi."
        override val markPeriod = "Hayzni belgilash"
        override val today = "Bugun"
        override fun daysToNextPeriod(days: Int) = "Keyingi hayz — $days kun"
        override val symptoms = "Simptomlar"
        override val change = "O'zgartirish"
        override val averageCycle = "O'rtacha sikl"
        override val averagePeriod = "O'rtacha hayz"
        override val day = "Kun"
        override fun daysValue(days: Int) = "$days kun"

        override val calendarTitle = "Kalendar"
        override val history = "Tarix"
        override val predictedNote = "Konturli kunlar — hisob-kitob natijasi, tibbiy kafolat emas."
        override val markPeriodDay = "Hayzni belgilash"
        override val phaseNotColouredYet = "Hayz sanalari kiritilgach, fazalar shu yerda bo'yaladi."
        override val previousMonth = "Oldingi oy"
        override val nextMonth = "Keyingi oy"
        override val keyPeriod = "Hayz"
        override val keyFertile = "Unumdor"
        override val keyPredicted = "Taxminiy"
        override val dayCaps = "KUN"
        override fun symptomsAndMood(symptoms: String, mood: String) = "$symptoms · kayfiyat $mood"
        override fun noSymptomsAndMood(mood: String) = "Simptom qayd etilmagan · kayfiyat $mood"
        override val statsNote = "Statistika kiritilgan sikllar asosida. Ko'proq ma'lumot " +
            "yig'ilgani sari aniqlik oshadi."
        override val regularity = "Muntazamlik"
        override val regularSteady = "Yaxshi"
        override val regularVaries = "O'zgaruvchan"
        override val cycleLength = "Sikl uzunligi"
        override fun lastNCycles(count: Int) = "oxirgi $count sikl"
        override val previousCycles = "Oldingi sikllar"
        override val noHistoryYet = "Sikl tarixi hali yo'q"
        override val noHistoryYetBody = "Ikkinchi hayz sanasi kiritilgach, uzunlik va " +
            "muntazamlik shu yerda hisoblanadi."
        override fun periodOfDays(days: Int) = "hayz $days kun"
        override val currentCycle = "Joriy"

        override fun cycleDayOrdinal(day: Int) = "Sikl $day-kuni"
        override val cycleDayCaps = "SIKL KUNI"
        override val loggedToday = "Bugun qayd etilgan"
        override val logged = "Qayd etilgan"
        override val noSymptomsLogged = "Simptom qayd etilmagan"
        override val nothingLoggedForDay = "Bu kun uchun yozuv yo'q."
        override fun moodLine(mood: String) = "Kayfiyat — $mood"
        override fun energyLine(level: Int) = "Energiya — $level / 5"
        override fun sleepAndSteps(sleep: String, steps: String) = "Uyqu $sleep · $steps qadam"
        override val fromDevice = "Qurilmadan"
        override val editEntry = "Tahrirlash"

        override val symptomSheetTitle = "Simptom qo'shish"
        override val catalogueLoading = "Belgilar ro'yxati yuklanmoqda…"
        override val severity = "Og'riq darajasi"
        override val severityWords = listOf(
            "Sezilmaydi",
            "Yengil — kunlik ishlarga to'sqinlik qilmaydi",
            "O'rtacha — ba'zan chalg'itadi",
            "Kuchli — ishni qiyinlashtiradi",
            "Juda kuchli — odatdagi ishni bajara olmayman",
        )
        override val notePlaceholder = "Izoh qo'shish…"
        override fun categoryName(category: SymptomCategory) = when (category) {
            SymptomCategory.PAIN -> "Og'riq"
            SymptomCategory.BLEEDING -> "Ajralma"
            SymptomCategory.MOOD -> "Kayfiyat"
            SymptomCategory.SLEEP -> "Uyqu"
            SymptomCategory.ENERGY -> "Energiya"
            SymptomCategory.DIGESTION -> "Hazm"
            SymptomCategory.SKIN -> "Teri"
            SymptomCategory.OTHER -> "Boshqa"
        }

        override val pregnancyTitle = "Homiladorlik"
        override fun trimester(week: Int) = when {
            week <= 13 -> "1-trimestr"
            week <= 27 -> "2-trimestr"
            else -> "3-trimestr"
        }
        override val weekCaps = "  HAFTA"
        override fun weekAndDay(week: Int, day: Int) = "$week-hafta, $day-kun"
        override fun weekOnly(week: Int) = "$week-hafta"
        override fun dueOn(date: String, daysLeft: Int) = "Tug'ish sanasi — $date · $daysLeft kun qoldi"
        override fun dueOnPast(date: String) = "Tug'ish sanasi — $date"
        override val babyDevelopment = "Bolaning rivojlanishi"
        override val babyDevelopmentBody =
            "Bu haftada nima o'zgarayotgani haqida Bilim kutubxonasida o'qing."
        override val todaysSymptoms = "Bugungi simptomlar"
        override val addSymptom = "+ Qo'shish"
        override val upcomingAppointments = "Yaqin uchrashuvlar"
        override val all = "Barchasi"
        override val noAppointments = "Tadbir qo'shilmagan"
        override val noAppointmentsBody =
            "Ko'rik yoki tahlil sanasini yozib qo'ying — eslatma yuboriladi."
        override val logToday = "Bugungi holatni qayd etish"
        override val aiAdvice =
            "Bu haftada temirga boy ovqatlar va yengil cho'zilish mashqlari foydali " +
                "bo'lishi mumkin. Umumiy salomatlik ma'lumoti."
        override val aiBadge = "SADORA AI · TAVSIYA"

        override val appointmentsTitle = "Tadbirlar"
        override val filterUpcoming = "Yaqin"
        override val filterPast = "O'tgan"
        override val filterAll = "Barchasi"
        override val listEmpty = "Ro'yxat bo'sh"
        override val nothingInThisFilter = "Bu bo'limda tadbir yo'q"
        override val appointmentsEmptyBody = "Shifokor ko'rigi, UTT yoki tahlil sanasini " +
            "yozib qo'ying — eslatma ham shu yerdan sozlanadi."
        override val addAppointment = "Tadbir qo'shish"
        override val nextCaps = "KEYINGI"
        override val todayCaps = "BUGUN"
        override val tomorrowCaps = "ERTAGA"
        override fun inDaysCaps(days: Int) = "$days KUNDAN KEYIN"
        override val appointmentsNote = "Tadbirlar ro'yxatini o'zingiz to'ldirasiz. " +
            "SADORA tekshiruv jadvalini tayinlamaydi."
        override val appointmentDone = "Bo'lib o'tdi"
        override fun reminderSet(offset: String) = "Eslatma $offset"
        override fun reminderOffset(hours: Int) = when (hours) {
            in 0..2 -> "2 soat oldin"
            in 3..24 -> "1 kun oldin"
            else -> "2 kun oldin"
        }
        override val noReminder = "Kerak emas"
        override val editAppointment = "Tadbirni tahrirlash"
        override val appointmentName = "Nomi"
        override val appointmentNameHint = "Skrining UTT"
        override val appointmentDate = "Sana"
        override val appointmentDateHint = "27.8.2026"
        override val appointmentDateInvalid = "Sana kun.oy.yil ko'rinishida"
        override val appointmentTime = "Vaqti (ixtiyoriy)"
        override val appointmentPlace = "Joyi (ixtiyoriy)"
        override val appointmentPlaceHint = "Respublika markazi"
        override val reminder = "Eslatma"
        override val appointmentDateNote = "Sana kun.oy.yil ko'rinishida yoziladi, masalan 27.8.2026."

        override val checkInTitle = "O'zingizni qanday his qilyapsiz?"
        override val todaysSymptomsLabel = "Bugungi simptomlar"
        override val babyMovement = "Bolaning harakati"
        override fun movement(movement: FetalMovement) = when (movement) {
            FetalMovement.USUAL -> "Odatdagidek"
            FetalMovement.LESS -> "Kamroq"
            FetalMovement.MORE -> "Ko'proq"
        }
        override val movementWarning = "Harakat sezilarli kamaysa yoki umuman sezilmasa, " +
            "kechiktirmasdan shifokorga murojaat qiling."
        override val privateNote = "Izoh — faqat siz ko'rasiz"
        override val privateNoteHint = "Yozib qo'ying…"
        override val checkInSaved = "Bugungi holat saqlandi"

        override val postpartumTitle = "Tug'ruqdan keyin"
        override val recoveryWeeks = "  hafta · tiklanish davri"
        override val recoveryNote =
            "Tiklanish har bir ayolda turlicha kechadi. Bu shkala faqat yo'naltiruvchi."
        override val mood = "Kayfiyat"
        override val sleep = "Uyqu"
        override val brokenSleep = "Bo'lingan uyqu"
        override val feedingAndWater = "Emizish va suv"
        override val water = "Suv"
        override val calories = "Kaloriya"
        override val moodWatch = "Kayfiyat kuzatuvi"
        override val moodWatchBody =
            "Uzoq davom etgan tushkunlik yoki tashvish bo'lsa, mutaxassisga murojaat " +
                "qilish tavsiya etiladi. SADORA tashxis qo'ymaydi."
        override val postpartumLibrary = "Bilim — tug'ruqdan keyin"
        override val postpartumLibraryBody = "Tug'ruqdan keyingi materiallar"

        override val perimenopauseTitle = "Perimenopauza"
        override val cycleRegularity = "Sikl muntazamligi"
        override val noData = "ma'lumot yo'q"
        override fun lastCycles(count: Int) = "oxirgi $count sikl"
        override val regularityEmpty =
            "Hayz sanalarini belgilay boshlaganingizda sikl uzunligi shu yerda " +
                "ko'rinadi. Bu bosqichda bashorat ko'rsatilmaydi."
        override fun regularitySpread(shortest: Int, longest: Int) =
            "Sikl uzunligi $shortest–$longest kun orasida o'zgargan — bu bosqich uchun " +
                "kutilgan holat. Bashorat ko'rsatilmaydi."
        override fun regularitySteady(shortest: Int, longest: Int) =
            "Sikl uzunligi $shortest–$longest kun orasida. Bu bosqichda bashorat " +
                "ko'rsatilmaydi."
        override val energy = "Energiya"
        override val observation = "Kuzatish"
        override val observationBody =
            "Uyqu, kayfiyat va simptomlar orasidagi bog'liqliklarni ko'rish."
        override val seeSymptoms = "Simptomlarni ko'rish"

        override val menopauseTitle = "Salomatlik"
        override val scoreNote =
            "Uyqu, faollik, ovqatlanish va kayfiyat asosida. Bu ball tibbiy " +
                "ko'rsatkich emas."
        override val activity = "Faollik"

        override val stageSymptomsTitle = "Simptomlar"
        override val noRecordsYet = "Hali yozuv yo'q"
        override val noRecordsYetBody = "Quyidan bugungi belgilarni belgilang. Bir necha " +
            "kundan keyin shu yerda qaysi belgi qanchalik tez-tez uchrashi ko'rinadi."
        override fun windowDays(days: Int) = "$days kun"
        override fun weekNumber(week: Int) = "$week-hafta"
        override fun recordedOnDays(window: Int, days: Int) = "$window kun ichida $days kun qayd etilgan."
        override val logToday2 = "Bugun qayd etish"
        override val mostFrequent = "Eng ko'p uchraganlar"
        override val symptomsDisclaimer = "Simptomlar ro'yxati kuzatuv uchun. Yangi yoki " +
            "kuchayib borayotgan belgilar bo'lsa shifokor bilan maslahatlashing."

        override val sleepMoodTitle = "Uyqu va kayfiyat"
        override val notEnoughData = "Ma'lumot yetarli emas"
        override val notEnoughDataBody = "Uyqu soat yoki telefondan keladi, kayfiyat esa " +
            "kunlik check-in'dan. Bir necha kundan keyin bu yerda ikkalasi birga ko'rinadi."
        override val scoreCaps = "BALL"
        override fun sleepGoal(hours: String) = "Maqsad · $hours"
        override val moodWeek7 = "7 kunlik kayfiyat"
        override val noticed = "Kuzatish"
        override val breathingCard = "Nafas mashqi"
        override val breathingCardNote = "Uyqu oldidan · 4 daqiqa"
        override val journalCard = "Kundalik"
        override val journalCardNote = "Faqat siz ko'rasiz"

        override val estimatedCaps = "TAXMINIY"
        override val balanceCaps = "BALANS"
        override val premiumCaps = "PREMIUM"
        override val libraryCaps = "KUTUBXONA"
        override val predictionDisclaimer = "Bashorat kiritilgan ma'lumotlarga asoslanadi " +
            "va tibbiy xulosa emas."
    }

    override val modules = object : ModuleStrings {
        override val sleepTitle = "Uyqu"
        override val sleepEmptyTitle = "Uyqu ma'lumoti yo'q"
        override val sleepEmptyBody =
            "Soat yoki telefon sinxronlanganda uyqu davomiyligi va bosqichlari shu " +
                "yerda ko'rinadi."
        override val sleepWeek = "7 kunlik davomiylik"
        override fun average(value: String) = "O'rtacha $value"
        override fun daysRecorded(withData: Int, total: Int) = "$withData / $total kun qayd etilgan"
        override val sleepManual = "Uyquni qo'lda kiritish"
        override fun goalFrom(hours: Int) = "$hours soatdan"
        override val lastNight = "Kecha"
        override fun restingPulse(bpm: Int) = "Tinch puls $bpm bpm"
        override val deep = "Chuqur"
        override val light = "Yengil"
        override val stages = "Bosqichlar"

        override val insightsTitle = "Tahlillar"
        override fun windowDays(days: Int) = "$days kun"
        override val windowPremium = "Bu oraliq Premium bilan ochiladi"
        override val insightsEmptyTitle = "Tahlillar hozircha yo'q"
        override val loadFailed = "Ma'lumotlar yuklanmadi. Internetni tekshirib, qayta urinib ko'ring."
        override val noRecordsInWindow = "Bu oraliqda yozuv yo'q"
        override val noRecordsBody =
            "Uyqu, kayfiyat, suv yoki ovqatni qayd etsangiz, trendlar shu yerda " +
                "chiziladi. O'lchanmagan raqamni ko'rsatmaymiz."
        override val sleepTrend = "Uyqu trendi"
        override val activityTrend = "Faollik"
        override val moodTrend = "Kayfiyat"
        override val notEnoughForChart = "Grafik uchun ma'lumot yetarli emas"
        override val notEnoughForChartBody =
            "Bu oraliqda uyqu, qadam va kayfiyat bo'yicha yozuv topilmadi."
        override val correlations = "Kuzatilgan bog'liqliklar"
        override val correlationsPremium = "Bog'liqliklar Premium bilan ochiladi"
        override val noCorrelation = "Bu oraliqda ishonchli bog'liqlik topilmadi."
        override val noCorrelationBody =
            "Kamida sakkiz kunlik yozuv kerak, va farq sezilarli bo'lishi shart — aks " +
                "holda hech narsa yozmaymiz."
        override val averagePrefix = "O'rtacha — "

        override val all = "Barchasi"
        override val knowledgeTitle = "Bilim"
        override val search = "Qidirish"
        override val libraryFailed = "Kutubxona ochilmadi"
        override val libraryEmpty = "Kutubxona hozircha bo'sh"
        override val libraryEmptyBody = "Yangi maqolalar chiqqanda shu yerda paydo bo'ladi."
        override val nothingFound = "Hech narsa topilmadi"
        override val nothingFoundBody = "Boshqa kalit so'z yoki kategoriya bilan urinib ko'ring."
        override val clearFilters = "Filtrlarni tozalash"
        override fun readMinutes(minutes: Int) = "$minutes DAQIQA"

        override val medsTitle = "Dorilar"
        override val today = "Bugun"
        override val history = "Tarix"
        override val nextDose = "Keyingi qabul"
        override fun oneTabletWith(note: String) = "1 tabletka · $note"
        override val take = "Qabul qildim"
        override val later = "Keyinroq"
        override val skip = "O'tkazish"
        override val medsEmpty = "Hali dori qo'shilmagan"
        override val medsEmptyBody = "Dori qo'shsangiz, qabul vaqtlari va zaxirasi shu yerda ko'rinadi."
        override val addMedication = "Dori qo'shish"
        override val medsDisclaimer =
            "O'tkazib yuborilgan qabul bo'yicha SADORA yo'riqnoma bermaydi. Dori qabul " +
                "qilish tartibi yoki shifokor/farmatsevt tavsiyasiga amal qiling."
        override fun stockLeft(name: String, days: Int) = "$name zaxirasi $days kunga qoldi"
        override fun stockDays(days: Int) = "Zaxira $days kun"
        override val pending = "Kutilmoqda"
        override val skipped = "O'tkazildi"

        override val featureCycleMood = "Sikl va kayfiyat"
        override val featureFoodDiary = "Ovqat kundaligi"
        override val featureAiChat = "AI suhbat"
        override val featureScanner = "Ovqat skaneri"
        override val featureLongInsights = "30/90 kunlik tahlil"
        override val premiumTitle = "SADORA Premium"
        override val premiumBody =
            "AI suhbat, ovqat skaneri va kengaytirilgan tahlillar. Bepul rejadagi " +
                "hamma narsa saqlanadi."
        override val plansFailed = "Tariflar yuklanmadi"
        override val plansFailedBody = "Internetni tekshirib, qayta urinib ko'ring."
        override val paymentAccepted = "To'lov qabul qilindi. Premium ochildi."
        override val paymentPending = "To'lov kutilmoqda…"
        override val noPaymentMethod = "Hozircha to'lov usuli mavjud emas."
        override val cancelAnytime = "Istalgan vaqtda bekor qilish mumkin"
        override val restorePurchase = "Xaridni tiklash"
        override fun priceFor(sum: String, monthly: Boolean) =
            "$sum so'm / " + (if (monthly) "oy" else "yil")
        override fun perMonth(sum: String) = "$sum so'm/oy"
        override fun saving(percent: Int) = "−$percent%"
        override val payWithPayme = "Payme orqali to'lash"
        override val payWithClick = "Click orqali to'lash"
        override val payWithAppStore = "App Store orqali"
        override val payWithGooglePlay = "Google Play orqali"

        override val journalTitle = "Kundalik va praktika"
        override val journalPrivate = "FAQAT SIZ KO'RASIZ"
        override val journalLabel = "Kundalik"
        override val journalPrompt = "Bugun o'zingizni qanday his qilyapsiz?"
        override val journalEmpty = "Kundalik hozircha bo'sh"
        override val journalEmptyBody = "Birinchi yozuvingizni yozing. Uni sizdan boshqa hech kim ko'rmaydi."
        override val journalDeleteTitle = "Yozuvni o'chirish"
        override val journalDeleteBody = "Bu yozuv butunlay o'chiriladi va uni qaytarib bo'lmaydi."
        override val journalDeleteAction = "Yozuvni o'chirish"

        override val sourcesTitle = "Ma'lumot manbalari"
        override fun sourcesConnected(count: Int) = "$count manba ulangan"
        override fun lastSample(ago: String) = "Oxirgi namuna $ago"
        override val noSampleYet = "Hali namuna kelmagan"
        override val sourcesEmpty = "Ulangan manba yo'q"
        override val sourcesEmptyBody = "HealthKit yoki Health Connect ruxsat bergach, kelgan " +
            "namunalar va ularning vaqti shu yerda ko'rinadi."
        override val sourcesNote = "Har bir ko'rsatkichda manba va vaqt belgisi ko'rsatiladi. " +
            "Bir xil ko'rsatkich bir nechta manbadan kelsa, ustuvorlik sozlamalari qo'llanadi."
        override val connected = "Ulangan"
        override val notConnected = "Ulanmagan"
        override fun samples(count: String) = "$count namuna"
        override fun metric(metric: HealthMetric) = when (metric) {
            HealthMetric.STEPS -> "Qadamlar"
            HealthMetric.ACTIVE_ENERGY -> "Faol kaloriya"
            HealthMetric.DISTANCE -> "Masofa"
            HealthMetric.HEART_RATE -> "Puls"
            HealthMetric.RESTING_HEART_RATE -> "Tinch puls"
            HealthMetric.HRV -> "HRV"
            HealthMetric.RESPIRATORY_RATE -> "Nafas"
            HealthMetric.BODY_TEMPERATURE -> "Harorat"
            HealthMetric.SLEEP_DURATION -> "Uyqu"
            HealthMetric.SLEEP_DEEP -> "Chuqur uyqu"
            HealthMetric.SLEEP_REM -> "REM"
            HealthMetric.WEIGHT -> "Vazn"
        }

        override val balanceTitle = "Balans"
        override val fourDirections = "To'rt yo'nalish"
        override val balanceDisclaimer =
            "Balans balli o'zingiz belgilagan maqsadlarga nisbatan hisoblanadi. Bu ball " +
                "tibbiy ko'rsatkich emas."
        override val balanced =
            "Bugun to'rt yo'nalish ham muvozanatda. Ovqat \"yoqib yuborilishi\" kerak " +
                "bo'lgan qarz emas."
        override fun someRoomIn(direction: String) =
            "Kun yaxshi ketyapti. \"$direction\" bo'yicha biroz joy bor — xohlasangiz " +
                "shunga e'tibor bering."
        override fun fallingBehind(direction: String) =
            "Bugun \"$direction\" ortda qolyapti. Kun hali tugagani yo'q, shoshilmang."
        override val food = "Ovqatlanish"
        override val water = "Suv"
        override val activity = "Faollik"
        override val sleep = "Uyqu"
        override fun ofKcal(eaten: String, goal: String) = "$eaten / $goal kkal"
        override fun ofLitres(drunk: String, goal: String) = "$drunk / $goal l"
        override fun ofSteps(walked: String, goal: String) = "$walked / $goal qadam"
        override fun ofSleep(slept: String) = "$slept / 8s"
    }
}
