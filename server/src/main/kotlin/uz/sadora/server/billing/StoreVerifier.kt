package uz.sadora.server.billing

import kotlin.time.Instant
import uz.sadora.contract.PaymentProvider

/** What a store said about a purchase. */
data class VerifiedPurchase(
    val productId: String,
    val transactionId: String,
    val expiresAt: Instant?,
    val autoRenewing: Boolean,
)

/**
 * Checks a receipt with the store that issued it.
 *
 * An interface because the two real implementations need credentials this project does
 * not have yet — an App Store Connect key and a Google service account — and because the
 * one rule that matters is already expressible without them: a receipt is verified with
 * the store, never trusted from the client. [UnconfiguredStoreVerifier] therefore refuses
 * rather than approving, so a deployment without credentials cannot hand out Premium to
 * anyone who posts a plausible token.
 */
fun interface StoreVerifier {
    suspend fun verify(provider: PaymentProvider, productId: String, token: String): VerifiedPurchase
}

/** Raised when a receipt cannot be checked, or the store rejects it. */
class ReceiptRejectedException(message: String) : Exception(message)

/**
 * The default: no credentials, so nothing is verified and nothing is granted.
 *
 * This is deliberately a refusal and not a pass-through. A stub that approved everything
 * would work in every test and give away the product in production.
 */
object UnconfiguredStoreVerifier : StoreVerifier {
    override suspend fun verify(
        provider: PaymentProvider,
        productId: String,
        token: String,
    ): VerifiedPurchase = throw ReceiptRejectedException(
        "Store verification is not configured for ${provider.name.lowercase()}",
    )
}
