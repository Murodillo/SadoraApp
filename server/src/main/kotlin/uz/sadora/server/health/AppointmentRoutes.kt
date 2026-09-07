package uz.sadora.server.health

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import uz.sadora.contract.Ack
import uz.sadora.contract.CompleteAppointmentRequest
import uz.sadora.contract.SaveAppointmentRequest
import uz.sadora.server.api.requireUserId
import uz.sadora.server.core.parseUuid
import uz.sadora.server.plugins.USER_AUTH

/**
 * Appointments. Like the rest of the health API every route is scoped to the caller, so
 * there is no user id anywhere in these paths.
 */
fun Route.appointmentRoutes(appointments: AppointmentService) {
    authenticate(USER_AUTH) {
        route("/appointments") {
            get {
                call.respond(appointments.list(call.requireUserId()))
            }

            post {
                val request = call.receive<SaveAppointmentRequest>()
                call.respond(HttpStatusCode.Created, appointments.add(call.requireUserId(), request))
            }

            route("/{id}") {
                put {
                    val request = call.receive<SaveAppointmentRequest>()
                    call.respond(appointments.update(call.requireUserId(), call.appointmentId(), request))
                }

                put("/completed") {
                    val request = call.receive<CompleteAppointmentRequest>()
                    call.respond(
                        appointments.setCompleted(call.requireUserId(), call.appointmentId(), request.done),
                    )
                }

                delete {
                    appointments.delete(call.requireUserId(), call.appointmentId())
                    call.respond(Ack())
                }
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.appointmentId() =
    parseUuid(parameters["id"].orEmpty(), "id")
