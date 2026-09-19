package uz.sadora.server.billing

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.sadora.contract.BillingPeriod
import uz.sadora.contract.BillingPlan
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentState
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.BillingPlans
import uz.sadora.server.db.PaymentCallbacks
import uz.sadora.server.db.PaymentTransactions
import uz.sadora.server.db.Subscriptions
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

data class TransactionRecord(
    val id: Uuid,
    val userId: Uuid,
    val planId: String,
    val provider: PaymentProvider,
    val amountMinor: Long,
    val currency: String,
    val state: PaymentState,
    val externalId: String?,
    val providerCreatedAt: Long?,
    val paidAt: Instant?,
    val cancelledAt: Instant?,
    val cancelReason: Int?,
    val subscriptionId: Uuid?,
    val createdAt: Instant,
)

class BillingRepository {

    // ---------------------------------------------------------------- plans

    suspend fun plans(activeOnly: Boolean = true): List<BillingPlan> = dbQuery {
        var query = BillingPlans.selectAll()
        if (activeOnly) query = query.andWhere { BillingPlans.active eq true }
        query
            .orderBy(BillingPlans.position to SortOrder.ASC)
            .map { it.toPlan() }
    }

    suspend fun plan(id: String): BillingPlan? = dbQuery {
        BillingPlans.selectAll().where { BillingPlans.id eq id }.limit(1).firstOrNull()?.toPlan()
    }

    // ---------------------------------------------------------------- transactions

    suspend fun createTransaction(
        userId: Uuid,
        planId: String,
        provider: PaymentProvider,
        amountMinor: Long,
        currency: String,
    ): TransactionRecord = dbQuery {
        val id = Uuid.random()
        val timestamp = now().toOffsetDateTime()
        PaymentTransactions.insert {
            it[PaymentTransactions.id] = id
            it[PaymentTransactions.userId] = userId
            it[PaymentTransactions.planId] = planId
            it[PaymentTransactions.provider] = provider.dbValue()
            it[PaymentTransactions.amountMinor] = amountMinor
            it[PaymentTransactions.currency] = currency
            it[state] = PaymentState.PENDING.dbValue()
            it[createdAt] = timestamp
            it[updatedAt] = timestamp
        }
        PaymentTransactions.selectAll().where { PaymentTransactions.id eq id }.first().toRecord()
    }

    suspend fun transaction(id: Uuid): TransactionRecord? = dbQuery {
        PaymentTransactions.selectAll()
            .where { PaymentTransactions.id eq id }
            .limit(1)
            .firstOrNull()
            ?.toRecord()
    }

    /** By the provider's own id — how every webhook after the first one finds its row. */
    suspend fun byExternalId(provider: PaymentProvider, externalId: String): TransactionRecord? = dbQuery {
        PaymentTransactions.selectAll()
            .where {
                (PaymentTransactions.provider eq provider.dbValue()) and
                    (PaymentTransactions.externalId eq externalId)
            }
            .limit(1)
            .firstOrNull()
            ?.toRecord()
    }

    suspend fun recent(limit: Int = 50, state: PaymentState? = null): List<TransactionRecord> = dbQuery {
        var query = PaymentTransactions.selectAll()
        state?.let { wanted -> query = query.andWhere { PaymentTransactions.state eq wanted.dbValue() } }
        query
            .orderBy(PaymentTransactions.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toRecord() }
    }

    suspend fun forUser(userId: Uuid, limit: Int = 20): List<TransactionRecord> = dbQuery {
        PaymentTransactions.selectAll()
            .where { PaymentTransactions.userId eq userId }
            .orderBy(PaymentTransactions.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toRecord() }
    }

    /**
     * Claims a pending transaction for a provider's id.
     *
     * Returns false when the row already carries a different id, which is how a second
     * provider transaction against the same order is refused rather than silently
     * overwriting the first.
     */
    suspend fun attachExternalId(
        id: Uuid,
        externalId: String,
        providerCreatedAt: Long?,
    ): Boolean = dbQuery {
        // One conditional UPDATE rather than read-then-write, so two providers' ids
        // racing for the same order cannot both see it free and both write.
        PaymentTransactions.update({
            (PaymentTransactions.id eq id) and
                (PaymentTransactions.externalId.isNull() or (PaymentTransactions.externalId eq externalId))
        }) {
            it[PaymentTransactions.externalId] = externalId
            it[PaymentTransactions.providerCreatedAt] = providerCreatedAt
            it[updatedAt] = now().toOffsetDateTime()
        } > 0
    }

    /**
     * Moves a transaction to PAID if nothing else has, and says whether this call did.
     *
     * Providers retry, and two deliveries of the same "paid" can arrive together; both
     * used to see PENDING and both granted a month. The state change is now the claim —
     * exactly one caller gets true, and only that one grants.
     */
    suspend fun claimPaid(id: Uuid): Boolean = dbQuery {
        val timestamp = now().toOffsetDateTime()
        PaymentTransactions.update({
            (PaymentTransactions.id eq id) and (PaymentTransactions.state neq PaymentState.PAID.dbValue())
        }) {
            it[state] = PaymentState.PAID.dbValue()
            it[paidAt] = timestamp
            it[updatedAt] = timestamp
        } > 0
    }

    /** Hands a claimed payment back when the grant after it failed, so a retry can finish. */
    suspend fun releasePaid(id: Uuid, previous: PaymentState): Unit = dbQuery {
        PaymentTransactions.update({ PaymentTransactions.id eq id }) {
            it[state] = previous.dbValue()
            it[paidAt] = null
            it[updatedAt] = now().toOffsetDateTime()
        }
    }

    suspend fun attachSubscription(id: Uuid, subscriptionId: Uuid): Unit = dbQuery {
        PaymentTransactions.update({ PaymentTransactions.id eq id }) {
            it[PaymentTransactions.subscriptionId] = subscriptionId
            it[updatedAt] = now().toOffsetDateTime()
        }
    }

    suspend fun markPaid(id: Uuid, subscriptionId: Uuid?): Unit = dbQuery {
        val timestamp = now().toOffsetDateTime()
        PaymentTransactions.update({ PaymentTransactions.id eq id }) {
            it[state] = PaymentState.PAID.dbValue()
            it[paidAt] = timestamp
            it[PaymentTransactions.subscriptionId] = subscriptionId
            it[updatedAt] = timestamp
        }
    }

    suspend fun markCancelled(id: Uuid, reason: Int?, state: PaymentState = PaymentState.CANCELLED): Unit = dbQuery {
        val timestamp = now().toOffsetDateTime()
        PaymentTransactions.update({ PaymentTransactions.id eq id }) {
            it[PaymentTransactions.state] = state.dbValue()
            it[cancelledAt] = timestamp
            it[cancelReason] = reason
            it[updatedAt] = timestamp
        }
    }

    /**
     * The window's money. Counted from the transactions rather than the subscriptions,
     * because a comped subscription is not revenue and must not appear as any.
     */
    suspend fun summary(days: Int): BillingSummary = dbQuery {
        val since = (now() - days.days).toOffsetDateTime()
        val rows = PaymentTransactions
            .selectAll()
            .where { PaymentTransactions.createdAt greaterEq since }
            .map { it.toRecord() }
        val paid = rows.filter { it.state == PaymentState.PAID }

        BillingSummary(
            days = days,
            paidCount = paid.size,
            pendingCount = rows.count { it.state == PaymentState.PENDING },
            failedCount = rows.count { it.state == PaymentState.FAILED || it.state == PaymentState.CANCELLED },
            revenueMinor = paid.sumOf { it.amountMinor },
            byProvider = paid
                .groupBy { it.provider }
                .map { (provider, group) -> ProviderRevenue(provider, group.size, group.sumOf { it.amountMinor }) }
                .sortedByDescending { it.revenueMinor },
            activeSubscriptions = Subscriptions
                .selectAll()
                .where { Subscriptions.status eq "active" }
                .count(),
        )
    }

    // ---------------------------------------------------------------- callbacks

    suspend fun recordCallback(
        provider: PaymentProvider,
        method: String?,
        transactionId: Uuid?,
        payload: String,
        outcome: String,
    ): Unit = dbQuery {
        PaymentCallbacks.insert {
            it[PaymentCallbacks.id] = Uuid.random()
            it[PaymentCallbacks.provider] = provider.dbValue()
            it[PaymentCallbacks.method] = method
            it[PaymentCallbacks.transactionId] = transactionId
            // Truncated because a provider retrying a broken body forever should not
            // become a storage problem; the head is what identifies the call.
            it[PaymentCallbacks.payload] = payload.take(4000)
            it[PaymentCallbacks.outcome] = outcome
            it[createdAt] = now().toOffsetDateTime()
        }
    }
}

private fun ResultRow.toPlan(): BillingPlan {
    val period = enumFromDb(this[BillingPlans.period], BillingPeriod.MONTH)
    val price = this[BillingPlans.priceMinor]
    return BillingPlan(
        id = this[BillingPlans.id],
        title = this[BillingPlans.title],
        period = period,
        priceMinor = price,
        currency = this[BillingPlans.currency],
        // Integer division: a year plan's monthly figure rounds down, which is the
        // direction that cannot overstate what she pays.
        monthlyEquivalentMinor = if (period == BillingPeriod.YEAR) price / 12 else null,
        trialDays = this[BillingPlans.trialDays],
        highlighted = this[BillingPlans.highlighted],
        appStoreProductId = this[BillingPlans.appStoreProductId],
        googlePlayProductId = this[BillingPlans.googlePlayProductId],
    )
}

private fun ResultRow.toRecord() = TransactionRecord(
    id = this[PaymentTransactions.id],
    userId = this[PaymentTransactions.userId],
    planId = this[PaymentTransactions.planId],
    provider = enumFromDb(this[PaymentTransactions.provider], PaymentProvider.PAYME),
    amountMinor = this[PaymentTransactions.amountMinor],
    currency = this[PaymentTransactions.currency],
    state = enumFromDb(this[PaymentTransactions.state], PaymentState.PENDING),
    externalId = this[PaymentTransactions.externalId],
    providerCreatedAt = this[PaymentTransactions.providerCreatedAt],
    paidAt = this[PaymentTransactions.paidAt]?.toKotlinInstant(),
    cancelledAt = this[PaymentTransactions.cancelledAt]?.toKotlinInstant(),
    cancelReason = this[PaymentTransactions.cancelReason],
    subscriptionId = this[PaymentTransactions.subscriptionId],
    createdAt = this[PaymentTransactions.createdAt].toKotlinInstant(),
)

/** What came in over a window, for the panel's revenue card. */
@kotlinx.serialization.Serializable
data class BillingSummary(
    val days: Int,
    val paidCount: Int,
    val pendingCount: Int,
    val failedCount: Int,
    val revenueMinor: Long,
    val byProvider: List<ProviderRevenue>,
    val activeSubscriptions: Long,
)

@kotlinx.serialization.Serializable
data class ProviderRevenue(val provider: PaymentProvider, val paidCount: Int, val revenueMinor: Long)
