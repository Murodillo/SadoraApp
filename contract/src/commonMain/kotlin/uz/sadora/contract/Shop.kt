package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The Gul shop.
 *
 * Three kinds of thing are sold here and only one of them is delivered by the app.
 * Premium is granted directly, because the server owns entitlements. A vitamin or a
 * device is a partner's product: Gul buys a *discount* on it, and what the app hands
 * over is a code the partner honours. The app never claims to have shipped anything.
 *
 * Prices are in so'm and stay integers — there are no tiyin in practice, and a currency
 * held as a floating point number is a rounding bug waiting for a receipt.
 */

@Serializable
enum class ShopKind {
    /** Days of Premium, granted by the server the moment the coins are spent. */
    @SerialName("premium") PREMIUM,

    /** A supplement from a partner pharmacy — D3, K2, zinc, biotin, iron. */
    @SerialName("vitamin") VITAMIN,

    /** A wearable from a partner retailer — a watch, a band, a ring. */
    @SerialName("device") DEVICE,
}

/**
 * One card in the shop.
 *
 * [coinCost] is what it takes, [discountPercent] is what it gives on [priceUzs]. A
 * Premium row carries [premiumDays] instead and leaves the price null: there is nothing
 * to discount, the coins *are* the payment.
 *
 * [affordable] and [outOfStock] are the server's answers rather than the app's
 * arithmetic, so a card is never enabled on a phone whose balance is stale.
 */
@Serializable
data class ShopProduct(
    val id: String,
    val slug: String,
    val kind: ShopKind,
    val title: String,
    val brand: String? = null,
    val description: String? = null,
    val emoji: String? = null,
    val priceUzs: Long? = null,
    val discountPercent: Int = 0,
    val coinCost: Int = 0,
    val premiumDays: Int? = null,
    val stock: Int? = null,
    val affordable: Boolean = false,
    val outOfStock: Boolean = false,
) {
    /** What she would pay the partner after the discount, or null for Premium. */
    val discountedPriceUzs: Long?
        get() = priceUzs?.let { it - it * discountPercent / 100 }

    /** What the discount is worth in so'm, for the card's second line. */
    val savingUzs: Long?
        get() = priceUzs?.let { it * discountPercent / 100 }
}

/** The shop screen in one response: the balance, then everything on sale. */
@Serializable
data class ShopCatalog(
    val balance: Int = 0,
    val products: List<ShopProduct> = emptyList(),
)

@Serializable
enum class RedemptionStatus {
    @SerialName("issued") ISSUED,
    @SerialName("used") USED,
    @SerialName("expired") EXPIRED,
    @SerialName("cancelled") CANCELLED,
}

/**
 * What she got for her coins.
 *
 * [code] is the whole product for a partner item — she shows it at the counter — and is
 * still issued for Premium so that a support conversation has something to refer to.
 */
@Serializable
data class Redemption(
    val id: String,
    val productSlug: String,
    val productTitle: String,
    val kind: ShopKind,
    val brand: String? = null,
    val code: String,
    val coinCost: Int,
    val discountPercent: Int,
    val priceUzs: Long? = null,
    val premiumDays: Int? = null,
    val status: RedemptionStatus = RedemptionStatus.ISSUED,
    val expiresAt: Instant? = null,
    val createdAt: Instant,
) {
    val discountedPriceUzs: Long?
        get() = priceUzs?.let { it - it * discountPercent / 100 }
}

@Serializable
data class RedeemRequest(val productId: String)

/** The result of spending: what was issued, and what is left. */
@Serializable
data class RedeemResult(
    val redemption: Redemption,
    val coins: CoinBalance,
    /** True when the purchase extended Premium, so the app can refresh entitlements. */
    val premiumGranted: Boolean = false,
)

// ---------------------------------------------------------------- admin

/** What the panel writes. The slug is the identity and is set once, at creation. */
@Serializable
data class SaveShopProductRequest(
    val kind: ShopKind,
    val title: String,
    val brand: String? = null,
    val description: String? = null,
    val emoji: String? = null,
    val priceUzs: Long? = null,
    val discountPercent: Int = 0,
    val coinCost: Int = 0,
    val premiumDays: Int? = null,
    val stock: Int? = null,
    val active: Boolean = true,
    val position: Int = 0,
)

@Serializable
data class CreateShopProductRequest(
    val slug: String,
    val product: SaveShopProductRequest,
)

/** A product as the admin list shows it — inactive rows included. */
@Serializable
data class AdminShopProduct(
    val id: String,
    val slug: String,
    val kind: ShopKind,
    val title: String,
    val brand: String? = null,
    val description: String? = null,
    val emoji: String? = null,
    val priceUzs: Long? = null,
    val discountPercent: Int = 0,
    val coinCost: Int = 0,
    val premiumDays: Int? = null,
    val stock: Int? = null,
    val active: Boolean = true,
    val position: Int = 0,
    /** How many times it has been taken, so a rate nobody uses is visible as such. */
    val redeemed: Long = 0,
    val updatedAt: Instant? = null,
)

/** A redemption in the operator's list, with the account it belongs to. */
@Serializable
data class AdminRedemption(
    val id: String,
    val userId: String,
    val userName: String,
    val productTitle: String,
    val kind: ShopKind,
    val code: String,
    val coinCost: Int,
    val discountPercent: Int,
    val status: RedemptionStatus,
    val createdAt: Instant,
    val usedAt: Instant? = null,
)

@Serializable
data class UpdateRedemptionRequest(val status: RedemptionStatus)
