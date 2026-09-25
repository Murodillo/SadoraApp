package uz.sadora.server.share

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.sadora.contract.ProfileShare
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.ProfileShares
import uz.sadora.server.db.dbQuery

/** A share row with its owner, which the DTO deliberately does not carry. */
data class ShareRecord(val userId: Uuid, val share: ProfileShare)

class ShareRepository {

    suspend fun create(userId: Uuid, tokenHash: String, expiresAt: Instant): ProfileShare = dbQuery {
        val id = Uuid.random()
        val createdAt = now()
        ProfileShares.insert {
            it[ProfileShares.id] = id
            it[ProfileShares.userId] = userId
            it[ProfileShares.tokenHash] = tokenHash
            it[ProfileShares.createdAt] = createdAt.toOffsetDateTime()
            it[ProfileShares.expiresAt] = expiresAt.toOffsetDateTime()
            it[viewCount] = 0
        }
        ProfileShare(id = id.toString(), createdAt = createdAt, expiresAt = expiresAt)
    }

    suspend fun listOf(userId: Uuid, limit: Int): List<ProfileShare> = dbQuery {
        ProfileShares.selectAll()
            .where { ProfileShares.userId eq userId }
            .orderBy(ProfileShares.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toShare() }
    }

    suspend fun byTokenHash(tokenHash: String): ShareRecord? = dbQuery {
        ProfileShares.selectAll()
            .where { ProfileShares.tokenHash eq tokenHash }
            .firstOrNull()
            ?.let { ShareRecord(it[ProfileShares.userId], it.toShare()) }
    }

    /** Revokes one share. False when it is not hers or already revoked. */
    suspend fun revoke(userId: Uuid, id: Uuid, at: Instant): Boolean = dbQuery {
        ProfileShares.update({
            (ProfileShares.id eq id) and (ProfileShares.userId eq userId) and ProfileShares.revokedAt.isNull()
        }) {
            it[revokedAt] = at.toOffsetDateTime()
        } > 0
    }

    /** Every live share she has, on sign-out or account deletion request. */
    suspend fun revokeAll(userId: Uuid, at: Instant): Int = dbQuery {
        ProfileShares.update({ (ProfileShares.userId eq userId) and ProfileShares.revokedAt.isNull() }) {
            it[revokedAt] = at.toOffsetDateTime()
        }
    }

    suspend fun recordView(id: Uuid, at: Instant): Unit = dbQuery {
        ProfileShares.update({ ProfileShares.id eq id }) {
            it[viewCount] = viewCount + 1
            it[lastViewedAt] = at.toOffsetDateTime()
        }
    }

    private fun ResultRow.toShare() = ProfileShare(
        id = this[ProfileShares.id].toString(),
        createdAt = this[ProfileShares.createdAt].toKotlinInstant(),
        expiresAt = this[ProfileShares.expiresAt].toKotlinInstant(),
        revokedAt = this[ProfileShares.revokedAt]?.toKotlinInstant(),
        viewCount = this[ProfileShares.viewCount],
        lastViewedAt = this[ProfileShares.lastViewedAt]?.toKotlinInstant(),
    )
}
