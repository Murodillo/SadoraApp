package org.example.project.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppLanguage(val code: String, val native: String, val english: String) {
    Uz("UZ", "O'zbekcha", "Uzbek"),
    Ru("RU", "Русский", "Russian"),
    En("EN", "English", "English"),
}

/** The eight onboarding goals. Selected goals surface first on the Today screen. */
enum class Goal(val label: String) {
    UnderstandCycle("Siklni tushunish"),
    SleepBetter("Yaxshi uxlash"),
    MoreEnergy("Energiyani oshirish"),
    LessStress("Stressni kamaytirish"),
    EatBalanced("Muvozanatli ovqatlanish"),
    DrinkWater("Ko'proq suv ichish"),
    BeActive("Faolroq bo'lish"),
    RememberMeds("Dorilarni eslab qolish"),
}

enum class Mood(val emoji: String, val label: String, val score: Int) {
    Bad("😞", "Yomon", 1),
    Low("😕", "So'lg'in", 2),
    Ok("😐", "O'rtacha", 3),
    Good("🙂", "Yaxshi", 4),
    Great("😄", "Ajoyib", 5),
}

/**
 * Single in-memory store for the whole prototype.
 *
 * There is no backend in this project yet, so screens read and write here directly.
 * Everything is Compose state, so any mutation recomposes the affected screens.
 */
class AppState {
    // ---- account / onboarding ----
    var language by mutableStateOf(AppLanguage.Uz)
    var name by mutableStateOf("Malika")
    var email by mutableStateOf("malika@example.com")
    var phone by mutableStateOf("90 123 45 67")
    var birthDate by mutableStateOf("14.03.1994")
    var heightCm by mutableStateOf("164")
    var weightKg by mutableStateOf("58")
    var lifeStage by mutableStateOf(LifeStage.Cycle)
    val goals = mutableStateListOf(Goal.UnderstandCycle, Goal.SleepBetter)

    var notificationsAllowed by mutableStateOf(true)
    var healthDataAllowed by mutableStateOf(true)
    var cameraAllowed by mutableStateOf(false)

    var consentStoreHealth by mutableStateOf(true)
    var consentAiInsights by mutableStateOf(true)
    var consentAnalytics by mutableStateOf(false)

    // ---- subscription ----
    var isPremium by mutableStateOf(true)
    var premiumRenewal by mutableStateOf("14-mart 2027-yilgacha")

    // ---- appearance ----
    var darkTheme by mutableStateOf(true)

    // ---- daily data ----
    var cycleDay by mutableStateOf(14)
    var averageCycleLength by mutableStateOf(28)
    var averagePeriodLength by mutableStateOf(5)
    var pregnancyWeek by mutableStateOf(24)
    var postpartumWeek by mutableStateOf(7)

    var waterMl by mutableStateOf(1200)
    var waterGoalMl by mutableStateOf(2000)

    var caloriesEaten by mutableStateOf(1240)
    var calorieGoal by mutableStateOf(1850)
    var proteinG by mutableStateOf(61)
    var proteinGoalG by mutableStateOf(85)
    var fatG by mutableStateOf(38)
    var fatGoalG by mutableStateOf(62)
    var carbsG by mutableStateOf(132)
    var carbsGoalG by mutableStateOf(210)

    /**
     * True until the user has logged anything. Drives Today's empty state — the
     * fourth Today state in the design, alongside free, premium and skeleton.
     */
    var isNewUser by mutableStateOf(false)

    var mood by mutableStateOf(Mood.Good)
    var steps by mutableStateOf(6420)
    var sleepMinutes by mutableStateOf(400) // 6s 40d

    val symptoms = mutableStateListOf("Ajralma")
    val meals = mutableStateListOf(*SampleData.meals.toTypedArray())
    val medications = mutableStateListOf(*SampleData.medications.toTypedArray())

    fun toggleGoal(goal: Goal) {
        if (!goals.remove(goal)) goals.add(goal)
    }

    fun toggleSymptom(symptom: String) {
        if (!symptoms.remove(symptom)) symptoms.add(symptom)
    }

    fun addWater(ml: Int) {
        waterMl = (waterMl + ml).coerceAtLeast(0)
    }

    fun markMedicationTaken(id: String) {
        val index = medications.indexOfFirst { it.id == id }
        if (index >= 0) medications[index] = medications[index].copy(status = MedStatus.Taken)
    }

    fun logMeal(meal: Meal) {
        meals.add(meal)
        caloriesEaten += meal.calories
        proteinG += meal.protein
        fatG += meal.fat
        carbsG += meal.carbs
    }

    /**
     * Which phase a day of the current month falls in.
     *
     * Purely derived from the averages — the calendar marks anything after today as
     * predicted, so this never has to distinguish recorded from forecast itself.
     */
    fun phaseForDay(dayOfMonth: Int): CyclePhase {
        // Day 1 of the cycle fell on 6 August in the sample data.
        val cycleDayForDate = ((dayOfMonth - 6) % averageCycleLength + averageCycleLength) %
            averageCycleLength + 1
        return when {
            cycleDayForDate <= averagePeriodLength -> CyclePhase.Period
            cycleDayForDate in 12..16 -> CyclePhase.Fertile
            cycleDayForDate < 12 -> CyclePhase.Follicular
            else -> CyclePhase.Luteal
        }
    }

    /** "6s 40d" — the app's sleep-duration format. */
    fun sleepLabel(minutes: Int = sleepMinutes): String = "${minutes / 60}s ${minutes % 60}d"
}
