package uz.sadora.doctor.data

import androidx.compose.runtime.Composable
import uz.sadora.doctor.i18n.ErrorStrings
import uz.sadora.doctor.i18n.strings

/**
 * What a failure says on screen.
 *
 * Written per case rather than passing the server's message through: the server speaks
 * to several clients and its wording is not always what a phone should show. Two cases
 * do prefer the server's text — a validation message names the field she just typed in,
 * and an OTP message says which of the several ways a code can be wrong it was.
 *
 * It takes the strings rather than reading them, so a failure stored in a controller is
 * a failure and not a sentence: one worded when it happened would stay in the language
 * the app was in at the time.
 */
fun ApiFailure.readable(t: ErrorStrings): String = when (this) {
    is ApiFailure.Network -> t.network
    is ApiFailure.Validation -> fields.values.firstOrNull() ?: t.validation
    is ApiFailure.Unauthorized -> t.sessionExpired
    is ApiFailure.Blocked -> t.blocked
    is ApiFailure.Forbidden -> t.forbidden
    is ApiFailure.NotFound -> t.notFound
    is ApiFailure.RateLimited -> retryAfterSeconds?.let { t.retryAfter(it) } ?: t.retrySoon
    is ApiFailure.Otp -> message.ifBlank { t.otpInvalid }
    is ApiFailure.FeatureDisabled -> t.featureClosed
    is ApiFailure.Unexpected -> t.unexpected
}

/** `controller.error?.let { Text(it.readable()) }` — the composable half of the above. */
@Composable
fun ApiFailure.readable(): String = readable(strings.errors)
