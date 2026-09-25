package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Our own enum rather than `kotlinx.datetime.DayOfWeek` — the wire spelling is ours. */
@Serializable
enum class Weekday(val isoNumber: Int) {
    @SerialName("mon") MONDAY(1),
    @SerialName("tue") TUESDAY(2),
    @SerialName("wed") WEDNESDAY(3),
    @SerialName("thu") THURSDAY(4),
    @SerialName("fri") FRIDAY(5),
    @SerialName("sat") SATURDAY(6),
    @SerialName("sun") SUNDAY(7),
}

@Serializable
enum class ScheduleKind {
    @SerialName("daily") DAILY,
    @SerialName("weekdays") WEEKDAYS,
    @SerialName("interval") INTERVAL,
}

/** "Ovqatga nisbatan" on the form. */
@Serializable
enum class FoodRelation {
    @SerialName("any") ANY,
    @SerialName("before") BEFORE,
    @SerialName("with") WITH,
    @SerialName("after") AFTER,
}

@Serializable
enum class DoseStatus {
    @SerialName("pending") PENDING,
    @SerialName("taken") TAKEN,
    @SerialName("skipped") SKIPPED,
}

/**
 * When a medication is due.
 *
 * [times] carries every dose in a day, so a twice-daily course is one medication with
 * two times rather than two medications that happen to share a name.
 */
@Serializable
data class MedicationSchedule(
    val kind: ScheduleKind = ScheduleKind.DAILY,
    val times: List<LocalTime> = emptyList(),
    /** Only for [ScheduleKind.WEEKDAYS]. */
    val weekdays: List<Weekday> = emptyList(),
    /** Only for [ScheduleKind.INTERVAL] — every N days from [Medication.startedOn]. */
    val intervalDays: Int? = null,
)

@Serializable
data class Medication(
    val id: String,
    val name: String,
    val emoji: String? = null,
    val dosage: String? = null,
    val unit: String? = null,
    val foodRelation: FoodRelation = FoodRelation.ANY,
    val note: String? = null,
    val schedule: MedicationSchedule,
    val remindersEnabled: Boolean = true,
    val startedOn: LocalDate,
    val endedOn: LocalDate? = null,
    /** Doses left in the pack, when she chooses to track supply. */
    val stockUnits: Int? = null,
    /** Derived from [stockUnits] and the doses per day. Null when supply is untracked. */
    val stockDaysLeft: Int? = null,
    val active: Boolean = true,
    val createdAt: Instant,
)

@Serializable
data class SaveMedicationRequest(
    val name: String,
    val emoji: String? = null,
    val dosage: String? = null,
    val unit: String? = null,
    val foodRelation: FoodRelation = FoodRelation.ANY,
    val note: String? = null,
    val schedule: MedicationSchedule,
    val remindersEnabled: Boolean = true,
    val startedOn: LocalDate? = null,
    val endedOn: LocalDate? = null,
    val stockUnits: Int? = null,
)

/**
 * One dose on one day.
 *
 * Doses are derived from the schedule rather than stored ahead, so changing a course
 * from daily to three-times-weekly does not leave a trail of orphaned future rows. Only
 * what actually happened is recorded.
 */
@Serializable
data class MedicationDose(
    val medicationId: String,
    val name: String,
    val emoji: String? = null,
    val dosage: String? = null,
    val foodRelation: FoodRelation = FoodRelation.ANY,
    val dueOn: LocalDate,
    val dueAt: LocalTime,
    val status: DoseStatus = DoseStatus.PENDING,
    val takenAt: Instant? = null,
    /**
     * Taken well after it was due. Surfaced as a fact for the history chip and nothing
     * else — the product does not advise on a missed or late dose.
     */
    val isLate: Boolean = false,
)

@Serializable
data class MedicationDay(
    val date: LocalDate,
    val doses: List<MedicationDose> = emptyList(),
) {
    // Derived, not serialised: both are a count of [doses], and sending a total the
    // client can already see invites the two to disagree. Non-Kotlin clients count the
    // list themselves.
    val takenCount: Int get() = doses.count { it.status == DoseStatus.TAKEN }
    val totalCount: Int get() = doses.size
}

@Serializable
data class RecordDoseRequest(
    val dueOn: LocalDate,
    val dueAt: LocalTime,
    val status: DoseStatus,
)

@Serializable
data class RefillRequest(
    /** Doses added to the pack. Negative corrects an over-count. */
    val units: Int,
)

/**
 * Adherence over a window, as counts and nothing more.
 *
 * There is deliberately no advice field and no "you missed 3 doses" copy: the app is
 * specified never to counsel on a missed dose, and to point at the prescription or a
 * pharmacist instead.
 */
@Serializable
data class MedicationHistory(
    val medicationId: String,
    val from: LocalDate,
    val to: LocalDate,
    val days: List<MedicationDay> = emptyList(),
    val takenCount: Int = 0,
    val skippedCount: Int = 0,
    val pendingCount: Int = 0,
)
