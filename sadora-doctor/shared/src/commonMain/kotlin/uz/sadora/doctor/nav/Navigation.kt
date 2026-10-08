package uz.sadora.doctor.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Before the stored session is checked, signing in, and the app itself. */
enum class AppPhase { Splash, SignIn, Main }

/**
 * The five roots of an approved doctor's app, in the order the bar draws them. Before
 * she is approved there is no bar: the panel, with her application, is the whole app.
 */
enum class Tab {
    /** The day at a glance: her status, the numbers, the questions waiting. */
    Home,

    /** Her private conversations. */
    Messages,

    /** The camera, for a patient's "show my doctor" QR code. */
    Scan,

    /** The community feed, where her posts and answers go out under her name. */
    Community,

    /** Her public page and the details she may change. */
    Profile,
}

/**
 * The screens of the signed-in app. [Panel] is the root — the selected tab, or the
 * panel alone before approval; the rest are pushed on top of it and popped by the back
 * arrow or the system Back.
 */
sealed interface Route {
    /** The root: whatever her status, it is where she lands. */
    data object Panel : Route

    /** The application form — first time, or again after a rejection. */
    data object Apply : Route

    /** A question (or any post) on its own page, with its thread and her answer. */
    data class Question(val postId: String) : Route

    /** Her public page, as readers see it, by her doctor id. */
    data class MyPage(val profileId: String) : Route

    /** A post of her own. */
    data object NewPost : Route

    /** One consultation, with a patient. */
    data class Conversation(val id: String) : Route

    /** What a patient's QR code opened, read by its share token. */
    data class PatientRecord(val token: String) : Route

    /** The record a patient attached in a consultation. */
    data class AttachedRecord(val conversationId: String, val messageId: String) : Route

    /** Her price, hours and the busy switch. */
    data object WorkSettings : Route

    /** What she has earned, been paid, and is still owed. */
    data object Earnings : Route

    /** Her quick replies: the list, adding, editing, deleting. */
    data object QuickReplies : Route

    /** The patient of a consultation: her private note and their history together. */
    data class Patient(val conversationId: String) : Route

    /** Her badges: what she has earned and what is ahead. */
    data object Badges : Route

    /** Writing a prescription for the patient of a consultation. */
    data class WritePrescription(val conversationId: String) : Route
}

/** A route and how deep in the stack it sits: pushing goes deeper, popping comes back. */
data class Screen(val route: Route, val depth: Int)

/**
 * Minimal navigation state, in the client app's style: the phase, the selected tab, and
 * a back stack of pushed routes over it. The project has no navigation dependency and
 * needs none.
 */
class Navigator {
    var phase by mutableStateOf(AppPhase.Splash)
        private set

    var tab by mutableStateOf(Tab.Home)
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
        tab = Tab.Home
    }

    /** A tab is a root: choosing one leaves whatever was pushed over the last. */
    fun select(tab: Tab) {
        stack.clear()
        this.tab = tab
    }

    fun push(route: Route) {
        // A double-tap on a card is one intention; it must not stack the screen twice.
        if (route != Route.Panel && stack.lastOrNull() != route) stack.add(route)
    }

    fun pop() {
        stack.removeLastOrNull()
    }

    /**
     * A conversation opened from outside — a tapped push — over the Messages tab, so
     * Back from it lands on her list rather than on whatever she had open before.
     */
    fun openConversation(id: String) {
        stack.clear()
        tab = Tab.Messages
        stack.add(Route.Conversation(id))
    }
}
