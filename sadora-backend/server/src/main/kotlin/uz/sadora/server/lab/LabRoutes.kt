package uz.sadora.server.lab

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.rateLimit
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
import uz.sadora.contract.LabScanRequest
import uz.sadora.contract.SaveLabReportRequest
import uz.sadora.server.api.intParameter
import uz.sadora.server.api.requireUserId
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.parseUuid
import uz.sadora.server.plugins.RateLimits
import uz.sadora.server.plugins.USER_AUTH

/**
 * Her lab sheets. Scoped to the caller like the rest of the health API: there is no
 * user id in these paths.
 */
fun Route.labRoutes(labs: LabService) {
    authenticate(USER_AUTH) {
        route("/labs") {
            /** One page of sheets, newest first. */
            get {
                val limit = call.intParameter("limit", LabService.LIST_DEFAULT, LabService.LIST_MAX)
                val offset = call.intParameter("offset", 0, Int.MAX_VALUE)
                call.respond(labs.list(call.requireUserId(), limit, offset))
            }

            post {
                val request = call.receive<SaveLabReportRequest>()
                call.respond(HttpStatusCode.Created, labs.create(call.requireUserId(), request))
            }

            /** The front page: a chart per known marker, and whether the scanner works. */
            get("/overview") {
                call.respond(labs.overview(call.requireUserId()))
            }

            get("/markers") {
                call.respond(labs.markers(call.requireUserId()))
            }

            /** Photos in, rows out; nothing is saved. A paid model call, so under the AI limit. */
            rateLimit(RateLimits.AI) {
                post("/scan") {
                    val request = call.receive<LabScanRequest>()
                    call.respond(labs.scan(call.requireUserId(), request))
                }
            }

            route("/{id}") {
                get {
                    call.respond(labs.get(call.requireUserId(), call.labId()))
                }

                put {
                    val request = call.receive<SaveLabReportRequest>()
                    call.respond(labs.update(call.requireUserId(), call.labId(), request))
                }

                delete {
                    labs.delete(call.requireUserId(), call.labId())
                    call.respond(Ack())
                }

                /** A page of the sheet. Health data, so never cached on the way. */
                get("/photos/{page}") {
                    val photo = labs.photo(call.requireUserId(), call.labId(), call.pageParameter())
                    call.response.header(HttpHeaders.CacheControl, "private, no-store")
                    call.respondBytes(photo.bytes, ContentType.parse(photo.mimeType))
                }
            }
        }
    }
}

internal fun io.ktor.server.application.ApplicationCall.labId() =
    parseUuid(parameters["id"].orEmpty(), "id")

internal fun io.ktor.server.application.ApplicationCall.pageParameter(): Int =
    parameters["page"]?.toIntOrNull()?.takeIf { it >= 0 }
        ?: throw ValidationException("page", "Butun son bo'lishi kerak")
