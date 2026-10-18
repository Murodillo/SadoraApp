package uz.sadora.contract

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Pelvic floor (Kegel) exercises: a program for her stage, levels that open as she keeps at
 * it, reminders at the times she picks, and a short check every four weeks so she and her
 * doctor can see whether anything changed.
 *
 * Levels 1–2 of every program are free — the gentle start is what a pregnant or postpartum
 * woman needs most, and it is never behind a paywall. Longer holds from level 3 are Premium.
 */
@Serializable
enum class KegelProgram {
    @SerialName("cycle") CYCLE,
    @SerialName("pregnancy") PREGNANCY,
    @SerialName("postpartum") POSTPARTUM,
    @SerialName("menopause") MENOPAUSE,
    ;

    companion object {
        fun forStage(stage: LifeStage): KegelProgram = when (stage) {
            LifeStage.CYCLE, LifeStage.TRYING_TO_CONCEIVE -> CYCLE
            LifeStage.PREGNANCY -> PREGNANCY
            LifeStage.POSTPARTUM -> POSTPARTUM
            LifeStage.PERIMENOPAUSE, LifeStage.MENOPAUSE -> MENOPAUSE
        }
    }
}

/** One level: [reps] squeezes held [holdSeconds] with [relaxSeconds] between, [sets] times, then quick flicks. */
@Serializable
data class KegelLevel(
    val level: Int,
    val holdSeconds: Int,
    val relaxSeconds: Int,
    val reps: Int,
    val sets: Int,
    /** One-second squeezes after each set, for the fast fibres; 0 for none. */
    val quickFlicks: Int = 0,
    val premium: Boolean = false,
) {
    val durationSeconds: Int
        get() = ((holdSeconds + relaxSeconds) * reps + quickFlicks * 2) * sets + (sets - 1) * SET_REST_SECONDS

    companion object {
        const val SET_REST_SECONDS = 20
    }
}

@Serializable
data class KegelState(
    /** The program she is on: her stage's, unless she picked another. */
    val program: KegelProgram,
    /** What her life stage suggests, so the screen can offer to go back to it. */
    val stageProgram: KegelProgram,
    val levels: List<KegelLevel> = emptyList(),
    /** The highest level she may start. */
    val unlockedLevel: Int = 1,
    /** Days with a session at the highest unlocked level, towards the next one. */
    val daysAtLevel: Int = 0,
    val daysToUnlock: Int = Limits.KEGEL_DAYS_TO_UNLOCK,
    val todaySessions: Int = 0,
    val dailyGoal: Int = 2,
    /** Days in a row with a session, ending today or yesterday. */
    val streakDays: Int = 0,
    val totalSessions: Int = 0,
    /** Days of the last four weeks with a session, for the little calendar. */
    val recentDays: List<LocalDate> = emptyList(),
    val reminders: List<LocalTime> = emptyList(),
    /** Four weeks since the last check, or none yet. */
    val checkDue: Boolean = false,
    /** Her checks, newest first. */
    val checks: List<KegelCheck> = emptyList(),
    /** Postpartum and her baby is under six weeks: the screen asks her to wait for her check-up. */
    val postpartumEarly: Boolean = false,
    /** Premium: levels from 3 are open to her once reached. */
    val advanced: Boolean = false,
)

@Serializable
data class LogKegelSessionRequest(
    val program: KegelProgram,
    val level: Int,
    /** Squeezes she finished; a session stopped halfway still counts what was done. */
    val reps: Int,
    val durationSeconds: Int,
)

@Serializable
data class KegelSessionResult(
    val state: KegelState,
    /** Gul for the session, when today's cap still pays. */
    val coins: CoinAward? = null,
    /** The level this session opened, if it did. */
    val unlockedLevel: Int? = null,
)

@Serializable
data class KegelSettingsRequest(
    /** Null goes back to her stage's program. */
    val program: KegelProgram? = null,
    /** Up to [Limits.KEGEL_MAX_REMINDERS] times of day; empty turns reminders off. */
    val reminders: List<LocalTime> = emptyList(),
)

/** When a leak happens. The app records; it never says what kind of incontinence it is. */
@Serializable
enum class LeakSituation {
    @SerialName("cough") COUGH,
    @SerialName("exercise") EXERCISE,
    @SerialName("urge") URGE,
    @SerialName("night") NIGHT,
    @SerialName("other") OTHER,
}

/**
 * The four-weekly check. [frequency]: 0 never, 1 about once a week or less, 2 two or three
 * times a week, 3 about once a day, 4 several times a day. [amount]: 0 none, 1 drops,
 * 2 a little, 3 more. [bother]: 0–10, how much it gets in the way of her life.
 */
@Serializable
data class KegelCheck(
    val takenOn: LocalDate,
    val frequency: Int,
    val amount: Int,
    val situations: List<LeakSituation> = emptyList(),
    val bother: Int,
)

@Serializable
data class SaveKegelCheckRequest(
    val frequency: Int,
    val amount: Int,
    val situations: List<LeakSituation> = emptyList(),
    val bother: Int,
)

/** What the doctor sees: whether she trains, at what level, and how the checks moved. */
@Serializable
data class SharedPelvicFloor(
    val program: KegelProgram,
    val level: Int,
    val sessions: Int,
    val activeDays: Int,
    val checks: List<KegelCheck> = emptyList(),
)
