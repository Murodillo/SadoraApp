package uz.sadora.server.partner

import uz.sadora.contract.Language
import uz.sadora.contract.PartnerMessageKind
import uz.sadora.contract.PartnerMessageKind.CALL
import uz.sadora.contract.PartnerMessageKind.CUSTOM
import uz.sadora.contract.PartnerMessageKind.DONE
import uz.sadora.contract.PartnerMessageKind.HEART
import uz.sadora.contract.PartnerMessageKind.HUG
import uz.sadora.contract.PartnerMessageKind.ON_IT
import uz.sadora.contract.PartnerMessageKind.QUIET
import uz.sadora.contract.PartnerMessageKind.REST
import uz.sadora.contract.PartnerMessageKind.SWEETS
import uz.sadora.contract.PartnerMessageKind.TEA
import uz.sadora.contract.PartnerMessageKind.THINKING

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

    /**
     * One of the small messages between them. The title is the whole message for a preset;
     * a line of their own goes in the body, as the other phone would show a text.
     */
    fun message(name: String, kind: PartnerMessageKind, text: String?, language: Language): Text {
        val title = when (language) {
            Language.UZ -> when (kind) {
                HEART -> "$name sizga ❤️ yubordi"
                HUG -> "$name sizni quchoqladi 🤗"
                THINKING -> "$name sizni o'ylayapti 💭"
                ON_IT -> "$name: Hozir! 🏃"
                DONE -> "$name: Bajarildi ✓"
                TEA -> "$name: issiq choy iltimos ☕"
                SWEETS -> "$name: shirinlik iltimos 🍫"
                REST -> "$name: bugun dam olishim kerak 😴"
                CALL -> "$name: qo'ng'iroq qiling 📞"
                QUIET -> "$name: biroz tinchlik kerak 🤫"
                CUSTOM -> "$name yozdi"
            }
            Language.RU -> when (kind) {
                HEART -> "$name отправил(а) вам ❤️"
                HUG -> "$name обнимает вас 🤗"
                THINKING -> "$name думает о вас 💭"
                ON_IT -> "$name: Уже иду! 🏃"
                DONE -> "$name: Готово ✓"
                TEA -> "$name: горячий чай, пожалуйста ☕"
                SWEETS -> "$name: что-нибудь сладкое, пожалуйста 🍫"
                REST -> "$name: мне сегодня нужен отдых 😴"
                CALL -> "$name: позвони мне 📞"
                QUIET -> "$name: мне нужно немного тишины 🤫"
                CUSTOM -> "$name пишет"
            }
            Language.EN -> when (kind) {
                HEART -> "$name sent you ❤️"
                HUG -> "$name sends you a hug 🤗"
                THINKING -> "$name is thinking of you 💭"
                ON_IT -> "$name: On my way! 🏃"
                DONE -> "$name: Done ✓"
                TEA -> "$name: a hot tea, please ☕"
                SWEETS -> "$name: something sweet, please 🍫"
                REST -> "$name: I need to rest today 😴"
                CALL -> "$name: please call me 📞"
                QUIET -> "$name: I need a little quiet 🤫"
                CUSTOM -> "$name wrote"
            }
        }
        val body = text ?: when (language) {
            Language.UZ -> "Javob berish uchun oching"
            Language.RU -> "Откройте, чтобы ответить"
            Language.EN -> "Open to reply"
        }
        return Text(title, body.take(PREVIEW))
    }

    private const val PREVIEW = 140
}
