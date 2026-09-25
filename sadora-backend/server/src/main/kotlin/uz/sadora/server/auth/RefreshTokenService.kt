package uz.sadora.server.auth

import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import uz.sadora.contract.ErrorCodes
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.config.JwtConfig
import uz.sadora.server.core.UnauthorizedException
import uz.sadora.server.core.now
import uz.sadora.server.core.randomToken
import uz.sadora.server.core.sha256
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.RefreshTokens
import uz.sadora.server.db.dbQuery

data class IssuedRefreshToken(val value: String, val expiresAt: Instant, val familyId: Uuid)

/**
 * Rotating refresh tokens.
 *
 * Each use revokes the presented token and issues a successor in the same family. If a
 * token that has already been spent turns up again, the only two explanations are a
 * stolen copy or a badly retried request — either way the whole family is revoked and
 * the event is audited, because guessing wrong in the user's favour means leaving a
 * thief signed in.
 */
class RefreshTokenService(
    private val config: JwtConfig,
    private val audit: AuditService,
) {
    private val logger = LoggerFactory.getLogger(RefreshTokenService::class.java)

    suspend fun issue(userId: Uuid, deviceId: String?, familyId: Uuid = Uuid.random()): IssuedRefreshToken {
        val raw = randomToken()
        val expiresAt = now() + config.refreshTokenTtl
        dbQuery {
            RefreshTokens.insert {
                it[id] = Uuid.random()
                it[RefreshTokens.userId] = userId
                it[RefreshTokens.familyId] = familyId
                it[tokenHash] = sha256(raw)
                it[RefreshTokens.deviceId] = deviceId
                it[issuedAt] = now().toOffsetDateTime()
                it[RefreshTokens.expiresAt] = expiresAt.toOffsetDateTime()
            }
        }
        return IssuedRefreshToken(raw, expiresAt, familyId)
    }

    /**
     * Validates the presented token and returns its owner plus a freshly issued
     * successor. Both steps run in one transaction so a concurrent replay cannot rotate
     * the same token twice.
     */
    suspend fun rotate(rawToken: String, deviceId: String?): Pair<Uuid, IssuedRefreshToken> {
        val hash = sha256(rawToken)
        val successorRaw = randomToken()
        val successorExpiry = now() + config.refreshTokenTtl

        val outcome = withContext(Dispatchers.IO) {
            transaction {
                // The row is locked for the rotation. Without the lock two requests
                // carrying the same token both read it unspent, both issued a successor
                // and neither noticed the other — the "single use" below was not.
                val row = RefreshTokens.selectAll().where { RefreshTokens.tokenHash eq hash }
                    .forUpdate()
                    .singleOrNull() ?: return@transaction RotationOutcome.Unknown

                val userId = row[RefreshTokens.userId]
                val familyId = row[RefreshTokens.familyId]

                val spentAt = row[RefreshTokens.revokedAt]?.toKotlinInstant()
                if (spentAt != null) {
                    // A token rotated a moment ago and presented again is, almost always,
                    // the same phone retrying a request whose answer was lost — the
                    // connection dropped after the server rotated, or two of its calls hit
                    // 401 together. Within the grace window that retry gets a successor of
                    // its own in the same family; a replay any later than that is what the
                    // family revocation exists for.
                    val benignRetry = row[RefreshTokens.revokedReason] == ROTATED &&
                        spentAt > now() - REUSE_GRACE
                    if (!benignRetry) {
                        revokeFamilyIn(familyId, "refresh_token_reuse")
                        return@transaction RotationOutcome.Reused(userId, familyId)
                    }
                }
                if (row[RefreshTokens.expiresAt].toKotlinInstant() <= now()) {
                    return@transaction RotationOutcome.Expired
                }

                val successorId = Uuid.random()
                RefreshTokens.insert {
                    it[id] = successorId
                    it[RefreshTokens.userId] = userId
                    it[RefreshTokens.familyId] = familyId
                    it[tokenHash] = sha256(successorRaw)
                    it[RefreshTokens.deviceId] = deviceId ?: row[RefreshTokens.deviceId]
                    it[issuedAt] = now().toOffsetDateTime()
                    it[RefreshTokens.expiresAt] = successorExpiry.toOffsetDateTime()
                }
                // Only an unspent row is stamped: a retry inside the grace window must
                // not move the window forward, or a chain of replays could keep a spent
                // token alive indefinitely.
                RefreshTokens.update({
                    (RefreshTokens.id eq row[RefreshTokens.id]) and RefreshTokens.revokedAt.isNull()
                }) {
                    it[revokedAt] = now().toOffsetDateTime()
                    it[revokedReason] = ROTATED
                    it[replacedBy] = successorId
                }
                RotationOutcome.Rotated(userId, familyId)
            }
        }

        return when (outcome) {
            RotationOutcome.Unknown ->
                throw UnauthorizedException(ErrorCodes.TOKEN_REVOKED, "Sessiya topilmadi")

            RotationOutcome.Expired ->
                throw UnauthorizedException(ErrorCodes.TOKEN_EXPIRED, "Sessiya muddati tugadi")

            is RotationOutcome.Reused -> {
                logger.warn("Refresh token reuse detected for user {}", outcome.userId)
                audit.record(
                    AuditEntry(
                        actorType = ActorType.SYSTEM,
                        actorId = outcome.userId,
                        action = AuditActions.REFRESH_TOKEN_REUSED,
                        entityType = "user",
                        entityId = outcome.userId.toString(),
                        reason = "Bir marta ishlatilgan refresh token qayta yuborildi",
                        metadata = mapOf("familyId" to outcome.familyId.toString()),
                    ),
                )
                throw UnauthorizedException(
                    ErrorCodes.TOKEN_REVOKED,
                    "Sessiya bekor qilindi. Qaytadan kiring.",
                )
            }

            is RotationOutcome.Rotated ->
                outcome.userId to IssuedRefreshToken(successorRaw, successorExpiry, outcome.familyId)
        }
    }

    suspend fun revoke(rawToken: String, reason: String = "logout"): Unit = dbQuery {
        RefreshTokens.update({
            (RefreshTokens.tokenHash eq sha256(rawToken)) and RefreshTokens.revokedAt.isNull()
        }) {
            it[revokedAt] = now().toOffsetDateTime()
            it[revokedReason] = reason
        }
    }

    suspend fun revokeAllForUser(userId: Uuid, reason: String): Unit = dbQuery {
        RefreshTokens.update({
            (RefreshTokens.userId eq userId) and RefreshTokens.revokedAt.isNull()
        }) {
            it[revokedAt] = now().toOffsetDateTime()
            it[revokedReason] = reason
        }
    }

    private fun revokeFamilyIn(familyId: Uuid, reason: String) {
        RefreshTokens.update({
            (RefreshTokens.familyId eq familyId) and RefreshTokens.revokedAt.isNull()
        }) {
            it[revokedAt] = now().toOffsetDateTime()
            it[revokedReason] = reason
        }
    }

    private sealed interface RotationOutcome {
        data object Unknown : RotationOutcome
        data object Expired : RotationOutcome
        data class Reused(val userId: Uuid, val familyId: Uuid) : RotationOutcome
        data class Rotated(val userId: Uuid, val familyId: Uuid) : RotationOutcome
    }

    private companion object {
        const val ROTATED = "rotated"

        /**
         * How long after a rotation the spent token is still taken for a retry. Long
         * enough for a lost response and a phone's second attempt, far too short for a
         * copy taken from a backup to be of use.
         */
        val REUSE_GRACE = 15.seconds
    }
}
