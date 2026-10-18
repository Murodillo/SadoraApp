package uz.sadora.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.principal
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.opentelemetry.api.trace.Span
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
    install(TraceAttributes)
}

/**
 * What the OTel Java agent cannot know about a request's span on its own: who made it,
 * and the path as it may be stored. Without the agent (local runs, production today)
 * [Span.current] is a no-op and this costs nothing.
 *
 * The agent records `url.path` raw, so a doctor-share token would sit in every trace of
 * that link; it is overwritten with [redactPath]. The collector masks it again, and drops
 * query strings, in case a span is ever recorded where this hook does not run.
 */
private val TraceAttributes = createApplicationPlugin("TraceAttributes") {
    onCallRespond { call ->
        val span = Span.current()
        if (!span.spanContext.isValid) return@onCallRespond
        span.setAttribute("url.path", redactPath(call.request.path()))
        // Her account id only — never a phone number or a name. Admins are kept apart,
        // so a search for one id never mixes an operator's calls into a user's.
        call.principal<UserPrincipal>()?.let { span.setAttribute("enduser.id", it.userId.toString()) }
        call.principal<BlockedPrincipal>()?.let { span.setAttribute("enduser.id", it.userId.toString()) }
        call.principal<AdminPrincipal>()?.let { span.setAttribute("sadora.admin.id", it.adminId.toString()) }
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
