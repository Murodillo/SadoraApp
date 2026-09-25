package uz.sadora.contract

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * A visit, scan or test she is keeping track of.
 *
 * The app never invents these. A screening schedule differs by clinic and by country,
 * and an appointment the app suggested but nobody booked reads exactly like one that
 * was — so the list holds only what she put in it.
 */
@Serializable
data class Appointment(
    val id: String,
    val title: String,
    val scheduledOn: LocalDate,
    /** Null when the clinic named a day but not an hour. */
    val scheduledAt: LocalTime? = null,
    val place: String? = null,
    val note: String? = null,
    /** Hours before it that she wants reminding; null means no reminder. */
    val remindHoursBefore: Int? = null,
    val completedAt: Instant? = null,
    val createdAt: Instant,
) {
    val isDone: Boolean get() = completedAt != null
}

@Serializable
data class SaveAppointmentRequest(
    val title: String,
    val scheduledOn: LocalDate,
    val scheduledAt: LocalTime? = null,
    val place: String? = null,
    val note: String? = null,
    val remindHoursBefore: Int? = null,
)

/** Marking one done, or undoing that. */
@Serializable
data class CompleteAppointmentRequest(val done: Boolean)
