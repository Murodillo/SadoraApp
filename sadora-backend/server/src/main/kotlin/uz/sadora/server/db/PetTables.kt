package uz.sadora.server.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/** The pet she picked, as V42 created it. No row means the default one. */
object UserPet : Table("user_pet") {
    val userId = uuid("user_id").references(Users.id)
    val pet = text("pet")
    /** What she had before a bought pet replaced it, for a refund to give back (V45). */
    val previousPet = text("previous_pet").nullable()
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(userId)
}

/** A legendary pet's price and store products (V45). */
object PetProducts : Table("pet_products") {
    val pet = text("pet")
    val priceMinor = long("price_minor")
    val currency = text("currency")
    val appStoreProductId = text("app_store_product_id").nullable()
    val googlePlayProductId = text("google_play_product_id").nullable()
    val active = bool("active")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(pet)
}

/** One bought pet; a refund revokes the row rather than deleting it (V45). */
object UserPetsOwned : Table("user_pets_owned") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val pet = text("pet")
    val transactionId = uuid("transaction_id").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

/** The one-off offer, once seen (V45). */
object PetOffersSeen : Table("pet_offers_seen") {
    val userId = uuid("user_id").references(Users.id)
    val pet = text("pet")
    val seenAt = timestampWithTimeZone("seen_at")

    override val primaryKey = PrimaryKey(userId, pet)
}
