package uz.sadora.doctor.ui.components

import androidx.compose.runtime.Composable
import uz.sadora.doctor.i18n.ErrorStrings
import uz.sadora.doctor.i18n.strings
import uz.sadora.contract.UzbekPhone

/**
 * What a field will accept, and what it says when it will not.
 *
 * The client app's rules, the few the doctor app's fields need. Each mirrors one the
 * server enforces — the numbers come from [uz.sadora.contract.Limits], which both read.
 * The point is not to replace the server's check but to stop the round trip: a form
 * that comes back rejected has already lost the field the reason belongs to, and on a
 * phone it has usually lost the keyboard as well.
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
fun phoneError(value: String): String? = phoneError(value, strings.errors)

/** The rule itself, apart from the language it is said in. */
fun phoneError(value: String, t: ErrorStrings): String? =
    if (value.isBlank() || UzbekPhone.isIncomplete(value) || UzbekPhone.isValid(value)) {
        null
    } else {
        t.phoneInvalid
    }

/** Whether the phone screen's button may be pressed at all. */
fun phoneIsComplete(value: String): Boolean = UzbekPhone.isValid(value)

/** Digits only, capped at [max] characters — for a field that holds a whole number. */
fun acceptDigits(raw: String, max: Int): String = raw.filter { it.isDigit() }.take(max)

/** Anything, capped at [max] characters, so the field cannot exceed the column. */
fun acceptText(raw: String, max: Int): String = raw.take(max)
