package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.Ack
import uz.sadora.contract.PhotoUpload
import uz.sadora.contract.PhotoView

/**
 * Profile photos: hers, which only she and a doctor she consults ever see, and the
 * doctors', which everyone signed in does. Every image is behind the same bearer token
 * as the rest of the API.
 */
class PhotoApi(private val caller: ApiCaller) {

    /** Hers, upright and base64. The server crops, scales and re-encodes it. */
    suspend fun setMine(upload: PhotoUpload): ApiResult<PhotoView> =
        caller.authenticated("v1/me/photo", HttpMethodKind.PUT) { setBody(upload) }

    suspend fun removeMine(): ApiResult<Ack> =
        caller.authenticated("v1/me/photo", HttpMethodKind.DELETE)

    /**
     * A photo's bytes by the path a profile, a byline or a thread carried —
     * `/v1/doctors/<id>/photo?v=…`. [photoRequestPath] turns it into a request path.
     */
    suspend fun image(path: String): ApiResult<ByteArray> =
        caller.authenticated(path, HttpMethodKind.GET)
}

/**
 * The request path for a `photoUrl` from the server, or null when there is no photo.
 *
 * The server writes an absolute path, `/v1/...`; every other call here is relative to
 * the API base, `v1/...`. Sent as it came, the leading slash would drop whatever path
 * the base URL has — a proxy that mounts the API under `/api` — so it comes off and
 * the base URL is the prefix, as it is for everything else. A whole URL is left alone.
 */
fun photoRequestPath(photoUrl: String?): String? {
    val url = photoUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    return url.trimStart('/').takeIf { it.isNotEmpty() }
}
