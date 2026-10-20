package uz.sadora.doctor.data

import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import uz.sadora.contract.Ack
import uz.sadora.contract.CompleteMediaUploadRequest
import uz.sadora.contract.MediaLink
import uz.sadora.contract.MediaUpload
import uz.sadora.contract.MessageMedia
import uz.sadora.contract.StartMediaUploadRequest

/**
 * Photos and videos in a consultation: uploads in pieces, thumbnails with the session,
 * and the signed addresses a photo or a video itself is opened from — see [MediaLink].
 * The client app's, unchanged: the routes are the same for both sides of a thread.
 */
class MediaApi(private val caller: ApiCaller, private val baseUrl: String) : MediaTransport {

    override suspend fun start(request: StartMediaUploadRequest): ApiResult<MediaUpload> =
        caller.authenticated("v1/media/uploads", HttpMethodKind.POST) { setBody(request) }

    override suspend fun progress(uploadId: String): ApiResult<MediaUpload> =
        caller.authenticated("v1/media/uploads/$uploadId", HttpMethodKind.GET)

    /** One piece, as raw bytes: the JSON content type every other call carries is replaced. */
    override suspend fun put(uploadId: String, offset: Long, bytes: ByteArray): ApiResult<MediaUpload> =
        caller.authenticated("v1/media/uploads/$uploadId?offset=$offset", HttpMethodKind.PUT) {
            contentType(ContentType.Application.OctetStream)
            setBody(bytes)
        }

    override suspend fun complete(uploadId: String, posterBase64: String?): ApiResult<MessageMedia> =
        caller.authenticated("v1/media/uploads/$uploadId/complete", HttpMethodKind.POST) {
            setBody(CompleteMediaUploadRequest(posterBase64))
        }

    override suspend fun cancel(uploadId: String): ApiResult<Ack> =
        caller.authenticated("v1/media/uploads/$uploadId", HttpMethodKind.DELETE)

    /** A thumbnail or a poster: a small JPEG, never cached by the server's headers. */
    suspend fun thumb(mediaId: String): ApiResult<ByteArray> =
        caller.authenticated("v1/media/$mediaId/thumb", HttpMethodKind.GET)

    /** An address for the photo itself or the video, good for a few minutes. */
    suspend fun link(mediaId: String): ApiResult<MediaLink> =
        caller.authenticated("v1/media/$mediaId/link", HttpMethodKind.GET)

    /** The bytes behind a signed address: the full photo for the viewer. */
    suspend fun fetch(link: MediaLink): ApiResult<ByteArray> =
        caller.authenticated(link.url.removePrefix("/"), HttpMethodKind.GET)

    /** The signed address as a player opens it: whole, with the server's own host. */
    fun absolute(link: MediaLink): String = baseUrl.trimEnd('/') + "/" + link.url.removePrefix("/")
}
