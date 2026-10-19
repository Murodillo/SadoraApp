package uz.sadora.contract

import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Logging by voice: she says how today is, Gemini hears it, and the app shows what it understood
 * before anything is saved. The recording is never kept — it is read once and dropped.
 */
@Serializable
data class VoiceOptions(
    /** False when the server has no model: the button is hidden. */
    val available: Boolean = false,
    val allowance: FeatureEntitlement? = null,
)

@Serializable
data class VoiceLogRequest(
    val audioBase64: String,
    /** audio/aac from Android, audio/wav from iOS. */
    val mimeType: String,
)

@Serializable
enum class VoicePeriod {
    @SerialName("started") STARTED,
    @SerialName("ended") ENDED,
}

@Serializable
data class VoiceSymptom(val key: String, val label: String)

/** A medicine she said she took, matched to today's dose of it. */
@Serializable
data class VoiceDose(val medicationId: String, val name: String, val dueAt: LocalTime)

/** What was heard, for her to check. Nothing is saved until [SaveVoiceLogRequest]. */
@Serializable
data class VoiceLogDraft(
    val transcript: String = "",
    val understood: Boolean = true,
    val mood: MoodLevel? = null,
    val symptoms: List<VoiceSymptom> = emptyList(),
    val waterMl: Int? = null,
    val doses: List<VoiceDose> = emptyList(),
    val period: VoicePeriod? = null,
) {
    val isEmpty: Boolean get() = mood == null && symptoms.isEmpty() && waterMl == null && doses.isEmpty() && period == null
}

/** The draft as she kept it. */
@Serializable
data class SaveVoiceLogRequest(
    val mood: MoodLevel? = null,
    val symptoms: List<String> = emptyList(),
    val waterMl: Int? = null,
    val doses: List<VoiceDose> = emptyList(),
    val period: VoicePeriod? = null,
)
