package uz.sadora.server.payrequest

import uz.sadora.contract.BillingPeriod
import uz.sadora.contract.Language
import uz.sadora.contract.PaymentRequestKind

/**
 * The pushes a request to pay sends, in the language of the person they reach. No price
 * in any of them: a lock screen is not the place for one, the app shows it.
 */
internal object PaymentRequestPhrases {

    data class Text(val title: String, val body: String)

    /** To the person she asked. */
    fun asked(name: String, kind: PaymentRequestKind, period: BillingPeriod?, language: Language) = when (language) {
        Language.UZ -> Text(
            "$name sizdan yordam so'rayapti 💝",
            if (kind == PaymentRequestKind.PREMIUM) {
                "U Sadora Premium (${period.uz()}) olmoqchi. Ilovada ko'ring"
            } else {
                "U shifokor bilan konsultatsiya olmoqchi. Ilovada ko'ring"
            },
        )
        Language.RU -> Text(
            "$name просит вас о помощи 💝",
            if (kind == PaymentRequestKind.PREMIUM) {
                "Она хочет Sadora Premium (${period.ru()}). Посмотрите в приложении"
            } else {
                "Она хочет консультацию врача. Посмотрите в приложении"
            },
        )
        Language.EN -> Text(
            "$name is asking for your help 💝",
            if (kind == PaymentRequestKind.PREMIUM) {
                "She'd like Sadora Premium (${period.en()}). See it in the app"
            } else {
                "She'd like a doctor consultation. See it in the app"
            },
        )
    }

    /** To the same person, once, two days on. */
    fun reminder(name: String, language: Language) = when (language) {
        Language.UZ -> Text("$name so'rovi hali ochiq", "Ilovada ko'rib chiqing")
        Language.RU -> Text("Просьба от $name всё ещё ждёт", "Посмотрите в приложении")
        Language.EN -> Text("$name's request is still open", "Take a look in the app")
    }

    /** To her: it was paid. [payer] is null for a browser payer. */
    fun paid(payer: String?, kind: PaymentRequestKind, period: BillingPeriod?, language: Language) = when (language) {
        Language.UZ -> {
            val who = payer ?: "Yaqiningiz"
            if (kind == PaymentRequestKind.PREMIUM) {
                Text("$who sizga ${period.uz()} Premium sovg'a qildi 💝", "Premium ochildi")
            } else {
                Text("$who konsultatsiya uchun to'ladi 💝", "Shifokor bilan suhbat ochildi")
            }
        }
        Language.RU -> {
            val who = payer ?: "Близкий человек"
            if (kind == PaymentRequestKind.PREMIUM) {
                Text("$who подарил(а) вам Premium (${period.ru()}) 💝", "Premium уже открыт")
            } else {
                Text("$who оплатил(а) консультацию 💝", "Чат с врачом открыт")
            }
        }
        Language.EN -> {
            val who = payer ?: "Someone close to you"
            if (kind == PaymentRequestKind.PREMIUM) {
                Text("$who gave you ${period.en()} of Premium 💝", "Premium is open")
            } else {
                Text("$who paid for your consultation 💝", "Your chat with the doctor is open")
            }
        }
    }

    /** To her: a gift was refunded and its days taken back. */
    fun refunded(language: Language) = when (language) {
        Language.UZ -> Text("Sovg'a qilingan Premium bekor qilindi", "To'lov qaytarildi, sovg'a kunlari olib tashlandi")
        Language.RU -> Text("Подаренный Premium отменён", "Платёж возвращён, подаренные дни сняты")
        Language.EN -> Text("A gifted Premium was cancelled", "The payment was refunded and those days removed")
    }

    private fun BillingPeriod?.uz() = if (this == BillingPeriod.YEAR) "1 yillik" else "1 oylik"
    private fun BillingPeriod?.ru() = if (this == BillingPeriod.YEAR) "на 1 год" else "на 1 месяц"
    private fun BillingPeriod?.en() = if (this == BillingPeriod.YEAR) "a year" else "a month"
}
