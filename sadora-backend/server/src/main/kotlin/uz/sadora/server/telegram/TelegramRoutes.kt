package uz.sadora.server.telegram

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import uz.sadora.contract.TelegramSettingsRequest
import uz.sadora.server.api.requireUserId
import uz.sadora.server.plugins.USER_AUTH

/** The app's side of the bot: whether it is linked, a link to link it, the detail switch, unlinking. */
fun Route.telegramRoutes(telegram: TelegramService) {
    authenticate(USER_AUTH) {
        route("/telegram") {
            get { call.respond(telegram.status(call.requireUserId())) }
            post("/link") { call.respond(telegram.createLink(call.requireUserId())) }
            put("/settings") {
                val request = call.receive<TelegramSettingsRequest>()
                call.respond(telegram.setDetailed(call.requireUserId(), request.detailed))
            }
            delete { call.respond(telegram.unlink(call.requireUserId())) }
        }
    }
}
