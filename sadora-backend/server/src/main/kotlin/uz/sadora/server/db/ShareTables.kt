package uz.sadora.server.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * Profile shares, as V21 created them.
 *
 * Only the token's hash is here. The summary a share opens is built from the health
 * tables at read time and never written down, which is why this file has one object.
 */
object ProfileShares : Table("profile_shares") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val tokenHash = text("token_hash")
    val createdAt = timestampWithTimeZone("created_at")
    val expiresAt = timestampWithTimeZone("expires_at")
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
    val viewCount = integer("view_count")
    val lastViewedAt = timestampWithTimeZone("last_viewed_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
