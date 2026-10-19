package uz.sadora.app.i18n

/**
 * SOS. Short sentences, big verbs: this screen is read by someone frightened. Every path
 * ends at 103, and nothing claims the app replaces it.
 */
interface SosStrings {
    val title: String
    val headerLabel: String
    val emergencyTitle: String
    val call103: String
    val call112: String
    val alertTitle: String
    val alertBody: String
    val send: String
    fun recipients(names: List<String>): String
    val noRecipients: String
    val partnerTag: String
    val contactsTitle: String
    val contactsNote: String
    val name: String
    val phone: String
    val add: String
    val saved: String
    val countdownTitle: String
    val cancel: String
    val locating: String
    val sentTitle: String
    fun sentBody(sms: Int, partner: Boolean, location: Boolean): String
    val failedAll: String
    val disclaimer: String
}

object SosStringsUz : SosStrings {
    override val title = "SOS"
    override val headerLabel = "SOS — favqulodda yordam"
    override val emergencyTitle = "Favqulodda holatda"
    override val call103 = "📞 103 — Tez yordam"
    override val call112 = "112 — Yagona xizmat"
    override val alertTitle = "Yaqinlarga signal"
    override val alertBody = "Yaqinim va kontaktlaringizga SMS boradi: yordam kerakligi va joylashuvingiz (bir marta)."
    override val send = "🆘 Signal yuborish"
    override fun recipients(names: List<String>) = "Signal oladi: " + names.joinToString(", ")
    override val noRecipients = "Hali hech kim qo'shilmagan — pastda kontakt qo'shing."
    override val partnerTag = "Yaqinim"
    override val contactsTitle = "Favqulodda kontaktlar"
    override val contactsNote = "3 tagacha. Ularga faqat siz SOS bosganingizda SMS boradi."
    override val name = "Ism"
    override val phone = "Telefon"
    override val add = "Qo'shish"
    override val saved = "Saqlandi"
    override val countdownTitle = "Signal yuboriladi"
    override val cancel = "Bekor qilish"
    override val locating = "Joylashuv aniqlanmoqda…"
    override val sentTitle = "Signal yuborildi"
    override fun sentBody(sms: Int, partner: Boolean, location: Boolean) = buildString {
        append("$sms kishiga SMS yuborildi")
        if (partner) append(", Yaqinim ilovada xabardor")
        append(". ")
        append(if (location) "Joylashuvingiz qo'shildi." else "Joylashuv aniqlanmadi.")
    }
    override val failedAll = "SMS yuborilmadi. Darhol 103 ga qo'ng'iroq qiling."
    override val disclaimer = "SADORA tez yordam xizmati o'rnini bosmaydi. Hayot xavf ostida bo'lsa, avval 103 ga qo'ng'iroq qiling."
}

object SosStringsRu : SosStrings {
    override val title = "SOS"
    override val headerLabel = "SOS — экстренная помощь"
    override val emergencyTitle = "В экстренной ситуации"
    override val call103 = "📞 103 — Скорая помощь"
    override val call112 = "112 — Единая служба"
    override val alertTitle = "Сигнал близким"
    override val alertBody = "Близкому и вашим контактам придёт SMS: что нужна помощь, и где вы (один раз)."
    override val send = "🆘 Отправить сигнал"
    override fun recipients(names: List<String>) = "Сигнал получат: " + names.joinToString(", ")
    override val noRecipients = "Пока никого нет — добавьте контакт ниже."
    override val partnerTag = "Близкий"
    override val contactsTitle = "Экстренные контакты"
    override val contactsNote = "До 3 человек. SMS придёт им только когда вы нажмёте SOS."
    override val name = "Имя"
    override val phone = "Телефон"
    override val add = "Добавить"
    override val saved = "Сохранено"
    override val countdownTitle = "Сигнал будет отправлен"
    override val cancel = "Отменить"
    override val locating = "Определяем местоположение…"
    override val sentTitle = "Сигнал отправлен"
    override fun sentBody(sms: Int, partner: Boolean, location: Boolean) = buildString {
        append("SMS отправлено: $sms")
        if (partner) append(", близкий получил уведомление")
        append(". ")
        append(if (location) "Местоположение добавлено." else "Местоположение не определено.")
    }
    override val failedAll = "SMS не отправлено. Сразу звоните 103."
    override val disclaimer = "SADORA не заменяет скорую помощь. Если жизнь в опасности, сначала звоните 103."
}

object SosStringsEn : SosStrings {
    override val title = "SOS"
    override val headerLabel = "SOS — emergency help"
    override val emergencyTitle = "In an emergency"
    override val call103 = "📞 103 — Ambulance"
    override val call112 = "112 — Emergency line"
    override val alertTitle = "Alert your people"
    override val alertBody = "Your Yaqinim and contacts get an SMS: that you need help, and where you are (once)."
    override val send = "🆘 Send the alert"
    override fun recipients(names: List<String>) = "Alert goes to: " + names.joinToString(", ")
    override val noRecipients = "Nobody yet — add a contact below."
    override val partnerTag = "Yaqinim"
    override val contactsTitle = "Emergency contacts"
    override val contactsNote = "Up to 3. They get an SMS only when you press SOS."
    override val name = "Name"
    override val phone = "Phone"
    override val add = "Add"
    override val saved = "Saved"
    override val countdownTitle = "Sending the alert"
    override val cancel = "Cancel"
    override val locating = "Finding your location…"
    override val sentTitle = "Alert sent"
    override fun sentBody(sms: Int, partner: Boolean, location: Boolean) = buildString {
        append("SMS sent to $sms ${en(sms, "person", "people")}")
        if (partner) append(", Yaqinim notified in the app")
        append(". ")
        append(if (location) "Your location was included." else "Location could not be found.")
    }
    override val failedAll = "The SMS did not go. Call 103 now."
    override val disclaimer = "SADORA does not replace emergency services. If a life is at risk, call 103 first."
}
