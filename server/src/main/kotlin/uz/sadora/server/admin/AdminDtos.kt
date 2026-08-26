package uz.sadora.server.admin

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import uz.sadora.contract.AccountStatus
import uz.sadora.contract.Language
import uz.sadora.contract.LifeStage
import uz.sadora.contract.SubscriptionSource
import uz.sadora.contract.SubscriptionTier
import uz.sadora.server.plugins.AdminRole

@Serializable
data class AdminSignInRequest(
    val email: String,
    val password: String,
    /** Required whenever the account has 2FA enabled, which every real account does. */
    val totpCode: String? = null,
)

@Serializable
data class AdminSession(
    val accessToken: String,
    val expiresAt: Instant,
    val name: String,
    val email: String,
    val role: AdminRole,
)

/**
 * The user row the admin panel's list page shows.
 *
 * Note what is not here and never will be: cycle, symptoms, mood, medications, AI
 * transcripts. The list page cannot leak health data because the type it renders has
 * nowhere to put it.
 */
@Serializable
data class AdminUserSummary(
    val id: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val language: Language,
    val lifeStage: LifeStage,
    val tier: SubscriptionTier,
    val status: AccountStatus,
    val registeredAt: Instant,
    val lastActiveAt: Instant? = null,
)

@Serializable
data class AdminUserCard(
    val general: AdminUserSummary,
    val subscription: AdminSubscriptionView,
    val technical: AdminTechnicalView,
)

@Serializable
data class AdminSubscriptionView(
    val tier: SubscriptionTier,
    val source: SubscriptionSource? = null,
    val expiresAt: Instant? = null,
    val inGracePeriod: Boolean = false,
    val history: List<AdminSubscriptionHistoryItem> = emptyList(),
)

@Serializable
data class AdminSubscriptionHistoryItem(
    val source: SubscriptionSource,
    val productId: String? = null,
    val startedAt: Instant,
    val expiresAt: Instant? = null,
)

/** Sync and usage counters only — the "Texnik" tab of the user card. */
@Serializable
data class AdminTechnicalView(
    val devices: List<AdminDeviceView> = emptyList(),
    val featureUsage: List<AdminUsageView> = emptyList(),
    val timezone: String,
)

@Serializable
data class AdminDeviceView(
    val deviceId: String,
    val platform: String,
    val model: String? = null,
    val appVersion: String? = null,
    val lastSeenAt: Instant,
)

@Serializable
data class AdminUsageView(val featureKey: String, val usedToday: Int, val usedThisMonth: Int)

@Serializable
data class BlockUserRequest(val blocked: Boolean, val reason: String)

@Serializable
data class GrantPremiumRequest(
    val expiresAt: Instant? = null,
    /** Mandatory: a manual grant with no stated reason is indistinguishable from fraud. */
    val reason: String,
)

@Serializable
data class UpdateFeatureDefinitionRequest(
    val freeEnabled: Boolean,
    val premiumEnabled: Boolean,
    val freeDailyLimit: Int? = null,
    val freeMonthlyLimit: Int? = null,
    val premiumDailyLimit: Int? = null,
    val premiumMonthlyLimit: Int? = null,
)

@Serializable
data class FeatureDefinitionView(
    val key: String,
    val description: String,
    val freeEnabled: Boolean,
    val premiumEnabled: Boolean,
    val freeDailyLimit: Int? = null,
    val freeMonthlyLimit: Int? = null,
    val premiumDailyLimit: Int? = null,
    val premiumMonthlyLimit: Int? = null,
)

@Serializable
data class SetOverrideRequest(
    val enabled: Boolean? = null,
    val dailyLimit: Int? = null,
    val monthlyLimit: Int? = null,
    val reason: String,
    val expiresAt: Instant? = null,
)

@Serializable
data class FlagView(
    val key: String,
    val description: String,
    val enabled: Boolean,
    val defaultValue: Boolean,
    val rules: List<FlagRuleView> = emptyList(),
)

@Serializable
data class FlagRuleView(
    val id: String,
    val environment: String? = null,
    val country: String? = null,
    val language: String? = null,
    val lifeStage: String? = null,
    val platform: String? = null,
    val cohort: String? = null,
    val rolloutPercentage: Int,
    val value: Boolean,
    val priority: Int,
)

@Serializable
data class UpdateFlagRequest(val enabled: Boolean, val defaultValue: Boolean)

@Serializable
data class CreateFlagRuleRequest(
    val environment: String? = null,
    val country: String? = null,
    val language: String? = null,
    val lifeStage: String? = null,
    val platform: String? = null,
    val cohort: String? = null,
    val rolloutPercentage: Int = 100,
    val value: Boolean = true,
    val priority: Int = 100,
)

@Serializable
data class AuditEntryView(
    val id: String,
    val actorType: String,
    val actorId: String? = null,
    val actorLabel: String? = null,
    val action: String,
    val entityType: String? = null,
    val entityId: String? = null,
    val reason: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val ip: String? = null,
    val createdAt: Instant,
)
