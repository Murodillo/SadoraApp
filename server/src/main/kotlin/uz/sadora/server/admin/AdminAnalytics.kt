package uz.sadora.server.admin

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import uz.sadora.contract.CoinReasons
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.core.DEFAULT_TIMEZONE
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.AiUsageLog
import uz.sadora.server.db.AuditLog
import uz.sadora.server.db.CoinLedger
import uz.sadora.server.db.CommunityPosts
import uz.sadora.server.db.DailyLogs
import uz.sadora.server.db.Devices
import uz.sadora.server.db.JournalEntries
import uz.sadora.server.db.Meals
import uz.sadora.server.db.PaymentTransactions
import uz.sadora.server.db.Subscriptions
import uz.sadora.server.db.UserConsents
import uz.sadora.server.db.UserStreaks
import uz.sadora.server.db.Users
import uz.sadora.server.db.dbQuery

/**
 * One calendar day of the analytics page, in the operator's own timezone.
 *
 * Every number here is a count of rows the product already writes for its own reasons —
 * nothing was added to the app to feed this page, and nothing here is a health value.
 */
@Serializable
data class AnalyticsDay(
    val date: String,
    /** Accounts created that day. */
    val signUps: Long,
    /**
     * Distinct accounts that opened the app that day.
     *
     * "Opened" is the daily check-in reward the streak system writes once per account per
     * local day, unioned with the sign-in audit rows for accounts that came in fresh. It
     * is the closest thing the database has to a daily active user, and it is measured,
     * not inferred from the last request the way the dashboard's DAU is.
     */
    val activeUsers: Long,
    /** Sign-ins recorded in the audit log — new sessions, not app opens. */
    val signIns: Long,
    /** Subscriptions that started that day, comped or paid. */
    val premiumStarted: Long,
    /** Paid transactions, in the currency's minor unit. */
    val revenueMinor: Long,
    val aiCalls: Long,
    val posts: Long,
    /** Journal, meal and daily-log rows written that day — engagement, never content. */
    val entries: Long,
)

@Serializable
data class AnalyticsTotals(
    val signUps: Long,
    val activeUsers: Long,
    val revenueMinor: Long,
    val aiCalls: Long,
    val premiumStarted: Long,
    val posts: Long,
    val entries: Long,
)

/**
 * Of the accounts old enough to have had the chance, how many came back.
 *
 * `cohort` is every account created between `days + horizon` and `horizon` days ago, so
 * each of them has lived through the whole horizon; `returned` is those whose last
 * request came at least `horizon` days after they signed up.
 */
@Serializable
data class RetentionCohort(val horizonDays: Int, val cohort: Long, val returned: Long)

@Serializable
data class NamedCount(val key: String, val count: Long)

@Serializable
data class AdminAnalytics(
    val days: Int,
    val timezone: String,
    val perDay: List<AnalyticsDay>,
    val current: AnalyticsTotals,
    /** The same window immediately before this one, so a change can be shown as a change. */
    val previous: AnalyticsTotals,
    val retention: List<RetentionCohort>,
    /** registered → onboarded → active in 30 days → ever premium → premium now → paying. */
    val funnel: List<NamedCount>,
    /** Distinct accounts per device platform. An account with two phones counts on both. */
    val platforms: List<NamedCount>,
    /** Distinct accounts per app version, most common first. */
    val appVersions: List<NamedCount>,
    /** Live streaks by length, and the accounts whose streak has lapsed. */
    val streaks: List<NamedCount>,
    /** How many accounts said yes to each consent. */
    val consents: List<NamedCount>,
    val generatedAt: Instant,
)

class AdminAnalyticsRepository {

    suspend fun analytics(days: Int): AdminAnalytics = dbQuery {
        val currentTime = now()
        val today = currentTime.dayIn(DEFAULT_TIMEZONE)
        // Two windows are read in one pass so the previous period is grouped the same way.
        val span = days * 2
        val firstDay = today.minus(span - 1, DateTimeUnit.DAY)
        val since = (currentTime - span.days).toOffsetDateTime()
        val dayOf = { instant: java.time.OffsetDateTime -> instant.toKotlinInstant().dayIn(DEFAULT_TIMEZONE) }

        val signUps = Users.select(Users.createdAt)
            .where { Users.createdAt greaterEq since }
            .groupingBy { dayOf(it[Users.createdAt]) }
            .eachCount()

        val openers = mutableMapOf<LocalDate, MutableSet<Uuid>>()
        CoinLedger.select(CoinLedger.userId, CoinLedger.earnedOn)
            .where { (CoinLedger.reason eq CoinReasons.DAILY_OPEN) and CoinLedger.earnedOn.isNotNull() and (CoinLedger.createdAt greaterEq since) }
            .forEach { row ->
                val day = row[CoinLedger.earnedOn] ?: return@forEach
                openers.getOrPut(day) { mutableSetOf() }.add(row[CoinLedger.userId])
            }
        val signIns = mutableMapOf<LocalDate, Long>()
        AuditLog.select(AuditLog.actorId, AuditLog.createdAt)
            .where { (AuditLog.action eq AuditActions.USER_SIGNED_IN) and (AuditLog.createdAt greaterEq since) }
            .forEach { row ->
                val day = dayOf(row[AuditLog.createdAt])
                signIns[day] = (signIns[day] ?: 0) + 1
                row[AuditLog.actorId]?.let { openers.getOrPut(day) { mutableSetOf() }.add(it) }
            }

        val premiumStarted = Subscriptions.select(Subscriptions.startedAt)
            .where { Subscriptions.startedAt greaterEq since }
            .groupingBy { dayOf(it[Subscriptions.startedAt]) }
            .eachCount()

        val revenue = mutableMapOf<LocalDate, Long>()
        PaymentTransactions.select(PaymentTransactions.paidAt, PaymentTransactions.amountMinor)
            .where { (PaymentTransactions.state eq "paid") and PaymentTransactions.paidAt.isNotNull() and (PaymentTransactions.paidAt greaterEq since) }
            .forEach { row ->
                val day = dayOf(row[PaymentTransactions.paidAt]!!)
                revenue[day] = (revenue[day] ?: 0) + row[PaymentTransactions.amountMinor]
            }

        val aiCalls = AiUsageLog.select(AiUsageLog.createdAt)
            .where { AiUsageLog.createdAt greaterEq since }
            .groupingBy { dayOf(it[AiUsageLog.createdAt]) }
            .eachCount()

        val posts = CommunityPosts.select(CommunityPosts.createdAt)
            .where { CommunityPosts.createdAt greaterEq since }
            .groupingBy { dayOf(it[CommunityPosts.createdAt]) }
            .eachCount()

        val entries = mutableMapOf<LocalDate, Long>()
        DailyLogs.select(DailyLogs.logDate).where { DailyLogs.logDate greaterEq firstDay }
            .forEach { entries.merge(it[DailyLogs.logDate], 1L, Long::plus) }
        Meals.select(Meals.logDate).where { Meals.logDate greaterEq firstDay }
            .forEach { entries.merge(it[Meals.logDate], 1L, Long::plus) }
        JournalEntries.select(JournalEntries.entryDate).where { JournalEntries.entryDate greaterEq firstDay }
            .forEach { entries.merge(it[JournalEntries.entryDate], 1L, Long::plus) }

        val series = (0 until span).map { offset ->
            val day = firstDay.plus(offset, DateTimeUnit.DAY)
            AnalyticsDay(
                date = day.toString(),
                signUps = (signUps[day] ?: 0).toLong(),
                activeUsers = (openers[day]?.size ?: 0).toLong(),
                signIns = signIns[day] ?: 0,
                premiumStarted = (premiumStarted[day] ?: 0).toLong(),
                revenueMinor = revenue[day] ?: 0,
                aiCalls = (aiCalls[day] ?: 0).toLong(),
                posts = (posts[day] ?: 0).toLong(),
                entries = entries[day] ?: 0,
            )
        }
        val previous = series.take(days)
        val current = series.drop(days)

        AdminAnalytics(
            days = days,
            timezone = DEFAULT_TIMEZONE,
            perDay = current,
            current = current.totals(),
            previous = previous.totals(),
            retention = listOf(1, 7, 30).map { horizon -> retention(currentTime, days, horizon) },
            funnel = funnel(currentTime),
            platforms = distinctUsersBy(Devices.platform),
            appVersions = distinctUsersBy(Devices.appVersion).take(8),
            streaks = streakBuckets(today),
            consents = consents(),
            generatedAt = currentTime,
        )
    }

    private fun List<AnalyticsDay>.totals() = AnalyticsTotals(
        signUps = sumOf { it.signUps },
        activeUsers = sumOf { it.activeUsers },
        revenueMinor = sumOf { it.revenueMinor },
        aiCalls = sumOf { it.aiCalls },
        premiumStarted = sumOf { it.premiumStarted },
        posts = sumOf { it.posts },
        entries = sumOf { it.entries },
    )

    private fun retention(currentTime: Instant, days: Int, horizon: Int): RetentionCohort {
        val newest = (currentTime - horizon.days).toOffsetDateTime()
        val oldest = (currentTime - (horizon + days).days).toOffsetDateTime()
        val rows = Users.select(Users.createdAt, Users.lastActiveAt)
            .where { (Users.createdAt greaterEq oldest) and (Users.createdAt less newest) }
            .map { it[Users.createdAt] to it[Users.lastActiveAt] }
        val returned = rows.count { (created, lastActive) ->
            lastActive != null && !lastActive.isBefore(created.plusDays(horizon.toLong()))
        }
        return RetentionCohort(horizonDays = horizon, cohort = rows.size.toLong(), returned = returned.toLong())
    }

    private fun funnel(currentTime: Instant): List<NamedCount> {
        val monthAgo = (currentTime - 30.days).toOffsetDateTime()
        val everPremium = Subscriptions.select(Subscriptions.userId).withDistinct().count()
        val premiumNow = Subscriptions.select(Subscriptions.userId)
            .where { Subscriptions.status eq "active" }
            .withDistinct()
            .count()
        val paying = Subscriptions.select(Subscriptions.userId)
            .where { (Subscriptions.status eq "active") and (Subscriptions.paymentSource neq "manual") }
            .withDistinct()
            .count()
        return listOf(
            NamedCount("registered", Users.selectAll().count()),
            NamedCount("onboarded", Users.selectAll().where { Users.onboardingCompleted eq true }.count()),
            NamedCount("active_30d", Users.selectAll().where { Users.lastActiveAt greaterEq monthAgo }.count()),
            NamedCount("ever_premium", everPremium),
            NamedCount("premium_now", premiumNow),
            NamedCount("paying", paying),
        )
    }

    private fun <T : String?> distinctUsersBy(column: org.jetbrains.exposed.v1.core.Column<T>): List<NamedCount> =
        Devices.select(Devices.userId, column)
            .withDistinct()
            .mapNotNull { row -> row[column]?.let { it to row[Devices.userId] } }
            .groupBy({ it.first }, { it.second })
            .map { (key, users) -> NamedCount(key, users.toSet().size.toLong()) }
            .sortedByDescending { it.count }

    /**
     * A streak is alive if the account opened today or yesterday — the same rule the
     * streak service applies before it decides whether a run continues or restarts.
     */
    private fun streakBuckets(today: LocalDate): List<NamedCount> {
        val yesterday = today.minus(1, DateTimeUnit.DAY)
        val buckets = linkedMapOf("1-2" to 0L, "3-6" to 0L, "7-13" to 0L, "14-29" to 0L, "30+" to 0L, "lapsed" to 0L)
        UserStreaks.select(UserStreaks.currentDays, UserStreaks.lastOpenOn).forEach { row ->
            val lastOpen = row[UserStreaks.lastOpenOn]
            val alive = lastOpen != null && lastOpen >= yesterday && row[UserStreaks.currentDays] > 0
            val key = when {
                !alive -> "lapsed"
                row[UserStreaks.currentDays] < 3 -> "1-2"
                row[UserStreaks.currentDays] < 7 -> "3-6"
                row[UserStreaks.currentDays] < 14 -> "7-13"
                row[UserStreaks.currentDays] < 30 -> "14-29"
                else -> "30+"
            }
            buckets[key] = (buckets[key] ?: 0) + 1
        }
        return buckets.map { (key, count) -> NamedCount(key, count) }
    }

    private fun consents(): List<NamedCount> {
        var storeHealth = 0L
        var aiInsights = 0L
        var analytics = 0L
        var marketing = 0L
        UserConsents.select(UserConsents.storeHealth, UserConsents.aiInsights, UserConsents.analytics, UserConsents.marketing)
            .forEach { row ->
                if (row[UserConsents.storeHealth]) storeHealth++
                if (row[UserConsents.aiInsights]) aiInsights++
                if (row[UserConsents.analytics]) analytics++
                if (row[UserConsents.marketing]) marketing++
            }
        return listOf(
            NamedCount("store_health", storeHealth),
            NamedCount("ai_insights", aiInsights),
            NamedCount("analytics", analytics),
            NamedCount("marketing", marketing),
        )
    }
}
