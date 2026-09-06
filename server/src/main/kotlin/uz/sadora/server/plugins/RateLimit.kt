package uz.sadora.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.origin
import kotlin.time.Duration.Companion.minutes
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
 */
fun Application.configureRateLimit() {
    install(RateLimit) {
        register(OTP) {
            rateLimiter(limit = 10, refillPeriod = 10.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
        register(AUTH) {
            rateLimiter(limit = 30, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
        register(AI) {
            rateLimiter(limit = 30, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }
}
