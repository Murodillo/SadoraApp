package uz.sadora.doctor.data

import kotlin.time.Instant
import uz.sadora.contract.DoctorAccount
import uz.sadora.contract.DoctorStatus

/**
 * What the panel draws, decided in one place from her account.
 *
 * The screen only renders the case it is handed, so which card a status earns — and
 * what each card needs from the account — is a plain function the tests can pin.
 */
sealed interface PanelState {
    /** The account has not arrived yet. */
    data object Loading : PanelState

    /** Never applied: the offer and the button into the form. */
    data object Intro : PanelState

    /** Waiting for an admin. */
    data class Pending(val submittedAt: Instant?) : PanelState

    /** Sent back with the admin's reason; she may apply again, prefilled. */
    data class Rejected(val note: String?) : PanelState

    /** Taken off the feed by an admin. Nothing to do here but read why. */
    data class Suspended(val note: String?) : PanelState

    /** Verified: her header, her details, and the questions waiting for a doctor. */
    data class Approved(val account: DoctorAccount) : PanelState
}

fun panelStateOf(account: DoctorAccount?): PanelState = when (account?.status) {
    null -> PanelState.Loading
    DoctorStatus.NONE -> PanelState.Intro
    DoctorStatus.PENDING -> PanelState.Pending(account.submittedAt)
    DoctorStatus.REJECTED -> PanelState.Rejected(account.reviewNote)
    DoctorStatus.SUSPENDED -> PanelState.Suspended(account.reviewNote)
    DoctorStatus.APPROVED -> PanelState.Approved(account)
}

/** Whether the application form may be opened: before a first application, or after a rejection. */
val DoctorAccount.canApply: Boolean
    get() = status == DoctorStatus.NONE || status == DoctorStatus.REJECTED
