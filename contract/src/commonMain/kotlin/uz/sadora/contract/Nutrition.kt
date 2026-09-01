package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MealSlot {
    @SerialName("breakfast") BREAKFAST,
    @SerialName("lunch") LUNCH,
    @SerialName("dinner") DINNER,
    @SerialName("snack") SNACK,
}

/**
 * A catalogue entry. Values are per 100 g unless [perPiece] is set, which is how the
 * local dishes the search leads with are actually counted — a somsa is a somsa.
 */
@Serializable
data class FoodItem(
    val key: String,
    val name: String,
    val kcal: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
    val perPiece: Boolean = false,
)

/** One logged meal. Macros are stored as eaten, not recomputed from the catalogue. */
@Serializable
data class Meal(
    val id: String,
    val date: LocalDate,
    val slot: MealSlot,
    val eatenAt: LocalTime? = null,
    val description: String,
    val kcal: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
    val createdAt: Instant,
)

@Serializable
data class LogMealRequest(
    val date: LocalDate,
    val slot: MealSlot,
    val eatenAt: LocalTime? = null,
    val description: String,
    val kcal: Int,
    val proteinG: Int = 0,
    val fatG: Int = 0,
    val carbsG: Int = 0,
)

/**
 * The daily targets. Defaults are deliberately generic — the app does not compute a
 * calorie target from body measurements, which would be dietary advice it is not
 * qualified to give.
 */
@Serializable
data class NutritionGoals(
    val calorieGoal: Int = 1850,
    val proteinGoalG: Int = 85,
    val fatGoalG: Int = 62,
    val carbsGoalG: Int = 210,
    val waterGoalMl: Int = 2000,
)

@Serializable
data class UpdateNutritionGoalsRequest(
    val calorieGoal: Int? = null,
    val proteinGoalG: Int? = null,
    val fatGoalG: Int? = null,
    val carbsGoalG: Int? = null,
    val waterGoalMl: Int? = null,
)

@Serializable
data class NutritionTotals(
    val kcal: Int = 0,
    val proteinG: Int = 0,
    val fatG: Int = 0,
    val carbsG: Int = 0,
)

/** Everything the Nutrition tab renders for one day. */
@Serializable
data class NutritionDay(
    val date: LocalDate,
    val meals: List<Meal> = emptyList(),
    val totals: NutritionTotals = NutritionTotals(),
    val goals: NutritionGoals = NutritionGoals(),
    val waterMl: Int = 0,
)

/**
 * Adds water. [ml] may be negative — the app's undo takes the last amount straight back
 * off, and a running total that cannot go down would need a second endpoint to do it.
 */
@Serializable
data class AddWaterRequest(val ml: Int)

@Serializable
data class WaterState(val date: LocalDate, val waterMl: Int, val waterGoalMl: Int)
