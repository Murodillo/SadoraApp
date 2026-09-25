package uz.sadora.app.data

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import uz.sadora.contract.PaymentProvider

/**
 * StoreKit, reached through Swift.
 *
 * StoreKit 2 is a Swift-only API, so the app target implements [StoreKitBridge] in Swift
 * (`iosApp/iosApp/StoreKitBridge.swift`) and registers it in [IosStore] at launch. The
 * interface speaks in callbacks rather than `suspend`, because Swift can implement a
 * Kotlin interface but not a Kotlin coroutine; [IosStoreBilling] turns them back into
 * suspending calls for the shared code.
 */
interface StoreKitBridge {
    /** Localized prices — `Product.displayPrice` — by product id. */
    fun prices(productIds: List<String>, done: (Map<String, String>) -> Unit)

    /** Shows the App Store sheet. [accountToken] becomes the transaction's `appAccountToken`. */
    fun purchase(productId: String, accountToken: String, done: (StoreKitResult) -> Unit)

    /** Current entitlements and unfinished transactions, as signed JWS. */
    fun owned(done: (List<StoreKitResult>) -> Unit)

    /** `Transaction.finish()` for the transaction that [jws] represents. */
    fun finish(jws: String, done: () -> Unit)
}

/**
 * One StoreKit outcome, flat so Swift can build it. [status] is `purchased`, `pending`,
 * `cancelled` or `failed`; [jws] is `VerificationResult.jwsRepresentation`, which the
 * server verifies against Apple's root.
 */
class StoreKitResult(
    val status: String,
    val productId: String?,
    val jws: String?,
    val message: String?,
)

/** Where Swift puts its bridge before the first screen is built. */
object IosStore {
    var bridge: StoreKitBridge? = null
}

class IosStoreBilling(private val bridge: StoreKitBridge) : StoreBilling {

    override val provider: PaymentProvider = PaymentProvider.APP_STORE

    override suspend fun prices(productIds: List<String>): Map<String, String> =
        suspendCancellableCoroutine { continuation ->
            bridge.prices(productIds) { prices -> if (continuation.isActive) continuation.resume(prices) }
        }

    override suspend fun purchase(productId: String, accountId: String): StoreOutcome {
        val result = suspendCancellableCoroutine { continuation ->
            bridge.purchase(productId, accountId) { if (continuation.isActive) continuation.resume(it) }
        }
        return when (result.status) {
            "purchased" -> result.jws?.let { StoreOutcome.Purchased(StoreReceipt(productId, it, needsFinish = true)) }
                ?: StoreOutcome.Failed("StoreKit returned no transaction")
            "pending" -> StoreOutcome.Pending
            "cancelled" -> StoreOutcome.Cancelled
            else -> StoreOutcome.Failed(result.message ?: "StoreKit failed")
        }
    }

    override suspend fun owned(): List<StoreReceipt> {
        val results = suspendCancellableCoroutine { continuation ->
            bridge.owned { if (continuation.isActive) continuation.resume(it) }
        }
        return results.mapNotNull { result ->
            val productId = result.productId ?: return@mapNotNull null
            val jws = result.jws ?: return@mapNotNull null
            StoreReceipt(productId, jws, needsFinish = true)
        }
    }

    override suspend fun finish(receipt: StoreReceipt) {
        suspendCancellableCoroutine { continuation ->
            bridge.finish(receipt.token) { if (continuation.isActive) continuation.resume(Unit) }
        }
    }
}
