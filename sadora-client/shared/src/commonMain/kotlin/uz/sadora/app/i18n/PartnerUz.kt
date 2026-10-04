package uz.sadora.app.i18n

import uz.sadora.app.model.CyclePhase
import uz.sadora.contract.PartnerMessageKind
import uz.sadora.contract.PartnerRelation

object PartnerUz : PartnerStrings {
    override val title = "Yaqinim"
    override val profileRowNote = "Holatingizni turmush o'rtog'ingiz yoki yaqiningiz bilan ulashing"
    override val joinEntry = "Yaqinim taklif qildi"

    override val introTitle = "Yaqiningiz sizni yaxshiroq tushunsin"
    override val introBody =
        "Turmush o'rtog'ingiz, onangiz yoki dugonangiz o'z telefonida bugungi holatingizni ko'radi: " +
            "sikl kuni, hayzgacha qancha qolgani va sizga qanday yordam berishi mumkinligi. " +
            "Nimani ko'rsatishni o'zingiz tanlaysiz va istalgan payt to'xtatasiz."
    override val whoLabel = "U siz uchun kim?"
    override fun relation(relation: PartnerRelation) = when (relation) {
        PartnerRelation.HUSBAND -> "Turmush o'rtog'im"
        PartnerRelation.MOTHER -> "Onam"
        PartnerRelation.SISTER -> "Opa-singlim"
        PartnerRelation.FRIEND -> "Dugonam"
        PartnerRelation.OTHER -> "Boshqa"
    }
    override val createInvite = "Taklif kodi yaratish"
    override val creating = "Yaratilmoqda…"
    override val codeTitle = "Shu kodni yaqiningizga yuboring"
    override val codeSteps =
        "U Sadora ilovasini o'rnatadi, «Yaqinim taklif qildi»ni bosadi va kodni kiritadi. Keyin siz ruxsat berasiz."
    override fun codeExpires(date: String) = "Kod $date gacha amal qiladi"
    override val shareCode = "Kodni yuborish"
    override fun shareMessage(code: String, url: String?) =
        "Salom! Sadora ilovasida holatimni siz bilan ulashmoqchiman. Ilovani o'rnating, " +
            "«Yaqinim taklif qildi»ni bosing va shu kodni kiriting: $code" + (url?.let { "\n$it" } ?: "")
    override val inviteOut = "Taklif yuborilgan"
    override fun inviteOutBody(date: String) =
        "Kod $date gacha amal qiladi. Kodni qayta ko'rsatish uchun yangisini yarating — eskisi bekor bo'ladi."
    override val newCode = "Yangi kod"
    override val cancelInvite = "Bekor qilish"
    override fun requestTitle(name: String) = "$name holatingizni ko'rmoqchi"
    override val requestBody = "Ruxsat bersangiz, u faqat siz tanlagan narsalarni ko'radi."
    override val approve = "Ruxsat berish"
    override val decline = "Rad etish"
    override fun linkedTitle(name: String) = "$name holatingizni ko'radi"
    override fun pausedTitle(name: String) = "$name uchun to'xtatilgan"
    override fun lastViewed(ago: String) = "Oxirgi marta $ago ko'rdi"
    override val neverViewed = "Hali ochib ko'rmagan"
    override val showsTitle = "Nimani ko'rsataman"
    override val permCycle = "Sikl va hayz"
    override val permCycleNote = "Sikl kuni, faza, hayz boshlangani va keyingisi qachonligi"
    override val permFertile = "Unumdor kunlar"
    override val permFertileNote = "Ovulyatsiya va unumdor oyna"
    override val permMood = "Kayfiyat va energiya"
    override val permMoodNote = "Bugun belgilagan kayfiyatingiz"
    override val permSymptoms = "Simptomlar"
    override val permSymptomsNote = "Bugungi simptomlaringiz nomi"
    override val permPregnancy = "Homiladorlik"
    override val permPregnancyNote = "Hafta va tug'ish sanasi, keyin chaqaloq yoshi"
    override val permAppointments = "Shifokor uchrashuvlari"
    override val permAppointmentsNote = "Sana, vaqt va joy — izohingiz emas"
    override val permCare = "Bosqich parvarishi"
    override val permCareNote = "Emizishlar soni yoki issiq toshishlar"
    override val neverTitle = "Hech qachon ko'rinmaydi"
    override val neverList = listOf(
        "Shaxsiy yozuvlaringiz va kundalik",
        "Jinsiy hayot va kontratsepsiya",
        "Vazningiz",
        "Ruhiy holat testi natijasi",
        "AI suhbatlar, Chat va shifokor konsultatsiyalari",
    )
    override val pause = "Vaqtincha to'xtatish"
    override val pauseNote = "U faqat «to'xtatilgan» degan yozuvni ko'radi"
    override val disconnect = "Ulanishni uzish"
    override val disconnectConfirmTitle = "Ulanishni uzasizmi?"
    override fun disconnectConfirmBody(name: String) =
        "$name endi holatingizni ko'rmaydi. Keyinroq yangi kod bilan qayta ulash mumkin."
    override val disconnected = "Ulanish uzildi"
    override val approved = "Ruxsat berildi"
    override val followingTitle = "Men kuzatayotganlar"
    override val haveCode = "Menda kod bor"
    override val enterCodeTitle = "Taklif kodini kiriting"
    override val codeLabel = "Kod"
    override val codeHint = "K7M2-QP4X"
    override val follow = "Ulanish"
    override val statusPending = "Ruxsat kutilmoqda"
    override val statusPaused = "To'xtatilgan"
    override val statusActive = "Faol"
    override fun requestSent(name: String) = "So'rov yuborildi — $name ruxsat berishi kerak"

    override fun pendingTitle(name: String) = "$name hali ruxsat bermadi"
    override val pendingBody = "U ruxsat berishi bilan holati shu yerda ko'rinadi — sizga xabar beramiz."
    override fun pausedViewTitle(name: String) = "$name ulashishni vaqtincha to'xtatdi"
    override val pausedViewBody = "U qayta yoqsa, holati yana shu yerda ko'rinadi."
    override fun nothingShared(name: String) = "$name hozircha hech narsa ulashmagan"
    override val todayHeading = "Bugun"
    override fun cycleDay(day: Int) = "Siklning $day-kuni"
    override fun periodDay(day: Int) = "Hayzning $day-kuni"
    override fun periodIn(days: Int) = when {
        days <= 0 -> "Hayz bugun kutilmoqda"
        days == 1 -> "Hayz ertaga kutilmoqda"
        else -> "Hayzgacha $days kun"
    }
    override fun periodAround(date: String) = "Taxminan $date"
    override fun phaseTitle(phase: CyclePhase) = when (phase) {
        CyclePhase.Period -> "Hayz kunlari"
        CyclePhase.Follicular -> "Energiya ko'tarilmoqda"
        CyclePhase.Fertile -> "Eng yuqori energiya"
        CyclePhase.Luteal -> "Hayzdan oldingi kunlar"
    }
    override fun phaseFeel(phase: CyclePhase) = when (phase) {
        CyclePhase.Period -> "Charchoq, qorin og'rig'i va kayfiyat pasayishi bo'lishi mumkin."
        CyclePhase.Follicular -> "Kuch qaytadi, kayfiyat yaxshilanadi, yangi ishlarga ishtiyoq paydo bo'ladi."
        CyclePhase.Fertile -> "O'ziga ishonch va energiya eng yuqori cho'qqida."
        CyclePhase.Luteal -> "Charchoq, ta'sirchanlik, shishish va shirinlikka ishtaha bo'lishi mumkin."
    }
    override fun phaseTips(phase: CyclePhase) = when (phase) {
        CyclePhase.Period -> listOf(
            "Issiq choy yoki grelka taklif qiling",
            "Uy ishlarining bir qismini o'z zimmangizga oling",
            "Sabrli bo'ling — kayfiyat o'zgarishi tabiiy",
            "Sevimli taomi yoki shokolad yoqimli syurpriz bo'ladi",
        )
        CyclePhase.Follicular -> listOf(
            "Birga sayr yoki sport rejalang",
            "Yangi narsani birga sinab ko'rish uchun yaxshi vaqt",
            "Uning rejalari va g'oyalarini qo'llab-quvvatlang",
        )
        CyclePhase.Fertile -> listOf(
            "Birga vaqt o'tkazing — uchrashuv yoki kechki ovqat",
            "Unga e'tibor va iliq so'zlar ayting",
        )
        CyclePhase.Luteal -> listOf(
            "Ko'proq tinglang, kamroq maslahat bering",
            "Tinch kechqurun va yaxshi uyqu uchun sharoit yarating",
            "Kichik g'amxo'rlik katta ahamiyatga ega",
            "Jahl chiqsa, buni shaxsan qabul qilmang",
        )
    }
    override fun fertileWindow(from: String, to: String) = "Unumdor kunlar: $from – $to"
    override val fertileToday = "Bugun unumdor kun"
    override val estimatedNote = "Sanalar taxminiy: ular uning yozuvlariga asoslangan."
    override val moodLabel = "Kayfiyati"
    override val energyLabel = "Energiyasi"
    override val symptomsLabel = "Bugun sezayotgani"
    override fun pregnancyWeek(week: Int) = "Homiladorlikning $week-haftasi"
    override fun daysToGo(days: Int) = if (days <= 0) "Tug'ish sanasi yetib keldi" else "Tug'ilishga $days kun qoldi"
    override fun babySize(fruit: String) = "Chaqaloq hozir $fruit kattaligida"
    override fun pregnancyTips(week: Int) = when {
        week <= 13 -> listOf(
            "Ko'ngil aynishi va charchoq tabiiy — dam olishiga imkon bering",
            "Hidi kuchli taomlarni uydan uzoqroq tuting",
            "Shifokor ko'riklariga birga boring",
        )
        week <= 27 -> listOf(
            "Chaqaloq xonasi va kerakli narsalarni birga rejalang",
            "Kechki sayrlar unga ham, chaqaloqqa ham foydali",
            "Bel og'rig'i bo'lsa, yengil massaj taklif qiling",
        )
        else -> listOf(
            "Tug'ruqxona sumkasini tayyorlab qo'ying",
            "Telefoningiz doim yoqilgan va yoningizda bo'lsin",
            "Og'ir ishlarni o'zingiz bajaring — unga dam kerak",
            "Tug'ruqxonaga yo'lni oldindan aniqlab qo'ying",
        )
    }
    override fun babyAge(days: Int) = if (days < 14) "Chaqaloq $days kunlik" else "Chaqaloq ${days / 7} haftalik"
    override val postpartumTips = listOf(
        "Tunda chaqaloqqa navbat bilan qarang — unga uyqu kerak",
        "Ovqat va uy ishlarini o'z zimmangizga oling",
        "Kayfiyati tushsa, gapini tinglang va yolg'iz qoldirmang",
        "Mehmonlarni cheklang — tiklanish vaqt talab qiladi",
    )
    override val menopauseTips = listOf(
        "Xonani salqin tuting — issiq toshishlar yengilroq o'tadi",
        "Uyqusi buzilsa, tinch kechqurun uchun sharoit yarating",
        "Kayfiyat o'zgarishlariga sabr bilan qarang",
        "Birga sayr va harakat ikkovingizga ham foydali",
    )
    override val appointmentsTitle = "Shifokor uchrashuvlari"
    override fun feedsToday(count: Int) = "Bugun $count marta emizdi"
    override fun lastFeed(ago: String) = "Oxirgisi $ago"
    override fun hotFlushesToday(count: Int) = "Bugun $count ta issiq toshish"
    override val helpTitle = "Bugun unga nima yordam beradi"
    override val leave = "Kuzatishni to'xtatish"
    override fun leaveConfirmBody(name: String) =
        "$name holati endi sizga ko'rinmaydi. Qayta ulanish uchun yangi kod kerak bo'ladi."

    override val emptyFollowingTitle = "Hali hech kimni kuzatmayapsiz"
    override val emptyFollowingBody = "Yaqiningiz yuborgan taklif kodini kiriting."
    override val settingsTitle = "Sozlamalar"
    override val followAnother = "Yana bir kod kiritish"

    override val joinTitle = "Yaqiningiz yuborgan kodni kiriting"
    override val joinSubtitle = "Kod 8 ta belgidan iborat, masalan K7M2-QP4X"
    override val yourNameLabel = "Ismingiz"
    override val yourNameHint = "Masalan, Aziz"
    override val yourNameNote = "U so'rovda shu ismni ko'radi"
    override val joinTermsLead = "Davom etib, quyidagilarga rozilik bildirasiz:"
    override val termsLink = "Foydalanish shartlari"
    override val privacyLink = "Maxfiylik siyosati"

    override val labourButton = "Yaqinimga xabar berish: tug'ruq boshlandi"
    override val labourConfirmTitle = "Yaqiningizga xabar yuborilsinmi?"
    override val labourConfirmBody = "U darhol «Tug'ruq boshlandi!» degan xabar oladi."
    override val labourSend = "Yuborish"
    override val labourSent = "Xabar yuborildi"

    override val messagesTitle = "Xabarlar"
    override fun sendTo(name: String) = "$name uchun"
    override fun askFrom(name: String) = "${name}dan so'rash"
    override fun kind(kind: PartnerMessageKind) = when (kind) {
        PartnerMessageKind.HEART -> "❤️ Yurak"
        PartnerMessageKind.HUG -> "🤗 Quchoq"
        PartnerMessageKind.THINKING -> "💭 Sizni o'ylayapman"
        PartnerMessageKind.ON_IT -> "🏃 Hozir!"
        PartnerMessageKind.DONE -> "✓ Bajardim"
        PartnerMessageKind.TEA -> "☕ Issiq choy"
        PartnerMessageKind.SWEETS -> "🍫 Shirinlik"
        PartnerMessageKind.REST -> "😴 Dam olishim kerak"
        PartnerMessageKind.CALL -> "📞 Qo'ng'iroq qiling"
        PartnerMessageKind.QUIET -> "🤫 Biroz tinchlik"
        PartnerMessageKind.CUSTOM -> "✍️ O'z so'zim"
    }
    override val youPrefix = "Siz"
    override fun asked(name: String) = "$name so'radi"
    override val noMessages = "Hali xabar yo'q — birinchisini yuboring"
    override val customTitle = "Xabar yozing"
    override val customHint = "Masalan, kechqurun birga sayr qilamizmi?"
    override val send = "Yuborish"
    override val sent = "Yuborildi"
    override fun unreadCount(count: Int) = "$count ta yangi xabar"

    override val webTitle = "Ilovasi yo'qlar uchun havola"
    override val webBody =
        "Ilova o'rnatmaydigan yaqiningiz holatingizni brauzerda ko'radi. Havola bir necha kun ishlaydi va istalgan payt bekor qilinadi."
    override val webShows = "Havola ko'rsatadi:"
    override val webCreate = "Havola yaratish"
    override fun webExpires(date: String) = "$date gacha ishlaydi"
    override fun webViews(count: Int) = "$count marta ochildi"
    override val webNever = "Hali ochilmagan"
    override val webShare = "Havolani yuborish"
    override val webRevoke = "Bekor qilish"
    override val webNew = "Yangi havola"
    override fun webShareMessage(url: String) = "Holatimni shu havolada ko'rishingiz mumkin (Sadora):\n$url"
    override val webRevoked = "Havola bekor qilindi"
    override fun webDays(days: Int) = "$days kun"
    override val webOutBody = "Havolani qayta ko'rsatish uchun yangisini yarating — eskisi ishlamay qoladi."
}
