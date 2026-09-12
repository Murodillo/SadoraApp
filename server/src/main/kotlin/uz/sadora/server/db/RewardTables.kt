package uz.sadora.server.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * Streaks, Gul, the shop and the home layout, as V20 created them.
 *
 * As everywhere else in this package, Flyway owns the DDL and these objects only
 * describe it. Note what is absent: no column here joins to a health table, because
 * nothing in the reward scheme is allowed to read one.
 */

object UserStreaks : Table("user_streaks") {
    val userId = uuid("user_id").references(Users.id)
    val currentDays = integer("current_days")
    val longestDays = integer("longest_days")
    /** Her calendar day, not the server's — the streak resets at her midnight. */
    val lastOpenOn = date("last_open_on").nullable()
    val totalDays = integer("total_days")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(userId)
}

object CoinRules : Table("coin_rules") {
    val reason = text("reason")
    val amount = integer("amount")
    val dailyCap = integer("daily_cap").nullable()
    val enabled = bool("enabled")
    val description = text("description")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(reason)
}

/** Every coin that has moved. The balance is the sum of [amount]; there is no cache. */
object CoinLedger : Table("coin_ledger") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val amount = integer("amount")
    val reason = text("reason")
    val earnedOn = date("earned_on").nullable()
    val reference = text("reference").nullable()
    val note = text("note").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object ReferralCodes : Table("referral_codes") {
    val code = text("code")
    val userId = uuid("user_id").references(Users.id)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(code)
}

object ReferralClaims : Table("referral_claims") {
    val invitedUserId = uuid("invited_user_id").references(Users.id)
    val inviterUserId = uuid("inviter_user_id").references(Users.id)
    val code = text("code")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(invitedUserId)
}

object ShopProducts : Table("shop_products") {
    val id = uuid("id")
    val slug = text("slug")
    val kind = text("kind")
    val title = text("title")
    val brand = text("brand").nullable()
    val description = text("description").nullable()
    val emoji = text("emoji").nullable()
    val priceUzs = long("price_uzs").nullable()
    val discountPercent = integer("discount_percent")
    val coinCost = integer("coin_cost")
    val premiumDays = integer("premium_days").nullable()
    val stock = integer("stock").nullable()
    val active = bool("active")
    val position = integer("position")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object ShopRedemptions : Table("shop_redemptions") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val productId = uuid("product_id").references(ShopProducts.id)
    val coinCost = integer("coin_cost")
    val discountPercent = integer("discount_percent")
    val code = text("code")
    val status = text("status")
    val expiresAt = timestampWithTimeZone("expires_at").nullable()
    val usedAt = timestampWithTimeZone("used_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object HomeWidgetLayout : Table("home_widgets") {
    val userId = uuid("user_id").references(Users.id)
    val widgetKey = text("widget_key")
    val position = integer("position")
    val visible = bool("visible")

    override val primaryKey = PrimaryKey(userId, widgetKey)
}
