package uz.sadora.server.billing

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.Serializable
import uz.sadora.contract.CheckoutRequest
import uz.sadora.contract.Page
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentState
import uz.sadora.contract.StorePurchaseRequest
import uz.sadora.server.api.intParameter
import uz.sadora.server.api.requireAdminRole
import uz.sadora.server.api.requireUserId
import uz.sadora.server.plugins.ADMIN_AUTH
import uz.sadora.server.plugins.AdminRole
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.parseUuid
import uz.sadora.server.plugins.USER_AUTH

/**
 * Buying Premium, and the providers reporting back.
 *
 * The two halves are deliberately different shapes. The app's half is the usual
 * authenticated JSON API. The providers' half speaks each provider's own protocol,
 * including their error envelopes and their status codes, because they are the client
 * there and a tidy-looking deviation is what makes a payment integration fail at 2am.
 */
fun Route.billingRoutes(billing: BillingService, store: StorePurchaseService) {
    authenticate(USER_AUTH) {
        route("/billing") {

            get("/plans") {
                call.respond(billing.catalogue(call.requireUserId()))
            }

            post("/checkout") {
                val request = call.receive<CheckoutRequest>()
                call.respond(billing.checkout(call.requireUserId(), request))
            }

            /** Polled after she comes back from the provider's page. */
            get("/payments/{id}") {
                val id = call.parameters["id"]?.let { parseUuid(it, "id") }
                    ?: throw ValidationException("id", "Ko'rsatilishi shart")
                call.respond(billing.status(call.requireUserId(), id))
            }

            /**
             * A store receipt, checked with the store before anything is granted. The
             * client's word that it paid is not evidence.
             */
            post("/store/verify") {
                val request = call.receive<StorePurchaseRequest>()
                call.respond(store.verifyAndGrant(call.requireUserId(), request))
            }
        }
    }
}

/**
 * Payme's Merchant API: one unauthenticated-by-JWT endpoint, authenticated instead by
 * their Basic header, answering in their JSON-RPC envelope — including for auth failures,
 * which is why this cannot sit behind the app's own auth plugin.
 */
fun Route.paymeWebhook(gateway: PaymeGateway, repository: BillingRepository) {
    post("/payments/payme") {
        val body = call.receiveText()
        if (!gateway.authorizes(call.request.headers[HttpHeaders.Authorization])) {
            repository.recordCallback(PaymentProvider.PAYME, null, null, body, "unauthorized")
            call.respond(HttpStatusCode.OK, paymeUnauthorized())
            return@post
        }

        val request = runCatching { Json.parseToJsonElement(body) as JsonObject }.getOrNull()
        if (request == null) {
            repository.recordCallback(PaymentProvider.PAYME, null, null, body, "unparseable")
            call.respond(HttpStatusCode.OK, paymeTransportError())
            return@post
        }

        val response = gateway.handle(request)
        repository.recordCallback(
            provider = PaymentProvider.PAYME,
            method = request["method"]?.toString()?.trim('"'),
            transactionId = null,
            payload = body,
            outcome = if ("error" in response) "error" else "ok",
        )
        // Payme reads the envelope, not the status code: a 200 with an error object is
        // how a refusal is reported to them.
        call.respond(HttpStatusCode.OK, response)
    }
}

/**
 * Click's two callbacks. Form-encoded in, JSON out, and their `error` field carries the
 * outcome — a rejected signature is still a 200 with `error: -1`.
 */
fun Route.clickWebhook(gateway: ClickGateway, repository: BillingRepository) {
    route("/payments/click") {
        post("/prepare") {
            val request = call.clickRequest()
            val response = gateway.prepare(request)
            repository.recordCallback(PaymentProvider.CLICK, "prepare", null, request.toString(), response.outcome())
            call.respond(response)
        }

        post("/complete") {
            val request = call.clickRequest()
            val response = gateway.complete(request)
            repository.recordCallback(PaymentProvider.CLICK, "complete", null, request.toString(), response.outcome())
            call.respond(response)
        }
    }
}

private fun ClickGateway.Response.outcome() = if (error == ClickGateway.SUCCESS) "ok" else "error:$error"

/**
 * Click posts a form. A missing field is not a validation error in the app's envelope —
 * it is a signature that cannot match, and it is answered in Click's own shape.
 */
private suspend fun io.ktor.server.application.ApplicationCall.clickRequest(): ClickGateway.Request {
    val form = receiveParameters()
    return ClickGateway.Request(
        clickTransId = form["click_trans_id"].orEmpty(),
        serviceId = form["service_id"].orEmpty(),
        clickPaydocId = form["click_paydoc_id"],
        merchantTransId = form["merchant_trans_id"].orEmpty(),
        merchantPrepareId = form["merchant_prepare_id"],
        amount = form["amount"].orEmpty(),
        action = form["action"]?.toIntOrNull() ?: -1,
        error = form["error"]?.toIntOrNull() ?: 0,
        signTime = form["sign_time"].orEmpty(),
        signString = form["sign_string"].orEmpty(),
    )
}

private fun paymeTransportError(): JsonObject = buildJsonObject {
    put("id", 0)
    put(
        "error",
        buildJsonObject {
            put("code", PaymeGateway.TRANSPORT_ERROR)
            put("message", "Parse error")
        },
    )
}

/**
 * The operator's view of money: the plans, and the payments that came in.
 *
 * Read-only. A refund is a provider-side action and a comped subscription already has its
 * own audited route on the user card, so there is nothing here that moves money — which
 * is also why this page can be open to Support and Analyst.
 */
fun Route.adminBillingRoutes(billing: BillingService, repository: BillingRepository) {
    authenticate(ADMIN_AUTH) {
        route("/admin/billing") {
            get("/plans") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT, AdminRole.ANALYST)
                call.respond(repository.plans(activeOnly = false, kind = null))
            }

            get("/payments") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT, AdminRole.ANALYST)
                val state = call.request.queryParameters["state"]
                    ?.let { wanted -> PaymentState.entries.firstOrNull { it.name.equals(wanted, ignoreCase = true) } }
                val limit = call.intParameter("limit", default = 50, max = 200)
                val offset = call.intParameter("offset", default = 0, max = Int.MAX_VALUE)
                val (rows, total) = repository.recent(limit = limit, offset = offset.toLong(), state = state)
                call.respond(Page(rows.map { it.toView() }, total, limit, offset))
            }

            get("/summary") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.ANALYST)
                call.respond(repository.summary(call.intParameter("days", 30, 365)))
            }
        }
    }
}

/** A payment as the panel lists it. The user id is here because Support needs the user card. */
@Serializable
data class AdminPaymentView(
    val id: String,
    val userId: String,
    /** Null for a consultation payment. */
    val planId: String?,
    val consultationSessionId: String? = null,
    val provider: PaymentProvider,
    val amountMinor: Long,
    val currency: String,
    val state: PaymentState,
    val externalId: String?,
    val paidAt: kotlin.time.Instant?,
    val createdAt: kotlin.time.Instant,
    /** Someone else paid for her: who, when it was an account; null for her own or a browser payer. */
    val payerId: String? = null,
    /** True for a payment answering her request — a gift plan or a consultation paid for her. */
    val gift: Boolean = false,
    /** True for a gift plan or a legendary pet: what it bought goes back with the money. */
    val refundable: Boolean = false,
    val refundedAt: kotlin.time.Instant? = null,
    /** The legendary pet a one-off payment bought, when it bought one. */
    val pet: String? = null,
)

private fun TransactionRecord.toView() = AdminPaymentView(
    id = id.toString(),
    userId = userId.toString(),
    planId = planId,
    consultationSessionId = consultationSessionId?.toString(),
    provider = provider,
    amountMinor = amountMinor,
    currency = currency,
    state = state,
    externalId = externalId,
    paidAt = paidAt,
    createdAt = createdAt,
    payerId = payerId?.toString(),
    gift = paymentRequestId != null || isGiftPlanId(planId),
    refundable = state == PaymentState.PAID && refundedAt == null && (isGiftPlanId(planId) || pet != null),
    refundedAt = refundedAt,
    pet = pet,
)

/** The gift plans V44 seeded. Plan ids are fixed rows, so the prefix is their kind. */
private fun isGiftPlanId(planId: String?) = planId?.startsWith("gift_") == true
