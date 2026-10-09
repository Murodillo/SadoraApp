package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.Ack
import uz.sadora.contract.CheckoutSession
import uz.sadora.contract.ChoosePetRequest
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentStatus
import uz.sadora.contract.PetCheckoutRequest
import uz.sadora.contract.PetKind
import uz.sadora.contract.PetNudgeAnswer
import uz.sadora.contract.PetNudgeRequest
import uz.sadora.contract.PetState
import uz.sadora.contract.PetStorePurchase
import uz.sadora.contract.PetTrigger

/**
 * The companion. Picking the five is free; the nudges are Premium and answer 402
 * otherwise; the legendary pet is bought — by Payme or Click, or a store receipt.
 */
class PetApi(private val caller: ApiCaller) {

    suspend fun state(): ApiResult<PetState> =
        caller.authenticated("v1/pet", HttpMethodKind.GET)

    suspend fun choose(pet: PetKind): ApiResult<PetState> =
        caller.authenticated("v1/pet", HttpMethodKind.PUT) { setBody(ChoosePetRequest(pet)) }

    suspend fun nudge(trigger: PetTrigger, detail: String?): ApiResult<PetNudgeAnswer> =
        caller.authenticated("v1/pet/nudge", HttpMethodKind.POST) { setBody(PetNudgeRequest(trigger, detail)) }

    suspend fun checkout(pet: PetKind, provider: PaymentProvider): ApiResult<CheckoutSession> =
        caller.authenticated("v1/pet/checkout", HttpMethodKind.POST) { setBody(PetCheckoutRequest(pet, provider)) }

    suspend fun paymentStatus(transactionId: String): ApiResult<PaymentStatus> =
        caller.authenticated("v1/billing/payments/$transactionId", HttpMethodKind.GET)

    suspend fun buyInStore(purchase: PetStorePurchase): ApiResult<PetState> =
        caller.authenticated("v1/pet/store", HttpMethodKind.POST) { setBody(purchase) }

    suspend fun offerSeen(): ApiResult<PetState> =
        caller.authenticated("v1/pet/offer/seen", HttpMethodKind.POST) { setBody(Ack()) }
}
