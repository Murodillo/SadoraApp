package uz.sadora.server.frame

import kotlin.uuid.Uuid
import uz.sadora.contract.AvatarFrames
import uz.sadora.contract.CheckoutSession
import uz.sadora.contract.FrameBoard
import uz.sadora.contract.FrameCheckoutRequest
import uz.sadora.contract.FrameProduct
import uz.sadora.contract.FrameState
import uz.sadora.contract.FrameStorePurchase
import uz.sadora.contract.FrameUnlock
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentState
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.billing.BillingRepository
import uz.sadora.server.billing.BillingService
import uz.sadora.server.billing.ReceiptRejectedException
import uz.sadora.server.billing.StoreVerifier
import uz.sadora.server.billing.TransactionRecord
import uz.sadora.server.config.Environment
import uz.sadora.server.core.ConflictException
import uz.sadora.server.core.FeatureDisabledException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.flags.FeatureFlagService
import uz.sadora.server.flags.FlagContext
import uz.sadora.server.rewards.RewardsRepository
import uz.sadora.server.user.UserRepository

/**
 * Avatar frames: what she owns, what she wears, and the three ways in.
 *
 * Gul buys a frame in one transaction under the wallet's lock, so the coins and the frame
 * go together or not at all. A badge's frame is owned by reaching the badge's tier — no
 * row, nothing to grant. Money takes the legendary pet's road: Payme and Click make a
 * pending payment, a store receipt is checked with the store, Yaqinim pays through the
 * request service, and all three end in [BillingService.activate], which calls [grant].
 *
 * Wearing is checked against ownership on every read rather than when it is chosen, so a
 * refund takes the frame off everywhere at once.
 */
class FrameService(
    private val frames: FrameRepository,
    private val wallet: RewardsRepository,
    private val billing: BillingService,
    private val billingRepository: BillingRepository,
    private val verifier: StoreVerifier,
    private val flags: FeatureFlagService,
    private val environment: Environment,
    private val users: UserRepository,
    private val audit: AuditService,
) {

    /** The same shop checking receipts with [other] — for tests, which cannot reach a store. */
    fun verifiedBy(other: StoreVerifier) =
        FrameService(frames, wallet, billing, billingRepository, other, flags, environment, users, audit).also { it.owned = owned }

    /** Told when a paid frame becomes hers, so a request still asking for it can close. Set once at wiring. */
    var owned: (suspend (userId: Uuid, frame: String, requestId: Uuid?) -> Unit)? = null

    // ---------------------------------------------------------------- her page

    suspend fun board(userId: Uuid): FrameBoard {
        val mine = frames.owned(userId)
        val products = frames.products(activeOnly = false).associateBy { it.frame }
        val onSale = onSale(userId)
        val providers = if (onSale) providers(userId) else emptyList()
        val states = AvatarFrames.catalogue.mapNotNull { entry ->
            val owned = entry.key in mine
            val product = products[entry.key]
            // Switched off in the panel: gone from the page unless it is already hers.
            if (!owned && entry.unlock != FrameUnlock.BADGE && product?.active != true) return@mapNotNull null
            val paid = if (entry.unlock == FrameUnlock.PAID && !owned && onSale) {
                product?.priceMinor?.let {
                    FrameProduct(entry.key, it, product.currency, product.appStoreProductId, product.googlePlayProductId, providers)
                }
            } else {
                null
            }
            // A paid frame off sale and not hers has nothing to show but a lock.
            if (entry.unlock == FrameUnlock.PAID && !owned && paid == null) return@mapNotNull null
            FrameState(
                key = entry.key,
                unlock = entry.unlock,
                owned = owned,
                animated = entry.animated,
                coinCost = product?.coinCost?.takeIf { entry.unlock == FrameUnlock.COINS },
                badge = entry.badge,
                badgeTier = entry.badgeTier,
                product = paid,
            )
        }
        return FrameBoard(
            frames = states,
            worn = frames.worn(userId)?.takeIf { it in mine },
            coins = wallet.balance(userId).balance,
        )
    }

    /** Buys a Gul frame: the coins and the frame in one transaction. */
    suspend fun buyWithCoins(userId: Uuid, key: String): FrameBoard {
        val entry = AvatarFrames.byKey(key) ?: throw NotFoundException("Bunday ramka yo'q")
        if (entry.unlock != FrameUnlock.COINS) throw ValidationException("key", "Bu ramka Gulga sotilmaydi")
        val cost = frames.products().firstOrNull { it.frame == key }?.coinCost ?: throw NotFoundException("Bunday ramka yo'q")
        if (key in frames.owned(userId)) throw ConflictException("Bu ramka allaqachon sizniki")
        val repository = frames
        wallet.spendAlongside(userId, cost, "frame:$key") {
            with(repository) { insertOwned(userId, key, SOURCE_COINS) }
        } ?: run {
            if (wallet.balance(userId).balance < cost) throw ValidationException("coins", "Gul yetarli emas")
            throw ConflictException("Bu ramka allaqachon sizniki")
        }
        audit.record(
            AuditEntry(
                actorType = ActorType.USER,
                actorId = userId,
                action = "frame.bought",
                entityType = "avatar_frame",
                entityId = key,
                metadata = mapOf("coins" to cost.toString()),
            ),
        )
        return board(userId)
    }

    /** Wears a frame she owns, or takes hers off. No Premium: she already paid for it. */
    suspend fun wear(userId: Uuid, key: String?): FrameBoard {
        if (key != null) {
            if (AvatarFrames.byKey(key) == null) throw NotFoundException("Bunday ramka yo'q")
            if (key !in frames.owned(userId)) throw ValidationException("key", "Bu ramka hali sizniki emas")
        }
        frames.wear(userId, key)
        return board(userId)
    }

    /**
     * The frame each author wears, for the chat — only one she still owns. Never throws:
     * a feed with no frames is better than no feed.
     */
    suspend fun wornBy(userIds: Collection<Uuid>): Map<Uuid, String> = frames.wornBy(userIds)

    // ---------------------------------------------------------------- paid frames

    suspend fun onSale(userId: Uuid): Boolean {
        val user = users.findById(userId) ?: return false
        return flags.isEnabled(
            SALE_FLAG,
            FlagContext(userId = userId, environment = environment, language = user.language, lifeStage = user.lifeStage),
        )
    }

    /** Payme or Click: a pending payment and the link that pays it. */
    suspend fun checkout(userId: Uuid, request: FrameCheckoutRequest, origin: String): CheckoutSession {
        val product = forSale(userId, request.key)
        if (request.provider != PaymentProvider.PAYME && request.provider != PaymentProvider.CLICK) {
            throw ValidationException("provider", "Store xaridi ilova ichida bo'ladi")
        }
        return billing.frameCheckout(userId, product.frame, product.priceMinor!!, request.provider, origin)
    }

    /** The price row a paid frame is sold or asked for at; refused while the sale is off or it is already hers. */
    suspend fun forSale(userId: Uuid, key: String): FrameProductRecord {
        val entry = AvatarFrames.byKey(key) ?: throw NotFoundException("Bunday ramka yo'q")
        if (entry.unlock != FrameUnlock.PAID || !onSale(userId)) throw FeatureDisabledException(SALE_FLAG)
        if (key in frames.owned(userId)) throw ConflictException("Bu ramka allaqachon sizniki")
        return frames.products().firstOrNull { it.frame == key && it.priceMinor != null }
            ?: throw NotFoundException("Bunday mahsulot yo'q")
    }

    /** The price row for a stored frame key, whatever the sale says — a request already made is honoured. */
    suspend fun product(key: String?): FrameProductRecord? =
        key?.let { k -> frames.products(activeOnly = false).firstOrNull { it.frame == k && it.priceMinor != null } }

    /** Whether she already owns [key] — a request for it is then moot. */
    suspend fun owns(userId: Uuid, key: String?): Boolean = key != null && key in frames.owned(userId)

    /**
     * A store receipt, checked with the store and bought for her own account. The store's
     * transaction id makes it idempotent: a restore or a retry finds the payment and the
     * frame already there.
     */
    suspend fun buyInStore(userId: Uuid, purchase: FrameStorePurchase): FrameBoard {
        val product = storeProduct(purchase.provider, purchase.productId)
            ?.takeIf { it.frame == purchase.key && it.priceMinor != null }
            ?: throw ValidationException("productId", "Bunday mahsulot yo'q")
        if (purchase.token.isBlank()) throw ValidationException("token", "Bo'sh bo'lishi mumkin emas")

        val verified = try {
            verifier.verifyOneTime(purchase.provider, purchase.productId, purchase.token)
        } catch (rejected: ReceiptRejectedException) {
            throw ValidationException("token", rejected.message ?: "Chek tasdiqlanmadi")
        }
        if (verified.accountId != userId.toString()) throw ValidationException("token", "Bu xarid boshqa hisobga tegishli")

        val existing = billingRepository.byExternalId(purchase.provider, verified.transactionId)
        if (existing != null && (existing.userId != userId || existing.frame != product.frame)) {
            throw ConflictException("Bu chek allaqachon qayd etilgan")
        }
        val transaction = existing ?: billingRepository.createTransaction(
            userId = userId,
            planId = null,
            provider = purchase.provider,
            amountMinor = product.priceMinor!!,
            currency = product.currency,
            frame = product.frame,
        ).also { created ->
            if (!billingRepository.attachExternalId(created.id, verified.transactionId, null)) {
                throw ConflictException("Bu chek allaqachon qayd etilgan")
            }
        }
        billing.activate(billingRepository.transaction(transaction.id) ?: transaction)
        return board(userId)
    }

    /**
     * The money arrived: the frame is hers, and she wears it at once — she just paid to.
     * Reached only from [BillingService.activate]; a retry finds the row given and changes nothing.
     */
    suspend fun grant(transaction: TransactionRecord) {
        val key = transaction.frame?.takeIf { AvatarFrames.byKey(it) != null } ?: return
        if (!frames.grant(transaction.userId, key, SOURCE_PAID, transaction.id)) return
        frames.wear(transaction.userId, key)
        owned?.invoke(transaction.userId, key, transaction.paymentRequestId)
    }

    /**
     * The operator's refund: the money went back in the provider's cabinet and the frame
     * goes too — off her avatar everywhere, since wearing is read against ownership. False
     * when [transactionId] bought no frame, so the caller can treat it as something else.
     */
    suspend fun refundIfFrame(transactionId: Uuid, adminId: Uuid?): Boolean {
        val transaction = billingRepository.transaction(transactionId) ?: throw NotFoundException("To'lov topilmadi")
        val key = transaction.frame ?: return false
        if (transaction.state != PaymentState.PAID) throw ValidationException("id", "Faqat to'langan sovg'ani qaytarish mumkin")
        if (!billingRepository.markRefunded(transaction.id)) return true
        frames.revoke(transaction.id)
        if (frames.worn(transaction.userId) == key && key !in frames.owned(transaction.userId)) {
            frames.wear(transaction.userId, null)
        }
        audit.record(
            AuditEntry(
                actorType = if (adminId != null) ActorType.ADMIN else ActorType.SYSTEM,
                actorId = adminId,
                action = "payment.frame_refunded",
                entityType = "payment_transaction",
                entityId = transaction.id.toString(),
            ),
        )
        return true
    }

    // ---------------------------------------------------------------- the panel

    suspend fun adminProducts(): List<AdminFrameProduct> {
        val rows = frames.products(activeOnly = false).associateBy { it.frame }
        return AvatarFrames.catalogue.filter { it.unlock != FrameUnlock.BADGE }.mapNotNull { entry ->
            val row = rows[entry.key] ?: return@mapNotNull null
            AdminFrameProduct(
                key = entry.key,
                unlock = entry.unlock,
                coinCost = row.coinCost,
                priceMinor = row.priceMinor,
                currency = row.currency,
                appStoreProductId = row.appStoreProductId,
                googlePlayProductId = row.googlePlayProductId,
                active = row.active,
            )
        }
    }

    suspend fun updateProduct(key: String, request: UpdateFrameProductRequest, adminId: Uuid) {
        val entry = AvatarFrames.byKey(key)?.takeIf { it.unlock != FrameUnlock.BADGE }
            ?: throw NotFoundException("Bunday mahsulot yo'q")
        val current = frames.products(activeOnly = false).firstOrNull { it.frame == key } ?: throw NotFoundException("Bunday mahsulot yo'q")
        var coinCost = current.coinCost
        var priceMinor = current.priceMinor
        if (entry.unlock == FrameUnlock.COINS) {
            val cost = request.coinCost ?: coinCost ?: throw ValidationException("coinCost", "Ko'rsatilishi shart")
            if (cost < 1) throw ValidationException("coinCost", "Narx juda past")
            coinCost = cost
        } else {
            val price = request.priceMinor ?: priceMinor ?: throw ValidationException("priceMinor", "Ko'rsatilishi shart")
            if (price < MIN_PRICE_MINOR) throw ValidationException("priceMinor", "Narx juda past")
            priceMinor = price
        }
        frames.updateProduct(key, coinCost, priceMinor, request.active)
        audit.record(
            AuditEntry(
                actorType = ActorType.ADMIN,
                actorId = adminId,
                action = "frame_product.updated",
                entityType = "avatar_frame",
                entityId = key,
                metadata = buildMap {
                    coinCost?.let { put("coinCost", it.toString()) }
                    priceMinor?.let { put("priceMinor", it.toString()) }
                    put("active", request.active.toString())
                },
            ),
        )
    }

    /** One account's frames for the user card. */
    suspend fun adminUserFrames(userId: Uuid): AdminUserFrames {
        val mine = frames.owned(userId)
        return AdminUserFrames(owned = AvatarFrames.catalogue.map { it.key }.filter { it in mine }, worn = frames.worn(userId)?.takeIf { it in mine })
    }

    /** An operator's present — a contest prize. Any frame but a badge's, which only the badge gives. */
    suspend fun adminGrant(userId: Uuid, key: String, adminId: Uuid): AdminUserFrames {
        val entry = AvatarFrames.byKey(key) ?: throw NotFoundException("Bunday ramka yo'q")
        if (entry.unlock == FrameUnlock.BADGE) throw ValidationException("key", "Nishon ramkasi faqat nishon bilan beriladi")
        users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")
        if (key in frames.owned(userId)) throw ConflictException("Bu ramka allaqachon sizniki")
        frames.grant(userId, key, SOURCE_ADMIN)
        // A request asking Yaqinim for it would only take a second payment for nothing.
        owned?.invoke(userId, key, null)
        audit.record(
            AuditEntry(
                actorType = ActorType.ADMIN,
                actorId = adminId,
                action = "frame.granted",
                entityType = "user",
                entityId = userId.toString(),
                metadata = mapOf("frame" to key),
            ),
        )
        return adminUserFrames(userId)
    }

    private suspend fun storeProduct(provider: PaymentProvider, productId: String): FrameProductRecord? =
        frames.products().firstOrNull {
            when (provider) {
                PaymentProvider.APP_STORE -> it.appStoreProductId == productId
                PaymentProvider.GOOGLE_PLAY -> it.googlePlayProductId == productId
                else -> throw ValidationException("provider", "Bu provayder store emas")
            }
        }

    /** Payme and Click where they are offered, and the stores when in-app purchase is on. */
    private suspend fun providers(userId: Uuid): List<PaymentProvider> {
        val stores = billing.catalogue(userId).providers.filter { it == PaymentProvider.APP_STORE || it == PaymentProvider.GOOGLE_PLAY }
        return billing.consultationProviders(userId) + stores
    }

    companion object {
        /** Off until the store products exist; an operator turns it on in the panel. */
        const val SALE_FLAG = "frame_sale"

        /** 1 000 so'm: below it a price is a typo, not a decision. */
        const val MIN_PRICE_MINOR = 100_000L

        const val SOURCE_COINS = "coins"
        const val SOURCE_PAID = "paid"
        const val SOURCE_ADMIN = "admin"
    }
}

/** A sold frame's prices, as the panel edits them. */
@kotlinx.serialization.Serializable
data class AdminFrameProduct(
    val key: String,
    val unlock: FrameUnlock,
    val coinCost: Int? = null,
    val priceMinor: Long? = null,
    val currency: String = "UZS",
    val appStoreProductId: String? = null,
    val googlePlayProductId: String? = null,
    val active: Boolean = true,
)

/** A Gul frame takes [coinCost], a paid one [priceMinor]; the other is ignored. */
@kotlinx.serialization.Serializable
data class UpdateFrameProductRequest(
    val coinCost: Int? = null,
    val priceMinor: Long? = null,
    val active: Boolean = true,
)

@kotlinx.serialization.Serializable
data class AdminUserFrames(val owned: List<String> = emptyList(), val worn: String? = null)

@kotlinx.serialization.Serializable
data class GrantFrameRequest(val key: String)
