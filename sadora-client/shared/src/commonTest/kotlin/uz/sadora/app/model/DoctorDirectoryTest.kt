package uz.sadora.app.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uz.sadora.contract.DoctorListItem
import uz.sadora.contract.DoctorSpecialty

/**
 * The directory's filter and sort: the chips keep only what they say, "recommended" is
 * the server's order untouched, doctors under the rating threshold never outrank rated
 * ones, and a doctor with no reply time yet is last when speed is the question.
 */
class DoctorDirectoryTest {

    private fun doctor(
        id: String,
        specialty: DoctorSpecialty = DoctorSpecialty.GYNECOLOGIST,
        priceMinor: Long = 0,
        rating: Double? = null,
        ratingCount: Int = 0,
        onlineNow: Boolean = false,
        avgFirstReplyMinutes: Int? = null,
    ) = DoctorListItem(
        id = id,
        fullName = "Dr. $id",
        specialty = specialty,
        workplace = "Klinika",
        experienceYears = 5,
        priceMinor = priceMinor,
        rating = rating,
        ratingCount = ratingCount,
        onlineNow = onlineNow,
        avgFirstReplyMinutes = avgFirstReplyMinutes,
    )

    private fun List<DoctorListItem>.ids() = map { it.id }

    // In the server's recommended order.
    private val a = doctor("a", priceMinor = 5_000_000, rating = 4.2, ratingCount = 10, onlineNow = true, avgFirstReplyMinutes = 40)
    private val b = doctor("b", specialty = DoctorSpecialty.PSYCHOLOGIST, rating = 5.0, ratingCount = 2, avgFirstReplyMinutes = 10)
    private val c = doctor("c", priceMinor = 3_000_000, rating = 4.9, ratingCount = 3, onlineNow = true)
    private val d = doctor("d", specialty = DoctorSpecialty.PSYCHOLOGIST, rating = 4.9, ratingCount = 30, avgFirstReplyMinutes = 10)
    private val e = doctor("e", specialty = DoctorSpecialty.ENDOCRINOLOGIST, ratingCount = 0)
    private val all = listOf(a, b, c, d, e)

    @Test
    fun `recommended with no filter is the server's list as it came`() {
        assertEquals(all, arrangeDoctors(all, DoctorFilter(), DoctorSort.Recommended))
        assertFalse(DoctorFilter().isActive)
    }

    @Test
    fun `each chip keeps only what it names and they combine`() {
        assertEquals(listOf("b", "d"), arrangeDoctors(all, DoctorFilter(specialty = DoctorSpecialty.PSYCHOLOGIST), DoctorSort.Recommended).ids())
        assertEquals(listOf("a", "c"), arrangeDoctors(all, DoctorFilter(onlineOnly = true), DoctorSort.Recommended).ids())
        assertEquals(listOf("b", "d", "e"), arrangeDoctors(all, DoctorFilter(freeOnly = true), DoctorSort.Recommended).ids())
        assertEquals(
            emptyList(),
            arrangeDoctors(all, DoctorFilter(onlineOnly = true, freeOnly = true), DoctorSort.Recommended).ids(),
            "online and free at once: nobody here is both",
        )
        assertTrue(DoctorFilter(freeOnly = true).isActive)
    }

    @Test
    fun `by rating the rated come first and the new ones last by count`() {
        // c and d tie on 4.9 and d has more ratings; b's 5.0 from two patients does not
        // count, so she follows the rated doctors, ahead of e who has none.
        assertEquals(listOf("d", "c", "a", "b", "e"), arrangeDoctors(all, DoctorFilter(), DoctorSort.Rating).ids())
    }

    @Test
    fun `a new doctor's high average does not lift her above a rated one`() {
        val fresh = doctor("fresh", rating = 5.0, ratingCount = 2)
        val steady = doctor("steady", rating = 3.1, ratingCount = 3)
        assertEquals(listOf("steady", "fresh"), arrangeDoctors(listOf(fresh, steady), DoctorFilter(), DoctorSort.Rating).ids())
    }

    @Test
    fun `by price the cheapest come first and ties keep the recommended order`() {
        assertEquals(listOf("b", "d", "e", "c", "a"), arrangeDoctors(all, DoctorFilter(), DoctorSort.Price).ids())
    }

    @Test
    fun `by reply time the fastest come first and those with none come last`() {
        // b and d tie at ten minutes and keep their order; c and e have no reply time yet.
        assertEquals(listOf("b", "d", "a", "c", "e"), arrangeDoctors(all, DoctorFilter(), DoctorSort.FastReply).ids())
    }

    @Test
    fun `a sort applies after the filter`() {
        assertEquals(
            listOf("d", "b"),
            arrangeDoctors(all, DoctorFilter(specialty = DoctorSpecialty.PSYCHOLOGIST), DoctorSort.Rating).ids(),
        )
    }

    @Test
    fun `the specialty chips are the ones the list has in a fixed order`() {
        assertEquals(
            listOf(DoctorSpecialty.GYNECOLOGIST, DoctorSpecialty.ENDOCRINOLOGIST, DoctorSpecialty.PSYCHOLOGIST),
            specialtiesIn(listOf(e, d, a, b)),
        )
        assertEquals(emptyList(), specialtiesIn(emptyList()))
    }

    @Test
    fun `three ratings are enough and two are not`() {
        assertFalse(hasEnoughRatings(0))
        assertFalse(hasEnoughRatings(2))
        assertTrue(hasEnoughRatings(3))
    }
}
