package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.CheckoutSession
import uz.sadora.contract.CommunityPost
import uz.sadora.contract.ConsultationCheckoutRequest
import uz.sadora.contract.DoctorAccount
import uz.sadora.contract.DoctorListItem
import uz.sadora.contract.DoctorProfile
import uz.sadora.contract.DoctorReview
import uz.sadora.contract.Page
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentStatus

/** The doctor calls the client app makes; the doctor's own ones are in sadora-doctor. */
class DoctorApi(private val caller: ApiCaller) {

    /** Her own doctor status — only "approved" changes anything here. */
    suspend fun account(): ApiResult<DoctorAccount> =
        caller.authenticated("v1/doctor/me", HttpMethodKind.GET)

    /** [limit] verified doctors from [offset], in the server's recommended order. */
    suspend fun list(limit: Int = DIRECTORY_PAGE, offset: Int = 0): ApiResult<List<DoctorListItem>> =
        caller.authenticated("v1/doctors?limit=$limit&offset=$offset", HttpMethodKind.GET)

    suspend fun profile(id: String): ApiResult<DoctorProfile> =
        caller.authenticated("v1/doctors/$id", HttpMethodKind.GET)

    /** Her posts past the few [profile] carries, newest first. */
    suspend fun posts(id: String, limit: Int = POSTS_PAGE, offset: Int = 0): ApiResult<Page<CommunityPost>> =
        caller.authenticated("v1/doctors/$id/posts?limit=$limit&offset=$offset", HttpMethodKind.GET)

    /** What patients said of her, newest first. Anonymous: no name comes with a review. */
    suspend fun reviews(id: String, limit: Int = REVIEW_PAGE, offset: Int = 0): ApiResult<List<DoctorReview>> =
        caller.authenticated("v1/doctors/$id/reviews?limit=$limit&offset=$offset", HttpMethodKind.GET)

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

    companion object {
        /** Pages: the server caps each, and a phone that sends none gets the old whole lists. */
        const val DIRECTORY_PAGE = 30
        /** The most doctors the server gives in one read. */
        const val DIRECTORY_MAX = 500
        const val POSTS_PAGE = 20
        const val REVIEW_PAGE = 20
    }
}
