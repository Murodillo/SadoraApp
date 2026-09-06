package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import uz.sadora.contract.FoodRelation
import uz.sadora.contract.ScheduleKind
import uz.sadora.contract.Weekday
import uz.sadora.server.health.DoseSchedule
import uz.sadora.server.health.MedicationRecord

class DoseScheduleTest {

    private fun date(text: String) = LocalDate.parse(text)
    private fun time(text: String) = LocalTime.parse(text)

    private fun medication(
        kind: ScheduleKind = ScheduleKind.DAILY,
        times: List<String> = listOf("08:00"),
        weekdays: List<Weekday> = emptyList(),
        intervalDays: Int? = null,
        startedOn: String = "2026-09-01",
        endedOn: String? = null,
        stockUnits: Int? = null,
        active: Boolean = true,
    ) = MedicationRecord(
        id = Uuid.random(),
        name = "Temir 30 mg",
        emoji = "🩸",
        dosage = "30",
        unit = "mg",
        foodRelation = FoodRelation.AFTER,
        note = null,
        scheduleKind = kind,
        times = times.map { time(it) },
        weekdays = weekdays,
        intervalDays = intervalDays,
        remindersEnabled = true,
        startedOn = date(startedOn),
        endedOn = endedOn?.let { date(it) },
        stockUnits = stockUnits,
        active = active,
        createdAt = Instant.DISTANT_PAST,
    )

    @Test
    fun `a daily course is due every day, in time order`() {
        val med = medication(times = listOf("20:00", "08:00"))
        assertEquals(listOf(time("08:00"), time("20:00")), DoseSchedule.dosesOn(med, date("2026-09-10")))
    }

    /** 2026-09-07 is a Monday; 09-08 a Tuesday. */
    @Test
    fun `a weekday course is due only on its days`() {
        val med = medication(
            kind = ScheduleKind.WEEKDAYS,
            weekdays = listOf(Weekday.MONDAY, Weekday.WEDNESDAY, Weekday.FRIDAY),
        )
        assertTrue(DoseSchedule.dosesOn(med, date("2026-09-07")).isNotEmpty(), "Monday")
        assertTrue(DoseSchedule.dosesOn(med, date("2026-09-08")).isEmpty(), "Tuesday")
        assertTrue(DoseSchedule.dosesOn(med, date("2026-09-09")).isNotEmpty(), "Wednesday")
    }

    @Test
    fun `an interval course counts from the day it started`() {
        val med = medication(kind = ScheduleKind.INTERVAL, intervalDays = 3, startedOn = "2026-09-01")
        listOf("2026-09-01", "2026-09-04", "2026-09-07").forEach {
            assertTrue(DoseSchedule.dosesOn(med, date(it)).isNotEmpty(), it)
        }
        listOf("2026-09-02", "2026-09-03", "2026-09-05").forEach {
            assertTrue(DoseSchedule.dosesOn(med, date(it)).isEmpty(), it)
        }
    }

    @Test
    fun `nothing is due before the start or after the end`() {
        val med = medication(startedOn = "2026-09-05", endedOn = "2026-09-10")
        assertTrue(DoseSchedule.dosesOn(med, date("2026-09-04")).isEmpty(), "before start")
        assertTrue(DoseSchedule.dosesOn(med, date("2026-09-05")).isNotEmpty(), "on start")
        assertTrue(DoseSchedule.dosesOn(med, date("2026-09-10")).isNotEmpty(), "on end")
        assertTrue(DoseSchedule.dosesOn(med, date("2026-09-11")).isEmpty(), "after end")
    }

    /**
     * The regression this exists for: archiving used to blank the schedule outright, so
     * a finished course reconstructed its own history as empty and "did I take it in
     * March" became unanswerable — which is the whole reason archiving is not a delete.
     */
    @Test
    fun `an archived course keeps its past but stops after the end date`() {
        val med = medication(startedOn = "2026-09-01", endedOn = "2026-09-10", active = false)
        assertTrue(DoseSchedule.dosesOn(med, date("2026-09-05")).isNotEmpty(), "history must survive")
        assertTrue(DoseSchedule.dosesOn(med, date("2026-09-10")).isNotEmpty(), "the final day counts")
        assertTrue(DoseSchedule.dosesOn(med, date("2026-09-11")).isEmpty(), "and nothing after it")
    }

    @Test
    fun `an inactive course with no end date does not run forever`() {
        val med = medication(active = false, endedOn = null)
        assertTrue(DoseSchedule.dosesOn(med, date("2026-09-10")).isEmpty())
    }

    @Test
    fun `a course with no times has nothing to be due`() {
        assertTrue(DoseSchedule.dosesOn(medication(times = emptyList()), date("2026-09-10")).isEmpty())
    }

    /** Twenty doses at two a day is ten days; a Mon/Wed/Fri course stretches much further. */
    @Test
    fun `supply is counted against how fast the schedule consumes it`() {
        val daily = medication(times = listOf("08:00", "20:00"), stockUnits = 20)
        assertEquals(10, DoseSchedule.stockDaysLeft(daily))

        val thriceWeekly = medication(
            kind = ScheduleKind.WEEKDAYS,
            weekdays = listOf(Weekday.MONDAY, Weekday.WEDNESDAY, Weekday.FRIDAY),
            stockUnits = 12,
        )
        assertEquals(28, DoseSchedule.stockDaysLeft(thriceWeekly))
    }

    @Test
    fun `an untracked pack reports no days left rather than zero`() {
        assertNull(DoseSchedule.stockDaysLeft(medication(stockUnits = null)))
    }
}
