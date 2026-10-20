package uz.sadora.server.media

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.sadora.contract.MediaKind
import uz.sadora.contract.MessageMedia
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.CommunityConversations
import uz.sadora.server.db.CommunityMessageImages
import uz.sadora.server.db.CommunityMessages
import uz.sadora.server.db.ContentStatus
import uz.sadora.server.db.MessageMediaTable
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

enum class MediaStatus { UPLOADING, READY }

data class MediaRecord(
    val id: Uuid,
    val ownerId: Uuid,
    val messageId: Uuid?,
    val position: Int,
    val kind: MediaKind,
    val status: MediaStatus,
    val mimeType: String,
    val sizeBytes: Long,
    val thumbBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long?,
    val wrappedKey: String,
    val createdAt: Instant,
) {
    fun dto() = MessageMedia(
        id = id.toString(),
        kind = kind,
        width = width,
        height = height,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        hasThumbnail = thumbBytes > 0,
    )
}

class LegacyImage(val messageId: Uuid, val senderId: Uuid, val bytes: ByteArray)

/** Who may see an attached item: the two people of its thread, while its line is up. */
data class MediaPlace(
    val conversationId: Uuid,
    val userA: Uuid,
    val userB: Uuid,
    val messageStatus: ContentStatus,
)

class MediaRepository {

    suspend fun insertUpload(id: Uuid, ownerId: Uuid, kind: MediaKind, mimeType: String, sizeBytes: Long, wrappedKey: String, at: Instant): Unit = dbQuery {
        MessageMediaTable.insert {
            it[MessageMediaTable.id] = id
            it[MessageMediaTable.ownerId] = ownerId
            it[MessageMediaTable.kind] = kind.dbValue()
            it[status] = MediaStatus.UPLOADING.dbValue()
            it[MessageMediaTable.mimeType] = mimeType
            it[MessageMediaTable.sizeBytes] = sizeBytes
            it[thumbBytes] = 0
            it[width] = 0
            it[height] = 0
            it[position] = 0
            it[MessageMediaTable.wrappedKey] = wrappedKey
            it[createdAt] = at.toOffsetDateTime()
        }
    }

    suspend fun byId(id: Uuid): MediaRecord? = dbQuery {
        MessageMediaTable.selectAll().where { MessageMediaTable.id eq id }.singleOrNull()?.toRecord()
    }

    suspend fun byIds(ids: Collection<Uuid>): List<MediaRecord> = dbQuery {
        if (ids.isEmpty()) return@dbQuery emptyList()
        MessageMediaTable.selectAll().where { MessageMediaTable.id inList ids }.map { it.toRecord() }
    }

    suspend fun markReady(
        id: Uuid,
        mimeType: String,
        sizeBytes: Long,
        thumbBytes: Long,
        width: Int,
        height: Int,
        durationMs: Long?,
        at: Instant,
    ): Unit = dbQuery {
        MessageMediaTable.update({ MessageMediaTable.id eq id }) {
            it[status] = MediaStatus.READY.dbValue()
            it[MessageMediaTable.mimeType] = mimeType
            it[MessageMediaTable.sizeBytes] = sizeBytes
            it[MessageMediaTable.thumbBytes] = thumbBytes
            it[MessageMediaTable.width] = width
            it[MessageMediaTable.height] = height
            it[MessageMediaTable.durationMs] = durationMs
            it[readyAt] = at.toOffsetDateTime()
        }
    }

    /** An upload she has not sent: gone, row and all. Answers whether there was one. */
    suspend fun deleteUnattached(id: Uuid, ownerId: Uuid): Boolean = dbQuery {
        MessageMediaTable.deleteWhere {
            (MessageMediaTable.id eq id) and (MessageMediaTable.ownerId eq ownerId) and MessageMediaTable.messageId.isNull()
        } > 0
    }

    /** Each message's items, in the order they were sent. */
    suspend fun ofMessages(messageIds: Collection<Uuid>): Map<Uuid, List<MediaRecord>> = dbQuery {
        if (messageIds.isEmpty()) return@dbQuery emptyMap()
        MessageMediaTable.selectAll()
            .where { MessageMediaTable.messageId inList messageIds }
            .orderBy(MessageMediaTable.position to SortOrder.ASC)
            .map { it.toRecord() }
            .groupBy { it.messageId!! }
    }

    /** The thread an attached item sits in, and whether its line is still up. */
    suspend fun placeOf(messageId: Uuid): MediaPlace? = dbQuery {
        (CommunityMessages innerJoin CommunityConversations)
            .select(CommunityConversations.id, CommunityConversations.userA, CommunityConversations.userB, CommunityMessages.status)
            .where { CommunityMessages.id eq messageId }
            .singleOrNull()
            ?.let {
                MediaPlace(
                    conversationId = it[CommunityConversations.id],
                    userA = it[CommunityConversations.userA],
                    userB = it[CommunityConversations.userB],
                    messageStatus = enumFromDb(it[CommunityMessages.status], ContentStatus.VISIBLE),
                )
            }
    }

    /** Uploads nobody sent, started before [before]. */
    suspend fun staleUnattached(before: Instant, limit: Int): List<Uuid> = dbQuery {
        MessageMediaTable.select(MessageMediaTable.id)
            .where { MessageMediaTable.messageId.isNull() and (MessageMediaTable.createdAt less before.toOffsetDateTime()) }
            .limit(limit)
            .map { it[MessageMediaTable.id] }
    }

    suspend fun deleteRows(ids: Collection<Uuid>): Int = dbQuery {
        if (ids.isEmpty()) return@dbQuery 0
        MessageMediaTable.deleteWhere { (MessageMediaTable.id inList ids) and MessageMediaTable.messageId.isNull() }
    }

    /** Which of [ids] still have a row: the sweeper's question. */
    suspend fun known(ids: Collection<Uuid>): Set<Uuid> = dbQuery {
        if (ids.isEmpty()) return@dbQuery emptySet()
        MessageMediaTable.select(MessageMediaTable.id).where { MessageMediaTable.id inList ids }.mapTo(HashSet()) { it[MessageMediaTable.id] }
    }

    // ---------------------------------------------------------------- photos from before V69

    /** Up to [limit] photos still in `community_message_images`, but not [skip]: id, sender, bytes. */
    suspend fun legacyImages(limit: Int, skip: Set<Uuid>): List<LegacyImage> = dbQuery {
        (CommunityMessageImages innerJoin CommunityMessages)
            .select(CommunityMessageImages.messageId, CommunityMessages.senderId, CommunityMessageImages.content)
            .where { if (skip.isEmpty()) Op.TRUE else CommunityMessageImages.messageId notInList skip }
            .limit(limit)
            .map { LegacyImage(it[CommunityMessageImages.messageId], it[CommunityMessages.senderId], it[CommunityMessageImages.content]) }
    }

    /**
     * The photo of [image] becomes item [record], whose files are already written: the
     * row, the line's kind, and the old bytes go in one transaction.
     */
    suspend fun adoptLegacy(image: LegacyImage, record: MediaRecord, at: Instant): Unit = dbQuery {
        MessageMediaTable.insert {
            it[id] = record.id
            it[ownerId] = record.ownerId
            it[messageId] = image.messageId
            it[position] = 0
            it[kind] = MediaKind.PHOTO.dbValue()
            it[status] = MediaStatus.READY.dbValue()
            it[mimeType] = record.mimeType
            it[sizeBytes] = record.sizeBytes
            it[thumbBytes] = record.thumbBytes
            it[width] = record.width
            it[height] = record.height
            it[wrappedKey] = record.wrappedKey
            it[createdAt] = at.toOffsetDateTime()
            it[readyAt] = at.toOffsetDateTime()
            it[attachedAt] = at.toOffsetDateTime()
        }
        CommunityMessages.update({ CommunityMessages.id eq image.messageId }) {
            it[kind] = uz.sadora.contract.MessageKind.MEDIA.dbValue()
        }
        CommunityMessageImages.deleteWhere { CommunityMessageImages.messageId eq image.messageId }
    }

    private fun ResultRow.toRecord() = MediaRecord(
        id = this[MessageMediaTable.id],
        ownerId = this[MessageMediaTable.ownerId],
        messageId = this[MessageMediaTable.messageId],
        position = this[MessageMediaTable.position],
        kind = enumFromDb(this[MessageMediaTable.kind], MediaKind.PHOTO),
        status = enumFromDb(this[MessageMediaTable.status], MediaStatus.UPLOADING),
        mimeType = this[MessageMediaTable.mimeType],
        sizeBytes = this[MessageMediaTable.sizeBytes],
        thumbBytes = this[MessageMediaTable.thumbBytes],
        width = this[MessageMediaTable.width],
        height = this[MessageMediaTable.height],
        durationMs = this[MessageMediaTable.durationMs],
        wrappedKey = this[MessageMediaTable.wrappedKey],
        createdAt = this[MessageMediaTable.createdAt].toKotlinInstant(),
    )
}
