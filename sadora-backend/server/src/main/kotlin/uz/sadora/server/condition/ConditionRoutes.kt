package uz.sadora.server.condition

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.datetime.LocalDate
import uz.sadora.contract.Ack
import uz.sadora.contract.HealthCondition
import uz.sadora.contract.SavePainLogRequest
import uz.sadora.contract.SetConditionRequest
import uz.sadora.server.api.intParameter
import uz.sadora.server.api.requireUserId
import uz.sadora.server.core.ValidationException
import uz.sadora.server.plugins.USER_AUTH

/** "Holatlarim", the pain diary and the condition report. Scoped to the caller. */
fun Route.conditionRoutes(conditions: ConditionService) {
    authenticate(USER_AUTH) {
        route("/conditions") {
            get { call.respond(conditions.state(call.requireUserId())) }

            /** The 3- or 6-month report. Premium. */
            get("/report") {
                val months = call.intParameter("months", 3, 6)
                call.respond(conditions.report(call.requireUserId(), months))
            }

            put("/{condition}") {
                val request = call.receive<SetConditionRequest>()
                call.respond(conditions.set(call.requireUserId(), call.condition(), request))
            }

            delete("/{condition}") {
                call.respond(conditions.remove(call.requireUserId(), call.condition()))
            }
        }

        route("/pain") {
            get {
                val days = call.intParameter("days", 30, ConditionService.MAX_PAIN_DAYS)
                call.respond(conditions.pain(call.requireUserId(), days))
            }

            put("/{date}") {
                val request = call.receive<SavePainLogRequest>()
                call.respond(conditions.savePain(call.requireUserId(), call.day(), request))
            }

            delete("/{date}") {
                conditions.deletePain(call.requireUserId(), call.day())
                call.respond(Ack())
            }
        }
    }
}

private fun ApplicationCall.condition(): HealthCondition =
    HealthCondition.entries.firstOrNull { it.name.equals(parameters["condition"], ignoreCase = true) }
        ?: throw ValidationException("condition", "Noto'g'ri format")

private fun ApplicationCall.day(): LocalDate =
    runCatching { LocalDate.parse(parameters["date"].orEmpty()) }.getOrNull()
        ?: throw ValidationException("date", "Sana noto'g'ri")
