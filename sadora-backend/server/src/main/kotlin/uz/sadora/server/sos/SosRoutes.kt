package uz.sadora.server.sos

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import uz.sadora.contract.SaveSosContactsRequest
import uz.sadora.contract.SosAlertRequest
import uz.sadora.server.api.requireUserId
import uz.sadora.server.plugins.USER_AUTH

/** SOS: her contacts and the alert. */
fun Route.sosRoutes(sos: SosService) {
    authenticate(USER_AUTH) {
        route("/sos") {
            get { call.respond(sos.state(call.requireUserId())) }
            put("/contacts") {
                val request = call.receive<SaveSosContactsRequest>()
                call.respond(sos.saveContacts(call.requireUserId(), request))
            }
            post("/alert") {
                val request = call.receive<SosAlertRequest>()
                call.respond(sos.alert(call.requireUserId(), request))
            }
        }
    }
}
