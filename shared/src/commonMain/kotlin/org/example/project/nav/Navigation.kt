package org.example.project.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The five root destinations. */
enum class Tab(val glyph: String, val label: String) {
    Today("◆", "Bugun"),
    Journey("◔", "Sikl"),
    Ai("✦", "AI"),
    Nutrition("◍", "Ovqat"),
    Profile("◯", "Profil"),
}

/** Screens pushed on top of a tab. */
sealed interface Route {
    // Cycle
    data object CycleCalendar : Route
    data class CycleDay(val date: String) : Route

    // Pregnancy
    data object PregnancyAppointments : Route
    data object PregnancyCheckIn : Route

    // Menopause / perimenopause
    data object StageSymptoms : Route
    data object StageSleepMood : Route

    // AI
    data object AiChat : Route

    // Nutrition
    data object FoodSearch : Route
    data object FoodScanCamera : Route
    data object FoodScan : Route
    data object Balance : Route

    // Modules
    data object Mind : Route
    data object MindJournal : Route
    data object Medications : Route
    data object AddMedication : Route
    data object MedicationHistory : Route
    data object Sleep : Route
    data object Insights : Route
    data object Knowledge : Route
    data class Article(val title: String) : Route
    data object DataSources : Route
    data object Paywall : Route

    // Settings
    data object PersonalDetails : Route
    data object GoalsSettings : Route
    data object LifeStageSettings : Route
    data object Notifications : Route
    data object PrivacySecurity : Route
    data object About : Route
}

/** Where the app is before the main tabs take over. */
sealed interface AppPhase {
    data object Splash : AppPhase
    data object Onboarding : AppPhase
    data object SignIn : AppPhase
    data object Main : AppPhase
}

/**
 * Minimal navigation state.
 *
 * The project has no navigation dependency, so this holds the current tab plus a
 * back stack of pushed routes and exposes the operations screens need.
 */
class Navigator {
    var phase by mutableStateOf<AppPhase>(AppPhase.Splash)
        private set

    var tab by mutableStateOf(Tab.Today)
        private set

    private val stack = mutableStateListOf<Route>()

    val current: Route? get() = stack.lastOrNull()
    val canGoBack: Boolean get() = stack.isNotEmpty()

    fun goTo(phase: AppPhase) {
        this.phase = phase
        stack.clear()
    }

    fun select(tab: Tab) {
        if (this.tab != tab) stack.clear()
        this.tab = tab
    }

    fun push(route: Route) {
        stack.add(route)
    }

    fun pop() {
        stack.removeLastOrNull()
    }

    /**
     * Swap the top of the stack. Used by linear flows such as
     * camera -> analysing -> result, where backing up to the previous step
     * would be wrong.
     */
    fun replaceTop(route: Route) {
        stack.removeLastOrNull()
        stack.add(route)
    }

    fun popToRoot() {
        stack.clear()
    }
}
