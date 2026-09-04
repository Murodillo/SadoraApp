package org.example.project.model

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
    /** Human-readable age, as the server would render it. */
    val ago: String,
    val body: String,
    /** Likes from everyone else. Her own like is counted on top of this. */
    val likes: Int,
    /** Loaded when the sheet opens; until then [commentCount] is what the card shows. */
    val comments: List<CommunityComment> = emptyList(),
    val commentCount: Int = comments.size,
    /** Her own post. The only thing that ever ties a post to her, and only on her phone. */
    val isMine: Boolean = false,
)

data class CommunityComment(
    val alias: String,
    val tint: Int,
    val ago: String,
    val body: String,
    val isMine: Boolean = false,
)

/** Why a post is being reported. Mirrors the wire enum without depending on it. */
enum class ReportReason(val label: String) {
    Spam("Spam yoki reklama"),
    Abuse("Haqorat yoki tahdid"),
    Misinformation("Xavfli tibbiy maslahat"),
    PersonalData("Shaxsiy ma'lumot oshkor qilingan"),
    Other("Boshqa sabab"),
}

/** The rooms the feed is divided into. */
enum class CommunityTopic(val label: String) {
    All("Hammasi"),
    Cycle("Sikl"),
    Pregnancy("Homiladorlik"),
    Wellbeing("Kayfiyat"),
    Body("Tana"),
}

/** What the feed is currently showing. */
enum class CommunityFilter(val label: String) {
    Feed("Lenta"),
    Saved("Saqlangan"),
}
