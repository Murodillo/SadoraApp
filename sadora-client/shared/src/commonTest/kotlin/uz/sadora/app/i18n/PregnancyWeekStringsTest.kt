package uz.sadora.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import uz.sadora.app.model.PregnancyWeeks

/**
 * The week card reads its lines by index, so a list one short shifts every later week
 * onto the wrong words — the kind of mistake that compiles and reads plausibly.
 */
class PregnancyWeekStringsTest {

    private val languages = listOf(StringsUz, StringsRu, StringsEn).map { it.pregnancyWeeks }
    private val weekCount = PregnancyWeeks.LAST - PregnancyWeeks.FIRST + 1

    @Test
    fun everyLanguageHasEveryWeek() {
        languages.forEach { t ->
            listOf(t.fruits, t.baby, t.mother).forEach { list ->
                assertEquals(weekCount, list.size)
                assertTrue(list.none { it.isBlank() })
            }
        }
    }

    @Test
    fun theTableCoversEveryWeekInOrder() {
        assertEquals((PregnancyWeeks.FIRST..PregnancyWeeks.LAST).toList(), PregnancyWeeks.all.map { it.week })
    }

    @Test
    fun theBabyOnlyGrows() {
        PregnancyWeeks.all.zipWithNext().forEach { (a, b) ->
            assertTrue(b.lengthMm > a.lengthMm, "length at week ${b.week}")
            if (a.weightG != null) assertTrue(b.weightG!! > a.weightG, "weight at week ${b.week}")
        }
    }

    @Test
    fun weeksOutsideTheTableClamp() {
        assertEquals(PregnancyWeeks.FIRST, PregnancyWeeks.of(1).week)
        assertEquals(PregnancyWeeks.LAST, PregnancyWeeks.of(42).week)
        assertEquals(StringsUz.pregnancyWeeks.baby.last(), StringsUz.pregnancyWeeks.baby.forWeek(41))
    }

    @Test
    fun unitsSwitchAtTheRightPoint() {
        assertEquals("5 mm", PregnancyWeeks.lengthValue(5, "mm", "sm"))
        assertEquals("5,4 sm", PregnancyWeeks.lengthValue(54, "mm", "sm"))
        assertEquals("600 g", PregnancyWeeks.weightValue(600, "g", "kg"))
        assertEquals("1,3 kg", PregnancyWeeks.weightValue(1320, "g", "kg"))
    }

    @Test
    fun languagesAreTranslated() {
        assertNotEquals(StringsUz.pregnancyWeeks.baby, StringsRu.pregnancyWeeks.baby)
        assertNotEquals(StringsUz.pregnancyWeeks.baby, StringsEn.pregnancyWeeks.baby)
    }
}

class RussianPluralTest {
    @Test
    fun formsFollowTheLastDigits() {
        val forms = listOf(1, 2, 5, 11, 14, 21, 22, 25, 101, 111).map { ru(it, "день", "дня", "дней") }
        assertEquals(listOf("день", "дня", "дней", "дней", "дней", "день", "дня", "дней", "день", "дней"), forms)
    }
}
