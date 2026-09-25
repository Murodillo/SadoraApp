package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import io.ktor.http.encodeURLPathPart
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
import uz.sadora.contract.CreateCommentRequest
import uz.sadora.contract.CreatePostRequest
import uz.sadora.contract.LikeState
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

    suspend fun setBlocked(alias: String, blocked: Boolean): ApiResult<BlockState> =
        caller.authenticated(
            "v1/community/profiles/${alias.encodePath()}/block",
            if (blocked) HttpMethodKind.PUT else HttpMethodKind.DELETE,
        )

    // ---- private messages

    suspend fun conversations(): ApiResult<List<Conversation>> =
        caller.authenticated("v1/community/conversations", HttpMethodKind.GET)

    suspend fun thread(conversationId: String): ApiResult<ConversationThread> =
        caller.authenticated("v1/community/conversations/$conversationId", HttpMethodKind.GET)

    suspend fun startConversation(alias: String, body: String): ApiResult<ConversationThread> =
        caller.authenticated("v1/community/conversations", HttpMethodKind.POST) {
            setBody(StartConversationRequest(alias, body))
        }

    suspend fun sendMessage(conversationId: String, body: String): ApiResult<DirectMessage> =
        caller.authenticated("v1/community/conversations/$conversationId/messages", HttpMethodKind.POST) {
            setBody(SendMessageRequest(body))
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

    suspend fun comments(postId: String): ApiResult<List<CommunityComment>> =
        caller.authenticated("v1/community/posts/$postId/comments", HttpMethodKind.GET)

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
}
