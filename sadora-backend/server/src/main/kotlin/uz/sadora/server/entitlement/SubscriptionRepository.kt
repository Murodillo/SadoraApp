package uz.sadora.server.entitlement

import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
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

    /**
     * Makes [userId]'s subscription one that ends at [expiresAt] (null: never). Used where
     * the end date is decided by someone — an operator's grant.
     */
    suspend fun grant(
        userId: Uuid,
        source: SubscriptionSource,
        expiresAt: Instant?,
        productId: String? = null,
        externalId: String? = null,
        grantedBy: Uuid? = null,
        reason: String? = null,
    ): Uuid = dbQuery {
        lockSubscriptions(userId)
        insertActive(userId, source, expiresAt, productId, externalId, grantedBy, reason)
    }

    /**
     * Adds [by] to whatever she has: from its end when it is still running, from now when
     * not. Read and written under one lock, so two purchases landing together add twice
     * instead of both starting from the same old end.
     *
     * An endless subscription already covers everything, so nothing is added to it. With
     * [onceFor], a subscription already granted for that reason is returned instead of a
     * second one — the provider's retry of a payment that was already granted.
     */
    suspend fun extend(
        userId: Uuid,
        source: SubscriptionSource,
        by: Duration,
        productId: String? = null,
        externalId: String? = null,
        reason: String? = null,
        onceFor: String? = null,
    ): Uuid = dbQuery {
        lockSubscriptions(userId)
        if (onceFor != null) {
            Subscriptions.selectAll()
                .where { (Subscriptions.userId eq userId) and (Subscriptions.grantReason eq onceFor) }
                .firstOrNull()
                ?.let { return@dbQuery it[Subscriptions.id] }
        }
        val active = activeRows(userId)
        active.firstOrNull { it[Subscriptions.expiresAt] == null }?.let { return@dbQuery it[Subscriptions.id] }
        val at = now()
        val from = active.mapNotNull { it[Subscriptions.expiresAt]?.toKotlinInstant() }
            .filter { it > at }
            .maxOrNull() ?: at
        insertActive(userId, source, from + by, productId, externalId, null, onceFor ?: reason)
    }

    /**
     * A store subscription, which ends when the store says. Time she already has from
     * elsewhere — Payme, a gift, Gul — is carried on top rather than thrown away, and a
     * renewal never ends earlier than what is running now.
     */
    suspend fun grantStore(
        userId: Uuid,
        source: SubscriptionSource,
        storeExpiresAt: Instant?,
        productId: String?,
        externalId: String?,
        reason: String?,
    ): Uuid = dbQuery {
        lockSubscriptions(userId)
        val at = now()
        val active = activeRows(userId)
        // A store product without an end, or an endless grant already running: endless.
        val endless = storeExpiresAt == null || active.any { it[Subscriptions.expiresAt] == null }
        val expiresAt = if (endless) {
            null
        } else {
            val carried = active
                .filter { enumFromDb(it[Subscriptions.paymentSource], SubscriptionSource.MANUAL) != source }
                .mapNotNull { it[Subscriptions.expiresAt]?.toKotlinInstant() }
                .filter { it > at }
                .maxOrNull()
                ?.let { it - at } ?: Duration.ZERO
            val running = active.mapNotNull { it[Subscriptions.expiresAt]?.toKotlinInstant() }.maxOrNull()
            listOfNotNull(storeExpiresAt + carried, running).max()
        }
        insertActive(userId, source, expiresAt, productId, externalId, null, reason)
    }

    /** Serialises every change to one person's subscription; released at commit. */
    private fun JdbcTransaction.lockSubscriptions(userId: Uuid) {
        val key = userId.toLongs { most, least -> most xor least } xor SUBSCRIPTION_LOCK_SALT
        exec("SELECT pg_advisory_xact_lock($key)")
    }

    private fun activeRows(userId: Uuid) = Subscriptions.selectAll()
        .where { (Subscriptions.userId eq userId) and (Subscriptions.status eq "active") }
        .toList()

    private fun insertActive(
        userId: Uuid,
        source: SubscriptionSource,
        expiresAt: Instant?,
        productId: String?,
        externalId: String?,
        grantedBy: Uuid?,
        reason: String?,
    ): Uuid {
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
        return id
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

    private companion object {
        /** Keeps this lock apart from the wallet's, which is keyed by the same user id. */
        const val SUBSCRIPTION_LOCK_SALT = 0x5AB5C41B71L
    }
}
