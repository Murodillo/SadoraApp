package uz.sadora.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

/** A post's view count is drawn whole up to 999 and shortened after, each language its own way. */
class CompactCountTest {

    private fun uz(n: Int) = StringsUz.community.viewsShort(n)
    private fun ru(n: Int) = StringsRu.community.viewsShort(n)
    private fun en(n: Int) = StringsEn.community.viewsShort(n)

    @Test
    fun `under a thousand is written out`() {
        listOf(1, 42, 999).forEach { n ->
            assertEquals(n.toString(), uz(n))
            assertEquals(n.toString(), ru(n))
            assertEquals(n.toString(), en(n))
        }
    }

    @Test
    fun `thousands keep one decimal until ten thousand and never round up`() {
        assertEquals("1 ming", uz(1000))
        assertEquals("1,2 ming", uz(1234))
        assertEquals("1,9 ming", uz(1999))
        assertEquals("12 ming", uz(12_900))
        assertEquals("999 ming", uz(999_999))
        assertEquals("1,2 тыс.", ru(1234))
        assertEquals("1.2K", en(1234))
        assertEquals("12K", en(12_900))
    }

    @Test
    fun `millions follow the same rule`() {
        assertEquals("1 mln", uz(1_000_000))
        assertEquals("2,5 mln", uz(2_540_000))
        assertEquals("2,5 млн", ru(2_540_000))
        assertEquals("2.5M", en(2_540_000))
        assertEquals("31M", en(31_400_000))
    }

    @Test
    fun `a screen reader hears the whole number and what it counts`() {
        assertEquals("1234 marta ko'rildi", StringsUz.community.viewsSpoken(1234))
        assertEquals("Просмотров: 1234", StringsRu.community.viewsSpoken(1234))
        assertEquals("1 view", StringsEn.community.viewsSpoken(1))
        assertEquals("1234 views", StringsEn.community.viewsSpoken(1234))
    }
}
