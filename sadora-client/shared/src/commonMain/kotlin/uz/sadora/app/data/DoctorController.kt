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
import uz.sadora.contract.CommunityPost as WirePost
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

    /** Her posts past the few [profile] carries, read as she scrolls; dropped when another page opens. */
    private var olderPosts by mutableStateOf<List<WirePost>>(emptyList())

    val profilePosts: List<CommunityPost>
        get() {
            val first = profile?.posts.orEmpty()
            val ids = first.mapTo(HashSet()) { it.id }
            return (first + olderPosts.filter { it.id !in ids }).map { it.toAppPost() }
        }

    /** Her posts go on past [profilePosts]. */
    var profilePostsHasMore by mutableStateOf(false)
        private set
    private var profilePostsOffset = 0
    private var loadingProfilePosts = false

    /** Her latest reviews, for the page on screen; [reviewsFor] says whose they are. */
    var reviews by mutableStateOf<List<DoctorReview>>(emptyList())
        private set
    var reviewsFor by mutableStateOf<String?>(null)
        private set

    /** More of her reviews are on the server than [reviews] holds. */
    var reviewsHasMore by mutableStateOf(false)
        private set
    private var reviewsOffset = 0
    private var loadingReviews = false

    /** Whether she is an approved doctor; quiet, since most accounts are not. */
    suspend fun loadAccount() {
        val api = api ?: return
        calls.run(silent = true) { api.account() }?.let { account ->
            state.doctorName = account.fullName.takeIf { account.status == DoctorStatus.APPROVED }
        }
    }

    suspend fun loadProfile(id: String) {
        val api = api ?: return
        if (profile?.id != id) {
            profile = null
            olderPosts = emptyList()
        }
        calls.run(silent = profile != null) { api.profile(id) }?.let { page ->
            profile = page
            // A refresh keeps the pages she scrolled to; their offset stays where it was.
            if (olderPosts.isEmpty()) {
                profilePostsOffset = page.posts.size
                profilePostsHasMore = page.posts.size < page.postCount
            }
        }
    }

    /** The next page of the open doctor's posts, under what is on screen. */
    suspend fun loadMoreProfilePosts() {
        val api = api ?: return
        val id = profile?.id ?: return
        if (!profilePostsHasMore || loadingProfilePosts) return
        loadingProfilePosts = true
        try {
            val page = calls.run(silent = true) {
                api.posts(id, limit = DoctorApi.POSTS_PAGE, offset = profilePostsOffset)
            } ?: return
            if (profile?.id != id) return
            // Offsets shift when she posts in between; the overlap is dropped by id.
            val known = (profile?.posts.orEmpty() + olderPosts).mapTo(HashSet()) { it.id }
            olderPosts = olderPosts + page.items.filter { it.id !in known }
            profilePostsOffset += page.items.size
            profilePostsHasMore = page.hasMore && page.items.isNotEmpty()
        } finally {
            loadingProfilePosts = false
        }
    }

    /**
     * Quiet: a page without its reviews is still her page. Reads the newest page; the
     * pages she had already read stay under it.
     */
    suspend fun loadReviews(id: String) {
        val api = api ?: return
        val same = reviewsFor == id
        if (!same) {
            reviews = emptyList()
            reviewsHasMore = false
        }
        calls.run(silent = true) { api.reviews(id, limit = DoctorApi.REVIEW_PAGE) }?.let { latest ->
            val full = latest.size >= DoctorApi.REVIEW_PAGE
            // A review has no id; the newest page's last date is the edge, and equal rows are one.
            val edge = latest.lastOrNull()?.createdAt
            val tail = if (full && same && edge != null) reviews.filter { it.createdAt < edge } else emptyList()
            reviews = (latest + tail).distinct()
            reviewsFor = id
            reviewsOffset = latest.size + tail.size
            reviewsHasMore = full && (tail.isEmpty() || reviewsHasMore)
        }
    }

    /** The next page of her reviews, under the ones on screen. */
    suspend fun loadMoreReviews() {
        val api = api ?: return
        val id = reviewsFor ?: return
        if (!reviewsHasMore || loadingReviews) return
        loadingReviews = true
        try {
            val page = calls.run(silent = true) {
                api.reviews(id, limit = DoctorApi.REVIEW_PAGE, offset = reviewsOffset)
            } ?: return
            if (reviewsFor != id) return
            reviews = (reviews + page).distinct()
            reviewsOffset += page.size
            reviewsHasMore = page.size >= DoctorApi.REVIEW_PAGE
        } finally {
            loadingReviews = false
        }
    }

    // ---------------------------------------------------------------- the directory

    /**
     * The directory's calls, apart from [calls]: a list that failed to refresh behind
     * Bugun must not raise a banner on the doctor page she opens next, nor the other way.
     */
    val directoryCalls = ApiCallState()

    /** The verified doctors read so far, a page at a time, in the server's recommended order. */
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
        // At least a page, and as many as she had already scrolled through: the order is
        // the server's, not a time, so the pages under the first cannot be kept by an edge.
        val limit = directoryOffset.coerceIn(DoctorApi.DIRECTORY_PAGE, DoctorApi.DIRECTORY_MAX)
        directoryCalls.run(silent = quiet) { api.list(limit = limit) }?.let {
            directory = it
            directoryOffset = it.size
            directoryHasMore = it.size >= limit
            directoryLoaded = true
        }
    }

    /** More doctors follow [directory] in the server's order. */
    var directoryHasMore by mutableStateOf(false)
        private set
    private var directoryOffset = 0
    private var loadingDirectory = false

    /** The next page of the directory, under what is on screen. */
    suspend fun loadMoreDirectory() {
        val api = api ?: return
        if (!directoryHasMore || loadingDirectory) return
        loadingDirectory = true
        try {
            val page = directoryCalls.run(silent = true) {
                api.list(limit = DoctorApi.DIRECTORY_PAGE, offset = directoryOffset)
            } ?: return
            // The order can shift between reads — someone came online; a doctor is shown once.
            val known = directory.mapTo(HashSet()) { it.id }
            directory = directory + page.filter { it.id !in known }
            directoryOffset += page.size
            directoryHasMore = page.size >= DoctorApi.DIRECTORY_PAGE
        } finally {
            loadingDirectory = false
        }
    }

    /**
     * Reads every page that is left. A filter or another order is over the whole
     * directory: "cheapest first" from the first page alone would be a wrong answer.
     */
    suspend fun loadWholeDirectory() {
        while (directoryHasMore) {
            val before = directoryOffset
            loadMoreDirectory()
            // A failed or busy read moved nothing; stop rather than spin.
            if (directoryOffset == before) return
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
