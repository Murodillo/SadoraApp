package uz.sadora.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A ring around her avatar: on her profile photo, and around her alias wherever the chat
 * draws it — posts, comments, conversations, her alias page.
 *
 * Each frame is had one way only ([FrameUnlock]): bought with Gul, given with a badge's
 * gold tier, or bought for money. A badge's frame is never sold, so the frame still says
 * she did the thing. Wearing one needs no Premium: she has already paid for it.
 *
 * On the wire a frame is its [AvatarFrames.Entry.key], a plain string, never an enum: the
 * catalogue grows with the seasons, and an app built before a frame existed must read a
 * post that wears it as a post with no frame, not fail the whole feed.
 */
object AvatarFrames {
    const val TULIP = "tulip"
    const val LAVENDER = "lavender"
    const val SAKURA = "sakura"
    const val MOON = "moon"
    const val ROSE = "rose"
    const val GOLD_FLAME = "gold_flame"
    const val GOLD_LAUREL = "gold_laurel"
    const val RAINBOW = "rainbow"
    const val HUMO_WING = "humo_wing"

    /**
     * One frame. [badge] and [badgeTier] are set for a [FrameUnlock.BADGE] frame: reaching
     * that tier of that badge is what owns it. Prices are not here — the panel edits them.
     */
    data class Entry(
        val key: String,
        val unlock: FrameUnlock,
        val animated: Boolean = false,
        val badge: String? = null,
        val badgeTier: Int = 0,
    )

    /** Every frame, in the order the frames page shows them. */
    val catalogue: List<Entry> = listOf(
        Entry(TULIP, FrameUnlock.COINS),
        Entry(LAVENDER, FrameUnlock.COINS),
        Entry(SAKURA, FrameUnlock.COINS),
        Entry(MOON, FrameUnlock.COINS),
        Entry(ROSE, FrameUnlock.COINS, animated = true),
        // The two hardest ladders, at gold: a hundred-day streak and a year with the app.
        Entry(GOLD_FLAME, FrameUnlock.BADGE, animated = true, badge = Badges.STREAK, badgeTier = 3),
        Entry(GOLD_LAUREL, FrameUnlock.BADGE, animated = true, badge = Badges.LOYAL, badgeTier = 3),
        Entry(RAINBOW, FrameUnlock.PAID, animated = true),
        Entry(HUMO_WING, FrameUnlock.PAID, animated = true),
    )

    fun byKey(key: String?): Entry? = key?.let { k -> catalogue.firstOrNull { it.key == k } }

    /** The frame a badge's tier gives, if that tier gives one. */
    fun forBadge(badge: String, tier: Int): Entry? =
        catalogue.firstOrNull { it.unlock == FrameUnlock.BADGE && it.badge == badge && it.badgeTier == tier }
}

/** How a frame becomes hers. */
@Serializable
enum class FrameUnlock {
    @SerialName("coins") COINS,
    @SerialName("badge") BADGE,
    @SerialName("paid") PAID,
}

/**
 * One frame on her frames page.
 *
 * A [FrameUnlock.COINS] frame carries [coinCost]; a [FrameUnlock.BADGE] one the badge and
 * tier that give it; a [FrameUnlock.PAID] one its [product] while it is on sale. A paid
 * frame off sale, or any frame the panel switched off, is left out unless she owns it.
 */
@Serializable
data class FrameState(
    val key: String,
    val unlock: FrameUnlock,
    val owned: Boolean = false,
    val animated: Boolean = false,
    val coinCost: Int? = null,
    val badge: String? = null,
    val badgeTier: Int = 0,
    val product: FrameProduct? = null,
)

/** A paid frame on sale: one price everywhere, in tiyin, and the store products that sell it. */
@Serializable
data class FrameProduct(
    val key: String,
    val priceMinor: Long,
    val currency: String = "UZS",
    val appStoreProductId: String? = null,
    val googlePlayProductId: String? = null,
    /** How she can pay for it here: Payme and Click where offered, the stores when in-app purchase is on. */
    val providers: List<PaymentProvider> = emptyList(),
)

/** The whole frames page in one read: every frame she can see, the one she wears, her Gul. */
@Serializable
data class FrameBoard(
    val frames: List<FrameState> = emptyList(),
    val worn: String? = null,
    val coins: Int = 0,
)

/** Wear a frame she owns; a null [key] takes hers off. */
@Serializable
data class WearFrameRequest(val key: String? = null)

/** Buy a [FrameUnlock.COINS] frame with Gul. */
@Serializable
data class BuyFrameRequest(val key: String)

/** Buy a paid frame with Payme or Click; the answer is the link to pay. */
@Serializable
data class FrameCheckoutRequest(val key: String, val provider: PaymentProvider)

/** A store receipt for a paid frame, bought for her own account. */
@Serializable
data class FrameStorePurchase(
    val key: String,
    val provider: PaymentProvider,
    val productId: String,
    val token: String,
)
