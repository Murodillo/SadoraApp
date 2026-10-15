package uz.sadora.server.widget

import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import uz.sadora.server.api.requireUserId
import uz.sadora.server.plugins.USER_AUTH

fun Route.widgetRoutes(widgets: WidgetService) {
    authenticate(USER_AUTH) {
        get("/widget/summary") {
            call.respond(widgets.summary(call.requireUserId()))
        }
    }
}
