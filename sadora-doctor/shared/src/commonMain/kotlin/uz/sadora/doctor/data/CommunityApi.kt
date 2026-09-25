package uz.sadora.doctor.data

import io.ktor.client.request.setBody
import uz.sadora.contract.CommunityComment
import uz.sadora.contract.CommunityPost
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.CreateCommentRequest
import uz.sadora.contract.CreatePostRequest

/**
 * The part of the community a doctor works in: a question and its thread, her answer,
 * and her own posts. The server signs whatever an approved doctor writes with her name
 * and the check mark, so nothing here says who is writing.
 */
class CommunityApi(private val caller: ApiCaller) {

    suspend fun post(id: String): ApiResult<CommunityPost> =
        caller.authenticated("v1/community/posts/$id", HttpMethodKind.GET)

    /** Doctors' answers first, then the rest oldest first — the server's order. */
    suspend fun comments(postId: String): ApiResult<List<CommunityComment>> =
        caller.authenticated("v1/community/posts/$postId/comments", HttpMethodKind.GET)

    suspend fun addComment(postId: String, body: String): ApiResult<CommunityComment> =
        caller.authenticated("v1/community/posts/$postId/comments", HttpMethodKind.POST) {
            setBody(CreateCommentRequest(body))
        }

    suspend fun createPost(topic: CommunityTopic, body: String): ApiResult<CommunityPost> =
        caller.authenticated("v1/community/posts", HttpMethodKind.POST) {
            setBody(CreatePostRequest(topic, body))
        }
}
