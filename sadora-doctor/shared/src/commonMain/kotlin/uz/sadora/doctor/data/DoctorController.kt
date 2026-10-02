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

    /** Older questions than the last one in [questions] are on the server. */
    var questionsHasMore by mutableStateOf(false)
        private set

    /**
     * Where the next page starts on the server: the rows it has given, less the ones she
     * answered since — those left its list, and every row after them moved up one. Not
     * [questions]' size: a page that only repeats rows (newer questions pushed them down)
     * still moves it on, so the list never asks for the same page twice.
     */
    var questionsOffset by mutableStateOf(0)
        private set
    private var loadingQuestions = false

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

    /** The open post has comments past [threadComments] on the server. */
    var threadHasMore by mutableStateOf(false)
        private set
    private var loadingComments = false

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

    /**
     * The newest page. The pages she scrolled to stay under it, as the feed's do: Home
     * reads this on every visit, and the list must not fold back to its first page.
     */
    suspend fun loadQuestions() {
        val api = api ?: return
        calls.run(silent = questionsLoaded) { api.questions() }?.let { posts ->
            val full = posts.size >= DoctorApi.QUESTION_PAGE
            val tail = if (full && questionsLoaded) {
                val ids = posts.mapTo(HashSet()) { it.id }
                val edge = posts.last().createdAt
                questions.filter { it.id !in ids && it.createdAt < edge }
            } else {
                emptyList()
            }
            questions = posts + tail
            questionsOffset = questions.size
            questionsHasMore = full && (tail.isEmpty() || questionsHasMore)
            questionsLoaded = true
        }
    }

    /** The next page of the work list, under what is on screen. */
    suspend fun loadMoreQuestions() {
        val api = api ?: return
        if (!questionsHasMore || loadingQuestions) return
        loadingQuestions = true
        try {
            val page = calls.run(silent = true) { api.questions(offset = questionsOffset) } ?: return
            // Offsets shift when a question is asked in between; the overlap is dropped by id.
            val known = questions.mapTo(HashSet()) { it.id }
            questions = questions + page.filter { it.id !in known }
            questionsOffset += page.size
            questionsHasMore = page.size >= DoctorApi.QUESTION_PAGE
        } finally {
            loadingQuestions = false
        }
    }

    // ---------------------------------------------------------------- her page

    /** Her posts go on past what [profile] holds. */
    var profilePostsHasMore by mutableStateOf(false)
        private set

    /** How many of her posts have been read: the next page's offset. */
    private var profilePostsOffset = 0
    private var loadingProfilePosts = false

    suspend fun loadProfile(id: String) {
        val api = api ?: return
        if (profile?.id != id) profile = null
        profileCalls.run(silent = profile != null) { api.profile(id) }?.let { page ->
            // A refresh keeps the posts she had scrolled to under the few her page carries.
            // They live on [profile] itself, so a new photo or answer count reaches them too.
            val shown = profile?.takeIf { it.id == id }?.posts.orEmpty()
            val ids = page.posts.mapTo(HashSet()) { it.id }
            val edge = page.posts.lastOrNull()?.createdAt
            val tail = if (edge != null && page.posts.size < page.postCount) {
                shown.filter { it.id !in ids && it.createdAt < edge }
            } else {
                emptyList()
            }
            profile = page.copy(posts = page.posts + tail)
            profilePostsOffset = page.posts.size + tail.size
            profilePostsHasMore = profilePostsOffset < page.postCount
        }
    }

    /** The next page of the open page's posts, under what is on screen. */
    suspend fun loadMoreProfilePosts() {
        val api = api ?: return
        val id = profile?.id ?: return
        if (!profilePostsHasMore || loadingProfilePosts) return
        loadingProfilePosts = true
        try {
            val page = profileCalls.run(silent = true) { api.posts(id, offset = profilePostsOffset) } ?: return
            val current = profile?.takeIf { it.id == id } ?: return
            // Offsets shift when she posts in between; the overlap is dropped by id.
            val known = current.posts.mapTo(HashSet()) { it.id }
            profile = current.copy(posts = current.posts + page.items.filter { it.id !in known })
            profilePostsOffset += page.items.size
            profilePostsHasMore = page.hasMore && page.items.isNotEmpty()
        } finally {
            loadingProfilePosts = false
        }
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
            threadHasMore = false
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
        threadHasMore = loaded.second.size >= CommunityApi.COMMENT_PAGE
        threadLoaded = true
    }

    /** The next page of the open post's comments, by offset. */
    suspend fun loadMoreComments() {
        val community = community ?: return
        val postId = openThreadId ?: return
        if (!threadHasMore || loadingComments) return
        loadingComments = true
        try {
            val page = threadCalls.run(silent = true) { community.comments(postId, offset = threadComments.size) } ?: return
            if (openThreadId != postId) return
            val known = threadComments.mapTo(HashSet()) { it.id }
            threadComments = threadComments + page.filter { it.id !in known }
            threadHasMore = page.size >= CommunityApi.COMMENT_PAGE
        } finally {
            loadingComments = false
        }
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
            // It has left the server's list too: the rows after it moved up one, and the
            // next page starts one sooner, or it would skip the row that took its place.
            questionsOffset = (questionsOffset - 1).coerceAtLeast(0)
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
            feedHasMore = false
        }
        val page = feedCalls.run(silent = feedLoaded) { community.feed(doctorsOnly) } ?: return
        if (doctorsOnly == feedDoctorsOnly) {
            // The pages she scrolled to stay under the fresh first one.
            val tail = if (page.hasMore && feedLoaded) {
                val ids = page.items.mapTo(HashSet()) { it.id }
                val edge = page.items.lastOrNull()?.createdAt
                feed.filter { it.id !in ids && edge != null && it.createdAt < edge }
            } else {
                emptyList()
            }
            feed = page.items + tail
            feedOffset = feed.size
            feedHasMore = page.hasMore && (tail.isEmpty() || feedHasMore)
            feedLoaded = true
        }
    }

    /** Older posts than the last one in [feed] are on the server. */
    var feedHasMore by mutableStateOf(false)
        private set
    /** Server rows read so far, the next page's offset; the feed's end asks again on it. */
    var feedOffset by mutableStateOf(0)
        private set
    private var loadingFeed = false

    /** The next page of the feed, under what is on screen. */
    suspend fun loadMoreFeed() {
        val community = community ?: return
        if (!feedHasMore || loadingFeed) return
        val doctorsOnly = feedDoctorsOnly
        loadingFeed = true
        try {
            val page = feedCalls.run(silent = true) { community.feed(doctorsOnly, offset = feedOffset) } ?: return
            if (doctorsOnly != feedDoctorsOnly) return
            // Offsets shift when someone posts in between; the overlap is dropped by id.
            val known = feed.mapTo(HashSet()) { it.id }
            feed = feed + page.items.filter { it.id !in known }
            feedOffset += page.items.size
            feedHasMore = page.hasMore && page.items.isNotEmpty()
        } finally {
            loadingFeed = false
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

    /** Consultations last written before the oldest in [conversations] are on the server. */
    var hasMoreConversations by mutableStateOf(false)
        private set
    private var loadingConversations = false

    /**
     * The newest page. The pages she scrolled to stay under it: the list is re-read every
     * few seconds while it is up, and must not fold back to its first page.
     */
    suspend fun loadConversations(silent: Boolean = conversationsLoaded) {
        val community = community ?: return
        chatCalls.run(silent = silent) { community.consultations() }?.let { latest ->
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
            conversationsLoaded = true
        }
    }

    /** The next page of consultations, under the oldest one on screen. */
    suspend fun loadMoreConversations() {
        val community = community ?: return
        val oldest = conversations.lastOrNull() ?: return
        if (!hasMoreConversations || loadingConversations) return
        loadingConversations = true
        try {
            val page = chatCalls.run(silent = true) { community.consultations(before = oldest.lastMessageAt) } ?: return
            val known = conversations.mapTo(HashSet()) { it.id }
            conversations = conversations + page.filter { it.id !in known }
            hasMoreConversations = page.size >= CommunityApi.CONVERSATION_PAGE
        } finally {
            loadingConversations = false
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
        openConversation = withOlderKept(thread)
        // Read now: the list's count for it goes, and with it, perhaps, the tab's dot.
        conversations = conversations.map { if (it.id == id) thread.conversation else it }
    }

    /**
     * A read of a thread brings its newest page only. The lines she scrolled up to stay
     * above it while the two still meet; when they do not — a burst longer than a page
     * between two polls — the newest page alone stands, and scrolling up reads the rest.
     */
    private fun withOlderKept(latest: ConversationThread): ConversationThread {
        val shown = openConversation?.takeIf { it.conversation.id == latest.conversation.id } ?: return latest
        val first = latest.messages.firstOrNull() ?: return latest
        if (!latest.hasMore || shown.messages.none { it.id == first.id }) return latest
        return latest.copy(messages = shown.messages.takeWhile { it.id != first.id } + latest.messages, hasMore = shown.hasMore)
    }

    private var loadingOlder = false

    /** The page of lines above the first one of the open thread. */
    suspend fun loadOlderMessages(id: String) {
        val community = community ?: return
        val shown = openConversation?.takeIf { it.conversation.id == id && it.hasMore } ?: return
        val first = shown.messages.firstOrNull() ?: return
        if (loadingOlder) return
        loadingOlder = true
        try {
            val page = chatCalls.run(silent = true) { community.olderMessages(id, first.id) } ?: return
            val current = openConversation?.takeIf { it.conversation.id == id } ?: return
            val known = current.messages.mapTo(HashSet()) { it.id }
            openConversation = current.copy(
                messages = page.messages.filter { it.id !in known } + current.messages,
                hasMore = page.hasMore,
            )
        } finally {
            loadingOlder = false
        }
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
        openConversation = withOlderKept(thread)
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
        profilePostsHasMore = false
        profilePostsOffset = 0
        feed = emptyList()
        feedLoaded = false
        feedDoctorsOnly = false
        conversations = emptyList()
        conversationsLoaded = false
        hasMoreConversations = false
        openConversation = null
        images.clear()
        lastTypingSent = 0L
        patient = null
        patientToken = null
        recentPatients = emptyList()
        lastScannedToken = null
        questions = emptyList()
        questionsLoaded = false
        questionsHasMore = false
        questionsOffset = 0
        answeredQuestions = emptyList()
        shownPanel = null
        openThreadId = null
        thread = null
        threadComments = emptyList()
        threadLoaded = false
        threadHasMore = false
        feedHasMore = false
        feedOffset = 0
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
