package uz.sadora.server.baby

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import uz.sadora.contract.Ack
import uz.sadora.contract.GrowthMetric
import uz.sadora.contract.MarkMilestoneRequest
import uz.sadora.contract.MarkVaccineRequest
import uz.sadora.contract.SaveBabyRequest
import uz.sadora.contract.SaveGrowthRequest
import uz.sadora.server.api.enumParameter
import uz.sadora.server.api.intParameter
import uz.sadora.server.api.requireUserId
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.parseUuid
import uz.sadora.server.plugins.USER_AUTH

/**
 * Her babies. Feeds, sleeps and nappies are written through `/stage-events` with a
 * `babyId`; everything else about a baby is here.
 */
fun Route.babyRoutes(babies: BabyService) {
    authenticate(USER_AUTH) {
        route("/babies") {
            get { call.respond(babies.list(call.requireUserId())) }

            post {
                val request = call.receive<SaveBabyRequest>()
                call.respond(HttpStatusCode.Created, babies.create(call.requireUserId(), request))
            }

            route("/{babyId}") {
                put {
                    call.respond(babies.update(call.requireUserId(), call.babyId(), call.receive<SaveBabyRequest>()))
                }

                delete {
                    babies.delete(call.requireUserId(), call.babyId())
                    call.respond(Ack())
                }

                /** The front page: the last days, what runs now, what is next. */
                get("/overview") {
                    val days = call.intParameter("days", BabyService.OVERVIEW_DAYS, BabyService.MAX_OVERVIEW_DAYS)
                    call.respond(babies.overview(call.requireUserId(), call.babyId(), days.coerceAtLeast(1)))
                }

                route("/growth") {
                    get { call.respond(babies.growth(call.requireUserId(), call.babyId())) }

                    post {
                        val request = call.receive<SaveGrowthRequest>()
                        call.respond(HttpStatusCode.Created, babies.addGrowth(call.requireUserId(), call.babyId(), request))
                    }

                    /** The WHO percentile chart for one metric. Premium. */
                    get("/chart") {
                        val metric = call.enumParameter<GrowthMetric>("metric")
                            ?: throw ValidationException("metric", "Noto'g'ri format")
                        call.respond(babies.chart(call.requireUserId(), call.babyId(), metric))
                    }

                    put("/{entryId}") {
                        val request = call.receive<SaveGrowthRequest>()
                        call.respond(babies.updateGrowth(call.requireUserId(), call.babyId(), call.entryId(), request))
                    }

                    delete("/{entryId}") {
                        babies.deleteGrowth(call.requireUserId(), call.babyId(), call.entryId())
                        call.respond(Ack())
                    }
                }

                route("/vaccines") {
                    get { call.respond(babies.vaccines(call.requireUserId(), call.babyId())) }

                    put("/{code}") {
                        val request = call.receive<MarkVaccineRequest>()
                        call.respond(babies.markVaccine(call.requireUserId(), call.babyId(), call.code(), request))
                    }

                    delete("/{code}") {
                        call.respond(babies.unmarkVaccine(call.requireUserId(), call.babyId(), call.code()))
                    }
                }

                route("/milestones") {
                    get { call.respond(babies.milestones(call.requireUserId(), call.babyId())) }

                    put("/{code}") {
                        val request = call.receive<MarkMilestoneRequest>()
                        call.respond(babies.markMilestone(call.requireUserId(), call.babyId(), call.code(), request))
                    }

                    delete("/{code}") {
                        call.respond(babies.unmarkMilestone(call.requireUserId(), call.babyId(), call.code()))
                    }

                    /** The photo of a milestone. Private, so never cached on the way. */
                    get("/{code}/photo") {
                        val (mime, bytes) = babies.milestonePhoto(call.requireUserId(), call.babyId(), call.code())
                        call.response.header(HttpHeaders.CacheControl, "private, no-store")
                        call.respondBytes(bytes, ContentType.parse(mime))
                    }
                }
            }
        }
    }
}

private fun ApplicationCall.babyId() = parseUuid(parameters["babyId"].orEmpty(), "babyId")

private fun ApplicationCall.entryId() = parseUuid(parameters["entryId"].orEmpty(), "entryId")

private fun ApplicationCall.code(): String =
    parameters["code"]?.takeIf { it.length in 1..40 } ?: throw ValidationException("code", "Noto'g'ri format")
