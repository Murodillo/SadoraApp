package uz.sadora.server.health

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import uz.sadora.contract.GoalsBasis
import uz.sadora.contract.LifeStage
import uz.sadora.contract.NutritionGoals

/**
 * The default targets for her stage, used until she sets her own.
 *
 * Everyone used to get 1850 kcal and two litres, a pregnancy in its third trimester and
 * a mother feeding a newborn included — both need more, and by figures that are
 * published rather than worked out about her: the extra energy per trimester (IOM, +340
 * and +452 kcal from the second and third) and while breastfeeding (+500 kcal), and the
 * water (EFSA, +300 ml in pregnancy, +700 ml while breastfeeding). Postpartum assumes
 * breastfeeding for the first year; the screen says so, and her own figures replace
 * these the moment she changes any.
 */
object StageNutrition {

    private val plain = NutritionGoals()

    fun defaults(stage: LifeStage, dueDate: LocalDate?, birthDate: LocalDate?, today: LocalDate): NutritionGoals =
        when (stage) {
            LifeStage.PREGNANCY -> pregnancy(dueDate?.let { pregnancyWeek(it, today) })
            LifeStage.POSTPARTUM ->
                if (birthDate != null && birthDate.daysUntil(today) > BREASTFEEDING_DAYS) plain else breastfeeding
            else -> plain
        }

    private fun pregnancy(week: Int?): NutritionGoals = when {
        week == null || week <= 13 -> plain.copy(waterGoalMl = PREGNANCY_WATER, basis = GoalsBasis.PREGNANCY_FIRST_TRIMESTER)
        week <= 27 -> NutritionGoals(2190, 95, 70, 245, PREGNANCY_WATER, GoalsBasis.PREGNANCY_SECOND_TRIMESTER)
        else -> NutritionGoals(2300, 105, 72, 260, PREGNANCY_WATER, GoalsBasis.PREGNANCY_THIRD_TRIMESTER)
    }

    private val breastfeeding = NutritionGoals(2350, 105, 75, 270, 2700, GoalsBasis.BREASTFEEDING)

    /** Completed weeks, counted back from a 280-day pregnancy. */
    fun pregnancyWeek(dueDate: LocalDate, today: LocalDate): Int = (PREGNANCY_DAYS - today.daysUntil(dueDate)) / 7

    private const val PREGNANCY_DAYS = 280
    private const val PREGNANCY_WATER = 2300
    private const val BREASTFEEDING_DAYS = 365
}
