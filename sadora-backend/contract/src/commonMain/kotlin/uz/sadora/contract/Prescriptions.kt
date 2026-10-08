package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A doctor's prescription, written inside an open consultation and sent as a message.
 *
 * It is advice, not a pharmacy document — the card says so — and it is fixed once sent:
 * the doctor cannot edit it, only cancel it with a reason, which archives whatever the
 * patient added from it. The patient turns it into ordinary medications with reminders
 * ("Tabletkalarimga qo'shish"), choosing the start date and shifting the times; the
 * name, dose and length of the course stay the doctor's.
 */
@Serializable
data class Prescription(
    val id: String,
    val conversationId: String,
    val messageId: String,
    /** The doctor who wrote it, as the patient sees her. */
    val doctor: DoctorAuthor,
    /** The patient's name, for the doctor's history; null on the patient's own copy. */
    val patientName: String? = null,
    val items: List<PrescriptionItem>,
    /** A note on the whole prescription: rest, water, when to come back. */
    val note: String? = null,
    val createdAt: Instant,
    val cancelledAt: Instant? = null,
    val cancelReason: String? = null,
    /** When the patient added it to her medications; the doctor sees that it was. */
    val addedAt: Instant? = null,
) {
    val cancelled: Boolean get() = cancelledAt != null
}

/** "Shakli" on the form; the apps word it, the unit after the dose is the doctor's own. */
@Serializable
enum class PrescriptionForm {
    @SerialName("tablet") TABLET,
    @SerialName("capsule") CAPSULE,
    @SerialName("syrup") SYRUP,
    @SerialName("drops") DROPS,
    @SerialName("injection") INJECTION,
    @SerialName("ointment") OINTMENT,
    @SerialName("powder") POWDER,
    @SerialName("other") OTHER,
}

/**
 * One medicine on a prescription.
 *
 * [startDay] and [days] are what make a sequence: "days 1–5 this one, from day 6 that
 * one" is two items, counted from the date the patient starts the course. A null [days]
 * is a course with no end.
 */
@Serializable
data class PrescriptionItem(
    val name: String,
    val form: PrescriptionForm = PrescriptionForm.TABLET,
    /** The amount: "1", "500", "5". */
    val dose: String,
    /** What the amount counts: "tabletka", "mg", "ml". */
    val unit: String? = null,
    /** Daily at these times, or every N days ([ScheduleKind.WEEKDAYS] is not offered). */
    val schedule: MedicationSchedule,
    val foodRelation: FoodRelation,
    val startDay: Int = 1,
    val days: Int? = null,
    val note: String? = null,
)

/** The doctor sends a prescription into the consultation's thread. */
@Serializable
data class SendPrescriptionRequest(
    val items: List<PrescriptionItem>,
    val note: String? = null,
)

@Serializable
data class CancelPrescriptionRequest(
    val reason: String,
)

/**
 * The patient adds a prescription to her medications.
 *
 * [items] are the ones she keeps, by their position on the prescription, each with the
 * times she takes it — the same number of times the doctor gave, shifted to her day.
 * Day 1 of the course is [startOn].
 */
@Serializable
data class AddPrescriptionRequest(
    val startOn: LocalDate,
    val items: List<AddPrescriptionItem>,
)

@Serializable
data class AddPrescriptionItem(
    val index: Int,
    val times: List<LocalTime>,
)

@Serializable
data class AddPrescriptionResult(
    val prescription: Prescription,
    val medications: List<Medication> = emptyList(),
)
