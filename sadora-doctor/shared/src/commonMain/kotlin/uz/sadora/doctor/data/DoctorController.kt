package uz.sadora.doctor.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Instant
import uz.sadora.contract.CommunityComment
import uz.sadora.contract.CommunityPost
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.Conversation
import uz.sadora.contract.ConversationThread
import uz.sadora.contract.DoctorAccount
import uz.sadora.contract.DoctorApplicationRequest
import uz.sadora.contract.DoctorProfile
import uz.sadora.contract.DoctorStatus
import uz.sadora.contract.DoctorSummary
import uz.sadora.contract.Language
import uz.sadora.contract.MessageImageUpload
import uz.sadora.contract.ReportReason
import uz.sadora.contract.SendMessageRequest
import uz.sadora.contract.UpdateDoctorProfileRequest

/**
 * Everything the doctor does, after signing in: her application and panel, her public
 * page, the questions waiting for a doctor, a question's thread with her answer, a post
 * of her own, the community feed, her private conversations, and the patient records
 * she opens from a QR code.
 *
 * Ported from the client app's controller. The screens each read their own
 * [ApiCallState], so a failure on one page is not left standing as a banner on the next.
 * Null APIs mean no backend — a preview — and every call then does nothing.
 */
class DoctorController(
    private val api: DoctorApi?,
    private val community: CommunityApi?,
    private val patients: PatientApi? = null,
) {
    /** The panel: her account, the edit card and the work list. */
    val calls = ApiCallState()

    /** The application form. */
    val applyCalls = ApiCallState()

    /** Her public page. */
    val profileCalls = ApiCallState()

    /** A question's page: loading it and answering it. */
    val threadCalls = ApiCallState()

    /** A new post of her own. */
    val composeCalls = ApiCallState()

    /** The Community tab's feed. */
    val feedCalls = ApiCallState()

    /** The Messages tab and an open conversation. */
    val chatCalls = ApiCallState()

    /** A patient's record, opened from her QR code. */
    val patientCalls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error
    val isOffline: Boolean get() = api == null

    fun clearError() = calls.clearError()

    /** Her own account; null until loaded. */
    var account by mutableStateOf<DoctorAccount?>(null)
        private set

    val panelState: PanelState get() = panelStateOf(account)

    /** The name her posts and answers go out under — only once she is approved. */
    val doctorName: String?
        get() = account?.takeIf { it.status == DoctorStatus.APPROVED }?.fullName

    /** Her public page, replaced on every open. */
    var profile by mutableStateOf<DoctorProfile?>(null)
        private set
    val profilePosts: List<CommunityPost> get() = profile?.posts.orEmpty()
    /** Questions waiting for a doctor — the approved panel's work list. */
    var questions by mutableStateOf<List<CommunityPost>>(emptyList())
        private set
    var questionsLoaded by mutableStateOf(false)
        private set

    /**
     * Questions she has answered since the panel last drew them, with where they stood.
     *
     * They have already left [questions]; the panel shows them once more, marked as
     * answered, and then lets them go with [settleAnswered] — so she sees the question
     * she answered leave the list, rather than coming back to a list that is simply
     * shorter.
     */
    var answeredQuestions by mutableStateOf<List<AnsweredQuestion>>(emptyList())
        private set

    /** The work list as the panel draws it: the waiting questions, the just-answered ones still in place. */
    val questionRows: List<QuestionRow>
        get() {
            val rows = questions.mapTo(mutableListOf()) { QuestionRow(it, answered = false) }
            answeredQuestions.sortedBy { it.index }.forEach { answered ->
                if (rows.none { it.post.id == answered.post.id }) {
                    rows.add(answered.index.coerceIn(0, rows.size), QuestionRow(answered.post, answered = true))
                }
            }
            return rows
        }

    fun settleAnswered() {
        answeredQuestions = emptyList()
    }

    /**
     * What the panel's status card showed last, so a change made elsewhere — the form
     * sent, a review come back — plays as a change when she returns, not as a new page.
     * Only the screen reads and writes it.
     */
    var shownPanel: PanelState? = null

    /** The post open on its own page, and its comments in the server's order. */
    var thread by mutableStateOf<CommunityPost?>(null)
        private set
    var threadComments by mutableStateOf<List<CommunityComment>>(emptyList())
        private set
    var threadLoaded by mutableStateOf(false)
        private set

    // ---------------------------------------------------------------- the panel

    suspend fun loadAccount(silent: Boolean = true) {
        val api = api ?: return
        calls.run(silent = silent || account != null) { api.account() }?.let { account = it }
    }

    suspend fun apply(request: DoctorApplicationRequest): Boolean {
        val api = api ?: return false
        val result = applyCalls.run { api.apply(request) } ?: return false
        account = result
        return true
    }

    /** Her switch for taking consultations; the page patients see follows it at once. */
    suspend fun setAcceptsConsultations(accepts: Boolean): Boolean {
        val api = api ?: return false
        val result = calls.run { api.update(UpdateDoctorProfileRequest(acceptsConsultations = accepts)) } ?: return false
        account = result
        return true
    }

    suspend fun update(workplace: String?, bio: String?): Boolean {
        val api = api ?: return false
        val result = calls.run { api.update(UpdateDoctorProfileRequest(workplace = workplace, bio = bio)) } ?: return false
        account = result
        return true
    }

    suspend fun loadQuestions() {
        val api = api ?: return
        calls.run(silent = questionsLoaded) { api.questions() }?.let { posts ->
            questions = posts
            questionsLoaded = true
        }
    }

    // ---------------------------------------------------------------- her page

    suspend fun loadProfile(id: String) {
        val api = api ?: return
        if (profile?.id != id) profile = null
        profileCalls.run(silent = profile != null) { api.profile(id) }?.let { profile = it }
    }

    // ---------------------------------------------------------------- a question

    /**
     * Opens a post's page. The card she tapped is shown at once from the list it came
     * from; the server's copy and the comments replace it as they arrive.
     */
    suspend fun openThread(postId: String) {
        if (openThreadId != postId) {
            openThreadId = postId
            thread = (questions + profilePosts + feed).firstOrNull { it.id == postId }
            threadComments = emptyList()
            threadLoaded = false
        }
        val community = community ?: return
        // One call as far as the page is concerned: a thread without its post, or a post
        // whose comments failed, is shown as the failure it is.
        val loaded = threadCalls.run(silent = threadLoaded) {
            when (val post = community.post(postId)) {
                is ApiResult.Failure -> post
                is ApiResult.Success -> community.comments(postId).map { post.value to it }
            }
        } ?: return
        // She may have left for another page while this one loaded.
        if (openThreadId != postId) return
        thread = loaded.first
        threadComments = loaded.second
        threadLoaded = true
    }

    /** The post whose page is open, so a late answer for an earlier one is dropped. */
    private var openThreadId: String? = null

    /**
     * Answers under a question. The answer joins the doctors' answers at the top of the
     * thread, and the question leaves the waiting list — a doctor has now answered it.
     */
    suspend fun answer(postId: String, body: String): Boolean {
        val community = community ?: return false
        val comment = threadCalls.run { community.addComment(postId, body) } ?: return false
        if (thread?.id == postId) {
            val leadingAnswers = threadComments.takeWhile { it.doctor != null }.size
            threadComments = threadComments.toMutableList().apply { add(leadingAnswers, comment) }
            thread = thread?.let { it.copy(commentCount = it.commentCount + 1, doctorAnswers = it.doctorAnswers + 1) }
        }
        val index = questions.indexOfFirst { it.id == postId }
        if (index >= 0) {
            val answered = questions[index].let { it.copy(commentCount = it.commentCount + 1, doctorAnswers = it.doctorAnswers + 1) }
            answeredQuestions = answeredQuestions + AnsweredQuestion(answered, index)
            questions = questions.filterNot { it.id == postId }
        }
        profile = profile?.let { page ->
            page.copy(
                answerCount = page.answerCount + 1,
                posts = page.posts.map { if (it.id == postId) it.copy(commentCount = it.commentCount + 1) else it },
            )
        }
        return true
    }

    // ---------------------------------------------------------------- the feed

    /** The community feed, newest first — all of it, or only the doctors' posts. */
    var feed by mutableStateOf<List<CommunityPost>>(emptyList())
        private set
    var feedLoaded by mutableStateOf(false)
        private set
    var feedDoctorsOnly by mutableStateOf(false)
        private set

    suspend fun loadFeed(doctorsOnly: Boolean = feedDoctorsOnly) {
        val community = community ?: return
        if (doctorsOnly != feedDoctorsOnly) {
            // Another filter is another list: the old one must not stand in for it.
            feedDoctorsOnly = doctorsOnly
            feed = emptyList()
            feedLoaded = false
        }
        val posts = feedCalls.run(silent = feedLoaded) { community.feed(doctorsOnly) }?.items ?: return
        if (doctorsOnly == feedDoctorsOnly) {
            feed = posts
            feedLoaded = true
        }
    }

    // ---------------------------------------------------------------- consultations

    /** The consultations she holds as a doctor, the most recent first. */
    var conversations by mutableStateOf<List<Conversation>>(emptyList())
        private set
    var conversationsLoaded by mutableStateOf(false)
        private set

    /** Messages she has not read yet, across every consultation — the tab's dot. */
    val unreadMessages: Int get() = conversations.sumOf { it.unread }

    /** The consultation open on its own page; every read of it marks it read on the server. */
    var openConversation by mutableStateOf<ConversationThread?>(null)
        private set

    /** Photos already fetched, by message id, so a thread polled every few seconds fetches each once. */
    private val images = mutableMapOf<String, ByteArray>()

    suspend fun loadConversations(silent: Boolean = conversationsLoaded) {
        val community = community ?: return
        chatCalls.run(silent = silent) { community.consultations() }?.let {
            conversations = it
            conversationsLoaded = true
        }
    }

    /**
     * Reads the thread — on opening, and again on every poll while it is on screen. A
     * poll is silent: a dropped connection for one tick is not a banner.
     */
    suspend fun openConversation(id: String, poll: Boolean = false) {
        val community = community ?: return
        if (openConversation?.conversation?.id != id) openConversation = null
        val thread = chatCalls.run(silent = poll || openConversation != null) { community.conversation(id) } ?: return
        openConversation = thread
        // Read now: the list's count for it goes, and with it, perhaps, the tab's dot.
        conversations = conversations.map { if (it.id == id) thread.conversation else it }
    }

    /** False when it did not go; the composer then keeps the text. */
    suspend fun sendMessage(id: String, body: String): Boolean = send(id, SendMessageRequest(body = body))

    suspend fun sendImage(id: String, photo: CapturedPhotoData, caption: String = ""): Boolean =
        send(id, SendMessageRequest(body = caption, image = MessageImageUpload(photo.base64, photo.mimeType)))

    private suspend fun send(id: String, request: SendMessageRequest): Boolean {
        val community = community ?: return false
        val message = chatCalls.run { community.sendMessage(id, request) } ?: return false
        openConversation = openConversation?.takeIf { it.conversation.id == id }?.let { thread ->
            thread.copy(
                conversation = thread.conversation.copy(
                    lastMessage = message.body,
                    lastMessageAt = message.createdAt,
                    lastMessageKind = message.kind,
                    lastMessageRead = false,
                ),
                // A poll may already have brought it in.
                messages = if (thread.messages.any { it.id == message.id }) thread.messages else thread.messages + message,
            )
        }
        // The conversation moves to the top of the list with its new last line.
        conversations = conversations
            .map {
                if (it.id == id) {
                    it.copy(lastMessage = message.body, lastMessageAt = message.createdAt, lastMessageKind = message.kind, lastMessageRead = false)
                } else {
                    it
                }
            }
            .sortedByDescending { it.lastMessageAt }
        return true
    }

    /** "yozmoqda…" for the patient: sent at most once per [TypingEveryMillis] while she types. */
    suspend fun typing(id: String, nowMillis: Long) {
        val community = community ?: return
        if (nowMillis - lastTypingSent < TypingEveryMillis) return
        lastTypingSent = nowMillis
        community.typing(id)
    }

    private var lastTypingSent = 0L

    /**
     * Ends the consultation, with her advice for the patient when she wrote one. On a
     * window already over the same call adds the advice it was closed without.
     */
    suspend fun closeConsultation(id: String, summary: String? = null): Boolean {
        val community = community ?: return false
        val thread = chatCalls.run { community.close(id, summary?.trim()?.ifEmpty { null }) } ?: return false
        openConversation = thread
        conversations = conversations.map { if (it.id == id) thread.conversation else it }
        return true
    }

    suspend fun reportConversation(id: String, reason: ReportReason): Boolean {
        val community = community ?: return false
        return chatCalls.run { community.report(id, reason) } != null
    }

    /** A photo's bytes, fetched once; null while it cannot be had. */
    suspend fun image(conversationId: String, messageId: String): ByteArray? {
        images[messageId]?.let { return it }
        val community = community ?: return null
        val bytes = when (val result = community.image(conversationId, messageId)) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return null
        }
        images[messageId] = bytes
        return bytes
    }

    /**
     * The record a patient attached, opened on the same page as a scanned one. Keyed
     * apart from share tokens, and not added to the Scan tab's list: that list is for
     * the patients in front of her.
     */
    suspend fun openAttachedRecord(conversationId: String, messageId: String, language: Language) {
        val community = community ?: return
        val key = attachedKey(messageId)
        if (patientToken != key) {
            patientToken = key
            patient = null
        }
        val record = patientCalls.run(silent = patient != null) { community.record(conversationId, messageId, language) } ?: return
        if (patientToken != key) return
        patient = record
    }

    // ---------------------------------------------------------------- patients

    /** The record open on its page, and the token it was read by. */
    var patient by mutableStateOf<DoctorSummary?>(null)
        private set
    private var patientToken: String? = null

    /**
     * The patients opened this session, the latest first, so a second look during the
     * visit does not need the code again. Held in memory only — never written to the
     * phone — and gone on sign-out; the link itself expires on her side.
     */
    var recentPatients by mutableStateOf<List<RecentPatient>>(emptyList())
        private set

    /** The code the camera opened last; the Scan tab does not open it again by itself. */
    var lastScannedToken: String? = null

    fun patientFor(token: String): DoctorSummary? = patient.takeIf { patientToken == token }

    suspend fun openPatient(token: String, language: Language) {
        val patients = patients ?: return
        if (patientToken != token) {
            patientToken = token
            patient = null
        }
        val record = patientCalls.run(silent = patient != null) { patients.record(token, language) } ?: return
        // She may have scanned another code while this one loaded.
        if (patientToken != token) return
        patient = record
        recentPatients = listOf(RecentPatient(token, record.person.name, record.person.age, record.generatedAt)) +
            recentPatients.filterNot { it.token == token }.take(RecentPatientsMax - 1)
    }

    // ---------------------------------------------------------------- her own post

    /** Publishes a post; the server signs it with her name and check mark. */
    suspend fun createPost(topic: CommunityTopic, body: String): Boolean {
        val community = community ?: return false
        val post = composeCalls.run { community.createPost(topic, body) } ?: return false
        profile = profile?.let { it.copy(postCount = it.postCount + 1, posts = listOf(post) + it.posts) }
        // Her post is a doctor's post: it belongs at the top of either filter.
        if (feedLoaded) feed = listOf(post) + feed
        return true
    }

    /** Forgets everything: another account may sign in on this phone next. */
    fun reset() {
        account = null
        profile = null
        feed = emptyList()
        feedLoaded = false
        feedDoctorsOnly = false
        conversations = emptyList()
        conversationsLoaded = false
        openConversation = null
        images.clear()
        lastTypingSent = 0L
        patient = null
        patientToken = null
        recentPatients = emptyList()
        lastScannedToken = null
        questions = emptyList()
        questionsLoaded = false
        answeredQuestions = emptyList()
        shownPanel = null
        openThreadId = null
        thread = null
        threadComments = emptyList()
        threadLoaded = false
        listOf(calls, applyCalls, profileCalls, threadCalls, composeCalls, feedCalls, chatCalls, patientCalls).forEach { it.clearError() }
    }
}

/** A question she answered, and the place in the list it had. */
data class AnsweredQuestion(val post: CommunityPost, val index: Int)

/** One entry of the work list. [answered] entries are on their way out. */
data class QuestionRow(val post: CommunityPost, val answered: Boolean)

/** A patient opened this session, as the Scan tab lists her. */
data class RecentPatient(val token: String, val name: String, val age: Int?, val openedAt: Instant)

private const val RecentPatientsMax = 10

/** How often "yozmoqda…" is reported while she types; the server holds it a little longer. */
private const val TypingEveryMillis = 3_000L

/** A photo on its way to a thread: the picker's bytes, already resized and encoded. */
data class CapturedPhotoData(val base64: String, val mimeType: String)

/** The key a record attached in a consultation is held under, apart from share tokens. */
fun attachedKey(messageId: String): String = "message:$messageId"
