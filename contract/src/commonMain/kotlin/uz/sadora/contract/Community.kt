package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The rooms the secret chat is divided into. Mirrors `CommunityTopic` in the app. */
@Serializable
enum class CommunityTopic {
    @SerialName("cycle") CYCLE,
    @SerialName("pregnancy") PREGNANCY,
    @SerialName("wellbeing") WELLBEING,
    @SerialName("body") BODY,
}

/**
 * Why a post or comment was reported. A closed list so the moderation queue can be
 * sorted by it, with [OTHER] and a free-text note for everything the list misses.
 */
@Serializable
enum class ReportReason {
    @SerialName("spam") SPAM,
    @SerialName("abuse") ABUSE,
    @SerialName("misinformation") MISINFORMATION,
    @SerialName("personal_data") PERSONAL_DATA,
    @SerialName("other") OTHER,
}

/**
 * The name she posts under.
 *
 * Generated once per account and never chosen, so it cannot be a real name, and never
 * changed, so a thread stays readable. Nothing in it or around it links back to the
 * account: the wire carries the alias, the server keeps the join to itself.
 */
@Serializable
data class CommunityIdentity(
    val alias: String,
    /** Which of the app's avatar tints to draw behind the initial. */
    val tint: Int,
)

/**
 * One post as the reader sees it.
 *
 * [liked], [saved] and [isMine] are the caller's own relationship to the post, resolved
 * server-side so the feed needs one request rather than three. [isMine] is the only
 * ownership signal on the wire, and it is only ever true for the author herself.
 */
@Serializable
data class CommunityPost(
    val id: String,
    val topic: CommunityTopic,
    val alias: String,
    val tint: Int,
    val body: String,
    val createdAt: Instant,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val liked: Boolean = false,
    val saved: Boolean = false,
    val isMine: Boolean = false,
)

@Serializable
data class CommunityComment(
    val id: String,
    val postId: String,
    val alias: String,
    val tint: Int,
    val body: String,
    val createdAt: Instant,
    val isMine: Boolean = false,
)

@Serializable
data class CreatePostRequest(
    val topic: CommunityTopic,
    val body: String,
)

@Serializable
data class CreateCommentRequest(val body: String)

@Serializable
data class ReportRequest(
    val reason: ReportReason,
    val note: String? = null,
)

/** Returned by like and unlike, so the count on screen is the server's, not a guess. */
@Serializable
data class LikeState(val liked: Boolean, val likeCount: Int)

@Serializable
data class SaveState(val saved: Boolean)
