package uz.sadora.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import uz.sadora.app.model.AppState
import uz.sadora.contract.AppIconMood
import uz.sadora.contract.CoinAward
import uz.sadora.contract.CoinBalance
import uz.sadora.contract.CoinReasons
import uz.sadora.contract.DailyCheckInResult
import uz.sadora.contract.HomeLayout
import uz.sadora.contract.HomeWidget
import uz.sadora.contract.HomeWidgets
import uz.sadora.contract.StreakStatus

/**
 * The reward scheme, from the app's side.
 *
 * What these pin down is the boundary: the app displays what the server says and never
 * computes a balance, a streak or a celebration of its own. Everything else about the
 * scheme — how much an action pays, whether a day counted — is the server's, and there
 * is deliberately nothing here that would let the app disagree with it.
 */
class RewardsControllerTest {

    private fun graph(recording: RecordingEngine) = SadoraGraph(
        tokenStorage = InMemoryTokenStorage(token = "refresh-0"),
        device = FixedDeviceIdentity(),
        environment = SadoraEnvironment("http://test.local"),
        engine = recording.build(),
    )

    private fun checkIn(
        current: Int,
        celebrate: Boolean,
        milestone: Int? = null,
        balance: Int = 120,
    ) = DailyCheckInResult(
        streak = StreakStatus(current = current, longest = current, openedToday = true, totalDays = current),
        coins = CoinBalance(balance = balance, earned = balance, spent = 0),
        awards = if (celebrate) {
            listOf(CoinAward(CoinReasons.DAILY_OPEN, 10, "Kunlik kirish"))
        } else {
            emptyList()
        },
        celebrate = celebrate,
        milestone = milestone,
    )

    /** Records what the launcher was asked to show, so the icon rule can be asserted. */
    private class RecordingIcons : AppIcons {
        val applied = mutableListOf<AppIconMood>()
        override fun apply(mood: AppIconMood) {
            applied.add(mood)
        }
    }

    @Test
    fun `the check-in mirrors the server's numbers onto the store`() = runTest {
        val recording = RecordingEngine { json(encode(checkIn(current = 5, celebrate = true))) }
        val state = AppState()
        val rewards = RewardsController(graph(recording).rewardsApi, state)

        rewards.checkIn()

        assertEquals(120, state.coins)
        assertEquals(5, state.streakDays)
        assertTrue(state.streakOpenedToday)
    }

    @Test
    fun `the celebration is raised only when the server says a new day began`() = runTest {
        val quiet = RecordingEngine { json(encode(checkIn(current = 5, celebrate = false))) }
        val quietRewards = RewardsController(graph(quiet).rewardsApi, AppState())
        quietRewards.checkIn()
        assertNull(quietRewards.celebration, "a second open of the same day must not celebrate")

        val loud = RecordingEngine { json(encode(checkIn(current = 6, celebrate = true))) }
        val loudRewards = RewardsController(graph(loud).rewardsApi, AppState())
        loudRewards.checkIn()
        assertNotNull(loudRewards.celebration)

        loudRewards.celebrationShown()
        assertNull(loudRewards.celebration, "the overlay clears itself once it has played")
    }

    @Test
    fun `the launcher icon warms with the streak`() = runTest {
        val icons = RecordingIcons()
        val recording = RecordingEngine { json(encode(checkIn(current = 9, celebrate = true))) }
        val rewards = RewardsController(graph(recording).rewardsApi, AppState(), icons)

        rewards.checkIn()

        assertEquals(listOf(AppIconMood.WARM), icons.applied)
    }

    @Test
    fun `a short streak keeps the calm icon rather than the warm one`() = runTest {
        val icons = RecordingIcons()
        val recording = RecordingEngine { json(encode(checkIn(current = 2, celebrate = true))) }
        val rewards = RewardsController(graph(recording).rewardsApi, AppState(), icons)

        rewards.checkIn()

        assertEquals(listOf(AppIconMood.CALM), icons.applied)
    }

    @Test
    fun `with no backend nothing is claimed and no coin appears`() = runTest {
        val state = AppState()
        val rewards = RewardsController(null, state)

        rewards.checkIn()
        rewards.loadSummary()
        rewards.loadCatalog()

        assertEquals(0, state.coins)
        assertEquals(0, state.streakDays)
        assertNull(rewards.celebration)
        assertNull(rewards.catalog)
        assertTrue(rewards.isOffline)
    }

    @Test
    fun `hiding a widget writes the whole arrangement and keeps the rest in order`() = runTest {
        val recording = RecordingEngine { json(encode(HomeLayout())) }
        val state = AppState()
        val rewards = RewardsController(graph(recording).rewardsApi, state)

        rewards.setWidgetVisible(HomeWidgets.SCORE, visible = false)

        val body = recording.bodies.last()
        assertTrue(body.contains(HomeWidgets.SCORE), "the saved layout names every widget")
        assertEquals(HomeWidgets.keys.size, state.homeLayout.widgets.size)
    }

    @Test
    fun `the one required widget cannot be switched off`() = runTest {
        val recording = RecordingEngine { json(encode(HomeLayout())) }
        val state = AppState()
        val rewards = RewardsController(graph(recording).rewardsApi, state)

        rewards.setWidgetVisible(HomeWidgets.AI, visible = false)

        assertTrue(HomeWidgets.AI in state.homeWidgets())
        assertTrue(recording.paths.isEmpty(), "a refused change is not sent to the server")
    }

    @Test
    fun `moving a widget reorders it locally before the server answers`() = runTest {
        // The server echoes the default order back; the point of the test is that the
        // local list moved first, which is what makes the row follow the finger.
        val recording = RecordingEngine { json(encode(HomeLayout())) }
        val state = AppState()
        val rewards = RewardsController(graph(recording).rewardsApi, state)

        val second = state.homeLayout.widgets.sortedBy { it.position }[1].key
        rewards.moveWidget(second, by = -1)

        val sent = recording.bodies.last()
        assertTrue(sent.indexOf(second) < sent.indexOf(HomeWidgets.AI), "the moved card is sent first")
    }

    @Test
    fun `a layout from a newer release keeps its known cards and drops the rest`() {
        val stored = HomeLayout(
            listOf(
                HomeWidget(HomeWidgets.STREAK, 0, true),
                HomeWidget("a_widget_from_the_future", 1, true),
                HomeWidget(HomeWidgets.AI, 2, true),
            ),
        )

        val reconciled = stored.reconciled()

        assertEquals(HomeWidgets.keys.size, reconciled.widgets.size)
        assertFalse(reconciled.widgets.any { it.key == "a_widget_from_the_future" })
        assertEquals(HomeWidgets.STREAK, reconciled.visible().first())
    }
}
