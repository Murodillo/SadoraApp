package uz.sadora.server.community

import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid
import uz.sadora.contract.Conversation
import uz.sadora.contract.ConversationThread
import uz.sadora.contract.DirectMessage
import uz.sadora.contract.Limits
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.NotificationStatus
import uz.sadora.contract.ReportRequest
import uz.sadora.contract.SendMessageRequest
import uz.sadora.contract.StartConversationRequest
import uz.sadora.server.core.ConflictException
import uz.sadora.server.core.ForbiddenException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.RateLimitedException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.now
import uz.sadora.server.db.ContentStatus
import uz.sadora.server.notify.NotificationRepository

/**
 * Private messages between two aliases.
 *
 * The same gates as the feed — the room must be open to her, and a silenced author
 * cannot write — plus three of its own. Nobody messages herself. A closed door
 * (`dmOpen`) or a block on either side refuses the thread with the one word "not
 * available", so a blocked sender learns nothing about which it was. And there is a
 * per-day ceiling, because a private channel is where spam goes when the feed is
 * moderated.
 *
 * The push a message earns names the alias and nothing else; the phone that receives
 * it is looked up by account, inside the outbox, and never appears here.
 */
class MessagingService(
    private val messages: MessagingRepository,
    private val community: CommunityService,
    private val identities: CommunityRepository,
    private val notifications: NotificationRepository,
) {

    suspend fun conversations(userId: Uuid): List<Conversation> {
        community.openIdentity(userId)
        val threads = messages.conversationsOf(userId, MAX_THREADS)
        if (threads.isEmpty()) return emptyList()
        val others = threads.map { it.other(userId) }
        val names = identities.identitiesFor(others)
        val stats = identities.activityFor(others)
        val last = messages.lastMessages(threads.map { it.id })
        val unread = messages.unreadCounts(threads, userId)
        val at = now()
        return threads.map { thread ->
            val other = thread.other(userId)
            thread.toDto(
                identity = names[other],
                badges = stats[other]?.let { CommunityBadges.of(it, at) }.orEmpty(),
                last = last[thread.id],
                unread = unread[thread.id] ?: 0,
                blocked = identities.blockedEitherWay(userId, other),
            )
        }
    }

    /** Opens the thread and marks it read: what is on screen has been seen. */
    suspend fun thread(userId: Uuid, conversationId: Uuid): ConversationThread {
        community.openIdentity(userId)
        val thread = requireParticipant(userId, conversationId)
        val other = thread.other(userId)
        val identity = identities.identitiesFor(listOf(other))[other]
        val stats = identities.activityFor(listOf(other))[other]
        val lines = messages.messagesOf(conversationId, MAX_MESSAGES)
        messages.markRead(thread, userId)
        return ConversationThread(
            conversation = thread.toDto(
                identity = identity,
                badges = stats?.let { CommunityBadges.of(it, now()) }.orEmpty(),
                last = lines.lastOrNull(),
                unread = 0,
                blocked = identities.blockedEitherWay(userId, other),
            ),
            messages = lines.map { it.toDto(userId) },
        )
    }

    /** Finds or opens the thread with [StartConversationRequest.alias] and sends the first line. */
    suspend fun start(userId: Uuid, request: StartConversationRequest): ConversationThread {
        val me = community.openIdentity(userId)
        val target = identities.identityByAlias(request.alias.trim()) ?: throw NotFoundException("Taxallus topilmadi")
        if (target.userId == me.userId) throw ValidationException("alias", "O'zingizga xabar yozib bo'lmaydi")
        if (!target.dmOpen || identities.blockedEitherWay(userId, target.userId)) {
            throw ForbiddenException(message = UNAVAILABLE)
        }
        val thread = messages.openConversation(userId, target.userId)
        send(userId, thread, request.body)
        return thread(userId, thread.id)
    }

    suspend fun send(userId: Uuid, conversationId: Uuid, request: SendMessageRequest): DirectMessage {
        community.openIdentity(userId)
        val thread = requireParticipant(userId, conversationId)
        if (identities.blockedEitherWay(userId, thread.other(userId))) throw ForbiddenException(message = UNAVAILABLE)
        return send(userId, thread, request.body).toDto(userId)
    }

    private suspend fun send(userId: Uuid, thread: ConversationRecord, rawBody: String): MessageRecord {
        community.requireCanWrite(userId)
        val body = rawBody.trim()
        if (body.isEmpty()) throw ValidationException("body", "Xabar bo'sh bo'lishi mumkin emas")
        if (body.length > Limits.MESSAGE_MAX) throw ValidationException("body", "Eng ko'pi ${Limits.MESSAGE_MAX} belgi")
        if (messages.messagesSince(userId, now() - 24.hours) >= MAX_MESSAGES_PER_DAY) {
            throw RateLimitedException("Bir kunda $MAX_MESSAGES_PER_DAY tadan ko'p xabar yozib bo'lmaydi")
        }
        val message = messages.insertMessage(thread.id, userId, body)
        notify(recipient = thread.other(userId), sender = userId, message = message)
        return message
    }

    /**
     * Files a report on the other side's latest message. Reports are what a moderator
     * reads, so the line reported is the one she can see, not the whole thread.
     */
    suspend fun report(userId: Uuid, conversationId: Uuid, request: ReportRequest) {
        community.openIdentity(userId)
        val thread = requireParticipant(userId, conversationId)
        val target = messages.latestFrom(conversationId, thread.other(userId))
            ?: throw ValidationException("conversationId", "Shikoyat qiladigan xabar yo'q")
        val note = request.note?.trim()?.take(Limits.REPORT_NOTE_MAX)
        if (!identities.addReport(userId, null, null, request.reason, note, messageId = target.id)) {
            throw ConflictException("Bu xabar allaqachon shikoyat qilingan")
        }
    }

    private suspend fun notify(recipient: Uuid, sender: Uuid, message: MessageRecord) {
        val alias = identities.identitiesFor(listOf(sender))[sender]?.alias ?: CommunityService.FALLBACK_ALIAS
        notifications.enqueue(
            userId = recipient,
            category = NotificationCategory.SYSTEM,
            title = "$alias: yangi xabar",
            body = message.body.take(PUSH_PREVIEW),
            scheduledFor = message.createdAt,
            dedupeKey = "dm:${message.id}",
            status = NotificationStatus.QUEUED,
            suppressedReason = null,
        )
    }

    private suspend fun requireParticipant(userId: Uuid, conversationId: Uuid): ConversationRecord {
        val thread = messages.conversationById(conversationId)
        // Not hers reads the same as not there: a thread id is not a thing to probe.
        if (thread == null || !thread.has(userId)) throw NotFoundException("Suhbat topilmadi")
        return thread
    }

    private fun ConversationRecord.toDto(
        identity: IdentityRecord?,
        badges: List<uz.sadora.contract.CommunityBadge>,
        last: MessageRecord?,
        unread: Int,
        blocked: Boolean,
    ) = Conversation(
        id = id.toString(),
        alias = identity?.alias ?: CommunityService.FALLBACK_ALIAS,
        tint = identity?.tint ?: 0,
        badges = badges,
        lastMessage = last?.body?.take(PREVIEW_LENGTH),
        lastMessageAt = last?.createdAt ?: lastMessageAt,
        unread = unread,
        blocked = blocked,
    )

    private fun MessageRecord.toDto(viewer: Uuid) = DirectMessage(
        id = id.toString(),
        body = body,
        createdAt = createdAt,
        isMine = senderId == viewer,
    )

    companion object {
        const val MAX_THREADS = 100
        const val MAX_MESSAGES = 200
        const val MAX_MESSAGES_PER_DAY = 200
        const val PREVIEW_LENGTH = 120
        const val PUSH_PREVIEW = 80
        const val UNAVAILABLE = "Bu taxallusga xabar yozib bo'lmaydi"
    }
}
