package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import uz.sadora.app.data.api.FrameApi
import uz.sadora.app.model.AppState
import uz.sadora.contract.CheckoutSession
import uz.sadora.contract.FrameBoard
import uz.sadora.contract.FrameProduct
import uz.sadora.contract.FrameStorePurchase
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentState

/**
 * Her avatar frames on this phone: the page, the one she wears, and buying.
 *
 * The frame she wears goes into [AppState.applyWornFrame] on every answer, so her photo,
 * her chat header and her own posts on screen change the moment the server agrees.
 * Gul and wearing wait for the server; nothing is shown as hers before it says so.
 */
class FrameController(
    private val api: FrameApi?,
    private val state: AppState,
    /** The store's sheet in a store build: a paid frame is bought there, not by Payme or Click. */
    val store: StoreBilling? = null,
    /** Stamped on a store purchase so the server can tell whose it is. */
    private val currentUserId: () -> String? = { null },
) {
    private val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error

    fun clearError() = calls.clearError()

    var board by mutableStateOf<FrameBoard?>(null)
        private set

    /** The store's localized prices for the paid frames, by store product id. */
    var storePrices by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /** A Payme or Click payment while its page is open. */
    var paying by mutableStateOf<CheckoutSession?>(null)
        private set

    /** A store purchase that clears later — cash at a kiosk. */
    var storePending by mutableStateOf(false)
        private set

    suspend fun load() {
        val api = api ?: return
        calls.run(silent = true) { api.board() }?.let(::apply)
    }

    suspend fun loadStorePrices() {
        val store = store ?: return
        val ids = board?.frames?.mapNotNull { it.product }?.mapNotNull(::storeProductId).orEmpty()
        if (ids.isEmpty() || storePrices.keys.containsAll(ids)) return
        storePrices = storePrices + runCatching { store.keepsakePrices(ids) }.getOrDefault(emptyMap())
    }

    /** Wears [key], or takes hers off with null. True when the server agreed. */
    suspend fun wear(key: String?): Boolean {
        val api = api ?: return false
        val next = calls.run { api.wear(key) } ?: return false
        apply(next)
        return true
    }

    /** A Gul frame: the coins and the frame together. True when it is hers. */
    suspend fun buyWithCoins(key: String): Boolean {
        val api = api ?: return false
        val next = calls.run { api.buyWithCoins(key) } ?: return false
        apply(next)
        state.coins = next.coins
        return true
    }

    /** Payme or Click: the checkout link to open. */
    suspend fun checkout(key: String, provider: PaymentProvider): CheckoutSession? {
        val api = api ?: return null
        val session = calls.run { api.checkout(key, provider) } ?: return null
        paying = session
        return session
    }

    /** Waits for the provider's callback, the way the pet's purchase does. True once it is hers. */
    suspend fun awaitPayment(): Boolean {
        val api = api ?: return false
        val session = paying ?: return false
        repeat(PollAttempts) {
            delay(PollIntervalMillis)
            val status = api.paymentStatus(session.transactionId).valueOrNull ?: return@repeat
            when (status.state) {
                PaymentState.PAID -> {
                    load()
                    // Last, with nothing suspending after it: the sheet waiting on this is keyed on [paying].
                    paying = null
                    return true
                }
                PaymentState.CANCELLED, PaymentState.FAILED -> {
                    paying = null
                    return false
                }
                PaymentState.PENDING -> Unit
            }
        }
        paying = null
        return false
    }

    fun cancelCheckout() {
        paying = null
    }

    /**
     * Through the store's own sheet: bought for this account, verified by the server, and
     * only then finished — a purchase the server never saw stays unfinished, and the next
     * attempt finds it already owned and posts it again.
     */
    suspend fun buyInStore(product: FrameProduct): Boolean {
        val api = api ?: return false
        val store = store ?: return false
        val productId = storeProductId(product) ?: return false
        val accountId = currentUserId() ?: return false
        storePending = false
        return when (val outcome = store.purchaseKeepsake(productId, accountId)) {
            is StoreOutcome.Purchased -> {
                val receipt = outcome.receipt
                val next = calls.run {
                    api.buyInStore(FrameStorePurchase(product.key, store.provider, receipt.productId, receipt.token))
                } ?: return false
                runCatching { store.finish(receipt) }
                apply(next)
                true
            }
            StoreOutcome.Pending -> {
                storePending = true
                false
            }
            StoreOutcome.Cancelled, is StoreOutcome.Failed -> false
        }
    }

    /**
     * Paid frames bought in the store whose verification never landed: posted again, and
     * finished once the server has them. Nothing when nothing is unfinished.
     */
    suspend fun reconcileStore() {
        val api = api ?: return
        val store = store ?: return
        val receipts = runCatching { store.unfinishedKeepsakes() }.getOrDefault(emptyList())
        if (receipts.isEmpty()) return
        if (board == null) load()
        val products = board?.frames?.mapNotNull { it.product }.orEmpty()
        receipts.forEach { receipt ->
            val product = products.firstOrNull { storeProductId(it) == receipt.productId } ?: return@forEach
            val next = calls.run(silent = true) {
                api.buyInStore(FrameStorePurchase(product.key, store.provider, receipt.productId, receipt.token))
            } ?: return@forEach
            runCatching { store.finish(receipt) }
            storePending = false
            apply(next)
        }
    }

    fun storeProductId(product: FrameProduct): String? = when (store?.provider) {
        PaymentProvider.GOOGLE_PLAY -> product.googlePlayProductId
        PaymentProvider.APP_STORE -> product.appStoreProductId
        else -> null
    }

    private fun apply(next: FrameBoard) {
        board = next
        state.applyWornFrame(next.worn)
    }

    private companion object {
        const val PollAttempts = 60
        const val PollIntervalMillis = 3_000L
    }
}
