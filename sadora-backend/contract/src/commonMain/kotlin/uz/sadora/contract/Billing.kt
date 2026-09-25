package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What Premium costs and how it is paid for.
 *
 * Prices live on the server, not in the app: a price change must not need a release, and
 * two app versions must never quote different numbers for the same plan.
 */

/** How a plan is billed. The period is what the price buys, once. */
@Serializable
enum class BillingPeriod {
    @SerialName("month") MONTH,
    @SerialName("year") YEAR,
}

/** Where the money comes in. */
@Serializable
enum class PaymentProvider {
    @SerialName("payme") PAYME,
    @SerialName("click") CLICK,
    @SerialName("app_store") APP_STORE,
    @SerialName("google_play") GOOGLE_PLAY,
}

/**
 * One thing she can buy.
 *
 * [priceMinor] is in tiyin — the currency's smallest unit — because that is what the
 * payment providers count in and money in a floating point number is a bug waiting for a
 * big enough sum. The app formats it; nothing computes with the formatted string.
 */
@Serializable
data class BillingPlan(
    val id: String,
    val title: String,
    val period: BillingPeriod,
    val priceMinor: Long,
    val currency: String = "UZS",
    /** The same price expressed per month, so a year plan can show what it saves. */
    val monthlyEquivalentMinor: Long? = null,
    val trialDays: Int = 0,
    val highlighted: Boolean = false,
    /** Store product ids, when the purchase goes through a platform's billing instead. */
    val appStoreProductId: String? = null,
    val googlePlayProductId: String? = null,
)

/** The plans, and which providers are actually open right now. */
@Serializable
data class BillingCatalogue(
    val plans: List<BillingPlan>,
    val providers: List<PaymentProvider>,
)

@Serializable
data class CheckoutRequest(
    val planId: String,
    val provider: PaymentProvider,
)

/**
 * Where to send her to pay.
 *
 * [url] is the provider's hosted checkout. The app opens it and does not handle card
 * details itself — that is the point of hosted checkout, and it keeps the app out of PCI
 * scope entirely.
 */
@Serializable
data class CheckoutSession(
    val transactionId: String,
    val provider: PaymentProvider,
    val url: String,
    val amountMinor: Long,
    val currency: String = "UZS",
    val expiresAt: Instant? = null,
)

/** Where a payment has got to. The app polls this after returning from checkout. */
@Serializable
enum class PaymentState {
    @SerialName("pending") PENDING,
    @SerialName("paid") PAID,
    @SerialName("cancelled") CANCELLED,
    @SerialName("failed") FAILED,
}

@Serializable
data class PaymentStatus(
    val transactionId: String,
    val state: PaymentState,
    val provider: PaymentProvider,
    val planId: String,
    val amountMinor: Long,
    val paidAt: Instant? = null,
    /** The subscription this payment produced, once it has produced one. */
    val subscription: SubscriptionStatus? = null,
)

/** A receipt from App Store or Google Play, handed over for verification. */
@Serializable
data class StorePurchaseRequest(
    val provider: PaymentProvider,
    val productId: String,
    /** The store's own token: `signedTransactionInfo` on iOS, `purchaseToken` on Android. */
    val token: String,
)
