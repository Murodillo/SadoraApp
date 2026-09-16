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
 * O'zbekcha — the language the app was written in, and the reference the other two are
 * translated from. When a wording question comes up, this file is the answer.
 */
object StringsUz : Strings {
    override val languageName = object : LanguageNames {}

    override val tabs = object : TabStrings {
        override val today = "Bugun"
        override val mind = "Ong"
        override val mindAndNutrition = "Ong · Ovqat"
        override val secretChat = "Chat"
        override val nutrition = "Ovqat"
        override val premium = "Premium"
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
        override val skip = "O'tkazish"
        override val back = "Ortga"
        override val skipTheseQuestions = "Bu savollarni o'tkazib yuborish"

        override val nameTitle = "Sizni qanday chaqiraylik?"
        override val nameSubtitle = "Keling, tanishamiz. Bu ismni keyin ham o'zgartira olasiz."
        override val nameLabel = "Ism"
        override val nameHint = "Ismingiz"
        override val nameNote = "Ma'lumotlaringiz faqat SADORA ichida saqlanadi. Uchinchi " +
            "shaxslarga berilmaydi va istalgan vaqtda o'chirasiz."

        override val birthYearTitle = "Qaysi yilda tug'ilgansiz?"
        override val birthYearSubtitle = "Yosh bashoratlarni aniqroq qiladi."

        override val goalsTitle = "Sizga nimada yordam beraylik?"
        override val goalsSubtitle = "Xohlaganingizcha tanlang."

        override val stageTitle = "Hozir qaysi bosqichdasiz?"
        override val stageSubtitle = "Keyingi savollar va ilovaning o'zi shu tanlovga moslashadi."
        override fun stagePromise(stage: LifeStage) = when (stage) {
            LifeStage.Cycle -> "Siklingizni kuzatamiz va keyingi hayzni oldindan aytamiz."
            LifeStage.TryingToConceive -> "Unumdor kunlarni belgilaymiz va tayyorgarlikda yoningizda bo'lamiz."
            LifeStage.Pregnancy -> "Har haftaning o'zgarishlarini va tekshiruvlarni kuzatamiz."
            LifeStage.Postpartum -> "Tiklanish, emizish va kayfiyatga alohida e'tibor beramiz."
            LifeStage.Perimenopause -> "Simptomlar, uyqu va energiyani birga kuzatib boramiz."
            LifeStage.Menopause -> "Salomatlik maqsadlariga qaratilgan kundalik yordam beramiz."
        }

        override val doctorTitle = "SADORA'ni sizga shifokor tavsiya qildimi?"
        override val no = "Yo'q"

        override val cycleLengthTitle = "Siklingiz odatda necha kun davom etadi?"
        override fun cycleLengthDerived(days: Int) =
            "Belgilagan sanalaringizdan $days kun chiqdi. Noto'g'ri bo'lsa, to'g'rilang."
        override val cycleLengthHint = "Aniq bilmasangiz, taxminiy son ham yetarli — keyin o'zi aniqlashadi."
        override val periodLengthTitle = "Hayz necha kun davom etadi?"

        override fun feelingTitle(name: String) =
            if (name.isBlank()) "O'zingizni qanday his qilyapsiz?" else "$name, o'zingizni qanday his qilyapsiz?"
        override val feelingSubtitle = "Rostini ayting — javobingizga qarab boshlashni moslaymiz."
        override val feelings = listOf(
            FeelingOption("Yaxshi — hammasi joyida 🙂", 4, "Ajoyib. Shu holatni ushlab turishga yordam beramiz."),
            FeelingOption("Charchaganman 😴", 2, "Uyqu va energiyani birinchi o'ringa qo'yamiz."),
            FeelingOption("Xavotirdaman 😟", 2, "Sekin boshlaymiz. Faqat o'zingiz xohlagan narsani yozasiz."),
            FeelingOption("Tanamni yaxshiroq bilmoqchiman ✨", 3, "Aynan shu uchun ham SADORA bor."),
        )

        override val bodyTitle = "Bo'y va vazningiz"
        override val bodySubtitle = "Ixtiyoriy. Hech kimga ko'rsatilmaydi va istalgan vaqtda o'chiriladi."
        override val height = "Bo'y"
        override val weight = "Vazn"

        override val deviceTitle = "Aqlli soat yoki bilaguzuk bormi?"
        override val deviceSubtitle =
            "Bo'lsa, uyqu va qadamlar o'zi tushadi — qo'lda kiritishning hojati qolmaydi."
        override val deviceYes = "Ha, bor"
        override val deviceYesNote = "Keyingi qadamda ulaymiz"
        override val deviceNo = "Yo'q"
        override val deviceNoNote = "Hammasini qo'lda ham kiritish mumkin"
        override val deviceConnectTitle = "Qurilmangizni ulaymizmi?"
        override val deviceConnectBody =
            "Bir marta ulasangiz, uyqu, pulse va qadamlar har kuni o'zi yangilanadi. " +
                "Istalgan vaqtda uzib qo'yishingiz mumkin."
        override val deviceConnectNow = "Hozir ulash"
        override val deviceConnectLater = "Keyinroq"

        override val inviteTitle = "Taklif kodingiz bormi?"
        override val inviteSubtitle = "Bo'lmasa, bu qadamni o'tkazib yuboring."
        override val inviteLabel = "Taklif kodi"
        override val inviteHint = "Masalan, K7M2QP"
        override fun inviteReward(coins: String) = "Kod bilan kelsangiz — $coins gul sovg'a"
        override val inviteFromLink = "Havoladan olindi"

        override val permissionsTitle = "Nimalarga ruxsat berasiz?"
        override val permissionsSubtitle = "Har birini keyin Profil bo'limidan o'zgartira olasiz."
        override val permissionReminders = "Eslatmalar"
        override val permissionRemindersNote = "Hayz, dori va tekshiruv vaqtini eslatib turamiz."
        override val permissionHealth = "Salomatlik ma'lumotlari"
        override val permissionHealthNote = "Qadamlar va uyquni soatingizdan o'qiymiz."
        override val permissionCamera = "Kamera"
        override val permissionCameraNote = "Ovqatni suratga olib, tarkibini aniqlash uchun."

        override val phoneTitle = "Raqamingizni kiriting"
        override val phoneSubtitle = "Javoblaringizni saqlash uchun bir martalik kod yuboramiz."
        override val sending = "Yuborilmoqda…"
        override val sendCode = "Kodni yuborish"
        override val haveAccount = "Hisobim bor · Kirish"
        override val phoneLabel = "Telefon raqami"
        override val phoneNote = "Raqam faqat kirish uchun ishlatiladi va reklama uchun berilmaydi."
        override val codeTitle = "Kodni kiriting"
        override fun codeSubtitle(phone: String) = "+998 $phone raqamiga 6 xonali kod yubordik."
        override val checking = "Tekshirilmoqda…"
        override val confirm = "Tasdiqlash"
        override fun resendIn(seconds: Int) = "Qayta yuborish · ${seconds}s"
        override val resend = "Kodni qayta yuborish"
        override val codeSecrecy = "Kodni hech kimga aytmang. SADORA xodimlari kodni so'ramaydi."

        override val periodsTitle = "Oxirgi hayzlaringiz qachon bo'lgan?"
        override fun periodsSubtitle(periodLength: Int) =
            "Hayz boshlangan kunni bosing — qolgan $periodLength kun o'zi belgilanadi. " +
                "Keyin kunlarni bittalab qo'shish yoki olib tashlash mumkin."
        override val markMore = "Yana belgilaysizmi?"
        override fun markMoreBody(marked: Int) =
            "Hozir $marked ta hayz belgilandi. Uchtasi belgilansa, siklingiz uzunligini " +
                "o'lchay olamiz va bashorat ancha aniq bo'ladi."
        override val iWillMark = "Belgilayman"
        override fun markedWithAverage(filled: Int, total: Int, averageCycle: Int) =
            "$filled/$total belgilandi · o'rtacha sikl $averageCycle kun"
        override fun markedMoreNeeded(filled: Int, total: Int) = "$filled/$total belgilandi · yana belgilang"
        override val markAPeriodStart = "Hayz boshlangan kunni belgilang"

        override val regularityTitle = "Siklingiz muntazammi?"
        override val regularitySubtitle = "Har oy taxminan bir xil kunda keladimi?"
        override val regularYes = "Ha, muntazam"
        override val regularYesNote = "Yaxshi — bashoratlar boshidanoq aniqroq bo'ladi."
        override val regularNo = "Yo'q, o'zgarib turadi"
        override val regularNoNote = "Buni hisobga olamiz va bashoratlarga ishonch darajasini ko'rsatamiz."
        override val regularUnknown = "Bilmayman"
        override val regularUnknownNote = "Muammo emas. Bir necha oy kuzatgach, o'zi ayon bo'ladi."

        override val sensitiveTitle = "Keyingi savollar shaxsiy"
        override val sensitiveBody = "Kontratsepsiya va homiladorlikni rejalashtirish haqida " +
            "so'raymiz. Bu savollar bashoratlarni aniqroq qiladi, lekin javob berish shart emas."

        override val birthControlTitle = "Oxirgi 6 oyda kontratsepsiyadan foydalanganmisiz?"
        override val birthControlSubtitle = "Ba'zi usullar siklga ta'sir qiladi, shuning uchun so'rayapmiz."
        override fun birthControl(option: BirthControl) = when (option) {
            BirthControl.None -> "Yo'q"
            BirthControl.StillUsing -> "Hozir ham ishlatyapman"
            BirthControl.Pill -> "Ha, tabletka"
            BirthControl.Iud -> "Ha, spiral (IUD)"
            BirthControl.Barrier -> "Ha, prezervativ yoki boshqa nogormonal usul"
            BirthControl.Other -> "Ha, boshqa usul"
            BirthControl.Undisclosed -> "Aytishni xohlamayman"
        }
        override fun birthControlNote(option: BirthControl) = when (option) {
            BirthControl.Pill, BirthControl.Iud ->
                "Gormonal usuldan keyin sikl bir necha oy tiklanadi — bashoratlarni ehtiyotkorlik bilan beramiz."
            BirthControl.StillUsing ->
                "Gormonal usul davomida ovulyatsiya bo'lmaydi, shuning uchun unumdor kunlarni ko'rsatmaymiz."
            else -> null
        }

        override val conceptionTitle = "Qachondan beri homiladorlikka harakat qilyapsiz?"
        override fun conceptionNote(window: ConceptionWindow) = when (window) {
            ConceptionWindow.JustStarted ->
                "Yo'lning boshi — savollar ko'p bo'ladi va biz har birida yoningizdamiz."
            ConceptionWindow.OverAYear ->
                "Bir yildan oshgan bo'lsa, shifokorga murojaat qilish tavsiya etiladi. Buni ham eslatib turamiz."
            else -> null
        }

        override val dueDateTitle = "Tug'ilish sanasi qachon kutilyapti?"
        override val dueDateSubtitle = "Shifokor aytgan taxminiy sanani belgilang."
        override val birthDateTitle = "Farzandingiz qachon tug'ilgan?"
        override val birthDateSubtitle = "Tiklanish bosqichlarini shu sanadan hisoblaymiz."

        override fun symptomsTitle(name: String) =
            if (name.isBlank()) "Bugun nimani sezyapsiz?" else "$name, bugun nimani sezyapsiz?"
        override val symptomsSubtitle = "Bir nechtasini tanlashingiz mumkin. Hech qaysisi bo'lmasa — o'tkazib yuboring."
        override val saveSymptoms = "Belgilarni saqlash"
        override val notAloneTitle = "Siz yolg'iz emassiz"
        override val proofs = listOf(
            Proof("Ayollar tanlagan", "O'zbekistonda minglab ayol siklini SADORA bilan kuzatadi."),
            Proof("Shifokorlar bilan", "Savollar va maqolalar ginekologlar bilan birga tayyorlanadi."),
            Proof("Ma'lumot sizniki", "Istalgan vaqtda eksport qiling yoki butunlay o'chiring."),
        )
        override val analysingTitle = "Sizga moslashtirilmoqda"
        override val analysisSteps = listOf(
            "Javoblaringiz o'qilmoqda…",
            "Siklingiz hisoblanmoqda…",
            "Bugun ekrani sozlanmoqda…",
            "Deyarli tayyor…",
        )

        override fun readyTitle(name: String) =
            if (name.isBlank()) "Tayyor! Profilingiz yaratildi" else "Tayyor, $name!"
        override val readyBody = "Bugun ekranini javoblaringiz asosida sozladik. " +
            "Hammasini keyin Profil bo'limidan o'zgartira olasiz."
        override val saving = "Saqlanmoqda…"
        override val startSadora = "SADORA'ni boshlash"
        override fun cycleSummary(cycleLength: Int, periodLength: Int) =
            "Sikl $cycleLength kun · hayz $periodLength kun"
        override val remindersOn = "Eslatmalar yoqildi"
        override val healthDataOn = "Salomatlik ma'lumotlari ulanadi"
        override fun goalsChosen(count: Int) = "$count ta maqsad belgilandi"

        override val signInTitle = "Xush kelibsiz"
        override val signInSubtitle = "Raqamingizga kod yuboramiz"
        override val noAccount = "Hisobingiz yo'qmi? "
        override val signUp = "Ro'yxatdan o'tish"

        override val consentTitle = "Tanangiz. Ma'lumotingiz."
        override val consentBody = "Salomatlik ma'lumotlaringiz SADORA'dan tashqarida " +
            "hech kimga berilmaydi va uni istalgan vaqtda o'chira olasiz."
        override val consentHealth = "Salomatlik ma'lumotlarimni ilova funksiyalari uchun qayta ishlashga roziman. "
        override val consentHealthMore = "Batafsil — "
        override val consentTermsPrefix = "Men "
        override val terms = "Foydalanish shartlari"
        override val and = " va "
        override val privacyPolicy = "Maxfiylik siyosati"
        override val consentAnalytics = "Ilovadagi harakatlarim anonim tahlil qilinishiga " +
            "roziman. Bu ixtiyoriy va SADORA'ni yaxshilash uchun ishlatiladi."
        override val consentAll = "Hammasiga rozilik"

        override val starterSymptoms = listOf(
            StarterSymptom("cramps", "Qorin og'rig'i"),
            StarterSymptom("fatigue", "Charchoq"),
            StarterSymptom("swelling", "Shishish"),
            StarterSymptom("breast_tender", "Ko'krak og'rig'i"),
            StarterSymptom("back_pain", "Bel og'rig'i"),
            StarterSymptom("headache", "Bosh og'rig'i"),
        )
    }

    override val profile = object : ProfileStrings {
        override val title = "Profil"
        override val unnamed = "Foydalanuvchi"

        override val sleep = "Uyqu"
        override val medications = "Dorilar"
        override val secretChat = "Chat"
        override val insights = "Tahlillar"
        override val knowledge = "Bilim"

        override val rewards = "Gul va streak"
        override val shop = "Gul do'koni"
        override val referral = "Do'stlarni taklif qilish"
        override val homeLayout = "Bosh ekran tartibi"

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
        override val shareProfile = "Shifokorga ko'rsatish"
        override val shareProfileNote = "QR kod — shifokor skanerlab, yozuvlaringizni ko'radi"
        override val devices = "Qurilmalar"
    }

    override val settings = object : SettingsStrings {
        override val aboutTitle = "SADORA haqida"
        override fun version(number: String) = "Versiya $number"
        override val languageTitle = "Til"
        override val languageNote = "Ilova tili darhol o'zgaradi. AI javoblari, tahlillar " +
            "va ovqat skaneri ham shu tilda javob beradi."
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
        override val legalEffectiveDate = "Kuchga kirgan sana"
        override val legal = LegalTextsUz
        override val terms = "Foydalanish shartlari"
        override val privacyPolicy = "Maxfiylik siyosati"
        override val yourData = "Ma'lumotlaringiz"
        override val exportData = "Ma'lumotlarni eksport qilish"
        override val exportReady = "Eksport tayyor — qayerga yuborishni tanlang"
        override val exportFailed = "Eksport qilib bo'lmadi. Keyinroq urinib ko'ring."
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
        override val daysWord = "kun"
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

    override val ai = object : AiStrings {
        override val title = "SADORA AI"
        override val subtitle = "Shaxsiy yordamchingiz"
        override val menu = "Yana"
        override val back = "Ortga"
        override val send = "Yuborish"
        override val inputHint = "Istalgan savolni bering…"
        override val emptyPrompt = "Sikl, ovqatlanish, kayfiyat yoki dorilaringiz haqida " +
            "so'rang — javob sizning ma'lumotlaringiz asosida bo'ladi."
        override fun basis(cycleDay: Int, sleep: String, water: String) =
            "Sikl $cycleDay-kun · Uyqu $sleep · Suv $water l asosida"
        override fun questionsLeft(left: Int, limit: Int) = " · $left/$limit savol qoldi"
        override val answerFailed = "Javob berib bo'lmadi. Qayta urinib ko'ring."
        override val sessionOnly = "Suhbat faqat shu seansda saqlanadi va serverga " +
            "yozilmaydi. Ilovadan chiqsangiz u o'chadi."
        override val clearChat = "Suhbatni tozalash"
        override val medicalDisclaimer =
            "SADORA — salomatlik yordamchisi. Tashxis qo'ymaydi va dori tayinlamaydi."

        override val topics = listOf(
            "Energiya" to "Energiyamni qanday barqaror ushlasam bo'ladi?",
            "Ovqatlanish" to "Bugun nima yeganim ma'qul?",
            "Sikl" to "Nega hayzdan oldin charchoq sezaman?",
            "Teri" to "Sikl davomida terim nega o'zgaradi?",
        )

        override val freeBadge = "BEPUL REJA"
        override val howCanIHelp = "Sizga qanday yordam bera olaman?"
        override val readsYourData = "Ma'lumotlaringizni o'qib shaxsiy javob beradi"
        override val sampleAnswer = "Namuna javob"
        override val sampleAnswerBody = "Oxirgi uch kunda uyqu odatdagidan qisqa bo'lgan va " +
            "suv iste'moli pasaygan."
        override val sampleAnswerAdvice = "Shu kunlarda energiya ham past qayd etilgan. " +
            "Bugun ikki qadam: tushga qadar 700 ml suv va 23:00 gacha yotish."
        override val freeFeatures = listOf(
            "Kuniga 20 savol, ma'lumotlar kontekstida",
            "Har kunlik shaxsiy AI xulosa",
            "Ovqat skaneri",
            "30 va 90 kunlik tahlillar",
        )
        override val freeKeeps = "Bepul rejadagi hamma narsa qoladi: sikl, kayfiyat, suv, " +
            "ovqat kundaligi, dorilar, 7 kunlik tahlil."
        override val seePremium = "Premium'ni ko'rish"
        override val notNow = "Hozir emas"
    }

    override val community = object : CommunityStrings {
        override val title = "Chat"
        override val compose = "Yozish"
        override val more = "Yana"
        override val saved = "Saqlangan"
        override fun topic(topic: CommunityTopic) = when (topic) {
            CommunityTopic.All -> "Hammasi"
            CommunityTopic.Cycle -> "Sikl"
            CommunityTopic.Pregnancy -> "Homiladorlik"
            CommunityTopic.Wellbeing -> "Kayfiyat"
            CommunityTopic.Body -> "Tana"
        }
        override fun filter(filter: CommunityFilter) = when (filter) {
            CommunityFilter.Feed -> "Lenta"
            CommunityFilter.Saved -> "Saqlangan"
            CommunityFilter.Mine -> "Meniki"
        }
        override fun sort(sort: CommunitySort) = when (sort) {
            CommunitySort.Newest -> "Yangi"
            CommunitySort.Active -> "Faol"
        }
        override fun anonymousAs(alias: String) = "Anonim · siz: $alias"
        override val anonymous = "Anonim — ismingiz hech kimga ko'rinmaydi"
        override val rulesTitle = "Chat qanday ishlaydi"
        override val rulesIntro = "Bu yerda hamma taxallus ostida. Postlaringiz profilingizga, telefon raqamingizga yoki ismingizga bog'lanmaydi — hatto SADORA jamoasi ham lentada kim yozganini ko'rmaydi."
        override val rules = listOf(
            "Hurmat bilan yozing — bu yerda hamma o'z savoli bilan kelgan.",
            "Shaxsiy ma'lumot qoldirmang: ism, raqam, manzil, surat.",
            "Bu shifokor maslahati emas. Og'riq, qon ketish, isitma bo'lsa — shifokorga.",
            "Reklama va sotuv taqiqlanadi.",
            "Qoidani buzgan postni bayroqcha orqali xabar qiling — u tekshiriladi.",
        )
        override val rulesButton = "Tushunarli"
        override fun reportReason(reason: ReportReason) = when (reason) {
            ReportReason.Spam -> "Spam yoki reklama"
            ReportReason.Abuse -> "Haqorat yoki tahdid"
            ReportReason.Misinformation -> "Xavfli tibbiy maslahat"
            ReportReason.PersonalData -> "Shaxsiy ma'lumot oshkor qilingan"
            ReportReason.Other -> "Boshqa sabab"
        }

        override val nothingSaved = "Saqlangan post yo'q"
        override val nothingHere = "Bu bo'limda hozircha post yo'q"
        override val nothingMine = "Siz hali yozmagansiz"
        override val nothingSavedBody = "Yoqqan postni belgilab qo'ying — u shu yerda turadi."
        override val nothingHereBody = "Birinchi bo'lib yozing — savolingiz taxallus ostida chiqadi."
        override val nothingMineBody = "Yozgan postlaringiz shu yerda yig'iladi. Boshqalar faqat taxallusni ko'radi."
        override val readMore = "…ko'proq"
        override val postTitle = "Post"
        override fun commentsCount(count: Int) = if (count == 0) "Izohlar" else "$count izoh"
        override fun badge(badge: CommunityBadge) = when (badge) {
            CommunityBadge.Newcomer -> "Yangi"
            CommunityBadge.Early -> "Birinchilardan"
            CommunityBadge.Writer -> "Yozuvchi"
            CommunityBadge.Helper -> "Yordamchi"
            CommunityBadge.Loved -> "Sevimli"
            CommunityBadge.Veteran -> "Tajribali"
        }
        override fun badgeHint(badge: CommunityBadge) = when (badge) {
            CommunityBadge.Newcomer -> "Bu hafta qo'shildi"
            CommunityBadge.Early -> "Chatning ilk 500 a'zosidan"
            CommunityBadge.Writer -> "5 va undan ko'p post yozgan"
            CommunityBadge.Helper -> "20 va undan ko'p izoh bilan javob bergan"
            CommunityBadge.Loved -> "Postlari 50 dan ko'p yoqtirish olgan"
            CommunityBadge.Veteran -> "3 oydan beri chatda"
        }
        override val profileTitle = "Profil"
        override val myProfileTitle = "Mening taxallusim"
        override val noBio = "Hali o'zi haqida yozmagan"
        override val editBio = "Bio tahrirlash"
        override val bioHint = "O'zingiz haqingizda bir qator — yosh, bosqich, nima qiziqtiradi. Ism va raqam yozmang."
        override val acceptMessages = "Xabarlarni qabul qilish"
        override val acceptMessagesHint = "O'chirilsa, boshqalar sizga shaxsiy xabar yoza olmaydi"
        override val saveProfile = "Saqlash"
        override val profileSaved = "Profil saqlandi"
        override val statPosts = "Postlar"
        override val statComments = "Izohlar"
        override val statLikes = "Yoqtirish"
        override fun memberSince(date: String) = "$date dan beri"
        override val badgesTitle = "Belgilar"
        override val noBadges = "Hali belgi yo'q — yozing, javob bering, ular o'zi keladi"
        override val herPosts = "Postlari"
        override val noPostsYet = "Hali post yozmagan"
        override val messageButton = "Xabar yozish"
        override val messagesClosed = "Xabarlarni qabul qilmaydi"
        override val block = "Bloklash"
        override val unblock = "Blokdan chiqarish"
        override val blockConfirmTitle = "Bloklaysizmi?"
        override val blockConfirmBody = "U sizga xabar yoza olmaydi, siz ham unga. Postlari lentada qoladi. Istalgan vaqt blokdan chiqarish mumkin."
        override val blocked = "Bloklandi"
        override val unblocked = "Blokdan chiqarildi"
        override val viewProfile = "Profilni ko'rish"
        override val messagesTitle = "Xabarlar"
        override val messagesSubtitle = "Taxallus ostida, faqat ikkingiz o'rtasida"
        override val noMessages = "Hali xabar yo'q"
        override val noMessagesBody = "Lentada postning muallifini bosing — profilidan xabar yozish mumkin."
        override val messageHint = "Xabar yozing"
        override val conversationBlocked = "Bu suhbat yopilgan — xabar yuborib bo'lmaydi"
        override val conversationMenu = "Suhbat"
        override val reportConversation = "Suhbatga shikoyat"
        override val newConversation = "Yangi suhbat"
        override fun unreadCount(count: Int) = "$count ta o'qilmagan"
        override val write = "Yozish"
        override val you = "siz"
        override fun youParenthesised(alias: String) = "$alias (siz)"

        override val noComments = "Hali izoh yo'q. Birinchi bo'lib javob bering."
        override val commentHint = "Izoh yozing"
        override val send = "Yuborish"
        override val whatIsOnYourMind = "Nima haqida so'ramoqchisiz?"
        override fun postsAs(alias: String) = "Post \"$alias\" nomidan chiqadi — ismingiz ko'rinmaydi."
        override val postsAnonymously = "Post taxallus ostida chiqadi — ismingiz ko'rinmaydi."
        override val yourOwnPost = "Bu sizning postingiz."
        override val deletePost = "Postni o'chirish"
        override val newPost = "Yangi post"
        override val postSent = "Post yuborildi"
        override val comments = "Izohlar"
        override val reportPost = "Shikoyat qilish"
        override val postDeleted = "Post o'chirildi"
        override val reportReasonTitle = "Shikoyat sababi"
        override val reportNote = "Shikoyat moderatorga boradi. Kim yuborgani ko'rinmaydi."
        override val sendReport = "Shikoyat yuborish"
        override val reportSent = "Shikoyat yuborildi"
        override val shareSuffix = "SADORA — Chat"
    }

    override val errors = object : ErrorStrings {
        override val phoneInvalid = "Raqam to'liq emas yoki bunday operator kodi yo'q"
        override val nameRequired = "Ism bo'sh bo'lishi mumkin emas"
        override fun tooLong(max: Int) = "Eng ko'pi $max belgi"
        override fun outOfRange(min: Int, max: Int) = "$min–$max oralig'ida bo'lishi kerak"
        override val dateFormat = "Sana kun.oy.yil ko'rinishida"
        override val dateInFuture = "Kelajakdagi sana bo'lishi mumkin emas"
        override val timeFormat = "Vaqt 20:00 ko'rinishida"
        override val wholeNumber = "Faqat raqam"

        override val network = "Internetga ulanib bo'lmadi. Qayta urinib ko'ring."
        override val validation = "Kiritilgan ma'lumot noto'g'ri."
        override val sessionExpired = "Sessiya tugadi. Qaytadan kiring."
        override val blocked = "Hisob bloklangan. Qo'llab-quvvatlash bilan bog'laning."
        override val premiumRequired = "Bu imkoniyat Premium'da ochiladi."
        override val monthlyLimit = "Bu oylik limit tugadi."
        override val dailyLimit = "Bugungi limit tugadi."
        override fun retryAfter(seconds: Int) =
            "Juda ko'p urinish. $seconds soniyadan keyin qayta urining."
        override val retrySoon = "Juda ko'p urinish. Birozdan keyin qayta urining."
        override val otpInvalid = "Kod noto'g'ri yoki muddati tugagan."
        override val featureClosed = "Bu bo'lim hozircha yopiq."
        override val consentRequired = "Buning uchun Maxfiylik bo'limida rozilik bering."
        override val paymentFailed = "To'lov amalga oshmadi. Qayta urinib ko'ring."
        override val unexpected = "Nimadir noto'g'ri ketdi. Qayta urinib ko'ring."
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

        override val customise = "Bosh ekranni sozlash"
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
        override val addWaterTitle = "Suv qo'shish"
        override val undo = "Qaytarish"
        override fun waterAdded(ml: Int) = "$ml ml qo'shildi"
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
        override val sleepManualBody = "Soat bo'lmasa ham uyquni yozib qo'ying — Balans va tahlillar shuni hisobga oladi."
        override val sleepHours = "Soat"
        override val sleepMinutesLabel = "Daqiqa"
        override val sleepSaved = "Uyqu saqlandi"
        override val bodySignalsTitle = "Tana signallari"
        override val bodySignalsNote = "Qurilmangiz o'lchagan ko'rsatkichlar. Lyuteal fazada harorat va puls ko'pincha biroz ko'tariladi — bu kuzatuv, tashxis emas."
        override fun vsLastWeek(delta: String) = "$delta o'tgan haftaga nisbatan"
        override val strain = "Yuklama"
        override val recovery = "Tiklanish"
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
        override val correlationDisclaimer =
            "Bog'liqliklar sabab-natija emas. \"Ko'pincha birga kuzatilgan\" degan ma'noni bildiradi."

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

        override val searchFood = "Taom qidirish"
        override val searchTabAll = "Barchasi"
        override val searchTabFrequent = "Tez-tez"
        override val searchTabRecipes = "Retseptlar"
        override val typeADishName = "Taom nomini yozing"
        override fun nothingFoundFor(query: String) = "\"$query\" bo'yicha topilmadi"
        override val catalogueNote = "Katalog serverdan keladi — o'zbek taomlari birinchi o'rinda."
        override val portionLabel = "Porsiya"
        override val pieces = "dona × 100"
        override val grams = "gramm"
        override fun bowls(count: Int) = "$count kosa"
        override val total = "Jami"
        override val addToDiary = "Kundalikka qo'shish"
        override val perPiece = "dona"
        override val perHundredGrams = "100 g"
        override val proteinInitial = "O"
        override val fatInitial = "Y"
        override val carbsInitial = "U"

        override val articleFailed = "Maqola ochilmadi"
        override val articleFailedBody = "Ma'lumot yuklanmadi. Internetni tekshirib, qayta urinib ko'ring."
        override fun readMinutesCaps(minutes: Int) = "$minutes DAQIQA"
        override val premiumCaps = "PREMIUM"
        override val author = "Muallif"
        override val reviewed = "✓ Ko'rib chiqqan"
        override val restIsPremium = "Maqolaning davomi Premium bilan ochiladi"

        override fun stepsValue(steps: String) = "$steps qadam"
        override fun litresValue(litres: String) = "$litres l"
        override fun kcalValue(kcal: String) = "$kcal kkal"
        override fun outOfFive(value: String) = "$value / 5"
        override fun sleepEnergyFinding(high: String, low: String) =
            "Ko'proq uxlagan kunlarda energiya o'rtacha $high, kamroq uxlagan kunlarda $low bo'lgan."
        override fun activityMoodFinding(high: String, low: String) =
            "Ko'proq yurgan kunlarda kayfiyat o'rtacha $high, kamroq yurgan kunlarda $low bo'lgan."
        override fun waterHeadacheFinding(high: String, low: String) =
            "Ko'proq suv ichgan kunlarning ${high}ida bosh og'rig'i qayd etilgan, kamroq ichgan kunlarning ${low}ida."
        override fun basedOnDays(days: Int) = "$days kun asosida · birga kuzatilgan"
        override fun minutesOnly(minutes: Int) = "$minutes daqiqa"

        override val addMedTitle = "Dori qo'shish"
        override val medName = "Nomi"
        override val medNameHint = "Temir"
        override val medDose = "Doza"
        override val medUnit = "Birlik"
        override val medTime = "Qabul vaqti"
        override val medTimeInvalid = "Vaqt 20:00 ko'rinishida"
        override val addTime = "+ Vaqt"
        override val medDays = "Kunlar"
        override val medFoodRelation = "Ovqatga nisbatan"
        override fun foodRelation(relation: FoodRelation) = when (relation) {
            FoodRelation.ANY -> "Farqi yo'q"
            FoodRelation.BEFORE -> "Oldin"
            FoodRelation.WITH -> "Bilan"
            FoodRelation.AFTER -> "Keyin"
        }
        override fun scheduleKind(kind: ScheduleKind) = when (kind) {
            ScheduleKind.DAILY -> "Har kuni"
            ScheduleKind.WEEKDAYS -> "Tanlangan kunlar"
            ScheduleKind.INTERVAL -> "Bir necha kunda"
        }

        override fun doseCaption(note: String?, relation: FoodRelation) =
            note?.takeIf { it.isNotBlank() } ?: when (relation) {
                FoodRelation.ANY -> "Vaqtidan qat'i nazar"
                FoodRelation.BEFORE -> "Ovqatdan oldin"
                FoodRelation.WITH -> "Ovqat bilan"
                FoodRelation.AFTER -> "Ovqatdan keyin"
            }

        override val medStock = "Zaxira"
        override val medStockUnit = "dona"
        override val medEndDate = "Tugash sanasi"
        override val medNone = "Yo'q"

        override val doseHistoryTitle = "Qabul tarixi"
        override val takenCount = "Qabul qilingan"
        override val skippedCount = "O'tkazilgan"
        override fun adherenceOver(days: Int) = "$days kun"
        override fun lastDays(days: Int) = "Oxirgi $days kun"
        override val noDoseHistory = "Tarix hali bo'sh"
        override val noDoseHistoryBody = "Dori qo'shib, qabulni belgilay boshlaganingizda " +
            "shu yerda qanchasi o'z vaqtida bo'lgani ko'rinadi."
        override fun doseStatus(status: DoseStatus) = when (status) {
            DoseStatus.TAKEN -> "Qabul qilingan"
            DoseStatus.PENDING -> "Kechiktirilgan"
            DoseStatus.SKIPPED -> "O'tkazilgan"
        }

        override val scannerTitle = "Ovqat skaneri"
        override val scannerFrameHint = "Taomni ramka ichiga joylashtiring"
        override val scannerLightHint = "Yaxshi yorug'lik natijani aniqroq qiladi"
        override val scannerGallery = "Galereya"
        override val scannerShutter = "Suratga olish"
        override val scannerManual = "Qo'lda"
        override val scannerPremium = "Skaner Premium obunada ishlaydi."
        override val scannerUnavailable = "Skaner hozircha ishlamayapti"
        override val scannerUnavailableBody = "Taomni qo'lda qidirib qo'shishingiz mumkin."
        override val analysing = "Tahlil qilinmoqda…"
        override val analysingWait = "Bu odatda bir necha soniya oladi"
        override val scanFailed = "Rasmni o'qib bo'lmadi"
        override val scanFailedBody = "Qaytadan urinib ko'ring yoki taomni qo'lda qo'shing."
        override val notFood = "Rasmda ovqat ko'rinmadi"
        override val scanResult = "Skan natijasi"
        override fun scanConfidence(percent: Int) = "Ishonch $percent%"
        override fun portionAndKcal(portion: String, kcal: String) =
            "$portion porsiya • $kcal kkal • taxminan"
        override val nutrients = "Ozuqaviy qiymat"
        override val fibre = "Tola"
        override val sugar = "Shakar"
        override val sodium = "Natriy"
        override val portion = "Porsiya"
        override val portionHint = "AI baholashi taxminiy — o'zingiz to'g'rilang"
        override val didYouEatIt = "Buni yedingizmi?"
        override val yesIAte = "Ha, yedim"
        override val planningToEat = "Yeyishni rejalashtiryapman"

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
            HealthMetric.SLEEP_LIGHT -> "Yengil uyqu"
            HealthMetric.SLEEP_AWAKE -> "Uyg'oqlik"
            HealthMetric.SLEEP_PERFORMANCE -> "Uyqu samarasi"
            HealthMetric.SLEEP_EFFICIENCY -> "Uyqu samaradorligi"
            HealthMetric.RECOVERY -> "Tiklanish"
            HealthMetric.STRAIN -> "Yuklama"
            HealthMetric.SPO2 -> "SpO₂"
            HealthMetric.SKIN_TEMPERATURE -> "Teri harorati"
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
        override val balanceCapsWord = "BALANS"
        override fun articleKind(kind: ArticleKind) = when (kind) {
            ArticleKind.ARTICLE -> "MAQOLA"
            ArticleKind.COURSE -> "KURS"
            ArticleKind.VIDEO -> "VIDEO"
        }
        override val featureCaps = "IMKONIYAT"
        override val freeCaps = "BEPUL"
        override val premiumCapsBadge = "PREMIUM"
        override val journalCardTitle = "Jurnal"
        override val moodLabel = "Kayfiyat"
        override val allDoneToday = "Bugungi hamma narsa bajarildi 🌸"
    }

    override val rewards = object : RewardStrings {
        override val coinName = "Gul"
        override fun coins(amount: String) = "$amount gul"
        override fun coinsGained(amount: String) = "+$amount gul"

        override fun streakDays(days: Int) = "$days kun ketma-ket"
        override val streakStarted = "Streak boshlandi"
        override val streakSubtitle = "Bugun ham keldingiz 🌸"
        override fun milestoneReached(days: Int) = "$days kun! 🎉"
        override fun daysToMilestone(days: Int, milestone: Int) =
            "Yana $days kun — $milestone kunlik bosqich"
        override val streakBeyondMilestones = "Barcha bosqichlar ortda qoldi"

        override val walletTitle = "Gul hamyoni"
        override val balance = "Balans"
        override val earned = "Yig'ilgan"
        override val spent = "Sarflangan"
        override val currentStreak = "Joriy streak"
        override val longestStreak = "Eng uzun"
        override fun days(count: Int) = "$count kun"
        override val history = "Harakatlar"
        override val historyEmpty = "Hozircha harakat yo'q. Ilovadan foydalansangiz, gul yig'iladi."
        override val howToEarn = "Gul qanday yig'iladi"
        override fun perDay(times: Int) = "kuniga $times martagacha"
        override fun earnReason(reason: String) = when (reason) {
            CoinReasons.DAILY_OPEN -> "Kunda birinchi kirish"
            CoinReasons.STREAK_MILESTONE -> "Streak bosqichi"
            CoinReasons.CHECK_IN -> "Kayfiyatni belgilash"
            CoinReasons.WATER_GOAL -> "Suv maqsadiga yetish"
            CoinReasons.DOSE_TAKEN -> "Dori qabulini tasdiqlash"
            CoinReasons.MEAL_LOGGED -> "Ovqat qo'shish"
            CoinReasons.JOURNAL_ENTRY -> "Kundalikka yozuv"
            CoinReasons.PRACTICE -> "Nafas yoki meditatsiya"
            CoinReasons.ARTICLE_READ -> "Maqolani o'qish"
            CoinReasons.REFERRAL_JOINED -> "Do'st taklif bo'yicha qo'shildi"
            CoinReasons.REFERRAL_WELCOME -> "Taklif kodi bilan kelish"
            CoinReasons.REDEMPTION -> "Do'kondan xarid"
            CoinReasons.ADMIN_ADJUSTMENT -> "Qo'lda o'zgartirish"
            else -> reason
        }
        override val openShop = "Gul do'koni"
        override val inviteFriends = "Taklif qilish"

        override val referralTitle = "Do'stlarni taklif qiling"
        override val referralSubtitle =
            "Havolangiz orqali kelgan har bir do'st uchun ikkalangiz ham gul olasiz."
        override val yourCode = "Sizning kodingiz"
        override val copyCode = "Nusxalash"
        override val codeCopied = "Kod nusxalandi"
        override val shareLink = "Havolani ulashish"
        override fun shareMessage(link: String) =
            "SADORA — ayollar salomatligi ilovasi. Mening taklif havolam orqali qo'shiling: $link"
        override fun invitedCount(count: Int) = "$count ta do'st qo'shildi"
        override fun referralEarned(amount: String) = "Taklifdan $amount gul"
        override fun rewardPerJoin(amount: String) = "Har bir do'st uchun $amount gul"
        override fun welcomeReward(amount: String) = "Do'stingiz $amount gul bilan boshlaydi"
        override val referralHowTitle = "Qanday ishlaydi"
        override val referralSteps = listOf(
            "Havolani do'stingizga yuboring",
            "U ilovani o'rnatib, ro'yxatdan o'tadi",
            "Gul ikkalangizga ham tushadi",
        )
        override val referralFairUse =
            "Har bir kod bir marta — faqat yangi hisob uchun ishlaydi. O'z kodingiz o'zingizga tushmaydi."
    }

    override val shop = object : ShopStrings {
        override val title = "Gul do'koni"
        override val subtitle = "Yig'gan gulingizni Premium, vitamin va qurilmalarga almashtiring"
        override fun tab(kind: ShopKind) = when (kind) {
            ShopKind.PREMIUM -> "Premium"
            ShopKind.VITAMIN -> "Vitaminlar"
            ShopKind.DEVICE -> "Qurilmalar"
        }
        override val empty = "Bu bo'limda hozircha mahsulot yo'q"
        override val loading = "Do'kon yuklanmoqda…"

        override fun discount(percent: Int) = "$percent% chegirma"
        override fun priceWas(price: String) = price
        override fun priceNow(price: String) = price
        override fun saving(amount: String) = "$amount tejaysiz"
        override fun premiumDays(days: Int) = "$days kun Premium"
        override val outOfStock = "Tugadi"
        override fun stockLeft(count: Int) = "$count ta qoldi"
        override val notEnough = "Gul yetarli emas"
        override fun shortBy(amount: String) = "Yana $amount gul kerak"

        override val redeem = "Almashtirish"
        override val redeeming = "Bajarilmoqda…"
        override fun confirmTitle(product: String) = product
        override fun confirmBody(cost: String) =
            "$cost gul yechiladi va sizga chegirma kodi beriladi."
        override val confirmPremiumBody = "Gul yechiladi va Premium darhol ochiladi."
        override val cancel = "Bekor qilish"

        override val issuedTitle = "Kodingiz tayyor"
        override val issuedPremiumTitle = "Premium ochildi 🎉"
        override val issuedBody = "Kodni sotuvchiga ko'rsating — chegirma o'sha yerda qo'llanadi."
        override val issuedPremiumBody = "Obunangiz yangilandi. Hammasi shu zahoti ochiq."
        override val yourCode = "Chegirma kodi"
        override val copyCode = "Nusxalash"
        override val codeCopied = "Kod nusxalandi"
        override fun validUntil(date: String) = "$date gacha amal qiladi"
        override val myCodes = "Mening kodlarim"
        override val myCodesEmpty = "Hozircha kod yo'q"
        override val statusIssued = "Faol"
        override val statusUsed = "Ishlatilgan"
        override val statusExpired = "Muddati tugagan"
        override val statusCancelled = "Bekor qilingan"

        override val partnerNote =
            "Vitamin va qurilmalar hamkorlarnikida sotiladi. SADORA chegirma kodini beradi, " +
                "mahsulotni sotmaydi va yetkazib bermaydi. Qo'shimcha qabul qilishdan oldin " +
                "shifokor yoki farmatsevt bilan maslahatlashing."
    }

    override val homeLayout = object : HomeLayoutStrings {
        override val title = "Bosh ekran"
        override val subtitle = "Qaysi bloklar ko'rinsin va qanday tartibda tursin — o'zingiz tanlaysiz."
        override val visible = "Ko'rinadi"
        override val hidden = "Yashirilgan"
        override val moveUp = "Yuqoriga"
        override val moveDown = "Pastga"
        override val reset = "Standart tartibga qaytarish"
        override val alwaysOn = "Doim ko'rinadi"
        override fun widget(key: String) = when (key) {
            HomeWidgets.AI -> "AI xulosasi"
            HomeWidgets.SCORE -> "Salomatlik ko'rsatkichi"
            HomeWidgets.STREAK -> "Streak va gul"
            HomeWidgets.STAGE -> "Sikl / bosqich"
            HomeWidgets.PLAN -> "Bugungi reja"
            HomeWidgets.SLEEP -> "Uyqu"
            HomeWidgets.MEDICATIONS -> "Dorilar"
            HomeWidgets.INSIGHTS -> "Tahlillar"
            HomeWidgets.KNOWLEDGE -> "Bilim"
            HomeWidgets.QUICK_ACTIONS -> "Tezkor amallar"
            HomeWidgets.SUMMARY -> "Bugungi xulosa"
            else -> key
        }
        override fun widgetNote(key: String) = when (key) {
            HomeWidgets.AI -> "Kunning qisqacha tahlili"
            HomeWidgets.SCORE -> "Uyqu, kayfiyat, suv va qadam"
            HomeWidgets.STREAK -> "Ketma-ket kunlar va balans"
            HomeWidgets.STAGE -> "Sikl kuni yoki hafta"
            HomeWidgets.PLAN -> "Dorilar va suv"
            HomeWidgets.SLEEP -> "Kechagi uyqu"
            HomeWidgets.MEDICATIONS -> "Bugungi qabullar"
            HomeWidgets.INSIGHTS -> "Oxirgi topilma"
            HomeWidgets.KNOWLEDGE -> "Siz uchun maqola"
            HomeWidgets.QUICK_ACTIONS -> "To'rtta yorliq"
            HomeWidgets.SUMMARY -> "Raqamlar bo'yicha qisqacha"
            else -> ""
        }
    }

    override val share = object : ShareStrings {
        override val title = "Shifokorga ko'rsatish"
        override val subtitle = "QR kod orqali yozuvlaringiz shifokor ekranida ochiladi"
        override val intro = "Qabulda telefoningizdagi QR kodni ko'rsating. Shifokor uni kamerasi bilan skanerlaydi va " +
            "sikl, simptomlar, kayfiyat, dorilar, ko'riklar va qurilma ko'rsatkichlarini bitta sahifada ko'radi. " +
            "Havola vaqtinchalik — o'zingiz tanlagan muddatdan keyin o'chadi."
        override val create = "QR kod yaratish"
        override val creating = "Tayyorlanmoqda…"
        override val regenerate = "Yangi kod"
        override val revoke = "O'chirish"
        override val revoked = "Havola o'chirildi"
        override val copyLink = "Havolani nusxalash"
        override val linkCopied = "Havola nusxalandi"
        override val shareLink = "Yuborish"
        override fun shareMessage(link: String) = "SADORA — mening salomatlik yozuvlarim (vaqtinchalik havola): $link"
        override val showToDoctor = "Shifokorga shu kodni ko'rsating"
        override val validFor = "Amal qilish muddati"
        override fun hours(count: Int) = "$count soat"
        override fun days(count: Int) = "$count kun"
        override fun expiresAt(at: String) = "$at gacha amal qiladi"
        override val expired = "Muddati tugagan"
        override fun viewedTimes(count: Int) = "$count marta ochilgan"
        override val neverViewed = "Hali ochilmagan"
        override fun lastViewed(ago: String) = "Oxirgi marta $ago"
        override val includesTitle = "Sahifada nima bor"
        override val includes = listOf(
            "Yosh, bo'y, vazn va hayot bosqichi",
            "Sikl tarixi, oxirgi hayz va bashorat",
            "Oxirgi 90 kunlik simptomlar, kayfiyat va energiya",
            "Dorilar, jadval va qabul foizi",
            "Ko'riklar va tekshiruvlar",
            "Uyqu, puls, HRV va boshqa qurilma ko'rsatkichlari",
        )
        override val excludesTitle = "Nima ko'rsatilmaydi"
        override val excludes = listOf(
            "Kundalik yozuvlaringiz matni",
            "Homiladorlik belgilaridagi shaxsiy izohlar",
            "Chatdagi yozishmalar",
        )
        override val privacyNote = "Havolada ismingiz yoki telefoningiz yo'q — faqat tasodifiy kod. Yangi kod yaratsangiz, eskisi darhol ishlamay qoladi."
        override val offline = "QR kod yaratish uchun internet kerak"
        override val failed = "Havola yaratilmadi. Qaytadan urinib ko'ring."
    }

    override val premium = object : PremiumStrings {
        override val tab = "Premium"
        override val title = "SADORA Premium"
        override val activeTitle = "Premium faol"
        override val activeBody = "Hammasi ochiq: AI suhbat, ovqat skaneri, uzoq muddatli tahlillar va butun kutubxona."
        override val inactiveTitle = "Ko'proq tushunish uchun"
        override val inactiveBody = "Bepul rejadagi hech narsa olib tashlanmaydi. Premium — chuqurroq tahlil va AI yordamchi."
        override val benefitsTitle = "Premium nima beradi"
        override val benefitAiTitle = "AI yordamchi"
        override val benefitAiBody = "Kuniga 20 tagacha savol — sikl, uyqu va ovqatlanishingizni bilgan holda javob beradi."
        override val benefitScannerTitle = "Ovqat skaneri"
        override val benefitScannerBody = "Oyiga 30 ta surat: taomni suratga oling, kaloriya va tarkib o'zi hisoblanadi."
        override val benefitInsightsTitle = "Tahlillar tarixi"
        override val benefitInsightsBody = "30 va 90 kunlik oynalar va kuzatuvlar: nima nima bilan birga kelayotganini ko'rasiz."
        override val benefitLibraryTitle = "Butun Bilim kutubxonasi"
        override val benefitLibraryBody = "Shifokorlar tekshirgan barcha maqolalar, bosqichingizga mos."
        override val benefitDevicesTitle = "Qurilma tahlili"
        override val benefitDevicesBody = "WHOOP va boshqa qurilmalar ko'rsatkichlari sikl fazalari bilan solishtiriladi."
        override val compareTitle = "Bepul va Premium"
        override val seePlans = "Tariflarni ko'rish"
        override val manage = "Obunani boshqarish"
        override fun buyWithCoins(coinName: String) = "$coinName bilan olish"
        override val faqTitle = "Ko'p so'raladigan savollar"
        override val faq = listOf(
            "Bepul rejada nima qoladi?" to "Hammasi: sikl, kayfiyat, ovqat kundaligi, dorilar, ko'riklar va 7 kunlik tahlillar. Premium faqat qo'shadi.",
            "Istalgan vaqt bekor qila olamanmi?" to "Ha. Obuna tugagan kungacha Premium ochiq turadi, keyin bepul rejaga qaytasiz — ma'lumotlar saqlanadi.",
            "Qanday to'lanadi?" to "Payme yoki Click orqali. To'lovni server tasdiqlaydi va Premium darhol ochiladi.",
            "Gul bilan olsa bo'ladimi?" to "Ha — do'konda 7 va 30 kunlik Premium gul evaziga beriladi. Gul ilovani ochish va belgilash uchun yig'iladi.",
        )
        override val freeStays = "Bepul rejadagi hamma narsa qoladi"
    }

    override val devices = object : DeviceStrings {
        override val title = "Qurilmalar"
        override val subtitle = "Soat yoki bilaguzuk ulang — uyqu, puls va tiklanish o'zi keladi"
        override val connectedSection = "Ulangan"
        override val availableSection = "Ulash mumkin"
        override val plannedSection = "Rejada"
        override fun provider(provider: HealthProvider) = when (provider) {
            HealthProvider.APPLE_HEALTH -> "Apple Health"
            HealthProvider.HEALTH_CONNECT -> "Health Connect"
            HealthProvider.OURA -> "Oura"
            HealthProvider.GARMIN -> "Garmin"
            HealthProvider.WHOOP -> "WHOOP"
            HealthProvider.FITBIT -> "Fitbit"
            HealthProvider.SAMSUNG_HEALTH -> "Samsung Health"
            HealthProvider.MANUAL -> "Qo'lda kiritilgan"
        }
        override fun providerTagline(provider: HealthProvider) = when (provider) {
            HealthProvider.WHOOP -> "Tiklanish, yuklama, HRV va teri harorati"
            HealthProvider.APPLE_HEALTH -> "iPhone va Apple Watch"
            HealthProvider.HEALTH_CONNECT -> "Android soatlari va bilaguzuklar"
            HealthProvider.OURA -> "Uzuk: uyqu va tiklanish"
            HealthProvider.GARMIN -> "Sport soatlari"
            HealthProvider.FITBIT -> "Bilaguzuk va soatlar"
            HealthProvider.SAMSUNG_HEALTH -> "Galaxy Watch"
            HealthProvider.MANUAL -> "O'zingiz yozgan ko'rsatkichlar"
        }
        override val connect = "Ulash"
        override val connecting = "Ulanmoqda…"
        override val disconnect = "Uzish"
        override val disconnectConfirmTitle = "Qurilmani uzasizmi?"
        override val disconnectConfirmBody = "Yangi ma'lumotlar kelmay qoladi. Avval kelganlari saqlanadi."
        override val syncNow = "Yangilash"
        override val syncing = "Yangilanmoqda…"
        override val synced = "Yangilandi"
        override fun lastSync(ago: String) = "Oxirgi yangilanish: $ago"
        override val neverSynced = "Hali yangilanmagan"
        override val statusActive = "Faol"
        override val statusExpired = "Qayta ulash kerak"
        override val statusError = "Xatolik"
        override val reconnect = "Qayta ulash"
        override fun unavailable(reason: String) = when (reason) {
            "not_configured" -> "Hozircha ulanmaydi"
            "ios_only" -> "Faqat iPhone'da"
            "android_only" -> "Faqat Android'da"
            "unsupported" -> "Bu telefonda ishlamaydi"
            else -> "Tez orada"
        }
        override val givesTitle = "Nimalar keladi"
        override val usedInTitle = "Qayerda ishlatiladi"
        override fun usedIn(provider: HealthProvider) = when (provider) {
            HealthProvider.WHOOP -> listOf("Uyqu ekrani", "Bugun — salomatlik ko'rsatkichi", "Balans", "Sikl — tana signallari", "Shifokor sahifasi")
            HealthProvider.APPLE_HEALTH, HealthProvider.HEALTH_CONNECT ->
                listOf("Uyqu ekrani", "Bugun — salomatlik ko'rsatkichi", "Balans", "Sikl — hayz kunlari va tana harorati", "Tahlillar")
            else -> listOf("Uyqu ekrani", "Bugun — salomatlik ko'rsatkichi", "Balans", "Tahlillar")
        }
        override val openBrowserNote = "Brauzerda WHOOP sahifasi ochiladi. Ruxsat bergach, ilovaga qaytasiz — birinchi yuklab olish 30 kunlik va bir necha daqiqa oladi."
        override val returnedOk = "WHOOP ulandi — ma'lumotlar kelmoqda"
        override val returnedError = "WHOOP ulanmadi. Qaytadan urinib ko'ring."
        override val noStepsNote = "WHOOP qadam sanamaydi — buning o'rniga yuklama (strain) ko'rsatiladi."
        override val manualTitle = "Soat yo'qmi?"
        override val manualBody = "Uyquni Uyqu ekranidan qo'lda kiriting — Balans va tahlillar shuni ishlatadi."
        override val note = "SADORA qurilmadan faqat sanab o'tilgan ko'rsatkichlarni oladi va ularni sotmaydi. Ulanishni istalgan vaqt uzishingiz mumkin."
        override fun onDeviceNote(provider: HealthProvider) = when (provider) {
            HealthProvider.APPLE_HEALTH ->
                "Salomatlik oynasi ochiladi: SADORA o'qishi mumkin bo'lgan ko'rsatkichlarni belgilang. Ma'lumotlar ilova ochilganda yangilanadi — birinchi marta 30 kunlik ko'rsatkichlar va 6 oylik hayz kunlari olinadi."
            else ->
                "Health Connect oynasi ochiladi: ruxsat bering. Samsung Health, Mi Fitness, Zepp va boshqa ilovalar Health Connect'ga yozgan ma'lumotlar keladi. Ilova ochilganda yangilanadi."
        }
        override val installHealthConnect = "Health Connect'ni o'rnatish"
        override val healthConnectMissing = "Bu telefonda Health Connect yo'q yoki eskirgan. Play Market'dan o'rnating, so'ng shu yerga qayting."
        override fun deviceConnected(name: String) = "$name ulandi — ma'lumotlar kelmoqda"
        override val accessDenied = "Ruxsat berilmadi — hech narsa o'qilmadi"
        override fun periodsImported(count: Int) = "$count ta hayz davri qo'shildi"
        override val appleHealthManage = "Qaysi ko'rsatkichlar o'qilishini Salomatlik ilovasida o'zgartirasiz: Profil → Ilovalar → SADORA."
    }
}
