package uz.sadora.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import uz.sadora.contract.CyclePhase
import uz.sadora.contract.CyclePrediction
import uz.sadora.contract.CycleStatus
import uz.sadora.contract.DailyHealth
import uz.sadora.contract.DailyMetric
import uz.sadora.contract.DoseStatus
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.Language
import uz.sadora.contract.LifeStage
import uz.sadora.contract.MedicationDay
import uz.sadora.contract.MedicationDose
import uz.sadora.contract.PredictionConfidence
import uz.sadora.contract.StageBaseline
import uz.sadora.contract.WaterState
import uz.sadora.contract.WidgetSummary

class WidgetContentTest {
    private val day = LocalDate(2026, 10, 16)
    private val morning = LocalDateTime(day, LocalTime(9, 0))
    private val prediction = CyclePrediction(PredictionConfidence.entries.first(), "test")

    private fun dose(name: String, at: LocalTime, status: DoseStatus = DoseStatus.PENDING, id: String = name) =
        MedicationDose(medicationId = id, name = name, dueOn = day, dueAt = at, status = status)

    private fun summary(
        stage: LifeStage = LifeStage.CYCLE,
        cycle: CycleStatus? = CycleStatus(
            cycleDay = 14,
            phase = CyclePhase.FERTILE,
            daysUntilNextPeriod = 14,
            prediction = prediction,
            today = day,
        ),
        baseline: StageBaseline? = null,
        premium: Boolean = true,
        discreet: Boolean = false,
        language: Language = Language.UZ,
    ) = WidgetSummary(
        today = day,
        lifeStage = stage,
        stage = baseline,
        cycle = cycle,
        water = WaterState(day, 1250, 2000),
        medications = MedicationDay(
            day,
            listOf(
                dose("Folat", LocalTime(8, 0), DoseStatus.TAKEN),
                dose("Temir", LocalTime(14, 0)),
                dose("Vitamin D", LocalTime(21, 0)),
            ),
        ),
        health = DailyHealth(
            day,
            listOf(
                DailyMetric(HealthMetric.STEPS, 6420.0, "count", 1),
                DailyMetric(HealthMetric.SLEEP_DURATION, 440.0, "min", 1),
            ),
        ),
        premium = premium,
        discreet = discreet,
        language = language,
    )

    private val all = WidgetConfig(WidgetSize.LARGE, listOf(WidgetBlock.WATER, WidgetBlock.DOSES, WidgetBlock.HEALTH))

    private fun build(
        summary: WidgetSummary = summary(),
        config: WidgetConfig = all,
        now: LocalDateTime = morning,
        pending: List<PendingAction> = emptyList(),
    ) = WidgetContent.build(WidgetSnapshot(summary, Instant.fromEpochSeconds(0), pending), config, now)

    @Test
    fun `a cycle widget shows the day, the phase and the days to her period`() {
        val stage = build().stage
        assertEquals("14", stage.big)
        assertEquals("Sikl kuni", stage.title)
        assertEquals("Unumdor kunlar", stage.line1)
        assertEquals("Hayzgacha 14 kun", stage.line2)
        assertEquals(CyclePhase.FERTILE, stage.phase)
    }

    @Test
    fun `each stage brings its own look, and discretion hides it`() {
        assertEquals(StageLook.CYCLE, build().stage.look)
        assertEquals(StageLook.PREGNANCY, build(summary(stage = LifeStage.PREGNANCY, cycle = null, baseline = StageBaseline(dueDate = LocalDate(2027, 3, 14)))).stage.look)
        assertEquals(StageLook.MENOPAUSE, build(summary(stage = LifeStage.MENOPAUSE, cycle = null)).stage.look)
        assertEquals(StageLook.DISCREET, build(summary(discreet = true)).stage.look, "a pregnancy palette would say what the words no longer do")
        val period = summary(cycle = CycleStatus(cycleDay = 2, phase = CyclePhase.PERIOD, daysUntilNextPeriod = 26, prediction = prediction, today = day))
        assertEquals(StageLook.PERIOD, build(period).stage.look)
    }

    @Test
    fun `a widget not refreshed since yesterday counts the new day itself`() {
        val content = build(now = LocalDateTime(LocalDate(2026, 10, 17), LocalTime(7, 0)))
        assertEquals("15", content.stage.big)
        assertEquals("Hayzgacha 13 kun", content.stage.line2)
        assertEquals(0, content.water?.ml, "a new day starts with no water")
        assertNull(content.doses, "yesterday's doses are not today's")
        assertTrue(content.needsRefresh)
    }

    @Test
    fun `the phase turns with the day, the period ends and the fertile days come`() {
        // Day 5 of a 5-day period, fertile from day 12 to 16, next period on day 29.
        val withWindow = summary(
            cycle = CycleStatus(
                cycleDay = 5,
                phase = CyclePhase.PERIOD,
                daysUntilNextPeriod = 24,
                prediction = CyclePrediction(
                    PredictionConfidence.entries.first(), "t",
                    nextPeriodStart = LocalDate(2026, 11, 9),
                    fertileFrom = LocalDate(2026, 10, 23),
                    fertileUntil = LocalDate(2026, 10, 27),
                    averagePeriodLength = 5,
                    averageCycleLength = 28,
                ),
                today = day,
            ),
        )
        fun phaseOn(date: LocalDate) = build(withWindow, now = LocalDateTime(date, LocalTime(9, 0))).stage.phase
        assertEquals(CyclePhase.FOLLICULAR, phaseOn(LocalDate(2026, 10, 17)), "the day after the period")
        assertEquals(CyclePhase.FERTILE, phaseOn(LocalDate(2026, 10, 24)))
        assertEquals(CyclePhase.LUTEAL, phaseOn(LocalDate(2026, 10, 30)))
        assertEquals(CyclePhase.PERIOD, phaseOn(LocalDate(2026, 11, 10)))
    }

    @Test
    fun `a period past its predicted day is called late, not negative`() {
        val content = build(now = LocalDateTime(LocalDate(2026, 11, 1), LocalTime(7, 0)))
        assertEquals("Hayz 2 kun kechikmoqda", content.stage.line2)
        val today = build(now = LocalDateTime(LocalDate(2026, 10, 30), LocalTime(7, 0)))
        assertEquals("Hayz bugun kutilmoqda", today.stage.line2)
    }

    @Test
    fun `no recorded period asks for one`() {
        val stage = build(summary(cycle = CycleStatus(prediction = prediction, today = day))).stage
        assertEquals("—", stage.big)
        assertEquals("Hayz kunini belgilang", stage.line2)
    }

    @Test
    fun `a pregnancy widget counts weeks from the due date`() {
        val content = build(summary(stage = LifeStage.PREGNANCY, cycle = null, baseline = StageBaseline(dueDate = LocalDate(2027, 3, 14))))
        assertEquals("18", content.stage.big)
        assertEquals("hafta", content.stage.unit)
        assertEquals("Homiladorlik", content.stage.title)
        assertEquals("18 hafta 5 kun", content.stage.line1)
        assertEquals("Tug'ruqqacha 149 kun", content.stage.line2)
        assertNull(content.stage.phase)
    }

    @Test
    fun `a postpartum widget counts the baby's weeks`() {
        val content = build(summary(stage = LifeStage.POSTPARTUM, cycle = null, baseline = StageBaseline(birthDate = LocalDate(2026, 9, 1))))
        assertEquals("6", content.stage.big)
        assertEquals("Chaqaloq 45 kunlik", content.stage.line2)
    }

    @Test
    fun `water and doses read as the widget draws them, the next dose first`() {
        val content = build()
        assertEquals("1.25 / 2 L", content.water?.label)
        assertEquals(250, content.water?.stepMl)
        val doses = content.doses!!
        assertEquals("1 / 3", doses.label)
        assertEquals("Temir", doses.next?.name)
        assertEquals("14:00", doses.next?.time)
        assertEquals(listOf("Folat", "Temir", "Vitamin D"), doses.rows.map { it.name })
    }

    @Test
    fun `the wearable block shows the metrics she picked, in words`() {
        val health = build().health!!
        assertEquals(listOf("Qadam" to "6\u202F420", "Uyqu" to "7 s 20 daq"), health.items.map { it.label to it.value })
        val none = build(summary().copy(health = null)).health!!
        assertTrue(none.connectPrompt)
    }

    @Test
    fun `a tap waiting in the queue is already counted`() {
        val content = build(
            pending = listOf(
                PendingAction.Water(250, "tap-1", day),
                PendingAction.Dose("Temir", day, LocalTime(14, 0), DoseStatus.TAKEN),
            ),
        )
        assertEquals(1500, content.water?.ml)
        assertEquals("2 / 3", content.doses?.label)
        assertEquals("Vitamin D", content.doses?.next?.name)
    }

    @Test
    fun `discreet mode leaves a number and no words about her body`() {
        val content = build(summary(discreet = true))
        assertEquals("14", content.stage.big)
        assertNull(content.stage.line1)
        assertNull(content.stage.line2)
        assertEquals("Sadora", content.stage.title)
        assertEquals(listOf("Dori 1", "Dori 2", "Dori 3"), content.doses!!.rows.map { it.name })
        val words = listOfNotNull(content.stage.title, content.stage.line1, content.stage.line2, content.stage.unit)
        assertFalse(words.any { "Hayz" in it || "Sikl" in it })
    }

    @Test
    fun `the lock screen is discreet unless she said otherwise`() {
        assertNull(build(config = all.copy(lockScreen = true)).stage.line2)
        assertEquals("Hayzgacha 14 kun", build(config = all.copy(lockScreen = true, revealOnLockScreen = true)).stage.line2)
    }

    @Test
    fun `a free account gets the stage, and a lock where the Premium blocks would be`() {
        val content = build(summary(premium = false))
        assertEquals("14", content.stage.big)
        assertNull(content.water)
        assertNull(content.doses)
        assertTrue(content.locked)
        assertFalse(build(summary(premium = false), WidgetConfig(WidgetSize.SMALL)).locked, "the small stage widget is free")
    }

    @Test
    fun `russian and english speak their own words`() {
        assertEquals("До месячных 14 дн.", build(summary(language = Language.RU)).stage.line2)
        assertEquals("Period in 14 days", build(summary(language = Language.EN)).stage.line2)
    }
}
