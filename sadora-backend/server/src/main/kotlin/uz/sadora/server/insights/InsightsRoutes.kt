package uz.sadora.server.insights

import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import uz.sadora.server.api.intParameter
import uz.sadora.server.api.requireUserId
import uz.sadora.server.plugins.USER_AUTH

fun Route.insightsRoutes(insights: InsightsService) {
    authenticate(USER_AUTH) {
        /**
         * The Tahlillar window. Seven days is what every account gets; thirty and ninety
         * need `insights_history`, and the narrative needs `ai_insights` — the response
         * says which of those applied rather than leaving the screen to guess.
         */
        get("/insights") {
            call.respond(
                insights.summary(
                    userId = call.requireUserId(),
                    days = call.intParameter("days", default = 7, max = 90),
                ),
            )
        }
    }
}
