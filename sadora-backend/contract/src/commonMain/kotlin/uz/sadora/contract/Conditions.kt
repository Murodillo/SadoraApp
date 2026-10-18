package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * "Holatlarim": a long-term condition she tells the app about — polycystic ovary syndrome
 * or endometriosis — as diagnosed by a doctor or only suspected.
 *
 * The app never decides either. It changes what it tracks (a pain diary, PCOS signs), how
 * it forecasts (a range for an irregular cycle), and what it puts in front of a doctor;
 * a suspicion is answered with "show a doctor", never with a verdict.
 */
@Serializable
enum class HealthCondition {
    @SerialName("pcos") PCOS,
    @SerialName("endometriosis") ENDOMETRIOSIS,
}

@Serializable
enum class ConditionStatus {
    @SerialName("diagnosed") DIAGNOSED,
    @SerialName("suspected") SUSPECTED,
}

@Serializable
data class UserCondition(
    val condition: HealthCondition,
    val status: ConditionStatus,
    /** When the doctor said so, if she remembers; only for a diagnosis. */
    val diagnosedOn: LocalDate? = null,
    val updatedAt: Instant,
)

@Serializable
data class SetConditionRequest(
    val status: ConditionStatus,
    val diagnosedOn: LocalDate? = null,
)

/** What a condition is and what the app does about it, in her language. */
@Serializable
data class ConditionInfo(
    val condition: HealthCondition,
    val name: String,
    val about: String,
    /** What turns on when she marks it. */
    val tracks: String,
    /** Said to a woman who only suspects it. */
    val suspectedNote: String,
)

@Serializable
data class ConditionsState(
    val conditions: List<UserCondition> = emptyList(),
    val catalog: List<ConditionInfo> = emptyList(),
) {
    fun has(condition: HealthCondition): Boolean = conditions.any { it.condition == condition }
}

// ---------------------------------------------------------------- the pain diary

/** Where it hurt, as the body map draws it. */
@Serializable
enum class PainArea {
    @SerialName("lower_abdomen") LOWER_ABDOMEN,
    @SerialName("pelvis_left") PELVIS_LEFT,
    @SerialName("pelvis_right") PELVIS_RIGHT,
    @SerialName("lower_back") LOWER_BACK,
    @SerialName("legs") LEGS,
    @SerialName("rectum") RECTUM,
}

/** When it hurt: the questions an endometriosis consultation asks. */
@Serializable
enum class PainKind {
    @SerialName("period") PERIOD,
    @SerialName("ovulation") OVULATION,
    @SerialName("intercourse") INTERCOURSE,
    @SerialName("bowel") BOWEL,
    @SerialName("urination") URINATION,
    @SerialName("constant") CONSTANT,
}

/** One day of the pain diary. Level 0 is a day she says did not hurt — a fact too. */
@Serializable
data class PainLog(
    val date: LocalDate,
    val level: Int,
    val areas: List<PainArea> = emptyList(),
    val kinds: List<PainKind> = emptyList(),
    val painkiller: Boolean = false,
    val painkillerName: String? = null,
    /** She could not go to work or school, or do what she planned. */
    val missedActivities: Boolean = false,
    val note: String? = null,
    val updatedAt: Instant,
)

@Serializable
data class SavePainLogRequest(
    val level: Int,
    val areas: List<PainArea> = emptyList(),
    val kinds: List<PainKind> = emptyList(),
    val painkiller: Boolean = false,
    val painkillerName: String? = null,
    val missedActivities: Boolean = false,
    val note: String? = null,
)

/** A stretch of the diary summed: what the report and the doctor's page show. */
@Serializable
data class PainSummary(
    val days: Int,
    val daysLogged: Int,
    val painDays: Int,
    val averageLevel: Double? = null,
    val maxLevel: Int = 0,
    val painkillerDays: Int = 0,
    val missedDays: Int = 0,
    val byKind: Map<PainKind, Int> = emptyMap(),
    val byArea: Map<PainArea, Int> = emptyMap(),
)

// ---------------------------------------------------------------- the report

/**
 * Three or six months of a condition, month by month: the cycles, the pain, the signs and
 * the weight. Premium. Built to be read across a desk by her gynaecologist.
 */
@Serializable
data class ConditionReport(
    val months: Int,
    val from: LocalDate,
    val to: LocalDate,
    val conditions: List<UserCondition> = emptyList(),
    /** Each cycle that started in the window, oldest first; the last may still be running. */
    val cycles: List<ReportCycle> = emptyList(),
    val pain: PainSummary? = null,
    val monthly: List<ReportMonth> = emptyList(),
    /** Days each PCOS sign was logged, over the whole window. */
    val signs: List<ReportSign> = emptyList(),
    /** Her weighings, oldest first. */
    val weights: List<ReportWeight> = emptyList(),
)

@Serializable
data class ReportCycle(val startedOn: LocalDate, val length: Int? = null, val periodDays: Int? = null)

@Serializable
data class ReportMonth(
    /** "2026-08". */
    val month: String,
    val painDays: Int = 0,
    val averagePain: Double? = null,
    val maxPain: Int = 0,
    val missedDays: Int = 0,
    val signDays: Int = 0,
)

@Serializable
data class ReportSign(val key: String, val label: String, val days: Int)

@Serializable
data class ReportWeight(val date: LocalDate, val kg: Double)

/** What the doctor's page carries for a woman with a condition: the conditions and 90 days of pain. */
@Serializable
data class SharedConditions(
    val conditions: List<UserCondition> = emptyList(),
    val pain: PainSummary? = null,
)
