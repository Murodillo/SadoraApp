package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * The Telegram bot: her reminders in Telegram too, and quick logging with buttons.
 *
 * Linked by a one-time link from the app, never by phone number. Discreet by default: a
 * reminder reads "Sizda yangi eslatma bor" until she turns [TelegramStatus.detailed] on.
 */
@Serializable
data class TelegramStatus(
    /** False until the server has a bot: the app then says it is coming. */
    val available: Boolean = false,
    val linked: Boolean = false,
    /** Her Telegram @username, when she has one. */
    val username: String? = null,
    val detailed: Boolean = false,
    val botUsername: String? = null,
)

/** Opens Telegram on the bot with a one-time code; valid until [expiresAt]. */
@Serializable
data class TelegramLink(val url: String, val expiresAt: Instant)

@Serializable
data class TelegramSettingsRequest(val detailed: Boolean)
