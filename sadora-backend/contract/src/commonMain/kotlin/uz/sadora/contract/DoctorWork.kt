package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The doctor's working day and paid consultations (2026-09-30): her hours and price,
// shortcuts and notes, each consultation's session with its payment, summary and
// rating, her numbers and her earnings.

/** Where a consultation's money stands. */
@Serializable
enum class ConsultationPayment {
    /** Nothing to pay: her price is zero. */
    @SerialName("free") FREE,

    /** A checkout was started and has not come back paid. */
    @SerialName("pending") PENDING,
    @SerialName("paid") PAID,

    /** Paid, and ended with no answer from the doctor: the money is owed back. */
    @SerialName("refund_due") REFUND_DUE,

    /** An operator returned it through the provider's cabinet. */
    @SerialName("refunded") REFUNDED,
}

/** One weekday's hours, in minutes from midnight in the doctor's own time zone. 1 is Monday. */
@Serializable
data class DoctorHours(
    val weekday: Int,
    val startMinute: Int,
    val endMinute: Int,
)

/**
 * Whether she is answering now, as a patient sees it on her page. [onlineNow] folds her
 * hours and her "busy" switch; with no hours set it follows the switch alone.
 */
@Serializable
data class DoctorAvailability(
    val onlineNow: Boolean,
    val busy: Boolean = false,
    /** When she is next in her hours, if she is not now and has hours at all. */
    val nextAvailableAt: Instant? = null,
    val hours: List<DoctorHours> = emptyList(),
    val timezone: String = "Asia/Tashkent",
)

/** Her own settings for consultations: price, hours, the "busy" switch. */
@Serializable
data class DoctorSettings(
    /** In tiyin; 0 keeps her consultations free. */
    val priceMinor: Long = 0,
    val busy: Boolean = false,
    val hours: List<DoctorHours> = emptyList(),
    val timezone: String = "Asia/Tashkent",
    val acceptsConsultations: Boolean = true,
    /** Sadora's share of a paid consultation, as an operator set it. */
    val commissionPercent: Int = 0,
)

/** A null field is left as it is; [hours] replaces the whole week. */
@Serializable
data class UpdateDoctorSettingsRequest(
    val priceMinor: Long? = null,
    val busy: Boolean? = null,
    val hours: List<DoctorHours>? = null,
)

@Serializable
data class QuickReply(
    val id: String,
    val title: String,
    val body: String,
    val position: Int = 0,
)

@Serializable
data class SaveQuickReplyRequest(
    val title: String,
    val body: String,
    val position: Int = 0,
)

/** Her note on a patient: hers alone, never shown to the patient or to staff. */
@Serializable
data class PatientNote(
    val body: String = "",
    val updatedAt: Instant? = null,
)

@Serializable
data class SavePatientNoteRequest(val body: String)

/** One 24-hour window of a consultation, as the doctor's history of a patient lists it. */
@Serializable
data class ConsultationSession(
    val id: String,
    val openedAt: Instant? = null,
    val expiresAt: Instant? = null,
    val closedAt: Instant? = null,
    /** `doctor`, `expired` or `refund`. */
    val closedReason: String? = null,
    val priceMinor: Long = 0,
    val payment: ConsultationPayment = ConsultationPayment.FREE,
    val firstReplyAt: Instant? = null,
    val summary: String? = null,
    val rating: Int? = null,
    val review: String? = null,
    /** The records the patient attached in this window, by message id. */
    val recordMessageIds: List<String> = emptyList(),
)

/** Everything a doctor has had with one patient: every window, oldest first. */
@Serializable
data class PatientHistory(
    val patient: ConsultationPatient? = null,
    val sessions: List<ConsultationSession> = emptyList(),
)

/** Ends a consultation; the summary is what the patient keeps as her doctor's advice. */
@Serializable
data class CloseConsultationRequest(val summary: String? = null)

@Serializable
data class RateConsultationRequest(
    val rating: Int,
    val review: String? = null,
)

/** A patient's rating on a doctor's page. Anonymous: the room never learns who rated. */
@Serializable
data class DoctorReview(
    val rating: Int,
    val review: String? = null,
    val createdAt: Instant,
)

@Serializable
data class TopicCount(val topic: CommunityTopic, val count: Int)

/** Her numbers, on the doctor's own Home and the web panel's statistics page. */
@Serializable
data class DoctorStats(
    val consultationsWeek: Int = 0,
    val consultationsMonth: Int = 0,
    val consultationsTotal: Int = 0,
    val openNow: Int = 0,
    /** From a window opening to her first line in it, over the windows she answered. */
    val avgFirstReplyMinutes: Int? = null,
    /** Windows that ended without a word from her. */
    val unansweredTotal: Int = 0,
    val rating: Double? = null,
    val ratingCount: Int = 0,
    /** The topics of the questions she has answered in the room, most first. */
    val topTopics: List<TopicCount> = emptyList(),
    val answersTotal: Int = 0,
)

@Serializable
data class EarningLine(
    val sessionId: String,
    val patientName: String,
    val openedAt: Instant? = null,
    val priceMinor: Long,
    val commissionMinor: Long,
    val netMinor: Long,
    val payment: ConsultationPayment,
)

@Serializable
data class DoctorPayoutView(
    val id: String,
    val amountMinor: Long,
    val note: String? = null,
    val paidAt: Instant,
)

/** What she has earned, what Sadora has paid her, and what is still hers to be paid. */
@Serializable
data class DoctorEarnings(
    val currency: String = "UZS",
    val grossMinor: Long = 0,
    val commissionMinor: Long = 0,
    val netMinor: Long = 0,
    val paidOutMinor: Long = 0,
    val balanceMinor: Long = 0,
    val refundDueMinor: Long = 0,
    val lines: List<EarningLine> = emptyList(),
    val payouts: List<DoctorPayoutView> = emptyList(),
)

/** Paying for a consultation with a doctor whose price is not zero. */
@Serializable
data class ConsultationCheckoutRequest(val provider: PaymentProvider)
