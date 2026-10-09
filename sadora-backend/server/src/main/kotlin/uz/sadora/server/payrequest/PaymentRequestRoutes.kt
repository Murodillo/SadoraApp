package uz.sadora.server.payrequest

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import uz.sadora.contract.Ack
import uz.sadora.contract.CreatePaymentRequest
import uz.sadora.contract.Language
import uz.sadora.contract.PayPaymentRequest
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentRequestState
import uz.sadora.contract.PaymentRequestStorePurchase
import uz.sadora.server.api.requireAdminRole
import uz.sadora.server.api.requireUserId
import uz.sadora.server.consultation.origin
import uz.sadora.server.core.parseUuid
import uz.sadora.server.plugins.ADMIN_AUTH
import uz.sadora.server.plugins.AdminRole
import uz.sadora.server.plugins.RateLimits
import uz.sadora.server.plugins.USER_AUTH

/**
 * Requests to pay. Her side is `/payment-requests`; the side of the person she asked is
 * `/payment-requests/incoming` and the actions under each request's id.
 */
fun Route.paymentRequestRoutes(requests: PaymentRequestService) {
    authenticate(USER_AUTH) {
        route("/payment-requests") {
            get {
                call.respond(PaymentRequestState(requests.current(call.requireUserId())))
            }

            post {
                val request = call.receive<CreatePaymentRequest>()
                call.respond(HttpStatusCode.Created, requests.create(call.requireUserId(), request))
            }

            get("/incoming") {
                call.response.header("Cache-Control", "no-store")
                call.respond(requests.incoming(call.requireUserId()))
            }

            route("/{id}") {
                delete {
                    call.respond(requests.cancel(call.requireUserId(), call.parameters["id"].orEmpty()))
                }

                post("/share") {
                    call.respond(requests.shareLink(call.requireUserId(), call.parameters["id"].orEmpty()))
                }

                post("/decline") {
                    requests.decline(call.requireUserId(), call.parameters["id"].orEmpty())
                    call.respond(Ack())
                }

                post("/checkout") {
                    val request = call.receive<PayPaymentRequest>()
                    call.respond(
                        HttpStatusCode.Created,
                        requests.pay(call.requireUserId(), call.parameters["id"].orEmpty(), request, call.origin()),
                    )
                }

                post("/store") {
                    val purchase = call.receive<PaymentRequestStorePurchase>()
                    call.respond(requests.payInStore(call.requireUserId(), call.parameters["id"].orEmpty(), purchase))
                }
            }
        }
    }
}

/**
 * The browser link: the request, and Payme or Click for someone without the app. The
 * pay button is a GET that makes a pending payment and goes to the provider — nothing is
 * paid by opening it — behind the same per-address limit as the other public pages.
 */
fun Route.publicPaymentRequestRoutes(requests: PaymentRequestService) {
    rateLimit(RateLimits.SHARE) {
        get("/pr/{token}") {
            val token = call.parameters["token"].orEmpty()
            val asked = call.request.queryParameters["lang"]
                ?.let { raw -> Language.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
            val view = requests.webView(token)
            call.response.header("Cache-Control", "no-store")
            if (view == null) {
                call.respondText(PaymentRequestPage.gone(asked ?: Language.UZ), ContentType.Text.Html, HttpStatusCode.NotFound)
            } else {
                call.respondText(PaymentRequestPage.render(view, token, asked ?: view.language), ContentType.Text.Html)
            }
        }

        get("/pr/{token}/pay") {
            val token = call.parameters["token"].orEmpty()
            val provider = when (call.request.queryParameters["provider"]) {
                "payme" -> PaymentProvider.PAYME
                "click" -> PaymentProvider.CLICK
                else -> null
            }
            val session = provider?.let {
                runCatching { requests.webCheckout(token, it, call.request.queryParameters["plan"], call.origin()) }.getOrNull()
            }
            call.response.header("Cache-Control", "no-store")
            if (session == null) call.respondRedirect("/pr/$token") else call.respondRedirect(session.url)
        }
    }
}

/** The operator's refund of a gift: the days go back with the money. */
fun Route.adminPaymentRequestRoutes(requests: PaymentRequestService) {
    authenticate(ADMIN_AUTH) {
        post("/admin/billing/payments/{id}/refund") {
            val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
            val id = parseUuid(call.parameters["id"].orEmpty(), "id")
            requests.refundGift(id, admin.adminId)
            call.respond(Ack())
        }
    }
}
