package uz.sadora.server.rewards

import uz.sadora.contract.CoinReasons
import uz.sadora.contract.Language

/**
 * What a ledger row is called, in the language she reads.
 *
 * The wording lives on the server for the same reason the AI rule engine's does: the
 * ledger is written here, and a row's meaning is fixed at the moment it is written. An
 * app that translated reason keys of its own would show a blank line for a reason an
 * operator added after that release shipped — [title] falls back to the key's own
 * description instead, which is always something.
 */
object RewardPhrases {

    fun title(reason: String, language: Language, milestone: Int? = null): String = when (language) {
        Language.RU -> ru(reason, milestone)
        Language.EN -> en(reason, milestone)
        Language.UZ -> uz(reason, milestone)
    }

    private fun uz(reason: String, milestone: Int?): String = when (reason) {
        CoinReasons.DAILY_OPEN -> "Kunlik kirish"
        CoinReasons.STREAK_MILESTONE -> milestone?.let { "$it kunlik streak" } ?: "Streak bosqichi"
        CoinReasons.CHECK_IN -> "Kayfiyat belgisi"
        CoinReasons.WATER_GOAL -> "Suv maqsadi bajarildi"
        CoinReasons.DOSE_TAKEN -> "Dori qabul qilindi"
        CoinReasons.MEAL_LOGGED -> "Ovqat qo'shildi"
        CoinReasons.JOURNAL_ENTRY -> "Kundalikka yozuv"
        CoinReasons.PRACTICE -> "Nafas / meditatsiya"
        CoinReasons.ARTICLE_READ -> "Maqola o'qildi"
        CoinReasons.REFERRAL_JOINED -> "Do'st taklif bo'yicha qo'shildi"
        CoinReasons.REFERRAL_WELCOME -> "Taklif sovg'asi"
        CoinReasons.REDEMPTION -> "Do'kondan xarid"
        CoinReasons.ADMIN_ADJUSTMENT -> "Qo'lda o'zgartirish"
        else -> reason
    }

    private fun ru(reason: String, milestone: Int?): String = when (reason) {
        CoinReasons.DAILY_OPEN -> "Ежедневный вход"
        CoinReasons.STREAK_MILESTONE -> milestone?.let { "Серия $it дней" } ?: "Рубеж серии"
        CoinReasons.CHECK_IN -> "Отметка настроения"
        CoinReasons.WATER_GOAL -> "Цель по воде выполнена"
        CoinReasons.DOSE_TAKEN -> "Приём отмечен"
        CoinReasons.MEAL_LOGGED -> "Добавлена еда"
        CoinReasons.JOURNAL_ENTRY -> "Запись в дневнике"
        CoinReasons.PRACTICE -> "Дыхание / медитация"
        CoinReasons.ARTICLE_READ -> "Статья прочитана"
        CoinReasons.REFERRAL_JOINED -> "Подруга присоединилась"
        CoinReasons.REFERRAL_WELCOME -> "Подарок за приглашение"
        CoinReasons.REDEMPTION -> "Покупка в магазине"
        CoinReasons.ADMIN_ADJUSTMENT -> "Ручная корректировка"
        else -> reason
    }

    private fun en(reason: String, milestone: Int?): String = when (reason) {
        CoinReasons.DAILY_OPEN -> "Daily open"
        CoinReasons.STREAK_MILESTONE -> milestone?.let { "$it-day streak" } ?: "Streak milestone"
        CoinReasons.CHECK_IN -> "Daily check-in"
        CoinReasons.WATER_GOAL -> "Water goal reached"
        CoinReasons.DOSE_TAKEN -> "Dose confirmed"
        CoinReasons.MEAL_LOGGED -> "Meal logged"
        CoinReasons.JOURNAL_ENTRY -> "Journal entry"
        CoinReasons.PRACTICE -> "Breathing / meditation"
        CoinReasons.ARTICLE_READ -> "Article read"
        CoinReasons.REFERRAL_JOINED -> "A friend joined"
        CoinReasons.REFERRAL_WELCOME -> "Invite welcome"
        CoinReasons.REDEMPTION -> "Shop purchase"
        CoinReasons.ADMIN_ADJUSTMENT -> "Manual adjustment"
        else -> reason
    }
}
