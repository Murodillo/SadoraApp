package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * "Ask Yaqinim to pay": she asks the person close to her to pay for Premium or a
 * consultation, and when they pay, it is hers.
 *
 * The person who follows her in the app hears of it by push and pays inside their own
 * app; anyone else gets a browser link she shares herself. One request is open at a time,
 * and it closes when it is paid, when she takes it back, when they say "not now", or after
 * [PaymentRequestLimits.OPEN_DAYS] days.
 */
object PaymentRequestLimits {
    /** How long a request stays open. */
    const val OPEN_DAYS = 7
    /** When an unanswered request gets its one reminder. */
    const val REMIND_AFTER_HOURS = 48
    const val NOTE_MAX = 140
}

@Serializable
enum class PaymentRequestKind {
    @SerialName("premium") PREMIUM,
    @SerialName("consultation") CONSULTATION,
    /** A legendary pet as a present. */
    @SerialName("pet") PET,
    /** A paid avatar frame as a present. */
    @SerialName("frame") FRAME,
}

@Serializable
enum class PaymentRequestStatus {
    @SerialName("open") OPEN,
    @SerialName("paid") PAID,
    /** They said "not now". She is told it closed, not that it was refused. */
    @SerialName("declined") DECLINED,
    @SerialName("cancelled") CANCELLED,
    @SerialName("expired") EXPIRED,
}

@Serializable
data class CreatePaymentRequest(
    val kind: PaymentRequestKind,
    /** Premium: how long she asks for. */
    val period: BillingPeriod? = null,
    /** Consultation: the doctor. */
    val doctorId: String? = null,
    /** A line of her own, up to [PaymentRequestLimits.NOTE_MAX] characters. */
    val note: String? = null,
    /** Pet: which legendary pet. */
    val pet: PetKind? = null,
    /** Frame: which paid frame ([AvatarFrames] key). */
    val frame: String? = null,
)

@Serializable
data class PaymentRequest(
    val id: String,
    val kind: PaymentRequestKind,
    val status: PaymentRequestStatus,
    /** Premium: what she asked for, or what was paid. */
    val period: BillingPeriod? = null,
    /** Consultation: who with. */
    val doctorName: String? = null,
    /** Pet: which one. */
    val pet: PetKind? = null,
    /** Frame: which one ([AvatarFrames] key). */
    val frame: String? = null,
    val amountMinor: Long,
    val currency: String = "UZS",
    val note: String? = null,
    /** Whether the person who follows her in the app was told by push. */
    val sentToPartner: Boolean = false,
    /** The browser link; only on the answer that made or rotated it. */
    val shareUrl: String? = null,
    val createdAt: Instant,
    val expiresAt: Instant,
    val closedAt: Instant? = null,
)

/** A request as the person who is asked sees it. */
@Serializable
data class IncomingPaymentRequest(
    val id: String,
    val linkId: String,
    /** Her name. */
    val fromName: String,
    val kind: PaymentRequestKind,
    val period: BillingPeriod? = null,
    val doctorName: String? = null,
    val pet: PetKind? = null,
    val frame: String? = null,
    val amountMinor: Long,
    val currency: String = "UZS",
    val note: String? = null,
    val createdAt: Instant,
    val expiresAt: Instant,
    /** Premium: the gift plans, so the payer can switch month and year. */
    val plans: List<BillingPlan> = emptyList(),
    /** Pet: its price row and store products, so a store build can sell it in its own sheet. */
    val petProduct: PetProduct? = null,
    /** Frame: its price row and store products, the same way. */
    val frameProduct: FrameProduct? = null,
    /** What this payer can pay with here. */
    val providers: List<PaymentProvider> = emptyList(),
)

/** The payer's answer: pay by Payme or Click. [planId] may switch to the other gift plan. */
@Serializable
data class PayPaymentRequest(
    val provider: PaymentProvider,
    val planId: String? = null,
)

/** The payer's store receipt for a gift plan. */
@Serializable
data class PaymentRequestStorePurchase(
    val provider: PaymentProvider,
    val productId: String,
    val token: String,
)

/** Whether the person who follows her takes requests to pay. */
@Serializable
data class PaymentRequestSwitch(val enabled: Boolean)

/** Her side in one read: the request open now, or the one that closed in the last days. */
@Serializable
data class PaymentRequestState(val current: PaymentRequest? = null)
