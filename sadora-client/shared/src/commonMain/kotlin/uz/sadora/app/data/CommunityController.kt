package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uz.sadora.app.data.api.CommunityApi
import uz.sadora.app.model.AliasProfile
import uz.sadora.app.model.AppState
import uz.sadora.app.model.CommunitySync
import uz.sadora.app.model.CommunityTopic
import uz.sadora.app.model.ReportReason
import uz.sadora.contract.CommunityIdentity
import uz.sadora.contract.ReportReason as WireReason
import uz.sadora.contract.UpdateIdentityRequest

/**
 * The secret chat's data, mirrored onto the store the screen already reads.
 *
 * The feed is loaded a page at a time, every room together, and filtered on the phone,
 * because the screen switches rooms with a chip and a round trip per tap would make
 * the chips feel broken; scrolling to its end reads the next page. Writes go up and the
 * affected part of the store is refreshed from the server's answer, so a like count on
 * screen is always the server's count.
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
        calls.run(silent = true) { api.identity() }?.let(::applyIdentity)
        refreshFeed()
    }

    private fun applyIdentity(identity: CommunityIdentity) {
        state.communityAlias = identity.alias
        state.communityTint = identity.tint
        state.communityBio = identity.bio
        state.communityDmOpen = identity.dmOpen
        state.communityBadges = identity.badges.map { it.toAppBadge() }
        state.applyWornBadge(identity.worn)
        state.applyWornFrame(identity.frame)
        state.communityUnread = identity.unreadMessages
    }

    /** Re-reads her identity alone: the unread count, after a thread was opened. */
    suspend fun refreshIdentity() {
        val api = api ?: return
        calls.run(silent = true) { api.identity() }?.let(::applyIdentity)
    }

    // ---------------------------------------------------------------- profiles

    /** The alias page on screen. Null until loaded, and replaced on every open. */
    var profile by mutableStateOf<AliasProfile?>(null)
        private set

    /** Her posts go on past what [profile] holds. */
    var profileHasMore by mutableStateOf(false)
        private set

    /** How many of her posts have been read: the next page's offset. */
    private var profileLoadedCount = 0
    private var loadingProfilePosts = false

    suspend fun loadProfile(alias: String) {
        val api = api ?: return
        if (profile?.alias != alias) profile = null
        // Loud while there is nothing to show: the skeleton used to sit there for good.
        calls.run(silent = profile != null) { api.profile(alias) }?.let { wire ->
            val fresh = wire.toAppProfile()
            // A refresh — her bio saved, a block — keeps the posts she had scrolled to
            // under the few the page carries, rather than folding the list back up.
            val shown = profile?.takeIf { it.alias == alias }?.posts.orEmpty()
            val ids = fresh.posts.mapTo(HashSet()) { it.id }
            val edge = fresh.posts.lastOrNull()?.createdAt
            val tail = if (edge != null && fresh.posts.size < fresh.postCount) {
                shown.filter { it.id !in ids && it.createdAt < edge }
            } else {
                emptyList()
            }
            profile = fresh.copy(posts = fresh.posts + tail)
            profileLoadedCount = fresh.posts.size + tail.size
            profileHasMore = profileLoadedCount < fresh.postCount
        }
    }

    /** The next page of the open profile's posts, under what is on screen. */
    suspend fun loadMoreProfilePosts() {
        val api = api ?: return
        val alias = profile?.alias ?: return
        if (!profileHasMore || loadingProfilePosts) return
        loadingProfilePosts = true
        try {
            val page = calls.run(silent = true) {
                api.profilePosts(alias, limit = CommunityApi.PROFILE_POSTS_PAGE, offset = profileLoadedCount)
            } ?: return
            val current = profile?.takeIf { it.alias == alias } ?: return
            // Offsets shift when she posts in between; the overlap is dropped by id.
            val known = current.posts.mapTo(HashSet()) { it.id }
            profile = current.copy(posts = current.posts + page.items.map { it.toAppPost() }.filter { it.id !in known })
            profileLoadedCount += page.items.size
            profileHasMore = page.hasMore && page.items.isNotEmpty()
        } finally {
            loadingProfilePosts = false
        }
    }

    /** Her bio and her door. Null leaves a field as it is. */
    suspend fun updateProfile(bio: String?, dmOpen: Boolean?): Boolean {
        val api = api ?: run {
            bio?.let { state.communityBio = it.trim().ifEmpty { null } }
            dmOpen?.let { state.communityDmOpen = it }
            return true
        }
        val updated = calls.run { api.updateIdentity(UpdateIdentityRequest(bio = bio, dmOpen = dmOpen)) } ?: return false
        applyIdentity(updated)
        // Her own page, if it is open, shows the new line.
        profile?.takeIf { it.isMe }?.let { loadProfile(it.alias) }
        return true
    }

    suspend fun setBlocked(alias: String, blocked: Boolean): Boolean {
        val api = api ?: return true
        calls.run { api.setBlocked(alias, blocked) } ?: return false
        loadProfile(alias)
        return true
    }

    /** Older posts than the last one loaded are still on the server. */
    var feedHasMore by mutableStateOf(false)
        private set

    /**
     * How many of the server's posts have been read: the next page's offset. Observable,
     * so the feed's end asks again even after a page that was all overlap.
     */
    var feedLoadedCount by mutableStateOf(0)
        private set
    private var loadingFeed = false

    /**
     * Reads the newest page. The pages she has scrolled to stay under it, so a like or a
     * new post no longer cuts the feed back to its first page.
     */
    suspend fun refreshFeed() {
        val api = api ?: return
        // Quiet once there is a feed to keep showing. The first read is not: offline it
        // failed without a word, and the screen said "hali post yo'q — birinchisini yozing".
        calls.run(silent = loaded) { api.feed(limit = CommunityApi.FEED_PAGE) }?.let { page ->
            val latest = page.items.map { it.toAppPost() }
            val tail = if (page.hasMore && loaded) {
                val ids = latest.mapTo(HashSet()) { it.id }
                val edge = latest.lastOrNull()?.createdAt
                state.communityPosts.filter { it.id !in ids && edge != null && it.createdAt < edge }
            } else {
                emptyList()
            }
            val tailIds = tail.mapTo(HashSet()) { it.id }
            state.replaceCommunityFeed(
                posts = latest + tail,
                liked = page.items.filter { it.liked }.map { it.id }.toSet() + state.likedPosts.filter { it in tailIds },
                saved = page.items.filter { it.saved }.map { it.id }.toSet() + state.savedPosts.filter { it in tailIds },
            )
            feedLoadedCount = latest.size + tail.size
            feedHasMore = page.hasMore && (tail.isEmpty() || feedHasMore)
            loaded = true
        }
    }

    /** The next page of the feed, appended under what is on screen. */
    suspend fun loadMoreFeed() {
        val api = api ?: return
        if (!feedHasMore || loadingFeed) return
        loadingFeed = true
        try {
            val page = calls.run(silent = true) { api.feed(limit = CommunityApi.FEED_PAGE, offset = feedLoadedCount) } ?: return
            // Offsets shift when someone posts in between; the overlap is dropped by id.
            val known = state.communityPosts.mapTo(HashSet()) { it.id }
            val fresh = page.items.filter { it.id !in known }
            state.appendCommunityFeed(
                posts = fresh.map { it.toAppPost() },
                liked = fresh.filter { it.liked }.map { it.id }.toSet(),
                saved = fresh.filter { it.saved }.map { it.id }.toSet(),
            )
            feedLoadedCount += page.items.size
            feedHasMore = page.hasMore && page.items.isNotEmpty()
        } finally {
            loadingFeed = false
        }
    }

    /** Posts whose comments go on past what the store holds, by id. */
    var commentsWithMore by mutableStateOf<Set<String>>(emptySet())
        private set
    private var loadingComments = false

    /**
     * Reads a post's comments from the top — at least a page, and as many as she had
     * already scrolled through, so a new comment does not fold the list back up.
     */
    suspend fun loadComments(postId: String) {
        val api = api ?: return
        val shown = state.communityPosts.firstOrNull { it.id == postId }?.comments?.size ?: 0
        val limit = (shown + 1).coerceIn(CommunityApi.COMMENT_PAGE, MAX_COMMENTS)
        calls.run(silent = true) { api.comments(postId, limit = limit) }?.let { comments ->
            val more = comments.size >= limit
            state.replaceComments(postId, comments.map { it.toAppComment() }, complete = !more)
            commentsWithMore = if (more) commentsWithMore + postId else commentsWithMore - postId
        }
    }

    /** The next page of a post's comments. */
    suspend fun loadMoreComments(postId: String) {
        val api = api ?: return
        if (postId !in commentsWithMore || loadingComments) return
        val shown = state.communityPosts.firstOrNull { it.id == postId }?.comments ?: return
        loadingComments = true
        try {
            val page = calls.run(silent = true) {
                api.comments(postId, limit = CommunityApi.COMMENT_PAGE, offset = shown.size)
            } ?: return
            val known = shown.mapTo(HashSet()) { it.id }
            val fresh = page.map { it.toAppComment() }.filter { it.id !in known }
            val more = page.size >= CommunityApi.COMMENT_PAGE
            state.replaceComments(postId, shown + fresh, complete = !more)
            commentsWithMore = if (more) commentsWithMore + postId else commentsWithMore - postId
        } finally {
            loadingComments = false
        }
    }

    // ---------------------------------------------------------------- writes

    suspend fun setLiked(postId: String, liked: Boolean): Boolean {
        val api = api ?: return true
        // Silent: a like that failed is undone, not announced. Undone here and not by a
        // refresh — offline the refresh fails too, and the heart and its count stayed wrong.
        val result = calls.run(silent = true) { api.setLiked(postId, liked) }
        if (result == null) state.revertLike(postId, liked)
        return result != null
    }

    /**
     * Reports a batch of seen posts. Not through [calls]: it runs behind whatever she is
     * doing, and must neither raise the busy flag nor an error.
     */
    suspend fun sendViews(postIds: List<String>): Boolean {
        val api = api ?: return true
        return api.recordViews(postIds) is ApiResult.Success
    }

    suspend fun setSaved(postId: String, saved: Boolean): Boolean {
        val api = api ?: return true
        val result = calls.run(silent = true) { api.setSaved(postId, saved) }
        if (result == null) state.revertSaved(postId, saved)
        return result != null
    }

    suspend fun addComment(postId: String, body: String): Boolean {
        val api = api ?: return true
        calls.run { api.addComment(postId, body) } ?: return false
        loadComments(postId)
        refreshFeed()
        return true
    }

    /** The store's optimistic copy of a comment the server refused. */
    fun dropComment(postId: String, body: String) = state.dropOwnComment(postId, body)

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
        // Her own profile lists it too; it stayed there, and opened as "o'chirilgan".
        profile?.let { shown -> loadProfile(shown.alias) }
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
        /** The server's cap on one read of comments. */
        const val MAX_COMMENTS = 200
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

    private val views = PostViewTracker(scope, community::sendViews)

    override fun postSeen(postId: String) = views.seen(postId)

    override fun flushPostViews() = views.flush()

    override fun postLiked(postId: String, liked: Boolean) {
        scope.launch { community.setLiked(postId, liked) }
    }

    override fun postSaved(postId: String, saved: Boolean) {
        scope.launch { community.setSaved(postId, saved) }
    }

    override fun commentAdded(postId: String, body: String) {
        scope.launch { if (!community.addComment(postId, body)) community.dropComment(postId, body) }
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
