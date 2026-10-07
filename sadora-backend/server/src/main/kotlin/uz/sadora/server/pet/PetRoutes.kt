package uz.sadora.server.pet

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import uz.sadora.contract.ChoosePetRequest
import uz.sadora.contract.PetNudgeRequest
import uz.sadora.server.api.requireUserId
import uz.sadora.server.plugins.USER_AUTH

fun Route.petRoutes(pets: PetService) {
    authenticate(USER_AUTH) {
        route("/pet") {
            get {
                call.respond(pets.state(call.requireUserId()))
            }

            put {
                val request = call.receive<ChoosePetRequest>()
                call.respond(pets.choose(call.requireUserId(), request.pet))
            }

            /** 402 for a free account; an empty answer when the pet stays quiet. */
            post("/nudge") {
                val request = call.receive<PetNudgeRequest>()
                call.respond(pets.nudge(call.requireUserId(), request))
            }
        }
    }
}
