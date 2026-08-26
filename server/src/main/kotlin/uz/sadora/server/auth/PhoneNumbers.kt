package uz.sadora.server.auth

import uz.sadora.server.core.ValidationException

/**
 * Uzbek numbers arrive from the app in half a dozen shapes — `90 123 45 67`,
 * `+998901234567`, `998 90 123-45-67`. They are all the same person, so everything is
 * normalised to E.164 before it reaches the database, where the unique index lives.
 */
object PhoneNumbers {

    private const val COUNTRY_CODE = "998"
    private const val NATIONAL_LENGTH = 9

    fun normalize(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        val national = when {
            digits.length == NATIONAL_LENGTH -> digits
            digits.length == NATIONAL_LENGTH + COUNTRY_CODE.length &&
                digits.startsWith(COUNTRY_CODE) -> digits.drop(COUNTRY_CODE.length)
            else -> throw ValidationException("phone", "O'zbekiston raqami formatida bo'lishi kerak")
        }
        return "+$COUNTRY_CODE$national"
    }

    /** Masked for logs and error messages: `+998 90 *** ** 67`. */
    fun mask(e164: String): String =
        if (e164.length < 6) "***" else "${e164.dropLast(4)}**${e164.takeLast(2)}"
}
