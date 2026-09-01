package org.example.project.data

import org.example.project.model.AppState
import org.example.project.model.MedStatus
import org.example.project.model.Mood
import uz.sadora.contract.CycleStatus
import uz.sadora.contract.DailyHealth
import uz.sadora.contract.DailyLog
import uz.sadora.contract.DoseStatus
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.MealSlot
import uz.sadora.contract.Medication
import uz.sadora.contract.MedicationDay
import uz.sadora.contract.MoodLevel
import uz.sadora.contract.NutritionDay
import uz.sadora.contract.SymptomDefinition
import org.example.project.model.Meal as AppMeal
import org.example.project.model.Medication as AppMedication

/**
 * Copies server data onto the in-memory store the screens already read.
 *
 * The alternative was threading a controller through every screen and rewriting each one
 * to speak wire types. This keeps the UI untouched and the wire types out of it, the
 * same way `applyServerProfile` already does for the profile — and it means the whole
 * app goes live at once rather than tab by tab.
 *
 * The projection is deliberately lossy. `AppState` holds what the screens draw; anything
 * richer than that — a prediction's confidence, a dose's lateness — is read from the
 * controller by the screens that actually show it.
 */

fun AppState.applyCycle(status: CycleStatus) {
    status.cycleDay?.let { cycleDay = it }
    status.prediction.averageCycleLength?.let { averageCycleLength = it }
    status.prediction.averagePeriodLength?.let { averagePeriodLength = it }
}

/**
 * Symptoms are stored as labels because that is what the sheet renders; the catalogue
 * supplies them, so an unknown key is dropped rather than shown as a raw identifier.
 */
fun AppState.applyDay(log: DailyLog, catalogue: List<SymptomDefinition>) {
    mood = log.mood?.toAppMood() ?: mood
    val labels = catalogue.associate { it.key to it.label }
    symptoms.clear()
    symptoms.addAll(log.symptoms.mapNotNull { labels[it.key] })
}

fun AppState.applyNutrition(day: NutritionDay) {
    waterMl = day.waterMl
    waterGoalMl = day.goals.waterGoalMl
    caloriesEaten = day.totals.kcal
    calorieGoal = day.goals.calorieGoal
    proteinG = day.totals.proteinG
    proteinGoalG = day.goals.proteinGoalG
    fatG = day.totals.fatG
    fatGoalG = day.goals.fatGoalG
    carbsG = day.totals.carbsG
    carbsGoalG = day.goals.carbsGoalG

    meals.clear()
    meals.addAll(
        day.meals.map { meal ->
            AppMeal(
                id = meal.id,
                slot = meal.slot.label(),
                time = meal.eatenAt?.toString()?.take(5).orEmpty(),
                description = meal.description,
                calories = meal.kcal,
                protein = meal.proteinG,
                fat = meal.fatG,
                carbs = meal.carbsG,
            )
        },
    )
}

/**
 * The screens list a day's doses, so a twice-daily course appears twice — once per dose,
 * each with its own status. The medication row supplies the pack figure.
 */
fun AppState.applyMedications(day: MedicationDay, courses: List<Medication>) {
    val byId = courses.associateBy { it.id }
    medications.clear()
    medications.addAll(
        day.doses.map { dose ->
            val course = byId[dose.medicationId]
            AppMedication(
                id = "${dose.medicationId}@${dose.dueAt}",
                emoji = dose.emoji ?: "💊",
                name = listOfNotNull(dose.name, dose.dosage, course?.unit)
                    .joinToString(" ")
                    .trim(),
                time = dose.dueAt.toString().take(5),
                schedule = course?.schedule?.kind?.label() ?: "Har kuni",
                note = course?.note ?: dose.foodRelation.label(),
                status = dose.status.toAppStatus(),
                stockDays = course?.stockDaysLeft,
            )
        },
    )
}

/** Steps and sleep come from the wearable layer, already normalised and deduplicated. */
fun AppState.applyWearables(daily: DailyHealth) {
    daily.value(HealthMetric.STEPS)?.let { steps = it.toInt() }
    daily.value(HealthMetric.SLEEP_DURATION)?.let { sleepMinutes = it.toInt() }
}

private fun MoodLevel.toAppMood(): Mood = when (this) {
    MoodLevel.BAD -> Mood.Bad
    MoodLevel.LOW -> Mood.Low
    MoodLevel.OK -> Mood.Ok
    MoodLevel.GOOD -> Mood.Good
    MoodLevel.GREAT -> Mood.Great
}

fun Mood.toWire(): MoodLevel = when (this) {
    Mood.Bad -> MoodLevel.BAD
    Mood.Low -> MoodLevel.LOW
    Mood.Ok -> MoodLevel.OK
    Mood.Good -> MoodLevel.GOOD
    Mood.Great -> MoodLevel.GREAT
}

private fun DoseStatus.toAppStatus(): MedStatus = when (this) {
    DoseStatus.TAKEN -> MedStatus.Taken
    DoseStatus.SKIPPED -> MedStatus.Skipped
    DoseStatus.PENDING -> MedStatus.Pending
}

private fun MealSlot.label(): String = when (this) {
    MealSlot.BREAKFAST -> "Nonushta"
    MealSlot.LUNCH -> "Tushlik"
    MealSlot.DINNER -> "Kechki ovqat"
    MealSlot.SNACK -> "Gazak"
}

private fun uz.sadora.contract.ScheduleKind.label(): String = when (this) {
    uz.sadora.contract.ScheduleKind.DAILY -> "Har kuni"
    uz.sadora.contract.ScheduleKind.WEEKDAYS -> "Tanlangan kunlar"
    uz.sadora.contract.ScheduleKind.INTERVAL -> "Bir necha kunda"
}

private fun uz.sadora.contract.FoodRelation.label(): String = when (this) {
    uz.sadora.contract.FoodRelation.BEFORE -> "Ovqatdan oldin"
    uz.sadora.contract.FoodRelation.WITH -> "Ovqat bilan"
    uz.sadora.contract.FoodRelation.AFTER -> "Ovqatdan keyin"
    uz.sadora.contract.FoodRelation.ANY -> ""
}
