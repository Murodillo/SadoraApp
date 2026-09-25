package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uz.sadora.app.data.api.RewardsApi
import uz.sadora.app.model.AppState
import uz.sadora.contract.AppIconMood
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
