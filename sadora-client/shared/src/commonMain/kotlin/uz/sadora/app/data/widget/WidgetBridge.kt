package uz.sadora.app.data.widget

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.SadoraApi
import uz.sadora.app.data.SessionStore
import uz.sadora.contract.CompanionKind
import uz.sadora.widget.WidgetApi
import uz.sadora.widget.WidgetSnapshot
import uz.sadora.widget.WidgetStore
import uz.sadora.widget.WidgetSync

/**
 * The app's side of the widgets and the watch.
 *
 * On sign-in it hands them sessions of their own and draws them; after anything they show
 * changes in the app it redraws them; at sign-out it empties them. The app is where the
 * snapshot is freshest, so a widget is current the moment the app is.
 */
class WidgetBridge(
    private val platform: WidgetPlatform,
    appApi: WidgetApi,
    private val api: SadoraApi,
    private val session: SessionStore,
    private val baseUrl: String,
    /** Outlives any screen: a redraw asked for as she leaves the app must still happen. */
    private val scope: CoroutineScope,
) {
    private val sync: WidgetSync = platform.sync(appApi)
    private val handover = Mutex()
    private var watchHandedOver = false
    private var pending: Job? = null

    /** Signed in, or the app came back to the foreground. */
    suspend fun signedIn() {
        handover.withLock {
            platform.store.setBaseUrl(baseUrl)
            if (platform.widgetTokens.refresh() == null) {
                // A failure leaves the widgets without a session of their own; the app still
                // draws them from its own, and the next foreground tries again.
                (api.companionSession(CompanionKind.WIDGET) as? ApiResult.Success)
                    ?.value?.let { platform.widgetTokens.save(it.accessToken, it.refreshToken) }
            }
            if (platform.hasWatch && !watchHandedOver) {
                (api.companionSession(CompanionKind.WATCH) as? ApiResult.Success)?.value?.let {
                    platform.sendToWatch(it, snapshotJson())
                    watchHandedOver = true
                }
            }
        }
        refresh()
    }

    /** Fetches the summary and redraws; the watch gets the new snapshot too. */
    suspend fun refresh() {
        if (session.currentRefreshToken() == null) return
        if (sync.refresh() == WidgetSync.Outcome.Updated) {
            platform.reload()
            if (watchHandedOver) platform.sendToWatch(null, snapshotJson())
        }
    }

    /**
     * Something a widget shows changed in the app — water, a dose, a period, the stage.
     * Coalesced: a run of taps is one fetch a moment after the last.
     */
    fun changed() {
        pending?.cancel()
        pending = scope.launch {
            delay(DEBOUNCE_MS)
            refresh()
        }
    }

    /** Nothing of hers stays on the home screen or the watch. */
    suspend fun signedOut() {
        pending?.cancel()
        handover.withLock {
            platform.store.clear()
            platform.widgetTokens.clear()
            platform.sendToWatch(null, null)
            watchHandedOver = false
        }
        platform.reload()
    }

    private fun snapshotJson(): String? =
        platform.store.snapshot()?.let { WidgetStore.json.encodeToString(WidgetSnapshot.serializer(), it) }

    companion object {
        private const val DEBOUNCE_MS = 1_500L

        /** The writes that change something a widget draws; the server's list is the same. */
        fun affects(path: String): Boolean {
            val p = path.trimStart('/')
            return p == "v1/me" || WATCHED.any { p.startsWith(it) }
        }

        private val WATCHED = listOf("v1/nutrition/water", "v1/meds", "v1/cycle", "v1/prescriptions")
    }
}
