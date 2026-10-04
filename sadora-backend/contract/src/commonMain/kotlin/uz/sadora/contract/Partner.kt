package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * "Yaqinim": the one person she lets see how she is.
 *
 * She makes a short code, the person she trusts types it into their own Sadora, and she
 * approves the request. From then on that person sees a small, plain-language view of
 * the parts she ticked — never her notes, her intimate records, her weight, her mood
 * questionnaire, her chats or her consultations. What is not ticked is not in the
 * server's answer at all; the app is not trusted to hide it.
 *
 * One person may see her at a time. The person seeing her may follow up to
 * [Limits.PARTNER_MAX_FOLLOWING] people — a mother with two daughters.
 */

/** Whether the account tracks her own health or only follows someone else's. */
@Serializable
enum class AccountKind {
    @SerialName("self") SELF,
    /** Signed up with a Yaqinim code: no tracking of their own, only the people they follow. */
    @SerialName("partner") PARTNER,
}

/** Who the person is to her. Only changes the wording. */
@Serializable
enum class PartnerRelation {
    @SerialName("husband") HUSBAND,
    @SerialName("mother") MOTHER,
    @SerialName("sister") SISTER,
    @SerialName("friend") FRIEND,
    @SerialName("other") OTHER,
}

@Serializable
enum class PartnerLinkStatus {
    /** A code exists and nobody has typed it yet. */
    @SerialName("invited") INVITED,
    /** The person typed it; she has not said yes. */
    @SerialName("pending") PENDING,
    @SerialName("active") ACTIVE,
    /** She stopped showing for now; the person sees that it is paused, nothing else. */
    @SerialName("paused") PAUSED,
}

/**
 * What she shows. The defaults are the answer to "what would a husband need to be kind
 * this week" and nothing more; the rest she turns on herself.
 */
@Serializable
data class PartnerPermissions(
    /** Cycle day, phase, the period now and the next one. */
    val cycle: Boolean = true,
    /** The fertile window and ovulation. Off unless she turns it on. */
    val fertile: Boolean = false,
    /** Today's mood and energy, when she marked them. */
    val mood: Boolean = false,
    /** Today's symptoms, by name. */
    val symptoms: Boolean = false,
    /** Pregnancy week and due date; after the birth, the baby's age. */
    val pregnancy: Boolean = true,
    /** The doctor visits ahead: the title, day and place — never her note. */
    val appointments: Boolean = true,
    /** Stage care: feeds after the birth, hot flushes in menopause. */
    val care: Boolean = false,
)

@Serializable
data class CreatePartnerInviteRequest(
    val relation: PartnerRelation = PartnerRelation.HUSBAND,
)

/**
 * The code she hands over. [code] and [url] exist only on the response that made it —
 * the server keeps a hash — so a later read shows that an invite is out, not what it is.
 */
@Serializable
data class PartnerInvite(
    val code: String? = null,
    val url: String? = null,
    val relation: PartnerRelation,
    val createdAt: Instant,
    val expiresAt: Instant,
)

/** The person who sees her, as she sees them in her settings. */
@Serializable
data class PartnerLink(
    val id: String,
    val status: PartnerLinkStatus,
    val partnerName: String,
    val relation: PartnerRelation,
    val permissions: PartnerPermissions,
    val createdAt: Instant,
    val acceptedAt: Instant? = null,
    val approvedAt: Instant? = null,
    val pausedAt: Instant? = null,
    /** When they last opened her view; she is told, so nothing about it is hidden. */
    val lastViewedAt: Instant? = null,
    /** What they sent that she has not opened yet. */
    val unread: Int = 0,
)

/** Someone this account follows. */
@Serializable
data class FollowedPerson(
    val linkId: String,
    val name: String,
    val relation: PartnerRelation,
    val status: PartnerLinkStatus,
    /** What she sent that this account has not opened yet. */
    val unread: Int = 0,
)

/** Her side and the follower side in one read, for the Yaqinim screen. */
@Serializable
data class PartnerState(
    /** An invite still waiting to be typed in. */
    val invite: PartnerInvite? = null,
    /** The person who sees her, once they have typed the code. */
    val link: PartnerLink? = null,
    /** The people this account sees. */
    val following: List<FollowedPerson> = emptyList(),
    /** Her live browser link, if one is out. */
    val webLink: PartnerWebLink? = null,
)

@Serializable
data class AcceptPartnerInviteRequest(
    val code: String,
    /** Their name, for an account that has none yet. */
    val name: String? = null,
    /**
     * True from the "Yaqinim taklif qildi" sign-up: a new account becomes a follower-only
     * account. Ignored for an account that already tracks her own health.
     */
    val asPartnerAccount: Boolean = false,
)

@Serializable
data class PausePartnerRequest(val paused: Boolean)

/**
 * What the person sees. Built from her records at the moment it is asked for, and only
 * from the parts she ticked; a section she did not tick is null, not empty.
 */
@Serializable
data class PartnerView(
    val linkId: String,
    val status: PartnerLinkStatus,
    val name: String,
    val relation: PartnerRelation,
    val permissions: PartnerPermissions,
    val generatedAt: Instant,
    /** Her calendar day, which may not be theirs. */
    val today: LocalDate,
    /**
     * Her stage as far as she shows it. Trying to conceive reads as the cycle unless the
     * fertile window is shown; a pregnancy is not named unless the pregnancy part is on.
     */
    val stage: LifeStage? = null,
    val cycle: PartnerCycle? = null,
    val pregnancy: PartnerPregnancy? = null,
    val day: PartnerDay? = null,
    val appointments: List<PartnerAppointment> = emptyList(),
    val care: PartnerCare? = null,
) {
    /** Nothing to show: paused, waiting, or every part switched off. */
    val isEmpty: Boolean
        get() = cycle == null && pregnancy == null && day == null && appointments.isEmpty() && care == null
}

@Serializable
data class PartnerCycle(
    val cycleDay: Int? = null,
    /**
     * The phase. Without the fertile permission a fertile day reads as follicular, so the
     * phase cannot be used to read the window she kept to herself.
     */
    val phase: CyclePhase? = null,
    val periodNow: Boolean = false,
    /** Day of the period now, 1 on its first day. */
    val periodDay: Int? = null,
    val nextPeriodStart: LocalDate? = null,
    val daysUntilNextPeriod: Int? = null,
    val averageCycleLength: Int? = null,
    /** Only with the fertile permission. */
    val fertileFrom: LocalDate? = null,
    val fertileUntil: LocalDate? = null,
    val ovulationOn: LocalDate? = null,
    val confidence: PredictionConfidence = PredictionConfidence.NONE,
)

@Serializable
data class PartnerPregnancy(
    val week: Int? = null,
    val dueDate: LocalDate? = null,
    val daysToGo: Int? = null,
    /** After the birth. */
    val childBirthDate: LocalDate? = null,
    val babyAgeDays: Int? = null,
)

/** Today, as she marked it. */
@Serializable
data class PartnerDay(
    val mood: MoodLevel? = null,
    val energy: Int? = null,
    /** Labels in the follower's language. */
    val symptoms: List<String> = emptyList(),
)

@Serializable
data class PartnerAppointment(
    val title: String,
    val scheduledOn: LocalDate,
    val scheduledAt: LocalTime? = null,
    val place: String? = null,
)

@Serializable
data class PartnerCare(
    /** Feeds since midnight, her time. */
    val feedsToday: Int? = null,
    val lastFeedAt: Instant? = null,
    val hotFlushesToday: Int? = null,
)

// ---------------------------------------------------------------- messages

/**
 * The small things the two of them send each other. Most are one tap — a heart from him,
 * "a hot tea, please" from her — and [CUSTOM] carries a short line of their own. Not a
 * chat: no thread history beyond the last few, no media, nothing anyone else can read.
 */
@Serializable
enum class PartnerMessageKind {
    // From the person who follows her.
    @SerialName("heart") HEART,
    @SerialName("hug") HUG,
    @SerialName("thinking") THINKING,
    /** The answer to a request: "on my way". */
    @SerialName("on_it") ON_IT,
    /** The answer to a request: "done". */
    @SerialName("done") DONE,

    // From her.
    @SerialName("tea") TEA,
    @SerialName("sweets") SWEETS,
    @SerialName("rest") REST,
    @SerialName("call") CALL,
    @SerialName("quiet") QUIET,

    @SerialName("custom") CUSTOM;

    /** Something she asked for, which the follower's screen offers to answer. */
    val isRequest: Boolean
        get() = this == TEA || this == SWEETS || this == REST || this == CALL || this == QUIET
}

@Serializable
data class PartnerMessage(
    val id: String,
    val linkId: String,
    /** True when the account reading the list sent it. */
    val fromMe: Boolean,
    val kind: PartnerMessageKind,
    /** Only for [PartnerMessageKind.CUSTOM]. */
    val text: String? = null,
    val createdAt: Instant,
    val readAt: Instant? = null,
)

@Serializable
data class SendPartnerMessageRequest(
    val kind: PartnerMessageKind,
    val text: String? = null,
)

/** Newest first. [unread] counts what the other one sent that this account has not opened. */
@Serializable
data class PartnerMessages(
    val items: List<PartnerMessage> = emptyList(),
    val unread: Int = 0,
)

// ---------------------------------------------------------------- the web link

/**
 * For someone who will not install an app — a grandmother, a husband abroad on a work
 * phone: a link that opens the same view in a browser. It is a bearer link, so it lives
 * days, not forever, she can take it back at once, and she sees how often it was opened.
 * [url] exists only on the response that made it.
 */
@Serializable
data class PartnerWebLink(
    val id: String,
    val url: String? = null,
    val permissions: PartnerPermissions,
    val createdAt: Instant,
    val expiresAt: Instant,
    val viewCount: Int = 0,
    val lastViewedAt: Instant? = null,
)

@Serializable
data class CreatePartnerWebLinkRequest(
    val ttlHours: Int = 72,
    val permissions: PartnerPermissions = PartnerPermissions(),
)
