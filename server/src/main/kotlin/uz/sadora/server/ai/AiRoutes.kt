package uz.sadora.server.ai

import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import uz.sadora.contract.AiChatRequest
import uz.sadora.server.api.requireUserId
import uz.sadora.server.plugins.RateLimits
import uz.sadora.server.plugins.USER_AUTH

fun Route.aiRoutes(ai: AiService) {
    authenticate(USER_AUTH) {
        route("/ai") {
            /** What the chat screen shows before the first question. */
            get("/chat/quota") {
                call.respond(ai.quota(call.requireUserId()))
            }

            // The per-IP limit is the flood guard; the per-account allowance is inside.
            rateLimit(RateLimits.AI) {
                post("/chat") {
                    val request = call.receive<AiChatRequest>()
                    call.respond(ai.chat(call.requireUserId(), request))
                }
            }
        }
    }
}
