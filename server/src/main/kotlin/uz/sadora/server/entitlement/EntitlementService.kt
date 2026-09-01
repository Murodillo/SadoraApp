package uz.sadora.server.entitlement

import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import uz.sadora.contract.Entitlements
import uz.sadora.contract.FeatureEntitlement
import uz.sadora.contract.SubscriptionStatus
import uz.sadora.contract.SubscriptionTier
import uz.sadora.server.core.EntitlementRequiredException
import uz.sadora.server.core.LimitReachedException
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now

/**
 * Resolves what a user may do right now.
 *
 * Three inputs stack, in this order: the feature definition for her tier, then any
 * per-user override, then her usage so far. The last one is why the result is not
 * cached wholesale — a cached "3 of 3 chats left" would let a user spend the same
 * allowance twice.
 */
class EntitlementService(
    private val repository: EntitlementRepository,
) {
    /** Definitions change only from the admin panel, so a short cache is safe here. */
    private var cachedDefinitions: List<FeatureDefinition> = emptyList()
    private var definitionsExpireAtMillis: Long = 0

    private suspend fun definitions(): List<FeatureDefinition> {
        val currentMillis = now().toEpochMilliseconds()
        if (currentMillis >= definitionsExpireAtMillis || cachedDefinitions.isEmpty()) {
            cachedDefinitions = repository.definitions()
            definitionsExpireAtMillis = currentMillis + DEFINITION_CACHE_TTL.inWholeMilliseconds
        }
        return cachedDefinitions
    }

    /** Called after an admin edits a definition so the next read sees it immediately. */
    fun invalidateDefinitions() {
        definitionsExpireAtMillis = 0
    }

    suspend fun resolve(userId: Uuid, timezone: String): Entitlements {
        val subscription = repository.activeSubscription(userId)
        val tier = subscription?.tier ?: SubscriptionTier.FREE
        val overrides = repository.overridesOf(userId).associateBy { it.featureKey }
        val usage = repository.usage(userId, now().dayIn(timezone))

        val features = definitions().map { definition ->
            val override = overrides[definition.key]
            val counters = usage[definition.key] ?: UsageCounters(0, 0)
            FeatureEntitlement(
                key = definition.key,
                enabled = override?.enabled ?: definition.enabledFor(tier),
                dailyLimit = override?.dailyLimit ?: definition.dailyLimitFor(tier),
                monthlyLimit = override?.monthlyLimit ?: definition.monthlyLimitFor(tier),
                usedToday = counters.today,
                usedThisMonth = counters.thisMonth,
            )
        }

        return Entitlements(
            tier = tier,
            features = features,
            source = subscription?.source,
            expiresAt = subscription?.expiresAt,
            inGracePeriod = subscription?.inGracePeriod ?: false,
            evaluatedAt = now(),
        )
    }

    suspend fun subscriptionStatus(userId: Uuid): SubscriptionStatus {
        val subscription = repository.activeSubscription(userId)
            ?: return SubscriptionStatus(tier = SubscriptionTier.FREE)
        return SubscriptionStatus(
            tier = subscription.tier,
            source = subscription.source,
            productId = subscription.productId,
            startedAt = subscription.startedAt,
            expiresAt = subscription.expiresAt,
            autoRenewing = subscription.autoRenewing,
            inGracePeriod = subscription.inGracePeriod,
        )
    }

    /**
     * Checks a feature without recording a use.
     *
     * For unmetered reads — a calendar has no per-call cost — where the only question is
     * whether an operator has switched the feature off.
     */
    suspend fun requireAvailable(userId: Uuid, featureKey: String, timezone: String) {
        val feature = resolve(userId, timezone).feature(featureKey)
            ?: throw EntitlementRequiredException(featureKey)
        if (!feature.enabled) throw EntitlementRequiredException(featureKey)
        if (feature.remainingToday == 0) throw LimitReachedException(featureKey, "day")
        if (feature.remainingThisMonth == 0) throw LimitReachedException(featureKey, "month")
    }

    /**
     * Checks a metered feature and records the use in one step.
     *
     * Kept as a single call on purpose: a separate `check` then `record` invites a caller
     * to do the work and forget to record it, which is how AI budgets quietly overrun.
     */
    suspend fun consume(
        userId: Uuid,
        featureKey: String,
        timezone: String,
        costMicros: Long = 0,
    ) {
        val entitlements = resolve(userId, timezone)
        val feature = entitlements.feature(featureKey)
            ?: throw EntitlementRequiredException(featureKey)
        if (!feature.enabled) throw EntitlementRequiredException(featureKey)
        if (feature.remainingToday == 0) throw LimitReachedException(featureKey, "day")
        if (feature.remainingThisMonth == 0) throw LimitReachedException(featureKey, "month")
        repository.recordUse(userId, featureKey, now().dayIn(timezone), costMicros)
    }

    private companion object {
        val DEFINITION_CACHE_TTL = 60.seconds
    }
}
