package uz.sadora.server.health

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import uz.sadora.contract.Ack
import uz.sadora.contract.LogStageEventRequest
import uz.sadora.contract.StageEventKind
import uz.sadora.server.api.enumParameter
import uz.sadora.server.api.intParameter
import uz.sadora.server.api.requireUserId
import uz.sadora.server.core.parseUuid
import uz.sadora.server.plugins.USER_AUTH

/**
 * `GET /stage-events?kind=feeding&days=7` — her events of one kind (or all), newest
 * first, over the last few days; `POST` to add one, `DELETE /{id}` to take one back.
 */
fun Route.stageEventRoutes(events: StageEventService) {
    authenticate(USER_AUTH) {
        route("/stage-events") {
            get {
                val days = call.intParameter("days", default = 7, max = 120)
                call.respond(events.list(call.requireUserId(), call.enumParameter<StageEventKind>("kind"), days))
            }

            post {
                val request = call.receive<LogStageEventRequest>()
                call.respond(HttpStatusCode.Created, events.add(call.requireUserId(), request))
            }

            delete("/{id}") {
                events.delete(call.requireUserId(), parseUuid(call.parameters["id"].orEmpty(), "id"))
                call.respond(Ack())
            }
        }
    }
}
