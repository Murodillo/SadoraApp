package uz.sadora.server.entitlement

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import uz.sadora.contract.SubscriptionSource
import uz.sadora.contract.SubscriptionTier
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.FeatureDefinitions
import uz.sadora.server.db.FeatureUsageDaily
import uz.sadora.server.db.Subscriptions
import uz.sadora.server.db.UserEntitlementOverrides
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

/** One row of `feature_definitions` — the Free/Premium split for a single feature. */
data class FeatureDefinition(
    val key: String,
    val description: String,
    val freeEnabled: Boolean,
    val premiumEnabled: Boolean,
    val freeDailyLimit: Int?,
    val freeMonthlyLimit: Int?,
    val premiumDailyLimit: Int?,
    val premiumMonthlyLimit: Int?,
) {
    fun enabledFor(tier: SubscriptionTier): Boolean =
        if (tier == SubscriptionTier.PREMIUM) premiumEnabled else freeEnabled

    fun dailyLimitFor(tier: SubscriptionTier): Int? =
        if (tier == SubscriptionTier.PREMIUM) premiumDailyLimit else freeDailyLimit

    fun monthlyLimitFor(tier: SubscriptionTier): Int? =
        if (tier == SubscriptionTier.PREMIUM) premiumMonthlyLimit else freeMonthlyLimit
}

/** A per-user exception. Null fields inherit from the definition. */
data class EntitlementOverride(
    val featureKey: String,
    val enabled: Boolean?,
    val dailyLimit: Int?,
    val monthlyLimit: Int?,
    val reason: String,
    val expiresAt: Instant?,
)

data class SubscriptionRecord(
    val id: Uuid,
    val tier: SubscriptionTier,
    val source: SubscriptionSource,
    val productId: String?,
    val startedAt: Instant,
    val expiresAt: Instant?,
    val autoRenewing: Boolean,
    val inGracePeriod: Boolean,
)

data class UsageCounters(val today: Int, val thisMonth: Int)

class EntitlementRepository {

    // Ordered by key: without it Postgres returns heap order, so editing one row moves
    // it to the bottom of the admin panel's table and the operator loses their place.
    suspend fun definitions(): List<FeatureDefinition> = dbQuery {
        FeatureDefinitions.selectAll().orderBy(FeatureDefinitions.key to SortOrder.ASC).map { row ->
            FeatureDefinition(
                key = row[FeatureDefinitions.key],
                description = row[FeatureDefinitions.description],
                freeEnabled = row[FeatureDefinitions.freeEnabled],
                premiumEnabled = row[FeatureDefinitions.premiumEnabled],
                freeDailyLimit = row[FeatureDefinitions.freeDailyLimit],
                freeMonthlyLimit = row[FeatureDefinitions.freeMonthlyLimit],
                premiumDailyLimit = row[FeatureDefinitions.premiumDailyLimit],
                premiumMonthlyLimit = row[FeatureDefinitions.premiumMonthlyLimit],
            )
        }
    }

    suspend fun updateDefinition(
        key: String,
        freeEnabled: Boolean,
        premiumEnabled: Boolean,
        freeDailyLimit: Int?,
        freeMonthlyLimit: Int?,
        premiumDailyLimit: Int?,
        premiumMonthlyLimit: Int?,
        updatedBy: Uuid?,
    ): Boolean = dbQuery {
        FeatureDefinitions.update({ FeatureDefinitions.key eq key }) {
            it[FeatureDefinitions.freeEnabled] = freeEnabled
            it[FeatureDefinitions.premiumEnabled] = premiumEnabled
            it[FeatureDefinitions.freeDailyLimit] = freeDailyLimit
            it[FeatureDefinitions.freeMonthlyLimit] = freeMonthlyLimit
            it[FeatureDefinitions.premiumDailyLimit] = premiumDailyLimit
            it[FeatureDefinitions.premiumMonthlyLimit] = premiumMonthlyLimit
            it[updatedAt] = now().toOffsetDateTime()
            it[FeatureDefinitions.updatedBy] = updatedBy
        } > 0
    }

    /** Expired overrides are filtered out in SQL, so a lapsed grant needs no cleanup job. */
    suspend fun overridesOf(userId: Uuid): List<EntitlementOverride> = dbQuery {
        val currentTime = now().toOffsetDateTime()
        UserEntitlementOverrides.selectAll()
            .where {
                (UserEntitlementOverrides.userId eq userId) and
                    (
                        UserEntitlementOverrides.expiresAt.isNull() or
                            (UserEntitlementOverrides.expiresAt greater currentTime)
                        )
            }
            .map { row ->
                EntitlementOverride(
                    featureKey = row[UserEntitlementOverrides.featureKey],
                    enabled = row[UserEntitlementOverrides.enabled],
                    dailyLimit = row[UserEntitlementOverrides.dailyLimit],
                    monthlyLimit = row[UserEntitlementOverrides.monthlyLimit],
                    reason = row[UserEntitlementOverrides.reason],
                    expiresAt = row[UserEntitlementOverrides.expiresAt]?.toKotlinInstant(),
                )
            }
    }

    suspend fun setOverride(
        userId: Uuid,
        featureKey: String,
        enabled: Boolean?,
        dailyLimit: Int?,
        monthlyLimit: Int?,
        reason: String,
        expiresAt: Instant?,
        createdBy: Uuid?,
    ): Unit = dbQuery {
        UserEntitlementOverrides.upsert(
            UserEntitlementOverrides.userId,
            UserEntitlementOverrides.featureKey,
        ) {
            it[id] = Uuid.random()
            it[UserEntitlementOverrides.userId] = userId
            it[UserEntitlementOverrides.featureKey] = featureKey
            it[UserEntitlementOverrides.enabled] = enabled
            it[UserEntitlementOverrides.dailyLimit] = dailyLimit
            it[UserEntitlementOverrides.monthlyLimit] = monthlyLimit
            it[UserEntitlementOverrides.reason] = reason
            it[UserEntitlementOverrides.expiresAt] = expiresAt?.toOffsetDateTime()
            it[UserEntitlementOverrides.createdBy] = createdBy
            it[createdAt] = now().toOffsetDateTime()
        }
    }

    suspend fun clearOverride(userId: Uuid, featureKey: String): Boolean = dbQuery {
        UserEntitlementOverrides.deleteWhere {
            (UserEntitlementOverrides.userId eq userId) and
                (UserEntitlementOverrides.featureKey eq featureKey)
        } > 0
    }

    /**
     * The subscription that decides the tier right now. Grace-period rows still count as
     * Premium — a failed renewal should not lock a paying user out mid-cycle.
     */
    suspend fun activeSubscription(userId: Uuid): SubscriptionRecord? = dbQuery {
        val currentTime = now().toOffsetDateTime()
        Subscriptions.selectAll()
            .where {
                (Subscriptions.userId eq userId) and
                    (Subscriptions.status eq "active") and
                    (
                        Subscriptions.expiresAt.isNull() or
                            (Subscriptions.expiresAt greater currentTime) or
                            (Subscriptions.inGracePeriod eq true)
                        )
            }
            .maxByOrNull { it[Subscriptions.startedAt] }
            ?.let { row ->
                SubscriptionRecord(
                    id = row[Subscriptions.id],
                    tier = enumFromDb(row[Subscriptions.tier], SubscriptionTier.PREMIUM),
                    source = enumFromDb(row[Subscriptions.paymentSource], SubscriptionSource.MANUAL),
                    productId = row[Subscriptions.productId],
                    startedAt = row[Subscriptions.startedAt].toKotlinInstant(),
                    expiresAt = row[Subscriptions.expiresAt]?.toKotlinInstant(),
                    autoRenewing = row[Subscriptions.autoRenewing],
                    inGracePeriod = row[Subscriptions.inGracePeriod],
                )
            }
    }

    // ---------------------------------------------------------------- usage

    /**
     * Usage for one day and for the calendar month that day belongs to, both counted
     * from the same daily rows so they can never disagree.
     */
    suspend fun usage(userId: Uuid, day: LocalDate): Map<String, UsageCounters> = dbQuery {
        val monthStart = LocalDate(day.year, day.month, 1)
        val usedSum = FeatureUsageDaily.used.sum()
        val monthly = FeatureUsageDaily
            .select(FeatureUsageDaily.featureKey, usedSum)
            .where {
                (FeatureUsageDaily.userId eq userId) and
                    (FeatureUsageDaily.usageDate greaterEq monthStart) and
                    (FeatureUsageDaily.usageDate lessEq day)
            }
            .groupBy(FeatureUsageDaily.featureKey)
            .associate { it[FeatureUsageDaily.featureKey] to (it[usedSum] ?: 0) }

        val daily = FeatureUsageDaily
            .selectAll()
            .where {
                (FeatureUsageDaily.userId eq userId) and (FeatureUsageDaily.usageDate eq day)
            }
            .associate { it[FeatureUsageDaily.featureKey] to it[FeatureUsageDaily.used] }

        (monthly.keys + daily.keys).associateWith { key ->
            UsageCounters(today = daily[key] ?: 0, thisMonth = monthly[key] ?: 0)
        }
    }

    /**
     * Records one use and returns the new daily count.
     *
     * The increment is done by the database (`used = used + 1`) rather than read-then-write,
     * so two concurrent AI requests cannot both see the same count and overshoot the limit.
     */
    suspend fun recordUse(
        userId: Uuid,
        featureKey: String,
        day: LocalDate,
        costMicros: Long = 0,
    ): Int = dbQuery {
        FeatureUsageDaily.upsert(
            FeatureUsageDaily.userId,
            FeatureUsageDaily.featureKey,
            FeatureUsageDaily.usageDate,
            onUpdate = {
                it[FeatureUsageDaily.used] = FeatureUsageDaily.used + 1
                it[FeatureUsageDaily.costMicros] = FeatureUsageDaily.costMicros + costMicros
            },
        ) {
            it[FeatureUsageDaily.userId] = userId
            it[FeatureUsageDaily.featureKey] = featureKey
            it[usageDate] = day
            it[used] = 1
            it[FeatureUsageDaily.costMicros] = costMicros
        }
        FeatureUsageDaily.selectAll()
            .where {
                (FeatureUsageDaily.userId eq userId) and
                    (FeatureUsageDaily.featureKey eq featureKey) and
                    (FeatureUsageDaily.usageDate eq day)
            }
            .single()[FeatureUsageDaily.used]
    }
}
