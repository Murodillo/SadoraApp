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
 * What an alias has earned in the room. Computed from her activity every time it is
 * read, never stored, so nothing has to be recounted when a post is deleted.
 *
 * The thresholds live on the server; the app only draws them.
 */
@Serializable
enum class CommunityBadge {
    /** Joined in the last week. */
    @SerialName("newcomer") NEWCOMER,
    /** Among the first few hundred aliases the room ever had. */
    @SerialName("early") EARLY,
    /** Has written several posts. */
    @SerialName("writer") WRITER,
    /** Answers others: many comments. */
    @SerialName("helper") HELPER,
    /** Her posts have gathered many likes. */
    @SerialName("loved") LOVED,
    /** Around for months. */
    @SerialName("veteran") VETERAN,
}

/**
 * The name she posts under.
 *
 * Generated once per account and never chosen, so it cannot be a real name, and never
 * changed, so a thread stays readable. Nothing in it or around it links back to the
 * account: the wire carries the alias, the server keeps the join to itself.
 *
 * [unreadMessages] rides along so the chat header can show a count without a second
 * request on every open.
 */
@Serializable
data class CommunityIdentity(
    val alias: String,
    /** Which of the app's avatar tints to draw behind the initial. */
    val tint: Int,
    val bio: String? = null,
    /** Whether other aliases may open a conversation with her. */
    val dmOpen: Boolean = true,
    val badges: List<CommunityBadge> = emptyList(),
    val unreadMessages: Int = 0,
)

/** Edits to her own alias profile. A null field is left as it is; an empty bio clears it. */
@Serializable
data class UpdateIdentityRequest(
    val bio: String? = null,
    val dmOpen: Boolean? = null,
)

/**
 * An alias as others see her: the page behind a name in the feed.
 *
 * Addressed by alias, which is unique and permanent. [canMessage] folds every reason a
 * conversation could be refused — her own profile, a block either way, her door closed
 * — so the button is drawn or not, and the tap is never a surprise.
 */
@Serializable
data class CommunityProfile(
    val alias: String,
    val tint: Int,
    val bio: String? = null,
    val badges: List<CommunityBadge> = emptyList(),
    val postCount: Int = 0,
    val commentCount: Int = 0,
    val likesReceived: Int = 0,
    val memberSince: Instant,
    val isMe: Boolean = false,
    val canMessage: Boolean = false,
    /** True when the viewer has blocked this alias; the button then offers to unblock. */
    val blocked: Boolean = false,
    /** Her recent visible posts, newest first. */
    val posts: List<CommunityPost> = emptyList(),
)

/** One thread as the list shows it: who, the last line, and how much is unread. */
@Serializable
data class Conversation(
    val id: String,
    val alias: String,
    val tint: Int,
    val badges: List<CommunityBadge> = emptyList(),
    val lastMessage: String? = null,
    val lastMessageAt: Instant,
    val unread: Int = 0,
    /** Blocked by either side; the thread stays readable but takes no more messages. */
    val blocked: Boolean = false,
)

@Serializable
data class DirectMessage(
    val id: String,
    val body: String,
    val createdAt: Instant,
    val isMine: Boolean,
)

/** A thread opened: the conversation and its messages, oldest first. Reading marks it read. */
@Serializable
data class ConversationThread(
    val conversation: Conversation,
    val messages: List<DirectMessage> = emptyList(),
)

/** Opens a conversation with an alias, or finds the existing one, and sends the first line. */
@Serializable
data class StartConversationRequest(
    val alias: String,
    val body: String,
)

@Serializable
data class SendMessageRequest(val body: String)

/** The viewer's block on an alias, as the block and unblock calls return it. */
@Serializable
data class BlockState(val blocked: Boolean)

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
    /** The author's badges, so the card can show one or two next to the alias. */
    val badges: List<CommunityBadge> = emptyList(),
    /**
     * Set when a verified doctor wrote it. [alias] then holds her name, and the app
     * opens her doctor page rather than an alias profile.
     */
    val doctor: DoctorAuthor? = null,
    /** How many verified doctors have answered under it. */
    val doctorAnswers: Int = 0,
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
    val badges: List<CommunityBadge> = emptyList(),
    /** A verified doctor's answer; the thread lists these first. */
    val doctor: DoctorAuthor? = null,
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
