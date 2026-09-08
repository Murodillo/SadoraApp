package uz.sadora.app.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import uz.sadora.app.model.Fmt
import uz.sadora.app.model.AppState
import uz.sadora.app.model.CyclePhase
import uz.sadora.app.model.MedStatus
import uz.sadora.app.model.JournalNote
import uz.sadora.app.model.Mood
import uz.sadora.contract.CycleStatus
import uz.sadora.contract.DailyHealth
import uz.sadora.contract.DailyLog
import uz.sadora.contract.DoseStatus
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.Medication
import uz.sadora.contract.MedicationDay
import uz.sadora.contract.ScheduleKind
import uz.sadora.contract.MindSummary
import uz.sadora.contract.MoodLevel
import uz.sadora.contract.NutritionDay
import uz.sadora.contract.PredictionConfidence
import uz.sadora.contract.SymptomDefinition
import uz.sadora.app.model.Meal as AppMeal
import uz.sadora.app.model.Medication as AppMedication
import uz.sadora.contract.CyclePhase as WirePhase

/**
 * Copies server data onto the in-memory store the screens already read.
 *
 * The alternative was threading a controller through every screen and rewriting each one
 * to speak wire types. This keeps the UI untouched and the wire types out of it, the
 * same way `applyServerProfile` already does for the profile — and it means the whole
 * app goes live at once rather than tab by tab.
 *
 * The projection is deliberately lossy. `AppState` holds what the screens draw; anything
 * richer than that — a dose's lateness, a prediction's spread — is read from the
 * controller by the screens that actually show it.
 */

fun AppState.applyCycle(status: CycleStatus) {
    today = status.today
    status.cycleDay?.let { cycleDay = it }
    status.prediction.averageCycleLength?.let { averageCycleLength = it }
    status.prediction.averagePeriodLength?.let { averagePeriodLength = it }
    // The anchor is derived from the server's own day count rather than taken from a
    // period record: an unended period from an earlier cycle can still be "current" on
    // the server, and counting from it would put every phase on the dial a cycle out.
    cycleStartDate = status.cycleDay?.let { status.today.minus(it - 1, DateTimeUnit.DAY) }
        ?: status.lastPeriodStart
        ?: cycleStartDate
    cyclePhase = status.phase?.toAppPhase()
    daysUntilNextPeriod = status.daysUntilNextPeriod
    fertileFrom = status.prediction.fertileFrom
    fertileUntil = status.prediction.fertileUntil
    hasCyclePrediction = status.prediction.confidence != PredictionConfidence.NONE
}

/**
 * Symptoms are stored as labels because that is what the sheet renders; the catalogue
 * supplies them, so an unknown key is dropped rather than shown as a raw identifier.
 */
fun AppState.applyDay(log: DailyLog, catalogue: List<SymptomDefinition>) {
    mood = log.mood?.toAppMood() ?: mood
    log.energy?.let { energy = it.coerceIn(1, 5) }
    log.stress?.let { stress = it.coerceIn(1, 5) }
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
                slot = meal.slot,
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
                schedule = course?.schedule?.kind ?: ScheduleKind.DAILY,
                note = course?.note,
                foodRelation = dose.foodRelation,
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

private fun WirePhase.toAppPhase(): CyclePhase = when (this) {
    WirePhase.PERIOD -> CyclePhase.Period
    WirePhase.FOLLICULAR -> CyclePhase.Follicular
    WirePhase.FERTILE -> CyclePhase.Fertile
    WirePhase.LUTEAL -> CyclePhase.Luteal
}

fun MoodLevel.toAppMood(): Mood = when (this) {
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

/**
 * Mirrors the Mind summary: the check-in the screens already show, and the journal.
 *
 * The list is replaced rather than merged. An entry written a moment ago is in it under
 * a local id, and the server's copy is the one that should survive — merging would leave
 * the optimistic duplicate behind.
 */
fun AppState.applyMind(summary: MindSummary) {
    val zone = TimeZone.currentSystemDefault()
    journal.clear()
    journal.addAll(
        summary.recentEntries
            .sortedByDescending { it.createdAt }
            .map { entry ->
                JournalNote(
                    id = entry.id,
                    date = entry.date,
                    time = Fmt.time(entry.createdAt.toLocalDateTime(zone)),
                    body = entry.body,
                )
            },
    )
}
