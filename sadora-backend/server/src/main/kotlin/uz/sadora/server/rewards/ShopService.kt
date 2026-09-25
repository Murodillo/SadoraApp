package uz.sadora.server.rewards

import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid
import uz.sadora.contract.AdminRedemption
import uz.sadora.contract.AdminShopProduct
import uz.sadora.contract.CreateShopProductRequest
import uz.sadora.contract.Redemption
import uz.sadora.contract.RedemptionStatus
import uz.sadora.contract.RedeemResult
import uz.sadora.contract.RewardsOverview
import uz.sadora.contract.SaveShopProductRequest
import uz.sadora.contract.ShopCatalog
import uz.sadora.contract.ShopKind
import uz.sadora.contract.SubscriptionSource
import uz.sadora.server.core.ConflictException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.now
import uz.sadora.server.entitlement.EntitlementService
import uz.sadora.server.entitlement.SubscriptionRepository

/**
 * Spending Gul.
 *
 * Premium is the only thing this server can actually deliver, and it does: the days are
 * granted the moment the coins are spent. A vitamin or a device belongs to a partner, so
 * what she gets is a code and a discount, and the app is careful never to imply that
 * anything has been shipped.
 *
 * The order of operations matters and is the reverse of the obvious one. The code is
 * issued *first* — that is the step which can fail on stock — and only then are the
 * coins taken. Spending first would leave a debited account holding nothing on the day a
 * partner's last unit went to somebody else a second earlier.
 */
class ShopService(
    private val shop: ShopRepository,
    private val rewards: RewardsRepository,
    private val subscriptions: SubscriptionRepository,
    private val entitlements: EntitlementService,
) {

    suspend fun catalogue(userId: Uuid): ShopCatalog {
        val balance = rewards.balance(userId).balance
        return ShopCatalog(
            balance = balance,
            products = shop.catalogue().map { it.toProduct(balance) },
        )
    }

    suspend fun redemptions(userId: Uuid): List<Redemption> = shop.redemptionsOf(userId)

    /**
     * Takes the coins and issues the code.
     *
     * Every refusal is its own error so the app can say which one it is: not enough Gul
     * reads differently from a product that has just gone out of stock, and a paywall is
     * the wrong screen for both.
     */
    suspend fun redeem(userId: Uuid, productId: Uuid): RedeemResult {
        val product = shop.byId(productId)?.takeIf { it.active }
            ?: throw NotFoundException("Mahsulot topilmadi")

        val balance = rewards.balance(userId)
        if (balance.balance < product.coinCost) {
            throw ValidationException("coins", "Gul yetarli emas")
        }

        val expiresAt = if (product.kind == ShopKind.PREMIUM) null else now() + CODE_VALIDITY
        val redemption = shop.issue(userId, product, generateCode(), expiresAt)
            ?: throw ConflictException("Mahsulot tugadi")

        // The code exists; now pay for it. If this fails the coins were never taken and
        // the code is cancelled rather than left as a free one.
        val spent = rewards.spend(userId, product.coinCost, redemption.id)
        if (spent == null) {
            shop.setStatus(Uuid.parse(redemption.id), RedemptionStatus.CANCELLED)
            // The unit came off the shelf before the coins were asked for; it goes back.
            shop.restock(product.id)
            throw ValidationException("coins", "Gul yetarli emas")
        }

        var premiumGranted = false
        if (product.kind == ShopKind.PREMIUM && product.premiumDays != null) {
            // Extends an existing subscription rather than replacing it: buying a week
            // on the last day of a month should add to it, not cut it short.
            val current = entitlements.subscriptionStatus(userId).expiresAt
            val from = current?.takeIf { it > now() } ?: now()
            subscriptions.grant(
                userId = userId,
                source = SubscriptionSource.MANUAL,
                expiresAt = from + product.premiumDays.days,
                productId = product.slug,
                externalId = redemption.id,
                reason = "nur_shop",
            )
            premiumGranted = true
        }

        return RedeemResult(
            redemption = redemption,
            coins = spent,
            premiumGranted = premiumGranted,
        )
    }

    // ---------------------------------------------------------------- admin

    suspend fun adminCatalogue(): List<AdminShopProduct> = shop.adminCatalogue()

    suspend fun create(request: CreateShopProductRequest): AdminShopProduct {
        val slug = request.slug.trim().lowercase()
        if (!SLUG.matches(slug)) {
            throw ValidationException("slug", "Faqat kichik harflar, raqamlar va chiziqcha")
        }
        validate(request.product)
        if (shop.catalogue(includeInactive = true).any { it.slug == slug }) {
            throw ConflictException("Bunday slug allaqachon bor")
        }
        shop.create(slug, request.product)
        return adminCatalogue().first { it.slug == slug }
    }

    suspend fun update(id: Uuid, request: SaveShopProductRequest): AdminShopProduct {
        validate(request)
        shop.update(id, request) ?: throw NotFoundException("Mahsulot topilmadi")
        return adminCatalogue().first { it.id == id.toString() }
    }

    /** Deletes an unredeemed product; one with codes against it is deactivated instead. */
    suspend fun delete(id: Uuid): Boolean {
        shop.byId(id) ?: throw NotFoundException("Mahsulot topilmadi")
        return shop.delete(id)
    }

    suspend fun recentRedemptions(limit: Int): List<AdminRedemption> = shop.recentRedemptions(limit)

    suspend fun setRedemptionStatus(id: Uuid, status: RedemptionStatus) {
        if (!shop.setStatus(id, status)) throw NotFoundException("Xarid topilmadi")
    }

    suspend fun overview(): RewardsOverview {
        val totals = rewards.overview()
        return RewardsOverview(
            coinsOutstanding = totals.earned - totals.spent,
            coinsEarnedTotal = totals.earned,
            coinsSpentTotal = totals.spent,
            redemptionsIssued = shop.issuedCount(),
            activeStreaks = totals.activeStreaks,
            longestStreak = totals.longestStreak,
            referralsAccepted = totals.referralsAccepted,
        )
    }

    /**
     * The two shapes a product can have, checked here as well as in the schema.
     *
     * The CHECK constraint would refuse a bad row anyway, but a constraint violation
     * reaches the operator as "internal error" — this reaches her as the field that is
     * wrong, which is the difference between fixing it and filing a bug.
     */
    private fun validate(request: SaveShopProductRequest) {
        if (request.title.isBlank()) throw ValidationException("title", "Nomi bo'sh")
        if (request.title.length > TITLE_MAX) {
            throw ValidationException("title", "Nomi $TITLE_MAX ta belgidan oshmasligi kerak")
        }
        if (request.coinCost < 0) throw ValidationException("coinCost", "Manfiy bo'lishi mumkin emas")
        if (request.discountPercent !in 0..100) {
            throw ValidationException("discountPercent", "0 dan 100 gacha bo'lishi kerak")
        }
        request.stock?.let { if (it < 0) throw ValidationException("stock", "Manfiy bo'lishi mumkin emas") }

        if (request.kind == ShopKind.PREMIUM) {
            val days = request.premiumDays
                ?: throw ValidationException("premiumDays", "Premium uchun kunlar soni kerak")
            if (days !in 1..PREMIUM_DAYS_MAX) {
                throw ValidationException("premiumDays", "1 dan $PREMIUM_DAYS_MAX gacha")
            }
        } else {
            val price = request.priceUzs
                ?: throw ValidationException("priceUzs", "Narx kerak — chegirma nimadan hisoblanadi")
            if (price < 0) throw ValidationException("priceUzs", "Manfiy bo'lishi mumkin emas")
            if (request.premiumDays != null) {
                throw ValidationException("premiumDays", "Faqat Premium mahsulot uchun")
            }
        }
    }

    /**
     * A code a cashier can read off a phone screen: `SDR-XXXX-XXXX`, no ambiguous
     * characters, and checked for collision before it is written.
     */
    private suspend fun generateCode(): String {
        repeat(CODE_ATTEMPTS) {
            val body = (1..8).map { CODE_ALPHABET.random(Random) }.joinToString("")
            val code = "SDR-${body.take(4)}-${body.drop(4)}"
            if (!shop.codeExists(code)) return code
        }
        throw ConflictException("Kod yaratib bo'lmadi, qayta urinib ko'ring")
    }

    private companion object {
        val SLUG = Regex("^[a-z0-9-]{2,64}$")
        val CODE_VALIDITY = 90.days
        const val CODE_ALPHABET = "BCDFGHJKLMNPQRSTVWXYZ23456789"
        const val CODE_ATTEMPTS = 8
        const val TITLE_MAX = 120
        const val PREMIUM_DAYS_MAX = 365
    }
}
