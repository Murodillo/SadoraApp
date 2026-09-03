package org.example.project.data.api

import io.ktor.client.request.setBody
import kotlinx.datetime.LocalDate
import org.example.project.data.ApiCaller
import org.example.project.data.ApiResult
import org.example.project.data.HttpMethodKind
import uz.sadora.contract.Ack
import uz.sadora.contract.AddWaterRequest
import uz.sadora.contract.FoodItem
import uz.sadora.contract.LogMealRequest
import uz.sadora.contract.Meal
import uz.sadora.contract.NutritionDay
import uz.sadora.contract.NutritionGoals
import uz.sadora.contract.UpdateNutritionGoalsRequest
import uz.sadora.contract.WaterState

class NutritionApi(private val caller: ApiCaller) {

    suspend fun today(): ApiResult<NutritionDay> =
        caller.authenticated("v1/nutrition/today", HttpMethodKind.GET)

    suspend fun day(date: LocalDate): ApiResult<NutritionDay> =
        caller.authenticated("v1/nutrition/days/$date", HttpMethodKind.GET)

    suspend fun addMeal(request: LogMealRequest): ApiResult<Meal> =
        caller.authenticated("v1/nutrition/meals", HttpMethodKind.POST) { setBody(request) }

    suspend fun deleteMeal(id: String): ApiResult<Ack> =
        caller.authenticated("v1/nutrition/meals/$id", HttpMethodKind.DELETE)

    /** A negative amount is the undo the water sheet offers. */
    suspend fun addWater(ml: Int): ApiResult<WaterState> =
        caller.authenticated("v1/nutrition/water", HttpMethodKind.POST) { setBody(AddWaterRequest(ml)) }

    suspend fun goals(): ApiResult<NutritionGoals> =
        caller.authenticated("v1/nutrition/goals", HttpMethodKind.GET)

    suspend fun updateGoals(request: UpdateNutritionGoalsRequest): ApiResult<NutritionGoals> =
        caller.authenticated("v1/nutrition/goals", HttpMethodKind.PUT) { setBody(request) }

    suspend fun searchFoods(query: String?): ApiResult<List<FoodItem>> {
        val suffix = query?.takeIf { it.isNotBlank() }?.let { "?q=$it" }.orEmpty()
        return caller.authenticated("v1/nutrition/foods$suffix", HttpMethodKind.GET)
    }
}
