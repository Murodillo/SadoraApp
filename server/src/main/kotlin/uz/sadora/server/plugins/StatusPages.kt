package uz.sadora.server.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
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

        exception<Throwable> { call, cause ->
            logger.error("Unhandled failure on {} {}", call.request.local.method.value, call.request.local.uri, cause)
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
    }
}
