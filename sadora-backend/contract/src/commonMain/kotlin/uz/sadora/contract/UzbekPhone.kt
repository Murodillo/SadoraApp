package uz.sadora.contract

/**
 * An Uzbek mobile number, in the one shape everything agrees on.
 *
 * The field types it as `90 123 45 67`, the wire carries `+998901234567`, and the same
 * person may paste either — plus `998 90 123-45-67` from a contact card. Every one of
 * those is nine national digits behind an operator code, so the rules live here and the
 * app and the server both read them rather than each keeping their own idea.
 *
 * Two jobs, deliberately separate. [accept] is what a text field calls on every
 * keystroke: it is forgiving, and it truncates. [parse] is what a request is checked
 * with: it is strict, and sixteen digits are not a number with nine at the front —
 * they are a mistake, and truncating them would sign someone in as somebody else.
 */
object UzbekPhone {

    const val COUNTRY_CODE = "998"

    /** Nine digits after the country code. Nothing longer is a number; it is a typo. */
    const val NATIONAL_LENGTH = 9

    /**
     * The two-digit codes an Uzbek mobile number can start with.
     *
     * Checked because the first two digits are the one part a person cannot get
     * "nearly right": `70 123 45 67` is not a slow typist, it is the wrong number, and
     * finding that out from a code that never arrives is the expensive way.
     *
     * Landlines are deliberately absent — the app signs in by SMS.
     */
    val OPERATOR_CODES = setOf(
        "20", "33", "50", "55", "77", "88", "90", "91", "93", "94", "95", "97", "98", "99",
    )

    /**
     * What a field should hold after this keystroke: digits only, at most nine.
     *
     * A leading `998` is dropped once there is more than a whole number's worth of
     * digits, so pasting `+998901234567` works and typing `99…` — the start of a real
     * `99` operator code — is not eaten.
     */
    fun accept(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        val national = if (digits.length > NATIONAL_LENGTH && digits.startsWith(COUNTRY_CODE)) {
            digits.drop(COUNTRY_CODE.length)
        } else {
            digits
        }
        return national.take(NATIONAL_LENGTH)
    }

    /** The nine national digits of a complete number, or null when [raw] is not one. */
    fun parse(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        val national = when {
            digits.length == NATIONAL_LENGTH -> digits
            digits.length == NATIONAL_LENGTH + COUNTRY_CODE.length &&
                digits.startsWith(COUNTRY_CODE) -> digits.drop(COUNTRY_CODE.length)
            else -> return null
        }
        return national.takeIf { it.take(2) in OPERATOR_CODES }
    }

    /** `901234567` -> `90 123 45 67`. A part-typed number is grouped as far as it goes. */
    fun format(raw: String): String {
        val d = accept(raw)
        return buildString {
            append(d.take(2))
            if (d.length > 2) append(' ').append(d.substring(2, minOf(5, d.length)))
            if (d.length > 5) append(' ').append(d.substring(5, minOf(7, d.length)))
            if (d.length > 7) append(' ').append(d.substring(7))
        }
    }

    fun isValid(raw: String): Boolean = parse(raw) != null

    /** True while it could still become valid — used to hold an error back as she types. */
    fun isIncomplete(raw: String): Boolean =
        raw.filter { it.isDigit() }.length < NATIONAL_LENGTH

    /** `+998901234567`, or null when [raw] is not a number. */
    fun toE164(raw: String): String? = parse(raw)?.let { "+$COUNTRY_CODE$it" }
}
