package uz.sadora.server.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.request.path
import uz.sadora.server.config.AppConfig

/**
 * The largest request body an endpoint may carry.
 *
 * Without a ceiling every route — the unauthenticated OTP and webhook ones included —
 * read whatever arrived into memory before looking at it, so one client could fill the
 * heap with a single POST. Two ceilings, because the food scanner legitimately sends a
 * photograph: base64 of a 1024 px JPEG is a few megabytes, nothing else is more than a
 * few kilobytes, and the wearable batch of two thousand samples sits well under a
 * megabyte.
 */
private const val DEFAULT_BODY_LIMIT: Long = 2L * 1024 * 1024
private const val SCAN_BODY_LIMIT: Long = 8L * 1024 * 1024

fun Application.configureHttp(config: AppConfig) {
    install(RequestBodyLimit) {
        bodyLimit { call ->
            if (call.request.path().endsWith("/nutrition/scan")) SCAN_BODY_LIMIT else DEFAULT_BODY_LIMIT
        }
    }

    // The client's address, for the rate limiter, the OTP record and the audit log.
    // Every deployment puts a proxy in front — the gateway that terminates TLS for the
    // sadora.app hostnames and forwards each one to its own port on the server — and the
    // last X-Forwarded-For entry is the address that proxy saw. Without this every request
    // carried the proxy's address, and each per-IP limit was one bucket shared by
    // everybody. In development nothing sets the header and the socket's address is used
    // as before.
    install(XForwardedHeaders) {
        useLastProxy()
    }

    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Referrer-Policy", "no-referrer")
        if (config.environment.isProduction) {
            header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        }
    }

    install(Compression) { gzip() }

    // The two web panels are browser-based, each on a hostname of its own beside the
    // API's; the mobile apps are not subject to CORS.
    install(CORS) {
        config.http.allowedOrigins.forEach { origin ->
            val withoutScheme = origin.substringAfter("://")
            val scheme = origin.substringBefore("://", "https")
            allowHost(withoutScheme, schemes = listOf(scheme))
        }
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(REQUEST_ID_HEADER)
        // The doctors' panel names its device when it renews or revokes a session.
        allowHeader("X-Device-Id")
        exposeHeader(REQUEST_ID_HEADER)
        allowCredentials = true
    }
}
