package uz.sadora.server.plugins

import io.ktor.server.request.path
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.CannotTransformContentToTypeException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import uz.sadora.contract.ApiError
import uz.sadora.contract.ApiErrorResponse
import uz.sadora.contract.ErrorCodes
import uz.sadora.server.core.ApiException

/**
 * The single place an error becomes a response body.
 *
 * Deliberate failures ([ApiException]) keep their message; anything else is logged in
 * full and reported as a bare `internal_error`. That asymmetry is the point — an
 * unexpected exception may have a health value or a connection string in its message.
 */
fun Application.configureStatusPages() {
    val logger = LoggerFactory.getLogger("uz.sadora.server.errors")

    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(
                cause.status,
                ApiErrorResponse(
                    ApiError(
                        code = cause.code,
                        message = cause.message,
                        details = cause.details,
                        requestId = call.callId,
                    ),
                ),
            )
        }

        // A malformed body is the client's problem, so it is safe to say so — but the
        // parser's message can quote the payload, and that payload may be health data.
        exception<SerializationException> { call, cause ->
            logger.debug("Malformed request body", cause)
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorResponse(
                    ApiError(
                        code = ErrorCodes.VALIDATION_FAILED,
                        message = "So'rov tanasi noto'g'ri formatda",
                        requestId = call.callId,
                    ),
                ),
            )
        }

        exception<BadRequestException> { call, cause ->
            logger.debug("Bad request", cause)
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorResponse(
                    ApiError(
                        code = ErrorCodes.VALIDATION_FAILED,
                        message = "So'rov noto'g'ri",
                        requestId = call.callId,
                    ),
                ),
            )
        }

        // A body the negotiator cannot read at all — no Content-Type, or one that is not
        // JSON — is the caller's mistake, not a server failure. It used to fall through
        // to the handler below and be reported as `internal_error`, which both alarmed
        // the log and told the client to retry something that could never work.
        exception<CannotTransformContentToTypeException> { call, cause ->
            logger.debug("Unreadable request body", cause)
            call.respondUnsupportedMedia()
        }
        exception<UnsupportedMediaTypeException> { call, cause ->
            logger.debug("Unsupported request media type", cause)
            call.respondUnsupportedMedia()
        }

        exception<PayloadTooLargeException> { call, cause ->
            logger.debug("Request body over the limit", cause)
            call.respond(
                HttpStatusCode.PayloadTooLarge,
                ApiErrorResponse(
                    ApiError(
                        code = ErrorCodes.VALIDATION_FAILED,
                        message = "So'rov hajmi juda katta",
                        requestId = call.callId,
                    ),
                ),
            )
        }

        exception<Throwable> { call, cause ->
            // The path, masked — not the URI, whose query can hold an OAuth code.
            logger.error("Unhandled failure on {} {}", call.request.local.method.value, redactPath(call.request.path()), cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiErrorResponse(
                    ApiError(
                        code = ErrorCodes.INTERNAL_ERROR,
                        message = "Serverda kutilmagan xatolik",
                        requestId = call.callId,
                    ),
                ),
            )
        }

        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                status,
                ApiErrorResponse(
                    ApiError(
                        code = ErrorCodes.NOT_FOUND,
                        message = "Bunday endpoint yo'q",
                        requestId = call.callId,
                    ),
                ),
            )
        }

        // The rate limiter answers with a bare status and a Retry-After header. Clients
        // branch on the envelope's code, so the refusal is put into the same shape as
        // every other one, with the wait carried over into the details.
        status(HttpStatusCode.TooManyRequests) { call, status ->
            val retryAfter = call.response.headers[HttpHeaders.RetryAfter]
            call.respond(
                status,
                ApiErrorResponse(
                    ApiError(
                        code = ErrorCodes.RATE_LIMITED,
                        message = "Juda ko'p so'rov yuborildi",
                        details = retryAfter?.let { mapOf("retryAfterSeconds" to it) }.orEmpty(),
                        requestId = call.callId,
                    ),
                ),
            )
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondUnsupportedMedia() {
    respond(
        HttpStatusCode.UnsupportedMediaType,
        ApiErrorResponse(
            ApiError(
                code = ErrorCodes.VALIDATION_FAILED,
                message = "So'rov JSON formatida bo'lishi kerak",
                requestId = callId,
            ),
        ),
    )
}
