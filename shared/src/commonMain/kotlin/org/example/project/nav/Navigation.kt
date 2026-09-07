package org.example.project.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import org.example.project.design.SadoraIcons

/**
 * The five root destinations, in the order the deck's tab bar draws them:
 * home, mind, cycle, nutrition, profile.
 */
enum class Tab(val icon: ImageVector) {
    Today(SadoraIcons.Home),
    Mind(SadoraIcons.Heart),
    Journey(SadoraIcons.Journey),
    Nutrition(SadoraIcons.Apple),
    Profile(SadoraIcons.Profile),
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

    // AI — the chat itself is Premium; free accounts land on the preview.
    data object AiChat : Route
    data object AiPreview : Route

    // Nutrition
    data object FoodSearch : Route
    data object FoodScanCamera : Route
    data object Balance : Route

    // Modules
    data object MindJournal : Route
    data object Medications : Route
    data object AddMedication : Route
    data object MedicationHistory : Route
    data object Sleep : Route
    data object Insights : Route
    data object Knowledge : Route
    /** The article's slug: its identity on the server and in every link. */
    data class Article(val slug: String) : Route
    data object DataSources : Route
    data object Paywall : Route
    data object SecretChat : Route

    // Settings
    data object PersonalDetails : Route
    data object GoalsSettings : Route
    data object LifeStageSettings : Route
    data object Notifications : Route
    data object PrivacySecurity : Route
    data object LanguageSettings : Route
    data object About : Route

    // Legal
    data object Terms : Route
    data object PrivacyPolicy : Route
}

/**
 * Screens that take the whole display and hide the tab bar: the AI assistant is drawn
 * on its own dark ground in the deck, and a camera viewfinder has nowhere to put a bar.
 */
val Route.isFullScreen: Boolean
    get() = this == Route.AiChat || this == Route.FoodScanCamera || this == Route.Paywall

/**
 * Where an AI entry point leads. The chat runs for Premium only; a free account sees
 * the value proposition and a way to upgrade, never a chat that answers nothing.
 */
fun org.example.project.model.AppState.aiRoute(): Route =
    if (isPremium) Route.AiChat else Route.AiPreview

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
