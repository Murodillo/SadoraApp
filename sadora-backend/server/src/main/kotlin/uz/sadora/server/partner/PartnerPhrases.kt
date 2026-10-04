package uz.sadora.server.partner

import uz.sadora.contract.Language

/**
 * The pushes Yaqinim sends, in the language of the person they reach.
 *
 * Kept apart from the service so a reviewer can read every sentence one person's phone
 * may show about another in one place. None of them carries a health value: they say
 * that something happened and the app shows the rest behind the person's own sign-in.
 */
internal object PartnerPhrases {

    data class Text(val title: String, val body: String)

    /** To her: someone typed her code. */
    fun accepted(name: String, language: Language) = when (language) {
        Language.UZ -> Text("$name holatingizni ko'rmoqchi", "Ruxsat berish uchun ilovani oching")
        Language.RU -> Text("$name хочет видеть ваше состояние", "Откройте приложение, чтобы разрешить")
        Language.EN -> Text("$name wants to see how you are", "Open the app to allow it")
    }

    /** To them: she said yes. */
    fun approved(name: String, language: Language) = when (language) {
        Language.UZ -> Text("$name holatini siz bilan ulashdi", "Bugun unga nima yordam berishini ko'ring")
        Language.RU -> Text("$name делится с вами своим состоянием", "Посмотрите, чем помочь ей сегодня")
        Language.EN -> Text("$name is sharing how she is with you", "See what would help her today")
    }

    /** To them: her period started today. */
    fun periodStarted(name: String, language: Language) = when (language) {
        Language.UZ -> Text("$name: hayz boshlandi", "Bugun unga ko'proq e'tibor va iliqlik kerak bo'lishi mumkin")
        Language.RU -> Text("$name: начались месячные", "Сегодня ей может понадобиться больше внимания и тепла")
        Language.EN -> Text("$name: her period started", "She may need a little more care and warmth today")
    }

    /** To them: her period is due in a couple of days. */
    fun periodSoon(name: String, days: Int, language: Language) = when (language) {
        Language.UZ -> Text("$name: $days kundan keyin hayz", "Charchoq va kayfiyat o'zgarishi bo'lishi mumkin — sabrli bo'ling")
        Language.RU -> Text("$name: месячные через $days дн.", "Возможны усталость и перепады настроения — будьте терпеливы")
        Language.EN -> Text("$name: period in $days days", "Tiredness and mood changes are common — be patient")
    }

    /** To them: a doctor visit tomorrow. */
    fun appointmentTomorrow(name: String, title: String, language: Language) = when (language) {
        Language.UZ -> Text("$name ertaga shifokorga boradi", title)
        Language.RU -> Text("$name завтра идёт к врачу", title)
        Language.EN -> Text("$name sees a doctor tomorrow", title)
    }

    /** To them: she pressed "labour has started". */
    fun labour(name: String, language: Language) = when (language) {
        Language.UZ -> Text("$name: tug'ruq boshlandi!", "Hoziroq u bilan bog'laning")
        Language.RU -> Text("$name: начались роды!", "Свяжитесь с ней прямо сейчас")
        Language.EN -> Text("$name: labour has started!", "Get in touch with her right now")
    }
}
