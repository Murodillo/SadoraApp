package org.example.project.model

/**
 * The shapes the health screens read.
 *
 * This file used to be `SampleData.kt` and carried a seeded copy of the whole product —
 * two meals, a medication course, five secret-chat posts with their likes, a plate of
 * salmon for the scanner, four symptom lists, a screening appointment and a night's
 * sleep stages. Every one of them was drawn on a phone that had recorded none of it.
 * What is left here is the types; the values come from the server.
 */

import uz.sadora.contract.FoodRelation
import uz.sadora.contract.MealSlot
import uz.sadora.contract.ScheduleKind

data class Meal(
    val id: String,
    val slot: MealSlot,
    val time: String,
    val description: String,
    val calories: Int,
    val protein: Int,
    val fat: Int,
    val carbs: Int,
) {
    /**
     * The thumbnail stand-in for a meal photo.
     *
     * Photos come from the scanner or, later, the server; until one exists the tile
     * shows the slot's own dish rather than a grey box.
     */
    val emoji: String
        get() = when (slot) {
            MealSlot.BREAKFAST -> "🥣"
            MealSlot.LUNCH -> "🍲"
            MealSlot.DINNER -> "🍽️"
            MealSlot.SNACK -> "🍎"
        }
}

enum class MedStatus { Taken, Pending, Skipped }

data class Medication(
    val id: String,
    val emoji: String,
    val name: String,
    val time: String,
    /** How often it is due. The words for it belong to the screen, not to the store. */
    val schedule: ScheduleKind,
    /** Her own note when she wrote one; otherwise the food relation, named by the screen. */
    val note: String?,
    val foodRelation: FoodRelation,
    val status: MedStatus,
    /** Days of stock left, when the user tracks supply. */
    val stockDays: Int? = null,
)


/** A catalogue entry. Values are per 100 g unless [perPiece] is set. */
data class FoodItem(
    val name: String,
    val kcal: Int,
    val protein: Int,
    val fat: Int,
    val carbs: Int,
    val perPiece: Boolean = false,
)
