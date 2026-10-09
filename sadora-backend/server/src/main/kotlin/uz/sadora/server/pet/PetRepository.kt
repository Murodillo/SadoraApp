package uz.sadora.server.pet

import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import uz.sadora.contract.PetKind
import uz.sadora.server.core.now
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.PetOffersSeen
import uz.sadora.server.db.PetProducts
import uz.sadora.server.db.UserPet
import uz.sadora.server.db.UserPetsOwned
import uz.sadora.server.db.dbQuery

/** A legendary pet's price row. */
data class PetProductRecord(
    val pet: PetKind,
    val priceMinor: Long,
    val currency: String,
    val appStoreProductId: String?,
    val googlePlayProductId: String?,
    val active: Boolean = true,
)

class PetRepository {

    /** Her pet, or null when she never picked one. An unknown stored key reads as none. */
    suspend fun chosen(userId: Uuid): PetKind? = dbQuery {
        val key = UserPet.selectAll().where { UserPet.userId eq userId }
            .firstOrNull()?.get(UserPet.pet) ?: return@dbQuery null
        petOf(key)
    }

    /**
     * Picks [pet]. Moving onto a legendary pet remembers the one she leaves, so a refund
     * can hand it back; any other move forgets it.
     */
    suspend fun choose(userId: Uuid, pet: PetKind) = dbQuery {
        val current = UserPet.selectAll().where { UserPet.userId eq userId }.firstOrNull()
        val leaving = current?.get(UserPet.pet)?.let(::petOf)
        val previous = when {
            !pet.legendary -> null
            leaving == null || leaving.legendary -> current?.get(UserPet.previousPet)
            else -> leaving.wireKey
        }
        UserPet.upsert {
            it[UserPet.userId] = userId
            it[UserPet.pet] = pet.wireKey
            it[previousPet] = previous
            it[updatedAt] = now().toOffsetDateTime()
        }
    }

    /** After a refund: if she still has [pet] on, the one she had before it comes back. */
    suspend fun stepBackFrom(userId: Uuid, pet: PetKind) = dbQuery {
        val row = UserPet.selectAll().where { UserPet.userId eq userId }.firstOrNull() ?: return@dbQuery
        if (row[UserPet.pet] != pet.wireKey) return@dbQuery
        val back = row[UserPet.previousPet]?.let(::petOf)?.takeUnless { it.legendary } ?: PetKind.DEFAULT
        UserPet.update({ UserPet.userId eq userId }) {
            it[UserPet.pet] = back.wireKey
            it[previousPet] = null
            it[updatedAt] = now().toOffsetDateTime()
        }
    }

    // ---------------------------------------------------------------- bought pets

    suspend fun owned(userId: Uuid): List<PetKind> = dbQuery {
        UserPetsOwned.selectAll()
            .where { (UserPetsOwned.userId eq userId) and UserPetsOwned.revokedAt.isNull() }
            .mapNotNull { petOf(it[UserPetsOwned.pet]) }
            .distinct()
    }

    /**
     * Records that [transactionId] bought her [pet]; true when this call added it.
     * Idempotent: the payment's own id and the one-live-copy index both refuse a second
     * row, so a provider's retry adds nothing.
     */
    suspend fun grant(userId: Uuid, pet: PetKind, transactionId: Uuid): Boolean = dbQuery {
        UserPetsOwned.insertIgnore {
            it[id] = Uuid.random()
            it[UserPetsOwned.userId] = userId
            it[UserPetsOwned.pet] = pet.wireKey
            it[UserPetsOwned.transactionId] = transactionId
            it[createdAt] = now().toOffsetDateTime()
        }.insertedCount > 0
    }

    /** Takes back what [transactionId] bought. True when there was something live to take. */
    suspend fun revoke(transactionId: Uuid): Boolean = dbQuery {
        UserPetsOwned.update({ (UserPetsOwned.transactionId eq transactionId) and UserPetsOwned.revokedAt.isNull() }) {
            it[revokedAt] = now().toOffsetDateTime()
        } > 0
    }

    suspend fun products(activeOnly: Boolean = true): List<PetProductRecord> = dbQuery {
        var query = PetProducts.selectAll()
        if (activeOnly) query = query.where { PetProducts.active eq true }
        query.mapNotNull { row ->
            val pet = petOf(row[PetProducts.pet]) ?: return@mapNotNull null
            PetProductRecord(
                pet = pet,
                priceMinor = row[PetProducts.priceMinor],
                currency = row[PetProducts.currency],
                appStoreProductId = row[PetProducts.appStoreProductId],
                googlePlayProductId = row[PetProducts.googlePlayProductId],
                active = row[PetProducts.active],
            )
        }
    }

    /** The operator's new price. The stores keep their own copy, set in their consoles. */
    suspend fun updateProduct(pet: PetKind, priceMinor: Long, active: Boolean): Boolean = dbQuery {
        PetProducts.update({ PetProducts.pet eq pet.wireKey }) {
            it[PetProducts.priceMinor] = priceMinor
            it[PetProducts.active] = active
            it[updatedAt] = now().toOffsetDateTime()
        } > 0
    }

    // ---------------------------------------------------------------- the one-off offer

    suspend fun offerSeen(userId: Uuid, pet: PetKind): Boolean = dbQuery {
        PetOffersSeen.selectAll().where { (PetOffersSeen.userId eq userId) and (PetOffersSeen.pet eq pet.wireKey) }.count() > 0
    }

    suspend fun markOfferSeen(userId: Uuid, pet: PetKind) = dbQuery {
        PetOffersSeen.insertIgnore {
            it[PetOffersSeen.userId] = userId
            it[PetOffersSeen.pet] = pet.wireKey
            it[seenAt] = now().toOffsetDateTime()
        }
        Unit
    }

    private fun petOf(key: String): PetKind? = PetKind.entries.firstOrNull { it.wireKey == key }
}

/** The key as the contract serialises it, so the column and the JSON agree. */
val PetKind.wireKey: String get() = name.lowercase()
