package uz.sadora.server.admin

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.AuditLog
import uz.sadora.server.db.FeatureUsageDaily
import uz.sadora.server.db.Subscriptions
import uz.sadora.server.db.Users
import uz.sadora.server.db.dbQuery

/**
 * The dashboard's numbers.
 *
 * Deliberately account-level only. The proposal's dashboard also promises DAU/MAU and an
 * AI spend curve; those need an events table and the AI Gateway's cost log, neither of
 * which exists before sprint 3. Reporting a placeholder as if it were measured would be
 * worse than leaving it out, so the fields below are the ones the database can actually
 * answer today.
 */
@Serializable
data class AdminStats(
    val totalUsers: Long,
    val newToday: Long,
    val newThisWeek: Long,
    val activeToday: Long,
    val premiumUsers: Long,
    val blockedUsers: Long,
    val deletionPending: Long,
    val expiringWithinWeek: Long,
    val byLifeStage: Map<String, Long>,
    val byLanguage: Map<String, Long>,
    /** Per AI feature, how many calls were spent across all users today. */
    val aiUsageToday: Map<String, Long>,
    val generatedAt: Instant,
)

@Serializable
data class AdminActivityPoint(val date: String, val signUps: Long)

class AdminStatsRepository {

    suspend fun stats(): AdminStats = dbQuery {
        val currentTime = now()
        val dayAgo = (currentTime - 1.days).toOffsetDateTime()
        val weekAgo = (currentTime - 7.days).toOffsetDateTime()
        val weekAhead = (currentTime + 7.days).toOffsetDateTime()

        val userCount = Users.id.count()

        fun countWhere(predicate: () -> org.jetbrains.exposed.v1.core.Op<Boolean>): Long =
            Users.select(userCount).where(predicate()).firstOrNull()?.get(userCount) ?: 0L

        fun groupCount(column: org.jetbrains.exposed.v1.core.Column<String>): Map<String, Long> =
            Users.select(column, userCount)
                .groupBy(column)
                .associate { it[column] to it[userCount] }

        AdminStats(
            totalUsers = Users.selectAll().count(),
            newToday = countWhere { Users.createdAt greaterEq dayAgo },
            newThisWeek = countWhere { Users.createdAt greaterEq weekAgo },
            activeToday = countWhere { Users.lastActiveAt greaterEq dayAgo },
            premiumUsers = Subscriptions
                .select(Subscriptions.userId)
                .where { Subscriptions.status eq "active" }
                .withDistinct()
                .count(),
            blockedUsers = countWhere { Users.status eq "blocked" },
            deletionPending = countWhere { Users.status eq "deletion_pending" },
            expiringWithinWeek = Subscriptions.selectAll()
                .where {
                    (Subscriptions.status eq "active") and
                        Subscriptions.expiresAt.isNotNull() and
                        (Subscriptions.expiresAt less weekAhead)
                }
                .count(),
            byLifeStage = groupCount(Users.lifeStage),
            byLanguage = groupCount(Users.language),
            aiUsageToday = aiUsageToday(),
            generatedAt = currentTime,
        )
    }

    /** Sign-ups per day for the last [days] days, oldest first — the growth chart. */
    suspend fun signUpsPerDay(days: Int = 14): List<AdminActivityPoint> = dbQuery {
        val since = (now() - days.days).toOffsetDateTime()
        Users.select(Users.createdAt)
            .where { Users.createdAt greaterEq since }
            .map { it[Users.createdAt].toKotlinInstant().toString().take(10) }
            .groupingBy { it }
            .eachCount()
            .map { (date, count) -> AdminActivityPoint(date, count.toLong()) }
            .sortedBy { it.date }
    }

    /** The most recent audit entries, for the dashboard's attention feed. */
    suspend fun recentEvents(limit: Int = 12): List<AuditEntryView> = dbQuery {
        AuditLog.selectAll()
            .orderBy(AuditLog.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { row ->
                AuditEntryView(
                    id = row[AuditLog.id].toString(),
                    actorType = row[AuditLog.actorType],
                    actorId = row[AuditLog.actorId]?.toString(),
                    actorLabel = row[AuditLog.actorLabel],
                    action = row[AuditLog.action],
                    entityType = row[AuditLog.entityType],
                    entityId = row[AuditLog.entityId],
                    reason = row[AuditLog.reason],
                    metadata = row[AuditLog.metadata],
                    ip = row[AuditLog.ip],
                    createdAt = row[AuditLog.createdAt].toKotlinInstant(),
                )
            }
    }

    private fun aiUsageToday(): Map<String, Long> {
        val usedSum = FeatureUsageDaily.used.sum()
        // Counted in UTC rather than per user's timezone: this is an operational total,
        // not a limit, and a stable server-day is what makes the numbers comparable.
        val today = now().toString().take(10).let { kotlinx.datetime.LocalDate.parse(it) }
        return FeatureUsageDaily
            .select(FeatureUsageDaily.featureKey, usedSum)
            .where { FeatureUsageDaily.usageDate eq today }
            .groupBy(FeatureUsageDaily.featureKey)
            .filter { it[FeatureUsageDaily.featureKey].startsWith("ai_") }
            .associate { it[FeatureUsageDaily.featureKey] to (it[usedSum] ?: 0).toLong() }
    }
}
