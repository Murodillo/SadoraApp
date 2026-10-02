package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uz.sadora.app.data.api.CommunityApi
import uz.sadora.app.model.Conversation
import uz.sadora.app.model.DirectMessage
import uz.sadora.app.model.MessageKind
import uz.sadora.app.model.ReportReason
import uz.sadora.contract.ConversationThread
import uz.sadora.contract.DoctorSummary
import uz.sadora.contract.MessageImageUpload
import uz.sadora.contract.ReportReason as WireReason
import uz.sadora.contract.SendMessageRequest
import uz.sadora.contract.DirectMessage as WireMessage

/**
 * Private messages: between aliases, and her consultations with verified doctors.
 *
 * The list and the open thread are both server truth: a send appends the server's
 * copy of the line rather than a guess, and a thread on screen is re-read every few
 * seconds by the screen so the other side's reply — and its read ticks and "yozmoqda…"
 * — arrive without a pull. A thread that has not been opened on the server yet — the
 * first message to an alias — is created by [send] through the start call, so the
 * screen never has to know which.
 *
 * A consultation is opened from the doctor's page by [startConsultation], and opened
 * again from the thread by [reopenConsultation] once its day has run out.
 */
class MessagesController(
    private val api: CommunityApi?,
    private val clock: Clock = Clock.System,
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

    /** Threads last written before the oldest one in [conversations] are still on the server. */
    var hasMoreConversations by mutableStateOf(false)
        private set
    private var loadingConversations = false

    /** The thread on screen, or null while nothing is open or the alias has no thread yet. */
    var current by mutableStateOf<Conversation?>(null)
        private set
    var messages by mutableStateOf<List<DirectMessage>>(emptyList())
        private set

    /** Lines older than the first of [messages] are still on the server; scrolling up reads them. */
    var hasOlder by mutableStateOf(false)
        private set
    private var loadingOlder = false

    /** The other side reported typing in the last few seconds, as of the last read. */
    var otherTyping by mutableStateOf(false)
        private set

    /** When the other side last opened the thread; the ticks are already on the lines. */
    var otherReadAt by mutableStateOf<Instant?>(null)
        private set

    /** A photo or a record on its way up, so the composer can say so and not take a second. */
    var sendingAttachment by mutableStateOf(false)
        private set

    /** Her attached record, fetched to show her what the doctor sees. */
    var record by mutableStateOf<DoctorSummary?>(null)
        private set

    val unreadTotal: Int get() = conversations.sumOf { it.unread }

    /**
     * Reads the newest page of threads. Pages she has already scrolled to stay under it:
     * the refresh runs every few seconds while the list is up, and it used to cut the
     * list back to its first page under her finger.
     */
    suspend fun load() {
        val api = api ?: return
        calls.run(silent = loaded) { api.conversations() }?.let { page ->
            val latest = page.map { thread -> thread.toAppConversation() }
            val full = latest.size >= CommunityApi.CONVERSATION_PAGE
            val tail = if (full) {
                val ids = latest.mapTo(HashSet()) { it.id }
                val edge = latest.last().lastMessageAt
                conversations.filter { it.id !in ids && it.lastMessageAt < edge }
            } else {
                emptyList()
            }
            conversations = latest + tail
            hasMoreConversations = full && (tail.isEmpty() || hasMoreConversations)
            loaded = true
        }
    }

    /** The next page of threads, under the oldest one on screen. */
    suspend fun loadMoreConversations() {
        val api = api ?: return
        val oldest = conversations.lastOrNull() ?: return
        if (!hasMoreConversations || loadingConversations) return
        loadingConversations = true
        try {
            val page = calls.run(silent = true) { api.conversations(before = oldest.lastMessageAt) } ?: return
            val known = conversations.mapTo(HashSet()) { it.id }
            conversations = conversations + page.map { it.toAppConversation() }.filter { it.id !in known }
            hasMoreConversations = page.size >= CommunityApi.CONVERSATION_PAGE
        } finally {
            loadingConversations = false
        }
    }

    /**
     * Opens a thread. With an id, the thread is read; with only an alias, the screen
     * starts empty and the first send creates it.
     */
    suspend fun open(conversationId: String?, alias: String) {
        current = conversationId?.let { id -> conversations.firstOrNull { it.id == id } }
        messages = emptyList()
        hasOlder = false
        otherTyping = false
        otherReadAt = null
        lastTypingSent = null
        if (conversationId != null) refreshThread(conversationId) else {
            // Nothing on the server yet; the header still needs a name.
            current = Conversation(
                id = "",
                alias = alias,
                tint = 0,
                badges = emptyList(),
                lastMessage = null,
                lastMessageAt = clock.now(),
                unread = 0,
                blocked = false,
            )
        }
    }

    /**
     * Re-reads the open thread. Silent: a poll that fails is the next poll's problem.
     * True when the read reached the server — which is also what marked it read there.
     */
    suspend fun refreshThread(conversationId: String): Boolean {
        val api = api ?: return false
        val thread = calls.run(silent = true) { api.thread(conversationId) } ?: return false
        // A poll that returns after she has left the thread must not put it back.
        if (current != null && current?.id != conversationId && current?.id?.isNotEmpty() == true) return true
        apply(thread)
        return true
    }

    private fun apply(thread: ConversationThread) {
        val conversation = thread.conversation.toAppConversation()
        val shown = if (current?.id == conversation.id) messages else emptyList()
        current = conversation
        mergeLatest(shown, thread.messages.map { it.toAppMessage() }, thread.hasMore)
        otherTyping = thread.otherTyping
        otherReadAt = thread.otherReadAt
        // Opening it read it; the list's count follows without a reload, and so does
        // the consultation's state, which the list draws as a chip.
        val known = conversations.any { it.id == conversation.id }
        conversations = if (known) {
            conversations.map { if (it.id == conversation.id) conversation.copy(unread = 0) else it }
        } else {
            listOf(conversation.copy(unread = 0)) + conversations
        }
    }

    /**
     * The poll reads only the newest page. What she scrolled up to stays above it as long
     * as the two still meet; when they do not — a burst longer than a page between two
     * polls — the newest page alone is shown and scrolling up reads the rest again.
     */
    private fun mergeLatest(shown: List<DirectMessage>, latest: List<DirectMessage>, latestHasMore: Boolean) {
        val first = latest.firstOrNull()
        val meets = first != null && shown.any { it.id == first.id }
        if (!latestHasMore || !meets) {
            messages = latest
            hasOlder = latestHasMore
            return
        }
        messages = shown.takeWhile { it.id != first.id } + latest
    }

    /** The page of lines above the first one on screen. */
    suspend fun loadOlder() {
        val api = api ?: return
        val thread = current?.takeIf { it.id.isNotEmpty() } ?: return
        val first = messages.firstOrNull() ?: return
        if (!hasOlder || loadingOlder) return
        loadingOlder = true
        try {
            val page = calls.run(silent = true) { api.olderMessages(thread.id, first.id) } ?: return
            // She may have left, or opened another thread, while it was on its way.
            if (current?.id != thread.id) return
            val known = messages.mapTo(HashSet()) { it.id }
            messages = page.messages.map { it.toAppMessage() }.filter { it.id !in known } + messages
            hasOlder = page.hasMore
        } finally {
            loadingOlder = false
        }
    }

    fun close() {
        current = null
        messages = emptyList()
        hasOlder = false
        otherTyping = false
        otherReadAt = null
        record = null
    }

    /** Sends into the open thread, or opens one with the alias when there is none. */
    suspend fun send(body: String): Boolean {
        val api = api ?: return true
        val thread = current ?: return false
        if (thread.id.isEmpty()) {
            val started = calls.run { api.startConversation(thread.alias, body) } ?: return false
            apply(started)
            load()
            return true
        }
        return deliver(thread.id, SendMessageRequest(body = body)) != null
    }

    /**
     * A photo, already resized and encoded by the picker, with an optional caption. Only
     * into a thread that exists: the start call takes text, so a first line is words.
     */
    suspend fun sendImage(imageBase64: String, mimeType: String, caption: String = ""): Boolean {
        val api = api ?: return true
        val thread = current?.takeIf { it.id.isNotEmpty() } ?: return false
        sendingAttachment = true
        return try {
            val sent = deliver(thread.id, SendMessageRequest(body = caption.trim(), image = MessageImageUpload(imageBase64, mimeType)))
            // Her own bubble draws from what she already has rather than downloading it back.
            sent?.let { message -> decodeBase64(imageBase64)?.let { rememberImage(message.id, it) } }
            sent != null
        } finally {
            sendingAttachment = false
        }
    }

    /**
     * Attaches her health record for the doctor. Only in an open consultation — the
     * server refuses it anywhere else, and the screen never offers it there.
     */
    suspend fun attachRecord(): Boolean {
        val api = api ?: return true
        val thread = current?.takeIf { it.id.isNotEmpty() && it.consultation?.open == true } ?: return false
        sendingAttachment = true
        return try {
            deliver(thread.id, SendMessageRequest(attachRecord = true)) != null
        } finally {
            sendingAttachment = false
        }
    }

    private suspend fun deliver(conversationId: String, request: SendMessageRequest): WireMessage? {
        val api = api ?: return null
        val sent = calls.run { api.sendMessage(conversationId, request) }
        if (sent == null) {
            // Refused because the window shut or a block landed while she was typing:
            // re-read, so the banner and the composer say so instead of the same error again.
            if (calls.error is ApiFailure.Forbidden || calls.error is ApiFailure.Blocked) refreshThread(conversationId)
            return null
        }
        appendSent(conversationId, sent)
        // Whatever she typed has gone; the next keystroke is a new "yozmoqda".
        lastTypingSent = null
        return sent
    }

    private fun appendSent(conversationId: String, sent: WireMessage) {
        // The poll can bring this message back before the send returns. The list is
        // keyed by id, and the same id twice is a crash, not a duplicate bubble.
        if (messages.none { it.id == sent.id }) messages = messages + sent.toAppMessage()
        val kind = sent.kind.toAppKind()
        conversations = conversations.map {
            if (it.id == conversationId) {
                it.copy(lastMessage = sent.body, lastMessageAt = sent.createdAt, lastMessageKind = kind, lastMessageRead = false)
            } else {
                it
            }
        }
    }

    private var lastTypingSent: Instant? = null

    /**
     * Tells the other side she is typing, at most once every [TypingEvery]. Called on
     * every keystroke; the throttle is here so the screen does not have to keep a clock.
     * Quiet and outside [calls]: a lost "yozmoqda" is not worth a banner, and it must
     * not flicker the send button's busy state.
     */
    suspend fun typing() {
        val api = api ?: return
        val thread = current?.takeIf { it.id.isNotEmpty() && it.canWrite } ?: return
        val now = clock.now()
        val last = lastTypingSent
        if (last != null && now - last < TypingEvery) return
        lastTypingSent = now
        api.typing(thread.id)
    }

    /**
     * Opens a consultation with a doctor, or opens hers again, and makes it the thread
     * on screen. Returns it, so the caller can navigate to it by id.
     */
    suspend fun startConsultation(doctorId: String, body: String? = null): Conversation? {
        val api = api ?: return null
        val thread = calls.run { api.startConsultation(doctorId, body?.trim()?.ifEmpty { null }) } ?: return null
        apply(thread)
        return current
    }

    /** "Yangi konsultatsiya ochish" from inside a closed one: the same thread, a new day. */
    suspend fun reopenConsultation(): Boolean {
        val doctorId = current?.takeIf { it.isConsultation }?.doctor?.id ?: return false
        return startConsultation(doctorId) != null
    }

    /**
     * The last start was refused because the doctor charges and nothing is open: the
     * caller raises the pay sheet instead of a banner. Clears the error it answers.
     */
    fun takePaymentRequired(): Boolean {
        if (calls.error !is ApiFailure.PaymentRequired) return false
        calls.clearError()
        return true
    }

    /**
     * Her stars, and a line if she wrote one, for the open thread's last window. A 409 —
     * rated already, from another phone or a double tap — counts as done: what she wanted
     * is true. The thread is re-read either way so `canRate` follows the server.
     */
    suspend fun rate(rating: Int, review: String?): Boolean {
        val api = api ?: return true
        val thread = current?.takeIf { it.id.isNotEmpty() && it.isConsultation } ?: return false
        val sent = calls.run { api.rateConsultation(thread.id, rating.coerceIn(1, 5), review?.trim()?.ifEmpty { null }) }
        val done = sent != null || calls.error is ApiFailure.Conflict
        if (done) {
            calls.clearError()
            refreshThread(thread.id)
        }
        return done
    }

    // ---------------------------------------------------------------- photos

    /** Bytes by message id. Bounded, oldest out first; a photo is a few hundred kilobytes. */
    private val imageCache = LinkedHashMap<String, ByteArray>()
    private val imageLock = Mutex()

    /**
     * A photo's bytes, from memory when it has been fetched before. Never written to
     * disk: the server marks them no-store, and a private photo in a cache directory is
     * one backup away from somewhere she never sent it.
     */
    suspend fun imageBytes(conversationId: String, messageId: String): ByteArray? {
        imageLock.withLock { imageCache[messageId] }?.let { return it }
        val api = api ?: return null
        val bytes = (api.messageImage(conversationId, messageId) as? ApiResult.Success)?.value ?: return null
        imageLock.withLock {
            imageCache.remove(messageId)
            imageCache[messageId] = bytes
            while (imageCache.size > ImageCacheSize) imageCache.remove(imageCache.keys.first())
        }
        return bytes
    }

    /** Remembers a photo she just sent, so her own bubble does not download what she has. */
    suspend fun rememberImage(messageId: String, bytes: ByteArray) = imageLock.withLock {
        imageCache[messageId] = bytes
        while (imageCache.size > ImageCacheSize) imageCache.remove(imageCache.keys.first())
    }

    // ---------------------------------------------------------------- her record

    /** Fetches the record she attached, in the language she reads, for the record sheet. */
    suspend fun loadRecord(messageId: String, language: String): Boolean {
        val api = api ?: return false
        val thread = current?.takeIf { it.id.isNotEmpty() } ?: return false
        val message = messages.firstOrNull { it.id == messageId && it.kind == MessageKind.Record } ?: return false
        record = null
        record = calls.run { api.messageRecord(thread.id, message.id, language.lowercase()) }
        return record != null
    }

    fun clearRecord() {
        record = null
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

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBase64(value: String): ByteArray? = runCatching { Base64.decode(value) }.getOrNull()

    companion object {
        /** The server shows "yozmoqda" for six seconds; one call in three keeps it lit. */
        val TypingEvery = 3.seconds
        const val ImageCacheSize = 40
    }
}
