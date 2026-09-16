package uz.sadora.app.model

import kotlin.time.Instant

/**
 * One post in the secret chat.
 *
 * The author is a chosen alias rather than a profile: the whole point of the space is
 * that someone can ask about her own body without it being attached to her name, so
 * nothing here links back to an account.
 */
data class CommunityPost(
    val id: String,
    val alias: String,
    /** Which of the avatar tints to draw behind the initial. */
    val tint: Int,
    /** The room the post belongs to. */
    val topic: CommunityTopic,
    /** When it was posted. The screen words the age, in the language it is drawn in. */
    val createdAt: Instant,
    val body: String,
    /** Likes from everyone else. Her own like is counted on top of this. */
    val likes: Int,
    /** Loaded when the sheet opens; until then [commentCount] is what the card shows. */
    val comments: List<CommunityComment> = emptyList(),
    val commentCount: Int = comments.size,
    /** What the author has earned in the room; the card shows the first two. */
    val badges: List<CommunityBadge> = emptyList(),
    /** Her own post. The only thing that ever ties a post to her, and only on her phone. */
    val isMine: Boolean = false,
)

data class CommunityComment(
    val alias: String,
    val tint: Int,
    val createdAt: Instant,
    val body: String,
    val isMine: Boolean = false,
    val badges: List<CommunityBadge> = emptyList(),
)

/**
 * A badge an alias wears. Mirrors the wire enum without depending on it; the words
 * live in the strings, the rules on the server.
 */
enum class CommunityBadge { Newcomer, Early, Writer, Helper, Loved, Veteran }

/** An alias's page, as the screen draws it. */
data class AliasProfile(
    val alias: String,
    val tint: Int,
    val bio: String?,
    val badges: List<CommunityBadge>,
    val postCount: Int,
    val commentCount: Int,
    val likesReceived: Int,
    val memberSince: Instant,
    val isMe: Boolean,
    val canMessage: Boolean,
    val blocked: Boolean,
    val posts: List<CommunityPost>,
)

/** One private thread in the list. */
data class Conversation(
    val id: String,
    val alias: String,
    val tint: Int,
    val badges: List<CommunityBadge>,
    val lastMessage: String?,
    val lastMessageAt: Instant,
    val unread: Int,
    val blocked: Boolean,
)

data class DirectMessage(
    val id: String,
    val body: String,
    val createdAt: Instant,
    val isMine: Boolean,
)

/**
 * Why a post is being reported. Mirrors the wire enum without depending on it.
 *
 * The words for these live in [uz.sadora.app.i18n.CommunityStrings], not here:
 * an enum is one object for the process, and the language belongs to the screen.
 */
enum class ReportReason { Spam, Abuse, Misinformation, PersonalData, Other }

/** The rooms the feed is divided into. */
enum class CommunityTopic { All, Cycle, Pregnancy, Wellbeing, Body }

/** What the feed is currently showing: everything, what she saved, or what she wrote. */
enum class CommunityFilter { Feed, Saved, Mine }

/**
 * How the feed is ordered.
 *
 * [Active] puts the posts people are answering first, so a question that found its
 * thread is not buried under an hour of newer ones nobody has replied to yet.
 */
enum class CommunitySort { Newest, Active }
