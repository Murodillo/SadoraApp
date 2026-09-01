package uz.sadora.server.db

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.time
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

/**
 * Exposed mappings over the Flyway-managed schema.
 *
 * Flyway owns the DDL — these objects only describe it for query building, and
 * `SchemaUtils.create` is deliberately never called. When the two drift, the migration
 * is the truth and this file is the bug.
 *
 * Every timestamp column is `timestamptz` and therefore maps to `OffsetDateTime`; use
 * the converters in `core/Time.kt` at the repository boundary rather than passing
 * `OffsetDateTime` upward.
 */
private val auditJson = Json { encodeDefaults = true }

object Users : Table("users") {
    val id = uuid("id")
    val phone = text("phone").nullable()
    val email = text("email").nullable()
    /**
     * Unused since users sign in by phone code. Left in place rather than dropped: a
     * migration that deletes a column deletes whatever it holds, and this one is not in
     * the way. Admin passwords live in [AdminUsers], which is a separate realm.
     */
    val passwordHash = text("password_hash").nullable()
    val name = text("name")
    val language = text("language")
    val timezone = text("timezone")
    val lifeStage = text("life_stage")
    val birthDate = date("birth_date").nullable()
    val heightCm = integer("height_cm").nullable()
    val weightKg = integer("weight_kg").nullable()
    val avatarUrl = text("avatar_url").nullable()
    val onboardingCompleted = bool("onboarding_completed")
    val status = text("status")
    val blockedReason = text("blocked_reason").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    val lastActiveAt = timestampWithTimeZone("last_active_at").nullable()
    val deletionRequestedAt = timestampWithTimeZone("deletion_requested_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object UserGoals : Table("user_goals") {
    val userId = uuid("user_id").references(Users.id)
    val goal = text("goal")

    override val primaryKey = PrimaryKey(userId, goal)
}

object AuthIdentities : Table("auth_identities") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val provider = text("provider")
    val subject = text("subject")
    val email = text("email").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object OtpChallenges : Table("otp_challenges") {
    val id = uuid("id")
    val phone = text("phone")
    val codeHash = text("code_hash")
    val purpose = text("purpose")
    val attempts = integer("attempts")
    val maxAttempts = integer("max_attempts")
    val expiresAt = timestampWithTimeZone("expires_at")
    val consumedAt = timestampWithTimeZone("consumed_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val requestIp = text("request_ip").nullable()

    override val primaryKey = PrimaryKey(id)
}

object RefreshTokens : Table("refresh_tokens") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val familyId = uuid("family_id")
    val tokenHash = text("token_hash")
    val deviceId = text("device_id").nullable()
    val issuedAt = timestampWithTimeZone("issued_at")
    val expiresAt = timestampWithTimeZone("expires_at")
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
    val revokedReason = text("revoked_reason").nullable()
    val replacedBy = uuid("replaced_by").nullable()

    override val primaryKey = PrimaryKey(id)
}

object Devices : Table("devices") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val deviceId = text("device_id")
    val platform = text("platform")
    val osVersion = text("os_version").nullable()
    val appVersion = text("app_version").nullable()
    val model = text("model").nullable()
    val pushToken = text("push_token").nullable()
    val timezone = text("timezone").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val lastSeenAt = timestampWithTimeZone("last_seen_at")

    override val primaryKey = PrimaryKey(id)
}

object UserConsents : Table("user_consents") {
    val userId = uuid("user_id").references(Users.id)
    val storeHealth = bool("store_health")
    val aiInsights = bool("ai_insights")
    val analytics = bool("analytics")
    val marketing = bool("marketing")
    val policyVersion = text("policy_version")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(userId)
}

object ConsentEvents : Table("consent_events") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val consentKey = text("consent_key")
    val granted = bool("granted")
    val policyVersion = text("policy_version")
    val consentSource = text("source")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object CycleBaselines : Table("cycle_baselines") {
    val userId = uuid("user_id").references(Users.id)
    val lastPeriodStart = date("last_period_start").nullable()
    val averageCycleLength = integer("average_cycle_length")
    val averagePeriodLength = integer("average_period_length")
    val isRegular = bool("is_regular")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(userId)
}

object StageBaselines : Table("stage_baselines") {
    val userId = uuid("user_id").references(Users.id)
    val dueDate = date("due_date").nullable()
    val childBirthDate = date("child_birth_date").nullable()
    val lastPeriodStart = date("last_period_start").nullable()
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(userId)
}

object Subscriptions : Table("subscriptions") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val tier = text("tier")
    val paymentSource = text("source")
    val productId = text("product_id").nullable()
    val externalId = text("external_id").nullable()
    val status = text("status")
    val startedAt = timestampWithTimeZone("started_at")
    val expiresAt = timestampWithTimeZone("expires_at").nullable()
    val autoRenewing = bool("auto_renewing")
    val inGracePeriod = bool("in_grace_period")
    val grantedBy = uuid("granted_by").nullable()
    val grantReason = text("grant_reason").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object FeatureDefinitions : Table("feature_definitions") {
    val key = text("key")
    val description = text("description")
    val freeEnabled = bool("free_enabled")
    val premiumEnabled = bool("premium_enabled")
    val freeDailyLimit = integer("free_daily_limit").nullable()
    val freeMonthlyLimit = integer("free_monthly_limit").nullable()
    val premiumDailyLimit = integer("premium_daily_limit").nullable()
    val premiumMonthlyLimit = integer("premium_monthly_limit").nullable()
    val updatedAt = timestampWithTimeZone("updated_at")
    val updatedBy = uuid("updated_by").nullable()

    override val primaryKey = PrimaryKey(key)
}

object UserEntitlementOverrides : Table("user_entitlement_overrides") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val featureKey = text("feature_key").references(FeatureDefinitions.key)
    val enabled = bool("enabled").nullable()
    val dailyLimit = integer("daily_limit").nullable()
    val monthlyLimit = integer("monthly_limit").nullable()
    val reason = text("reason")
    val expiresAt = timestampWithTimeZone("expires_at").nullable()
    val createdBy = uuid("created_by").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object FeatureUsageDaily : Table("feature_usage_daily") {
    val userId = uuid("user_id").references(Users.id)
    val featureKey = text("feature_key")
    val usageDate = date("usage_date")
    val used = integer("used")
    val costMicros = long("cost_micros")

    override val primaryKey = PrimaryKey(userId, featureKey, usageDate)
}

object FeatureFlagsTable : Table("feature_flags") {
    val key = text("key")
    val description = text("description")
    val enabled = bool("enabled")
    val defaultValue = bool("default_value")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    val updatedBy = uuid("updated_by").nullable()

    override val primaryKey = PrimaryKey(key)
}

object FeatureFlagRules : Table("feature_flag_rules") {
    val id = uuid("id")
    val flagKey = text("flag_key").references(FeatureFlagsTable.key)
    val environment = text("environment").nullable()
    val country = text("country").nullable()
    val language = text("language").nullable()
    val lifeStage = text("life_stage").nullable()
    val platform = text("platform").nullable()
    val cohort = text("cohort").nullable()
    val rolloutPercentage = integer("rollout_percentage")
    val value = bool("value")
    val priority = integer("priority")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object AdminUsers : Table("admin_users") {
    val id = uuid("id")
    val email = text("email")
    val passwordHash = text("password_hash")
    val name = text("name")
    val role = text("role")
    val totpSecret = text("totp_secret").nullable()
    val totpEnabled = bool("totp_enabled")
    val status = text("status")
    val failedAttempts = integer("failed_attempts")
    val lockedUntil = timestampWithTimeZone("locked_until").nullable()
    val lastLoginAt = timestampWithTimeZone("last_login_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object AuditLog : Table("audit_log") {
    val id = uuid("id")
    val actorType = text("actor_type")
    val actorId = uuid("actor_id").nullable()
    val actorLabel = text("actor_label").nullable()
    val action = text("action")
    val entityType = text("entity_type").nullable()
    val entityId = text("entity_id").nullable()
    val reason = text("reason").nullable()
    val metadata = jsonb<Map<String, String>>("metadata", auditJson)
    val ip = text("ip").nullable()
    val userAgent = text("user_agent").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

// ---------------------------------------------------------------- notifications
//
// Account-level, not health data: the outbox stores a rendered title and body, and the
// admin panel's notification page reads it. Nothing here carries a symptom or a mood —
// a medication reminder shows the name the user typed, which she chose to be reminded of.

object UserNotificationSettings : Table("user_notification_settings") {
    val userId = uuid("user_id").references(Users.id)
    val enabled = bool("enabled")
    /** `med_reminder:true,water:false`; an absent category means on. */
    val categories = text("categories")
    val quietFrom = time("quiet_from").nullable()
    val quietUntil = time("quiet_until").nullable()
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(userId)
}

object NotificationTemplates : Table("notification_templates") {
    val key = text("key")
    val language = text("language")
    val category = text("category")
    val title = text("title")
    val body = text("body")
    val active = bool("active")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(key, language)
}

object NotificationOutbox : Table("notification_outbox") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val category = text("category")
    val title = text("title")
    val body = text("body")
    val scheduledFor = timestampWithTimeZone("scheduled_for")
    val status = text("status")
    val sentAt = timestampWithTimeZone("sent_at").nullable()
    val suppressedReason = text("suppressed_reason").nullable()
    val dedupeKey = text("dedupe_key")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object NotificationCaps : Table("notification_caps") {
    val id = integer("id")
    val maxPerDay = integer("max_per_day")
    val maxPerWeek = integer("max_per_week")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
