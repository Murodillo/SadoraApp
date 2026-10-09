package uz.sadora.server.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/** What the panel edits about a frame: its prices and whether it is offered (V46). */
object FrameProducts : Table("frame_products") {
    val frame = text("frame")
    val coinCost = integer("coin_cost").nullable()
    val priceMinor = long("price_minor").nullable()
    val currency = text("currency")
    val appStoreProductId = text("app_store_product_id").nullable()
    val googlePlayProductId = text("google_play_product_id").nullable()
    val active = bool("active")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(frame)
}

/** A frame she bought or was given; a refund revokes the row rather than deleting it (V46). */
object UserFramesOwned : Table("user_frames_owned") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val frame = text("frame")
    val acquiredBy = text("source")
    val transactionId = uuid("transaction_id").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

/** The frame she wears; ownership is read live (V46). */
object UserWornFrame : Table("user_worn_frame") {
    val userId = uuid("user_id").references(Users.id)
    val frame = text("frame")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(userId)
}
