package org.example.project.i18n

import org.example.project.model.CyclePhase
import org.example.project.model.LifeStage
import org.example.project.model.Mood

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

        override val save = "Saqlash"
        override val cancel = "Bekor"
        override val delete = "O'chirish"
        override val close = "Yopish"
        override val add = "Qo'shish"
        override val edit = "Tahrirlash"
        override val done = "Tayyor"

        override val litres = "l"
        override val millilitres = "ml"
        override val kcal = "kkal"
        override val steps = "qadam"
        override val minutesShort = "daq"
        override fun days(count: Int) = "$count kun"
    }

    override val today = object : TodayStrings {
        override fun greetingLine(greeting: String) =
            "$greeting — bugun o'zingizga g'amxo'rlik qilish uchun ajoyib kun 🌸"

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
        override val weeklyGoals = "Haftalik maqsadlar"
        override val strengthTraining = "Kuch mashqlari"
        override val calciumAndD = "Kalsiy va D vitamini"
    }
}
