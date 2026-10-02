package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import io.ktor.http.encodeURLPathPart
import io.ktor.http.encodeURLQueryComponent
import kotlin.time.Instant
import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.Ack
import uz.sadora.contract.BlockState
import uz.sadora.contract.CommunityComment
import uz.sadora.contract.CommunityIdentity
import uz.sadora.contract.CommunityPost
import uz.sadora.contract.CommunityProfile
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.Conversation
import uz.sadora.contract.ConversationThread
import uz.sadora.contract.DirectMessage
import uz.sadora.contract.DoctorSummary
import uz.sadora.contract.RateConsultationRequest
import uz.sadora.contract.StartConsultationRequest
import kotlinx.serialization.json.JsonObject
import uz.sadora.contract.CreateCommentRequest
import uz.sadora.contract.CreatePostRequest
import uz.sadora.contract.LikeState
import uz.sadora.contract.MessagePage
import uz.sadora.contract.Page
import uz.sadora.contract.ReportReason
import uz.sadora.contract.ReportRequest
import uz.sadora.contract.SaveState
import uz.sadora.contract.SendMessageRequest
import uz.sadora.contract.StartConversationRequest
import uz.sadora.contract.UpdateIdentityRequest

/** The secret chat. Every call is the caller's own view: her alias, her reactions, her posts. */
class CommunityApi(private val caller: ApiCaller) {

    suspend fun identity(): ApiResult<CommunityIdentity> =
        caller.authenticated("v1/community/me", HttpMethodKind.GET)

    suspend fun updateIdentity(request: UpdateIdentityRequest): ApiResult<CommunityIdentity> =
        caller.authenticated("v1/community/me", HttpMethodKind.PUT) { setBody(request) }

    // ---- profiles, addressed by alias

    suspend fun profile(alias: String): ApiResult<CommunityProfile> =
        caller.authenticated("v1/community/profiles/${alias.encodePath()}", HttpMethodKind.GET)

    /** Her posts past the few [profile] carries, newest first. */
    suspend fun profilePosts(alias: String, limit: Int = PROFILE_POSTS_PAGE, offset: Int = 0): ApiResult<Page<CommunityPost>> =
        caller.authenticated("v1/community/profiles/${alias.encodePath()}/posts?limit=$limit&offset=$offset", HttpMethodKind.GET)

    suspend fun setBlocked(alias: String, blocked: Boolean): ApiResult<BlockState> =
        caller.authenticated(
            "v1/community/profiles/${alias.encodePath()}/block",
            if (blocked) HttpMethodKind.PUT else HttpMethodKind.DELETE,
        )

    // ---- private messages

    /**
     * Her threads. "personal" is everything but consultations in which she is the doctor:
     * a doctor who also uses this app reads her patients in sadora-doctor, not here.
     */
    suspend fun conversations(
        scope: String = SCOPE_PERSONAL,
        limit: Int = CONVERSATION_PAGE,
        before: Instant? = null,
    ): ApiResult<List<Conversation>> {
        val after = before?.let { "&before=${it.toString().encodeURLQueryComponent()}" }.orEmpty()
        return caller.authenticated("v1/community/conversations?scope=$scope&limit=$limit$after", HttpMethodKind.GET)
    }

    /** Reads the thread's latest [limit] lines, and reading it marks it read on the server. */
    suspend fun thread(conversationId: String, limit: Int = MESSAGE_PAGE): ApiResult<ConversationThread> =
        caller.authenticated("v1/community/conversations/$conversationId?limit=$limit", HttpMethodKind.GET)

    /** The lines before [beforeId], scrolling up. Does not mark anything read. */
    suspend fun olderMessages(conversationId: String, beforeId: String, limit: Int = MESSAGE_PAGE): ApiResult<MessagePage> =
        caller.authenticated(
            "v1/community/conversations/$conversationId/messages?before=$beforeId&limit=$limit",
            HttpMethodKind.GET,
        )

    suspend fun startConversation(alias: String, body: String): ApiResult<ConversationThread> =
        caller.authenticated("v1/community/conversations", HttpMethodKind.POST) {
            setBody(StartConversationRequest(alias, body))
        }

    /** One line: text, a photo with an optional caption, or her record — see [SendMessageRequest]. */
    suspend fun sendMessage(conversationId: String, request: SendMessageRequest): ApiResult<DirectMessage> =
        caller.authenticated("v1/community/conversations/$conversationId/messages", HttpMethodKind.POST) {
            setBody(request)
        }

    suspend fun sendMessage(conversationId: String, body: String): ApiResult<DirectMessage> =
        sendMessage(conversationId, SendMessageRequest(body))

    /**
     * A photo's bytes. Authenticated like everything else and never cached by the
     * server's headers, so the phone keeps them in memory only, for as long as it runs.
     */
    suspend fun messageImage(conversationId: String, messageId: String): ApiResult<ByteArray> =
        caller.authenticated(
            "v1/community/conversations/$conversationId/messages/$messageId/image",
            HttpMethodKind.GET,
        )

    /**
     * The record she attached, as the doctor reads it — so she can see exactly what she
     * shared. She may always read her own; the doctor only while the consultation is open.
     */
    suspend fun messageRecord(conversationId: String, messageId: String, language: String): ApiResult<DoctorSummary> =
        caller.authenticated(
            "v1/community/conversations/$conversationId/messages/$messageId/record?lang=$language",
            HttpMethodKind.GET,
        )

    /** "She is typing." The other side sees it for a few seconds; the caller throttles it. */
    suspend fun typing(conversationId: String): ApiResult<Ack> =
        caller.authenticated("v1/community/conversations/$conversationId/typing", HttpMethodKind.POST) {
            setBody(JsonObject(emptyMap()))
        }

    /**
     * Opens a consultation with a verified doctor — or opens it again once it has closed —
     * and returns the thread. One that is still open comes back as it is.
     */
    suspend fun startConsultation(doctorId: String, body: String? = null): ApiResult<ConversationThread> =
        caller.authenticated("v1/doctors/$doctorId/consultations", HttpMethodKind.POST) {
            setBody(StartConsultationRequest(body))
        }

    /**
     * Her stars for the doctor's last window. 409 when she has already rated it, 400 while
     * the doctor has not answered — the thread's `canRate` says when it may be offered.
     */
    suspend fun rateConsultation(conversationId: String, rating: Int, review: String?): ApiResult<Ack> =
        caller.authenticated("v1/community/conversations/$conversationId/rating", HttpMethodKind.POST) {
            setBody(RateConsultationRequest(rating, review))
        }

    suspend fun reportConversation(conversationId: String, reason: ReportReason, note: String?): ApiResult<Ack> =
        caller.authenticated("v1/community/conversations/$conversationId/report", HttpMethodKind.POST) {
            setBody(ReportRequest(reason, note))
        }

    /** An alias has spaces; a path segment may not. */
    private fun String.encodePath(): String = encodeURLPathPart()

    suspend fun feed(
        topic: CommunityTopic? = null,
        savedOnly: Boolean = false,
        limit: Int = 50,
        offset: Int = 0,
    ): ApiResult<Page<CommunityPost>> {
        val query = buildList {
            topic?.let { add("topic=${it.name.lowercase()}") }
            if (savedOnly) add("saved=true")
            add("limit=$limit")
            add("offset=$offset")
        }.joinToString("&")
        return caller.authenticated("v1/community/posts?$query", HttpMethodKind.GET)
    }

    suspend fun createPost(topic: CommunityTopic, body: String): ApiResult<CommunityPost> =
        caller.authenticated("v1/community/posts", HttpMethodKind.POST) {
            setBody(CreatePostRequest(topic, body))
        }

    suspend fun deletePost(id: String): ApiResult<Ack> =
        caller.authenticated("v1/community/posts/$id", HttpMethodKind.DELETE)

    /** A page of a post's comments, doctors' answers first. */
    suspend fun comments(postId: String, limit: Int = COMMENT_PAGE, offset: Int = 0): ApiResult<List<CommunityComment>> =
        caller.authenticated("v1/community/posts/$postId/comments?limit=$limit&offset=$offset", HttpMethodKind.GET)

    suspend fun addComment(postId: String, body: String): ApiResult<CommunityComment> =
        caller.authenticated("v1/community/posts/$postId/comments", HttpMethodKind.POST) {
            setBody(CreateCommentRequest(body))
        }

    suspend fun setLiked(postId: String, liked: Boolean): ApiResult<LikeState> =
        caller.authenticated(
            "v1/community/posts/$postId/like",
            if (liked) HttpMethodKind.PUT else HttpMethodKind.DELETE,
        )

    suspend fun setSaved(postId: String, saved: Boolean): ApiResult<SaveState> =
        caller.authenticated(
            "v1/community/posts/$postId/save",
            if (saved) HttpMethodKind.PUT else HttpMethodKind.DELETE,
        )

    suspend fun reportPost(postId: String, reason: ReportReason, note: String?): ApiResult<Ack> =
        caller.authenticated("v1/community/posts/$postId/report", HttpMethodKind.POST) {
            setBody(ReportRequest(reason, note))
        }

    suspend fun reportComment(commentId: String, reason: ReportReason, note: String?): ApiResult<Ack> =
        caller.authenticated("v1/community/comments/$commentId/report", HttpMethodKind.POST) {
            setBody(ReportRequest(reason, note))
        }

    companion object {
        const val SCOPE_PERSONAL = "personal"

        /** Pages: the server caps each, and a phone that sends none gets the old whole lists. */
        const val CONVERSATION_PAGE = 30
        const val MESSAGE_PAGE = 50
        const val COMMENT_PAGE = 50
        const val FEED_PAGE = 30
        const val PROFILE_POSTS_PAGE = 20
    }
}
