package uz.sadora.server.doctor

import uz.sadora.contract.DoctorStatus
import uz.sadora.contract.Language

/**
 * The pushes a doctor gets about her own account — the review of her application and
 * her photo — in the language her app is set to. An operator's own note, when there is
 * one, is sent as written.
 */
internal object DoctorPhrases {

    data class Text(val title: String, val body: String)

    fun decision(status: DoctorStatus, note: String?, language: Language): Text? {
        val details = note ?: when (language) {
            Language.UZ -> "Tafsilotlarni ilovada ko'ring."
            Language.RU -> "Подробности — в приложении."
            Language.EN -> "See the details in the app."
        }
        return when (status) {
            DoctorStatus.APPROVED -> when (language) {
                Language.UZ -> Text("Tasdiqlandingiz ✓", "Endi Chatda shifokor sifatida javob bera olasiz.")
                Language.RU -> Text("Вы подтверждены ✓", "Теперь вы можете отвечать в чате как врач.")
                Language.EN -> Text("You're verified ✓", "You can now answer in the chat as a doctor.")
            }
            DoctorStatus.REJECTED -> when (language) {
                Language.UZ -> Text("Arizangiz qaytarildi", details)
                Language.RU -> Text("Заявка возвращена", details)
                Language.EN -> Text("Your application was returned", details)
            }
            DoctorStatus.SUSPENDED -> when (language) {
                Language.UZ -> Text("Shifokor hisobingiz to'xtatildi", details)
                Language.RU -> Text("Аккаунт врача приостановлен", details)
                Language.EN -> Text("Your doctor account is suspended", details)
            }
            else -> null
        }
    }

    fun photoRemoved(reason: String?, language: Language) = when (language) {
        Language.UZ -> Text("Rasmingiz olib tashlandi", reason ?: "Yuzingiz aniq ko'rinadigan rasm qo'ying.")
        Language.RU -> Text("Ваше фото удалено", reason ?: "Загрузите фото, на котором хорошо видно лицо.")
        Language.EN -> Text("Your photo was removed", reason ?: "Please add a photo where your face is clearly visible.")
    }
}
