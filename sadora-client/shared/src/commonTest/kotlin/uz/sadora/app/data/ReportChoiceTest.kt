package uz.sadora.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uz.sadora.app.model.AppLanguage
import uz.sadora.app.ui.modules.ReportChoice
import uz.sadora.contract.Language
import uz.sadora.contract.ReportSection

class ReportChoiceTest {
    @Test
    fun `the report starts with every section in her own language and keeps the catalogue order`() {
        val choice = ReportChoice(AppLanguage.Ru)
        assertEquals(Language.RU, choice.request().language)
        assertEquals(ReportSection.entries, choice.request().sections)

        choice.toggle(ReportSection.CYCLE)
        choice.toggle(ReportSection.LABS)
        choice.toggle(ReportSection.CYCLE)
        assertEquals(ReportSection.entries - ReportSection.LABS, choice.request().sections)
    }

    @Test
    fun `no section left means no button`() {
        val choice = ReportChoice(AppLanguage.Uz)
        ReportSection.entries.forEach(choice::toggle)
        assertFalse(choice.ready)
        choice.toggle(ReportSection.MOOD)
        assertTrue(choice.ready)
    }

    @Test
    fun `an allowance refusal opens the paywall instead of an error strip`() {
        assertTrue(ApiFailure.LimitReached("doctor_report_pdf", "month", "").isAllowance())
        assertTrue(ApiFailure.PremiumRequired("doctor_report_pdf", "").isAllowance())
        assertFalse(ApiFailure.Network("").isAllowance())
    }
}
