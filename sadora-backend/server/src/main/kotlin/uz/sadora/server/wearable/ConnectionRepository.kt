package uz.sadora.server.wearable

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import uz.sadora.contract.ConnectionStatus
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.WearableConnection
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.OAuthStates
import uz.sadora.server.db.WearableConnections
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

/** A connection row with the encrypted tokens the DTO never carries. */
data class ConnectionRecord(
    val userId: Uuid,
    val provider: HealthProvider,
    val externalUserId: String?,
    val accessTokenEnc: String,
    val refreshTokenEnc: String?,
    val tokenExpiresAt: Instant,
    val scopes: List<String>,
    val status: ConnectionStatus,
    val lastSyncAt: Instant?,
    val lastError: String?,
    val connectedAt: Instant,
) {
    fun toDto() = WearableConnection(
        provider = provider,
        status = status,
        connectedAt = connectedAt,
        lastSyncAt = lastSyncAt,
        lastError = lastError,
        scopes = scopes,
    )
}

/** What a successful token exchange or refresh hands over to be stored. */
data class GrantedTokens(
    val accessTokenEnc: String,
    val refreshTokenEnc: String?,
    val expiresAt: Instant,
    val scopes: List<String>,
    val externalUserId: String? = null,
)

class ConnectionRepository {

    // ---------------------------------------------------------------- oauth state

    suspend fun saveState(state: String, userId: Uuid, provider: HealthProvider, expiresAt: Instant): Unit = dbQuery {
        OAuthStates.insert {
            it[OAuthStates.state] = state
            it[OAuthStates.userId] = userId
            it[OAuthStates.provider] = provider.dbValue()
            it[createdAt] = now().toOffsetDateTime()
            it[OAuthStates.expiresAt] = expiresAt.toOffsetDateTime()
        }
    }

    /** Takes the state out, so it can be used exactly once. Null when unknown or stale. */
    suspend fun consumeState(state: String, provider: HealthProvider): Uuid? = dbQuery {
        val row = OAuthStates.selectAll()
            .where { (OAuthStates.state eq state) and (OAuthStates.provider eq provider.dbValue()) }
            .firstOrNull()
        OAuthStates.deleteWhere { OAuthStates.state eq state }
        row?.takeIf { it[OAuthStates.expiresAt].toKotlinInstant() > now() }?.get(OAuthStates.userId)
    }

    suspend fun sweepStates(): Int = dbQuery {
        OAuthStates.deleteWhere { OAuthStates.expiresAt less now().toOffsetDateTime() }
    }

    // ---------------------------------------------------------------- connections

    suspend fun save(userId: Uuid, provider: HealthProvider, tokens: GrantedTokens): Unit = dbQuery {
        val at = now().toOffsetDateTime()
        WearableConnections.upsert(WearableConnections.userId, WearableConnections.provider) {
            it[WearableConnections.userId] = userId
            it[WearableConnections.provider] = provider.dbValue()
            it[externalUserId] = tokens.externalUserId
            it[accessTokenEnc] = tokens.accessTokenEnc
            it[refreshTokenEnc] = tokens.refreshTokenEnc
            it[tokenExpiresAt] = tokens.expiresAt.toOffsetDateTime()
            it[scopes] = tokens.scopes.joinToString(" ")
            it[status] = ConnectionStatus.ACTIVE.dbValue()
            it[lastError] = null
            it[connectedAt] = at
            it[updatedAt] = at
        }
    }

    /** A refresh keeps the row — and its connected-at and sync history — and swaps the tokens. */
    suspend fun updateTokens(userId: Uuid, provider: HealthProvider, tokens: GrantedTokens): Unit = dbQuery {
        WearableConnections.update({ (WearableConnections.userId eq userId) and (WearableConnections.provider eq provider.dbValue()) }) {
            it[accessTokenEnc] = tokens.accessTokenEnc
            if (tokens.refreshTokenEnc != null) it[refreshTokenEnc] = tokens.refreshTokenEnc
            it[tokenExpiresAt] = tokens.expiresAt.toOffsetDateTime()
            it[status] = ConnectionStatus.ACTIVE.dbValue()
            it[lastError] = null
            it[updatedAt] = now().toOffsetDateTime()
        }
    }

    suspend fun find(userId: Uuid, provider: HealthProvider): ConnectionRecord? = dbQuery {
        WearableConnections.selectAll()
            .where { (WearableConnections.userId eq userId) and (WearableConnections.provider eq provider.dbValue()) }
            .firstOrNull()
            ?.toRecord()
    }

    suspend fun listOf(userId: Uuid): List<ConnectionRecord> = dbQuery {
        WearableConnections.selectAll()
            .where { WearableConnections.userId eq userId }
            .mapNotNull { it.toRecord() }
    }

    suspend fun findByExternalUser(provider: HealthProvider, externalUserId: String): ConnectionRecord? = dbQuery {
        WearableConnections.selectAll()
            .where { (WearableConnections.provider eq provider.dbValue()) and (WearableConnections.externalUserId eq externalUserId) }
            .firstOrNull()
            ?.toRecord()
    }

    /** Active connections not synced since [before], oldest first — the job's queue. */
    suspend fun dueForSync(provider: HealthProvider, before: Instant, limit: Int): List<ConnectionRecord> = dbQuery {
        WearableConnections.selectAll()
            .where {
                (WearableConnections.provider eq provider.dbValue()) and
                    (WearableConnections.status eq ConnectionStatus.ACTIVE.dbValue()) and
                    (WearableConnections.lastSyncAt.isNull() or (WearableConnections.lastSyncAt less before.toOffsetDateTime()))
            }
            .limit(limit)
            .mapNotNull { it.toRecord() }
            .sortedBy { it.lastSyncAt ?: Instant.DISTANT_PAST }
    }

    suspend fun markSynced(userId: Uuid, provider: HealthProvider, at: Instant): Unit = dbQuery {
        WearableConnections.update({ (WearableConnections.userId eq userId) and (WearableConnections.provider eq provider.dbValue()) }) {
            it[lastSyncAt] = at.toOffsetDateTime()
            it[lastError] = null
            it[status] = ConnectionStatus.ACTIVE.dbValue()
            it[updatedAt] = at.toOffsetDateTime()
        }
    }

    suspend fun markFailed(userId: Uuid, provider: HealthProvider, status: ConnectionStatus, error: String): Unit = dbQuery {
        WearableConnections.update({ (WearableConnections.userId eq userId) and (WearableConnections.provider eq provider.dbValue()) }) {
            it[WearableConnections.status] = status.dbValue()
            it[lastError] = error.take(64)
            it[updatedAt] = now().toOffsetDateTime()
        }
    }

    suspend fun delete(userId: Uuid, provider: HealthProvider): Boolean = dbQuery {
        WearableConnections.deleteWhere { (WearableConnections.userId eq userId) and (WearableConnections.provider eq provider.dbValue()) } > 0
    }

    private fun ResultRow.toRecord(): ConnectionRecord? {
        val provider = enumFromDb<HealthProvider>(this[WearableConnections.provider]) ?: return null
        return ConnectionRecord(
            userId = this[WearableConnections.userId],
            provider = provider,
            externalUserId = this[WearableConnections.externalUserId],
            accessTokenEnc = this[WearableConnections.accessTokenEnc],
            refreshTokenEnc = this[WearableConnections.refreshTokenEnc],
            tokenExpiresAt = this[WearableConnections.tokenExpiresAt].toKotlinInstant(),
            scopes = this[WearableConnections.scopes].split(' ').filter { it.isNotBlank() },
            status = enumFromDb(this[WearableConnections.status], ConnectionStatus.ACTIVE),
            lastSyncAt = this[WearableConnections.lastSyncAt]?.toKotlinInstant(),
            lastError = this[WearableConnections.lastError],
            connectedAt = this[WearableConnections.connectedAt].toKotlinInstant(),
        )
    }
}
