package uz.sadora.server.partner

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import uz.sadora.contract.Ack
import uz.sadora.contract.AcceptPartnerInviteRequest
import uz.sadora.contract.CreatePartnerInviteRequest
import uz.sadora.contract.Language
import uz.sadora.contract.PartnerPermissions
import uz.sadora.contract.PausePartnerRequest
import uz.sadora.server.api.requestContext
import uz.sadora.server.api.requireUserId
import uz.sadora.server.plugins.RateLimits
import uz.sadora.server.plugins.USER_AUTH

/**
 * Yaqinim, both sides, behind the user's token. Her side is the bare `/partner`; the
 * follower's is `/partner/accept` and `/partner/following/{id}`. Typing a code sits
 * behind its own per-address limit: codes are short enough to read out, so the door they
 * open must not be tried at speed.
 */
fun Route.partnerRoutes(partners: PartnerService) {
    authenticate(USER_AUTH) {
        route("/partner") {
            get {
                call.respond(partners.state(call.requireUserId()))
            }

            post("/invite") {
                val request = runCatching { call.receive<CreatePartnerInviteRequest>() }.getOrDefault(CreatePartnerInviteRequest())
                call.respond(HttpStatusCode.Created, partners.createInvite(call.requireUserId(), request, call.requestContext().ip))
            }

            post("/approve") {
                call.respond(partners.approve(call.requireUserId(), call.requestContext().ip))
            }

            put("/permissions") {
                val request = call.receive<PartnerPermissions>()
                call.respond(partners.savePermissions(call.requireUserId(), request, call.requestContext().ip))
            }

            post("/pause") {
                val request = call.receive<PausePartnerRequest>()
                call.respond(partners.pause(call.requireUserId(), request.paused, call.requestContext().ip))
            }

            delete {
                call.respond(partners.end(call.requireUserId(), call.requestContext().ip))
            }

            post("/alert/labour") {
                partners.labourAlert(call.requireUserId(), call.requestContext().ip)
                call.respond(Ack())
            }

            rateLimit(RateLimits.PARTNER_CODE) {
                post("/accept") {
                    val request = call.receive<AcceptPartnerInviteRequest>()
                    call.respond(partners.accept(call.requireUserId(), request, call.requestContext().ip))
                }
            }

            route("/following/{id}") {
                get {
                    call.response.header("Cache-Control", "no-store")
                    call.respond(partners.view(call.requireUserId(), call.parameters["id"].orEmpty()))
                }

                delete {
                    partners.leave(call.requireUserId(), call.parameters["id"].orEmpty(), call.requestContext().ip)
                    call.respond(Ack())
                }
            }
        }
    }
}

/**
 * The page a Yaqinim link opens on a phone without the app: what to install and the code
 * to type. It looks nothing up — the code in the path is only echoed back, filtered to
 * the code alphabet — so the page cannot be used to test which codes exist.
 */
fun Route.publicPartnerRoutes() {
    get("/y/{code}") {
        val code = PartnerService.normaliseCode(call.parameters["code"].orEmpty()).take(16)
        val language = call.request.queryParameters["lang"]
            ?.let { raw -> Language.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
            ?: Language.UZ
        call.response.header("Cache-Control", "no-store")
        call.respondText(PartnerInvitePage.render(code, language), ContentType.Text.Html)
    }
}
