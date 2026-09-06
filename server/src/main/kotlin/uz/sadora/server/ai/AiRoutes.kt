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
import uz.sadora.server.admin.AdminService
import uz.sadora.server.api.intParameter
import uz.sadora.server.api.requireAdminRole
import uz.sadora.server.api.requireUserId
import uz.sadora.server.plugins.ADMIN_AUTH
import uz.sadora.server.plugins.AdminRole
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

/**
 * What the AI costs, for the panel.
 *
 * There is nothing here to read a conversation with — the log has no text — so this is
 * the whole of the operator's view: how many answers, from what, at what price, and what
 * went wrong when the model did not answer.
 */
fun Route.adminAiRoutes(ai: AiService, admin: AdminService) {
    authenticate(ADMIN_AUTH) {
        route("/admin/ai") {
            get("/usage") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.ANALYST)
                val modelEnabled = admin.flags().firstOrNull { it.key == AiService.MODEL_FLAG }?.enabled ?: false
                call.respond(ai.usageReport(call.intParameter("days", default = 14, max = 90), modelEnabled))
            }
        }
    }
}
