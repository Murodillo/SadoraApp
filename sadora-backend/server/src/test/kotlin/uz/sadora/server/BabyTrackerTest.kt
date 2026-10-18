package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import uz.sadora.contract.Baby
import uz.sadora.contract.BabySex
import uz.sadora.contract.GrowthMetric
import uz.sadora.contract.Language
import uz.sadora.contract.StageEvent
import uz.sadora.contract.StageEventKind
import uz.sadora.contract.VaccineStatus
import uz.sadora.server.baby.BabyMilestones
import uz.sadora.server.baby.BabyRecord
import uz.sadora.server.baby.BabyService
import uz.sadora.server.baby.VaccineCalendar
import uz.sadora.server.baby.VaccineReminderJob
import uz.sadora.server.baby.WhoGrowth

/**
 * The parts of the baby tracker that need no database: the WHO arithmetic, the calendar's
 * dates, how a night's sleep is split across days, and the catalogues' completeness.
 */
class BabyTrackerTest {

    // ---------------------------------------------------------------- WHO

    @Test
    fun `the median is the 50th percentile and the tables are WHO's`() {
        val birth = assertNotNull(WhoGrowth.lms(GrowthMetric.WEIGHT, BabySex.BOY, 0.0))
        assertEquals(3.3464, birth.m, 1e-4)
        assertEquals(3.2322, assertNotNull(WhoGrowth.lms(GrowthMetric.WEIGHT, BabySex.GIRL, 0.0)).m, 1e-4)
        assertEquals(49.8842, assertNotNull(WhoGrowth.lms(GrowthMetric.LENGTH, BabySex.BOY, 0.0)).m, 1e-4)
        assertEquals(50.0, assertNotNull(WhoGrowth.percentile(GrowthMetric.WEIGHT, BabySex.BOY, 0.0, 3.3464)), 0.1)
    }

    @Test
    fun `the curves match WHO's published z-score tables`() {
        // WHO weight-for-age, boys, birth: -2 SD 2.5 kg, +2 SD 4.4 kg (to one decimal).
        val p = assertNotNull(WhoGrowth.lms(GrowthMetric.WEIGHT, BabySex.BOY, 0.0))
        assertEquals(2.5, WhoGrowth.valueAt(p, -2.0), 0.05)
        assertEquals(4.4, WhoGrowth.valueAt(p, 2.0), 0.05)
        // The value at a z-score reads back as that z-score.
        assertEquals(1.3, WhoGrowth.zScore(p, WhoGrowth.valueAt(p, 1.3)), 1e-6)
        val curves = WhoGrowth.curves(GrowthMetric.HEAD, BabySex.GIRL)
        assertEquals(25, curves.size)
        curves.forEach { assertTrue(it.p3 < it.p15 && it.p15 < it.p50 && it.p50 < it.p85 && it.p85 < it.p97) }
    }

    @Test
    fun `a fractional age interpolates and past two years there is no percentile`() {
        val one = assertNotNull(WhoGrowth.lms(GrowthMetric.LENGTH, BabySex.GIRL, 1.0)).m
        val two = assertNotNull(WhoGrowth.lms(GrowthMetric.LENGTH, BabySex.GIRL, 2.0)).m
        assertEquals((one + two) / 2, assertNotNull(WhoGrowth.lms(GrowthMetric.LENGTH, BabySex.GIRL, 1.5)).m, 1e-9)
        assertNull(WhoGrowth.percentile(GrowthMetric.WEIGHT, BabySex.GIRL, 25.0, 12.0))
        assertEquals(97.7, WhoGrowth.normalCdf(2.0) * 100, 0.1)
    }

    // ---------------------------------------------------------------- vaccines

    private fun baby(born: LocalDate) = BabyRecord(
        kotlin.uuid.Uuid.random(),
        Baby("00000000-0000-0000-0000-000000000001", "Oysha", BabySex.GIRL, born, createdAt = kotlin.time.Clock.System.now()),
    )

    @Test
    fun `the calendar is dated from the birth and marks what is done, due and overdue`() {
        assertEquals(22, VaccineCalendar.doses.size)
        assertEquals(VaccineCalendar.doses.size, VaccineCalendar.doses.map { it.code }.distinct().size)
        val born = LocalDate(2026, 8, 1)
        val today = LocalDate(2026, 10, 10)
        val doses = BabyService.vaccines(baby(born), mapOf("hepb_1" to born), today, Language.UZ)
        val byCode = doses.associateBy { it.code }
        assertEquals(VaccineStatus.DONE, byCode.getValue("hepb_1").status)
        assertEquals(LocalDate(2026, 8, 3), byCode.getValue("bcg_1").dueOn)
        assertEquals(VaccineStatus.OVERDUE, byCode.getValue("bcg_1").status)
        assertEquals(LocalDate(2026, 10, 1), byCode.getValue("penta_1").dueOn)
        assertEquals(VaccineStatus.OVERDUE, byCode.getValue("penta_1").status)
        assertEquals(LocalDate(2026, 11, 1), byCode.getValue("penta_2").dueOn)
        assertEquals(VaccineStatus.UPCOMING, byCode.getValue("penta_2").status)
        assertEquals(LocalDate(2032, 8, 1), byCode.getValue("mmr_2").dueOn)
        assertEquals("2 oylik", byCode.getValue("penta_1").ageLabel)
        assertEquals(doses.sortedBy { it.dueOn }.map { it.dueOn }, doses.map { it.dueOn }, "in date order")
    }

    @Test
    fun `every dose speaks three languages`() {
        VaccineCalendar.doses.forEach { dose ->
            listOf(dose.names.uz, dose.names.ru, dose.names.en).forEach { assertTrue(it.isNotBlank(), dose.code) }
            assertTrue('‘' !in dose.names.uz && 'ʻ' !in dose.names.uz, "plain apostrophes, as the app writes them")
        }
        assertEquals("Bugun emlash kuni" to "Oysha: Gepatit B — bugun.", VaccineReminderJob.words("Oysha", "Gepatit B", 0, Language.UZ))
    }

    // ---------------------------------------------------------------- the day

    @Test
    fun `a night's sleep is split at midnight and a running one counts to now`() {
        val zone = TimeZone.of("Asia/Tashkent")
        val day1 = LocalDate(2026, 10, 17)
        val day2 = LocalDate(2026, 10, 18)
        val midnight = day2.atStartOfDayIn(zone)
        fun event(kind: StageEventKind, at: kotlin.time.Instant, seconds: Int? = null, detail: String? = null) =
            StageEvent("x", kind, at, seconds, null, detail, at)
        val events = listOf(
            event(StageEventKind.SLEEP, midnight - 2.hours, seconds = 5 * 3600),
            event(StageEventKind.FEEDING, midnight + 1.hours, seconds = 600, detail = "left"),
            event(StageEventKind.DIAPER, midnight + 2.hours, detail = "both"),
            event(StageEventKind.SLEEP, midnight + 10.hours),
        )
        val days = BabyService.daysOf(events, day1, day2, zone, at = midnight + 11.hours)
        assertEquals(120, days[0].sleepMinutes)
        assertEquals(180 + 60, days[1].sleepMinutes)
        assertEquals(1, days[1].feeds)
        assertEquals(10, days[1].breastMinutes)
        assertEquals(1, days[1].wetDiapers)
        assertEquals(1, days[1].dirtyDiapers)
        assertEquals(0, days[0].feeds)
        assertTrue((midnight + 30.minutes) > midnight)
    }

    @Test
    fun `milestones are unique, ordered, and speak three languages`() {
        val all = BabyMilestones.all
        assertEquals(all.size, all.map { it.code }.distinct().size)
        assertEquals(all.sortedBy { it.fromMonth }.map { it.fromMonth }, all.map { it.fromMonth })
        all.forEach {
            assertTrue(it.fromMonth < it.toMonth, it.code)
            listOf(it.names.uz, it.names.ru, it.names.en, it.about.uz, it.about.ru, it.about.en).forEach { w -> assertTrue(w.isNotBlank()) }
        }
    }
}
