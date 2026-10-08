package uz.sadora.server.prescription

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import uz.sadora.contract.AddPrescriptionRequest
import uz.sadora.contract.CancelPrescriptionRequest
import uz.sadora.contract.SendPrescriptionRequest
import uz.sadora.server.api.requireUserId
import uz.sadora.server.community.MessagingService
import uz.sadora.server.core.parseUuid
import uz.sadora.server.plugins.USER_AUTH

/**
 * Prescriptions. A doctor sends one into a consultation's thread; afterwards it is read,
 * cancelled and added under `/prescriptions/{id}`, the same for the app and the panel.
 */
fun Route.prescriptionRoutes(messaging: MessagingService, prescriptions: PrescriptionService) {
    authenticate(USER_AUTH) {
        route("/community/conversations/{id}/prescriptions") {
            /** The doctor writes one; the answer is the new line, as the thread shows it. */
            post {
                val request = call.receive<SendPrescriptionRequest>()
                val conversationId = parseUuid(call.parameters["id"].orEmpty(), "id")
                call.respond(HttpStatusCode.Created, messaging.sendPrescription(call.requireUserId(), conversationId, request))
            }
            /** Every prescription in the consultation, newest first. */
            get {
                val conversationId = parseUuid(call.parameters["id"].orEmpty(), "id")
                call.respond(prescriptions.ofConversation(call.requireUserId(), conversationId))
            }
        }

        route("/prescriptions") {
            /** The patient's own, from every doctor. */
            get {
                call.respond(prescriptions.mine(call.requireUserId()))
            }
            route("/{prescriptionId}") {
                get {
                    call.respond(prescriptions.get(call.requireUserId(), call.prescriptionId()))
                }
                post("/cancel") {
                    val request = call.receive<CancelPrescriptionRequest>()
                    call.respond(prescriptions.cancel(call.requireUserId(), call.prescriptionId(), request))
                }
                post("/add") {
                    val request = call.receive<AddPrescriptionRequest>()
                    call.respond(prescriptions.add(call.requireUserId(), call.prescriptionId(), request))
                }
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.prescriptionId() =
    parseUuid(parameters["prescriptionId"].orEmpty(), "prescriptionId")
