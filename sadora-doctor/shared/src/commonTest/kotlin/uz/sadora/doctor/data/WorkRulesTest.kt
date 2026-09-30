package uz.sadora.doctor.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import uz.sadora.contract.DoctorHours

/** Money in so'm, the price she may set, the week of hours, a quick reply, a push link. */
class WorkRulesTest {

    @Test
    fun `tiyin read as so'm grouped by threes`() {
        assertEquals("0", groupedSom(0))
        assertEquals("999", groupedSom(99_900))
        assertEquals("1 000", groupedSom(100_000))
        assertEquals("50 000", groupedSom(5_000_000))
        assertEquals("2 000 000", groupedSom(200_000_000))
        assertEquals("-1 500", groupedSom(-150_000))
    }

    @Test
    fun `zero or nothing is free and a paid price stays within the server's range`() {
        assertEquals(PriceCheck.Free, checkPrice(""))
        assertEquals(PriceCheck.Free, checkPrice("0"))
        assertEquals(PriceCheck.TooLow, checkPrice("999"))
        assertEquals(PriceCheck.Paid(100_000), checkPrice("1000"))
        assertEquals(PriceCheck.Paid(200_000_000), checkPrice("2000000"))
        assertEquals(PriceCheck.TooHigh, checkPrice("2000001"))
        assertEquals(0L, checkPrice("").minorOrNull)
        assertNull(checkPrice("5").minorOrNull)
        assertFalse(PriceCheck.TooHigh.ok)
    }

    @Test
    fun `the price field keeps digits only and drops leading zeros`() {
        assertEquals("50000", acceptPriceDigits("50 000 so'm"))
        assertEquals("120", acceptPriceDigits("00120"))
        assertEquals("", acceptPriceDigits("0"))
        // Long enough to say "too high", no longer.
        assertEquals(8, acceptPriceDigits("123456789012").length)
    }

    @Test
    fun `her share is the price less the commission and rounded as the server does`() {
        assertEquals(4_000_000, netOf(5_000_000, 20))
        assertEquals(5_000_000, netOf(5_000_000, 0))
        assertEquals(100_001 - 100_001 * 15 / 100, netOf(100_001, 15))
    }

    @Test
    fun `the week has seven days and a day not set is a day off`() {
        val week = weekOf(listOf(DoctorHours(1, 9 * 60, 18 * 60), DoctorHours(5, 10 * 60, 14 * 60)))
        assertEquals((1..7).toList(), week.map { it.weekday })
        assertEquals(listOf(true, false, false, false, true, false, false), week.map { it.on })
        assertEquals(DayHours(5, on = true, start = 600, end = 840), week[4])
        // Only the days she works go back, and the hours of a day switched off are forgotten.
        assertEquals(
            listOf(DoctorHours(1, 540, 1080), DoctorHours(5, 600, 840)),
            week.toHours(),
        )
        assertEquals(emptyList(), weekOf(emptyList()).toHours())
    }

    @Test
    fun `stepping the hours keeps every day a real span inside the day`() {
        val day = DayHours(1, on = true, start = 9 * 60, end = 18 * 60)
        assertEquals(9 * 60 + 30, day.stepStart(1).start)
        assertEquals(0, day.stepStart(-100).start)
        assertEquals(24 * 60, day.stepEnd(100).end)
        // The start stops half an hour short of the end, and the end half an hour past the start.
        assertEquals(18 * 60 - 30, day.stepStart(100).start)
        assertEquals(9 * 60 + 30, day.stepEnd(-100).end)
        assertTrue(day.stepStart(100).valid)
        assertFalse(DayHours(1, on = true, start = 600, end = 600).valid)
        assertFalse(listOf(DayHours(2, on = true, start = 700, end = 600)).allValid())
        // A day off is not held to its hours.
        assertTrue(listOf(DayHours(2, on = false, start = 700, end = 600)).allValid())
    }

    @Test
    fun `times read as a clock and midnight at the end of a day is 24 00`() {
        assertEquals("00:00", clockOf(0))
        assertEquals("09:30", clockOf(570))
        assertEquals("24:00", clockOf(1440))
        assertEquals("09:00–18:00", spanOf(540, 1080))
    }

    @Test
    fun `a quick reply fills an empty field and follows her own words on a new line`() {
        assertEquals("Tahlil natijasini yuboring", insertReply("", "Tahlil natijasini yuboring", 100))
        assertEquals("Salom!\nTahlil", insertReply("Salom!  ", "Tahlil", 100))
        assertEquals("Salom!\nTah", insertReply("Salom!", "Tahlil", 10))
    }

    @Test
    fun `only a conversation link opens a conversation`() {
        assertEquals("c-1", conversationIdFromLink("sadora://conversation/c-1"))
        assertEquals("c-1", conversationIdFromLink("sadora://conversation/c-1/"))
        assertEquals("c-1", conversationIdFromLink("sadora://conversation/c-1?from=push"))
        assertNull(conversationIdFromLink("sadora://conversation/"))
        assertNull(conversationIdFromLink("sadora://conversation/c-1/messages"))
        assertNull(conversationIdFromLink("sadora://invite/K7M2QP"))
        assertNull(conversationIdFromLink(null))
    }
}
