package uz.sadora.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.origin
import kotlin.time.Duration.Companion.minutes
import uz.sadora.server.config.AppConfig
import uz.sadora.server.plugins.RateLimits.AI
import uz.sadora.server.plugins.RateLimits.AUTH
import uz.sadora.server.plugins.RateLimits.OTP

object RateLimits {
    val OTP = RateLimitName("otp")
    val AUTH = RateLimitName("auth")
    val AI = RateLimitName("ai")
}

/**
 * Coarse per-IP limits in front of the expensive and abusable endpoints. The precise,
 * per-account caps live in [uz.sadora.server.auth.OtpService] and the entitlement
 * service; this layer exists to stop a flood before it reaches the database at all.
 *
 * Development multiplies every limit. Seeding, a demo walkthrough and an automated pass
 * over the API all come from one address there, and would spend a production OTP budget
 * in a few seconds — while in production that same shape of traffic is exactly what the
 * limit is for. The per-account caps are not relaxed, so the rule each endpoint actually
 * relies on still holds.
 */
fun Application.configureRateLimit(config: AppConfig) {
    val relax = if (config.environment.isProduction) 1 else 20

    install(RateLimit) {
        register(OTP) {
            rateLimiter(limit = 10 * relax, refillPeriod = 10.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
        register(AUTH) {
            rateLimiter(limit = 30 * relax, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
        register(AI) {
            rateLimiter(limit = 30 * relax, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }
}
