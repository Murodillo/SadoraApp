package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import uz.sadora.contract.Epds
import uz.sadora.contract.GoalsBasis
import uz.sadora.contract.LifeStage
import uz.sadora.contract.NutritionGoals
import uz.sadora.server.health.StageNutrition

/**
 * The defaults follow the stage by published figures; everything else keeps the plain
 * ones. And the questionnaire scores the way the paper does, reversed items included.
 */
class StageNutritionTest {

    private val today = LocalDate(2026, 10, 4)

    @Test
    fun `a pregnancy's targets rise by trimester and its water from the start`() {
        val first = StageNutrition.defaults(LifeStage.PREGNANCY, LocalDate(2027, 5, 1), null, today)
        assertEquals(1850, first.calorieGoal)
        assertEquals(2300, first.waterGoalMl)
        assertEquals(GoalsBasis.PREGNANCY_FIRST_TRIMESTER, first.basis)

        // Due 15 January: week 25.
        val second = StageNutrition.defaults(LifeStage.PREGNANCY, LocalDate(2027, 1, 15), null, today)
        assertEquals(2190, second.calorieGoal)
        assertEquals(GoalsBasis.PREGNANCY_SECOND_TRIMESTER, second.basis)

        val third = StageNutrition.defaults(LifeStage.PREGNANCY, LocalDate(2026, 11, 20), null, today)
        assertEquals(2300, third.calorieGoal)
    }

    @Test
    fun `breastfeeding defaults last the first year and then give way`() {
        val early = StageNutrition.defaults(LifeStage.POSTPARTUM, null, LocalDate(2026, 8, 9), today)
        assertEquals(2350, early.calorieGoal)
        assertEquals(2700, early.waterGoalMl)
        assertEquals(GoalsBasis.BREASTFEEDING, early.basis)

        val later = StageNutrition.defaults(LifeStage.POSTPARTUM, null, LocalDate(2025, 6, 1), today)
        assertEquals(NutritionGoals(), later)
    }

    @Test
    fun `the other stages keep the plain defaults`() {
        listOf(LifeStage.CYCLE, LifeStage.TRYING_TO_CONCEIVE, LifeStage.PERIMENOPAUSE, LifeStage.MENOPAUSE).forEach {
            assertNull(StageNutrition.defaults(it, null, null, today).basis, it.name)
        }
    }

    @Test
    fun `the EPDS reverses all but items 1, 2 and 4`() {
        // The first option everywhere: 0 for the forward items, 3 for the seven reversed.
        assertEquals(21, Epds.score(List(10) { 0 }))
        // The last option everywhere: 3 for the forward items, 0 for the reversed.
        assertEquals(9, Epds.score(List(10) { 3 }))
        assertTrue(Epds.selfHarm(List(10) { 0 }), "\"yes, quite often\" to the last question")
        assertTrue(!Epds.selfHarm(List(9) { 0 } + 3), "\"never\"")
        assertTrue(!Epds.isComplete(List(9) { 0 }))
        assertTrue(!Epds.isComplete(List(10) { 4 }))
    }
}
