package uz.sadora.server.pet

import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import uz.sadora.contract.PetKind
import uz.sadora.server.core.now
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.UserPet
import uz.sadora.server.db.dbQuery

class PetRepository {

    /** Her pet, or null when she never picked one. An unknown stored key reads as none. */
    suspend fun chosen(userId: Uuid): PetKind? = dbQuery {
        val key = UserPet.selectAll().where { UserPet.userId eq userId }
            .firstOrNull()?.get(UserPet.pet) ?: return@dbQuery null
        PetKind.entries.firstOrNull { it.wireKey == key }
    }

    suspend fun choose(userId: Uuid, pet: PetKind) = dbQuery {
        UserPet.upsert {
            it[UserPet.userId] = userId
            it[UserPet.pet] = pet.wireKey
            it[updatedAt] = now().toOffsetDateTime()
        }
    }
}

/** The key as the contract serialises it, so the column and the JSON agree. */
val PetKind.wireKey: String get() = name.lowercase()
