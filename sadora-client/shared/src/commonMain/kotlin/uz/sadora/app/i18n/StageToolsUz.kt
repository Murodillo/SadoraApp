package uz.sadora.app.i18n

import uz.sadora.contract.FeedingSide
import uz.sadora.contract.GoalsBasis
import uz.sadora.contract.HotFlushTrigger

object StageToolsUz : StageToolsStrings {

    override val fertileTodayTitle = "Bugun unumdor kunlar"
    override fun fertileTodayBody(ovulation: String) =
        "Ovulyatsiya taxminan $ovulation. Shu kunlarda homilador bo'lish ehtimoli eng yuqori."
    override fun fertileInDays(days: Int) = if (days == 1) "Unumdor oyna ertaga" else "Unumdor oyna $days kundan keyin"
    override fun fertileWindow(from: String, to: String, ovulation: String) =
        "$from – $to, ovulyatsiya taxminan $ovulation"
    override val fertilePassedTitle = "Unumdor oyna o'tdi"
    override fun fertilePassedBody(nextPeriod: String) =
        "Keyingi hayz $nextPeriod atrofida kutiladi. Kechiksa, homiladorlik testini qiling."
    override fun periodLate(days: Int) = "Hayz $days kun kechikdi"
    override val periodLateBody =
        "Homiladorlik testini qilish vaqti — ertalabki birinchi siydikda natija aniqroq bo'ladi."
    override val periodLateCycleBody =
        "Kechikish stress, kasallik yoki homiladorlik sababli bo'lishi mumkin. Bir haftadan oshsa, test qiling yoki shifokorga murojaat qiling."
    override val pregnantButton = "Homilador bo'ldim"
    override val periodStartedButton = "Hayz boshlandi"
    override fun dueFromPeriod(date: String) = "Oxirgi hayzdan hisoblash: $date"

    override val birthPromptTitle = "Farzandingiz tug'ildimi?"
    override val birthPromptBody =
        "Tug'ruqdan keyingi bosqichga o'tsangiz, tiklanish, emizish va kayfiyat kuzatuvi ochiladi."
    override val birthPromptButton = "Tug'ruqdan keyingi bosqichga o'tish"
    override val cycleBackTitle = "Sikl kuzatuviga qaytasizmi?"
    override val cycleBackBody =
        "Hayz qaytgan bo'lsa, sikl kuzatuvi keyingi hayzni va unumdor kunlarni hisoblaydi."
    override val cycleBackButton = "Sikl kuzatuviga o'tish"

    override val flagsTitle = "Shifokor bilan gaplashishga arziydi"
    override fun shortCycles(days: Int) = "Sikllaringiz o'rtacha $days kun — 21 kundan qisqa."
    override fun longCycles(days: Int) = "Sikllaringiz o'rtacha $days kun — 35 kundan uzun."
    override fun irregularCycles(spread: Int) = "Sikllaringiz uzunligi bir-biridan $spread kungacha farq qiladi."
    override fun longPeriods(days: Int) = "Hayz o'rtacha $days kun davom etadi — 7 kundan uzun."
    override val bleedingAfterMenopause =
        "So'nggi 12 oyda qon ketish qayd etilgan. Menopauzada har qanday qon ketishni shifokor ko'rishi kerak."
    override val flagsNote = "Bu tashxis emas — yozuvlaringizda ko'ringan belgi."
    override val menopauseBleedingTitle = "Qon ketish"
    override val menopauseBleedingBody =
        "Menopauzadan keyin qon ketsa, oz bo'lsa ham, shifokorga ko'rining. Shu kunni belgilab qo'ying."
    override val menopauseBleedingButton = "Qon ketishni belgilash"

    override fun goalsBasis(basis: GoalsBasis) = when (basis) {
        GoalsBasis.PREGNANCY_FIRST_TRIMESTER -> "Maqsadlar homiladorlikning 1-trimestriga moslangan"
        GoalsBasis.PREGNANCY_SECOND_TRIMESTER -> "Maqsadlar homiladorlikning 2-trimestriga moslangan"
        GoalsBasis.PREGNANCY_THIRD_TRIMESTER -> "Maqsadlar homiladorlikning 3-trimestriga moslangan"
        GoalsBasis.BREASTFEEDING -> "Maqsadlar emizish davriga moslangan — emizmayotgan bo'lsangiz, o'zgartiring"
    }

    override val feedingTitle = "Emizish"
    override val feedingIntro = "Qaysi tomondan emizayotganingizni tanlang — vaqt o'zi hisoblanadi."
    override fun side(side: FeedingSide) = when (side) {
        FeedingSide.LEFT -> "Chap"
        FeedingSide.RIGHT -> "O'ng"
        FeedingSide.BOTTLE -> "Shisha"
        FeedingSide.PUMP -> "Sog'ib olingan"
    }
    override fun lastFeed(ago: String, side: String) = "Oxirgisi $ago oldin · $side"
    override fun feedsToday(count: Int) = "Bugun $count marta"
    override val noFeedsToday = "Bugun hali qayd etilmagan"
    override val startFeed = "Boshlash"
    override val stopFeed = "Tugatish"
    override fun feedRunning(side: String) = "$side tomondan emizilmoqda"
    override val bottleTitle = "Shisha yoki sog'ib olingan sut"
    override val millilitres = "ml"
    override val saveBottle = "Saqlash"
    override val nothingToday = "Bugun hali hech narsa yo'q."

    override val screenTitle = "Kayfiyat so'rovnomasi"
    override val screenCardTitle = "Kayfiyatni tekshirish"
    override val screenCardBody =
        "10 ta savol, 2 daqiqa. Tug'ruqdan keyingi tushkunlikni erta sezishga yordam beradi."
    override fun lastScreen(date: String, score: Int) = "Oxirgi marta $date · ball $score"
    override val screenIntro =
        "So'nggi 7 kun ichida o'zingizni qanday his qildingiz? Har bir savolga eng yaqin javobni tanlang."
    override val questions = listOf(
        "Kulish va narsalarning kulgili tomonini ko'ra olish",
        "Biror narsani zavq bilan kutish",
        "Ishlar yomon ketganda o'zimni behuda ayblash",
        "Sababsiz xavotirlanish yoki tashvishlanish",
        "Sababsiz qo'rquv yoki vahima",
        "Ishlar ustimdan bosib ketayotgandek tuyulishi",
        "Shunchalik baxtsizmanki, uxlashim qiyinlashdi",
        "O'zimni g'amgin yoki baxtsiz his qildim",
        "Shunchalik baxtsizmanki, yig'lab yubordim",
        "O'zimga zarar yetkazish fikri xayolimga keldi",
    )
    override val options = listOf(
        listOf("Har doimgidek", "Hozir unchalik emas", "Avvalgidan ancha kam", "Umuman yo'q"),
        listOf("Har doimgidek", "Avvalgidan biroz kam", "Avvalgidan ancha kam", "Deyarli yo'q"),
        listOf("Ha, ko'pincha", "Ha, ba'zan", "Unchalik emas", "Yo'q, hech qachon"),
        listOf("Yo'q, umuman", "Deyarli yo'q", "Ha, ba'zan", "Ha, juda tez-tez"),
        listOf("Ha, ancha ko'p", "Ha, ba'zan", "Yo'q, unchalik emas", "Yo'q, umuman"),
        listOf(
            "Ha, ko'pincha umuman uddalay olmadim",
            "Ha, ba'zan odatdagidek uddalay olmadim",
            "Yo'q, ko'pincha yaxshi uddaladim",
            "Yo'q, har doimgidek uddaladim",
        ),
        listOf("Ha, ko'pincha", "Ha, ba'zan", "Unchalik emas", "Yo'q, umuman"),
        listOf("Ha, ko'pincha", "Ha, ancha tez-tez", "Unchalik emas", "Yo'q, umuman"),
        listOf("Ha, ko'pincha", "Ha, ancha tez-tez", "Faqat ba'zan", "Yo'q, hech qachon"),
        listOf("Ha, ancha tez-tez", "Ba'zan", "Deyarli yo'q", "Hech qachon"),
    )
    override val screenSubmit = "Natijani ko'rish"
    override fun screenScore(score: Int) = "Ball: $score / 30"
    override val screenLow =
        "Hozircha tushkunlik belgilari kam. Kayfiyatingiz o'zgarsa, 2–4 haftadan keyin yana o'tib ko'ring."
    override val screenPossible =
        "Tushkunlik belgilari bo'lishi mumkin. Shifokoringiz bilan gaplashing va 2 haftadan keyin qayta o'ting."
    override val screenLikely =
        "Tug'ruqdan keyingi depressiya ehtimoli yuqori. Iloji boricha tezroq shifokor yoki psixologga murojaat qiling — bu davolanadi."
    override val screenSelfHarm =
        "Siz o'zingizga zarar yetkazish fikrlari haqida javob berdingiz. Bu jiddiy: hoziroq yaqinlaringizdan biriga ayting va shifokorga murojaat qiling. Xavf bo'lsa, 103 ga qo'ng'iroq qiling."
    override val askDoctor = "Shifokordan so'rash"
    override val screenSource =
        "Edinburg tug'ruqdan keyingi depressiya shkalasi (EPDS; Cox, Holden, Sagovsky, 1987). Tashxis emas — skrining vositasi."
    override fun answeredOf(answered: Int, total: Int) = "$answered / $total"

    override val kicksTitle = "Tepishlarni sanash"
    override val kicksCardBody = "28-haftadan boshlab kuniga bir marta: 10 ta harakat qancha vaqtda sezilishi."
    override val kicksIntro =
        "Bola faol paytda yonboshlab yoting va har bir harakatni sezganingizda tugmani bosing. Odatda 10 ta harakat 2 soat ichida seziladi."
    override val kickTap = "Harakat"
    override fun kicksCount(count: Int, goal: Int) = "$count / $goal"
    override val kicksFinish = "Tugatish va saqlash"
    override fun kicksResult(count: Int, duration: String) = "$count ta harakat · $duration"
    override val kicksSlow =
        "10 ta harakat 2 soatda sezilmadi. Kechiktirmasdan shifokoringizga yoki tug'ruqxonaga murojaat qiling."
    override val previousCounts = "Oldingi sanashlar"

    override val contractionsTitle = "To'lg'oq taymeri"
    override val contractionsCardBody = "To'lg'oqlar qancha davom etayotgani va qanchalik tez-tez kelayotganini hisoblaydi."
    override val contractionsIntro =
        "To'lg'oq boshlanganda «Boshlandi»ni, tugaganda «Tugadi»ni bosing."
    override val contractionStart = "Boshlandi"
    override val contractionStop = "Tugadi"
    override fun contractionLasted(duration: String) = "$duration davom etdi"
    override fun contractionApart(interval: String) = "oldingisidan $interval keyin"
    override fun contractionsSummary(count: Int, duration: String, interval: String) =
        "Oxirgi soatda $count ta: o'rtacha $duration davom etadi, har $interval da"
    override val contractionsGo =
        "To'lg'oqlar 5 daqiqadan tez-tez, 1 daqiqadan uzun va bir soatdan beri davom etmoqda — tug'ruqxonaga borish vaqti."
    override val contractionsUrgent =
        "Suv ketsa, qon ketsa yoki bola harakati kamaysa — to'lg'oqni kutmasdan darhol tug'ruqxonaga boring."
    override fun minutesSeconds(minutes: Int, seconds: Int) =
        if (minutes == 0) "$seconds soniya" else "$minutes daq ${seconds.toString().padStart(2, '0')} son"

    override val hotFlushTitle = "Issiqlik to'lqinlari"
    override val hotFlushLog = "Qayd etish"
    override fun intensity(level: Int) = when (level) {
        1 -> "Yengil"
        2 -> "O'rtacha"
        else -> "Kuchli"
    }
    override fun trigger(trigger: HotFlushTrigger) = when (trigger) {
        HotFlushTrigger.HEAT -> "Issiq xona"
        HotFlushTrigger.HOT_DRINK -> "Issiq ichimlik"
        HotFlushTrigger.SPICY_FOOD -> "Achchiq taom"
        HotFlushTrigger.CAFFEINE -> "Kofein"
        HotFlushTrigger.ALCOHOL -> "Spirtli ichimlik"
        HotFlushTrigger.STRESS -> "Stress"
        HotFlushTrigger.NIGHT -> "Tunda"
    }
    override val triggerQuestion = "Nima sabab bo'lgan bo'lishi mumkin?"
    override fun hotFlushCounts(today: Int, week: Int) = "Bugun $today · 7 kunda $week"
    override fun commonTrigger(name: String) = "Ko'p uchraydigan sabab: $name"
    override val hotFlushSaved = "Qayd etildi"
    override fun ago(hours: Int, minutes: Int) = if (hours == 0) "$minutes daq" else "$hours soat $minutes daq"
}
