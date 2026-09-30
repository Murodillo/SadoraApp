package uz.sadora.doctor.data

import io.ktor.client.request.setBody
import uz.sadora.contract.Ack
import uz.sadora.contract.CloseConsultationRequest
import uz.sadora.contract.CommunityComment
import uz.sadora.contract.CommunityPost
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.Conversation
import uz.sadora.contract.ConversationThread
import uz.sadora.contract.CreateCommentRequest
import uz.sadora.contract.CreatePostRequest
import uz.sadora.contract.DirectMessage
import uz.sadora.contract.DoctorSummary
import uz.sadora.contract.Language
import uz.sadora.contract.Page
import uz.sadora.contract.ReportReason
import uz.sadora.contract.ReportRequest
import uz.sadora.contract.SendMessageRequest

/**
 * The part of the community a doctor works in: the feed, a question and its thread, her
 * answer, her own posts, and her private conversations. The server signs whatever an approved doctor writes with her name
 * and the check mark, so nothing here says who is writing.
 */
class CommunityApi(private val caller: ApiCaller) {

    /** The feed's first page, newest first; [doctorsOnly] is the "Shifokorlar" filter. */
    suspend fun feed(doctorsOnly: Boolean): ApiResult<Page<CommunityPost>> =
        caller.authenticated("v1/community/posts?limit=50&doctors=$doctorsOnly", HttpMethodKind.GET)

    /** The consultations she holds as a doctor, most recent first. */
    suspend fun consultations(): ApiResult<List<Conversation>> =
        caller.authenticated("v1/community/conversations?scope=patients", HttpMethodKind.GET)

    /** One thread; reading it marks it read, and it says whether the patient is typing. */
    suspend fun conversation(id: String): ApiResult<ConversationThread> =
        caller.authenticated("v1/community/conversations/$id", HttpMethodKind.GET)

    suspend fun sendMessage(id: String, request: SendMessageRequest): ApiResult<DirectMessage> =
        caller.authenticated("v1/community/conversations/$id/messages", HttpMethodKind.POST) {
            setBody(request)
        }

    /** A photo in a thread, as the bytes the patient's phone sent. */
    suspend fun image(conversationId: String, messageId: String): ApiResult<ByteArray> =
        caller.authenticated("v1/community/conversations/$conversationId/messages/$messageId/image", HttpMethodKind.GET)

    /** The record a patient attached, assembled now; refused once the consultation closes. */
    suspend fun record(conversationId: String, messageId: String, language: Language): ApiResult<DoctorSummary> =
        caller.authenticated(
            "v1/community/conversations/$conversationId/messages/$messageId/record?lang=${language.name.lowercase()}",
            HttpMethodKind.GET,
        )

    suspend fun typing(id: String): ApiResult<Ack> =
        caller.authenticated("v1/community/conversations/$id/typing", HttpMethodKind.POST) { setBody(Ack()) }

    /**
     * Ends a consultation before its window runs out, with her advice for the patient if
     * she wrote one. On a window already over, the same call adds the advice it lacks.
     */
    suspend fun close(id: String, summary: String? = null): ApiResult<ConversationThread> =
        caller.authenticated("v1/community/conversations/$id/close", HttpMethodKind.POST) {
            setBody(CloseConsultationRequest(summary))
        }

    suspend fun report(id: String, reason: ReportReason): ApiResult<Ack> =
        caller.authenticated("v1/community/conversations/$id/report", HttpMethodKind.POST) {
            setBody(ReportRequest(reason))
        }

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
