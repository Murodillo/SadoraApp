package uz.sadora.server.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/** Cloud wearable grants and the OAuth states in flight, as V22 created them. */
object WearableConnections : Table("wearable_connections") {
    val userId = uuid("user_id").references(Users.id)
    val provider = text("provider")
    val externalUserId = text("external_user_id").nullable()
    val accessTokenEnc = text("access_token_enc")
    val refreshTokenEnc = text("refresh_token_enc").nullable()
    val tokenExpiresAt = timestampWithTimeZone("token_expires_at")
    val scopes = text("scopes")
    val status = text("status")
    val lastSyncAt = timestampWithTimeZone("last_sync_at").nullable()
    val lastError = text("last_error").nullable()
    val connectedAt = timestampWithTimeZone("connected_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(userId, provider)
}

object OAuthStates : Table("oauth_states") {
    val state = text("state")
    val userId = uuid("user_id").references(Users.id)
    val provider = text("provider")
    val createdAt = timestampWithTimeZone("created_at")
    val expiresAt = timestampWithTimeZone("expires_at")

    override val primaryKey = PrimaryKey(state)
}
