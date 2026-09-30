package uz.sadora.doctor.i18n

import uz.sadora.contract.ConsultationPayment

/**
 * Her working day: price, hours and the busy switch, her numbers and earnings, quick
 * replies, a patient's page, and the advice she leaves when she closes a consultation.
 * The three languages sit beside the interface, as the tab strings do.
 */
interface WorkStrings {
    // ---- money
    /** [amount] arrives grouped: "50 000". */
    fun som(amount: String): String
    val free: String
    fun payment(payment: ConsultationPayment): String

    // ---- price, hours, busy
    val settingsTitle: String
    val priceTitle: String
    val priceLabel: String
    val priceNote: String
    val priceTooLow: String
    val priceTooHigh: String
    fun commission(percent: Int): String
    fun youGet(amount: String): String
    val busyTitle: String
    val busyBody: String
    val hoursTitle: String
    val hoursNote: String
    /** 1 is Monday. */
    fun weekday(day: Int): String
    val dayOff: String
    val hoursFrom: String
    val hoursTo: String
    val earlier: String
    val later: String
    fun timezone(zone: String): String
    val hoursInvalid: String
    fun workDays(count: Int): String
    val noHours: String

    // ---- her numbers
    val statsTitle: String
    val statWeek: String
    val statMonth: String
    val statTotal: String
    val openNow: String
    val avgReply: String
    /** A span of minutes: "12 daq", "1 soat 5 daq". */
    fun duration(minutes: Int): String
    val unanswered: String
    val rating: String
    fun ratingValue(rating: String, count: Int): String
    val noRating: String
    val topTopics: String
    val noValue: String

    // ---- earnings
    val earningsTitle: String
    val balance: String
    val gross: String
    val commissionLine: String
    val net: String
    val paidOut: String
    val refundDue: String
    val earningsNote: String
    val linesTitle: String
    val linesEmpty: String
    val linesEmptyBody: String
    val payoutsTitle: String
    val payoutsEmpty: String
    val details: String

    // ---- quick replies
    val quickReplies: String
    val quickRepliesBody: String
    val quickRepliesEmpty: String
    val quickRepliesEmptyBody: String
    val manageReplies: String
    val addReply: String
    val editReply: String
    val replyTitle: String
    val replyTitleHint: String
    val replyBody: String
    val replyBodyHint: String
    val deleteReply: String
    val deleteReplyTitle: String
    val deleteReplyBody: String
    fun repliesMax(max: Int): String

    // ---- a patient
    val patientTitle: String
    val patientInfo: String
    val noteTitle: String
    val notePrivate: String
    val noteHint: String
    val noteSaved: String
    val historyTitle: String
    val historyEmpty: String
    /** `doctor`, `expired`, `refund`, or null while the window is open. */
    fun closedReason(reason: String?): String
    val summaryLabel: String
    val reviewLabel: String
    val recordsLabel: String
    fun recordNumber(n: Int): String

    // ---- closing with advice, and the thread's chips
    val closeSummaryLabel: String
    val closeSummaryHint: String
    val writeSummary: String
    val writeSummaryBody: String
    val summarySent: String
    val yourSummary: String
    val awaitingReply: String
}

object WorkStringsUz : WorkStrings {
    override fun som(amount: String) = "$amount so'm"
    override val free = "Bepul"
    override fun payment(payment: ConsultationPayment) = when (payment) {
        ConsultationPayment.FREE -> "Bepul"
        ConsultationPayment.PENDING -> "To'lov kutilmoqda"
        ConsultationPayment.PAID -> "To'langan"
        ConsultationPayment.REFUND_DUE -> "Qaytarilishi kerak"
        ConsultationPayment.REFUNDED -> "Qaytarildi"
    }

    override val settingsTitle = "Ish vaqti va narx"
    override val priceTitle = "Konsultatsiya narxi"
    override val priceLabel = "Narx, so'm"
    override val priceNote = "0 — konsultatsiyalaringiz bepul. Pullik narx 1 000 dan 2 000 000 so'mgacha bo'ladi."
    override val priceTooLow = "Eng kami 1 000 so'm — yoki bepul uchun 0"
    override val priceTooHigh = "Eng ko'pi 2 000 000 so'm"
    override fun commission(percent: Int) = "Sadora ulushi: $percent%"
    override fun youGet(amount: String) = "Har bir konsultatsiyadan sizga: $amount"
    override val busyTitle = "Band — hozir javob bera olmayman"
    override val busyBody = "Yoqilganda bemorlar sahifangizda sizni band deb ko'radi. Ochiq konsultatsiyalar davom etadi."
    override val hoursTitle = "Ish vaqti"
    override val hoursNote = "Bemorlar sahifangizda qachon javob berishingizni ko'radi. Birorta kun tanlanmasa, faqat «Band» holati hisobga olinadi."
    override fun weekday(day: Int) = listOf("Dushanba", "Seshanba", "Chorshanba", "Payshanba", "Juma", "Shanba", "Yakshanba")[day - 1]
    override val dayOff = "Dam olish"
    override val hoursFrom = "Boshlanishi"
    override val hoursTo = "Tugashi"
    override val earlier = "Ertaroq"
    override val later = "Kechroq"
    override fun timezone(zone: String) = "Vaqtlar $zone bo'yicha"
    override val hoursInvalid = "Har kunning tugashi boshlanishidan keyin bo'lsin"
    override fun workDays(count: Int) = "Haftada $count kun"
    override val noHours = "Ish vaqti belgilanmagan"

    override val statsTitle = "Konsultatsiyalar"
    override val statWeek = "Bu hafta"
    override val statMonth = "Bu oy"
    override val statTotal = "Jami"
    override val openNow = "Hozir ochiq"
    override val avgReply = "O'rtacha birinchi javob"
    override fun duration(minutes: Int) = if (minutes < 60) "$minutes daq" else "${minutes / 60} soat ${minutes % 60} daq"
    override val unanswered = "Javobsiz qolgan"
    override val rating = "Reyting"
    override fun ratingValue(rating: String, count: Int) = "$rating · $count ta baho"
    override val noRating = "Hali baho yo'q"
    override val topTopics = "Ko'p javob bergan mavzularingiz"
    override val noValue = "—"

    override val earningsTitle = "Daromad"
    override val balance = "To'lanishi kerak bo'lgan qoldiq"
    override val gross = "Jami tushum"
    override val commissionLine = "Sadora ulushi"
    override val net = "Sizning ulushingiz"
    override val paidOut = "Sizga o'tkazilgan"
    override val refundDue = "Bemorlarga qaytariladigan"
    override val earningsNote = "Qoldiq — Sadora sizga hali o'tkazmagan summa. Javobsiz qolgan to'lovlar bemorga qaytariladi va hisobga kirmaydi."
    override val linesTitle = "Pullik konsultatsiyalar"
    override val linesEmpty = "Hali pullik konsultatsiya yo'q"
    override val linesEmptyBody = "Narx belgilasangiz, to'langan konsultatsiyalar shu yerda ko'rinadi."
    override val payoutsTitle = "O'tkazmalar"
    override val payoutsEmpty = "Hali o'tkazma bo'lmagan"
    override val details = "Batafsil"

    override val quickReplies = "Tayyor javoblar"
    override val quickRepliesBody = "Ko'p yoziladigan javoblar. Suhbatda bittasini tanlasangiz, matn maydonga qo'yiladi — yuborishdan oldin tahrirlashingiz mumkin."
    override val quickRepliesEmpty = "Hali tayyor javob yo'q"
    override val quickRepliesEmptyBody = "Tez-tez yozadigan javobingizni bir marta saqlang — keyin suhbatda bir bosishda qo'yasiz."
    override val manageReplies = "Tayyor javoblarni boshqarish"
    override val addReply = "Javob qo'shish"
    override val editReply = "Javobni tahrirlash"
    override val replyTitle = "Sarlavha"
    override val replyTitleHint = "Masalan: Tahlil so'rash"
    override val replyBody = "Matn"
    override val replyBodyHint = "Javob matni…"
    override val deleteReply = "O'chirish"
    override val deleteReplyTitle = "Javob o'chirilsinmi?"
    override val deleteReplyBody = "Bu tayyor javob ro'yxatdan olib tashlanadi. Yuborilgan xabarlarga ta'sir qilmaydi."
    override fun repliesMax(max: Int) = "Eng ko'pi $max ta tayyor javob"

    override val patientTitle = "Bemor"
    override val patientInfo = "Bemor haqida"
    override val noteTitle = "Shaxsiy eslatma"
    override val notePrivate = "Faqat siz ko'rasiz"
    override val noteHint = "Bemor haqida o'zingiz uchun eslatma…"
    override val noteSaved = "Eslatma saqlandi"
    override val historyTitle = "Konsultatsiyalar tarixi"
    override val historyEmpty = "Hali tarix yo'q"
    override fun closedReason(reason: String?) = when (reason) {
        null -> "Ochiq"
        "doctor" -> "Siz yakunladingiz"
        "expired" -> "Vaqti tugadi"
        "refund" -> "Javobsiz qoldi"
        else -> "Yopilgan"
    }
    override val summaryLabel = "Tavsiya"
    override val reviewLabel = "Bemor bahosi"
    override val recordsLabel = "Biriktirilgan kartalar"
    override fun recordNumber(n: Int) = "Karta $n"

    override val closeSummaryLabel = "Tavsiya (bemor ko'radi)"
    override val closeSummaryHint = "Masalan: tahlil natijasi bilan yana yozing…"
    override val writeSummary = "Tavsiya yozish"
    override val writeSummaryBody = "Bemor tavsiyangizni suhbatda ko'radi va saqlab qoladi."
    override val summarySent = "Tavsiya yuborildi"
    override val yourSummary = "Tavsiyangiz"
    override val awaitingReply = "Javob kutilmoqda"
}

object WorkStringsRu : WorkStrings {
    override fun som(amount: String) = "$amount сум"
    override val free = "Бесплатно"
    override fun payment(payment: ConsultationPayment) = when (payment) {
        ConsultationPayment.FREE -> "Бесплатно"
        ConsultationPayment.PENDING -> "Ожидает оплаты"
        ConsultationPayment.PAID -> "Оплачено"
        ConsultationPayment.REFUND_DUE -> "К возврату"
        ConsultationPayment.REFUNDED -> "Возвращено"
    }

    override val settingsTitle = "Часы работы и цена"
    override val priceTitle = "Цена консультации"
    override val priceLabel = "Цена, сум"
    override val priceNote = "0 — консультации бесплатны. Платная цена — от 1 000 до 2 000 000 сум."
    override val priceTooLow = "Не меньше 1 000 сум — или 0, чтобы было бесплатно"
    override val priceTooHigh = "Не больше 2 000 000 сум"
    override fun commission(percent: Int) = "Доля Sadora: $percent%"
    override fun youGet(amount: String) = "Вам с каждой консультации: $amount"
    override val busyTitle = "Занята — сейчас не могу ответить"
    override val busyBody = "Пока включено, пациентки видят на вашей странице, что вы заняты. Открытые консультации продолжаются."
    override val hoursTitle = "Часы работы"
    override val hoursNote = "Пациентки видят на вашей странице, когда вы отвечаете. Если не выбран ни один день, учитывается только «Занята»."
    override fun weekday(day: Int) = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")[day - 1]
    override val dayOff = "Выходной"
    override val hoursFrom = "Начало"
    override val hoursTo = "Конец"
    override val earlier = "Раньше"
    override val later = "Позже"
    override fun timezone(zone: String) = "Время по $zone"
    override val hoursInvalid = "Конец дня должен быть позже начала"
    override fun workDays(count: Int) = "Дней в неделю: $count"
    override val noHours = "Часы работы не заданы"

    override val statsTitle = "Консультации"
    override val statWeek = "На этой неделе"
    override val statMonth = "В этом месяце"
    override val statTotal = "Всего"
    override val openNow = "Открыто сейчас"
    override val avgReply = "Средний первый ответ"
    override fun duration(minutes: Int) = if (minutes < 60) "$minutes мин" else "${minutes / 60} ч ${minutes % 60} мин"
    override val unanswered = "Остались без ответа"
    override val rating = "Рейтинг"
    override fun ratingValue(rating: String, count: Int) = "$rating · оценок: $count"
    override val noRating = "Оценок пока нет"
    override val topTopics = "Темы, где вы отвечаете чаще всего"
    override val noValue = "—"

    override val earningsTitle = "Доход"
    override val balance = "Остаток к выплате"
    override val gross = "Всего поступило"
    override val commissionLine = "Доля Sadora"
    override val net = "Ваша доля"
    override val paidOut = "Выплачено вам"
    override val refundDue = "Вернуть пациенткам"
    override val earningsNote = "Остаток — сумма, которую Sadora вам ещё не перевела. Оплаты без ответа возвращаются пациентке и не учитываются."
    override val linesTitle = "Платные консультации"
    override val linesEmpty = "Платных консультаций пока нет"
    override val linesEmptyBody = "Когда вы назначите цену, оплаченные консультации появятся здесь."
    override val payoutsTitle = "Выплаты"
    override val payoutsEmpty = "Выплат пока не было"
    override val details = "Подробнее"

    override val quickReplies = "Быстрые ответы"
    override val quickRepliesBody = "Ответы, которые вы пишете часто. Выберите один в чате — текст встанет в поле, и его можно поправить перед отправкой."
    override val quickRepliesEmpty = "Быстрых ответов пока нет"
    override val quickRepliesEmptyBody = "Сохраните частый ответ один раз — потом вставляйте его в чат одним нажатием."
    override val manageReplies = "Настроить быстрые ответы"
    override val addReply = "Добавить ответ"
    override val editReply = "Изменить ответ"
    override val replyTitle = "Название"
    override val replyTitleHint = "Например: Попросить анализы"
    override val replyBody = "Текст"
    override val replyBodyHint = "Текст ответа…"
    override val deleteReply = "Удалить"
    override val deleteReplyTitle = "Удалить ответ?"
    override val deleteReplyBody = "Быстрый ответ исчезнет из списка. Отправленные сообщения не изменятся."
    override fun repliesMax(max: Int) = "Не больше $max быстрых ответов"

    override val patientTitle = "Пациентка"
    override val patientInfo = "О пациентке"
    override val noteTitle = "Личная заметка"
    override val notePrivate = "Видите только вы"
    override val noteHint = "Заметка о пациентке для себя…"
    override val noteSaved = "Заметка сохранена"
    override val historyTitle = "История консультаций"
    override val historyEmpty = "Истории пока нет"
    override fun closedReason(reason: String?) = when (reason) {
        null -> "Открыта"
        "doctor" -> "Вы завершили"
        "expired" -> "Время вышло"
        "refund" -> "Осталась без ответа"
        else -> "Закрыта"
    }
    override val summaryLabel = "Рекомендация"
    override val reviewLabel = "Оценка пациентки"
    override val recordsLabel = "Прикреплённые карты"
    override fun recordNumber(n: Int) = "Карта $n"

    override val closeSummaryLabel = "Рекомендация (увидит пациентка)"
    override val closeSummaryHint = "Например: напишите снова с результатом анализа…"
    override val writeSummary = "Написать рекомендацию"
    override val writeSummaryBody = "Пациентка увидит вашу рекомендацию в чате и сохранит её."
    override val summarySent = "Рекомендация отправлена"
    override val yourSummary = "Ваша рекомендация"
    override val awaitingReply = "Ждёт ответа"
}

object WorkStringsEn : WorkStrings {
    override fun som(amount: String) = "$amount UZS"
    override val free = "Free"
    override fun payment(payment: ConsultationPayment) = when (payment) {
        ConsultationPayment.FREE -> "Free"
        ConsultationPayment.PENDING -> "Awaiting payment"
        ConsultationPayment.PAID -> "Paid"
        ConsultationPayment.REFUND_DUE -> "Refund due"
        ConsultationPayment.REFUNDED -> "Refunded"
    }

    override val settingsTitle = "Hours and price"
    override val priceTitle = "Consultation price"
    override val priceLabel = "Price, UZS"
    override val priceNote = "0 keeps your consultations free. A paid price is 1,000 to 2,000,000 UZS."
    override val priceTooLow = "At least 1,000 UZS — or 0 for free"
    override val priceTooHigh = "At most 2,000,000 UZS"
    override fun commission(percent: Int) = "Sadora's share: $percent%"
    override fun youGet(amount: String) = "You get per consultation: $amount"
    override val busyTitle = "Busy — I can't answer right now"
    override val busyBody = "While it is on, patients see on your page that you are busy. Open consultations carry on."
    override val hoursTitle = "Working hours"
    override val hoursNote = "Patients see on your page when you answer. With no day chosen, only the \"Busy\" switch counts."
    override fun weekday(day: Int) = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[day - 1]
    override val dayOff = "Day off"
    override val hoursFrom = "Starts"
    override val hoursTo = "Ends"
    override val earlier = "Earlier"
    override val later = "Later"
    override fun timezone(zone: String) = "Times in $zone"
    override val hoursInvalid = "Each day must end after it starts"
    override fun workDays(count: Int) = if (count == 1) "1 day a week" else "$count days a week"
    override val noHours = "No working hours set"

    override val statsTitle = "Consultations"
    override val statWeek = "This week"
    override val statMonth = "This month"
    override val statTotal = "In total"
    override val openNow = "Open now"
    override val avgReply = "Average first reply"
    override fun duration(minutes: Int) = if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
    override val unanswered = "Left unanswered"
    override val rating = "Rating"
    override fun ratingValue(rating: String, count: Int) = "$rating · $count ${if (count == 1) "rating" else "ratings"}"
    override val noRating = "No ratings yet"
    override val topTopics = "Topics you answer most"
    override val noValue = "—"

    override val earningsTitle = "Earnings"
    override val balance = "Balance to be paid"
    override val gross = "Total received"
    override val commissionLine = "Sadora's share"
    override val net = "Your share"
    override val paidOut = "Paid out to you"
    override val refundDue = "To refund to patients"
    override val earningsNote = "The balance is what Sadora has not paid you yet. Payments left unanswered go back to the patient and are not counted."
    override val linesTitle = "Paid consultations"
    override val linesEmpty = "No paid consultations yet"
    override val linesEmptyBody = "Once you set a price, paid consultations show up here."
    override val payoutsTitle = "Payouts"
    override val payoutsEmpty = "No payouts yet"
    override val details = "Details"

    override val quickReplies = "Quick replies"
    override val quickRepliesBody = "Answers you write often. Pick one in a chat and its text goes into the field, ready to edit before you send."
    override val quickRepliesEmpty = "No quick replies yet"
    override val quickRepliesEmptyBody = "Save an answer you write often once, then drop it into a chat with one tap."
    override val manageReplies = "Manage quick replies"
    override val addReply = "Add a reply"
    override val editReply = "Edit reply"
    override val replyTitle = "Title"
    override val replyTitleHint = "For example: Ask for tests"
    override val replyBody = "Text"
    override val replyBodyHint = "The reply's text…"
    override val deleteReply = "Delete"
    override val deleteReplyTitle = "Delete this reply?"
    override val deleteReplyBody = "The quick reply leaves the list. Messages already sent stay as they are."
    override fun repliesMax(max: Int) = "At most $max quick replies"

    override val patientTitle = "Patient"
    override val patientInfo = "About the patient"
    override val noteTitle = "Private note"
    override val notePrivate = "Only you can see this"
    override val noteHint = "A note on the patient, for yourself…"
    override val noteSaved = "Note saved"
    override val historyTitle = "Consultation history"
    override val historyEmpty = "No history yet"
    override fun closedReason(reason: String?) = when (reason) {
        null -> "Open"
        "doctor" -> "You closed it"
        "expired" -> "Time ran out"
        "refund" -> "Left unanswered"
        else -> "Closed"
    }
    override val summaryLabel = "Advice"
    override val reviewLabel = "Patient's rating"
    override val recordsLabel = "Attached records"
    override fun recordNumber(n: Int) = "Record $n"

    override val closeSummaryLabel = "Advice (the patient sees it)"
    override val closeSummaryHint = "For example: write again with the test result…"
    override val writeSummary = "Write advice"
    override val writeSummaryBody = "The patient sees your advice in the chat and keeps it."
    override val summarySent = "Advice sent"
    override val yourSummary = "Your advice"
    override val awaitingReply = "Awaiting your reply"
}
