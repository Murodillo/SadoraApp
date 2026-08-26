package uz.sadora.server.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import uz.sadora.contract.Ack
import uz.sadora.contract.EmailRegisterRequest
import uz.sadora.contract.EmailSignInRequest
import uz.sadora.contract.LogoutRequest
import uz.sadora.contract.OtpRequest
import uz.sadora.contract.OtpVerifyRequest
import uz.sadora.contract.RefreshRequest
import uz.sadora.contract.SocialSignInRequest
import uz.sadora.server.api.deviceIdHeader
import uz.sadora.server.api.requestContext
import uz.sadora.server.api.requireUserId
import uz.sadora.server.plugins.RateLimits
import uz.sadora.server.plugins.USER_AUTH

fun Route.authRoutes(authService: AuthService, otpService: OtpService) {
    route("/auth") {

        // OTP requests send an SMS and therefore cost money — they get the tightest
        // per-IP limit in the API, on top of the per-phone cap inside the service.
        rateLimit(RateLimits.OTP) {
            post("/otp/request") {
                val request = call.receive<OtpRequest>()
                val challenge = otpService.request(request.phone, call.request.origin.remoteHost)
                call.respond(HttpStatusCode.Created, challenge)
            }
        }

        rateLimit(RateLimits.AUTH) {
            post("/otp/verify") {
                val request = call.receive<OtpVerifyRequest>()
                call.respond(authService.signInWithOtp(request, call.requestContext()))
            }

            post("/social") {
                val request = call.receive<SocialSignInRequest>()
                call.respond(authService.signInWithSocial(request, call.requestContext()))
            }

            post("/email/register") {
                val request = call.receive<EmailRegisterRequest>()
                call.respond(
                    HttpStatusCode.Created,
                    authService.registerWithEmail(request, call.requestContext()),
                )
            }

            post("/email/login") {
                val request = call.receive<EmailSignInRequest>()
                call.respond(authService.signInWithEmail(request, call.requestContext()))
            }

            post("/refresh") {
                val request = call.receive<RefreshRequest>()
                call.respond(authService.refresh(request.refreshToken, call.deviceIdHeader()))
            }
        }

        authenticate(USER_AUTH) {
            post("/logout") {
                val request = call.receive<LogoutRequest>()
                authService.logout(
                    userId = call.requireUserId(),
                    rawRefreshToken = request.refreshToken,
                    allDevices = request.allDevices,
                    context = call.requestContext(),
                )
                call.respond(Ack())
            }
        }
    }
}
