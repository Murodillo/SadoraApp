package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import uz.sadora.app.data.api.BillingApi
import uz.sadora.contract.BillingCatalogue
import uz.sadora.contract.CheckoutSession
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentState

/** How long to keep asking whether a payment landed, and how often. */
private const val PollAttempts = 20
private const val PollIntervalMillis = 3_000L

/**
 * The paywall's state.
 *
 * The prices are the server's, so a price change reaches a shipped app. The purchase
 * itself is the provider's page: the app opens a link, and then asks the server — not the
 * provider, and not itself — whether the money arrived. That polling is what turns "she
 * closed the browser" into a real answer instead of a guess.
 */
class BillingController(
    private val api: BillingApi?,
    /** The store's sheet when the app was installed from one; null for a direct build. */
    val store: StoreBilling? = null,
) {
    private val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val isOffline: Boolean get() = api == null

    var catalogue by mutableStateOf<BillingCatalogue?>(null)
        private set

    var error by mutableStateOf<ApiFailure?>(null)
        private set

    /** The checkout that is open, so the screen knows to show "waiting for payment". */
    var pending by mutableStateOf<CheckoutSession?>(null)
        private set

    /** Set once a payment completes, so the screen can say so before it closes. */
    var paid by mutableStateOf(false)
        private set

    fun clearError() {
        error = null
    }

    suspend fun loadCatalogue(force: Boolean = false) {
        val api = api ?: return
        if (!force && catalogue != null) return

        var refusal: ApiFailure? = null
        val loaded = calls.run(silent = true) { api.catalogue().onFailure { refusal = it } }
        when {
            loaded != null -> {
                catalogue = loaded
                error = null
            }
            refusal != null -> error = refusal
        }
    }

    /** Asks for a checkout link. Returns null when the server refused, with [error] set. */
    suspend fun startCheckout(planId: String, provider: PaymentProvider): CheckoutSession? {
        val api = api ?: return null
        paid = false

        var refusal: ApiFailure? = null
        val session = calls.run(silent = true) {
            api.checkout(planId, provider).onFailure { refusal = it }
        }
        if (session == null) {
            error = refusal
            return null
        }
        pending = session
        error = null
        return session
    }

    /**
     * Waits for the provider's callback to land.
     *
     * Polling rather than pushing because the payment happens outside the app: she may
     * come back before the provider has called us, or never come back at all and pay on
     * her laptop. Giving up after a few minutes leaves the transaction pending rather
     * than calling it failed — the server still knows the truth.
     */
    suspend fun awaitPayment(onPaid: suspend () -> Unit) {
        val api = api ?: return
        val session = pending ?: return

        repeat(PollAttempts) {
            delay(PollIntervalMillis)
            val status = api.status(session.transactionId).valueOrNull ?: return@repeat
            when (status.state) {
                PaymentState.PAID -> {
                    paid = true
                    pending = null
                    onPaid()
                    return
                }
                PaymentState.CANCELLED, PaymentState.FAILED -> {
                    pending = null
                    error = ApiFailure.PaymentFailed
                    return
                }
                PaymentState.PENDING -> Unit
            }
        }
        pending = null
    }

    fun cancelPending() {
        pending = null
    }

    // ---------------------------------------------------------------- store builds

    /** The store's localized prices by product id, once [loadStorePrices] has run. */
    var storePrices by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /** A purchase the store accepted but has not settled yet — paid in cash, say. */
    var storePending by mutableStateOf(false)
        private set

    /** The product id this store sells [plan] under, or null when it does not sell it. */
    fun storeProductId(plan: uz.sadora.contract.BillingPlan): String? = when (store?.provider) {
        PaymentProvider.GOOGLE_PLAY -> plan.googlePlayProductId
        PaymentProvider.APP_STORE -> plan.appStoreProductId
        else -> null
    }

    suspend fun loadStorePrices() {
        val store = store ?: return
        val ids = catalogue?.plans.orEmpty().mapNotNull(::storeProductId)
        if (ids.isNotEmpty()) storePrices = runCatching { store.prices(ids) }.getOrDefault(emptyMap())
    }

    /**
     * Buys [plan] through the store's sheet, then has the server verify it before the
     * store is told it was delivered. A verification that fails — no connection — leaves
     * the purchase unfinished, and [reconcileStore] picks it up on the next launch.
     */
    suspend fun buyInStore(plan: uz.sadora.contract.BillingPlan, accountId: String, onPaid: suspend () -> Unit) {
        val store = store ?: return
        val productId = storeProductId(plan) ?: return
        paid = false
        storePending = false
        error = null
        when (val outcome = store.purchase(productId, accountId)) {
            is StoreOutcome.Purchased -> if (deliver(outcome.receipt)) {
                paid = true
                onPaid()
            }
            StoreOutcome.Pending -> storePending = true
            StoreOutcome.Cancelled -> Unit
            is StoreOutcome.Failed -> error = ApiFailure.PaymentFailed
        }
    }

    /**
     * Sends every purchase the store still holds to the server. "Restore purchases" on a
     * new phone, and the safety net for one bought while the server could not be reached.
     * Returns whether anything was granted.
     */
    suspend fun reconcileStore(onPaid: suspend () -> Unit = {}): Boolean {
        val store = store ?: return false
        val receipts = runCatching { store.owned() }.getOrDefault(emptyList())
        var granted = false
        receipts.forEach { if (deliver(it)) granted = true }
        if (granted) {
            storePending = false
            onPaid()
        }
        return granted
    }

    /** Server first, store second: a purchase is finished only once it is ours. */
    private suspend fun deliver(receipt: StoreReceipt): Boolean {
        val api = api ?: return false
        val store = store ?: return false
        var refusal: ApiFailure? = null
        val status = calls.run(silent = true) {
            api.verifyStorePurchase(store.provider, receipt.productId, receipt.token).onFailure { refusal = it }
        }
        if (status == null) {
            error = refusal
            return false
        }
        if (receipt.needsFinish) runCatching { store.finish(receipt) }
        return true
    }
}
