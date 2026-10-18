package uz.sadora.server.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/** Her Telegram chat (V62). One chat per account and one account per chat. */
object TelegramLinks : Table("telegram_links") {
    val userId = uuid("user_id").references(Users.id)
    val chatId = long("chat_id")
    val username = text("username").nullable()
    val detailed = bool("detailed")
    val linkedAt = timestampWithTimeZone("linked_at")

    override val primaryKey = PrimaryKey(userId)
}

object TelegramLinkCodes : Table("telegram_link_codes") {
    val code = text("code")
    val userId = uuid("user_id").references(Users.id)
    val expiresAt = timestampWithTimeZone("expires_at")
    val usedAt = timestampWithTimeZone("used_at").nullable()

    override val primaryKey = PrimaryKey(code)
}

object TelegramOffset : Table("telegram_offset") {
    val id = integer("id")
    val nextUpdate = long("next_update")

    override val primaryKey = PrimaryKey(id)
}
