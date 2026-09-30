package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import uz.sadora.app.data.api.DoctorApi
import uz.sadora.app.model.AppState
import uz.sadora.app.model.CommunityPost
import uz.sadora.app.model.DoctorFilter
import uz.sadora.app.model.DoctorSort
import uz.sadora.app.model.arrangeDoctors
import uz.sadora.contract.CheckoutSession
import uz.sadora.contract.DoctorListItem
import uz.sadora.contract.DoctorProfile
import uz.sadora.contract.DoctorReview
import uz.sadora.contract.DoctorStatus
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentState

/**
 * How long one stretch of waiting for a consultation's payment lasts, and how often it
 * asks: five minutes, like the paywall. The stretch starts again each time the app comes
 * back to the front, which is when a payment made in the browser is most likely to land.
 */
private const val PayPollAttempts = 100
private const val PayPollIntervalMillis = 3_000L

/**
 * Doctors as the chat shows them: the directory of every verified doctor, a doctor's
 * public page, and whether she — the woman holding this phone — is one herself.
 *
 * Applying, the panel and answering questions live in the doctor's own app
 * (sadora-doctor). An approved doctor who also uses this app still writes under her
 * name — the server decides that by account — so her approved name is mirrored onto
 * [AppState.doctorName] for the composer and her optimistic comments to say so.
 *
 * A doctor who charges is paid for here too. The purchase is the provider's page, as
 * with Premium: the app asks for a link, opens it, and then asks the server — never
 * itself — whether the money arrived. The server opens the window when it does.
 */
class DoctorController(
    private val api: DoctorApi?,
    private val state: AppState,
) {
    val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error

    fun clearError() = calls.clearError()

    /** The doctor page on screen, replaced on every open. */
    var profile by mutableStateOf<DoctorProfile?>(null)
        private set
    val profilePosts: List<CommunityPost> get() = profile?.posts.orEmpty().map { it.toAppPost() }

    /** Her latest reviews, for the page on screen; [reviewsFor] says whose they are. */
    var reviews by mutableStateOf<List<DoctorReview>>(emptyList())
        private set
    var reviewsFor by mutableStateOf<String?>(null)
        private set

    /** Whether she is an approved doctor; quiet, since most accounts are not. */
    suspend fun loadAccount() {
        val api = api ?: return
        calls.run(silent = true) { api.account() }?.let { account ->
            state.doctorName = account.fullName.takeIf { account.status == DoctorStatus.APPROVED }
        }
    }

    suspend fun loadProfile(id: String) {
        val api = api ?: return
        if (profile?.id != id) profile = null
        calls.run(silent = profile != null) { api.profile(id) }?.let { profile = it }
    }

    /** Quiet: a page without its reviews is still her page. */
    suspend fun loadReviews(id: String) {
        val api = api ?: return
        if (reviewsFor != id) reviews = emptyList()
        calls.run(silent = true) { api.reviews(id) }?.let {
            reviews = it
            reviewsFor = id
        }
    }

    // ---------------------------------------------------------------- the directory

    /**
     * The directory's calls, apart from [calls]: a list that failed to refresh behind
     * Bugun must not raise a banner on the doctor page she opens next, nor the other way.
     */
    val directoryCalls = ApiCallState()

    /** Every verified doctor, in the server's recommended order. */
    var directory by mutableStateOf<List<DoctorListItem>>(emptyList())
        private set

    /** Whether the list has been heard at least once, so "none" can be told from "not yet". */
    var directoryLoaded by mutableStateOf(false)
        private set

    /** Kept here rather than on the screen so a doctor's page and back finds the same view. */
    var directoryFilter by mutableStateOf(DoctorFilter())
    var directorySort by mutableStateOf(DoctorSort.Recommended)

    /** The directory as the screen draws it: filtered, then sorted. */
    val arrangedDirectory: List<DoctorListItem>
        get() = arrangeDoctors(directory, directoryFilter, directorySort)

    /**
     * Reads the list. [quiet] is for the chat's strip and Bugun's card: a failure there
     * hides the card rather than raising a banner over a screen about something else.
     */
    suspend fun loadDirectory(quiet: Boolean = false) {
        val api = api ?: run {
            // No backend — a preview, a test: an empty directory, not one forever loading.
            directoryLoaded = true
            return
        }
        directoryCalls.run(silent = quiet) { api.list() }?.let {
            directory = it
            directoryLoaded = true
        }
    }

    fun resetDirectoryFilter() {
        directoryFilter = DoctorFilter()
    }

    // ---------------------------------------------------------------- paying

    /** The checkout that is open, so the sheet shows "waiting" instead of the pay button. */
    var checkout by mutableStateOf<CheckoutSession?>(null)
        private set

    /** The doctor [checkout] is for. */
    var checkoutFor by mutableStateOf<String?>(null)
        private set

    /** Asking for the link; the button waits rather than taking a second tap. */
    var startingCheckout by mutableStateOf(false)
        private set

    /** Why the last checkout failed, apart from [error] so the page's banner stays the page's. */
    var payError by mutableStateOf<ApiFailure?>(null)
        private set

    fun clearPayError() {
        payError = null
    }

    /** Asks for a checkout link. Null when the server refused, with [payError] set. */
    suspend fun startCheckout(doctorId: String, provider: PaymentProvider): CheckoutSession? {
        val api = api ?: return null
        startingCheckout = true
        payError = null
        val result = try {
            api.consultationCheckout(doctorId, provider)
        } finally {
            startingCheckout = false
        }
        return when (result) {
            is ApiResult.Success -> {
                checkout = result.value
                checkoutFor = doctorId
                result.value
            }
            is ApiResult.Failure -> {
                payError = result.failure
                null
            }
        }
    }

    /**
     * Asks once whether the open checkout was paid. True when it was — [checkout] is then
     * cleared and the window is open on the server. A cancelled or failed one is cleared
     * too, with [payError] saying so; a lost poll changes nothing.
     */
    suspend fun checkPayment(): Boolean {
        val api = api ?: return false
        val session = checkout ?: return false
        val status = api.paymentStatus(session.transactionId).valueOrNull ?: return false
        // She may have cancelled and started another while this one was on the wire.
        if (checkout?.transactionId != session.transactionId) return false
        return when (status.state) {
            PaymentState.PAID -> {
                checkout = null
                true
            }
            PaymentState.CANCELLED, PaymentState.FAILED -> {
                checkout = null
                checkoutFor = null
                payError = ApiFailure.PaymentFailed
                false
            }
            PaymentState.PENDING -> false
        }
    }

    /**
     * Polls until the payment lands, it is refused, or this stretch runs out. Returns
     * whether it was paid. Giving up leaves the checkout open: coming back to the app
     * starts another stretch, and the server still knows the truth either way.
     */
    suspend fun awaitPayment(): Boolean {
        repeat(PayPollAttempts) {
            if (checkout == null) return false
            if (checkPayment()) return true
            if (checkout == null) return false
            delay(PayPollIntervalMillis)
        }
        return false
    }

    fun cancelCheckout() {
        checkout = null
        checkoutFor = null
    }
}
