package uz.sadora.app.model

import kotlin.time.Duration
import kotlin.time.Instant
import uz.sadora.contract.ConsultationPatient
import uz.sadora.contract.ConsultationPayment
import uz.sadora.contract.DoctorAuthor

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
    /** The achievement badge the author chose to wear after her alias. */
    val worn: uz.sadora.contract.WornBadge? = null,
    /** The frame around the author's alias ([uz.sadora.contract.AvatarFrames] key). */
    val frame: String? = null,
    /** Her own post. The only thing that ever ties a post to her, and only on her phone. */
    val isMine: Boolean = false,
    /**
     * Set when a verified doctor wrote it: [alias] is then her real name, and the card
     * opens her doctor page instead of an alias profile.
     */
    val doctor: DoctorAuthor? = null,
    /** How many verified doctors have answered under it. */
    val doctorAnswers: Int = 0,
    /** How many other readers have had it on screen, as of the last read of the feed. */
    val viewCount: Int = 0,
)

data class CommunityComment(
    val alias: String,
    val tint: Int,
    val createdAt: Instant,
    val body: String,
    val isMine: Boolean = false,
    val badges: List<CommunityBadge> = emptyList(),
    /** The achievement badge the author wears after her alias. */
    val worn: uz.sadora.contract.WornBadge? = null,
    /** The frame around the author's alias. */
    val frame: String? = null,
    /** A verified doctor's answer; the thread draws it apart and lists it first. */
    val doctor: DoctorAuthor? = null,
    /** The server's id; empty on her optimistic copy and on samples. Pages are joined by it. */
    val id: String = "",
    /** Everyone's likes, hers among them when [liked]. */
    val likeCount: Int = 0,
    val liked: Boolean = false,
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
    /** The achievement badge she wears after her alias. */
    val worn: uz.sadora.contract.WornBadge? = null,
    /** The frame around her alias. */
    val frame: String? = null,
    val commentCount: Int,
    val likesReceived: Int,
    val memberSince: Instant,
    val isMe: Boolean,
    val canMessage: Boolean,
    val blocked: Boolean,
    val posts: List<CommunityPost>,
)

/**
 * One private thread in the list.
 *
 * Two kinds share it. Between two aliases [alias] is the other alias. In a consultation
 * with a verified doctor [consultation] is set, [doctor] names her, and [alias] is her
 * real name — the patient was told before she wrote that the doctor sees hers too.
 */
data class Conversation(
    val id: String,
    val alias: String,
    val tint: Int,
    val badges: List<CommunityBadge>,
    val lastMessage: String?,
    val lastMessageAt: Instant,
    val unread: Int,
    val blocked: Boolean,
    /** The doctor on the other side, when this is her consultation. */
    val doctor: DoctorAuthor? = null,
    /**
     * The patient on the other side, for a doctor reading her own consultations. This
     * app lists only her "personal" threads, so it stays null here; it is mapped anyway
     * so the model never quietly drops a field the wire carries.
     */
    val patient: ConsultationPatient? = null,
    val consultation: ConsultationWindow? = null,
    /** What the last line was, so the list can say "Rasm" rather than show an empty caption. */
    val lastMessageKind: MessageKind = MessageKind.Text,
    /** The last line is hers and the other side has read it: the double tick in the list. */
    val lastMessageRead: Boolean = false,
    /** The frame the other alias wears; never on a consultation. */
    val frame: String? = null,
) {
    val isConsultation: Boolean get() = consultation != null

    /**
     * Whether a line can go in now. A block closes any thread; a consultation also
     * closes when its window runs out or the doctor ends it.
     */
    val canWrite: Boolean get() = !blocked && consultation?.open != false
}

/**
 * A consultation's window: open for a day from when she starts it, until then or until
 * the doctor closes it. [open] is the server's verdict, so a phone with a wrong clock
 * still agrees with the doctor's panel about whether a message will go.
 */
data class ConsultationWindow(
    val openedAt: Instant,
    val expiresAt: Instant,
    val closedAt: Instant? = null,
    val open: Boolean,
    /** The window's own id: what a rating belongs to. */
    val sessionId: String? = null,
    /** Her price when this window opened, in tiyin; 0 when it was free. */
    val priceMinor: Long = 0,
    val payment: ConsultationPayment = ConsultationPayment.FREE,
    /** The doctor's advice from the last window she closed with one. */
    val summary: String? = null,
    /** The doctor answered and the window is not rated yet: the stars may be offered. */
    val canRate: Boolean = false,
    val rating: Int? = null,
    val answered: Boolean = false,
    /** The doctor's price now: what the next window costs, whatever this one did. */
    val doctorPriceMinor: Long = 0,
) {
    /** What is left of the window, never negative; zero once it is shut. */
    fun remaining(now: Instant): Duration =
        if (!open) Duration.ZERO else (expiresAt - now).coerceAtLeast(Duration.ZERO)

    /** The doctor ended it, as opposed to the day simply running out. */
    val closedByDoctor: Boolean get() = !open && closedAt != null
}

/** What a line carries. Mirrors the wire enum without depending on it. */
enum class MessageKind { Text, Image, Record, Prescription }

/** A photo's pixel size, known before the picture itself, so the bubble keeps its place. */
data class MessageImageSize(val width: Int, val height: Int) {
    /** Height over width, held to a range a bubble can draw: no ribbons, no towers. */
    val aspect: Float
        get() = if (width <= 0 || height <= 0) 1f else (height.toFloat() / width).coerceIn(0.4f, 1.6f)
}

data class DirectMessage(
    val id: String,
    /** The text, a photo's caption, or empty for an attached record. */
    val body: String,
    val createdAt: Instant,
    val isMine: Boolean,
    val kind: MessageKind = MessageKind.Text,
    /** Set on a photo. The bytes are fetched separately, by [id]. */
    val image: MessageImageSize? = null,
    /** Hers, and the other side has opened the thread since: the double tick. */
    val read: Boolean = false,
    /** Set on a doctor's prescription line. */
    val prescription: uz.sadora.contract.Prescription? = null,
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
