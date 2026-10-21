package uz.sadora.doctor.data.netlog

import io.ktor.client.HttpClient
import io.ktor.client.call.save
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.contentLength
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock

/** One request the app sent, and what came back — or why nothing did. */
data class NetworkCall(
    val id: Long,
    /** Epoch milliseconds, when the request left. */
    val startedAt: Long,
    val method: String,
    val url: String,
    val requestHeaders: List<Pair<String, String>>,
    val requestBody: String?,
    /** Null while the call is in flight, and when it failed without an answer. */
    val status: Int? = null,
    val responseHeaders: List<Pair<String, String>> = emptyList(),
    val responseBody: String? = null,
    val durationMs: Long? = null,
    /** Set when no HTTP answer arrived: refused, timed out, cancelled. */
    val error: String? = null,
) {
    val inFlight: Boolean get() = status == null && error == null
}

/**
 * The network inspector's memory: the last [capacity] calls, newest first.
 *
 * Only staging and development builds have one ([uz.sadora.doctor.data.SadoraEnvironment.networkInspector]);
 * a production build never records a request. What it keeps lives in memory and goes with
 * the process — the bodies carry health data, so nothing is written to disk — and secrets
 * are masked before they are kept: the bearer token, cookies, sign-in codes and tokens in
 * a body.
 */
@OptIn(ExperimentalAtomicApi::class)
class NetworkLog(private val capacity: Int = 150) {
    private val _calls = MutableStateFlow<List<NetworkCall>>(emptyList())
    val calls: StateFlow<List<NetworkCall>> = _calls.asStateFlow()
    // Requests start on several threads at once.
    private val nextId = AtomicLong(0L)

    fun clear() = _calls.update { emptyList() }

    internal fun started(method: String, url: String, headers: Headers, body: String?): Long {
        val id = nextId.fetchAndAdd(1L)
        val call = NetworkCall(
            id = id,
            startedAt = Clock.System.now().toEpochMilliseconds(),
            method = method,
            url = url,
            requestHeaders = maskHeaders(headers),
            requestBody = body?.let { maskBody(it, url) },
        )
        _calls.update { (listOf(call) + it).take(capacity) }
        return id
    }

    internal fun answered(id: Long, status: Int, headers: Headers, body: String?) = change(id) {
        it.copy(
            status = status,
            responseHeaders = maskHeaders(headers),
            responseBody = body?.let { b -> maskBody(b, it.url) },
            durationMs = elapsed(it),
        )
    }

    internal fun failed(id: Long, error: String) = change(id) { it.copy(error = error, durationMs = elapsed(it)) }

    private fun change(id: Long, edit: (NetworkCall) -> NetworkCall) =
        _calls.update { calls -> calls.map { if (it.id == id) edit(it) else it } }

    private fun elapsed(call: NetworkCall) = Clock.System.now().toEpochMilliseconds() - call.startedAt

    internal companion object {
        /** A body longer than this is cut; the inspector is for reading, not for archives. */
        const val MAX_BODY = 64 * 1024
        const val MASK = "••••"

        private val secretHeaders = setOf(
            HttpHeaders.Authorization.lowercase(), HttpHeaders.Cookie.lowercase(), HttpHeaders.SetCookie.lowercase(),
        )

        /** Body fields whose value is masked wherever they appear. */
        private val secretFields = setOf(
            "password", "otp", "token", "accesstoken", "access_token", "refreshtoken", "refresh_token",
            "pushtoken", "push_token", "secret", "idtoken", "id_token",
        )

        fun maskHeaders(headers: Headers): List<Pair<String, String>> =
            headers.entries().flatMap { (name, values) ->
                values.map { name to if (name.lowercase() in secretHeaders) MASK else it }
            }.sortedBy { it.first.lowercase() }

        /**
         * Masks the secret fields of a JSON body; anything else is kept as it is. On the
         * sign-in routes `code` is the one-time password, so it is masked there too.
         */
        fun maskBody(body: String, url: String): String {
            val cut = if (body.length > MAX_BODY) body.take(MAX_BODY) + "\n… (${body.length - MAX_BODY} more characters)" else body
            if (body.length > MAX_BODY) return cut
            // Only an object or an array is JSON worth reading; anything else — a placeholder
            // such as <image/jpeg>, plain text — is shown as it came.
            val start = body.trimStart().firstOrNull()
            if (start != '{' && start != '[') return cut
            val element = runCatching { Json.parseToJsonElement(body) }.getOrNull() ?: return cut
            val auth = "/auth/" in url
            return pretty.encodeToString(JsonElement.serializer(), mask(element, auth))
        }

        private fun mask(element: JsonElement, auth: Boolean): JsonElement = when (element) {
            is JsonObject -> JsonObject(element.mapValues { (key, value) ->
                val k = key.lowercase()
                if (k in secretFields || (auth && k == "code")) JsonPrimitive(MASK) else mask(value, auth)
            })
            is JsonArray -> JsonArray(element.map { mask(it, auth) })
            else -> element
        }

        private val pretty = Json { prettyPrint = true }

        fun isText(type: ContentType?): Boolean = type != null && (
            type.contentType == "text" ||
                type.match(ContentType.Application.Json) ||
                type.contentSubtype.endsWith("+json") ||
                type.match(ContentType.Application.Xml) ||
                type.match(ContentType.Application.FormUrlEncoded)
            )

        fun describe(content: Any): String? = when (content) {
            is TextContent -> content.text
            is OutgoingContent.ByteArrayContent ->
                if (isText(content.contentType)) content.bytes().decodeToString() else "<${content.bytes().size} bytes, ${content.contentType}>"
            is OutgoingContent.NoContent -> null
            is OutgoingContent -> "<${content.contentType ?: "stream"}${content.contentLength?.let { ", $it bytes" } ?: ""}>"
            else -> null
        }
    }
}

/**
 * Records every request this client sends into [log], at the last step before the engine —
 * so the body is the JSON that actually went, and a retry after a token refresh shows up
 * as a call of its own. A text answer of a sane size is read into memory once and handed
 * on as such; a photo, a video or a file streams past untouched and shows as its size.
 */
internal fun HttpClient.recordInto(log: NetworkLog) {
    plugin(HttpSend).intercept { request ->
        val id = log.started(
            method = request.method.value,
            url = request.url.buildString(),
            headers = request.headers.build(),
            body = NetworkLog.describe(request.body),
        )
        try {
            val call = execute(request)
            val response = call.response
            val small = (response.contentLength() ?: 0L) <= 1_000_000L
            if (NetworkLog.isText(response.contentType()) && small) {
                val saved = call.save()
                log.answered(id, saved.response.status.value, saved.response.headers, saved.response.bodyAsText())
                saved
            } else {
                val length = response.contentLength()
                log.answered(id, response.status.value, response.headers,
                    "<${response.contentType() ?: "no content type"}${length?.let { ", $it bytes" } ?: ""}>")
                call
            }
        } catch (e: CancellationException) {
            log.failed(id, "cancelled")
            throw e
        } catch (e: Throwable) {
            log.failed(id, e.message?.takeIf { it.isNotBlank() } ?: (e::class.simpleName ?: "error"))
            throw e
        }
    }
}
