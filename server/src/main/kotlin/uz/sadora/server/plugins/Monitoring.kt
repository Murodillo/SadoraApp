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
            "${call.request.httpMethod.value} ${call.request.path()} -> $status"
        }
        // Health probes would otherwise dominate the log.
        filter { call -> !call.request.path().startsWith("/health") }
    }
}
