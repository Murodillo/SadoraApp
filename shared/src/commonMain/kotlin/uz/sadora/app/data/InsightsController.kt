package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uz.sadora.app.data.api.InsightsApi
import uz.sadora.contract.InsightsSummary

/**
 * The trend windows, kept per length.
 *
 * Switching the range chip on Tahlillar must not blank the chart it just drew, and the
 * seven-day window is shared with Mind and Uyqu — so each window is cached under its own
 * length and a screen reads whichever one it asked for.
 *
 * A null API means no backend: every window stays absent and the screens say they have
 * nothing to show, which is the honest answer for a build with no server behind it.
 */
class InsightsController(private val api: InsightsApi?) {
    private val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val isOffline: Boolean get() = api == null

    /**
     * Held here rather than read from [ApiCallState] because one failure is not an error:
     * a window the subscription does not cover is the paywall, and the screen draws that
     * as a lock instead of a banner.
     */
    var error by mutableStateOf<ApiFailure?>(null)
        private set

    fun clearError() {
        error = null
    }

    private val windows = mutableStateMapOf<Int, InsightsSummary>()

    /** The window the server refused, so the screen can show the lock on that range. */
    var lockedWindow by mutableStateOf<Int?>(null)
        private set

    fun summary(days: Int): InsightsSummary? = windows[days]

    /**
     * Loads a window unless it is already held.
     *
     * [force] is for a deliberate refresh; moving between the range chips should not
     * re-ask for a window that can already be drawn.
     */
    suspend fun load(days: Int, force: Boolean = false) {
        val api = api ?: return
        if (!force && windows.containsKey(days)) return

        var refusal: ApiFailure? = null
        val summary = calls.run(silent = true) {
            api.summary(days).onFailure { refusal = it }
        }

        when {
            summary != null -> {
                windows[days] = summary
                if (lockedWindow == days) lockedWindow = null
                error = null
            }
            // Only a Premium refusal is a lock. A limit reached by a Premium account used
            // to be shown as "this window is Premium" and lead to a paywall she had paid.
            refusal is ApiFailure.PremiumRequired -> lockedWindow = days
            refusal != null -> error = refusal
        }
    }
}
