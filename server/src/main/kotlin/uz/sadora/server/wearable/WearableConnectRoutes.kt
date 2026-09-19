package uz.sadora.server.wearable

import uz.sadora.contract.CompleteConnectRequest
import io.ktor.server.request.receive
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import uz.sadora.contract.Ack
import uz.sadora.contract.HealthProvider
import uz.sadora.server.api.requireUserId
import uz.sadora.server.core.ValidationException
import uz.sadora.server.plugins.RateLimits
import uz.sadora.server.plugins.USER_AUTH
import uz.sadora.server.wearable.whoop.WhoopWebhookEvent

/** Where the browser is sent after the provider's consent page: back into the app. */
private const val APP_RETURN_LINK = "sadora://wearables/whoop"

/** The device list, the OAuth start, disconnect and sync — all behind her token. */
fun Route.wearableConnectRoutes(service: WearableConnectService, job: WearableSyncJob) {
    authenticate(USER_AUTH) {
        route("/wearables") {
            get("/providers") {
                call.respond(service.providers(call.requireUserId()))
            }

            get("/connections") {
                call.respond(service.connections(call.requireUserId()))
            }

            route("/{provider}") {
                post("/connect") {
                    call.respond(service.startConnect(call.requireUserId(), call.provider()))
                }

                /** The app hands back what the browser brought; see [CompleteConnectRequest]. */
                post("/complete") {
                    val request = call.receive<CompleteConnectRequest>()
                    val userId = service.completeConnect(call.provider(), request.state, request.code, call.requireUserId())
                    // The first pull is thirty days and a few pages; it does not hold the call.
                    job.syncInBackground { service.dueForSyncOf(userId)?.let { service.sync(it) } }
                    call.respond(Ack())
                }

                delete {
                    service.disconnect(call.requireUserId(), call.provider())
                    call.respond(Ack())
                }

                post("/sync") {
                    call.respond(service.syncNow(call.requireUserId(), call.provider()))
                }
            }
        }
    }
}

/**
 * The two doors a provider knocks on without a token: the OAuth return and the webhook.
 *
 * The return is a browser, so it gets a page — one that says it worked and sends her
 * back into the app, or one that says it did not, in words rather than JSON. The
 * webhook is verified against the app secret before the body is even parsed.
 */
fun Route.wearablePublicRoutes(service: WearableConnectService, job: WearableSyncJob) {
    rateLimit(RateLimits.WEARABLE) {
        route("/wearables/whoop") {
            // The browser only carries the code back to the app. Exchanging it here would
            // save the grant to whoever started the flow, not whoever is holding the phone.
            get("/callback") {
                val error = call.request.queryParameters["error"]
                val code = call.request.queryParameters["code"]
                val state = call.request.queryParameters["state"]
                if (error != null || code.isNullOrBlank() || state.isNullOrBlank()) {
                    call.respondText(ReturnPage.render(ok = false), ContentType.Text.Html, HttpStatusCode.BadRequest)
                    return@get
                }
                call.respondText(ReturnPage.render(ok = true, code = code, state = state), ContentType.Text.Html)
            }

            post("/webhook") {
                val secret = service.whoopWebhookSecret
                    ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, Ack(false))
                val body = call.receiveText()
                val signature = call.request.header("X-WHOOP-Signature").orEmpty()
                val timestamp = call.request.header("X-WHOOP-Signature-Timestamp").orEmpty()
                if (!WebhookSignature.matches(secret, timestamp, body, signature)) {
                    return@post call.respond(HttpStatusCode.Unauthorized, Ack(false))
                }
                val event = runCatching { webhookJson.decodeFromString<WhoopWebhookEvent>(body) }.getOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, Ack(false))
                // Acknowledge first, pull after: WHOOP retries anything slower than a moment.
                if (event.type.endsWith(".updated")) {
                    job.syncInBackground { service.syncByExternalUser(HealthProvider.WHOOP, event.userId.toString()) }
                }
                call.respond(Ack())
            }
        }
    }
}

private val webhookJson = Json { ignoreUnknownKeys = true }

private fun io.ktor.server.application.ApplicationCall.provider(): HealthProvider {
    val raw = parameters["provider"].orEmpty()
    return HealthProvider.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        ?: throw ValidationException("provider", "Noma'lum provayder: $raw")
}

/** `base64(HMAC-SHA256(secret, timestamp + body))`, compared in constant time. */
object WebhookSignature {
    fun sign(secret: String, timestamp: String, body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal((timestamp + body).toByteArray(Charsets.UTF_8)))
    }

    fun matches(secret: String, timestamp: String, body: String, signature: String): Boolean {
        if (timestamp.isBlank() || signature.isBlank()) return false
        val expected = sign(secret, timestamp, body).toByteArray()
        val given = signature.toByteArray()
        return java.security.MessageDigest.isEqual(expected, given)
    }
}

/** The page after the provider's consent screen. Three languages, one line each, and a button back. */
private object ReturnPage {
    fun render(ok: Boolean, code: String? = null, state: String? = null): String {
        val title = if (ok) "Deyarli tayyor · Почти готово · Almost done" else "Ulanmadi · Не удалось · Not connected"
        val body = if (ok) {
            "Ruxsat berildi. Ulanishni tugatish uchun SADORA ilovasiga qayting.<br>" +
                "Доступ получен. Вернитесь в приложение SADORA, чтобы завершить подключение.<br>" +
                "Access granted. Return to the SADORA app to finish connecting."
        } else {
            "Ruxsat berilmadi yoki havola eskirgan. Ilovada qaytadan urinib ko'ring.<br>" +
                "Доступ не был дан, или ссылка устарела. Попробуйте ещё раз из приложения.<br>" +
                "Access was not granted, or the link is stale. Try again from the app."
        }
        val status = if (ok) "ok" else "error"
        val params = buildString {
            append("status=").append(status)
            if (code != null) append("&code=").append(java.net.URLEncoder.encode(code, Charsets.UTF_8))
            if (state != null) append("&state=").append(java.net.URLEncoder.encode(state, Charsets.UTF_8))
        }
        val link = "$APP_RETURN_LINK?$params".replace("&", "&amp;")
        return """
            <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
            <meta name="robots" content="noindex"><title>SADORA</title>
            ${if (ok) "<meta http-equiv=\"refresh\" content=\"2;url=$link\">" else ""}
            <style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#f7f5ff;color:#1a1630;font:16px/1.5 -apple-system,Segoe UI,Roboto,sans-serif}
            .card{background:#fff;border-radius:20px;padding:28px;max-width:420px;margin:16px;box-shadow:0 8px 30px rgba(123,97,255,.12);text-align:center}
            h1{font-size:20px;margin:0 0 10px}p{color:#6f6a8a;margin:0 0 18px}a{display:inline-block;background:linear-gradient(90deg,#7b61ff,#ff6fb8);color:#fff;text-decoration:none;padding:12px 22px;border-radius:999px;font-weight:600}</style></head>
            <body><div class="card"><h1>$title</h1><p>$body</p><a href="$link">SADORA</a></div></body></html>
        """.trimIndent()
    }
}
