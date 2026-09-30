package uz.sadora.server.community

import kotlin.time.Instant
import kotlin.uuid.Uuid
import uz.sadora.contract.MessageKind
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.CommunityConversations
import uz.sadora.server.db.CommunityMessageImages
import uz.sadora.server.db.CommunityReports
import uz.sadora.server.db.CommunityMessages
import uz.sadora.server.db.ContentStatus
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb
import uz.sadora.server.doctor.DoctorConsultationStats

data class ConversationRecord(
    val id: Uuid,
    val userA: Uuid,
    val userB: Uuid,
    val createdAt: Instant,
    val lastMessageAt: Instant,
    val aReadAt: Instant?,
    val bReadAt: Instant?,
    /** Set on a consultation: the doctor profile it is held under. */
    val doctorId: Uuid? = null,
    val openedAt: Instant? = null,
    val expiresAt: Instant? = null,
    val closedAt: Instant? = null,
) {
    fun other(userId: Uuid): Uuid = if (userId == userA) userB else userA
    fun readAt(userId: Uuid): Instant? = if (userId == userA) aReadAt else bReadAt
    fun has(userId: Uuid): Boolean = userId == userA || userId == userB
    val isConsultation: Boolean get() = doctorId != null

    /** Takes messages: an alias thread always, a consultation inside its window. */
    fun isOpen(at: Instant): Boolean =
        doctorId == null || (closedAt == null && expiresAt != null && expiresAt > at)
}

/** The picture of an `image` message. */
class MessageImageRecord(
    val mimeType: String,
    val width: Int,
    val height: Int,
    val bytes: ByteArray,
)

data class MessageRecord(
    val id: Uuid,
    val conversationId: Uuid,
    val senderId: Uuid,
    val body: String,
    val status: ContentStatus,
    val createdAt: Instant,
    val kind: MessageKind = MessageKind.TEXT,
    /** Width and height of the photo, for an `image` message; read with the thread. */
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val imageMime: String? = null,
)

/** Which of her threads a list asks for; see [MessagingRepository.conversationsOf]. */
enum class ConversationScope {
    /** Everything she is part of. */
    ALL,

    /** The consultations she holds as a doctor — the doctor app's list. */
    PATIENTS,

    /** Everything but those: her alias threads and her own consultations with doctors. */
    PERSONAL,
}

data class MessagingStats(
    val messagesToday: Long,
    val conversations: Long,
    val consultations: Long,
    val openConsultations: Long,
    val openMessageReports: Long,
)

/**
 * Private threads between two accounts.
 *
 * A pair is one row whichever side opened it: the two ids are ordered before the
 * lookup, so "the conversation between these two" is a unique-key hit and two people
 * writing to each other at the same moment cannot open two threads.
 */
class MessagingRepository {

    /** The pair in the order the table keeps it. Uuid compares as a string. */
    private fun ordered(a: Uuid, b: Uuid): Pair<Uuid, Uuid> =
        if (a.toString() < b.toString()) a to b else b to a

    /** The alias thread between two accounts — not a consultation they may also share. */
    suspend fun conversationBetween(a: Uuid, b: Uuid): ConversationRecord? = dbQuery {
        val (first, second) = ordered(a, b)
        CommunityConversations.selectAll()
            .where {
                (CommunityConversations.userA eq first) and (CommunityConversations.userB eq second) and
                    CommunityConversations.doctorId.isNull()
            }
            .singleOrNull()
            ?.toConversation()
    }

    /** The patient's consultation with the doctor behind [doctorId], if she ever opened one. */
    suspend fun consultationBetween(patient: Uuid, doctorUser: Uuid, doctorId: Uuid): ConversationRecord? = dbQuery {
        val (first, second) = ordered(patient, doctorUser)
        CommunityConversations.selectAll()
            .where {
                (CommunityConversations.userA eq first) and (CommunityConversations.userB eq second) and
                    (CommunityConversations.doctorId eq doctorId)
            }
            .singleOrNull()
            ?.toConversation()
    }

    /**
     * Opens the consultation, or opens it again: a fresh window from now, whatever the
     * old one was. Called only when it is not open, so a running window is never reset.
     */
    suspend fun openConsultation(patient: Uuid, doctorUser: Uuid, doctorId: Uuid, window: kotlin.time.Duration): ConversationRecord = dbQuery {
        val (first, second) = ordered(patient, doctorUser)
        val timestamp = now()
        val existing = CommunityConversations.selectAll()
            .where {
                (CommunityConversations.userA eq first) and (CommunityConversations.userB eq second) and
                    (CommunityConversations.doctorId eq doctorId)
            }
            .singleOrNull()
            ?.toConversation()
        if (existing != null) {
            CommunityConversations.update({ CommunityConversations.id eq existing.id }) {
                it[openedAt] = timestamp.toOffsetDateTime()
                it[expiresAt] = (timestamp + window).toOffsetDateTime()
                it[closedAt] = null
                it[lastMessageAt] = timestamp.toOffsetDateTime()
            }
            existing.copy(openedAt = timestamp, expiresAt = timestamp + window, closedAt = null, lastMessageAt = timestamp)
        } else {
            val id = Uuid.random()
            CommunityConversations.insert {
                it[CommunityConversations.id] = id
                it[userA] = first
                it[userB] = second
                it[createdAt] = timestamp.toOffsetDateTime()
                it[lastMessageAt] = timestamp.toOffsetDateTime()
                it[CommunityConversations.doctorId] = doctorId
                it[openedAt] = timestamp.toOffsetDateTime()
                it[expiresAt] = (timestamp + window).toOffsetDateTime()
            }
            ConversationRecord(id, first, second, timestamp, timestamp, null, null, doctorId, timestamp, timestamp + window, null)
        }
    }

    /**
     * The consultation's row without opening a window: what a paid consultation hangs
     * its checkout on. An existing row is returned as it is, open or not.
     */
    suspend fun ensureConsultation(patient: Uuid, doctorUser: Uuid, doctorId: Uuid): ConversationRecord = dbQuery {
        val (first, second) = ordered(patient, doctorUser)
        CommunityConversations.selectAll()
            .where {
                (CommunityConversations.userA eq first) and (CommunityConversations.userB eq second) and
                    (CommunityConversations.doctorId eq doctorId)
            }
            .singleOrNull()
            ?.toConversation()
            ?: run {
                val id = Uuid.random()
                val timestamp = now()
                CommunityConversations.insert {
                    it[CommunityConversations.id] = id
                    it[userA] = first
                    it[userB] = second
                    it[createdAt] = timestamp.toOffsetDateTime()
                    it[lastMessageAt] = timestamp.toOffsetDateTime()
                    it[CommunityConversations.doctorId] = doctorId
                }
                ConversationRecord(id, first, second, timestamp, timestamp, null, null, doctorId)
            }
    }

    suspend fun closeConsultation(id: Uuid): Unit = dbQuery {
        CommunityConversations.update({ CommunityConversations.id eq id }) {
            it[closedAt] = now().toOffsetDateTime()
        }
    }

    suspend fun conversationById(id: Uuid): ConversationRecord? = dbQuery {
        CommunityConversations.selectAll().where { CommunityConversations.id eq id }.singleOrNull()?.toConversation()
    }

    suspend fun openConversation(a: Uuid, b: Uuid): ConversationRecord = dbQuery {
        val (first, second) = ordered(a, b)
        CommunityConversations.selectAll()
            .where {
                (CommunityConversations.userA eq first) and (CommunityConversations.userB eq second) and
                    CommunityConversations.doctorId.isNull()
            }
            .singleOrNull()
            ?.toConversation()
            ?: run {
                val id = Uuid.random()
                val timestamp = now()
                CommunityConversations.insert {
                    it[CommunityConversations.id] = id
                    it[userA] = first
                    it[userB] = second
                    it[createdAt] = timestamp.toOffsetDateTime()
                    it[lastMessageAt] = timestamp.toOffsetDateTime()
                }
                ConversationRecord(id, first, second, timestamp, timestamp, null, null)
            }
    }

    /**
     * Her threads, most recently written first. [doctorId] is her own doctor profile:
     * [scope] uses it to tell the consultations she holds as a doctor from her own.
     */
    suspend fun conversationsOf(
        userId: Uuid,
        limit: Int,
        scope: ConversationScope = ConversationScope.ALL,
        doctorId: Uuid? = null,
    ): List<ConversationRecord> = dbQuery {
        var query = CommunityConversations.selectAll()
            .where { (CommunityConversations.userA eq userId) or (CommunityConversations.userB eq userId) }
        when (scope) {
            ConversationScope.ALL -> Unit
            ConversationScope.PATIENTS ->
                query = if (doctorId == null) {
                    return@dbQuery emptyList()
                } else {
                    query.andWhere { CommunityConversations.doctorId eq doctorId }
                }
            ConversationScope.PERSONAL -> if (doctorId != null) {
                query = query.andWhere {
                    CommunityConversations.doctorId.isNull() or (CommunityConversations.doctorId neq doctorId)
                }
            }
        }
        query.orderBy(CommunityConversations.lastMessageAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toConversation() }
    }

    suspend fun insertMessage(
        conversationId: Uuid,
        senderId: Uuid,
        body: String,
        kind: MessageKind = MessageKind.TEXT,
        image: MessageImageRecord? = null,
    ): MessageRecord = dbQuery {
        val id = Uuid.random()
        val timestamp = now()
        CommunityMessages.insert {
            it[CommunityMessages.id] = id
            it[CommunityMessages.conversationId] = conversationId
            it[CommunityMessages.senderId] = senderId
            it[CommunityMessages.body] = body
            it[CommunityMessages.kind] = kind.dbValue()
            it[status] = ContentStatus.VISIBLE.dbValue()
            it[createdAt] = timestamp.toOffsetDateTime()
        }
        if (image != null) {
            CommunityMessageImages.insert {
                it[messageId] = id
                it[mimeType] = image.mimeType
                it[width] = image.width
                it[height] = image.height
                it[content] = image.bytes
                it[createdAt] = timestamp.toOffsetDateTime()
            }
        }
        CommunityConversations.update({ CommunityConversations.id eq conversationId }) {
            it[lastMessageAt] = timestamp.toOffsetDateTime()
        }
        MessageRecord(
            id, conversationId, senderId, body, ContentStatus.VISIBLE, timestamp, kind,
            image?.width, image?.height, image?.mimeType,
        )
    }

    /** The visible messages of a thread, oldest first, the last [limit] of them, with photo sizes. */
    suspend fun messagesOf(conversationId: Uuid, limit: Int): List<MessageRecord> = dbQuery {
        val lines = CommunityMessages.selectAll()
            .where {
                (CommunityMessages.conversationId eq conversationId) and
                    (CommunityMessages.status eq ContentStatus.VISIBLE.dbValue())
            }
            .orderBy(CommunityMessages.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toMessage() }
            .asReversed()
        withImageSizes(lines)
    }

    /**
     * Every line around [messageId] in its thread, hidden ones included — the context a
     * moderator reads a report in. [radius] lines each side.
     */
    suspend fun contextOf(messageId: Uuid, radius: Int): List<MessageRecord> = dbQuery {
        val target = CommunityMessages.selectAll().where { CommunityMessages.id eq messageId }.singleOrNull()?.toMessage()
            ?: return@dbQuery emptyList()
        val before = CommunityMessages.selectAll()
            .where {
                (CommunityMessages.conversationId eq target.conversationId) and
                    (CommunityMessages.createdAt less target.createdAt.toOffsetDateTime())
            }
            .orderBy(CommunityMessages.createdAt to SortOrder.DESC)
            .limit(radius)
            .map { it.toMessage() }
            .asReversed()
        val after = CommunityMessages.selectAll()
            .where {
                (CommunityMessages.conversationId eq target.conversationId) and
                    (CommunityMessages.createdAt greater target.createdAt.toOffsetDateTime())
            }
            .orderBy(CommunityMessages.createdAt to SortOrder.ASC)
            .limit(radius)
            .map { it.toMessage() }
        withImageSizes(before + target + after)
    }

    private fun withImageSizes(lines: List<MessageRecord>): List<MessageRecord> {
        val ids = lines.filter { it.kind == MessageKind.IMAGE }.map { it.id }
        if (ids.isEmpty()) return lines
        val sizes = CommunityMessageImages
            .select(CommunityMessageImages.messageId, CommunityMessageImages.width, CommunityMessageImages.height, CommunityMessageImages.mimeType)
            .where { CommunityMessageImages.messageId inList ids }
            .associate {
                it[CommunityMessageImages.messageId] to
                    Triple(it[CommunityMessageImages.width], it[CommunityMessageImages.height], it[CommunityMessageImages.mimeType])
            }
        return lines.map { line ->
            sizes[line.id]?.let { (w, h, mime) -> line.copy(imageWidth = w, imageHeight = h, imageMime = mime) } ?: line
        }
    }

    suspend fun imageOf(messageId: Uuid): MessageImageRecord? = dbQuery {
        CommunityMessageImages.selectAll()
            .where { CommunityMessageImages.messageId eq messageId }
            .singleOrNull()
            ?.let {
                MessageImageRecord(
                    it[CommunityMessageImages.mimeType],
                    it[CommunityMessageImages.width],
                    it[CommunityMessageImages.height],
                    it[CommunityMessageImages.content],
                )
            }
    }

    // ---------------------------------------------------------------- moderation

    /** Who filed a message report, and on which message — never leaves the server. */
    suspend fun messageReport(reportId: Uuid): Pair<Uuid, Uuid>? = dbQuery {
        CommunityReports.select(CommunityReports.reporterId, CommunityReports.messageId)
            .where { CommunityReports.id eq reportId }
            .singleOrNull()
            ?.let { row -> row[CommunityReports.messageId]?.let { row[CommunityReports.reporterId] to it } }
    }

    // ---------------------------------------------------------------- aggregates for the admin

    /** Counts only: the staff panel sees how much is said, never what. */
    suspend fun messagingStats(since: Instant): MessagingStats = dbQuery {
        val open = now().toOffsetDateTime()
        MessagingStats(
            messagesToday = CommunityMessages.selectAll()
                .where { CommunityMessages.createdAt greaterEq since.toOffsetDateTime() }
                .count(),
            conversations = CommunityConversations.selectAll().where { CommunityConversations.doctorId.isNull() }.count(),
            consultations = CommunityConversations.selectAll().where { CommunityConversations.doctorId.isNotNull() }.count(),
            openConsultations = CommunityConversations.selectAll()
                .where {
                    CommunityConversations.doctorId.isNotNull() and CommunityConversations.closedAt.isNull() and
                        (CommunityConversations.expiresAt greater open)
                }
                .count(),
            openMessageReports = CommunityReports.selectAll()
                .where { CommunityReports.messageId.isNotNull() and CommunityReports.resolvedAt.isNull() }
                .count(),
        )
    }

    /** One doctor's consultations, counted, for her card in the staff panel. */
    suspend fun consultationStats(doctorId: Uuid, doctorUser: Uuid): DoctorConsultationStats = dbQuery {
        val rows = CommunityConversations.selectAll()
            .where { CommunityConversations.doctorId eq doctorId }
            .map { it.toConversation() }
        val at = now()
        val ids = rows.map { it.id }
        val fromDoctor = if (ids.isEmpty()) 0L else CommunityMessages.selectAll()
            .where { (CommunityMessages.conversationId inList ids) and (CommunityMessages.senderId eq doctorUser) }
            .count()
        val fromPatients = if (ids.isEmpty()) 0L else CommunityMessages.selectAll()
            .where { (CommunityMessages.conversationId inList ids) and (CommunityMessages.senderId neq doctorUser) }
            .count()
        DoctorConsultationStats(
            total = rows.size.toLong(),
            open = rows.count { it.isOpen(at) }.toLong(),
            messagesFromDoctor = fromDoctor,
            messagesFromPatients = fromPatients,
            lastMessageAt = rows.maxOfOrNull { it.lastMessageAt },
        )
    }

    /** The other side's latest visible message — what a report on the thread points at. */
    suspend fun latestFrom(conversationId: Uuid, senderId: Uuid): MessageRecord? = dbQuery {
        CommunityMessages.selectAll()
            .where {
                (CommunityMessages.conversationId eq conversationId) and
                    (CommunityMessages.senderId eq senderId) and
                    (CommunityMessages.status eq ContentStatus.VISIBLE.dbValue())
            }
            .orderBy(CommunityMessages.createdAt to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.toMessage()
    }

    suspend fun messageById(id: Uuid): MessageRecord? = dbQuery {
        CommunityMessages.selectAll().where { CommunityMessages.id eq id }.singleOrNull()?.toMessage()
    }

    /** The last line of each thread, for the list. One query for the page. */
    suspend fun lastMessages(conversationIds: List<Uuid>): Map<Uuid, MessageRecord> = dbQuery {
        if (conversationIds.isEmpty()) return@dbQuery emptyMap()
        // Newest first, keep the first per thread. A thread has few messages; fine.
        val result = linkedMapOf<Uuid, MessageRecord>()
        conversationIds.forEach { id ->
            CommunityMessages.selectAll()
                .where { (CommunityMessages.conversationId eq id) and (CommunityMessages.status eq ContentStatus.VISIBLE.dbValue()) }
                .orderBy(CommunityMessages.createdAt to SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.let { result[id] = it.toMessage() }
        }
        result
    }

    /** Messages from the other side that arrived after her read mark. */
    suspend fun unreadCount(conversation: ConversationRecord, userId: Uuid): Int = dbQuery {
        unreadIn(conversation, userId)
    }

    suspend fun unreadCounts(conversations: List<ConversationRecord>, userId: Uuid): Map<Uuid, Int> = dbQuery {
        conversations.associate { it.id to unreadIn(it, userId) }
    }

    /**
     * Everything unread across her own threads, for the badge on the chat header. The
     * consultations she holds as a doctor ([doctorId]) are her doctor app's, not counted here.
     */
    suspend fun unreadTotal(userId: Uuid, doctorId: Uuid? = null): Int = dbQuery {
        CommunityConversations.selectAll()
            .where { (CommunityConversations.userA eq userId) or (CommunityConversations.userB eq userId) }
            .map { it.toConversation() }
            .filter { doctorId == null || it.doctorId != doctorId }
            .sumOf { unreadIn(it, userId) }
    }

    private fun unreadIn(conversation: ConversationRecord, userId: Uuid): Int {
        val readAt = conversation.readAt(userId)
        var query = CommunityMessages.selectAll()
            .where {
                (CommunityMessages.conversationId eq conversation.id) and
                    (CommunityMessages.senderId neq userId) and
                    (CommunityMessages.status eq ContentStatus.VISIBLE.dbValue())
            }
        if (readAt != null) {
            query = query.andWhere { CommunityMessages.createdAt greater readAt.toOffsetDateTime() }
        }
        return query.count().toInt()
    }

    suspend fun markRead(conversation: ConversationRecord, userId: Uuid): Unit = dbQuery {
        val timestamp = now().toOffsetDateTime()
        CommunityConversations.update({ CommunityConversations.id eq conversation.id }) {
            if (userId == conversation.userA) it[aReadAt] = timestamp else it[bReadAt] = timestamp
        }
    }

    suspend fun messagesSince(senderId: Uuid, since: Instant): Long = dbQuery {
        CommunityMessages.selectAll()
            .where { (CommunityMessages.senderId eq senderId) and (CommunityMessages.createdAt greaterEq since.toOffsetDateTime()) }
            .count()
    }

    suspend fun setMessageStatus(id: Uuid, status: ContentStatus, reason: String?): Boolean = dbQuery {
        CommunityMessages.update({ CommunityMessages.id eq id }) {
            it[CommunityMessages.status] = status.dbValue()
            it[hiddenReason] = reason
        } > 0
    }

    private fun ResultRow.toConversation() = ConversationRecord(
        id = this[CommunityConversations.id],
        userA = this[CommunityConversations.userA],
        userB = this[CommunityConversations.userB],
        createdAt = this[CommunityConversations.createdAt].toKotlinInstant(),
        lastMessageAt = this[CommunityConversations.lastMessageAt].toKotlinInstant(),
        aReadAt = this[CommunityConversations.aReadAt]?.toKotlinInstant(),
        bReadAt = this[CommunityConversations.bReadAt]?.toKotlinInstant(),
        doctorId = this[CommunityConversations.doctorId],
        openedAt = this[CommunityConversations.openedAt]?.toKotlinInstant(),
        expiresAt = this[CommunityConversations.expiresAt]?.toKotlinInstant(),
        closedAt = this[CommunityConversations.closedAt]?.toKotlinInstant(),
    )

    private fun ResultRow.toMessage() = MessageRecord(
        id = this[CommunityMessages.id],
        conversationId = this[CommunityMessages.conversationId],
        senderId = this[CommunityMessages.senderId],
        body = this[CommunityMessages.body],
        status = enumFromDb(this[CommunityMessages.status], ContentStatus.VISIBLE),
        createdAt = this[CommunityMessages.createdAt].toKotlinInstant(),
        kind = enumFromDb(this[CommunityMessages.kind], MessageKind.TEXT),
    )
}
