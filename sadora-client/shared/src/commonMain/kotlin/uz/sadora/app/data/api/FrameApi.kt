package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.BuyFrameRequest
import uz.sadora.contract.CheckoutSession
import uz.sadora.contract.FrameBoard
import uz.sadora.contract.FrameCheckoutRequest
import uz.sadora.contract.FrameStorePurchase
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentStatus
import uz.sadora.contract.WearFrameRequest

/** Avatar frames: the page, wearing one, and the three ways to buy — Gul, Payme or Click, a store receipt. */
class FrameApi(private val caller: ApiCaller) {

    suspend fun board(): ApiResult<FrameBoard> =
        caller.authenticated("v1/frames", HttpMethodKind.GET)

    suspend fun wear(key: String?): ApiResult<FrameBoard> =
        caller.authenticated("v1/frames/worn", HttpMethodKind.PUT) { setBody(WearFrameRequest(key)) }

    suspend fun buyWithCoins(key: String): ApiResult<FrameBoard> =
        caller.authenticated("v1/frames/buy", HttpMethodKind.POST) { setBody(BuyFrameRequest(key)) }

    suspend fun checkout(key: String, provider: PaymentProvider): ApiResult<CheckoutSession> =
        caller.authenticated("v1/frames/checkout", HttpMethodKind.POST) { setBody(FrameCheckoutRequest(key, provider)) }

    suspend fun paymentStatus(transactionId: String): ApiResult<PaymentStatus> =
        caller.authenticated("v1/billing/payments/$transactionId", HttpMethodKind.GET)

    suspend fun buyInStore(purchase: FrameStorePurchase): ApiResult<FrameBoard> =
        caller.authenticated("v1/frames/store", HttpMethodKind.POST) { setBody(purchase) }
}
