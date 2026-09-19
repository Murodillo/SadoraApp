package uz.sadora.server.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import uz.sadora.server.config.AppConfig

fun Application.configureHttp(config: AppConfig) {
    // The client's address, for the rate limiter, the OTP record and the audit log.
    // Every deployment puts a proxy in front and binds the API to loopback or the compose
    // network, so X-Forwarded-For is always the proxy's word, never the caller's: Caddy
    // replaces whatever a client sent with the address it actually saw (deploy/Caddyfile),
    // and on stage with Cloudflare's CF-Connecting-IP (deploy/stage/Caddyfile). Without
    // this every request carried Caddy's address, and each per-IP limit was one bucket
    // shared by everybody. In development nothing sets the header and the socket's
    // address is used as before.
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

    // Only the admin panel is browser-based; the mobile apps are not subject to CORS.
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
        exposeHeader(REQUEST_ID_HEADER)
        allowCredentials = true
    }
}
