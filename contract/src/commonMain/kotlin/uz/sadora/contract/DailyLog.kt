package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mood on the five-step scale the app draws. Mirrors `Mood` in the app. */
@Serializable
enum class MoodLevel(val score: Int) {
    @SerialName("bad") BAD(1),
    @SerialName("low") LOW(2),
    @SerialName("ok") OK(3),
    @SerialName("good") GOOD(4),
    @SerialName("great") GREAT(5),
}

/**
 * How strongly a symptom was felt.
 *
 * Worded rather than numeric on the wire: the app's severity scale is spelled out in
 * words on the symptom sheet, and a bare 1–3 invites each client to invent its own
 * labels for them.
 */
@Serializable
enum class SymptomSeverity {
    @SerialName("mild") MILD,
    @SerialName("moderate") MODERATE,
    @SerialName("severe") SEVERE,
}

/** Which group a symptom belongs to on the sheet. */
@Serializable
enum class SymptomCategory {
    @SerialName("bleeding") BLEEDING,
    @SerialName("pain") PAIN,
    @SerialName("digestion") DIGESTION,
    @SerialName("skin") SKIN,
    @SerialName("mood") MOOD,
    @SerialName("sleep") SLEEP,
    @SerialName("energy") ENERGY,
    @SerialName("other") OTHER,
}

/**
 * A symptom the app can offer. Served from the backend rather than hardcoded so the list
 * can grow — and differ per life stage — without an app release.
 */
@Serializable
data class SymptomDefinition(
    val key: String,
    val label: String,
    val category: SymptomCategory,
    /** Stages that offer this symptom. Empty means every stage. */
    val lifeStages: List<LifeStage> = emptyList(),
    val sortOrder: Int = 0,
)

@Serializable
data class SymptomEntry(
    val key: String,
    val severity: SymptomSeverity = SymptomSeverity.MODERATE,
)

/**
 * Everything recorded for one calendar day.
 *
 * A single row per day rather than an event stream: the app's day sheet edits the day as
 * a whole, and "what did she record on the 14th" is the only question anything asks.
 */
@Serializable
data class DailyLog(
    val date: LocalDate,
    val flow: FlowLevel? = null,
    val mood: MoodLevel? = null,
    /** 1–5, the same shape as mood. */
    val energy: Int? = null,
    val symptoms: List<SymptomEntry> = emptyList(),
    val note: String? = null,
    val updatedAt: Instant? = null,
) {
    val isEmpty: Boolean
        get() = flow == null && mood == null && energy == null && symptoms.isEmpty() && note.isNullOrBlank()
}

/**
 * Replaces the whole day. A field left null clears it, which is what the day sheet's
 * "remove" gestures need — unlike the profile, where null means "unchanged".
 */
@Serializable
data class SaveDailyLogRequest(
    val flow: FlowLevel? = null,
    val mood: MoodLevel? = null,
    val energy: Int? = null,
    val symptoms: List<SymptomEntry> = emptyList(),
    val note: String? = null,
)

@Serializable
data class DailyLogRange(
    val from: LocalDate,
    val to: LocalDate,
    val logs: List<DailyLog>,
)
