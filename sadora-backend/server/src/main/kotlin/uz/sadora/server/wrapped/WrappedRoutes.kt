package uz.sadora.server.wrapped

import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import uz.sadora.contract.Ack
import uz.sadora.server.api.requireUserId
import uz.sadora.server.plugins.USER_AUTH

/** Cycle Wrapped: the list, one recap, and "she has opened it". */
fun Route.wrappedRoutes(wrapped: WrappedService) {
    authenticate(USER_AUTH) {
        route("/wrapped") {
            get { call.respond(wrapped.index(call.requireUserId())) }

            get("/{key}") {
                call.respond(wrapped.get(call.requireUserId(), call.parameters["key"].orEmpty()))
            }

            post("/{key}/seen") {
                wrapped.markSeen(call.requireUserId(), call.parameters["key"].orEmpty())
                call.respond(Ack())
            }
        }
    }
}
