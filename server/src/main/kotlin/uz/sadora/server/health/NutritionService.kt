package uz.sadora.server.health

import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import uz.sadora.contract.AddWaterRequest
import uz.sadora.contract.FeatureKeys
import uz.sadora.contract.FoodItem
import uz.sadora.contract.LogMealRequest
import uz.sadora.contract.Meal
import uz.sadora.contract.NutritionDay
import uz.sadora.contract.NutritionGoals
import uz.sadora.contract.NutritionTotals
import uz.sadora.contract.UpdateNutritionGoalsRequest
import uz.sadora.contract.WaterState
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now

/**
 * Food, water and the day's targets.
 *
 * The totals are summed from the logged meals rather than kept as a counter, so deleting
 * a meal cannot leave the day's figure wrong — the app's undo is a delete, and a stored
 * running total would drift the first time one was missed.
 */
class NutritionService(
    private val nutrition: NutritionRepository,
    private val access: HealthAccess,
) {

    suspend fun day(userId: Uuid, date: LocalDate?): NutritionDay {
        val user = access.requireUser(userId)
        val target = date ?: now().dayIn(user.timezone)
        val meals = nutrition.mealsOn(userId, target)
        return NutritionDay(
            date = target,
            meals = meals,
            totals = meals.total(),
            goals = nutrition.goalsOf(userId),
            waterMl = nutrition.waterOn(userId, target),
        )
    }

    suspend fun addMeal(userId: Uuid, request: LogMealRequest): Meal {
        val user = access.requireWritable(userId, FeatureKeys.NUTRITION_LOG)
        if (request.description.isBlank()) {
            throw ValidationException("description", "Bo'sh bo'lishi mumkin emas")
        }
        if (request.description.length > MAX_DESCRIPTION) {
            throw ValidationException("description", "Eng ko'pi $MAX_DESCRIPTION belgi")
        }
        if (request.date > now().dayIn(user.timezone)) {
            throw ValidationException("date", "Kelajakdagi kun uchun ovqat qo'shib bo'lmaydi")
        }
        validateAmount("kcal", request.kcal, MAX_KCAL)
        validateAmount("proteinG", request.proteinG, MAX_MACRO)
        validateAmount("fatG", request.fatG, MAX_MACRO)
        validateAmount("carbsG", request.carbsG, MAX_MACRO)

        val id = nutrition.addMeal(userId, request)
        return nutrition.mealsOn(userId, request.date).firstOrNull { it.id == id.toString() }
            ?: throw NotFoundException("Ovqat topilmadi")
    }

    suspend fun deleteMeal(userId: Uuid, id: Uuid) {
        access.requireWritable(userId, FeatureKeys.NUTRITION_LOG)
        if (!nutrition.deleteMeal(userId, id)) throw NotFoundException("Ovqat topilmadi")
    }

    /** A negative [AddWaterRequest.ml] is the undo path, so it is allowed here. */
    suspend fun addWater(userId: Uuid, request: AddWaterRequest): WaterState {
        val user = access.requireWritable(userId, FeatureKeys.NUTRITION_LOG)
        if (request.ml == 0) throw ValidationException("ml", "Nol bo'lishi mumkin emas")
        if (kotlin.math.abs(request.ml) > MAX_WATER_STEP) {
            throw ValidationException("ml", "Bir marta eng ko'pi $MAX_WATER_STEP ml")
        }
        val today = now().dayIn(user.timezone)
        val total = nutrition.addWater(userId, today, request.ml)
        return WaterState(today, total, nutrition.goalsOf(userId).waterGoalMl)
    }

    suspend fun goals(userId: Uuid): NutritionGoals {
        access.requireUser(userId)
        return nutrition.goalsOf(userId)
    }

    suspend fun updateGoals(userId: Uuid, request: UpdateNutritionGoalsRequest): NutritionGoals {
        access.requireWritable(userId, FeatureKeys.NUTRITION_LOG)
        val current = nutrition.goalsOf(userId)
        val updated = NutritionGoals(
            calorieGoal = request.calorieGoal ?: current.calorieGoal,
            proteinGoalG = request.proteinGoalG ?: current.proteinGoalG,
            fatGoalG = request.fatGoalG ?: current.fatGoalG,
            carbsGoalG = request.carbsGoalG ?: current.carbsGoalG,
            waterGoalMl = request.waterGoalMl ?: current.waterGoalMl,
        )
        // Bounds, not advice: the product does not compute a target from her body, it
        // only refuses figures that cannot be meant seriously.
        validateRange("calorieGoal", updated.calorieGoal, 800, 6000)
        validateRange("proteinGoalG", updated.proteinGoalG, 10, 400)
        validateRange("fatGoalG", updated.fatGoalG, 10, 300)
        validateRange("carbsGoalG", updated.carbsGoalG, 10, 800)
        validateRange("waterGoalMl", updated.waterGoalMl, 500, 6000)

        nutrition.saveGoals(userId, updated)
        return updated
    }

    suspend fun searchFoods(userId: Uuid, query: String?): List<FoodItem> {
        access.requireUser(userId)
        return nutrition.searchFoods(query, FOOD_SEARCH_LIMIT)
    }

    private fun List<Meal>.total() = NutritionTotals(
        kcal = sumOf { it.kcal },
        proteinG = sumOf { it.proteinG },
        fatG = sumOf { it.fatG },
        carbsG = sumOf { it.carbsG },
    )

    private fun validateAmount(field: String, value: Int, max: Int) {
        if (value < 0) throw ValidationException(field, "Manfiy bo'lishi mumkin emas")
        if (value > max) throw ValidationException(field, "Eng ko'pi $max")
    }

    private fun validateRange(field: String, value: Int, min: Int, max: Int) {
        if (value !in min..max) throw ValidationException(field, "$min–$max oralig'ida bo'lishi kerak")
    }

    private companion object {
        const val MAX_DESCRIPTION = 200
        const val MAX_KCAL = 10_000
        const val MAX_MACRO = 1_000
        const val MAX_WATER_STEP = 2_000
        const val FOOD_SEARCH_LIMIT = 50
    }
}
