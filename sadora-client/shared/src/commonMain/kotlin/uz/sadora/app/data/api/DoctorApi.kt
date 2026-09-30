package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.CheckoutSession
import uz.sadora.contract.ConsultationCheckoutRequest
import uz.sadora.contract.DoctorAccount
import uz.sadora.contract.DoctorListItem
import uz.sadora.contract.DoctorProfile
import uz.sadora.contract.DoctorReview
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentStatus

/** The doctor calls the client app makes; the doctor's own ones are in sadora-doctor. */
class DoctorApi(private val caller: ApiCaller) {

    /** Her own doctor status — only "approved" changes anything here. */
    suspend fun account(): ApiResult<DoctorAccount> =
        caller.authenticated("v1/doctor/me", HttpMethodKind.GET)

    /** Every verified doctor, already in the server's recommended order. */
    suspend fun list(): ApiResult<List<DoctorListItem>> =
        caller.authenticated("v1/doctors", HttpMethodKind.GET)

    suspend fun profile(id: String): ApiResult<DoctorProfile> =
        caller.authenticated("v1/doctors/$id", HttpMethodKind.GET)

    /** What patients said of her, newest first. Anonymous: no name comes with a review. */
    suspend fun reviews(id: String): ApiResult<List<DoctorReview>> =
        caller.authenticated("v1/doctors/$id/reviews", HttpMethodKind.GET)

    /**
     * A checkout for a paid consultation. The window opens when the provider's callback
     * reaches the server — never because the app came back from the browser.
     */
    suspend fun consultationCheckout(id: String, provider: PaymentProvider): ApiResult<CheckoutSession> =
        caller.authenticated("v1/doctors/$id/consultations/checkout", HttpMethodKind.POST) {
            setBody(ConsultationCheckoutRequest(provider))
        }

    /** The same status call the paywall polls; a consultation's payment is one of those. */
    suspend fun paymentStatus(transactionId: String): ApiResult<PaymentStatus> =
        caller.authenticated("v1/billing/payments/$transactionId", HttpMethodKind.GET)
}
