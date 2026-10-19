package uz.sadora.server.voice

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import uz.sadora.contract.Ack
import uz.sadora.contract.SaveVoiceLogRequest
import uz.sadora.contract.VoiceLogRequest
import uz.sadora.server.api.requireUserId
import uz.sadora.server.plugins.USER_AUTH

/** Logging by voice: whether it is on, hearing a recording, and saving what she kept. */
fun Route.voiceRoutes(voice: VoiceService) {
    authenticate(USER_AUTH) {
        route("/voice") {
            get("/options") { call.respond(voice.options(call.requireUserId())) }
            post("/hear") {
                val request = call.receive<VoiceLogRequest>()
                call.respond(voice.hear(call.requireUserId(), request))
            }
            post("/save") {
                val request = call.receive<SaveVoiceLogRequest>()
                voice.save(call.requireUserId(), request)
                call.respond(Ack())
            }
        }
    }
}
