package uz.sadora.server.health

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import uz.sadora.contract.Ack
import uz.sadora.contract.LifeStage
import uz.sadora.contract.LogPeriodRequest
import uz.sadora.contract.SaveDailyLogRequest
import uz.sadora.contract.UpdatePeriodRequest
import uz.sadora.server.api.enumParameter
import uz.sadora.server.api.requireUserId
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.parseUuid
import uz.sadora.server.plugins.USER_AUTH

/**
 * Cycle and daily logging. Everything here reads and writes one user's own health data,
 * so every route is behind [USER_AUTH] and scoped to the caller — there is no user id in
 * any path.
 */
fun Route.healthRoutes(health: HealthService) {
    authenticate(USER_AUTH) {

        route("/cycle") {
            get("/status") {
                call.respond(health.status(call.requireUserId()))
            }

            /**
             * Defaults to the month around today, which is what the calendar opens on.
             */
            get("/calendar") {
                val userId = call.requireUserId()
                val today = health.status(userId).today
                val from = call.dateParameter("from") ?: today.minus(35, DateTimeUnit.DAY)
                val to = call.dateParameter("to") ?: today.plus(35, DateTimeUnit.DAY)
                call.respond(health.calendar(userId, from, to))
            }

            get("/history") {
                call.respond(health.history(call.requireUserId()))
            }

            route("/periods") {
                get {
                    call.respond(health.periods(call.requireUserId()))
                }

                post {
                    val request = call.receive<LogPeriodRequest>()
                    call.respond(HttpStatusCode.Created, health.addPeriod(call.requireUserId(), request))
                }

                patch("/{id}") {
                    val id = parseUuid(call.parameters["id"].orEmpty(), "id")
                    val request = call.receive<UpdatePeriodRequest>()
                    call.respond(health.updatePeriod(call.requireUserId(), id, request))
                }

                delete("/{id}") {
                    val id = parseUuid(call.parameters["id"].orEmpty(), "id")
                    health.deletePeriod(call.requireUserId(), id)
                    call.respond(Ack())
                }
            }
        }

        route("/days") {
            get {
                val userId = call.requireUserId()
                val from = call.dateParameter("from")
                    ?: throw ValidationException("from", "Ko'rsatilishi shart")
                val to = call.dateParameter("to") ?: from
                call.respond(health.logs(userId, from, to))
            }

            get("/{date}") {
                call.respond(health.log(call.requireUserId(), call.pathDate()))
            }

            put("/{date}") {
                val request = call.receive<SaveDailyLogRequest>()
                call.respond(health.saveLog(call.requireUserId(), call.pathDate(), request))
            }

            delete("/{date}") {
                health.deleteLog(call.requireUserId(), call.pathDate())
                call.respond(Ack())
            }
        }

        get("/symptoms") {
            call.respond(
                health.symptomCatalogue(call.requireUserId(), call.enumParameter<LifeStage>("lifeStage")),
            )
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.dateParameter(name: String): LocalDate? {
    val raw = request.queryParameters[name] ?: return null
    return runCatching { LocalDate.parse(raw) }.getOrElse {
        throw ValidationException(name, "YYYY-MM-DD formatida bo'lishi kerak")
    }
}

private fun io.ktor.server.application.ApplicationCall.pathDate(): LocalDate {
    val raw = parameters["date"].orEmpty()
    return runCatching { LocalDate.parse(raw) }.getOrElse {
        throw ValidationException("date", "YYYY-MM-DD formatida bo'lishi kerak")
    }
}
