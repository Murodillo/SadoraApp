package uz.sadora.server.partner

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.sadora.contract.PartnerMessageKind
import uz.sadora.contract.PartnerPermissions
import uz.sadora.contract.PartnerWebLink
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.PartnerMessagesTable
import uz.sadora.server.db.PartnerWebLinks
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

/** A message as stored, before it is told whose screen it is drawn on. */
data class PartnerMessageRecord(
    val id: Uuid,
    val linkId: Uuid,
    val senderId: Uuid,
    val kind: PartnerMessageKind,
    val body: String?,
    val createdAt: Instant,
    val readAt: Instant?,
)

/** A web link with its owner, which the DTO does not carry. */
data class PartnerWebLinkRecord(val ownerId: Uuid, val link: PartnerWebLink, val revokedAt: Instant?) {
    fun isLive(at: Instant): Boolean = revokedAt == null && link.expiresAt > at
}

class PartnerMessageRepository {

    // ---------------------------------------------------------------- messages

    suspend fun add(linkId: Uuid, senderId: Uuid, kind: PartnerMessageKind, body: String?, at: Instant): PartnerMessageRecord =
        dbQuery {
            val id = Uuid.random()
            PartnerMessagesTable.insert {
                it[PartnerMessagesTable.id] = id
                it[PartnerMessagesTable.linkId] = linkId
                it[PartnerMessagesTable.senderId] = senderId
                it[PartnerMessagesTable.kind] = kind.dbValue()
                it[PartnerMessagesTable.body] = body
                it[createdAt] = at.toOffsetDateTime()
            }
            PartnerMessageRecord(id, linkId, senderId, kind, body, at, null)
        }

    suspend fun recent(linkId: Uuid, limit: Int): List<PartnerMessageRecord> = dbQuery {
        PartnerMessagesTable.selectAll()
            .where { PartnerMessagesTable.linkId eq linkId }
            .orderBy(PartnerMessagesTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toMessage() }
    }

    /** What the other one sent on [linkId] that [readerId] has not opened. */
    suspend fun unreadFor(linkId: Uuid, readerId: Uuid): Int = dbQuery {
        PartnerMessagesTable.selectAll()
            .where {
                (PartnerMessagesTable.linkId eq linkId) and
                    (PartnerMessagesTable.senderId neq readerId) and
                    PartnerMessagesTable.readAt.isNull()
            }
            .count()
            .toInt()
    }

    /** The same count for several links at once, for the follower's list. */
    suspend fun unreadFor(linkIds: List<Uuid>, readerId: Uuid): Map<Uuid, Int> = dbQuery {
        if (linkIds.isEmpty()) return@dbQuery emptyMap()
        PartnerMessagesTable.selectAll()
            .where {
                (PartnerMessagesTable.linkId inList linkIds) and
                    (PartnerMessagesTable.senderId neq readerId) and
                    PartnerMessagesTable.readAt.isNull()
            }
            .groupingBy { it[PartnerMessagesTable.linkId] }
            .eachCount()
    }

    suspend fun markRead(linkId: Uuid, readerId: Uuid, at: Instant): Int = dbQuery {
        PartnerMessagesTable.update({
            (PartnerMessagesTable.linkId eq linkId) and
                (PartnerMessagesTable.senderId neq readerId) and
                PartnerMessagesTable.readAt.isNull()
        }) {
            it[readAt] = at.toOffsetDateTime()
        }
    }

    suspend fun sentSince(linkId: Uuid, senderId: Uuid, since: Instant): Int = dbQuery {
        PartnerMessagesTable.selectAll()
            .where {
                (PartnerMessagesTable.linkId eq linkId) and
                    (PartnerMessagesTable.senderId eq senderId) and
                    (PartnerMessagesTable.createdAt greaterEq since.toOffsetDateTime())
            }
            .count()
            .toInt()
    }

    // ---------------------------------------------------------------- web links

    suspend fun createWebLink(
        ownerId: Uuid,
        tokenHash: String,
        permissions: PartnerPermissions,
        expiresAt: Instant,
        at: Instant,
    ): PartnerWebLink = dbQuery {
        val id = Uuid.random()
        PartnerWebLinks.insert {
            it[PartnerWebLinks.id] = id
            it[PartnerWebLinks.ownerId] = ownerId
            it[PartnerWebLinks.tokenHash] = tokenHash
            it[PartnerWebLinks.permissions] = permissions
            it[createdAt] = at.toOffsetDateTime()
            it[PartnerWebLinks.expiresAt] = expiresAt.toOffsetDateTime()
            it[viewCount] = 0
        }
        PartnerWebLink(id = id.toString(), permissions = permissions, createdAt = at, expiresAt = expiresAt)
    }

    /** Her newest link that is still live, if any. */
    suspend fun liveWebLinkOf(ownerId: Uuid, at: Instant): PartnerWebLink? = dbQuery {
        PartnerWebLinks.selectAll()
            .where { (PartnerWebLinks.ownerId eq ownerId) and PartnerWebLinks.revokedAt.isNull() }
            .orderBy(PartnerWebLinks.createdAt to SortOrder.DESC)
            .limit(1)
            .map { it.toWebLink() }
            .firstOrNull()
            ?.takeIf { it.link.expiresAt > at }
            ?.link
    }

    suspend fun webLinkByTokenHash(tokenHash: String): PartnerWebLinkRecord? = dbQuery {
        PartnerWebLinks.selectAll()
            .where { PartnerWebLinks.tokenHash eq tokenHash }
            .singleOrNull()
            ?.toWebLink()
    }

    suspend fun revokeWebLinks(ownerId: Uuid, at: Instant): Int = dbQuery {
        PartnerWebLinks.update({ (PartnerWebLinks.ownerId eq ownerId) and PartnerWebLinks.revokedAt.isNull() }) {
            it[revokedAt] = at.toOffsetDateTime()
        }
    }

    suspend fun recordWebView(id: Uuid, at: Instant): Unit = dbQuery {
        PartnerWebLinks.update({ PartnerWebLinks.id eq id }) {
            it[viewCount] = viewCount + 1
            it[lastViewedAt] = at.toOffsetDateTime()
        }
    }

    private fun ResultRow.toMessage() = PartnerMessageRecord(
        id = this[PartnerMessagesTable.id],
        linkId = this[PartnerMessagesTable.linkId],
        senderId = this[PartnerMessagesTable.senderId],
        kind = enumFromDb(this[PartnerMessagesTable.kind], PartnerMessageKind.CUSTOM),
        body = this[PartnerMessagesTable.body],
        createdAt = this[PartnerMessagesTable.createdAt].toKotlinInstant(),
        readAt = this[PartnerMessagesTable.readAt]?.toKotlinInstant(),
    )

    private fun ResultRow.toWebLink() = PartnerWebLinkRecord(
        ownerId = this[PartnerWebLinks.ownerId],
        link = PartnerWebLink(
            id = this[PartnerWebLinks.id].toString(),
            permissions = this[PartnerWebLinks.permissions],
            createdAt = this[PartnerWebLinks.createdAt].toKotlinInstant(),
            expiresAt = this[PartnerWebLinks.expiresAt].toKotlinInstant(),
            viewCount = this[PartnerWebLinks.viewCount],
            lastViewedAt = this[PartnerWebLinks.lastViewedAt]?.toKotlinInstant(),
        ),
        revokedAt = this[PartnerWebLinks.revokedAt]?.toKotlinInstant(),
    )
}
