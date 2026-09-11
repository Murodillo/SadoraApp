package uz.sadora.server.notify

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Delivery through Firebase Cloud Messaging's HTTP v1 API.
 *
 * FCM reaches both platforms — Android directly, iOS through the APNs key uploaded to
 * the Firebase project — so one sender covers the app rather than two that have to agree
 * about what a notification is.
 *
 * One request per token, because v1 has no multicast endpoint and because the useful
 * answer is per token: UNREGISTERED means that device uninstalled or reinstalled, and
 * the row should stop being sent to. A send counts as delivered if any token took it —
 * a phone that was wiped must not mark a reminder failed on a tablet that got it.
 */
class FcmPushSender(
    private val client: HttpClient,
    private val projectId: String,
    private val tokens: GoogleAccessTokens,
    private val endpoint: String = "https://fcm.googleapis.com",
    /** Called with a token FCM says no longer exists, so it can be forgotten. */
    private val onTokenRejected: suspend (String) -> Unit = {},
) : PushSender {

    private val logger = LoggerFactory.getLogger(FcmPushSender::class.java)

    override suspend fun send(record: OutboxRecord, pushTokens: List<String>): Boolean {
        if (pushTokens.isEmpty()) return false

        val bearer = try {
            tokens.token()
        } catch (failure: Exception) {
            // Credentials that are present but broken are an operator problem, not a
            // reason to drop the notification: the row stays failed and can be retried.
            logger.error("FCM credentials could not produce an access token", failure)
            return false
        }

        var delivered = false
        pushTokens.forEach { token ->
            when (val outcome = sendOne(bearer, record, token)) {
                Outcome.DELIVERED -> delivered = true
                Outcome.GONE -> onTokenRejected(token)
                Outcome.FAILED -> Unit
            }
        }
        return delivered
    }

    private suspend fun sendOne(bearer: String, record: OutboxRecord, token: String): Outcome {
        val response = try {
            client.post("$endpoint/v1/projects/$projectId/messages:send") {
                header("Authorization", "Bearer $bearer")
                contentType(ContentType.Application.Json)
                setBody(
                    FcmEnvelope(
                        FcmMessage(
                            token = token,
                            notification = FcmNotification(record.title, record.body),
                            // The app opens the right screen from these; the visible text
                            // is in `notification` so the system can show it while the app
                            // is not running.
                            data = mapOf(
                                "notificationId" to record.id.toString(),
                                "category" to record.category.name.lowercase(),
                            ),
                        ),
                    ),
                )
            }
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            logger.warn("FCM request failed for one device", failure)
            return Outcome.FAILED
        }

        if (response.status.isSuccess()) return Outcome.DELIVERED

        // 404 UNREGISTERED and 400 INVALID_ARGUMENT on the token both mean this device
        // will never receive anything again.
        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        if (response.status == HttpStatusCode.NotFound || "UNREGISTERED" in body) {
            logger.info("FCM says a device token is gone; dropping it")
            return Outcome.GONE
        }
        logger.warn("FCM refused a message: {} {}", response.status.value, body.take(200))
        return Outcome.FAILED
    }

    private enum class Outcome { DELIVERED, GONE, FAILED }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
    }
}

@Serializable
private data class FcmEnvelope(val message: FcmMessage)

@Serializable
private data class FcmMessage(
    val token: String,
    val notification: FcmNotification,
    val data: Map<String, String>,
)

@Serializable
private data class FcmNotification(val title: String, val body: String)
