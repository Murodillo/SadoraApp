package uz.sadora.server.telegram

import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import uz.sadora.contract.AuthProvider
import uz.sadora.contract.TelegramSettingsRequest
import uz.sadora.contract.TelegramWebAppLinkRequest
import uz.sadora.contract.TelegramWebAppSignInRequest
import uz.sadora.server.api.requestContext
import uz.sadora.server.api.requireUserId
import uz.sadora.server.auth.AuthService
import uz.sadora.server.plugins.RateLimits
import uz.sadora.server.plugins.USER_AUTH

/**
 * The app's side of the bot: whether it is linked, a link to link it, the detail switch, unlinking.
 * And the Mini App's: signing in with Telegram's signed initData, and linking after a phone sign-in.
 */
fun Route.telegramRoutes(telegram: TelegramService, auth: AuthService) {
    rateLimit(RateLimits.AUTH) {
        post("/telegram/webapp/session") {
            val request = call.receive<TelegramWebAppSignInRequest>()
            val userId = telegram.webAppAccount(request.initData)
            call.respond(auth.signInLinked(userId, request.device, AuthProvider.TELEGRAM, call.requestContext()))
        }
    }
    authenticate(USER_AUTH) {
        route("/telegram") {
            get { call.respond(telegram.status(call.requireUserId())) }
            post("/link") { call.respond(telegram.createLink(call.requireUserId())) }
            post("/webapp/link") {
                val request = call.receive<TelegramWebAppLinkRequest>()
                call.respond(telegram.linkWebApp(call.requireUserId(), request.initData))
            }
            put("/settings") {
                val request = call.receive<TelegramSettingsRequest>()
                call.respond(telegram.setDetailed(call.requireUserId(), request.detailed))
            }
            delete { call.respond(telegram.unlink(call.requireUserId())) }
        }
    }
}
