package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import uz.sadora.contract.CycleHistory
import uz.sadora.contract.CyclePrediction
import uz.sadora.contract.DoctorSummary
import uz.sadora.contract.Language
import uz.sadora.contract.LifeStage
import uz.sadora.contract.PredictionConfidence
import uz.sadora.contract.SharedCycle
import uz.sadora.contract.SharedDay
import uz.sadora.contract.SharedPerson
import uz.sadora.contract.SharedSymptom
import uz.sadora.contract.SymptomSeverity
import uz.sadora.server.share.DoctorPage

/**
 * The page a doctor sees is built from her records with string concatenation, so the
 * one thing that must never happen is a record that becomes markup. Everything she can
 * type — her name, a symptom label, a medication — goes through the escaper.
 */
class DoctorPageTest {

    private val summary = DoctorSummary(
        generatedAt = Instant.parse("2026-09-12T08:00:00Z"),
        language = Language.UZ,
        person = SharedPerson(
            name = "<script>alert(1)</script> Malika & Co",
            age = 32,
            lifeStage = LifeStage.CYCLE,
            memberSince = LocalDate.parse("2026-09-01"),
        ),
        cycle = SharedCycle(
            today = LocalDate.parse("2026-09-12"),
            cycleDay = 9,
            history = CycleHistory(cycles = emptyList(), prediction = CyclePrediction(PredictionConfidence.NONE, "no_data")),
        ),
        days = listOf(
            SharedDay(
                date = LocalDate.parse("2026-09-11"),
                symptoms = listOf(SharedSymptom("x", "Bosh og'rig'i <b>", SymptomSeverity.SEVERE)),
            ),
        ),
    )

    @Test
    fun `nothing she typed becomes markup`() {
        val html = DoctorPage.render(summary, Language.UZ)
        assertFalse("<script>" in html, "her name was rendered as markup")
        assertContains(html, "&lt;script&gt;")
        assertContains(html, "Malika &amp; Co")
        assertContains(html, "Bosh og&#39;rig&#39;i &lt;b&gt;")
    }

    @Test
    fun `every language renders, and says what the page is not`() {
        Language.entries.forEach { language ->
            val html = DoctorPage.render(summary, language)
            assertTrue(html.startsWith("<!doctype html>"), language.name)
            assertContains(html, "noindex")
            assertContains(html, "<title>SADORA")
        }
        assertContains(DoctorPage.render(summary, Language.RU), "Это не диагноз")
        assertContains(DoctorPage.render(summary, Language.EN), "It is not a diagnosis")
        assertContains(DoctorPage.gone(Language.EN), "expired")
    }
}
