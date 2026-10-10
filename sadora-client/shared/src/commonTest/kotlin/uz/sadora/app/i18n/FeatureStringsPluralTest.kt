package uz.sadora.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uz.sadora.contract.Badges
import uz.sadora.contract.FoodRelation
import uz.sadora.contract.PrescriptionItem
import uz.sadora.contract.ScheduleKind

/**
 * The feature tables beside the main strings — Yaqinim, badges, frames, prescriptions,
 * the stage tools — count things too. A number went into a fixed noun there as well:
 * "1 days", "Прочитайте статей: 1", "5 дн.", "50 гулей".
 */
class FeatureStringsPluralTest {

    private val counts = listOf(1, 2, 3, 5, 11, 21, 22, 25)

    private fun featureLines(t: Strings, n: Int): List<String> = buildList {
        with(t.partner) {
            add(periodIn(n)); add(daysToGo(n)); add(babyAge(n)); add(feedsToday(n)); add(hotFlushesToday(n))
            add(unreadCount(n)); add(webViews(n)); add(webDays(n))
        }
        with(t.tools) {
            add(fertileInDays(n)); add(periodLate(n)); add(shortCycles(n)); add(longCycles(n))
            add(irregularCycles(n)); add(longPeriods(n)); add(kicksResult(n, "x"))
        }
        with(t.frames) { add(coins(n)); add(balance(n)); add(short(n)); add(buyCoinsBody(n)); add(buyCoins(n)) }
        Badges.catalogue.forEach { (key, _) -> add(t.badges.goal(key, n)) }
        add(t.healthGate.partial(n, n))
    }

    @Test
    fun `no English line counts one of anything in the plural`() {
        counts.forEach { n ->
            featureLines(StringsEn, n).forEach { line ->
                if (n == 1) {
                    assertFalse(
                        Regex("""\b1 (days|weeks|times|feeds|messages|movements|hot flushes|periods|doses|meals|articles|friends|posts|minutes|litres|questions|comments|hearts|entries|sessions)\b""")
                            .containsMatchIn(line),
                        "\"$line\" counts one in the plural",
                    )
                }
            }
        }
    }

    @Test
    fun `no Russian line cuts a count short or declines the currency`() {
        counts.forEach { n ->
            featureLines(StringsRu, n).forEach { line ->
                assertFalse(line.contains("дн.") || line.contains("нед."), "\"$line\" abbreviates the count")
                assertFalse(line.contains("гул", ignoreCase = true), "\"$line\" spells Gul in Cyrillic")
            }
        }
        assertEquals("Задержка 1 день", StringsRu.tools.periodLate(1))
        assertEquals("Задержка 3 дня", StringsRu.tools.periodLate(3))
        assertEquals("Задержка 11 дней", StringsRu.tools.periodLate(11))
        assertEquals("Прочитайте 21 статью", StringsRu.badges.goal(Badges.READER, 21))
    }

    @Test
    fun `a prescription's later start reads as a start in every language`() {
        val item = PrescriptionItem(
            name = "X",
            dose = "1",
            unit = null,
            schedule = uz.sadora.contract.MedicationSchedule(kind = ScheduleKind.DAILY, times = emptyList()),
            foodRelation = FoodRelation.ANY,
            startDay = 3,
            days = 1,
        )
        assertTrue(StringsUz.prescriptions.summary(item).contains("3-kundan boshlab, 1 kun"))
        assertTrue(StringsRu.prescriptions.summary(item).contains("с 3-го дня, 1 день"))
        assertTrue(StringsEn.prescriptions.summary(item).contains("from day 3, 1 day"))
        assertEquals("Ovqatdan qat'i nazar", StringsUz.prescriptions.food(FoodRelation.ANY))
        assertEquals("Независимо от еды", StringsRu.prescriptions.food(FoodRelation.ANY))
        assertEquals("With or without food", StringsEn.prescriptions.food(FoodRelation.ANY))
    }
}
