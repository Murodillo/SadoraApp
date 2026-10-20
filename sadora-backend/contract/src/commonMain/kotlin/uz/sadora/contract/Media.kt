package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Photos and videos in messages (2026-10-20).
 *
 * A file goes up before the message that carries it. The phone starts an upload, sends
 * the bytes in pieces of [MediaUpload.chunkBytes] with `PUT /v1/media/uploads/{id}?offset=`,
 * finishes it, and only then sends the message with the finished items' ids. A piece
 * whose answer was lost is sent again at the same offset; an upload a dropped network
 * cut short carries on from [MediaUpload.receivedBytes].
 *
 * Nothing is ever at a public address. A thumbnail is read with the session like any
 * other request; the photo itself and a video are opened through [MediaLink], an address
 * signed for a few minutes, because a video player cannot carry the session's header.
 */
@Serializable
enum class MediaKind {
    @SerialName("photo") PHOTO,
    @SerialName("video") VIDEO,
}

/** What the phone is about to send: the photo it resized, or the video it compressed. */
@Serializable
data class StartMediaUploadRequest(
    val kind: MediaKind,
    /** `image/jpeg` or `image/png` for a photo; `video/mp4` or `video/quicktime` for a video. */
    val mimeType: String,
    val sizeBytes: Long,
)

/** An upload in progress: how much of it the server has. */
@Serializable
data class MediaUpload(
    val id: String,
    val kind: MediaKind,
    val sizeBytes: Long,
    val receivedBytes: Long,
    /** The size of every piece but the last. */
    val chunkBytes: Int,
)

/** Finishes an upload. A video brings its poster frame: a JPEG the phone took from it. */
@Serializable
data class CompleteMediaUploadRequest(
    val posterBase64: String? = null,
)

/** One photo or video of a message, in the order it was sent. */
@Serializable
data class MessageMedia(
    val id: String,
    val kind: MediaKind,
    /** As it is shown — a video recorded upright is taller than it is wide. */
    val width: Int,
    val height: Int,
    /** A video's length. */
    val durationMs: Long? = null,
    /** The file a viewer downloads when she opens it: the photo, or the whole video. */
    val sizeBytes: Long,
    /** A video may have come without a poster; a photo always has its thumbnail. */
    val hasThumbnail: Boolean = true,
)

/** A signed address for a photo or a video, good until [expiresAt]. Relative to the API's base. */
@Serializable
data class MediaLink(
    val url: String,
    val expiresAt: Instant,
)
