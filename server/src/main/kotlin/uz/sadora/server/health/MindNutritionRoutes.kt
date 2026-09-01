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
import uz.sadora.contract.Ack
import uz.sadora.contract.AddWaterRequest
import uz.sadora.contract.LogMealRequest
import uz.sadora.contract.LogPracticeRequest
import uz.sadora.contract.MindCheckIn
import uz.sadora.contract.SaveJournalEntryRequest
import uz.sadora.contract.UpdateJournalEntryRequest
import uz.sadora.contract.UpdateNutritionGoalsRequest
import uz.sadora.server.api.requireUserId
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.parseUuid
import uz.sadora.server.plugins.USER_AUTH

fun Route.mindRoutes(mind: MindService) {
    authenticate(USER_AUTH) {
        route("/mind") {
            get("/today") {
                call.respond(mind.summary(call.requireUserId()))
            }

            put("/check-in") {
                val request = call.receive<MindCheckIn>()
                call.respond(mind.saveCheckIn(call.requireUserId(), request))
            }

            route("/journal") {
                get {
                    val userId = call.requireUserId()
                    val today = mind.summary(userId).today
                    val from = call.dateParameter("from") ?: today.minus(90, DateTimeUnit.DAY)
                    val to = call.dateParameter("to") ?: today
                    call.respond(mind.entries(userId, from, to))
                }

                post {
                    val request = call.receive<SaveJournalEntryRequest>()
                    call.respond(HttpStatusCode.Created, mind.addEntry(call.requireUserId(), request))
                }

                patch("/{id}") {
                    val id = parseUuid(call.parameters["id"].orEmpty(), "id")
                    val request = call.receive<UpdateJournalEntryRequest>()
                    call.respond(mind.updateEntry(call.requireUserId(), id, request))
                }

                delete("/{id}") {
                    val id = parseUuid(call.parameters["id"].orEmpty(), "id")
                    mind.deleteEntry(call.requireUserId(), id)
                    call.respond(Ack())
                }
            }

            post("/practices") {
                val request = call.receive<LogPracticeRequest>()
                call.respond(HttpStatusCode.Created, mind.logPractice(call.requireUserId(), request))
            }
        }
    }
}

fun Route.nutritionRoutes(nutrition: NutritionService) {
    authenticate(USER_AUTH) {
        route("/nutrition") {
            get("/today") {
                call.respond(nutrition.day(call.requireUserId(), null))
            }

            get("/days/{date}") {
                call.respond(nutrition.day(call.requireUserId(), call.pathDate()))
            }

            post("/meals") {
                val request = call.receive<LogMealRequest>()
                call.respond(HttpStatusCode.Created, nutrition.addMeal(call.requireUserId(), request))
            }

            delete("/meals/{id}") {
                val id = parseUuid(call.parameters["id"].orEmpty(), "id")
                nutrition.deleteMeal(call.requireUserId(), id)
                call.respond(Ack())
            }

            post("/water") {
                val request = call.receive<AddWaterRequest>()
                call.respond(nutrition.addWater(call.requireUserId(), request))
            }

            get("/goals") {
                call.respond(nutrition.goals(call.requireUserId()))
            }

            put("/goals") {
                val request = call.receive<UpdateNutritionGoalsRequest>()
                call.respond(nutrition.updateGoals(call.requireUserId(), request))
            }

            get("/foods") {
                call.respond(
                    nutrition.searchFoods(call.requireUserId(), call.request.queryParameters["q"]),
                )
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.dateParameter(name: String): LocalDate? {
    val raw = request.queryParameters[name] ?: return null
    return runCatching { LocalDate.parse(raw) }.getOrElse {
        throw ValidationException(name, "YYYY-MM-DD formatida bo'lishi kerak")
    }
}

private fun io.ktor.server.application.ApplicationCall.pathDate(): LocalDate =
    runCatching { LocalDate.parse(parameters["date"].orEmpty()) }.getOrElse {
        throw ValidationException("date", "YYYY-MM-DD formatida bo'lishi kerak")
    }
