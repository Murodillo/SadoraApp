package uz.sadora.server.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import java.util.Base64
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory
import uz.sadora.server.config.PlayMobileConfig

/**
 * Sign-in codes by SMS through Play Mobile (send.smsxabar.uz, the "broker API").
 *
 * One POST per code, signed with HTTP Basic — there is no token to fetch or keep. The
 * gateway answers 200 "Request is received" once it has queued the message; that is
 * acceptance, not delivery, and delivery is not waited for.
 *
 * The text is sent exactly as [text] has it with the code put in. The originator and the
 * wording are what the contract with Play Mobile names, so both are configuration.
 */
class PlayMobileOtpSender(
    private val client: HttpClient,
    private val config: PlayMobileConfig,
    private val text: String,
) : OtpSender, TextMessageSender {

    private val logger = LoggerFactory.getLogger(PlayMobileOtpSender::class.java)

    init {
        require(config.isConfigured) { "Play Mobile needs PLAYMOBILE_LOGIN and PLAYMOBILE_PASSWORD" }
    }

    private val authorization =
        "Basic " + Base64.getEncoder().encodeToString("${config.login}:${config.password}".toByteArray())

    override suspend fun send(phone: String, code: String) {
        sendText(phone, text.replace("{code}", code))
        logger.info("Sign-in code sent by SMS to {}", PhoneNumbers.mask(phone))
    }

    override suspend fun sendText(phone: String, text: String) {
        val body = buildJsonObject {
            putJsonArray("messages") {
                addJsonObject {
                    // Play Mobile wants the bare digits: 998901234567, no plus.
                    put("recipient", phone.removePrefix("+"))
                    // Theirs to deduplicate on, at most 40 characters; ours only has to
                    // be new each time, a resend included.
                    put("message-id", "sdr" + UUID.randomUUID().toString().replace("-", ""))
                    putJsonObject("sms") {
                        put("originator", config.originator)
                        putJsonObject("content") { put("text", text) }
                    }
                }
            }
        }.toString()

        val response = guarded {
            client.post("${config.baseUrl}/broker-api/send") {
                header("Authorization", authorization)
                setBody(TextContent(body, ContentType.Application.Json.withParameter("charset", "UTF-8")))
            }
        }
        if (!response.status.isSuccess()) {
            // The body is their error code and a sentence about it; nothing of hers.
            val answer = runCatching { response.bodyAsText() }.getOrDefault("")
            if (response.status == HttpStatusCode.Unauthorized) {
                logger.error("Play Mobile refused the login: check PLAYMOBILE_LOGIN and PLAYMOBILE_PASSWORD")
            } else {
                logger.warn(
                    "Play Mobile refused an SMS to {}: {} {}",
                    PhoneNumbers.mask(phone), response.status.value, answer.take(200),
                )
            }
            throw SmsDeliveryException("Play Mobile said ${response.status.value}")
        }
    }

    /**
     * The call, or [SmsDeliveryException]. Bounded, because the shared client has no
     * timeout of its own and she is looking at a spinner while this runs.
     */
    private suspend fun guarded(call: suspend () -> HttpResponse): HttpResponse =
        try {
            withTimeout(CALL_TIMEOUT) { call() }
        } catch (timeout: TimeoutCancellationException) {
            logger.warn("Play Mobile did not answer within {}", CALL_TIMEOUT)
            throw SmsDeliveryException("Play Mobile timed out")
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            logger.warn("Play Mobile could not be reached", failure)
            throw SmsDeliveryException("Play Mobile could not be reached: ${failure.message}")
        }

    private companion object {
        val CALL_TIMEOUT = 15.seconds
    }
}

/** The code was not handed to the SMS gateway. Not the user's fault, and worth retrying. */
class SmsDeliveryException(message: String) : Exception(message)

/** Any text by SMS — the SOS alert. Throws [SmsDeliveryException] when the gateway refused it. */
interface TextMessageSender {
    suspend fun sendText(phone: String, text: String)
}

/** Where SMS must not leave the machine: the text is logged, masked phone and all, and dropped. */
class LoggingTextSender : TextMessageSender {
    private val logger = LoggerFactory.getLogger(LoggingTextSender::class.java)

    override suspend fun sendText(phone: String, text: String) {
        logger.info("SMS not sent (SOS_SMS_LIVE is off) to {}: {}", PhoneNumbers.mask(phone), text)
    }
}
