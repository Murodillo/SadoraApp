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
import kotlinx.datetime.LocalDate
import uz.sadora.contract.Ack
import uz.sadora.contract.RecordDoseRequest
import uz.sadora.contract.RefillRequest
import uz.sadora.contract.SaveMedicationRequest
import uz.sadora.server.api.intParameter
import uz.sadora.server.api.requireUserId
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.parseUuid
import uz.sadora.server.plugins.USER_AUTH

fun Route.medicationRoutes(meds: MedicationService) {
    authenticate(USER_AUTH) {
        route("/meds") {
            get {
                val includeArchived = call.request.queryParameters["includeArchived"].toBoolean()
                call.respond(meds.list(call.requireUserId(), includeArchived))
            }

            post {
                val request = call.receive<SaveMedicationRequest>()
                call.respond(HttpStatusCode.Created, meds.add(call.requireUserId(), request))
            }

            /** The Meds tab opens on this: everything due today, in time order. */
            get("/today") {
                call.respond(meds.day(call.requireUserId(), null))
            }

            get("/days/{date}") {
                val date = runCatching { LocalDate.parse(call.parameters["date"].orEmpty()) }
                    .getOrElse { throw ValidationException("date", "YYYY-MM-DD formatida bo'lishi kerak") }
                call.respond(meds.day(call.requireUserId(), date))
            }

            put("/{id}") {
                val id = parseUuid(call.parameters["id"].orEmpty(), "id")
                val request = call.receive<SaveMedicationRequest>()
                call.respond(meds.update(call.requireUserId(), id, request))
            }

            delete("/{id}") {
                val id = parseUuid(call.parameters["id"].orEmpty(), "id")
                meds.archive(call.requireUserId(), id)
                call.respond(Ack())
            }

            /** Records taken or skipped. Sending `pending` undoes a mistaken tap. */
            post("/{id}/doses") {
                val id = parseUuid(call.parameters["id"].orEmpty(), "id")
                val request = call.receive<RecordDoseRequest>()
                call.respond(meds.recordDose(call.requireUserId(), id, request))
            }

            post("/{id}/stock") {
                val id = parseUuid(call.parameters["id"].orEmpty(), "id")
                val request = call.receive<RefillRequest>()
                call.respond(meds.refill(call.requireUserId(), id, request))
            }

            get("/{id}/history") {
                val id = parseUuid(call.parameters["id"].orEmpty(), "id")
                call.respond(meds.history(call.requireUserId(), id, call.intParameter("days", 14, 180)))
            }
        }
    }
}
