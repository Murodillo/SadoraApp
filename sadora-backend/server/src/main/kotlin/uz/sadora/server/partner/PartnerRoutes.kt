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
import uz.sadora.contract.CreatePartnerWebLinkRequest
import uz.sadora.contract.SendPartnerMessageRequest
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

            route("/links/{id}/messages") {
                get {
                    call.response.header("Cache-Control", "no-store")
                    call.respond(partners.messages(call.requireUserId(), call.parameters["id"].orEmpty()))
                }
                post {
                    val request = call.receive<SendPartnerMessageRequest>()
                    call.respond(HttpStatusCode.Created, partners.send(call.requireUserId(), call.parameters["id"].orEmpty(), request))
                }
                post("/read") {
                    call.respond(partners.markRead(call.requireUserId(), call.parameters["id"].orEmpty()))
                }
            }

            route("/web") {
                post {
                    val request = runCatching { call.receive<CreatePartnerWebLinkRequest>() }.getOrDefault(CreatePartnerWebLinkRequest())
                    call.respond(HttpStatusCode.Created, partners.createWebLink(call.requireUserId(), request, call.requestContext().ip))
                }
                delete {
                    call.respond(partners.revokeWebLink(call.requireUserId(), call.requestContext().ip))
                }
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

                put("/payment-requests") {
                    val request = call.receive<uz.sadora.contract.PaymentRequestSwitch>()
                    call.respond(partners.setAcceptsPaymentRequests(call.requireUserId(), call.parameters["id"].orEmpty(), request.enabled))
                }
            }
        }
    }
}

/**
 * The browser view for someone without the app: her web link. Rate limited like the
 * doctor's page; an unknown token gets the same answer as an expired one.
 */
fun Route.publicPartnerWebRoutes(partners: PartnerService) {
    rateLimit(RateLimits.SHARE) {
        get("/yv/{token}") {
            val language = call.request.queryParameters["lang"]
                ?.let { raw -> Language.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
            val context = call.requestContext()
            val page = partners.openWebLink(call.parameters["token"].orEmpty(), language, context.ip, context.userAgent)
            call.response.header("Cache-Control", "no-store")
            if (page == null) {
                call.respondText(PartnerWebPage.gone(language ?: Language.UZ), ContentType.Text.Html, HttpStatusCode.NotFound)
            } else {
                call.respondText(PartnerWebPage.render(page.view, page.language), ContentType.Text.Html)
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
