package org.example.project.data

import uz.sadora.contract.ApiError
import uz.sadora.contract.ErrorCodes

/**
 * Every way a call can fail, as one closed set the UI can exhaust.
 *
 * The distinction that matters to a screen is not the HTTP status but what the user
 * can do next: retry, sign in again, upgrade, wait, or fix an input. Those are the
 * cases below.
 */
sealed class ApiFailure(open val message: String) {

    /** No usable connection. The only failure worth an automatic retry. */
    data class Network(override val message: String) : ApiFailure(message)

    /** A field was rejected. [fields] maps field name to the reason, ready to render. */
    data class Validation(
        override val message: String,
        val fields: Map<String, String>,
    ) : ApiFailure(message)

    /** The session is gone. The app must return to sign-in; refreshing will not help. */
    data class Unauthorized(override val message: String) : ApiFailure(message)

    /** The account is blocked or pending deletion — a different screen from sign-in. */
    data class Blocked(override val message: String) : ApiFailure(message)

    /** A Premium-only feature. The paywall is the right response. */
    data class PremiumRequired(val featureKey: String, override val message: String) :
        ApiFailure(message)

    /** Out of allowance for now. [period] is `day` or `month`. */
    data class LimitReached(
        val featureKey: String,
        val period: String,
        override val message: String,
    ) : ApiFailure(message)

    /** Too many requests. [retryAfterSeconds] is present when the server said so. */
    data class RateLimited(
        override val message: String,
        val retryAfterSeconds: Int?,
    ) : ApiFailure(message)

    /** A wrong or expired OTP code. Carries the server's specific code. */
    data class Otp(val code: String, override val message: String) : ApiFailure(message)

    /** Anything else, including 5xx. [requestId] is what support needs to trace it. */
    data class Unexpected(override val message: String, val requestId: String? = null) :
        ApiFailure(message)

    companion object {
        /**
         * Maps a server error body onto the cases above.
         *
         * An unrecognised code becomes [Unexpected] rather than being guessed at — a
         * server that grows a new error code should make the app say "something went
         * wrong", not silently take the closest-looking branch.
         */
        fun from(error: ApiError): ApiFailure = when (error.code) {
            ErrorCodes.VALIDATION_FAILED -> Validation(error.message, error.details)
            ErrorCodes.UNAUTHORIZED,
            ErrorCodes.TOKEN_EXPIRED,
            ErrorCodes.TOKEN_REVOKED,
            -> Unauthorized(error.message)

            ErrorCodes.ACCOUNT_BLOCKED -> Blocked(error.message)
            ErrorCodes.FORBIDDEN -> Blocked(error.message)
            ErrorCodes.ENTITLEMENT_REQUIRED ->
                PremiumRequired(error.details["feature"].orEmpty(), error.message)

            ErrorCodes.LIMIT_REACHED -> LimitReached(
                featureKey = error.details["feature"].orEmpty(),
                period = error.details["period"] ?: "day",
                message = error.message,
            )

            ErrorCodes.RATE_LIMITED ->
                RateLimited(error.message, error.details["retryAfterSeconds"]?.toIntOrNull())

            ErrorCodes.OTP_INVALID,
            ErrorCodes.OTP_EXPIRED,
            ErrorCodes.OTP_TOO_MANY_ATTEMPTS,
            ErrorCodes.SOCIAL_TOKEN_INVALID,
            -> Otp(error.code, error.message)

            else -> Unexpected(error.message, error.requestId)
        }
    }
}

/** Thrown inside the client and unwrapped by [ApiResult]; never escapes the data layer. */
internal class ApiFailureException(val failure: ApiFailure) : Exception(failure.message)
