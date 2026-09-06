package org.example.project.data.api

import io.ktor.client.request.setBody
import org.example.project.data.ApiCaller
import org.example.project.data.ApiResult
import org.example.project.data.HttpMethodKind
import uz.sadora.contract.BillingCatalogue
import uz.sadora.contract.CheckoutRequest
import uz.sadora.contract.CheckoutSession
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentStatus
import uz.sadora.contract.StorePurchaseRequest
import uz.sadora.contract.SubscriptionStatus

/**
 * Buying Premium.
 *
 * Nothing here grants anything. Checkout asks for a link, status asks what the provider
 * said, and a store receipt is handed over to be verified — the answer in every case
 * comes from the server, because a client that can grant itself Premium is not a paywall.
 */
class BillingApi(private val caller: ApiCaller) {

    suspend fun catalogue(): ApiResult<BillingCatalogue> =
        caller.authenticated("v1/billing/plans", HttpMethodKind.GET)

    suspend fun checkout(planId: String, provider: PaymentProvider): ApiResult<CheckoutSession> =
        caller.authenticated("v1/billing/checkout", HttpMethodKind.POST) {
            setBody(CheckoutRequest(planId, provider))
        }

    suspend fun status(transactionId: String): ApiResult<PaymentStatus> =
        caller.authenticated("v1/billing/payments/$transactionId", HttpMethodKind.GET)

    suspend fun verifyStorePurchase(
        provider: PaymentProvider,
        productId: String,
        token: String,
    ): ApiResult<SubscriptionStatus> =
        caller.authenticated("v1/billing/store/verify", HttpMethodKind.POST) {
            setBody(StorePurchaseRequest(provider, productId, token))
        }
}
