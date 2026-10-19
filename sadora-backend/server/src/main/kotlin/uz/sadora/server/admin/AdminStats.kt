package uz.sadora.server.admin

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.countDistinct
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import uz.sadora.contract.ConsultationPayment
import uz.sadora.contract.DoctorStatus
import uz.sadora.server.core.DEFAULT_TIMEZONE
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.AuditLog
import uz.sadora.server.db.CommunityPosts
import uz.sadora.server.db.CommunityReports
import uz.sadora.server.db.ConsultationSessions
import uz.sadora.server.db.DoctorProfiles
import uz.sadora.server.db.FeatureUsageDaily
import uz.sadora.server.db.ShopProducts
import uz.sadora.server.db.Subscriptions
import uz.sadora.server.db.Users
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.plugins.AdminRole

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
    /**
     * Distinct accounts seen in the last 30 days.
     *
     * DAU and MAU as this product can honestly measure them: "seen" is the last request
     * the account made, which the auth layer already stamps. It is not a session count
     * and does not pretend to be one.
     */
    val activeThisMonth: Long,
    val premiumUsers: Long,
    val blockedUsers: Long,
    val deletionPending: Long,
    val expiringWithinWeek: Long,
    val byLifeStage: Map<String, Long>,
    val byLanguage: Map<String, Long>,
    /** Per AI feature, how many calls were spent across all users today. */
    val aiUsageToday: Map<String, Long>,
    /** Accounts that answered "yes" to "did a doctor recommend SADORA". */
    val referredByDoctor: Long = 0,
    val communityPostsToday: Long = 0,
    val communityOpenReports: Long = 0,
    val generatedAt: Instant,
)

@Serializable
data class AdminActivityPoint(val date: String, val signUps: Long)

/**
 * The dashboard's "Bugun" list: queues an operator works down, each one a count of rows
 * waiting on a person. A queue is null for a role whose page refuses it, so the panel
 * never offers a link the server would answer with 403.
 */
@Serializable
data class AdminTodo(
    /** Doctor applications waiting for a decision. Owner, Admin, Support. */
    val doctorsPending: Long? = null,
    /** Chat reports nobody has resolved yet. Every role. */
    val openReports: Long,
    /** Unanswered paid consultations whose money is still to be returned. Owner, Admin, Support. */
    val refundsDue: Long? = null,
    /** Gul shop products on sale with no units left. Owner, Admin, Analyst. */
    val shopSoldOut: Long? = null,
    val generatedAt: Instant,
)

/** One metered feature over a window: how many uses were counted and by how many accounts. */
@Serializable
data class AdminFeatureUsage(val key: String, val uses: Long, val users: Long)

class AdminStatsRepository {

    suspend fun stats(): AdminStats = dbQuery {
        val currentTime = now()
        val dayAgo = (currentTime - 1.days).toOffsetDateTime()
        val monthAgo = (currentTime - 30.days).toOffsetDateTime()
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
            activeThisMonth = countWhere { Users.lastActiveAt greaterEq monthAgo },
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
            referredByDoctor = countWhere { Users.referredByDoctor eq true },
            communityPostsToday = CommunityPosts.selectAll()
                .where { CommunityPosts.createdAt greaterEq dayAgo }
                .count(),
            communityOpenReports = CommunityReports.selectAll()
                .where { CommunityReports.resolvedAt.isNull() }
                .count(),
            generatedAt = currentTime,
        )
    }

    suspend fun todo(role: AdminRole): AdminTodo = dbQuery {
        val reviewsPeople = role in setOf(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT)
        val readsShop = role in setOf(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.ANALYST)
        AdminTodo(
            doctorsPending = if (reviewsPeople) {
                DoctorProfiles.selectAll()
                    .where { DoctorProfiles.status eq DoctorStatus.PENDING.dbValue() }
                    .count()
            } else {
                null
            },
            openReports = CommunityReports.selectAll()
                .where { CommunityReports.resolvedAt.isNull() }
                .count(),
            refundsDue = if (reviewsPeople) {
                ConsultationSessions.selectAll()
                    .where { ConsultationSessions.paymentState eq ConsultationPayment.REFUND_DUE.dbValue() }
                    .count()
            } else {
                null
            },
            // A NULL stock is unlimited; only a counted product can run out.
            shopSoldOut = if (readsShop) {
                ShopProducts.selectAll()
                    .where { (ShopProducts.active eq true) and (ShopProducts.stock eq 0) }
                    .count()
            } else {
                null
            },
            generatedAt = now(),
        )
    }

    /**
     * Uses of every metered feature over the last [days] Tashkent days, today included.
     * Only features that go through the entitlement meter write these rows, so a feature
     * missing here was not measured — it is not reported as zero.
     */
    suspend fun featureUsage(days: Int): List<AdminFeatureUsage> = dbQuery {
        val since = now().dayIn(DEFAULT_TIMEZONE).minus(days - 1, DateTimeUnit.DAY)
        val usedSum = FeatureUsageDaily.used.sum()
        val accounts = FeatureUsageDaily.userId.countDistinct()
        FeatureUsageDaily
            .select(FeatureUsageDaily.featureKey, usedSum, accounts)
            // A use handed back at the limit leaves its row at zero; that account did not use it.
            .where { (FeatureUsageDaily.usageDate greaterEq since) and (FeatureUsageDaily.used greater 0) }
            .groupBy(FeatureUsageDaily.featureKey)
            .map { AdminFeatureUsage(it[FeatureUsageDaily.featureKey], (it[usedSum] ?: 0).toLong(), it[accounts]) }
            .sortedBy { it.key }
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
        // Usage rows are dated in each user's own timezone, and the product's users are
        // on Tashkent time — so "today" here is the Tashkent day. Counting the UTC day
        // instead left the column empty for the five hours after Tashkent midnight.
        val today = now().dayIn(DEFAULT_TIMEZONE)
        return FeatureUsageDaily
            .select(FeatureUsageDaily.featureKey, usedSum)
            .where { FeatureUsageDaily.usageDate eq today }
            .groupBy(FeatureUsageDaily.featureKey)
            .filter { it[FeatureUsageDaily.featureKey].startsWith("ai_") }
            .associate { it[FeatureUsageDaily.featureKey] to (it[usedSum] ?: 0).toLong() }
    }
}
