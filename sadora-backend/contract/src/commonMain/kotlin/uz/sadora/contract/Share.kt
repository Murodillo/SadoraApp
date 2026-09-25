package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * A profile share: a link she shows a doctor.
 *
 * The link carries a random token, not her id, and the token is stored hashed — so a
 * database read does not yield a working link, and a link that leaks is one she can
 * revoke without touching anything else. Every share expires; the default is a day,
 * which covers an appointment and not much more.
 */
@Serializable
data class ProfileShare(
    val id: String,
    /** The full URL the QR code encodes. Present only on the response that created it. */
    val url: String? = null,
    val createdAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant? = null,
    val viewCount: Int = 0,
    val lastViewedAt: Instant? = null,
) {
    fun isActive(now: Instant): Boolean = revokedAt == null && expiresAt > now
}

@Serializable
data class CreateShareRequest(
    /** How long the link lives. Clamped by the server to [Limits.SHARE_MAX_HOURS]. */
    val ttlHours: Int = 24,
)

/**
 * What a doctor sees when the QR code is scanned.
 *
 * One document rather than a bundle of endpoints, so the page can be rendered from
 * it directly and the same shape can be exported as JSON. Nothing in it is a verdict:
 * it is what she recorded and what her devices measured, laid out for someone who
 * can read it — which is why the journal and her private notes are not here.
 */
@Serializable
data class DoctorSummary(
    val generatedAt: Instant,
    val language: Language,
    val person: SharedPerson,
    val cycle: SharedCycle? = null,
    val pregnancy: SharedPregnancy? = null,
    /** The last [SHARE_WINDOW_DAYS] days of daily records, newest first. */
    val days: List<SharedDay> = emptyList(),
    /** How often each symptom came up over the window, most frequent first. */
    val symptomCounts: List<SharedSymptomCount> = emptyList(),
    val mind: SharedMind? = null,
    val medications: List<SharedMedication> = emptyList(),
    val appointments: List<Appointment> = emptyList(),
    val nutrition: SharedNutrition? = null,
    val wearable: SharedWearable? = null,
) {
    companion object {
        const val SHARE_WINDOW_DAYS = 90
    }
}

@Serializable
data class SharedPerson(
    val name: String,
    val age: Int? = null,
    val birthDate: LocalDate? = null,
    val heightCm: Int? = null,
    val weightKg: Int? = null,
    val lifeStage: LifeStage,
    val goals: List<Goal> = emptyList(),
    val memberSince: LocalDate,
)

@Serializable
data class SharedCycle(
    val today: LocalDate,
    val cycleDay: Int? = null,
    val phase: CyclePhase? = null,
    val lastPeriodStart: LocalDate? = null,
    val history: CycleHistory,
    /** Every period she recorded in the window, newest first. */
    val periods: List<PeriodEntry> = emptyList(),
)

@Serializable
data class SharedPregnancy(
    val dueDate: LocalDate? = null,
    val week: Int? = null,
    val childBirthDate: LocalDate? = null,
    /** Days in the window on which she reported less movement than usual. */
    val lessMovementDays: List<LocalDate> = emptyList(),
)

/** One day of her record, with the symptom keys already turned into labels. */
@Serializable
data class SharedDay(
    val date: LocalDate,
    val flow: FlowLevel? = null,
    val mood: MoodLevel? = null,
    val energy: Int? = null,
    val stress: Int? = null,
    val symptoms: List<SharedSymptom> = emptyList(),
    val fetalMovement: FetalMovement? = null,
)

@Serializable
data class SharedSymptom(
    val key: String,
    val label: String,
    val severity: SymptomSeverity,
)

@Serializable
data class SharedSymptomCount(
    val key: String,
    val label: String,
    val category: SymptomCategory,
    val days: Int,
)

@Serializable
data class SharedMind(
    val windowDays: Int,
    val daysLogged: Int,
    val averageMood: Double? = null,
    val averageEnergy: Double? = null,
    val averageStress: Double? = null,
    val journalEntries: Int = 0,
    val practiceMinutes: Int = 0,
)

@Serializable
data class SharedMedication(
    val name: String,
    val dosage: String? = null,
    val unit: String? = null,
    val schedule: MedicationSchedule,
    val foodRelation: FoodRelation,
    val startedOn: LocalDate,
    val endedOn: LocalDate? = null,
    val active: Boolean,
    /** Over the window: doses confirmed, skipped, and how many that makes. */
    val takenCount: Int = 0,
    val skippedCount: Int = 0,
    val adherencePercent: Int? = null,
)

@Serializable
data class SharedNutrition(
    val windowDays: Int,
    val daysLogged: Int,
    val averageKcal: Int? = null,
    val averageWaterMl: Int? = null,
    val goals: NutritionGoals,
)

/** Daily device metrics over the window, plus the averages a doctor scans first. */
@Serializable
data class SharedWearable(
    val providers: List<HealthProvider>,
    val days: List<DailyHealth>,
    val averages: List<DailyMetric> = emptyList(),
)
