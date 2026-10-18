package uz.sadora.server.kegel

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import uz.sadora.contract.KegelSettingsRequest
import uz.sadora.contract.LogKegelSessionRequest
import uz.sadora.contract.SaveKegelCheckRequest
import uz.sadora.server.api.requireUserId
import uz.sadora.server.plugins.USER_AUTH

/** Kegel exercises: her state, a finished session, her reminders and the leak check. */
fun Route.kegelRoutes(kegel: KegelService) {
    authenticate(USER_AUTH) {
        route("/kegel") {
            get { call.respond(kegel.state(call.requireUserId())) }

            post("/sessions") {
                val request = call.receive<LogKegelSessionRequest>()
                call.respond(kegel.logSession(call.requireUserId(), request))
            }

            put("/settings") {
                val request = call.receive<KegelSettingsRequest>()
                call.respond(kegel.saveSettings(call.requireUserId(), request))
            }

            post("/checks") {
                val request = call.receive<SaveKegelCheckRequest>()
                call.respond(kegel.saveCheck(call.requireUserId(), request))
            }
        }
    }
}
