package uz.sadora.server.core

import io.ktor.http.HttpStatusCode
import uz.sadora.contract.ErrorCodes

/**
 * Every failure the API reports deliberately is one of these. Anything else escaping to
 * the status-pages handler is a bug and is reported as `internal_error` with no detail,
 * so an unexpected exception can never leak a stack trace or a health value to a client.
 */
open class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
    val details: Map<String, String> = emptyMap(),
) : RuntimeException(message)

class ValidationException(
    message: String = "So'rov ma'lumotlari noto'g'ri",
    details: Map<String, String> = emptyMap(),
) : ApiException(HttpStatusCode.BadRequest, ErrorCodes.VALIDATION_FAILED, message, details) {

    constructor(field: String, problem: String) :
        this("So'rov ma'lumotlari noto'g'ri", mapOf(field to problem))
}

class UnauthorizedException(
    code: String = ErrorCodes.UNAUTHORIZED,
    message: String = "Avtorizatsiya talab qilinadi",
) : ApiException(HttpStatusCode.Unauthorized, code, message)

class ForbiddenException(
    code: String = ErrorCodes.FORBIDDEN,
    message: String = "Ruxsat yo'q",
) : ApiException(HttpStatusCode.Forbidden, code, message)

class NotFoundException(
    message: String = "Topilmadi",
) : ApiException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, message)

class ConflictException(
    message: String,
    code: String = ErrorCodes.CONFLICT,
) : ApiException(HttpStatusCode.Conflict, code, message)

class RateLimitedException(
    message: String = "Juda ko'p so'rov yuborildi",
    retryAfterSeconds: Int? = null,
) : ApiException(
    HttpStatusCode.TooManyRequests,
    ErrorCodes.RATE_LIMITED,
    message,
    retryAfterSeconds?.let { mapOf("retryAfterSeconds" to it.toString()) }.orEmpty(),
)

/** Raised when a metered feature is called past its daily or monthly allowance. */
class LimitReachedException(
    featureKey: String,
    val period: String,
) : ApiException(
    HttpStatusCode.TooManyRequests,
    ErrorCodes.LIMIT_REACHED,
    "Limit tugadi",
    mapOf("feature" to featureKey, "period" to period),
)

class EntitlementRequiredException(featureKey: String) : ApiException(
    HttpStatusCode.PaymentRequired,
    ErrorCodes.ENTITLEMENT_REQUIRED,
    "Bu funksiya Premium obunada mavjud",
    mapOf("feature" to featureKey),
)
