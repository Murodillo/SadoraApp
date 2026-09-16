package uz.sadora.server.community

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
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
import uz.sadora.server.db.CommunityMessages
import uz.sadora.server.db.ContentStatus
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

data class ConversationRecord(
    val id: Uuid,
    val userA: Uuid,
    val userB: Uuid,
    val createdAt: Instant,
    val lastMessageAt: Instant,
    val aReadAt: Instant?,
    val bReadAt: Instant?,
) {
    fun other(userId: Uuid): Uuid = if (userId == userA) userB else userA
    fun readAt(userId: Uuid): Instant? = if (userId == userA) aReadAt else bReadAt
    fun has(userId: Uuid): Boolean = userId == userA || userId == userB
}

data class MessageRecord(
    val id: Uuid,
    val conversationId: Uuid,
    val senderId: Uuid,
    val body: String,
    val status: ContentStatus,
    val createdAt: Instant,
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

    suspend fun conversationBetween(a: Uuid, b: Uuid): ConversationRecord? = dbQuery {
        val (first, second) = ordered(a, b)
        CommunityConversations.selectAll()
            .where { (CommunityConversations.userA eq first) and (CommunityConversations.userB eq second) }
            .singleOrNull()
            ?.toConversation()
    }

    suspend fun conversationById(id: Uuid): ConversationRecord? = dbQuery {
        CommunityConversations.selectAll().where { CommunityConversations.id eq id }.singleOrNull()?.toConversation()
    }

    suspend fun openConversation(a: Uuid, b: Uuid): ConversationRecord = dbQuery {
        val (first, second) = ordered(a, b)
        CommunityConversations.selectAll()
            .where { (CommunityConversations.userA eq first) and (CommunityConversations.userB eq second) }
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

    /** Her threads, most recently written first. */
    suspend fun conversationsOf(userId: Uuid, limit: Int): List<ConversationRecord> = dbQuery {
        CommunityConversations.selectAll()
            .where { (CommunityConversations.userA eq userId) or (CommunityConversations.userB eq userId) }
            .orderBy(CommunityConversations.lastMessageAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toConversation() }
    }

    suspend fun insertMessage(conversationId: Uuid, senderId: Uuid, body: String): MessageRecord = dbQuery {
        val id = Uuid.random()
        val timestamp = now()
        CommunityMessages.insert {
            it[CommunityMessages.id] = id
            it[CommunityMessages.conversationId] = conversationId
            it[CommunityMessages.senderId] = senderId
            it[CommunityMessages.body] = body
            it[status] = ContentStatus.VISIBLE.dbValue()
            it[createdAt] = timestamp.toOffsetDateTime()
        }
        CommunityConversations.update({ CommunityConversations.id eq conversationId }) {
            it[lastMessageAt] = timestamp.toOffsetDateTime()
        }
        MessageRecord(id, conversationId, senderId, body, ContentStatus.VISIBLE, timestamp)
    }

    /** The visible messages of a thread, oldest first, the last [limit] of them. */
    suspend fun messagesOf(conversationId: Uuid, limit: Int): List<MessageRecord> = dbQuery {
        CommunityMessages.selectAll()
            .where {
                (CommunityMessages.conversationId eq conversationId) and
                    (CommunityMessages.status eq ContentStatus.VISIBLE.dbValue())
            }
            .orderBy(CommunityMessages.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toMessage() }
            .asReversed()
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

    /** Everything unread across her threads, for the badge on the chat header. */
    suspend fun unreadTotal(userId: Uuid): Int = dbQuery {
        CommunityConversations.selectAll()
            .where { (CommunityConversations.userA eq userId) or (CommunityConversations.userB eq userId) }
            .map { it.toConversation() }
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
    )

    private fun ResultRow.toMessage() = MessageRecord(
        id = this[CommunityMessages.id],
        conversationId = this[CommunityMessages.conversationId],
        senderId = this[CommunityMessages.senderId],
        body = this[CommunityMessages.body],
        status = enumFromDb(this[CommunityMessages.status], ContentStatus.VISIBLE),
        createdAt = this[CommunityMessages.createdAt].toKotlinInstant(),
    )
}
