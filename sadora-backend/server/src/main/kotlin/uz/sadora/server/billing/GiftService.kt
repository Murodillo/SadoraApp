package uz.sadora.server.billing

import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.sadora.contract.SubscriptionSource
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.PremiumGiftCredits
import uz.sadora.server.db.Subscriptions
import uz.sadora.server.db.dbQuery
import uz.sadora.server.entitlement.EntitlementService
import uz.sadora.server.entitlement.SubscriptionRepository

/**
 * Premium someone else paid for.
 *
 * Gifted days go on top of what she has when nothing of hers renews by itself. When she
 * pays a store herself, the store's next renewal rewrites her expiry, so days added to it
 * would vanish; they are banked instead and spent the day her own subscription is gone.
 * Every gift is one credit row keyed by its payment, which is what makes a provider's
 * retry find the work done rather than give a second month.
 */
class GiftService(
    private val subscriptions: SubscriptionRepository,
    private val entitlements: EntitlementService,
) {

    /** Grants [days] to [userId] for [transactionId]; the subscription made, or null when banked. */
    suspend fun grant(userId: Uuid, days: Int, planId: String, transactionId: Uuid): Uuid? {
        existing(transactionId)?.let { return it.subscriptionId }

        val current = entitlements.subscriptionStatus(userId)
        val renewsByItself = current.source == SubscriptionSource.APP_STORE || current.source == SubscriptionSource.GOOGLE_PLAY
        val endless = current.startedAt != null && current.expiresAt == null
        if (renewsByItself || endless) {
            insertCredit(userId, days, transactionId, subscriptionId = null)
            return null
        }

        val subscriptionId = subscriptions.extend(
            userId = userId,
            source = SubscriptionSource.MANUAL,
            by = days.days,
            productId = planId,
            onceFor = "gift $transactionId",
        )
        insertCredit(userId, days, transactionId, subscriptionId)
        return subscriptionId
    }

    /**
     * Spends banked days once nothing else is active. True when it made a subscription.
     * The claim is the conditional update: two reads racing here apply the bank once.
     */
    suspend fun applyBanked(userId: Uuid): Boolean {
        val pending = dbQuery {
            PremiumGiftCredits.selectAll()
                .where {
                    (PremiumGiftCredits.userId eq userId) and PremiumGiftCredits.appliedAt.isNull() and
                        PremiumGiftCredits.revokedAt.isNull()
                }
                .orderBy(PremiumGiftCredits.createdAt to SortOrder.ASC)
                .map { it[PremiumGiftCredits.id] to it[PremiumGiftCredits.days] }
        }
        if (pending.isEmpty()) return false
        val ids = pending.map { it.first }
        val at = now()
        val claimed = dbQuery {
            PremiumGiftCredits.update({
                (PremiumGiftCredits.id inList ids) and PremiumGiftCredits.appliedAt.isNull() and
                    PremiumGiftCredits.revokedAt.isNull()
            }) { it[appliedAt] = at.toOffsetDateTime() }
        }
        if (claimed != ids.size) return false
        val subscriptionId = subscriptions.grant(
            userId = userId,
            source = SubscriptionSource.MANUAL,
            expiresAt = at + pending.sumOf { it.second }.days,
            productId = GIFT_PRODUCT,
            reason = "gift bank",
        )
        dbQuery {
            PremiumGiftCredits.update({ PremiumGiftCredits.id inList ids }) { it[PremiumGiftCredits.subscriptionId] = subscriptionId }
        }
        return true
    }

    /**
     * Takes a refunded gift's days back: unspent, the credit is simply withdrawn; spent,
     * her current expiry moves back by as many days, never into the past.
     */
    suspend fun revoke(transactionId: Uuid): Boolean {
        val credit = existing(transactionId) ?: return false
        if (credit.revoked) return false
        val at = now()
        dbQuery {
            PremiumGiftCredits.update({ PremiumGiftCredits.id eq credit.id }) { it[revokedAt] = at.toOffsetDateTime() }
        }
        if (!credit.applied) return true
        val active = dbQuery {
            Subscriptions.selectAll()
                .where { (Subscriptions.userId eq credit.userId) and (Subscriptions.status eq "active") }
                .maxByOrNull { it[Subscriptions.startedAt] }
                ?.let { it[Subscriptions.id] to it[Subscriptions.expiresAt]?.toKotlinInstant() }
        } ?: return true
        val (id, expiresAt) = active
        val shortened = expiresAt?.minus(credit.days.days)?.let { maxOf(it, at) } ?: return true
        dbQuery {
            Subscriptions.update({ Subscriptions.id eq id }) {
                it[Subscriptions.expiresAt] = shortened.toOffsetDateTime()
                it[updatedAt] = at.toOffsetDateTime()
            }
        }
        return true
    }

    private data class Credit(
        val id: Uuid,
        val userId: Uuid,
        val days: Int,
        val applied: Boolean,
        val revoked: Boolean,
        val subscriptionId: Uuid?,
    )

    private suspend fun existing(transactionId: Uuid): Credit? = dbQuery {
        PremiumGiftCredits.selectAll()
            .where { PremiumGiftCredits.transactionId eq transactionId }
            .singleOrNull()
            ?.let {
                Credit(
                    id = it[PremiumGiftCredits.id],
                    userId = it[PremiumGiftCredits.userId],
                    days = it[PremiumGiftCredits.days],
                    applied = it[PremiumGiftCredits.appliedAt] != null,
                    revoked = it[PremiumGiftCredits.revokedAt] != null,
                    subscriptionId = it[PremiumGiftCredits.subscriptionId],
                )
            }
    }

    private suspend fun insertCredit(userId: Uuid, days: Int, transactionId: Uuid, subscriptionId: Uuid?) = dbQuery {
        val at = now().toOffsetDateTime()
        PremiumGiftCredits.insert {
            it[id] = Uuid.random()
            it[PremiumGiftCredits.userId] = userId
            it[PremiumGiftCredits.days] = days
            it[PremiumGiftCredits.transactionId] = transactionId
            it[createdAt] = at
            it[appliedAt] = if (subscriptionId != null) at else null
            it[PremiumGiftCredits.subscriptionId] = subscriptionId
        }
    }

    companion object {
        const val GIFT_PRODUCT = "gift"
    }
}
