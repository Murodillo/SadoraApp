package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import uz.sadora.app.data.api.RewardsApi
import uz.sadora.app.model.AppState
import uz.sadora.contract.AppIconMood
import uz.sadora.contract.BadgeBoard
import uz.sadora.contract.BadgeUnlock
import uz.sadora.contract.WornBadge
import uz.sadora.contract.DailyCheckInResult
import uz.sadora.contract.HomeLayout
import uz.sadora.contract.HomeWidget
import uz.sadora.contract.HomeWidgets
import uz.sadora.contract.RedeemResult
import uz.sadora.contract.Redemption
import uz.sadora.contract.ReferralStatus
import uz.sadora.contract.RewardsSummary
import uz.sadora.contract.ShopCatalog
import uz.sadora.contract.ShopKind
import uz.sadora.contract.ShopProduct

/**
 * The app's side of the reward scheme.
 *
 * Everything it holds comes from the server; nothing is computed here. That is the whole
 * design: a balance the app worked out itself would be a balance a reinstall could
 * change, and a streak the device counted would be one a clock change could fake.
 *
 * With no backend — previews, tests — every call is a no-op and the screens draw their
 * empty states, exactly as they do for the other controllers.
 */
class RewardsController(
    private val api: RewardsApi?,
    private val state: AppState,
    private val icons: AppIcons = AppIcons.None,
) {
    private val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error
    val isOffline: Boolean get() = api == null

    fun clearError() = calls.clearError()

    var summary by mutableStateOf<RewardsSummary?>(null)
        private set

    var catalog by mutableStateOf<ShopCatalog?>(null)
        private set

    var referral by mutableStateOf<ReferralStatus?>(null)
        private set

    val redemptions = mutableStateListOf<Redemption>()

    /**
     * The check-in the overlay is waiting for, or null once it has been shown.
     *
     * Held here rather than returned so the shell can raise the celebration from
     * wherever the app happens to be when the answer lands.
     */
    var celebration by mutableStateOf<DailyCheckInResult?>(null)
        private set

    /** How warm the launcher icon should be, given the streak the server reports. */
    var iconMood by mutableStateOf(AppIconMood.CALM)
        private set

    fun celebrationShown() {
        celebration = null
    }

    // ---------------------------------------------------------------- launch

    /**
     * "The app just opened."
     *
     * Runs once per entry to the tab shell. The server decides whether this open was the
     * first of a day; only then does [celebration] fill in and the overlay appear.
     */
    suspend fun checkIn() {
        val api = api ?: return
        val result = calls.run(silent = true) { api.checkIn() } ?: return
        applyStreak(result)
        if (result.celebrate) celebration = result
    }

    private fun applyStreak(result: DailyCheckInResult) {
        state.coins = result.coins.balance
        state.streakDays = result.streak.current
        state.longestStreak = result.streak.longest
        state.streakOpenedToday = result.streak.openedToday
        iconMood = AppIconMood.forStreak(
            current = result.streak.current,
            // The check-in has just recorded today, so a live streak is zero days stale.
            daysSinceLastOpen = if (result.streak.openedToday) 0 else 1,
        )
        // The home screen follows the streak. Idempotent, so calling it on every launch
        // costs nothing on the days the mood has not changed.
        icons.apply(iconMood)
    }

    // ---------------------------------------------------------------- badges

    var badges by mutableStateOf<BadgeBoard?>(null)
        private set

    /**
     * Tiers reached and not yet celebrated, oldest first. The shell plays the head of
     * the queue, one at a time, once the streak overlay has gone.
     */
    val unlocks = mutableStateListOf<BadgeUnlock>()

    /**
     * Reads the board, which is also what awards it: the server counts now and writes any
     * tier newly crossed. Called on launch, on coming back to the app, and on returning to
     * Today — so a badge earned by logging a meal plays as she lands back on the home tab.
     */
    suspend fun loadBadges(force: Boolean = false) {
        val api = api ?: return
        val now = TimeSource.Monotonic.markNow()
        if (!force && lastBadgeRead?.let { now - it < BADGE_READ_GAP } == true) return
        lastBadgeRead = now
        val loaded = calls.run(silent = true) { api.badges() } ?: return
        applyBoard(loaded)
        val queued = unlocks.map { it.key to it.tier }.toSet()
        unlocks.addAll(loaded.unseen.filter { (it.key to it.tier) !in queued })
        // A new tier pays Gul; the board read is where that happens, so the pill follows.
        if (loaded.unseen.any { it.coins > 0 }) loadBalanceQuietly()
    }

    private fun applyBoard(board: BadgeBoard) {
        badges = board
        state.applyWornBadge(board.wornBadge())
    }

    /**
     * Wears a badge, or takes hers off with null. Applied on screen at once — her name and
     * her posts change as she taps — and put back if the server refuses.
     */
    suspend fun wear(key: String?) {
        val api = api ?: return
        val before = badges
        badges = before?.copy(worn = key)
        state.applyWornBadge(badges?.wornBadge())
        val saved = calls.run { api.wearBadge(key) }
        if (saved != null) applyBoard(saved) else before?.let(::applyBoard)
    }

    private suspend fun loadBalanceQuietly() {
        val api = api ?: return
        calls.run(silent = true) { api.summary() }?.let { state.coins = it.coins.balance }
    }

    private var lastBadgeRead: TimeSource.Monotonic.ValueTimeMark? = null

    /** The head of [unlocks] has been played; tell the server so it is not played again. */
    suspend fun unlockShown(unlock: BadgeUnlock) {
        unlocks.removeAll { it.key == unlock.key && it.tier <= unlock.tier }
        val api = api ?: return
        calls.run(silent = true) { api.badgesSeen(listOf(unlock.key)) }
    }

    /** Dismissed all at once ("skip"): everything still queued counts as seen. */
    suspend fun unlocksSkipped() {
        val keys = unlocks.map { it.key }.distinct()
        unlocks.clear()
        val api = api ?: return
        if (keys.isNotEmpty()) calls.run(silent = true) { api.badgesSeen(keys) }
    }

    // ---------------------------------------------------------------- wallet

    suspend fun loadSummary() {
        val api = api ?: return
        val loaded = calls.run(silent = true) { api.summary() } ?: return
        summary = loaded
        state.coins = loaded.coins.balance
        state.streakDays = loaded.streak.current
        state.longestStreak = loaded.streak.longest
        state.streakOpenedToday = loaded.streak.openedToday
        loaded.referral?.let {
            referral = it
            state.referralCode = it.code
        }
    }

    suspend fun loadReferral() {
        val api = api ?: return
        val loaded = calls.run(silent = true) { api.referral() } ?: return
        referral = loaded
        state.referralCode = loaded.code
    }

    // ---------------------------------------------------------------- shop

    suspend fun loadCatalog(force: Boolean = false) {
        val api = api ?: return
        if (!force && catalog != null) return
        val loaded = calls.run(silent = true) { api.catalogue() } ?: return
        catalog = loaded
        state.coins = loaded.balance
    }

    suspend fun loadRedemptions() {
        val api = api ?: return
        val loaded = calls.run(silent = true) { api.redemptions() } ?: return
        redemptions.clear()
        redemptions.addAll(loaded)
    }

    /** The products of one kind, in the order the server sent them. */
    fun productsOf(kind: ShopKind): List<ShopProduct> =
        catalog?.products?.filter { it.kind == kind }.orEmpty()

    /**
     * Spends the coins.
     *
     * Not silent: this is the one call in the controller the user is waiting on, and
     * "not enough Gul" or "out of stock" has to reach the sheet that asked.
     */
    suspend fun redeem(productId: String): RedeemResult? {
        val api = api ?: return null
        val result = calls.run { api.redeem(productId) } ?: return null
        state.coins = result.coins.balance
        // The catalogue's affordability flags and stock are now stale for every card.
        loadCatalog(force = true)
        loadRedemptions()
        return result
    }

    // ---------------------------------------------------------------- home layout

    suspend fun loadHomeLayout() {
        val api = api ?: return
        val loaded = calls.run(silent = true) { api.homeLayout() } ?: return
        state.homeLayout = loaded.reconciled()
    }

    /**
     * Saves the arrangement, applying it locally first.
     *
     * Rearranging a home screen is a direct manipulation: the card has to move under the
     * finger that dragged it, not after a round trip. The server's answer replaces it,
     * and a failure leaves what she did on screen rather than snapping it back.
     */
    suspend fun saveHomeLayout(widgets: List<HomeWidget>) {
        val normalised = widgets.mapIndexed { index, widget -> widget.copy(position = index) }
        state.homeLayout = HomeLayout(normalised)
        val api = api ?: return
        calls.run(silent = true) { api.saveHomeLayout(normalised) }?.let {
            state.homeLayout = it.reconciled()
        }
    }

    suspend fun resetHomeLayout() {
        state.homeLayout = HomeLayout()
        val api = api ?: return
        calls.run(silent = true) { api.resetHomeLayout() }?.let { state.homeLayout = it.reconciled() }
    }

    /** Moves one card up or down, then saves. Bounds are the caller's to ignore. */
    suspend fun moveWidget(key: String, by: Int) {
        val widgets = state.homeLayout.widgets.sortedBy { it.position }.toMutableList()
        val index = widgets.indexOfFirst { it.key == key }
        val target = index + by
        if (index < 0 || target !in widgets.indices) return
        widgets.add(target, widgets.removeAt(index))
        saveHomeLayout(widgets)
    }

    suspend fun setWidgetVisible(key: String, visible: Boolean) {
        if (key in HomeWidgets.required && !visible) return
        val widgets = state.homeLayout.widgets
            .sortedBy { it.position }
            .map { if (it.key == key) it.copy(visible = visible) else it }
        saveHomeLayout(widgets)
    }
}

/** The shortest gap between two badge reads the tabs ask for; a forced read ignores it. */
private val BADGE_READ_GAP = 15.seconds

/** The worn key as a medal: its current tier, from the board's own state. */
fun BadgeBoard.wornBadge(): WornBadge? {
    val key = worn ?: return null
    val state = badges.firstOrNull { it.key == key }?.takeIf { it.tier > 0 } ?: return null
    return WornBadge(key, state.tier, state.maxTier)
}
