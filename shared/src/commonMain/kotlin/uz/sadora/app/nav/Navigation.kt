package uz.sadora.app.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import uz.sadora.app.design.SadoraIcons

/**
 * The root destinations. The bar shows five of them, and which five depends on the
 * account — see [bar].
 *
 * Profile used to be the fifth tab. It is a settings screen — something she opens a
 * few times a month — and it now sits behind the avatar in the home header. The secret
 * chat took a slot of its own because it is the one place she comes back to daily
 * without being asked.
 */
enum class Tab(val icon: ImageVector) {
    Today(SadoraIcons.Home),
    /** Mind, and for a free account the food diary too, behind a switch at the top. */
    Mind(SadoraIcons.Heart),
    SecretChat(SadoraIcons.Chats),
    Journey(SadoraIcons.Journey),
    /** The food diary on its own — only once Premium has freed the fifth slot. */
    Nutrition(SadoraIcons.Apple),
    Premium(SadoraIcons.Sparkle),
    ;

    companion object {
        /**
         * The five slots, in order.
         *
         * The last one sells Premium until she has it; after that the pitch would be
         * a dead tab, so the food diary moves out of the Mind tab and takes the slot.
         */
        fun bar(isPremium: Boolean): List<Tab> = listOf(
            Today,
            Mind,
            SecretChat,
            Journey,
            if (isPremium) Nutrition else Premium,
        )
    }
}

/** What the Mind tab is showing for a free account: mind, or the food diary. */
enum class MindSection { Mind, Nutrition }

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
    /** One post in full, with its comments, over the chat tab. */
    data class Post(val id: String) : Route
    /** An alias's page — her own when the alias is hers. */
    data class AliasProfile(val alias: String) : Route
    /** Her private threads. */
    data object Messages : Route
    /** One thread. [id] is null until the first line to [alias] has been sent. */
    data class Conversation(val id: String?, val alias: String) : Route

    // Gul — the wallet, the shop and the invite screen.
    data object Rewards : Route
    data object Shop : Route
    data object Referral : Route

    /** Which cards Today draws, and in what order. */
    data object HomeLayout : Route

    // Her account: the screen that was the fifth tab, and the QR code for a doctor.
    data object Profile : Route
    data object ShareProfile : Route

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
fun uz.sadora.app.model.AppState.aiRoute(): Route =
    if (isPremium) Route.AiChat else Route.AiPreview

/**
 * A link the app was opened with, from outside.
 *
 * Two so far: an invite code from a shared link, and the return from a wearable
 * provider's consent page. Parsed in one place so both platforms read a URL the same
 * way, and so a wearable return can never be mistaken for an invite code — which the
 * Android entry point used to do by taking the last path segment of anything.
 */
sealed interface AppLink {
    data class Invite(val code: String) : AppLink
    data class WearableReturn(val provider: String, val ok: Boolean) : AppLink

    companion object {
        /**
         * `https://sadora.app/r/K7M2QP`, `sadora://invite/K7M2QP`, and
         * `sadora://wearables/whoop?status=ok`. Anything else is nothing.
         */
        fun parse(url: String): AppLink? {
            val withoutQuery = url.substringBefore('?')
            val query = url.substringAfter('?', "")
            val path = withoutQuery.substringAfter("://", "").trimEnd('/')
            val segments = path.split('/').filter { it.isNotBlank() }
            if (segments.isEmpty()) return null
            return when {
                segments[0].equals("wearables", ignoreCase = true) && segments.size >= 2 -> AppLink.WearableReturn(
                    provider = segments[1].lowercase(),
                    ok = query.split('&').any { it.equals("status=ok", ignoreCase = true) },
                )
                segments[0].equals("invite", ignoreCase = true) || segments.getOrNull(1) == "r" ||
                    (segments.size >= 2 && segments[segments.size - 2].equals("r", ignoreCase = true)) -> {
                    val raw = segments.last().uppercase().filter(Char::isLetterOrDigit)
                    raw.takeIf { it.length in 4..16 }?.let(AppLink::Invite)
                }
                else -> null
            }
        }
    }
}

/**
 * The link the platform received, waiting for the app to act on it.
 *
 * A single slot rather than a queue: a second link before the first is consumed replaces
 * it, which is what she meant by tapping it. The platform writes, the shell reads and
 * clears — and because it is Compose state, a link that arrives while the app is already
 * open is acted on without a restart.
 */
object AppLinks {
    var pending by mutableStateOf<AppLink?>(null)
        private set

    fun offer(url: String) {
        AppLink.parse(url)?.let { pending = it }
    }

    fun offer(link: AppLink?) {
        if (link != null) pending = link
    }

    fun consume(): AppLink? = pending.also { pending = null }
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

    /**
     * Which half of the free account's Mind tab is up. Kept here rather than in the
     * screen so that leaving the tab and coming back finds the same half.
     */
    var mindSection by mutableStateOf(MindSection.Mind)

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
