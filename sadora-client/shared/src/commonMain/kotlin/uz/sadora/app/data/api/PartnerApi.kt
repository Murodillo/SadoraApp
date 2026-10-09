package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.Ack
import uz.sadora.contract.AcceptPartnerInviteRequest
import uz.sadora.contract.CreatePartnerInviteRequest
import uz.sadora.contract.CreatePartnerWebLinkRequest
import uz.sadora.contract.PartnerMessage
import uz.sadora.contract.PartnerMessageKind
import uz.sadora.contract.PartnerMessages
import uz.sadora.contract.PartnerWebLink
import uz.sadora.contract.SendPartnerMessageRequest
import uz.sadora.contract.FollowedPerson
import uz.sadora.contract.PartnerInvite
import uz.sadora.contract.PartnerPermissions
import uz.sadora.contract.PartnerRelation
import uz.sadora.contract.PartnerState
import uz.sadora.contract.PartnerView
import uz.sadora.contract.PausePartnerRequest
import uz.sadora.contract.CheckoutSession
import uz.sadora.contract.CreatePaymentRequest
import uz.sadora.contract.IncomingPaymentRequest
import uz.sadora.contract.PayPaymentRequest
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentRequest
import uz.sadora.contract.PaymentRequestState
import uz.sadora.contract.PaymentRequestStorePurchase
import uz.sadora.contract.PaymentRequestSwitch
import uz.sadora.contract.PaymentStatus

/** Yaqinim: her side under `/partner`, the follower's under `/partner/following`. */
class PartnerApi(private val caller: ApiCaller) {

    suspend fun state(): ApiResult<PartnerState> =
        caller.authenticated("v1/partner", HttpMethodKind.GET)

    suspend fun invite(relation: PartnerRelation): ApiResult<PartnerInvite> =
        caller.authenticated("v1/partner/invite", HttpMethodKind.POST) { setBody(CreatePartnerInviteRequest(relation)) }

    suspend fun approve(): ApiResult<PartnerState> =
        caller.authenticated("v1/partner/approve", HttpMethodKind.POST)

    suspend fun savePermissions(permissions: PartnerPermissions): ApiResult<PartnerState> =
        caller.authenticated("v1/partner/permissions", HttpMethodKind.PUT) { setBody(permissions) }

    suspend fun pause(paused: Boolean): ApiResult<PartnerState> =
        caller.authenticated("v1/partner/pause", HttpMethodKind.POST) { setBody(PausePartnerRequest(paused)) }

    suspend fun end(): ApiResult<PartnerState> =
        caller.authenticated("v1/partner", HttpMethodKind.DELETE)

    suspend fun messages(linkId: String): ApiResult<PartnerMessages> =
        caller.authenticated("v1/partner/links/$linkId/messages", HttpMethodKind.GET)

    suspend fun send(linkId: String, kind: PartnerMessageKind, text: String?): ApiResult<PartnerMessage> =
        caller.authenticated("v1/partner/links/$linkId/messages", HttpMethodKind.POST) {
            setBody(SendPartnerMessageRequest(kind, text))
        }

    suspend fun markRead(linkId: String): ApiResult<PartnerMessages> =
        caller.authenticated("v1/partner/links/$linkId/messages/read", HttpMethodKind.POST)

    suspend fun createWebLink(ttlHours: Int, permissions: PartnerPermissions): ApiResult<PartnerWebLink> =
        caller.authenticated("v1/partner/web", HttpMethodKind.POST) { setBody(CreatePartnerWebLinkRequest(ttlHours, permissions)) }

    suspend fun revokeWebLink(): ApiResult<PartnerState> =
        caller.authenticated("v1/partner/web", HttpMethodKind.DELETE)

    suspend fun labourAlert(): ApiResult<Ack> =
        caller.authenticated("v1/partner/alert/labour", HttpMethodKind.POST)

    suspend fun accept(code: String, name: String?, asPartnerAccount: Boolean): ApiResult<FollowedPerson> =
        caller.authenticated("v1/partner/accept", HttpMethodKind.POST) {
            setBody(AcceptPartnerInviteRequest(code, name, asPartnerAccount))
        }

    suspend fun view(linkId: String): ApiResult<PartnerView> =
        caller.authenticated("v1/partner/following/$linkId", HttpMethodKind.GET)

    suspend fun leave(linkId: String): ApiResult<Ack> =
        caller.authenticated("v1/partner/following/$linkId", HttpMethodKind.DELETE)

    suspend fun setAcceptsPaymentRequests(linkId: String, enabled: Boolean): ApiResult<FollowedPerson> =
        caller.authenticated("v1/partner/following/$linkId/payment-requests", HttpMethodKind.PUT) {
            setBody(PaymentRequestSwitch(enabled))
        }

    // ---------------------------------------------------------------- requests to pay

    suspend fun myPaymentRequest(): ApiResult<PaymentRequestState> =
        caller.authenticated("v1/payment-requests", HttpMethodKind.GET)

    suspend fun askToPay(request: CreatePaymentRequest): ApiResult<PaymentRequest> =
        caller.authenticated("v1/payment-requests", HttpMethodKind.POST) { setBody(request) }

    suspend fun cancelPaymentRequest(id: String): ApiResult<PaymentRequest> =
        caller.authenticated("v1/payment-requests/$id", HttpMethodKind.DELETE)

    suspend fun sharePaymentRequest(id: String): ApiResult<PaymentRequest> =
        caller.authenticated("v1/payment-requests/$id/share", HttpMethodKind.POST)

    suspend fun incomingPaymentRequests(): ApiResult<List<IncomingPaymentRequest>> =
        caller.authenticated("v1/payment-requests/incoming", HttpMethodKind.GET)

    suspend fun declinePaymentRequest(id: String): ApiResult<Ack> =
        caller.authenticated("v1/payment-requests/$id/decline", HttpMethodKind.POST)

    suspend fun payPaymentRequest(id: String, provider: PaymentProvider, planId: String?): ApiResult<CheckoutSession> =
        caller.authenticated("v1/payment-requests/$id/checkout", HttpMethodKind.POST) {
            setBody(PayPaymentRequest(provider, planId))
        }

    suspend fun payPaymentRequestInStore(id: String, provider: PaymentProvider, productId: String, token: String): ApiResult<PaymentRequest> =
        caller.authenticated("v1/payment-requests/$id/store", HttpMethodKind.POST) {
            setBody(PaymentRequestStorePurchase(provider, productId, token))
        }

    /** The payer polls the payment they started, the way the paywall does. */
    suspend fun paymentStatus(transactionId: String): ApiResult<PaymentStatus> =
        caller.authenticated("v1/billing/payments/$transactionId", HttpMethodKind.GET)
}
