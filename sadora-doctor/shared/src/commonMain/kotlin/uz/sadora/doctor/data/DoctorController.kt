package uz.sadora.doctor.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uz.sadora.contract.CommunityComment
import uz.sadora.contract.CommunityPost
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.DoctorAccount
import uz.sadora.contract.DoctorApplicationRequest
import uz.sadora.contract.DoctorProfile
import uz.sadora.contract.DoctorStatus
import uz.sadora.contract.UpdateDoctorProfileRequest

/**
 * Everything the doctor does, after signing in: her application and panel, her public
 * page, the questions waiting for a doctor, a question's thread with her answer, and a
 * post of her own.
 *
 * Ported from the client app's controller. The screens each read their own
 * [ApiCallState], so a failure on one page is not left standing as a banner on the next.
 * Null APIs mean no backend — a preview — and every call then does nothing.
 */
class DoctorController(
    private val api: DoctorApi?,
    private val community: CommunityApi?,
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
            thread = (questions + profilePosts).firstOrNull { it.id == postId }
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

    // ---------------------------------------------------------------- her own post

    /** Publishes a post; the server signs it with her name and check mark. */
    suspend fun createPost(topic: CommunityTopic, body: String): Boolean {
        val community = community ?: return false
        val post = composeCalls.run { community.createPost(topic, body) } ?: return false
        profile = profile?.let { it.copy(postCount = it.postCount + 1, posts = listOf(post) + it.posts) }
        return true
    }

    /** Forgets everything: another account may sign in on this phone next. */
    fun reset() {
        account = null
        profile = null
        questions = emptyList()
        questionsLoaded = false
        answeredQuestions = emptyList()
        shownPanel = null
        openThreadId = null
        thread = null
        threadComments = emptyList()
        threadLoaded = false
        listOf(calls, applyCalls, profileCalls, threadCalls, composeCalls).forEach { it.clearError() }
    }
}

/** A question she answered, and the place in the list it had. */
data class AnsweredQuestion(val post: CommunityPost, val index: Int)

/** One entry of the work list. [answered] entries are on their way out. */
data class QuestionRow(val post: CommunityPost, val answered: Boolean)
