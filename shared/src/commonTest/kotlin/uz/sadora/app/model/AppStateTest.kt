package uz.sadora.app.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import uz.sadora.app.data.applyCycle
import uz.sadora.app.i18n.StringsUz
import uz.sadora.contract.FoodRelation
import uz.sadora.contract.MealSlot
import uz.sadora.contract.ScheduleKind

/**
 * The store's derived cycle logic is what every screen draws from, so it is pinned
 * here rather than left to be discovered on a device: the phase a day falls in, how
 * the calendar counts from the anchor, and what leaves through the sync sink.
 */
class AppStateTest {

    private val today = LocalDate.parse("2026-09-04")

    private fun state(cycleLength: Int = 28, periodLength: Int = 5): AppState = AppState().apply {
        this.today = this@AppStateTest.today
        averageCycleLength = cycleLength
        averagePeriodLength = periodLength
    }

    // ---------------------------------------------------------------- phases

    @Test
    fun `phase follows the period length and the assumed fertile window`() {
        val s = state(periodLength = 5)
        assertEquals(CyclePhase.Period, s.phaseForCycleDay(1))
        assertEquals(CyclePhase.Period, s.phaseForCycleDay(5))
        assertEquals(CyclePhase.Follicular, s.phaseForCycleDay(6))
        assertEquals(CyclePhase.Follicular, s.phaseForCycleDay(11))
        assertEquals(CyclePhase.Fertile, s.phaseForCycleDay(12))
        assertEquals(CyclePhase.Fertile, s.phaseForCycleDay(16))
        assertEquals(CyclePhase.Luteal, s.phaseForCycleDay(17))
        assertEquals(CyclePhase.Luteal, s.phaseForCycleDay(28))
    }

    @Test
    fun `the server's phase wins over the local estimate while it is known`() {
        val s = state().apply { cycleDay = 3 }
        assertEquals(CyclePhase.Period, s.currentPhase())
        s.cyclePhase = CyclePhase.Fertile
        assertEquals(CyclePhase.Fertile, s.currentPhase())
    }

    // ---------------------------------------------------------------- dates

    @Test
    fun `the calendar counts from the anchor in both directions`() {
        val s = state(cycleLength = 28).apply { cycleStartDate = LocalDate.parse("2026-08-30") }
        assertEquals(1, s.cycleDayFor(LocalDate.parse("2026-08-30")))
        assertEquals(6, s.cycleDayFor(today))
        assertEquals(28, s.cycleDayFor(LocalDate.parse("2026-09-26")))
        // The day after a full cycle is day 1 of the next — the prediction.
        assertEquals(1, s.cycleDayFor(LocalDate.parse("2026-09-27")))
        // And a day before the anchor belongs to the previous cycle's tail.
        assertEquals(28, s.cycleDayFor(LocalDate.parse("2026-08-29")))
        assertEquals(CyclePhase.Period, s.phaseForDate(today.minus(5)))
    }

    @Test
    fun `with no anchor the calendar has nothing to colour`() {
        val s = state()
        assertNull(s.cycleDayFor(today))
        assertNull(s.phaseForDate(today))
    }

    @Test
    fun `recomputing from onboarding sets the day and keeps the anchor`() {
        val s = state(cycleLength = 28)
        s.markedPeriodDays.addAll(listOf(LocalDate.parse("2026-08-30"), LocalDate.parse("2026-08-31")))

        s.recomputeCycleDay(today)

        assertEquals(6, s.cycleDay)
        assertEquals(LocalDate.parse("2026-08-30"), s.cycleStartDate)
        // Anything the server would have said is unknown until it loads.
        assertNull(s.cyclePhase)
        assertNull(s.daysUntilNextPeriod)
    }

    @Test
    fun `handing the marked periods to the server keeps the latest start as the anchor`() {
        val s = state()
        s.markedPeriodDays.addAll(listOf(LocalDate.parse("2026-08-02"), LocalDate.parse("2026-08-30")))

        val taken = s.takeMarkedPeriods()

        assertEquals(2, taken.size)
        assertTrue(s.markedPeriodDays.isEmpty())
        assertEquals(LocalDate.parse("2026-08-30"), s.cycleStartDate)
    }

    @Test
    fun `next period is the server's count when known and the average otherwise`() {
        val s = state(cycleLength = 28).apply { cycleDay = 6 }
        assertEquals(23, s.daysToNextPeriod())
        assertEquals(LocalDate.parse("2026-09-27"), s.nextPeriodStart())

        s.daysUntilNextPeriod = 20
        assertEquals(20, s.daysToNextPeriod())
        assertEquals(LocalDate.parse("2026-09-24"), s.nextPeriodStart())
    }

    @Test
    fun `the fertile window comes from the server when it has one`() {
        val s = state(cycleLength = 28).apply {
            cycleStartDate = LocalDate.parse("2026-08-30")
            fertileFrom = LocalDate.parse("2026-09-09")
            fertileUntil = LocalDate.parse("2026-09-13")
        }
        assertEquals(11..15, s.fertileWindowDays())
        assertTrue(s.isFertile(LocalDate.parse("2026-09-10")))
        assertFalse(s.isFertile(today))
    }

    // ---------------------------------------------------------------- writes

    /**
     * A store with a day's medication in it.
     *
     * The store no longer seeds itself with a sample course — a phone that has just
     * signed in must not show a prescription nobody entered — so a test about doses
     * says which doses it means.
     */
    private fun withMedications() = AppState().apply {
        medications.addAll(
            listOf(
                Medication(
                    "d1", "🌿", "Folik kislota", "08:00",
                    ScheduleKind.DAILY, null, FoodRelation.AFTER, MedStatus.Taken,
                ),
                Medication(
                    "d2", "💊", "D vitamini", "20:00",
                    ScheduleKind.DAILY, null, FoodRelation.ANY, MedStatus.Pending,
                ),
            ),
        )
    }

    private class RecordingSync : AppStateSync {
        val events = mutableListOf<String>()
        override fun symptomToggled(label: String, nowSelected: Boolean) { events += "symptom:$label:$nowSelected" }
        override fun waterAdded(ml: Int) { events += "water:$ml" }
        override fun doseTaken(doseId: String) { events += "taken:$doseId" }
        override fun doseSkipped(doseId: String) { events += "skipped:$doseId" }
        override fun mealLogged(meal: Meal) { events += "meal:${meal.slot}" }
        override fun checkInChanged(mood: Mood, energy: Int, stress: Int) { events += "checkin:${mood.name}:$energy:$stress" }
        override fun practiceLogged(kind: PracticeKind, seconds: Int) { events += "practice:${kind.name}:$seconds" }
        override fun journalSaved(body: String) { events += "journal:$body" }
        override fun journalDeleted(id: String) { events += "journal-deleted:$id" }
    }

    @Test
    fun `a check-in sends all three dials and clamps them`() {
        val sync = RecordingSync()
        val s = state().apply { this.sync = sync }

        s.setCheckIn(mood = Mood.Great)
        s.setCheckIn(energy = 9)
        s.setCheckIn(stress = 0)

        assertEquals(Mood.Great, s.mood)
        assertEquals(5, s.energy)
        assertEquals(1, s.stress)
        assertEquals(
            listOf("checkin:Great:4:2", "checkin:Great:5:2", "checkin:Great:5:1"),
            sync.events,
        )
    }

    @Test
    fun `skipping a dose marks it and reports it separately from taking one`() {
        val sync = RecordingSync()
        val s = withMedications().apply { this.sync = sync }
        val pending = s.medications.first { it.status == MedStatus.Pending }

        s.markMedicationSkipped(pending.id)

        assertEquals(MedStatus.Skipped, s.medications.first { it.id == pending.id }.status)
        assertEquals(listOf("skipped:${pending.id}"), sync.events)
    }

    @Test
    fun `a practice is only logged when time was actually spent`() {
        val sync = RecordingSync()
        val s = state().apply { this.sync = sync }

        s.logPractice(PracticeKind.Breathing, 0)
        s.logPractice(PracticeKind.Meditation, 90)

        assertEquals(90, s.practiceSecondsToday)
        assertEquals(listOf("practice:Meditation:90"), sync.events)
    }

    @Test
    fun `water remaining never goes negative`() {
        val s = state().apply { waterGoalMl = 2000; waterMl = 2400 }
        assertEquals(0, s.waterRemainingMl)
    }

    @Test
    fun `doses count what was confirmed against what is due`() {
        val s = withMedications()
        assertEquals(2, s.dosesDue)
        assertEquals(1, s.dosesTaken)
    }

    // ---------------------------------------------------------------- helpers

    @Test
    fun `a meal logged now lands in the slot the hour belongs to`() {
        assertEquals(MealSlot.BREAKFAST, mealSlotForHour(8))
        assertEquals(MealSlot.LUNCH, mealSlotForHour(13))
        assertEquals(MealSlot.DINNER, mealSlotForHour(19))
        assertEquals(MealSlot.SNACK, mealSlotForHour(23))
    }

    @Test
    fun `dates read in Uzbek`() {
        val uz = StringsUz.dates
        assertEquals("4-sentabr", uz.dayMonth(today))
        assertEquals("4-sentabr, juma", uz.dayMonthWeekday(today))
        assertEquals("Sentabr 2026", uz.monthYear(2026, 9))
    }

    private fun LocalDate.minus(days: Int): LocalDate =
        kotlinx.datetime.LocalDate.fromEpochDays(toEpochDays() - days)
}

class CycleAnchorTest {
    @Test
    fun `the anchor follows the server's day count not a stale open period`() {
        val today = LocalDate.parse("2026-09-04")
        val s = AppState()
        s.applyCycle(
            uz.sadora.contract.CycleStatus(
                cycleDay = 14,
                phase = uz.sadora.contract.CyclePhase.FERTILE,
                currentPeriod = uz.sadora.contract.PeriodEntry(
                    id = "p0",
                    startedOn = LocalDate.parse("2026-08-11"),
                    createdAt = kotlin.time.Instant.parse("2026-08-11T00:00:00Z"),
                ),
                lastPeriodStart = LocalDate.parse("2026-08-22"),
                prediction = uz.sadora.contract.CyclePrediction(
                    confidence = uz.sadora.contract.PredictionConfidence.MEDIUM,
                    reason = uz.sadora.contract.PredictionReasons.IRREGULAR,
                    fertileFrom = LocalDate.parse("2026-09-03"),
                    fertileUntil = LocalDate.parse("2026-09-09"),
                    averageCycleLength = 31,
                ),
                today = today,
            ),
        )
        assertEquals(LocalDate.parse("2026-08-22"), s.cycleStartDate)
        assertEquals(14, s.cycleDayFor(today))
        assertEquals(13..19, s.fertileWindowDays())
        assertEquals(CyclePhase.Fertile, s.currentPhase())
    }
}

class AppStateJournalTest {

    private fun state() = AppState()


    private class RecordingSync : AppStateSync {
        val events = mutableListOf<String>()
        override fun symptomToggled(label: String, nowSelected: Boolean) {}
        override fun waterAdded(ml: Int) {}
        override fun doseTaken(doseId: String) {}
        override fun doseSkipped(doseId: String) {}
        override fun mealLogged(meal: Meal) {}
        override fun checkInChanged(mood: Mood, energy: Int, stress: Int) {}
        override fun practiceLogged(kind: PracticeKind, seconds: Int) {}
        override fun journalSaved(body: String) { events += "journal:$body" }
        override fun journalDeleted(id: String) { events += "journal-deleted:$id" }
    }

    @Test
    fun `a journal note appears at once and is sent on`() {
        val sync = RecordingSync()
        val s = state().apply { this.sync = sync }

        s.addJournalNote("  Bugun yaxshi kun  ")

        assertEquals(1, s.journal.size)
        assertEquals("Bugun yaxshi kun", s.journal.first().body)
        assertEquals(listOf("journal:Bugun yaxshi kun"), sync.events)
    }

    @Test
    fun `an empty journal note is not written`() {
        val sync = RecordingSync()
        val s = state().apply { this.sync = sync }

        s.addJournalNote("   ")

        assertEquals(0, s.journal.size)
        assertEquals(emptyList(), sync.events)
    }

    @Test
    fun `deleting a note the server never saw sends nothing`() {
        val sync = RecordingSync()
        val s = state().apply { this.sync = sync }
        s.addJournalNote("hali yuborilmagan")
        sync.events.clear()

        s.deleteJournalNote(s.journal.first())

        assertEquals(0, s.journal.size)
        // It has no server id yet, so there is nothing there to delete.
        assertEquals(emptyList(), sync.events)
    }

    @Test
    fun `deleting a saved note sends its id`() {
        val sync = RecordingSync()
        val s = state().apply { this.sync = sync }
        val saved = JournalNote(id = "abc-123", date = s.today, time = "21:40", body = "kecha")
        s.journal.add(saved)

        s.deleteJournalNote(saved)

        assertEquals(0, s.journal.size)
        assertEquals(listOf("journal-deleted:abc-123"), sync.events)
    }
}
