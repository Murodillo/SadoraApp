package uz.sadora.doctor.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uz.sadora.contract.DoctorStatus

/** Which card each doctor status earns on the panel, and what the card is handed. */
class PanelStateTest {

    @Test
    fun `no account yet is the loading state`() {
        assertEquals(PanelState.Loading, panelStateOf(null))
    }

    @Test
    fun `never applied is the intro with the button into the form`() {
        assertEquals(PanelState.Intro, panelStateOf(testAccount(DoctorStatus.NONE)))
    }

    @Test
    fun `pending carries the date it was submitted`() {
        assertEquals(PanelState.Pending(TestNow), panelStateOf(testAccount(DoctorStatus.PENDING)))
    }

    @Test
    fun `rejected and suspended carry the admin's note`() {
        assertEquals(
            PanelState.Rejected("Diplom rasmi xira"),
            panelStateOf(testAccount(DoctorStatus.REJECTED, note = "Diplom rasmi xira")),
        )
        assertEquals(
            PanelState.Suspended("Shikoyatlar ko'rib chiqilmoqda"),
            panelStateOf(testAccount(DoctorStatus.SUSPENDED, note = "Shikoyatlar ko'rib chiqilmoqda")),
        )
    }

    @Test
    fun `approved carries the whole account for the header and the edit card`() {
        val account = testAccount(DoctorStatus.APPROVED)
        assertEquals(PanelState.Approved(account), panelStateOf(account))
    }

    @Test
    fun `every status maps to a state other than loading`() {
        DoctorStatus.entries.forEach { status ->
            assertTrue(panelStateOf(testAccount(status)) != PanelState.Loading, "$status")
        }
    }

    @Test
    fun `only a first application or a rejected one may open the form`() {
        assertTrue(testAccount(DoctorStatus.NONE).canApply)
        assertTrue(testAccount(DoctorStatus.REJECTED).canApply)
        assertFalse(testAccount(DoctorStatus.PENDING).canApply)
        assertFalse(testAccount(DoctorStatus.APPROVED).canApply)
        assertFalse(testAccount(DoctorStatus.SUSPENDED).canApply)
    }
}
