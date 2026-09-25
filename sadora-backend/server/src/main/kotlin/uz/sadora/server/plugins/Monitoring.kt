package uz.sadora.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.header
import org.slf4j.event.Level
import uz.sadora.server.core.randomToken

const val REQUEST_ID_HEADER: String = "X-Request-Id"

/**
 * Request logging, with two things deliberately absent: request bodies and query
 * strings. Bodies carry health data, and a log line is the easiest place for it to leak
 * into a system nobody audited.
 */
fun Application.configureMonitoring() {
    install(CallId) {
        header(REQUEST_ID_HEADER)
        generate { randomToken(12) }
        verify { it.isNotBlank() && it.length <= 64 }
        reply { call, callId -> call.response.header(REQUEST_ID_HEADER, callId) }
    }
    install(CallLogging) {
        level = Level.INFO
        callIdMdc("requestId")
        format { call ->
            val status = call.response.status()?.value ?: "-"
            "${call.request.httpMethod.value} ${redactPath(call.request.path())} -> $status"
        }
        // Health probes would otherwise dominate the log.
        filter { call -> !call.request.path().startsWith("/health") }
    }
}

/**
 * The path as it may be logged. A doctor-share link carries its bearer token in the
 * path — whoever reads `/share/<token>` from a log can open up to a week of her health
 * data — so that segment is masked. Query strings never reach here: [path] drops them,
 * and callers log this rather than the full URI, which is where OAuth codes travel.
 */
internal fun redactPath(path: String): String =
    SHARE_TOKEN.replace(path) { "${it.groupValues[1]}***" }

private val SHARE_TOKEN = Regex("(/share/)[^/?]+")
