package uz.sadora.server.auth

import uz.sadora.contract.UzbekPhone
import uz.sadora.server.core.ValidationException

/**
 * Uzbek numbers arrive from the app in half a dozen shapes — `90 123 45 67`,
 * `+998901234567`, `998 90 123-45-67`. They are all the same person, so everything is
 * normalised to E.164 before it reaches the database, where the unique index lives.
 *
 * The rules themselves live in [UzbekPhone], in `:contract`, so the field the number is
 * typed into refuses exactly what this refuses. The one thing that used to slip through
 * both was a plausible-looking number behind an operator code that does not exist:
 * `70 123 45 67` reached the SMS provider and failed silently, and the only thing the
 * user saw was a code that never arrived.
 */
object PhoneNumbers {

    fun normalize(raw: String): String = UzbekPhone.toE164(raw)
        ?: throw ValidationException("phone", "O'zbekiston raqami formatida bo'lishi kerak")

    /** Masked for logs and error messages: `+998 90 *** ** 67`. */
    fun mask(e164: String): String =
        if (e164.length < 6) "***" else "${e164.dropLast(4)}**${e164.takeLast(2)}"
}
