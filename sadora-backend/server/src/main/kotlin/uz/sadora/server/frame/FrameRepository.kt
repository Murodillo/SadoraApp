package uz.sadora.server.frame

import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import uz.sadora.contract.AvatarFrames
import uz.sadora.contract.FrameUnlock
import uz.sadora.server.core.now
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.FrameProducts
import uz.sadora.server.db.UserBadges
import uz.sadora.server.db.UserFramesOwned
import uz.sadora.server.db.UserWornFrame
import uz.sadora.server.db.dbQuery

/** A frame's prices as the panel set them. */
data class FrameProductRecord(
    val frame: String,
    val coinCost: Int?,
    val priceMinor: Long?,
    val currency: String,
    val appStoreProductId: String?,
    val googlePlayProductId: String?,
    val active: Boolean,
)

class FrameRepository {

    suspend fun products(activeOnly: Boolean = true): List<FrameProductRecord> = dbQuery {
        var query = FrameProducts.selectAll()
        if (activeOnly) query = query.where { FrameProducts.active eq true }
        query.map {
            FrameProductRecord(
                frame = it[FrameProducts.frame],
                coinCost = it[FrameProducts.coinCost],
                priceMinor = it[FrameProducts.priceMinor],
                currency = it[FrameProducts.currency],
                appStoreProductId = it[FrameProducts.appStoreProductId],
                googlePlayProductId = it[FrameProducts.googlePlayProductId],
                active = it[FrameProducts.active],
            )
        }
    }

    suspend fun updateProduct(frame: String, coinCost: Int?, priceMinor: Long?, active: Boolean): Boolean = dbQuery {
        FrameProducts.update({ FrameProducts.frame eq frame }) {
            it[FrameProducts.coinCost] = coinCost
            it[FrameProducts.priceMinor] = priceMinor
            it[FrameProducts.active] = active
            it[updatedAt] = now().toOffsetDateTime()
        } > 0
    }

    // ---------------------------------------------------------------- what she owns

    /**
     * The frames she owns: every live bought or given row, and every badge frame whose
     * tier she has reached. Keys outside the catalogue are left out.
     */
    suspend fun owned(userId: Uuid): Set<String> = ownedBy(listOf(userId))[userId].orEmpty()

    /** [owned] for many at once, for the chat's authors. */
    suspend fun ownedBy(userIds: Collection<Uuid>): Map<Uuid, Set<String>> {
        val ids = userIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        val badgeFrames = AvatarFrames.catalogue.filter { it.unlock == FrameUnlock.BADGE }
        return dbQuery {
            val result = HashMap<Uuid, MutableSet<String>>()
            UserFramesOwned.selectAll()
                .where { (UserFramesOwned.userId inList ids) and UserFramesOwned.revokedAt.isNull() }
                .forEach { row ->
                    val key = row[UserFramesOwned.frame]
                    if (AvatarFrames.byKey(key) != null) result.getOrPut(row[UserFramesOwned.userId]) { HashSet() } += key
                }
            if (badgeFrames.isNotEmpty()) {
                UserBadges.selectAll()
                    .where { (UserBadges.userId inList ids) and (UserBadges.badge inList badgeFrames.mapNotNull { it.badge }) }
                    .forEach { row ->
                        val badge = row[UserBadges.badge]
                        val tier = row[UserBadges.tier]
                        badgeFrames.filter { it.badge == badge && tier >= it.badgeTier }.forEach { frame ->
                            result.getOrPut(row[UserBadges.userId]) { HashSet() } += frame.key
                        }
                    }
            }
            result
        }
    }

    /**
     * A row of ownership, inside the caller's transaction — the Gul purchase writes it
     * under the wallet's lock. True when this call added it; the one-live-copy index and
     * the payment's own id both refuse a second row.
     */
    fun JdbcTransaction.insertOwned(userId: Uuid, frame: String, source: String, transactionId: Uuid? = null): Boolean =
        UserFramesOwned.insertIgnore {
            it[id] = Uuid.random()
            it[UserFramesOwned.userId] = userId
            it[UserFramesOwned.frame] = frame
            it[UserFramesOwned.acquiredBy] = source
            it[UserFramesOwned.transactionId] = transactionId
            it[createdAt] = now().toOffsetDateTime()
        }.insertedCount > 0

    suspend fun grant(userId: Uuid, frame: String, source: String, transactionId: Uuid? = null): Boolean = dbQuery {
        insertOwned(userId, frame, source, transactionId)
    }

    /** Takes back what [transactionId] bought. True when there was something live to take. */
    suspend fun revoke(transactionId: Uuid): Boolean = dbQuery {
        UserFramesOwned.update({ (UserFramesOwned.transactionId eq transactionId) and UserFramesOwned.revokedAt.isNull() }) {
            it[revokedAt] = now().toOffsetDateTime()
        } > 0
    }

    // ---------------------------------------------------------------- what she wears

    suspend fun worn(userId: Uuid): String? = dbQuery {
        UserWornFrame.selectAll().where { UserWornFrame.userId eq userId }.firstOrNull()?.get(UserWornFrame.frame)
    }

    /** The frame each of [userIds] chose, owned or not; the caller checks ownership. */
    suspend fun wornChoices(userIds: Collection<Uuid>): Map<Uuid, String> {
        val ids = userIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        return dbQuery {
            UserWornFrame.selectAll().where { UserWornFrame.userId inList ids }
                .associate { it[UserWornFrame.userId] to it[UserWornFrame.frame] }
        }
    }

    /** The frame each of [userIds] wears and still owns. */
    suspend fun wornBy(userIds: Collection<Uuid>): Map<Uuid, String> {
        val chosen = wornChoices(userIds)
        if (chosen.isEmpty()) return chosen
        val owned = ownedBy(chosen.keys)
        return chosen.filter { (userId, key) -> key in owned[userId].orEmpty() }
    }

    suspend fun wear(userId: Uuid, frame: String?) = dbQuery {
        if (frame == null) {
            UserWornFrame.deleteWhere { UserWornFrame.userId eq userId }
        } else {
            UserWornFrame.upsert {
                it[UserWornFrame.userId] = userId
                it[UserWornFrame.frame] = frame
                it[updatedAt] = now().toOffsetDateTime()
            }
        }
        Unit
    }
}
