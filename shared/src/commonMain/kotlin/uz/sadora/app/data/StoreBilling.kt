package uz.sadora.app.data

import uz.sadora.contract.PaymentProvider

/**
 * The platform's own purchase sheet: Google Play Billing on Android, StoreKit on iOS.
 *
 * A build installed from a store buys through that store and nothing else — both
 * stores require it for a digital subscription — so when the platform hands the graph
 * one of these, the paywall shows the store's prices and sheet, and Payme and Click are
 * not offered. A sideloaded build (a demo APK, a debug install) has none, and keeps the
 * direct providers.
 *
 * What this returns is never trusted on its own: every receipt goes to the server, which
 * asks the store, and only then is the purchase finished here.
 */
interface StoreBilling {
    val provider: PaymentProvider

    /** The store's localized price per product id — "39 000 so'm", "$2.99". Missing ids are ones the store does not sell. */
    suspend fun prices(productIds: List<String>): Map<String, String>

    /**
     * Shows the store's sheet for [productId]. [accountId] is stamped on the purchase
     * (Play's obfuscated account id, StoreKit's appAccountToken) so the server can tell
     * whose it is.
     */
    suspend fun purchase(productId: String, accountId: String): StoreOutcome

    /** Purchases the store still holds for this device's account — for restore and for anything left unfinished. */
    suspend fun owned(): List<StoreReceipt>

    /** Tells the store the purchase was delivered. Play refunds one that is not acknowledged within three days. */
    suspend fun finish(receipt: StoreReceipt)
}

/** One purchase as the store reported it. [token] is what the server verifies. */
data class StoreReceipt(
    val productId: String,
    val token: String,
    /** False once the store has already been told; nothing more to finish. */
    val needsFinish: Boolean,
)

sealed interface StoreOutcome {
    data class Purchased(val receipt: StoreReceipt) : StoreOutcome
    /** Paid with a method that clears later (cash at a kiosk); the purchase arrives through [StoreBilling.owned]. */
    data object Pending : StoreOutcome
    data object Cancelled : StoreOutcome
    data class Failed(val reason: String) : StoreOutcome
}
