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
    /** What the stage tools recorded; null when she recorded nothing with them. */
    val stageRecords: SharedStageRecords? = null,
) {
    companion object {
        const val SHARE_WINDOW_DAYS = 90
    }
}

/**
 * The stage tools, summarised the way a clinician asks: how often she feeds, how long
 * ten kicks take, how far apart the contractions are, how many hot flushes a day, and
 * what the mood questionnaire scored. Each part covers the window that question needs,
 * and is absent when nothing was recorded in it.
 */
@Serializable
data class SharedStageRecords(
    val feeding: SharedFeeding? = null,
    /** Newest first. */
    val kickCounts: List<SharedKickCount> = emptyList(),
    val contractions: SharedContractions? = null,
    val hotFlushes: SharedHotFlushes? = null,
    /** Newest first. */
    val moodScreens: List<SharedMoodScreen> = emptyList(),
) {
    val isEmpty: Boolean
        get() = feeding == null && kickCounts.isEmpty() && contractions == null && hotFlushes == null && moodScreens.isEmpty()

    companion object {
        const val FEEDING_DAYS = 7
        const val KICK_DAYS = 14
        const val CONTRACTION_HOURS = 24
        const val HOT_FLUSH_DAYS = 30
        const val MOOD_SCREEN_DAYS = 120
        /** Ten kicks in two hours is the line; slower is one to ask about. */
        const val KICKS_GOAL = 10
        const val KICKS_SLOW_SECONDS = 7_200
    }
}

@Serializable
data class SharedFeeding(
    val windowDays: Int,
    val feeds: Int,
    val breastFeeds: Int,
    val averageBreastMinutes: Int? = null,
    val bottleFeeds: Int,
    val bottleMl: Int,
    val lastAt: Instant,
)

@Serializable
data class SharedKickCount(
    val at: Instant,
    val kicks: Int,
    val durationSeconds: Int,
) {
    val isSlow: Boolean
        get() = kicks < SharedStageRecords.KICKS_GOAL || durationSeconds > SharedStageRecords.KICKS_SLOW_SECONDS
}

@Serializable
data class SharedContractions(
    val windowHours: Int,
    val count: Int,
    val averageDurationSeconds: Int? = null,
    val averageIntervalSeconds: Int? = null,
    val lastAt: Instant,
)

@Serializable
data class SharedHotFlushes(
    val windowDays: Int,
    val count: Int,
    val strong: Int,
    /** Most often named first. */
    val triggers: List<SharedTriggerCount> = emptyList(),
)

@Serializable
data class SharedTriggerCount(val trigger: HotFlushTrigger, val count: Int)

@Serializable
data class SharedMoodScreen(
    val takenOn: LocalDate,
    val score: Int,
    /** Any answer but "never" to the self-harm question. */
    val selfHarm: Boolean,
)

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
