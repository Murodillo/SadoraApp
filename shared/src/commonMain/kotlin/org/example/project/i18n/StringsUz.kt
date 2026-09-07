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
}
