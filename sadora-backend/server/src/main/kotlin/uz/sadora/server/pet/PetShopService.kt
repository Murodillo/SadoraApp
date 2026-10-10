package uz.sadora.server.pet

import kotlin.uuid.Uuid
import uz.sadora.contract.CheckoutSession
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentState
import uz.sadora.contract.PetCheckoutRequest
import uz.sadora.contract.PetKind
import uz.sadora.contract.PetProduct
import uz.sadora.contract.PetStorePurchase
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
import uz.sadora.server.user.UserRepository

/**
 * Selling the legendary pet.
 *
 * Three ways in, one way to own it. Payme and Click make a pending payment and a link; a
 * store receipt is checked with the store, for her own account; Yaqinim's payment comes
 * through the request service. All three end in [BillingService.activate], which calls
 * [grant] — so nothing here hands out a pet on the client's word.
 *
 * The sale is behind [SALE_FLAG], off until the store products exist. A pet already
 * bought stays hers whatever the flag says, and a receipt for one is honoured even while
 * the sale is off: the money has already moved.
 */
class PetShopService(
    private val pets: PetRepository,
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
        PetShopService(pets, billing, billingRepository, other, flags, environment, users, audit).also { it.owned = owned }

    /** Told when a pet becomes hers, so a request still asking for it can close. Set once at wiring. */
    var owned: (suspend (userId: Uuid, petKey: String, requestId: Uuid?) -> Unit)? = null

    suspend fun onSale(userId: Uuid): Boolean {
        val user = users.findById(userId) ?: return false
        return flags.isEnabled(
            SALE_FLAG,
            FlagContext(userId = userId, environment = environment, language = user.language, lifeStage = user.lifeStage),
        )
    }

    /** What is on sale to her: nothing while the sale is off, and nothing she already owns. */
    suspend fun products(userId: Uuid, owned: List<PetKind>): List<PetProduct> {
        if (!onSale(userId)) return emptyList()
        val providers = providers(userId)
        return pets.products().filter { it.pet !in owned }.map {
            PetProduct(
                pet = it.pet,
                priceMinor = it.priceMinor,
                currency = it.currency,
                appStoreProductId = it.appStoreProductId,
                googlePlayProductId = it.googlePlayProductId,
                providers = providers,
            )
        }
    }

    /** Payme or Click: a pending payment and the link that pays it. */
    suspend fun checkout(userId: Uuid, request: PetCheckoutRequest, origin: String): CheckoutSession {
        val product = forSale(userId, request.pet)
        if (request.provider != PaymentProvider.PAYME && request.provider != PaymentProvider.CLICK) {
            throw ValidationException("provider", "App Store yoki Google Play xaridi ilova ichida bo'ladi")
        }
        return billing.petCheckout(userId, product.pet.wireKey, product.priceMinor, request.provider, origin)
    }

    /** The price row for a stored pet key, whatever the sale says — a request already made is honoured. */
    suspend fun product(petKey: String?): PetProductRecord? =
        petKey?.let { key -> pets.products().firstOrNull { it.pet.wireKey == key } }

    /** Whether she already owns the pet stored as [petKey] — a request for it is then moot. */
    suspend fun owns(userId: Uuid, petKey: String?): Boolean = pets.owned(userId).any { it.wireKey == petKey }

    /** The price row a request to Yaqinim is made for. */
    suspend fun forSale(userId: Uuid, pet: PetKind): PetProductRecord {
        if (!pet.legendary || !onSale(userId)) throw FeatureDisabledException(SALE_FLAG)
        if (pet in pets.owned(userId)) throw ConflictException("Bu hamroh allaqachon sizniki")
        return pets.products().firstOrNull { it.pet == pet } ?: throw NotFoundException("Bunday mahsulot yo'q")
    }

    /**
     * A store receipt, checked with the store and bought for her own account. The store's
     * transaction id makes it idempotent: a restore or a retry finds the payment and the
     * pet already there.
     */
    suspend fun buyInStore(userId: Uuid, purchase: PetStorePurchase): List<PetKind> {
        val product = storeProduct(purchase.provider, purchase.productId)
            ?.takeIf { it.pet == purchase.pet }
            ?: throw ValidationException("productId", "Bunday mahsulot yo'q")
        if (purchase.token.isBlank()) throw ValidationException("token", "Bo'sh bo'lishi mumkin emas")

        val verified = try {
            verifier.verifyOneTime(purchase.provider, purchase.productId, purchase.token)
        } catch (rejected: ReceiptRejectedException) {
            throw ValidationException("token", rejected.message ?: "Chek tasdiqlanmadi")
        }
        if (verified.accountId != userId.toString()) throw ValidationException("token", "Bu xarid boshqa hisobga tegishli")

        val existing = billingRepository.byExternalId(purchase.provider, verified.transactionId)
        if (existing != null && (existing.userId != userId || existing.pet != product.pet.wireKey)) {
            throw ConflictException("Bu chek allaqachon qayd etilgan")
        }
        val transaction = existing ?: billingRepository.createTransaction(
            userId = userId,
            planId = null,
            provider = purchase.provider,
            amountMinor = product.priceMinor,
            currency = product.currency,
            pet = product.pet.wireKey,
        ).also { created ->
            if (!billingRepository.attachExternalId(created.id, verified.transactionId, null)) {
                throw ConflictException("Bu chek allaqachon qayd etilgan")
            }
        }
        billing.activate(billingRepository.transaction(transaction.id) ?: transaction)
        return pets.owned(userId)
    }

    /**
     * The money arrived: the pet is hers, and comes to her at once. Reached only from
     * [BillingService.activate]; a retry finds the row given and changes nothing.
     */
    suspend fun grant(transaction: TransactionRecord) {
        val pet = transaction.pet?.let { key -> PetKind.entries.firstOrNull { it.wireKey == key } } ?: return
        if (!pets.grant(transaction.userId, pet, transaction.id)) return
        pets.choose(transaction.userId, pet)
        owned?.invoke(transaction.userId, pet.wireKey, transaction.paymentRequestId)
    }

    /**
     * The operator's refund: the money went back in the provider's cabinet, the pet goes
     * too, and she is back on the pet she had. False when [transactionId] bought no pet,
     * so the caller can treat it as some other kind of payment.
     */
    suspend fun refundIfPet(transactionId: Uuid, adminId: Uuid?): Boolean {
        val transaction = billingRepository.transaction(transactionId) ?: throw NotFoundException("To'lov topilmadi")
        val pet = transaction.pet?.let { key -> PetKind.entries.firstOrNull { it.wireKey == key } } ?: return false
        if (transaction.state != PaymentState.PAID) throw ValidationException("id", "Faqat to'langan sovg'ani qaytarish mumkin")
        if (!billingRepository.markRefunded(transaction.id)) return true
        pets.revoke(transaction.id)
        pets.stepBackFrom(transaction.userId, pet)
        audit.record(
            AuditEntry(
                actorType = if (adminId != null) ActorType.ADMIN else ActorType.SYSTEM,
                actorId = adminId,
                action = "payment.pet_refunded",
                entityType = "payment_transaction",
                entityId = transaction.id.toString(),
            ),
        )
        return true
    }

    // ---------------------------------------------------------------- the panel

    suspend fun adminProducts(): List<AdminPetProduct> = pets.products(activeOnly = false).map {
        AdminPetProduct(it.pet, it.priceMinor, it.currency, it.appStoreProductId, it.googlePlayProductId, it.active)
    }

    suspend fun updateProduct(pet: PetKind, request: UpdatePetProductRequest, adminId: Uuid) {
        if (request.priceMinor < MIN_PRICE_MINOR) throw ValidationException("priceMinor", "Narx juda past")
        if (!pets.updateProduct(pet, request.priceMinor, request.active)) throw NotFoundException("Bunday mahsulot yo'q")
        audit.record(
            AuditEntry(
                actorType = ActorType.ADMIN,
                actorId = adminId,
                action = "pet_product.updated",
                entityType = "pet_product",
                entityId = pet.wireKey,
                metadata = mapOf("priceMinor" to request.priceMinor.toString(), "active" to request.active.toString()),
            ),
        )
    }

    private suspend fun storeProduct(provider: PaymentProvider, productId: String): PetProductRecord? =
        pets.products().firstOrNull {
            when (provider) {
                PaymentProvider.APP_STORE -> it.appStoreProductId == productId
                PaymentProvider.GOOGLE_PLAY -> it.googlePlayProductId == productId
                else -> throw ValidationException("provider", "Bu to'lov usuli App Store yoki Google Play emas")
            }
        }

    /** Payme and Click where they are offered, and the stores when in-app purchase is on. */
    private suspend fun providers(userId: Uuid): List<PaymentProvider> {
        val stores = billing.catalogue(userId).providers.filter { it == PaymentProvider.APP_STORE || it == PaymentProvider.GOOGLE_PLAY }
        return billing.consultationProviders(userId) + stores
    }

    companion object {
        /** Off until the store products exist; an operator turns it on in the panel. */
        const val SALE_FLAG = "pet_humo_sale"

        /** 1 000 so'm: below it a price is a typo, not a decision. */
        const val MIN_PRICE_MINOR = 100_000L
    }
}

/** A legendary pet's price row, as the panel edits it. */
@kotlinx.serialization.Serializable
data class AdminPetProduct(
    val pet: PetKind,
    val priceMinor: Long,
    val currency: String,
    val appStoreProductId: String?,
    val googlePlayProductId: String?,
    val active: Boolean,
)

@kotlinx.serialization.Serializable
data class UpdatePetProductRequest(val priceMinor: Long, val active: Boolean = true)
