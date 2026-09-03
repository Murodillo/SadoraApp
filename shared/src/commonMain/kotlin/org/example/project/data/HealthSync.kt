package org.example.project.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import org.example.project.model.AppStateSync
import org.example.project.model.Meal
import uz.sadora.contract.DoseStatus
import uz.sadora.contract.LogMealRequest
import uz.sadora.contract.MealSlot
import uz.sadora.contract.SymptomEntry

/**
 * Carries local edits to the server.
 *
 * Every method returns immediately and launches the request, because these are called
 * from the middle of a Compose click handler: the screen has already applied the change
 * optimistically, and the controller's refresh replaces it with whatever the server
 * actually stored.
 */
class HealthSync(
    private val health: HealthController,
    private val scope: CoroutineScope,
) : AppStateSync {

    override fun symptomToggled(label: String, nowSelected: Boolean) {
        val date = health.selectedDate ?: return
        val key = resolveSymptomKey(label) ?: return

        val current = health.day?.symptoms.orEmpty()
        val updated = if (nowSelected) {
            if (current.any { it.key == key }) current else current + SymptomEntry(key)
        } else {
            current.filterNot { it.key == key }
        }
        scope.launch { health.saveDay(date, symptomKeys = updated) }
    }

    /**
     * The store holds labels because that is what the sheet renders; the catalogue turns
     * one back into the key the API expects. An unknown label is a symptom the server
     * does not offer, so there is nothing to send.
     */
    internal fun resolveSymptomKey(label: String): String? =
        health.symptoms.firstOrNull { it.label == label }?.key

    override fun waterAdded(ml: Int) {
        scope.launch { health.addWater(ml) }
    }

    /**
     * The store's id packs the course and the time together, because a twice-daily
     * course is two rows on the screen and one medication on the server.
     */
    override fun doseTaken(doseId: String) {
        val date = health.doses?.date ?: health.selectedDate ?: return
        val medicationId = doseId.substringBefore('@')
        val dueAt = runCatching { LocalTime.parse(doseId.substringAfter('@', "")) }.getOrNull()
            ?: return
        scope.launch { health.recordDose(medicationId, date, dueAt, DoseStatus.TAKEN) }
    }

    override fun mealLogged(meal: Meal) {
        val date = health.selectedDate ?: return
        scope.launch {
            health.addMeal(
                LogMealRequest(
                    date = date,
                    slot = meal.slot.toSlot(),
                    eatenAt = runCatching { LocalTime.parse(meal.time) }.getOrNull(),
                    description = meal.description,
                    kcal = meal.calories,
                    proteinG = meal.protein,
                    fatG = meal.fat,
                    carbsG = meal.carbs,
                ),
            )
        }
    }

    private fun String.toSlot(): MealSlot = when (this) {
        "Nonushta" -> MealSlot.BREAKFAST
        "Tushlik" -> MealSlot.LUNCH
        "Kechki ovqat" -> MealSlot.DINNER
        else -> MealSlot.SNACK
    }
}
