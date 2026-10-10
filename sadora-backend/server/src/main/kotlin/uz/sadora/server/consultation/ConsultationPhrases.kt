package uz.sadora.server.consultation

import uz.sadora.contract.Language

/**
 * The pushes around a doctor — consultations, prescriptions, private messages and her
 * answers in the chat — in the language of the person they reach.
 *
 * Kept in one place, like `PartnerPhrases`, so a reviewer reads every sentence a lock
 * screen may show. None carries a health detail or a message's words: they say that
 * something happened, and the app shows the rest behind her own sign-in.
 */
internal object ConsultationPhrases {

    data class Text(val title: String, val body: String)

    /** To the doctor: a patient paid, the window is open. */
    fun paid(patient: String, hours: Int, language: Language) = when (language) {
        Language.UZ -> Text("Yangi pullik konsultatsiya", "$patient to'lov qildi — $hours soat ichida javob bering")
        Language.RU -> Text("Новая платная консультация", "$patient оплатила — ответьте в течение $hours ч")
        Language.EN -> Text("New paid consultation", "$patient has paid — please reply within $hours hours")
    }

    /** The name a patient goes by in a push when she has not given one. */
    fun patientFallback(language: Language) = when (language) {
        Language.UZ -> "Bemor"
        Language.RU -> "Пациентка"
        Language.EN -> "A patient"
    }

    /** To the patient: the doctor never answered, so the money comes back. */
    fun refundDue(language: Language) = when (language) {
        Language.UZ -> Text("Shifokor javob bermadi", "To'lovingiz 3 ish kuni ichida qaytariladi")
        Language.RU -> Text("Врач не ответил", "Оплата вернётся в течение 3 рабочих дней")
        Language.EN -> Text("The doctor didn't reply", "Your payment will be refunded within 3 working days")
    }

    /** To the patient: the window closed after an answer; [withSummary] when the doctor wrote one. */
    fun rate(withSummary: Boolean, language: Language) = when (language) {
        Language.UZ -> Text(
            if (withSummary) "Shifokor tavsiyasi tayyor" else "Konsultatsiya tugadi",
            "Shifokorga baho bering — bu boshqalarga ham yordam beradi",
        )
        Language.RU -> Text(
            if (withSummary) "Рекомендации врача готовы" else "Консультация завершена",
            "Поставьте врачу оценку — это поможет и другим",
        )
        Language.EN -> Text(
            if (withSummary) "Your doctor's advice is ready" else "Your consultation has ended",
            "Rate your doctor — it helps others too",
        )
    }

    /**
     * To the patient: the doctor cancelled a prescription. Neither the medicine nor the
     * reason is on the lock screen; both wait in the conversation.
     */
    fun prescriptionCancelled(doctor: String, language: Language) = when (language) {
        Language.UZ -> Text("Retsept bekor qilindi", "$doctor retseptni bekor qildi. Dori eslatmalari to'xtatildi — sababini ilovada ko'ring.")
        Language.RU -> Text("Рецепт отменён", "$doctor: рецепт отменён. Напоминания о лекарствах остановлены — причина в приложении.")
        Language.EN -> Text("Prescription cancelled", "$doctor cancelled the prescription. Medication reminders have stopped — see the reason in the app.")
    }

    /** A private message, a doctor's line included. Only who wrote, never what. */
    fun message(sender: String, language: Language) = when (language) {
        Language.UZ -> Text("$sender: yangi xabar", "O'qish uchun oching")
        Language.RU -> Text("$sender: новое сообщение", "Откройте, чтобы прочитать")
        Language.EN -> Text("$sender: new message", "Open to read it")
    }

    /** To the asker: a doctor answered her post in the chat. */
    fun doctorAnswered(doctor: String, language: Language) = when (language) {
        Language.UZ -> Text("Shifokor javob berdi", "$doctor savolingizga javob yozdi")
        Language.RU -> Text("Врач ответил", "$doctor: ответ на ваш вопрос")
        Language.EN -> Text("A doctor answered", "$doctor answered your question")
    }
}
