package uz.sadora.server.notify

import uz.sadora.contract.Language
import uz.sadora.contract.NotificationCategory

/**
 * A push in discreet mode: the app's name and "you have a reminder", nothing more.
 *
 * Rewritten at delivery rather than when the row is queued, so turning the mode on
 * covers the reminders already waiting and turning it off restores their own words. The
 * link stays — a tap is behind her own unlock and may open the right screen.
 */
object DiscreetPush {
    const val TITLE = "Sadora"

    fun neutral(record: OutboxRecord, language: Language): OutboxRecord =
        record.copy(title = TITLE, body = if (isMessage(record)) message(language) else reminder(language))

    private fun isMessage(record: OutboxRecord): Boolean =
        record.category == NotificationCategory.PARTNER ||
            record.link.orEmpty().startsWith("sadora://conversation") ||
            record.link.orEmpty().startsWith("sadora://yaqinim")

    private fun reminder(language: Language): String = when (language) {
        Language.UZ -> "Sizda yangi eslatma bor"
        Language.RU -> "У вас новое напоминание"
        Language.EN -> "You have a new reminder"
    }

    private fun message(language: Language): String = when (language) {
        Language.UZ -> "Sizda yangi xabar bor"
        Language.RU -> "У вас новое сообщение"
        Language.EN -> "You have a new message"
    }
}
