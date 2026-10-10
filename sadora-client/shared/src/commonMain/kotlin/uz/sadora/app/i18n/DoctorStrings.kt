package uz.sadora.app.i18n

import kotlin.time.Duration
import uz.sadora.app.model.DoctorSort
import uz.sadora.contract.DoctorSpecialty

/**
 * Doctors as the chat shows them: the check mark, a doctor's answer, her public page,
 * and the directory of all of them.
 * The panel a doctor applies and works from is in sadora-doctor, with its own strings.
 * Its own file, like [LegalTexts], with the three languages beside the interface so a
 * new line is added to all of them at once.
 */
interface DoctorStrings {
    fun specialty(specialty: DoctorSpecialty): String

    // ---- in the chat
    val verified: String
    val filterChip: String
    val doctorAnswer: String
    fun answeredBy(count: Int): String
    val nothingYet: String
    val nothingYetBody: String
    /** Under a doctor's answer and on her page: an answer here is not a diagnosis. */
    val disclaimer: String
    fun writingAs(name: String): String

    // ---- a doctor's page
    val profileTitle: String
    fun verifiedSince(date: String): String
    val statPosts: String
    val statAnswers: String
    val statExperience: String
    val herPosts: String
    val noPosts: String

    // ---- a consultation: writing to her, and the thread with her
    val messageDoctor: String
    val openConsultation: String
    val viewHistory: String
    /** Under the button: what a consultation costs and how long it lasts. */
    val messageDoctorNote: String
    val cannotMessage: String
    val consentTitle: String
    /**
     * Read before she writes: free for now, a day long, her real name and age are seen,
     * an answer is not a diagnosis, an emergency is 103. In that order.
     */
    val consentPoints: List<String>
    val consentConfirm: String
    val consultationOpen: String
    fun hoursLeft(hours: Int): String
    fun minutesLeft(minutes: Int): String

    /**
     * "23 soat qoldi" — whole hours while there is an hour or more, then minutes, and
     * never "0": a window that is still open has at least a minute in the words too.
     */
    fun timeLeft(remaining: Duration): String {
        val minutes = remaining.inWholeMinutes
        return if (minutes >= 60) hoursLeft((minutes / 60).toInt()) else minutesLeft(minutes.coerceAtLeast(1).toInt())
    }
    val consultationExpired: String
    val consultationClosed: String
    val consultationClosedBody: String
    val reopen: String
    /** In place of the composer while the consultation is shut. */
    val composerClosed: String
    val chipOpen: String
    val chipClosed: String
    /** At the top of a consultation thread. */
    val threadNote: String
    val doctorPage: String

    // ---- her price, her rating, her hours
    val free: String
    /** "50 000 so'm / 24 soat": what one window with her costs. */
    fun pricePerWindow(sum: String): String
    /** "50 000 so'm" on its own, large, on the pay sheet. */
    fun price(sum: String): String
    /** "★ 4,8 · 12 baho" — [rating] already formatted for the language. */
    fun ratingLabel(rating: String, count: Int): String
    /** Uzbek and Russian write "4,8"; English "4.8". */
    val decimalPoint: Char get() = ','
    /** In place of the stars while fewer than [uz.sadora.contract.Limits.DOCTOR_RATING_MIN] have rated her. */
    val newDoctor: String
    val onlineNow: String
    val busy: String
    val offlineNow: String
    /** "Keyingi: Du 09:00" — [day] is a weekday, or today / tomorrow. */
    fun nextAvailable(day: String, time: String): String
    val reviewsTitle: String
    val anonymousPatient: String
    /** Under the button for a doctor who charges. */
    fun paidNote(price: String): String
    /** The first consent point for a doctor who charges, in place of "free". */
    fun consentPaidPoint(price: String): String
    /** The consent sheet's button when a payment comes next. */
    val consentToPay: String

    // ---- paying for a consultation
    val payTitle: String
    val payWindow: String
    val payProvider: String
    val pay: String
    val payWaiting: String
    val payWaitingBody: String
    val payReopenPage: String
    val payNoProvider: String
    val paidToast: String

    // ---- the thread, after paying and after the doctor's answer
    val paidChip: String
    val summaryTitle: String
    val showMore: String
    val showLess: String
    val rateTitle: String
    val rateBody: String
    fun stars(count: Int): String
    val reviewPlaceholder: String
    val rateSend: String
    val rateThanks: String
    val refundDue: String
    val refunded: String
    /** Under a shut window whose doctor charges: a new one is paid for again. */
    val consultationClosedBodyPaid: String

    // ---- the directory: every doctor, and the ways into it
    val directoryTitle: String
    /** Beside the green dot on a card. */
    val online: String
    val fastReply: String
    /** "odatda ~15 daqiqada javob beradi" — see [replyTimeLabel] for when it turns to hours. */
    fun replyMinutes(minutes: Int): String
    fun replyHours(hours: Int): String
    fun consultations(count: Int): String
    val sortTitle: String
    fun sortLabel(sort: DoctorSort): String
    val directoryEmpty: String
    val directoryEmptyBody: String
    val filteredEmpty: String
    val filteredEmptyBody: String
    val resetFilters: String
    /** "Hammasi →" at the end of the chat's strip of doctors. */
    val seeAll: String
    /** The card on Bugun. */
    val askDoctorTitle: String
    val askDoctorBody: String
}

object DoctorStringsUz : DoctorStrings {
    override fun specialty(specialty: DoctorSpecialty) = when (specialty) {
        DoctorSpecialty.GYNECOLOGIST -> "Ginekolog"
        DoctorSpecialty.OBSTETRICIAN -> "Akusher"
        DoctorSpecialty.REPRODUCTOLOGIST -> "Reproduktolog"
        DoctorSpecialty.ENDOCRINOLOGIST -> "Endokrinolog"
        DoctorSpecialty.MAMMOLOGIST -> "Mammolog"
        DoctorSpecialty.PSYCHOLOGIST -> "Psixolog"
        DoctorSpecialty.NUTRITIONIST -> "Nutritsiolog"
        DoctorSpecialty.PEDIATRICIAN -> "Pediatr"
        DoctorSpecialty.GENERAL -> "Umumiy amaliyot shifokori"
        DoctorSpecialty.OTHER -> "Boshqa mutaxassis"
    }

    override val verified = "Tasdiqlangan shifokor"
    override val filterChip = "Shifokorlar"
    override val doctorAnswer = "Shifokor javobi"
    override fun answeredBy(count: Int) = if (count <= 1) "Shifokor javob berdi" else "$count shifokor javob berdi"
    override val nothingYet = "Hali shifokor posti yo'q"
    override val nothingYetBody = "Tasdiqlangan shifokorlar yozganda shu yerda ko'rinadi."
    override val disclaimer = "Shifokorning chatdagi javobi umumiy maslahat, tashxis emas. Shoshilinch holatda 103 ga qo'ng'iroq qiling."
    override fun writingAs(name: String) = "Siz shifokor sifatida yozasiz: $name ✓"

    override val profileTitle = "Shifokor"
    override fun verifiedSince(date: String) = "${date}dan beri tasdiqlangan"
    override val statPosts = "post"
    override val statAnswers = "javob"
    override val statExperience = "yil tajriba"
    override val herPosts = "Postlari"
    override val noPosts = "Hali post yozmagan."

    override val messageDoctor = "Shifokorga yozish"
    override val openConsultation = "Konsultatsiyani ochish"
    override val viewHistory = "Oldingi konsultatsiyani ko'rish"
    override val messageDoctorNote = "Bepul · konsultatsiya 24 soat ochiq turadi"
    override val cannotMessage = "Shifokor hozir konsultatsiya qabul qilmayapti."
    override val consentTitle = "Shifokor bilan konsultatsiya"
    override val consentPoints = listOf(
        "Bepul. Konsultatsiya 24 soat ochiq turadi — keyin yangisini ochishingiz mumkin.",
        "Bu yerda taxallus yo'q: shifokor ismingiz va yoshingizni ko'radi.",
        "Shifokorning javobi tashxis emas. To'liq tekshiruv uchun ko'rikka boring.",
        "Shoshilinch holatda kutmang — 103 ga qo'ng'iroq qiling.",
    )
    override val consentConfirm = "Tushundim, yozaman"
    override val consultationOpen = "Konsultatsiya ochiq"
    override fun hoursLeft(hours: Int) = "$hours soat qoldi"
    override fun minutesLeft(minutes: Int) = "$minutes daqiqa qoldi"
    override val consultationExpired = "Konsultatsiya muddati tugadi"
    override val consultationClosed = "Shifokor konsultatsiyani yakunladi"
    override val consultationClosedBody = "Yozishni davom ettirish uchun yangi konsultatsiya oching — yana 24 soat, bepul."
    override val reopen = "Yangi konsultatsiya ochish"
    override val composerClosed = "Konsultatsiya yopilgan — yangisini oching"
    override val chipOpen = "Ochiq"
    override val chipClosed = "Yopiq"
    override val threadNote = "Shifokor ismingiz va yoshingizni ko'radi. Javob tashxis emas; shoshilinch holatda 103 ga qo'ng'iroq qiling."
    override val doctorPage = "Shifokor sahifasi"

    override val free = "Bepul"
    override fun pricePerWindow(sum: String) = "$sum so'm / 24 soat"
    override fun price(sum: String) = "$sum so'm"
    override fun ratingLabel(rating: String, count: Int) = "★ $rating · $count baho"
    override val newDoctor = "Yangi shifokor"
    override val onlineNow = "Hozir onlayn"
    override val busy = "Band"
    override val offlineNow = "Hozir javob bermaydi"
    override fun nextAvailable(day: String, time: String) = "Keyingi: $day $time"
    override val reviewsTitle = "Baholar"
    override val anonymousPatient = "Anonim bemor"
    override fun paidNote(price: String) = "$price · to'lovdan so'ng 24 soat ochiq turadi"
    override fun consentPaidPoint(price: String) =
        "Narxi $price. To'lovdan so'ng konsultatsiya 24 soat ochiq turadi. Shifokor 24 soat ichida javob bermasa, pul 3 ish kuni ichida to'lagan usulingizga qaytadi."
    override val consentToPay = "Tushundim, to'lovga o'tish"

    override val payTitle = "Konsultatsiya uchun to'lov"
    override val payWindow = "24 soatlik konsultatsiya"
    override val payProvider = "To'lov usuli"
    override val pay = "To'lash"
    override val payWaiting = "To'lov kutilmoqda…"
    override val payWaitingBody = "To'lov sahifasida to'lang va ilovaga qayting — konsultatsiya o'zi ochiladi."
    override val payReopenPage = "To'lov sahifasini qayta ochish"
    override val payNoProvider = "Hozir to'lov qabul qilib bo'lmaydi. Keyinroq urinib ko'ring."
    override val paidToast = "To'lov qabul qilindi — konsultatsiya ochildi"

    override val paidChip = "To'langan"
    override val summaryTitle = "Shifokor tavsiyasi"
    override val showMore = "Batafsil"
    override val showLess = "Yig'ish"
    override val rateTitle = "Konsultatsiyani baholang"
    override val rateBody = "Bahoingiz anonim — boshqalarga shifokor tanlashda yordam beradi."
    override fun stars(count: Int) = "$count yulduz"
    override val reviewPlaceholder = "Fikringiz (ixtiyoriy)"
    override val rateSend = "Yuborish"
    override val rateThanks = "Rahmat! Bahoingiz qabul qilindi."
    override val refundDue = "Shifokor javob bermadi — to'lov 3 ish kuni ichida qaytariladi"
    override val refunded = "To'lov qaytarildi"
    override val consultationClosedBodyPaid = "Yozishni davom ettirish uchun yangi konsultatsiya oching — yana 24 soat, to'lovdan so'ng."

    override val directoryTitle = "Shifokorlar"
    override val online = "Onlayn"
    override val fastReply = "Tez javob beradi"
    override fun replyMinutes(minutes: Int) = "odatda ~$minutes daqiqada javob beradi"
    override fun replyHours(hours: Int) = "odatda ~$hours soatda javob beradi"
    override fun consultations(count: Int) = "$count konsultatsiya"
    override val sortTitle = "Saralash"
    override fun sortLabel(sort: DoctorSort) = when (sort) {
        DoctorSort.Recommended -> "Tavsiya etilgan"
        DoctorSort.Rating -> "Reyting"
        DoctorSort.Price -> "Narx (arzonidan)"
        DoctorSort.FastReply -> "Tez javob"
    }
    override val directoryEmpty = "Hozircha shifokorlar yo'q"
    override val directoryEmptyBody = "Tasdiqlangan shifokorlar qo'shilganda shu yerda ko'rinadi."
    override val filteredEmpty = "Bu filtrga mos shifokor yo'q"
    override val filteredEmptyBody = "Filtrni o'zgartiring yoki tozalang."
    override val resetFilters = "Filtrni tozalash"
    override val seeAll = "Hammasi"
    override val askDoctorTitle = "Shifokordan so'rang"
    override val askDoctorBody = "Tasdiqlangan shifokorlar · konsultatsiya 24 soat ochiq turadi"
}

object DoctorStringsRu : DoctorStrings {
    override fun specialty(specialty: DoctorSpecialty) = when (specialty) {
        DoctorSpecialty.GYNECOLOGIST -> "Гинеколог"
        DoctorSpecialty.OBSTETRICIAN -> "Акушер"
        DoctorSpecialty.REPRODUCTOLOGIST -> "Репродуктолог"
        DoctorSpecialty.ENDOCRINOLOGIST -> "Эндокринолог"
        DoctorSpecialty.MAMMOLOGIST -> "Маммолог"
        DoctorSpecialty.PSYCHOLOGIST -> "Психолог"
        DoctorSpecialty.NUTRITIONIST -> "Нутрициолог"
        DoctorSpecialty.PEDIATRICIAN -> "Педиатр"
        DoctorSpecialty.GENERAL -> "Врач общей практики"
        DoctorSpecialty.OTHER -> "Другой специалист"
    }

    override val verified = "Подтверждённый врач"
    override val filterChip = "Врачи"
    override val doctorAnswer = "Ответ врача"
    override fun answeredBy(count: Int) = if (count <= 1) "Врач ответил" else "Ответили врачи: $count"
    override val nothingYet = "Постов от врачей пока нет"
    override val nothingYetBody = "Когда подтверждённые врачи напишут, посты появятся здесь."
    override val disclaimer = "Ответ врача в чате — общий совет, а не диагноз. В экстренном случае звоните 103."
    override fun writingAs(name: String) = "Вы пишете как врач: $name ✓"

    override val profileTitle = "Врач"
    override fun verifiedSince(date: String) = "Подтверждён с $date"
    override val statPosts = "посты"
    override val statAnswers = "ответы"
    override val statExperience = "лет стажа"
    override val herPosts = "Посты"
    override val noPosts = "Пока нет постов."

    override val messageDoctor = "Написать врачу"
    override val openConsultation = "Открыть консультацию"
    override val viewHistory = "Посмотреть прошлую консультацию"
    override val messageDoctorNote = "Бесплатно · консультация открыта 24 часа"
    override val cannotMessage = "Врач сейчас не принимает консультации."
    override val consentTitle = "Консультация с врачом"
    override val consentPoints = listOf(
        "Бесплатно. Консультация открыта 24 часа — потом можно открыть новую.",
        "Здесь нет псевдонима: врач увидит ваше имя и возраст.",
        "Ответ врача — не диагноз. Для полного обследования запишитесь на очный визит.",
        "В экстренном случае не ждите — звоните 103.",
    )
    override val consentConfirm = "Понятно, написать"
    override val consultationOpen = "Консультация открыта"
    override fun hoursLeft(hours: Int) = "Осталось $hours ч"
    override fun minutesLeft(minutes: Int) = "Осталось $minutes мин"
    override val consultationExpired = "Время консультации истекло"
    override val consultationClosed = "Врач завершил консультацию"
    override val consultationClosedBody = "Чтобы продолжить, откройте новую консультацию — ещё 24 часа, бесплатно."
    override val reopen = "Открыть новую консультацию"
    override val composerClosed = "Консультация закрыта — откройте новую"
    override val chipOpen = "Открыта"
    override val chipClosed = "Закрыта"
    override val threadNote = "Врач видит ваше имя и возраст. Ответ — не диагноз; в экстренном случае звоните 103."
    override val doctorPage = "Страница врача"

    override val free = "Бесплатно"
    override fun pricePerWindow(sum: String) = "$sum сум / 24 часа"
    override fun price(sum: String) = "$sum сум"
    override fun ratingLabel(rating: String, count: Int) = "★ $rating · $count " + ru(count, "оценка", "оценки", "оценок")
    override val newDoctor = "Новый врач"
    override val onlineNow = "Сейчас онлайн"
    override val busy = "Перерыв"
    override val offlineNow = "Сейчас не отвечает"
    override fun nextAvailable(day: String, time: String) = "Снова на связи: $day $time"
    override val reviewsTitle = "Оценки"
    override val anonymousPatient = "Анонимная пациентка"
    override fun paidNote(price: String) = "$price · после оплаты открыта 24 часа"
    override fun consentPaidPoint(price: String) =
        "Стоимость $price. После оплаты консультация открыта 24 часа. Если врач не ответит за 24 часа, деньги вернутся тем же способом оплаты в течение 3 рабочих дней."
    override val consentToPay = "Понятно, к оплате"

    override val payTitle = "Оплата консультации"
    override val payWindow = "Консультация на 24 часа"
    override val payProvider = "Способ оплаты"
    override val pay = "Оплатить"
    override val payWaiting = "Ожидаем оплату…"
    override val payWaitingBody = "Оплатите на странице платежа и вернитесь в приложение — консультация откроется сама."
    override val payReopenPage = "Открыть страницу оплаты снова"
    override val payNoProvider = "Сейчас оплата недоступна. Попробуйте позже."
    override val paidToast = "Оплата принята — консультация открыта"

    override val paidChip = "Оплачено"
    override val summaryTitle = "Рекомендация врача"
    override val showMore = "Подробнее"
    override val showLess = "Свернуть"
    override val rateTitle = "Оцените консультацию"
    override val rateBody = "Оценка анонимна — она поможет другим выбрать врача."
    override fun stars(count: Int) = "$count " + ru(count, "звезда", "звезды", "звёзд")
    override val reviewPlaceholder = "Ваш отзыв (необязательно)"
    override val rateSend = "Отправить"
    override val rateThanks = "Спасибо! Оценка принята."
    override val refundDue = "Врач не ответил — оплата вернётся в течение 3 рабочих дней"
    override val refunded = "Оплата возвращена"
    override val consultationClosedBodyPaid = "Чтобы продолжить, откройте новую консультацию — ещё 24 часа, после оплаты."

    override val directoryTitle = "Врачи"
    override val online = "Онлайн"
    override val fastReply = "Отвечает быстро"
    override fun replyMinutes(minutes: Int) = "обычно отвечает за ~$minutes мин"
    override fun replyHours(hours: Int) = "обычно отвечает за ~$hours ч"
    override fun consultations(count: Int) = "$count " + ru(count, "консультация", "консультации", "консультаций")
    override val sortTitle = "Сортировка"
    override fun sortLabel(sort: DoctorSort) = when (sort) {
        DoctorSort.Recommended -> "Рекомендуемые"
        DoctorSort.Rating -> "По рейтингу"
        DoctorSort.Price -> "Сначала дешевле"
        DoctorSort.FastReply -> "Быстрый ответ"
    }
    override val directoryEmpty = "Пока нет врачей"
    override val directoryEmptyBody = "Когда подтверждённые врачи присоединятся, они появятся здесь."
    override val filteredEmpty = "Под этот фильтр врачей нет"
    override val filteredEmptyBody = "Измените фильтр или сбросьте его."
    override val resetFilters = "Сбросить фильтр"
    override val seeAll = "Все"
    override val askDoctorTitle = "Спросите врача"
    override val askDoctorBody = "Подтверждённые врачи · консультация открыта 24 часа"
}

object DoctorStringsEn : DoctorStrings {
    override fun specialty(specialty: DoctorSpecialty) = when (specialty) {
        DoctorSpecialty.GYNECOLOGIST -> "Gynaecologist"
        DoctorSpecialty.OBSTETRICIAN -> "Obstetrician"
        DoctorSpecialty.REPRODUCTOLOGIST -> "Fertility specialist"
        DoctorSpecialty.ENDOCRINOLOGIST -> "Endocrinologist"
        DoctorSpecialty.MAMMOLOGIST -> "Breast specialist"
        DoctorSpecialty.PSYCHOLOGIST -> "Psychologist"
        DoctorSpecialty.NUTRITIONIST -> "Nutritionist"
        DoctorSpecialty.PEDIATRICIAN -> "Paediatrician"
        DoctorSpecialty.GENERAL -> "General practitioner"
        DoctorSpecialty.OTHER -> "Other specialist"
    }

    override val verified = "Verified doctor"
    override val filterChip = "Doctors"
    override val doctorAnswer = "Doctor's answer"
    override fun answeredBy(count: Int) = if (count <= 1) "A doctor answered" else "$count doctors answered"
    override val nothingYet = "No posts from doctors yet"
    override val nothingYetBody = "When verified doctors write, their posts show up here."
    override val disclaimer = "A doctor's answer in the chat is general advice, not a diagnosis. In an emergency, call 103."
    override fun writingAs(name: String) = "You're writing as a doctor: $name ✓"

    override val profileTitle = "Doctor"
    override fun verifiedSince(date: String) = "Verified since $date"
    override val statPosts = "posts"
    override val statAnswers = "answers"
    override val statExperience = "years in practice"
    override val herPosts = "Posts"
    override val noPosts = "No posts yet."

    override val messageDoctor = "Message doctor"
    override val openConsultation = "Open consultation"
    override val viewHistory = "See earlier consultation"
    override val messageDoctorNote = "Free · a consultation stays open for 24 hours"
    override val cannotMessage = "The doctor isn't taking consultations right now."
    override val consentTitle = "A consultation with a doctor"
    override val consentPoints = listOf(
        "Free. A consultation stays open for 24 hours — after that you can open a new one.",
        "There's no alias here: the doctor sees your name and age.",
        "A doctor's answer isn't a diagnosis. For a full examination, book an in-person visit.",
        "In an emergency, don't wait — call 103.",
    )
    override val consentConfirm = "Got it, start writing"
    override val consultationOpen = "Consultation open"
    override fun hoursLeft(hours: Int) = if (hours == 1) "1 hour left" else "$hours hours left"
    override fun minutesLeft(minutes: Int) = if (minutes == 1) "1 minute left" else "$minutes minutes left"
    override val consultationExpired = "The consultation has ended"
    override val consultationClosed = "The doctor closed the consultation"
    override val consultationClosedBody = "To keep writing, open a new consultation — another 24 hours, free."
    override val reopen = "Open a new consultation"
    override val composerClosed = "The consultation is closed — open a new one"
    override val chipOpen = "Open"
    override val chipClosed = "Closed"
    override val threadNote = "The doctor sees your name and age. An answer isn't a diagnosis; in an emergency, call 103."
    override val doctorPage = "Doctor's page"

    override val free = "Free"
    override fun pricePerWindow(sum: String) = "$sum UZS / 24 hours"
    override fun price(sum: String) = "$sum UZS"
    override val decimalPoint = '.'
    override fun ratingLabel(rating: String, count: Int) = "★ $rating · " + if (count == 1) "1 rating" else "$count ratings"
    override val newDoctor = "New doctor"
    override val onlineNow = "Online now"
    override val busy = "Busy"
    override val offlineNow = "Not answering now"
    override fun nextAvailable(day: String, time: String) = "Next: $day $time"
    override val reviewsTitle = "Ratings"
    override val anonymousPatient = "Anonymous patient"
    override fun paidNote(price: String) = "$price · open for 24 hours after payment"
    override fun consentPaidPoint(price: String) =
        "It costs $price. After payment the consultation stays open for 24 hours. If the doctor doesn't answer within 24 hours, the money goes back to the way you paid within 3 working days."
    override val consentToPay = "Got it, go to payment"

    override val payTitle = "Pay for the consultation"
    override val payWindow = "A 24-hour consultation"
    override val payProvider = "Payment method"
    override val pay = "Pay"
    override val payWaiting = "Waiting for the payment…"
    override val payWaitingBody = "Pay on the payment page and come back to the app — the consultation opens by itself."
    override val payReopenPage = "Open payment page again"
    override val payNoProvider = "Payments aren't available right now. Try again later."
    override val paidToast = "Payment received — the consultation is open"

    override val paidChip = "Paid"
    override val summaryTitle = "Doctor's advice"
    override val showMore = "More"
    override val showLess = "Less"
    override val rateTitle = "Rate the consultation"
    override val rateBody = "Your rating is anonymous — it helps others choose a doctor."
    override fun stars(count: Int) = if (count == 1) "1 star" else "$count stars"
    override val reviewPlaceholder = "Your review (optional)"
    override val rateSend = "Send"
    override val rateThanks = "Thank you! Your rating was received."
    override val refundDue = "The doctor didn't answer — your payment will be refunded within 3 working days"
    override val refunded = "The payment was returned"
    override val consultationClosedBodyPaid = "To keep writing, open a new consultation — another 24 hours, after payment."

    override val directoryTitle = "Doctors"
    override val online = "Online"
    override val fastReply = "Replies fast"
    override fun replyMinutes(minutes: Int) = "usually replies in ~$minutes min"
    override fun replyHours(hours: Int) = "usually replies in ~$hours h"
    override fun consultations(count: Int) = if (count == 1) "1 consultation" else "$count consultations"
    override val sortTitle = "Sort"
    override fun sortLabel(sort: DoctorSort) = when (sort) {
        DoctorSort.Recommended -> "Recommended"
        DoctorSort.Rating -> "Rating"
        DoctorSort.Price -> "Price (lowest first)"
        DoctorSort.FastReply -> "Fastest reply"
    }
    override val directoryEmpty = "No doctors yet"
    override val directoryEmptyBody = "Verified doctors show up here once they join."
    override val filteredEmpty = "No doctors match this filter"
    override val filteredEmptyBody = "Change the filter or clear it."
    override val resetFilters = "Clear filters"
    override val seeAll = "See all"
    override val askDoctorTitle = "Ask a doctor"
    override val askDoctorBody = "Verified doctors · a consultation stays open for 24 hours"
}
