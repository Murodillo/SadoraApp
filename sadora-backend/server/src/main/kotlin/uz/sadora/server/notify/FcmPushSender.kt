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

    override suspend fun send(record: OutboxRecord, pushTokens: List<String>, drawsDoseActions: Set<String>): Boolean {
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
            when (val outcome = sendOne(bearer, record, token, token in drawsDoseActions)) {
                Outcome.DELIVERED -> delivered = true
                Outcome.GONE -> onTokenRejected(token)
                Outcome.FAILED -> Unit
            }
        }
        return delivered
    }

    override suspend fun sendSilent(data: Map<String, String>, pushTokens: List<String>): Boolean {
        if (pushTokens.isEmpty()) return false
        val bearer = try {
            tokens.token()
        } catch (failure: Exception) {
            logger.error("FCM credentials could not produce an access token", failure)
            return false
        }
        var delivered = false
        pushTokens.forEach { token ->
            val message = FcmMessage(
                token = token,
                data = data,
                // Normal priority: Android may batch it, which is right for a redraw.
                android = FcmAndroid(priority = "normal"),
                // iOS wakes the app in the background only for this exact shape.
                apns = FcmApns(
                    headers = mapOf("apns-push-type" to "background", "apns-priority" to "5"),
                    payload = FcmApnsPayload(FcmAps(contentAvailable = 1)),
                ),
            )
            when (post(bearer, message)) {
                Outcome.DELIVERED -> delivered = true
                Outcome.GONE -> onTokenRejected(token)
                Outcome.FAILED -> Unit
            }
        }
        return delivered
    }

    private suspend fun sendOne(bearer: String, record: OutboxRecord, token: String, drawsDose: Boolean): Outcome {
        // The app opens the right screen from these; where the system draws the
        // notification, the visible text is in `notification` so it can be shown while
        // the app is not running.
        val data = buildMap {
            put("notificationId", record.id.toString())
            put("category", record.category.name.lowercase())
            // Where a tap lands: the app routes it like any link of its own.
            record.link?.let { put("link", it) }
        }
        val dose = record.category == uz.sadora.contract.NotificationCategory.MED_REMINDER && record.targetApp == TARGET_CLIENT
        val message = if (dose && drawsDose) {
            // A dose: Android gets data only, so the app draws it with "taken" and
            // "skipped" — a notification the system draws can carry no actions. iOS
            // gets the alert under the DOSE category, whose actions the app registered.
            FcmMessage(
                token = token,
                data = data + mapOf("title" to record.title, "body" to record.body),
                android = FcmAndroid(priority = "high"),
                apns = FcmApns(
                    payload = FcmApnsPayload(
                        FcmAps(alert = FcmAlert(record.title, record.body), category = DOSE_CATEGORY, sound = "default"),
                    ),
                ),
            )
        } else {
            FcmMessage(
                token = token,
                notification = FcmNotification(record.title, record.body),
                data = data,
                android = channelFor(record)?.let { FcmAndroid(notification = FcmAndroidNotification(it)) },
                // An app that does not draw it itself (an older Android build) still shows the
                // reminder; on iOS the category alone brings the buttons.
                apns = if (dose) FcmApns(payload = FcmApnsPayload(FcmAps(category = DOSE_CATEGORY))) else null,
            )
        }
        return post(bearer, message)
    }

    private suspend fun post(bearer: String, message: FcmMessage): Outcome {
        val response = try {
            client.post("$endpoint/v1/projects/$projectId/messages:send") {
                header("Authorization", "Bearer $bearer")
                contentType(ContentType.Application.Json)
                setBody(FcmEnvelope(message))
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

        /** The women's app's channels; its manifest names the reminders one as the default. */
        const val CHANNEL_MESSAGES = "sadora_messages"
        const val CHANNEL_REMINDERS = "sadora_reminders"

        /** The iOS notification category whose actions are "taken" and "skipped". */
        const val DOSE_CATEGORY = "DOSE"

        /**
         * Which Android channel shows [record] while the app is in the background, when
         * the system and not the app posts it. A person writing to her — a private
         * message, a doctor, Yaqinim — rings on its own channel, so she can silence
         * reminders without missing them. The doctor app has one channel of its own and
         * is left to its default.
         */
        internal fun channelFor(record: OutboxRecord): String? {
            if (record.targetApp != TARGET_CLIENT) return null
            val link = record.link.orEmpty()
            val message = record.category == uz.sadora.contract.NotificationCategory.PARTNER ||
                link.startsWith("sadora://conversation") ||
                link.startsWith("sadora://yaqinim")
            return if (message) CHANNEL_MESSAGES else CHANNEL_REMINDERS
        }
    }
}

@Serializable
private data class FcmEnvelope(val message: FcmMessage)

@Serializable
private data class FcmMessage(
    val token: String,
    val notification: FcmNotification? = null,
    val data: Map<String, String>,
    val android: FcmAndroid? = null,
    val apns: FcmApns? = null,
)

@Serializable
private data class FcmAndroid(
    val notification: FcmAndroidNotification? = null,
    val priority: String? = null,
)

@Serializable
private data class FcmApns(
    val headers: Map<String, String> = emptyMap(),
    val payload: FcmApnsPayload,
)

@Serializable
private data class FcmApnsPayload(val aps: FcmAps)

@Serializable
private data class FcmAps(
    @kotlinx.serialization.SerialName("content-available") val contentAvailable: Int? = null,
    val category: String? = null,
    val alert: FcmAlert? = null,
    val sound: String? = null,
)

@Serializable
private data class FcmAlert(val title: String, val body: String)

@Serializable
private data class FcmAndroidNotification(@kotlinx.serialization.SerialName("channel_id") val channelId: String)

@Serializable
private data class FcmNotification(val title: String, val body: String)
