package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Clock
import uz.sadora.app.data.api.CommunityApi
import uz.sadora.app.model.Conversation
import uz.sadora.app.model.DirectMessage
import uz.sadora.app.model.ReportReason
import uz.sadora.contract.ReportReason as WireReason

/**
 * Private messages between aliases.
 *
 * The list and the open thread are both server truth: a send appends the server's
 * copy of the line rather than a guess, and a thread on screen is re-read every few
 * seconds by the screen so the other side's reply arrives without a pull. A thread
 * that has not been opened on the server yet — the first message to an alias — is
 * created by [send] through the start call, so the screen never has to know which.
 */
class MessagesController(
    private val api: CommunityApi?,
) {
    val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error
    val isOffline: Boolean get() = api == null

    fun clearError() = calls.clearError()

    var conversations by mutableStateOf<List<Conversation>>(emptyList())
        private set

    /** True once the list has been read from the server, so an empty list is real. */
    var loaded by mutableStateOf(false)
        private set

    /** The thread on screen, or null while nothing is open or the alias has no thread yet. */
    var current by mutableStateOf<Conversation?>(null)
        private set
    var messages by mutableStateOf<List<DirectMessage>>(emptyList())
        private set

    val unreadTotal: Int get() = conversations.sumOf { it.unread }

    suspend fun load() {
        val api = api ?: return
        calls.run(silent = loaded) { api.conversations() }?.let {
            conversations = it.map { thread -> thread.toAppConversation() }
            loaded = true
        }
    }

    /**
     * Opens a thread. With an id, the thread is read; with only an alias, the screen
     * starts empty and the first send creates it.
     */
    suspend fun open(conversationId: String?, alias: String) {
        current = conversationId?.let { id -> conversations.firstOrNull { it.id == id } }
        messages = emptyList()
        if (conversationId != null) refreshThread(conversationId) else {
            // Nothing on the server yet; the header still needs a name.
            current = Conversation(
                id = "",
                alias = alias,
                tint = 0,
                badges = emptyList(),
                lastMessage = null,
                lastMessageAt = Clock.System.now(),
                unread = 0,
                blocked = false,
            )
        }
    }

    /** Re-reads the open thread. Silent: a poll that fails is the next poll's problem. */
    suspend fun refreshThread(conversationId: String) {
        val api = api ?: return
        calls.run(silent = true) { api.thread(conversationId) }?.let { thread ->
            current = thread.conversation.toAppConversation()
            messages = thread.messages.map { it.toAppMessage() }
            // Opening it read it; the list's count follows without a reload.
            conversations = conversations.map { if (it.id == conversationId) it.copy(unread = 0) else it }
        }
    }

    fun close() {
        current = null
        messages = emptyList()
    }

    /** Sends into the open thread, or opens one with the alias when there is none. */
    suspend fun send(body: String): Boolean {
        val api = api ?: return true
        val thread = current ?: return false
        if (thread.id.isEmpty()) {
            val started = calls.run { api.startConversation(thread.alias, body) } ?: return false
            current = started.conversation.toAppConversation()
            messages = started.messages.map { it.toAppMessage() }
            load()
            return true
        }
        val sent = calls.run { api.sendMessage(thread.id, body) } ?: return false
        // The four-second poll can bring this message back before the send returns. The
        // list is keyed by id, and the same id twice is a crash, not a duplicate bubble.
        if (messages.none { it.id == sent.id }) messages = messages + sent.toAppMessage()
        conversations = conversations.map {
            if (it.id == thread.id) it.copy(lastMessage = sent.body, lastMessageAt = sent.createdAt) else it
        }
        return true
    }

    suspend fun report(reason: ReportReason, note: String?): Boolean {
        val api = api ?: return true
        val thread = current?.takeIf { it.id.isNotEmpty() } ?: return false
        return calls.run { api.reportConversation(thread.id, reason.toWire(), note) } != null
    }

    private fun ReportReason.toWire(): WireReason = when (this) {
        ReportReason.Spam -> WireReason.SPAM
        ReportReason.Abuse -> WireReason.ABUSE
        ReportReason.Misinformation -> WireReason.MISINFORMATION
        ReportReason.PersonalData -> WireReason.PERSONAL_DATA
        ReportReason.Other -> WireReason.OTHER
    }
}
