package uz.sadora.server.entitlement

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.sadora.contract.SubscriptionSource
import uz.sadora.contract.SubscriptionTier
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.Subscriptions
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

/**
 * Writes to `subscriptions`. Store IAP and Payme/Click webhooks land here in sprint 3;
 * for now the only writer is a manual admin grant, which the entitlement path already
 * treats identically to a paid one.
 */
class SubscriptionRepository {

    suspend fun grant(
        userId: Uuid,
        source: SubscriptionSource,
        expiresAt: Instant?,
        productId: String? = null,
        externalId: String? = null,
        grantedBy: Uuid? = null,
        reason: String? = null,
    ): Uuid = dbQuery {
        val timestamp = now().toOffsetDateTime()
        // Only one subscription is active at a time; an earlier one is superseded rather
        // than deleted so the history stays readable on the user card.
        Subscriptions.update({
            (Subscriptions.userId eq userId) and (Subscriptions.status eq "active")
        }) {
            it[status] = "superseded"
            it[updatedAt] = timestamp
        }
        val id = Uuid.random()
        Subscriptions.insert {
            it[Subscriptions.id] = id
            it[Subscriptions.userId] = userId
            it[tier] = SubscriptionTier.PREMIUM.dbValue()
            it[paymentSource] = source.dbValue()
            it[Subscriptions.productId] = productId
            it[Subscriptions.externalId] = externalId
            it[status] = "active"
            it[startedAt] = timestamp
            it[Subscriptions.expiresAt] = expiresAt?.toOffsetDateTime()
            it[autoRenewing] = false
            it[inGracePeriod] = false
            it[Subscriptions.grantedBy] = grantedBy
            it[grantReason] = reason
            it[createdAt] = timestamp
            it[updatedAt] = timestamp
        }
        id
    }

    suspend fun revoke(userId: Uuid, reason: String): Boolean = dbQuery {
        Subscriptions.update({
            (Subscriptions.userId eq userId) and (Subscriptions.status eq "active")
        }) {
            it[status] = "revoked"
            it[grantReason] = reason
            it[updatedAt] = now().toOffsetDateTime()
        } > 0
    }

    suspend fun historyOf(userId: Uuid): List<SubscriptionRecord> = dbQuery {
        Subscriptions.selectAll()
            .where { Subscriptions.userId eq userId }
            .map { row ->
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
            .sortedByDescending { it.startedAt }
    }
}
