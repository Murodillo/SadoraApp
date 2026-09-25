package uz.sadora.doctor.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Before the stored session is checked, signing in, and the app itself. */
enum class AppPhase { Splash, SignIn, Main }

/**
 * The screens of the signed-in app. [Panel] is the root; the rest are pushed on top of
 * it and popped by the back arrow or the system Back.
 */
sealed interface Route {
    /** The doctor panel: whatever her status, it is where she lands. */
    data object Panel : Route

    /** The application form — first time, or again after a rejection. */
    data object Apply : Route

    /** A question (or any post) on its own page, with its thread and her answer. */
    data class Question(val postId: String) : Route

    /** Her public page, as readers see it, by her doctor id. */
    data class MyPage(val profileId: String) : Route

    /** A post of her own. */
    data object NewPost : Route
}

/** A route and how deep in the stack it sits: pushing goes deeper, popping comes back. */
data class Screen(val route: Route, val depth: Int)

/**
 * Minimal navigation state, in the client app's style: the phase, plus a back stack of
 * pushed routes over the root. The project has no navigation dependency and needs none.
 */
class Navigator {
    var phase by mutableStateOf(AppPhase.Splash)
        private set

    private val stack = mutableStateListOf<Route>()

    val current: Route get() = stack.lastOrNull() ?: Route.Panel
    val canGoBack: Boolean get() = stack.isNotEmpty()

    /** How many screens sit over the root — what a transition reads to know its direction. */
    val depth: Int get() = stack.size

    /** The route on top with its depth, as one value for the route transition to key on. */
    val screen: Screen get() = Screen(current, stack.size)

    fun goTo(phase: AppPhase) {
        this.phase = phase
        stack.clear()
    }

    fun push(route: Route) {
        // A double-tap on a card is one intention; it must not stack the screen twice.
        if (route != Route.Panel && stack.lastOrNull() != route) stack.add(route)
    }

    fun pop() {
        stack.removeLastOrNull()
    }
}
