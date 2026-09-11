package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uz.sadora.app.data.api.CommunityApi
import uz.sadora.app.model.AppState
import uz.sadora.app.model.CommunitySync
import uz.sadora.app.model.CommunityTopic
import uz.sadora.app.model.ReportReason
import uz.sadora.contract.ReportReason as WireReason

/**
 * The secret chat's data, mirrored onto the store the screen already reads.
 *
 * The feed is loaded whole — newest hundred, every room — and filtered on the phone,
 * because the screen switches rooms with a chip and a round trip per tap would make
 * the chips feel broken. Writes go up and the affected part of the store is refreshed
 * from the server's answer, so a like count on screen is always the server's count.
 *
 * A null API means no backend, and the sample feed in the store stays as it is.
 */
class CommunityController(
    private val api: CommunityApi?,
    private val state: AppState,
) {
    val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error
    val isOffline: Boolean get() = api == null

    fun clearError() = calls.clearError()

    /** True once the server's feed has replaced the samples, so the screen can show a skeleton before. */
    var loaded by mutableStateOf(false)
        private set

    suspend fun load() {
        val api = api ?: return
        calls.run(silent = true) { api.identity() }?.let {
            state.communityAlias = it.alias
            state.communityTint = it.tint
        }
        refreshFeed()
    }

    suspend fun refreshFeed() {
        val api = api ?: return
        calls.run(silent = true) { api.feed(limit = FEED_LIMIT) }?.let { page ->
            state.replaceCommunityFeed(
                posts = page.items.map { it.toAppPost() },
                liked = page.items.filter { it.liked }.map { it.id }.toSet(),
                saved = page.items.filter { it.saved }.map { it.id }.toSet(),
            )
            loaded = true
        }
    }

    suspend fun loadComments(postId: String) {
        val api = api ?: return
        calls.run(silent = true) { api.comments(postId) }?.let { comments ->
            state.replaceComments(postId, comments.map { it.toAppComment() })
        }
    }

    // ---------------------------------------------------------------- writes

    suspend fun setLiked(postId: String, liked: Boolean): Boolean {
        val api = api ?: return true
        // Silent: a like that failed is undone by the refresh, not announced.
        val result = calls.run(silent = true) { api.setLiked(postId, liked) }
        if (result == null) refreshFeed()
        return result != null
    }

    suspend fun setSaved(postId: String, saved: Boolean): Boolean {
        val api = api ?: return true
        val result = calls.run(silent = true) { api.setSaved(postId, saved) }
        if (result == null) refreshFeed()
        return result != null
    }

    suspend fun addComment(postId: String, body: String): Boolean {
        val api = api ?: return true
        calls.run { api.addComment(postId, body) } ?: return false
        loadComments(postId)
        refreshFeed()
        return true
    }

    suspend fun createPost(topic: CommunityTopic, body: String): Boolean {
        val api = api ?: return true
        val wireTopic = topic.toWireTopic() ?: return false
        calls.run { api.createPost(wireTopic, body) } ?: return false
        refreshFeed()
        return true
    }

    suspend fun deletePost(postId: String): Boolean {
        val api = api ?: return true
        calls.run { api.deletePost(postId) } ?: return false
        refreshFeed()
        return true
    }

    suspend fun reportPost(postId: String, reason: ReportReason, note: String?): Boolean {
        val api = api ?: return true
        return calls.run { api.reportPost(postId, reason.toWire(), note) } != null
    }

    private fun ReportReason.toWire(): WireReason = when (this) {
        ReportReason.Spam -> WireReason.SPAM
        ReportReason.Abuse -> WireReason.ABUSE
        ReportReason.Misinformation -> WireReason.MISINFORMATION
        ReportReason.PersonalData -> WireReason.PERSONAL_DATA
        ReportReason.Other -> WireReason.OTHER
    }

    private companion object {
        const val FEED_LIMIT = 100
    }
}

/**
 * Carries the store's community edits to the controller.
 *
 * Same shape as `HealthSync`: the screen mutates optimistically and returns, the request
 * runs on [scope], and the controller's refresh replaces the guess with the server's
 * answer.
 */
class CommunitySyncBridge(
    private val community: CommunityController,
    private val scope: CoroutineScope,
) : CommunitySync {

    override fun postLiked(postId: String, liked: Boolean) {
        scope.launch { community.setLiked(postId, liked) }
    }

    override fun postSaved(postId: String, saved: Boolean) {
        scope.launch { community.setSaved(postId, saved) }
    }

    override fun commentAdded(postId: String, body: String) {
        scope.launch { community.addComment(postId, body) }
    }

    override fun postCreated(topic: CommunityTopic, body: String) {
        scope.launch { community.createPost(topic, body) }
    }

    override fun postDeleted(postId: String) {
        scope.launch { community.deletePost(postId) }
    }

    override fun postReported(postId: String, reason: ReportReason, note: String?) {
        scope.launch { community.reportPost(postId, reason, note) }
    }
}
