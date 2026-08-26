package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * Feature keys the entitlement service knows about. Admins change limits per key without
 * an app release, so the client must treat an unknown key as "off" rather than crash.
 */
object FeatureKeys {
    const val AI_CHAT = "ai_chat"
    const val AI_DAILY_SUMMARY = "ai_daily_summary"
    const val AI_INSIGHTS = "ai_insights"
    const val AI_MEAL_PLAN = "ai_meal_plan"
    const val INSIGHTS_HISTORY = "insights_history"
    const val CYCLE_PREDICTION = "cycle_prediction"
    const val NUTRITION_LOG = "nutrition_log"
    const val MIND_JOURNAL = "mind_journal"
    const val MEDS_REMINDERS = "meds_reminders"
    const val LEARN_PREMIUM = "learn_premium"
    const val WEARABLE_SYNC = "wearable_sync"
    const val DATA_EXPORT = "data_export"
}

/**
 * One resolved feature for one user, limits and usage included.
 *
 * [dailyLimit] / [monthlyLimit] are null when the feature is unmetered. [usedToday] and
 * [usedThisMonth] are counted in the user's own timezone, so the reset lands at their
 * midnight rather than the server's.
 */
@Serializable
data class FeatureEntitlement(
    val key: String,
    val enabled: Boolean,
    val dailyLimit: Int? = null,
    val monthlyLimit: Int? = null,
    val usedToday: Int = 0,
    val usedThisMonth: Int = 0,
) {
    val remainingToday: Int?
        get() = dailyLimit?.let { (it - usedToday).coerceAtLeast(0) }

    val remainingThisMonth: Int?
        get() = monthlyLimit?.let { (it - usedThisMonth).coerceAtLeast(0) }

    /** False when the feature is off, or on but out of budget for now. */
    val available: Boolean
        get() = enabled && remainingToday != 0 && remainingThisMonth != 0
}

@Serializable
data class Entitlements(
    val tier: SubscriptionTier,
    val features: List<FeatureEntitlement>,
    val source: SubscriptionSource? = null,
    val expiresAt: Instant? = null,
    val inGracePeriod: Boolean = false,
    val evaluatedAt: Instant,
) {
    fun feature(key: String): FeatureEntitlement? = features.firstOrNull { it.key == key }

    /** Unknown keys read as unavailable — never as an open door. */
    fun isAvailable(key: String): Boolean = feature(key)?.available == true
}

@Serializable
data class SubscriptionStatus(
    val tier: SubscriptionTier,
    val source: SubscriptionSource? = null,
    val productId: String? = null,
    val startedAt: Instant? = null,
    val expiresAt: Instant? = null,
    val autoRenewing: Boolean = false,
    val inGracePeriod: Boolean = false,
)
