package uz.sadora.server.billing

import kotlin.uuid.Uuid
import uz.sadora.contract.BillingPlan
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentState
import uz.sadora.contract.StorePurchaseRequest
import uz.sadora.contract.SubscriptionStatus
import uz.sadora.server.core.ValidationException
import uz.sadora.server.entitlement.EntitlementService
import uz.sadora.server.entitlement.SubscriptionRepository

/**
 * App Store and Google Play purchases.
 *
 * The flow is inverted compared to Payme and Click: the money has already moved inside
 * the platform's own sheet, and what reaches us is a receipt. So the only question here
 * is whether the store agrees the receipt is real — asked of the store, never of the
 * client — and with no credentials configured the answer is a refusal rather than a
 * shrug. A verifier that approved by default would be indistinguishable from having no
 * paywall at all.
 */
class StorePurchaseService(
    private val repository: BillingRepository,
    private val subscriptions: SubscriptionRepository,
    private val entitlements: EntitlementService,
    private val verifier: StoreVerifier,
) {

    suspend fun verifyAndGrant(userId: Uuid, request: StorePurchaseRequest): SubscriptionStatus {
        if (request.provider != PaymentProvider.APP_STORE && request.provider != PaymentProvider.GOOGLE_PLAY) {
            throw ValidationException("provider", "Bu provayder store emas")
        }
        if (request.token.isBlank()) throw ValidationException("token", "Bo'sh bo'lishi mumkin emas")

        val plan = repository.plans().firstOrNull { it.matches(request.provider, request.productId) }
            ?: throw ValidationException("productId", "Bunday mahsulot yo'q")

        val verified = try {
            verifier.verify(request.provider, request.productId, request.token)
        } catch (rejected: ReceiptRejectedException) {
            throw ValidationException("token", rejected.message ?: "Chek tasdiqlanmadi")
        }

        // The store's transaction id is the idempotency key: the same receipt sent twice
        // — a reinstall, a restore, a retry — must not buy a second subscription.
        repository.byExternalId(request.provider, verified.transactionId)?.let { existing ->
            if (existing.state == PaymentState.PAID) return entitlements.subscriptionStatus(userId)
        }

        val transaction = repository.createTransaction(
            userId = userId,
            planId = plan.id,
            provider = request.provider,
            amountMinor = plan.priceMinor,
            currency = plan.currency,
        )
        repository.attachExternalId(transaction.id, verified.transactionId, null)

        val subscriptionId = subscriptions.grant(
            userId = userId,
            source = request.provider.asSubscriptionSource(),
            expiresAt = verified.expiresAt,
            productId = plan.id,
            externalId = verified.transactionId,
            reason = "store receipt",
        )
        repository.markPaid(transaction.id, subscriptionId)

        return entitlements.subscriptionStatus(userId)
    }
}

private fun BillingPlan.matches(provider: PaymentProvider, productId: String): Boolean = when (provider) {
    PaymentProvider.APP_STORE -> appStoreProductId == productId
    PaymentProvider.GOOGLE_PLAY -> googlePlayProductId == productId
    else -> false
}
