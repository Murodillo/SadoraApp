package uz.sadora.server.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import uz.sadora.server.config.EskizConfig

/**
 * Sign-in codes by SMS through Eskiz (notify.eskiz.uz).
 *
 * Eskiz signs in with the cabinet's email and password and hands back a bearer that
 * lasts a month. It is kept until Eskiz refuses it, then fetched again once — a login per
 * code would double every send, and a month-old token must not lock everyone out.
 *
 * The text is sent exactly as [text] has it with the code put in. Eskiz drops anything
 * its moderators have not approved word for word, so the wording is configuration, not
 * code: changing it is a new approval first and an environment variable second.
 */
class EskizOtpSender(
    private val client: HttpClient,
    private val config: EskizConfig,
    private val text: String,
) : OtpSender {

    private val logger = LoggerFactory.getLogger(EskizOtpSender::class.java)
    private val mutex = Mutex()
    private var cachedToken: String? = null

    init {
        require(config.isConfigured) { "Eskiz needs ESKIZ_EMAIL and ESKIZ_PASSWORD" }
    }

    override suspend fun send(phone: String, code: String) {
        val message = text.replace("{code}", code)
        var token = token(stale = null)
        var response = post(token, phone, message)
        if (response.status == HttpStatusCode.Unauthorized) {
            // Expired or revoked between sends: one fresh login, then the same message.
            token = token(stale = token)
            response = post(token, phone, message)
        }
        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            logger.warn("Eskiz refused an SMS to {}: {} {}", PhoneNumbers.mask(phone), response.status.value, body.take(200))
            throw SmsDeliveryException("Eskiz said ${response.status.value}")
        }
        logger.info("Sign-in code sent by SMS to {}", PhoneNumbers.mask(phone))
    }

    private suspend fun post(token: String, phone: String, message: String): HttpResponse = guarded {
        client.submitForm(
            url = "${config.baseUrl}/api/message/sms/send",
            formParameters = Parameters.build {
                // Eskiz wants the bare digits: 998901234567, no plus.
                append("mobile_phone", phone.removePrefix("+"))
                append("message", message)
                append("from", config.from)
            },
        ) {
            header("Authorization", "Bearer $token")
        }
    }

    /**
     * The cached bearer, or a new one. [stale] is the token a caller just saw refused:
     * if another send already replaced it, that replacement is used rather than logging
     * in a second time.
     */
    private suspend fun token(stale: String?): String = mutex.withLock {
        cachedToken?.takeIf { it != stale }?.let { return it }
        val response = guarded {
            client.submitForm(
                url = "${config.baseUrl}/api/auth/login",
                formParameters = Parameters.build {
                    append("email", config.email!!)
                    append("password", config.password!!)
                },
            )
        }
        if (!response.status.isSuccess()) {
            // Wrong credentials are an operator problem; the body says which, and holds
            // nothing of hers.
            logger.error("Eskiz login failed: {} {}", response.status.value, response.bodyAsText().take(200))
            throw SmsDeliveryException("Eskiz login said ${response.status.value}")
        }
        val fresh = runCatching { response.body<LoginResponse>().data.token }
            .getOrElse { throw SmsDeliveryException("Eskiz login answer was not readable") }
        cachedToken = fresh
        fresh
    }

    /**
     * The call, or [SmsDeliveryException]. Bounded, because the shared client has no
     * timeout of its own and she is looking at a spinner while this runs.
     */
    private suspend fun guarded(call: suspend () -> HttpResponse): HttpResponse =
        try {
            withTimeout(CALL_TIMEOUT) { call() }
        } catch (timeout: TimeoutCancellationException) {
            logger.warn("Eskiz did not answer within {}", CALL_TIMEOUT)
            throw SmsDeliveryException("Eskiz timed out")
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            logger.warn("Eskiz could not be reached", failure)
            throw SmsDeliveryException("Eskiz could not be reached: ${failure.message}")
        }

    private companion object {
        val CALL_TIMEOUT = 15.seconds
    }

    @Serializable
    private data class LoginResponse(val data: TokenData)

    @Serializable
    private data class TokenData(val token: String)
}

/** The code was not handed to the SMS gateway. Not the user's fault, and worth retrying. */
class SmsDeliveryException(message: String) : Exception(message)
