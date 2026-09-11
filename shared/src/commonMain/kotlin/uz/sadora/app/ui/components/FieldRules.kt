package uz.sadora.app.ui.components

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.deviceToday
import uz.sadora.contract.Limits
import uz.sadora.contract.UzbekPhone

/**
 * What a field will accept, and what it says when it will not.
 *
 * Every rule here mirrors one the server enforces — the numbers come from
 * [uz.sadora.contract.Limits], which both read. The point is not to replace the
 * server's check but to stop the round trip: a form that comes back rejected has
 * already lost the field the reason belongs to, and on a phone it has usually lost
 * the keyboard as well.
 *
 * Errors are held back while an answer could still become right. Telling someone their
 * phone number is wrong after two digits is technically true and useless.
 */

/**
 * Digits only, capped at nine.
 *
 * The field stores the digits and [PhoneMask] draws them as `90 123 45 67`. Storing the
 * formatted string instead moved the caret every time a space was inserted, so editing
 * the middle of a number produced nonsense.
 */
fun acceptPhone(raw: String): String = UzbekPhone.accept(raw)

@Composable
fun phoneError(value: String): String? =
    if (value.isBlank() || UzbekPhone.isIncomplete(value) || UzbekPhone.isValid(value)) {
        null
    } else {
        strings.errors.phoneInvalid
    }

/** Whether the phone screen's button may be pressed at all. */
fun phoneIsComplete(value: String): Boolean = UzbekPhone.isValid(value)

/** Digits only, capped at [max] characters — for a field that holds a whole number. */
fun acceptDigits(raw: String, max: Int): String = raw.filter { it.isDigit() }.take(max)

/** Anything, capped at [max] characters, so the field cannot exceed the column. */
fun acceptText(raw: String, max: Int): String = raw.take(max)

@Composable
fun requiredTextError(value: String, max: Int): String? = when {
    value.isEmpty() -> null
    value.isBlank() -> strings.errors.nameRequired
    value.length > max -> strings.errors.tooLong(max)
    else -> null
}

/** A number that must sit inside [range]. Blank is not an error — it is unanswered. */
@Composable
fun numberError(value: String, range: IntRange): String? {
    if (value.isBlank()) return null
    val number = value.toIntOrNull() ?: return strings.errors.wholeNumber
    return if (number in range) null else strings.errors.outOfRange(range.first, range.last)
}

/**
 * `27.8.2026`, the way the app asks for a date everywhere it asks in text.
 *
 * Deliberately lenient about the separator and about a single-digit day or month: a
 * date typed on a phone keyboard is typed in a hurry.
 */
fun parseTypedDate(raw: String): LocalDate? {
    val parts = raw.trim().split('.', '/', '-').mapNotNull { it.trim().toIntOrNull() }
    if (parts.size != 3) return null
    val (day, month, year) = parts
    if (month !in 1..12 || day !in 1..31 || year !in Limits.BIRTH_YEAR.first..2100) return null
    return runCatching { LocalDate(year, month, day) }.getOrNull()
}

@Composable
fun typedDateError(raw: String, allowFuture: Boolean = true): String? {
    if (raw.isBlank()) return null
    val date = parseTypedDate(raw) ?: return strings.errors.dateFormat
    return if (!allowFuture && date > deviceToday()) strings.errors.dateInFuture else null
}

/** `10:30`, or null when it is blank or not a time. */
fun parseTypedTime(raw: String): LocalTime? {
    val parts = raw.trim().split(':', '.').mapNotNull { it.trim().toIntOrNull() }
    if (parts.size != 2) return null
    val (hour, minute) = parts
    if (hour !in 0..23 || minute !in 0..59) return null
    return LocalTime(hour, minute)
}

@Composable
fun typedTimeError(raw: String, required: Boolean): String? = when {
    raw.isBlank() -> if (required) strings.errors.timeFormat else null
    parseTypedTime(raw) == null -> strings.errors.timeFormat
    else -> null
}
