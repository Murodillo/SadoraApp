package uz.sadora.server.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/** The pet she picked, as V42 created it. No row means the default one. */
object UserPet : Table("user_pet") {
    val userId = uuid("user_id").references(Users.id)
    val pet = text("pet")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(userId)
}
