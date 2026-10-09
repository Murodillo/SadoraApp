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
            when (kind) {
                PaymentRequestKind.PREMIUM -> "U Sadora Premium (${period.uz()}) olmoqchi. Ilovada ko'ring"
                PaymentRequestKind.CONSULTATION -> "U shifokor bilan konsultatsiya olmoqchi. Ilovada ko'ring"
                PaymentRequestKind.PET -> "U Humo — legendar AI hamrohni olmoqchi. Ilovada ko'ring"
                PaymentRequestKind.FRAME -> "U profil rasmi uchun yangi ramka olmoqchi. Ilovada ko'ring"
            },
        )
        Language.RU -> Text(
            "$name просит вас о помощи 💝",
            when (kind) {
                PaymentRequestKind.PREMIUM -> "Она хочет Sadora Premium (${period.ru()}). Посмотрите в приложении"
                PaymentRequestKind.CONSULTATION -> "Она хочет консультацию врача. Посмотрите в приложении"
                PaymentRequestKind.PET -> "Она хочет Хумо — легендарного AI-компаньона. Посмотрите в приложении"
                PaymentRequestKind.FRAME -> "Она хочет новую рамку для фото профиля. Посмотрите в приложении"
            },
        )
        Language.EN -> Text(
            "$name is asking for your help 💝",
            when (kind) {
                PaymentRequestKind.PREMIUM -> "She'd like Sadora Premium (${period.en()}). See it in the app"
                PaymentRequestKind.CONSULTATION -> "She'd like a doctor consultation. See it in the app"
                PaymentRequestKind.PET -> "She'd like Humo, the legendary AI companion. See it in the app"
                PaymentRequestKind.FRAME -> "She'd like a new frame for her profile photo. See it in the app"
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
            when (kind) {
                PaymentRequestKind.PREMIUM -> Text("$who sizga ${period.uz()} Premium sovg'a qildi 💝", "Premium ochildi")
                PaymentRequestKind.CONSULTATION -> Text("$who konsultatsiya uchun to'ladi 💝", "Shifokor bilan suhbat ochildi")
                PaymentRequestKind.PET -> Text("$who sizga Humo'ni sovg'a qildi 💝", "Baxt qushi endi sizniki")
                PaymentRequestKind.FRAME -> Text("$who sizga ramka sovg'a qildi 💝", "U allaqachon rasmingizda")
            }
        }
        Language.RU -> {
            val who = payer ?: "Близкий человек"
            when (kind) {
                PaymentRequestKind.PREMIUM -> Text("$who подарил(а) вам Premium (${period.ru()}) 💝", "Premium уже открыт")
                PaymentRequestKind.CONSULTATION -> Text("$who оплатил(а) консультацию 💝", "Чат с врачом открыт")
                PaymentRequestKind.PET -> Text("$who подарил(а) вам Хумо 💝", "Птица счастья теперь ваша")
                PaymentRequestKind.FRAME -> Text("$who подарил(а) вам рамку 💝", "Она уже на вашем фото")
            }
        }
        Language.EN -> {
            val who = payer ?: "Someone close to you"
            when (kind) {
                PaymentRequestKind.PREMIUM -> Text("$who gave you ${period.en()} of Premium 💝", "Premium is open")
                PaymentRequestKind.CONSULTATION -> Text("$who paid for your consultation 💝", "Your chat with the doctor is open")
                PaymentRequestKind.PET -> Text("$who gave you Humo 💝", "The bird of happiness is yours now")
                PaymentRequestKind.FRAME -> Text("$who gave you a frame 💝", "It's already on your photo")
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
