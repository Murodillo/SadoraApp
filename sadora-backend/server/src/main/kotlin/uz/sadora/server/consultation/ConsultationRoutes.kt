package uz.sadora.server.consultation

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.host
import io.ktor.server.request.port
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
import uz.sadora.contract.ConsultationCheckoutRequest
import uz.sadora.contract.ConsultationPayment
import uz.sadora.contract.DoctorEarnings
import uz.sadora.contract.RateConsultationRequest
import uz.sadora.contract.SavePatientNoteRequest
import uz.sadora.contract.SaveQuickReplyRequest
import uz.sadora.contract.UpdateDoctorSettingsRequest
import uz.sadora.server.api.enumParameter
import uz.sadora.server.api.intParameter
import uz.sadora.server.api.requestContext
import uz.sadora.server.api.requireAdminRole
import uz.sadora.server.api.requireUserId
import uz.sadora.server.billing.BillingService
import uz.sadora.server.core.parseUuid
import uz.sadora.server.plugins.ADMIN_AUTH
import uz.sadora.server.plugins.AdminRole
import uz.sadora.server.plugins.USER_AUTH

/** The doctor's working day, a patient paying and rating, under the app's own auth. */
fun Route.consultationRoutes(consultations: ConsultationService) {
    authenticate(USER_AUTH) {
        route("/doctor") {
            get("/settings") {
                call.respond(consultations.settings(call.requireUserId()))
            }
            put("/settings") {
                val request = call.receive<UpdateDoctorSettingsRequest>()
                call.respond(consultations.updateSettings(call.requireUserId(), request))
            }
            route("/quick-replies") {
                get {
                    call.respond(consultations.quickReplies(call.requireUserId()))
                }
                post {
                    val request = call.receive<SaveQuickReplyRequest>()
                    call.respond(HttpStatusCode.Created, consultations.addQuickReply(call.requireUserId(), request))
                }
                put("/{id}") {
                    val request = call.receive<SaveQuickReplyRequest>()
                    call.respond(consultations.updateQuickReply(call.requireUserId(), call.uuid("id"), request))
                }
                delete("/{id}") {
                    consultations.deleteQuickReply(call.requireUserId(), call.uuid("id"))
                    call.respond(Ack())
                }
            }
            /** A patient, by the consultation she is in: the doctor never handles a user id. */
            route("/patients/{conversationId}") {
                get("/note") {
                    call.response.header(HttpHeaders.CacheControl, "no-store")
                    call.respond(consultations.note(call.requireUserId(), call.uuid("conversationId")))
                }
                put("/note") {
                    val request = call.receive<SavePatientNoteRequest>()
                    call.respond(consultations.saveNote(call.requireUserId(), call.uuid("conversationId"), request))
                }
                get("/history") {
                    call.response.header(HttpHeaders.CacheControl, "no-store")
                    call.respond(consultations.history(call.requireUserId(), call.uuid("conversationId")))
                }
            }
            get("/stats") {
                call.respond(consultations.stats(call.requireUserId()))
            }
            /** The totals and the first page of lines and payouts; the rest by offset below. */
            get("/earnings") {
                call.respond(consultations.earnings(call.requireUserId()))
            }
            get("/earnings/lines") {
                val (limit, offset) = call.earningsPage()
                call.respond(consultations.earningLines(call.requireUserId(), limit, offset))
            }
            get("/earnings/payouts") {
                val (limit, offset) = call.earningsPage()
                call.respond(consultations.payouts(call.requireUserId(), limit, offset))
            }
        }

        route("/doctors/{id}") {
            /** A paid doctor: the checkout whose payment opens the window. */
            post("/consultations/checkout") {
                val request = call.receive<ConsultationCheckoutRequest>()
                call.respond(
                    HttpStatusCode.Created,
                    consultations.checkout(call.requireUserId(), call.uuid("id"), request.provider, call.origin()),
                )
            }
            get("/reviews") {
                call.requireUserId()
                call.respond(
                    consultations.reviews(
                        doctorId = call.uuid("id"),
                        limit = call.intParameter("limit", default = ConsultationService.MAX_REVIEWS, max = ConsultationService.MAX_REVIEWS),
                        offset = call.intParameter("offset", default = 0, max = Int.MAX_VALUE).toLong(),
                    ),
                )
            }
        }

        post("/community/conversations/{id}/rating") {
            val request = call.receive<RateConsultationRequest>()
            consultations.rate(call.requireUserId(), call.uuid("id"), request)
            call.respond(Ack())
        }
    }
}

/**
 * A page that stands in for Payme and Click where no merchant keys are set: it pays the
 * consultation at once, through the same activation a real callback takes. The service
 * refuses it in production; the transaction id, random and unguessable, is the only key.
 */
fun Route.devPayRoutes(billing: BillingService) {
    // The button is a GET with `confirm=1`: the page's security headers make the browser
    // send `Origin: null` on a form POST, which CORS rightly refuses, and a navigation
    // carries no Origin at all. A GET that pays is tolerable only because this is a
    // development stand-in, never mounted in production.
    get("/billing/dev-pay/{id}") {
        val id = call.uuid("id")
        call.response.header(HttpHeaders.CacheControl, "no-store")
        if (call.request.queryParameters["confirm"] != "1") {
            call.respondText(devPayPage(id.toString(), paid = false), ContentType.Text.Html)
            return@get
        }
        val paid = billing.devPay(id)
        call.respondText(
            devPayPage(id.toString(), paid = paid, failed = !paid),
            ContentType.Text.Html,
            if (paid) HttpStatusCode.OK else HttpStatusCode.NotFound,
        )
    }
    // Kept for scripts and tests that pay without a browser.
    post("/billing/dev-pay/{id}") {
        val paid = billing.devPay(call.uuid("id"))
        call.respondText(
            devPayPage(call.parameters["id"].orEmpty(), paid = paid, failed = !paid),
            ContentType.Text.Html,
            if (paid) HttpStatusCode.OK else HttpStatusCode.NotFound,
        )
    }
}

/** Refunds, the commission, payouts and each doctor's record, for the staff panel. */
fun Route.adminConsultationRoutes(consultations: ConsultationService) {
    authenticate(ADMIN_AUTH) {
        route("/admin/consultations") {
            get {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT)
                call.respond(
                    consultations.adminConsultations(
                        payment = call.enumParameter<ConsultationPayment>("payment"),
                        limit = call.intParameter("limit", default = 50, max = 200),
                        offset = call.intParameter("offset", default = 0, max = Int.MAX_VALUE).toLong(),
                    ),
                )
            }
            post("/{id}/refunded") {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                consultations.markRefunded(call.uuid("id"), admin, call.requestContext())
                call.respond(Ack())
            }
        }
        route("/admin/settings/commission") {
            get {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT)
                call.respond(consultations.commission())
            }
            put {
                val admin = call.requireAdminRole(AdminRole.OWNER)
                val request = call.receive<SetCommissionRequest>()
                call.respond(consultations.setCommission(request.percent, admin, call.requestContext()))
            }
        }
        route("/admin/doctors") {
            get("/quality") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT)
                call.respond(
                    consultations.quality(
                        limit = call.intParameter("limit", default = ConsultationService.QUALITY_PAGE, max = ConsultationService.MAX_QUALITY_PAGE),
                        offset = call.intParameter("offset", default = 0, max = Int.MAX_VALUE).toLong(),
                    ),
                )
            }
            get("/{id}/earnings") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                call.respond(consultations.adminEarnings(call.uuid("id")))
            }
            get("/{id}/earnings/lines") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val (limit, offset) = call.earningsPage()
                call.respond(consultations.adminEarningLines(call.uuid("id"), limit, offset))
            }
            get("/{id}/earnings/payouts") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val (limit, offset) = call.earningsPage()
                call.respond(consultations.adminPayouts(call.uuid("id"), limit, offset))
            }
            post("/{id}/payouts") {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val request = call.receive<CreatePayoutRequest>()
                call.respond(consultations.addPayout(call.uuid("id"), request, admin, call.requestContext()))
            }
        }
    }
}

private fun ApplicationCall.uuid(name: String) = parseUuid(parameters[name].orEmpty(), name)

/** `limit` and `offset` for a page of earnings lines or payouts. */
private fun ApplicationCall.earningsPage(): Pair<Int, Long> =
    intParameter("limit", default = DoctorEarnings.PAGE, max = ConsultationService.MAX_EARNINGS_PAGE) to
        intParameter("offset", default = 0, max = Int.MAX_VALUE).toLong()

/**
 * The host the app reached us on, as the proxy in front passes it along: the development
 * payment page must be opened on the same address the phone already talks to.
 */
internal fun ApplicationCall.origin(): String {
    val proto = request.headers["X-Forwarded-Proto"]?.substringBefore(',')?.trim() ?: "http"
    val host = request.headers["X-Forwarded-Host"]?.substringBefore(',')?.trim()
        ?: request.headers[HttpHeaders.Host]
        ?: "${request.host()}:${request.port()}"
    return "$proto://$host"
}

private fun devPayPage(id: String, paid: Boolean, failed: Boolean = false): String {
    val body = when {
        paid -> """<h1>To'landi ✓</h1><p>Ilovaga qayting — konsultatsiya ochildi.</p>"""
        failed -> """<h1>To'lab bo'lmadi</h1><p>Bu to'lov topilmadi yoki allaqachon yopilgan.</p>"""
        else -> """
            <h1>Sinov to'lovi</h1>
            <p>Payme va Click hali ulanmagan. Bu sahifa to'lovni sinov uchun darhol bajaradi — haqiqiy pul yechilmaydi.</p>
            <form method="get"><input type="hidden" name="confirm" value="1"><button type="submit">To'lash (sinov)</button></form>
            <p class="id">$id</p>
        """.trimIndent()
    }
    return """
        <!doctype html><html lang="uz"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Sadora — to'lov</title>
        <style>
          body{font-family:system-ui,sans-serif;background:#faf7ff;color:#2b2140;margin:0;padding:48px 20px;text-align:center}
          h1{font-size:24px;margin:0 0 12px} p{color:#6b6180;line-height:1.5;max-width:420px;margin:0 auto 20px}
          button{background:#7c5cff;color:#fff;border:0;border-radius:999px;padding:14px 32px;font-size:16px;font-weight:600}
          .id{font-size:11px;color:#b3abc4}
        </style></head><body>$body</body></html>
    """.trimIndent()
}
