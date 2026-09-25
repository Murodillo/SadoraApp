package uz.sadora.doctor.data

import uz.sadora.contract.ApiError
import uz.sadora.contract.ErrorCodes

/**
 * Every way a call can fail, as one closed set the UI can exhaust.
 *
 * The client app's set, trimmed to what the doctor's endpoints can answer: there is no
 * paywall, allowance or health consent on this side, so those codes fall through to
 * [Unexpected] like any other the app does not know.
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

    /** A plain refusal: not hers, or a section closed to her. Not a blocked account. */
    data class Forbidden(override val message: String) : ApiFailure(message)

    /** The thing asked for is gone — a question deleted while she was reading it. */
    data class NotFound(override val message: String) : ApiFailure(message)

    /** Too many requests. [retryAfterSeconds] is present when the server said so. */
    data class RateLimited(
        override val message: String,
        val retryAfterSeconds: Int?,
    ) : ApiFailure(message)

    /** A wrong or expired OTP code. Carries the server's specific code. */
    data class Otp(val code: String, override val message: String) : ApiFailure(message)

    /** An operator has closed this section — the community, for everyone. */
    data class FeatureDisabled(val flag: String, override val message: String) : ApiFailure(message)

    /** Anything else, including 5xx. [requestId] is what support needs to trace it. */
    data class Unexpected(override val message: String, val requestId: String? = null) :
        ApiFailure(message)

    /**
     * Whether a failed token refresh means the session is really over. Only the server
     * saying no — the token is revoked, expired, or the account blocked — ends it. No
     * connection, a timeout or a 5xx says nothing about the token.
     */
    val endsSession: Boolean get() = this is Unauthorized || this is Blocked

    companion object {
        /**
         * Maps a server error body onto the cases above. An unrecognised code becomes
         * [Unexpected] rather than being guessed at.
         */
        fun from(error: ApiError): ApiFailure = when (error.code) {
            ErrorCodes.VALIDATION_FAILED -> Validation(error.message, error.details)
            ErrorCodes.UNAUTHORIZED,
            ErrorCodes.TOKEN_EXPIRED,
            ErrorCodes.TOKEN_REVOKED,
            -> Unauthorized(error.message)

            ErrorCodes.ACCOUNT_BLOCKED -> Blocked(error.message)
            ErrorCodes.FORBIDDEN -> Forbidden(error.message)
            ErrorCodes.NOT_FOUND -> NotFound(error.message)
            ErrorCodes.RATE_LIMITED ->
                RateLimited(error.message, error.details["retryAfterSeconds"]?.toIntOrNull())

            ErrorCodes.FEATURE_DISABLED -> FeatureDisabled(error.details["flag"].orEmpty(), error.message)

            ErrorCodes.OTP_INVALID,
            ErrorCodes.OTP_EXPIRED,
            ErrorCodes.OTP_TOO_MANY_ATTEMPTS,
            -> Otp(error.code, error.message)

            else -> Unexpected(error.message, error.requestId)
        }
    }
}
