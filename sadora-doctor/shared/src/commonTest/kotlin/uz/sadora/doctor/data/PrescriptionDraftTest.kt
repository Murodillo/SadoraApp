package uz.sadora.doctor.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalTime
import uz.sadora.contract.DoctorAuthor
import uz.sadora.contract.DoctorSpecialty
import uz.sadora.contract.FoodRelation
import uz.sadora.contract.Limits
import uz.sadora.contract.Prescription
import uz.sadora.contract.PrescriptionForm
import uz.sadora.contract.ScheduleKind
import uz.sadora.doctor.i18n.PrescriptionStringsEn
import uz.sadora.doctor.i18n.PrescriptionStringsRu
import uz.sadora.doctor.i18n.PrescriptionStringsUz

class PrescriptionDraftTest {

    private val filled = ItemDraft(name = "Amoksitsillin", dose = "500", unit = "mg", food = FoodRelation.AFTER, days = "5")

    @Test
    fun `a medicine is not ready until its name — dose — food relation and length are there`() {
        assertEquals(ItemProblem.entries.toSet(), ItemDraft().problems())
        assertTrue(filled.problems().isEmpty())
        assertTrue(filled.copy(days = "", ongoing = true).problems().isEmpty(), "an ongoing course needs no length")
        assertEquals(setOf(ItemProblem.Days), filled.copy(days = "0").problems())
        assertFalse(PrescriptionDraft().ready)
        assertTrue(PrescriptionDraft(listOf(filled)).ready)
    }

    @Test
    fun `the presets fill the day and a time moves half an hour without landing on another`() {
        assertEquals(listOf(9 * 60), ItemDraft().withTimesPerDay(1).minutes)
        assertEquals(listOf(8 * 60, 14 * 60, 20 * 60), ItemDraft().withTimesPerDay(3).minutes)
        val two = ItemDraft(minutes = listOf(9 * 60, 9 * 60 + 30))
        assertEquals(two, two.stepTime(0, 1), "a time cannot move onto the one after it")
        assertEquals(listOf(8 * 60 + 30, 9 * 60 + 30), two.stepTime(0, -1).minutes)
        assertEquals(0, ItemDraft(minutes = listOf(0)).stepTime(0, -1).minutes.single(), "the day starts at midnight")
    }

    @Test
    fun `the request carries the form's unit when she typed none — sorted times and the interval`() {
        val request = PrescriptionDraft(
            listOf(
                ItemDraft(name = " Magniy ", dose = "1", food = FoodRelation.WITH, ongoing = true, minutes = listOf(21 * 60, 9 * 60)),
                filled.copy(everyDays = 3, startDay = 6),
            ),
            note = "  ",
        ).toRequest { PrescriptionStringsUz.defaultUnit(it) }
        val first = request.items[0]
        assertEquals("Magniy", first.name)
        assertEquals("tabletka", first.unit)
        assertNull(first.days)
        assertEquals(listOf(LocalTime(9, 0), LocalTime(21, 0)), first.schedule.times)
        assertEquals(ScheduleKind.DAILY, first.schedule.kind)
        assertEquals(ScheduleKind.INTERVAL, request.items[1].schedule.kind)
        assertEquals(3, request.items[1].schedule.intervalDays)
        assertEquals(6, request.items[1].startDay)
        assertNull(request.note)
    }

    @Test
    fun `there are at most ten medicines — at least one — and a sent one copies back into the form`() {
        var draft = PrescriptionDraft()
        repeat(20) { draft = draft.addItem() }
        assertEquals(Limits.PRESCRIPTION_ITEMS_MAX, draft.items.size)
        assertEquals(1, PrescriptionDraft().removeItem(0).items.size)

        val sent = PrescriptionDraft(listOf(filled.copy(form = PrescriptionForm.DROPS))).toRequest { "" }
        val copied = PrescriptionDraft.from(
            Prescription(
                id = "rx1",
                conversationId = "c1",
                messageId = "m1",
                doctor = DoctorAuthor("d1", "Dr", DoctorSpecialty.GYNECOLOGIST),
                items = sent.items,
                createdAt = Instant.parse("2026-10-09T09:00:00Z"),
            ),
        )
        assertEquals(PrescriptionForm.DROPS, copied.items.single().form)
        assertEquals("5", copied.items.single().days)
        assertEquals(FoodRelation.AFTER, copied.items.single().food)
        assertTrue(copied.ready)
    }

    @Test
    fun `the card's summary line is worded in each language`() {
        val item = PrescriptionDraft(listOf(filled)).toRequest { "" }.items.single()
        assertEquals("500 mg · kuniga 2 marta: 09:00, 21:00 · ovqatdan keyin · 5 kun", PrescriptionStringsUz.summary(item))
        assertEquals("500 mg · 2 раза в день: 09:00, 21:00 · после еды · 5 дней", PrescriptionStringsRu.summary(item))
        assertEquals("500 mg · 2 times a day: 09:00, 21:00 · after food · for 5 days", PrescriptionStringsEn.summary(item))
    }

    @Test
    fun `a course that starts later says from which day — and food any reads the same as the patient's card`() {
        val later = filled.copy(startDay = 3, food = FoodRelation.ANY, minutes = listOf(9 * 60))
        val item = PrescriptionDraft(listOf(later)).toRequest { "" }.items.single()
        assertEquals("500 mg · kuniga 1 marta: 09:00 · ovqatdan qat'i nazar · 3-kundan boshlab, 5 kun", PrescriptionStringsUz.summary(item))
        assertEquals("500 mg · 1 раз в день: 09:00 · независимо от еды · с 3-го дня, 5 дней", PrescriptionStringsRu.summary(item))
        assertEquals("500 mg · once a day: 09:00 · with or without food · from day 3, for 5 days", PrescriptionStringsEn.summary(item))
        assertEquals("Ovqatdan qat'i nazar", PrescriptionStringsUz.food(FoodRelation.ANY))
        assertEquals("Независимо от еды", PrescriptionStringsRu.food(FoodRelation.ANY))
        assertEquals("With or without food", PrescriptionStringsEn.food(FoodRelation.ANY))
        assertEquals("1 день", "1 ${uz.sadora.doctor.i18n.ru(1, "день", "дня", "дней")}")
        assertEquals("Every day", PrescriptionStringsEn.everyDays(1))
        assertEquals("Раз в 2 дня", PrescriptionStringsRu.everyDays(2))
    }
}
