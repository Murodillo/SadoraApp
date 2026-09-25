package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What the user practised. The app offers breathing today; the rest follow. */
@Serializable
enum class MindPracticeKind {
    @SerialName("breathing") BREATHING,
    @SerialName("meditation") MEDITATION,
    @SerialName("grounding") GROUNDING,
}

/**
 * A journal entry.
 *
 * The app labels this "Faqat siz ko'rasiz" and means it: entries are never summarised
 * for anyone else, never reach the admin API, and are excluded from AI context unless
 * the user has separately consented to AI insights.
 */
@Serializable
data class JournalEntry(
    val id: String,
    val date: LocalDate,
    val body: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class SaveJournalEntryRequest(
    val date: LocalDate,
    val body: String,
)

@Serializable
data class UpdateJournalEntryRequest(val body: String)

@Serializable
data class MindPractice(
    val id: String,
    val kind: MindPracticeKind,
    val durationSeconds: Int,
    val completedAt: Instant,
)

@Serializable
data class LogPracticeRequest(
    val kind: MindPracticeKind = MindPracticeKind.BREATHING,
    val durationSeconds: Int,
)

/** The Mind tab's check-in. All three dials are optional — she may set only one. */
@Serializable
data class MindCheckIn(
    val mood: MoodLevel? = null,
    val energy: Int? = null,
    val stress: Int? = null,
)

/**
 * Everything the Mind tab shows on open.
 *
 * [averageMood] is a plain arithmetic mean over the window, presented as a number and
 * nothing more — the screen states co-occurrence, never cause.
 */
@Serializable
data class MindSummary(
    val today: LocalDate,
    val checkIn: MindCheckIn,
    val recentEntries: List<JournalEntry> = emptyList(),
    val recentPractices: List<MindPractice> = emptyList(),
    val averageMood: Double? = null,
    val averageEnergy: Double? = null,
    val averageStress: Double? = null,
    /** Days in the window that carry any check-in at all. */
    val daysLogged: Int = 0,
    val windowDays: Int = 14,
)
