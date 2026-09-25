package uz.sadora.server.health

import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import uz.sadora.contract.FoodRelation
import uz.sadora.contract.ScheduleKind
import uz.sadora.contract.Weekday

/** A medication as stored, with the schedule already parsed. */
data class MedicationRecord(
    val id: Uuid,
    val name: String,
    val emoji: String?,
    val dosage: String?,
    val unit: String?,
    val foodRelation: FoodRelation,
    val note: String?,
    val scheduleKind: ScheduleKind,
    val times: List<LocalTime>,
    val weekdays: List<Weekday>,
    val intervalDays: Int?,
    val remindersEnabled: Boolean,
    val startedOn: LocalDate,
    val endedOn: LocalDate?,
    val stockUnits: Int?,
    val active: Boolean,
    val createdAt: kotlin.time.Instant,
)

/**
 * Expands a schedule into the doses due on a given day.
 *
 * Doses are computed, never stored ahead. Materialising a year of rows would mean that
 * editing a course leaves stale ones behind, and that a schedule change silently
 * rewrites history; here only what the user did is recorded, and the plan is derived
 * fresh every time.
 */
object DoseSchedule {

    /**
     * What was due on a date — including for an archived course.
     *
     * `active` deliberately does not gate this. Archiving is what makes "did I take it in
     * March" answerable at all, and an earlier version checked the flag here, so the
     * moment a course was archived its whole history reconstructed as empty. The date
     * window is the only thing that decides: archiving sets `endedOn`, and from the day
     * after, nothing is due.
     */
    fun dosesOn(medication: MedicationRecord, date: LocalDate): List<LocalTime> {
        if (date < medication.startedOn) return emptyList()
        val endedOn = medication.endedOn
        if (endedOn != null && date > endedOn) return emptyList()
        // An inactive course with no end date should not run forever; treat it as over.
        if (!medication.active && endedOn == null) return emptyList()
        if (medication.times.isEmpty()) return emptyList()

        val dueToday = when (medication.scheduleKind) {
            ScheduleKind.DAILY -> true

            ScheduleKind.WEEKDAYS ->
                medication.weekdays.any { it.isoNumber == date.dayOfWeek.isoDayNumber }

            ScheduleKind.INTERVAL -> {
                val interval = medication.intervalDays ?: return emptyList()
                if (interval < 1) return emptyList()
                medication.startedOn.daysUntil(date) % interval == 0
            }
        }
        return if (dueToday) medication.times.sorted() else emptyList()
    }

    /**
     * Average doses per day for this schedule, used to turn a pack count into "N days
     * left". Averaged rather than exact because a weekday course does not consume stock
     * evenly, and the screen shows an approximate figure either way.
     */
    fun dosesPerDay(medication: MedicationRecord): Double = when (medication.scheduleKind) {
        ScheduleKind.DAILY -> medication.times.size.toDouble()
        ScheduleKind.WEEKDAYS ->
            medication.times.size * medication.weekdays.size.toDouble() / DAYS_IN_WEEK
        ScheduleKind.INTERVAL ->
            medication.intervalDays?.takeIf { it > 0 }
                ?.let { medication.times.size.toDouble() / it }
                ?: 0.0
    }

    /** Whole days of supply remaining, or null when she is not tracking a pack. */
    fun stockDaysLeft(medication: MedicationRecord): Int? {
        val units = medication.stockUnits ?: return null
        val perDay = dosesPerDay(medication)
        if (perDay <= 0.0) return null
        return kotlin.math.floor(units / perDay).toInt()
    }

    private const val DAYS_IN_WEEK = 7
}
