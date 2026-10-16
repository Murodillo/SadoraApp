package uz.sadora.app.data.widget

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.SadoraApi
import uz.sadora.app.data.SessionStore
import uz.sadora.contract.CompanionKind
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.widget.WidgetApi
import uz.sadora.widget.WidgetConfig
import uz.sadora.widget.WidgetContent
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

    /** Writes made anywhere in the app; collected with a debounce, so any thread may emit. */
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Set while this bridge sends its own queued taps, whose writes must not ask for another round. */
    private var draining = false

    init {
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        scope.launch { changes.debounce(DEBOUNCE_MS).collect { refresh() } }
    }

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
        draining = true
        val outcome = try {
            sync.refresh()
        } finally {
            draining = false
        }
        if (outcome == WidgetSync.Outcome.Updated) {
            platform.reload()
            if (watchHandedOver) platform.sendToWatch(null, snapshotJson())
        }
    }

    /**
     * Something a widget shows changed in the app — water, a dose, a period, the stage.
     * Coalesced: a run of taps is one fetch a moment after the last.
     */
    fun changed() {
        if (!draining) changes.tryEmit(Unit)
    }

    /** A dose answered on its notification (phone or watch): queued, sent, redrawn. */
    suspend fun answerDose(medicationId: String, day: kotlinx.datetime.LocalDate, time: kotlinx.datetime.LocalTime, status: uz.sadora.contract.DoseStatus) {
        sync.markDose(medicationId, day, time, status)
        refresh()
    }

    /** Nothing of hers stays on the home screen or the watch. */
    suspend fun signedOut() {
        handover.withLock {
            platform.store.clear()
            platform.widgetTokens.clear()
            platform.sendToWatch(null, null)
            watchHandedOver = false
        }
        platform.reload()
    }

    // ---------------------------------------------------------------- the settings page

    val canPin: Boolean get() = platform.canPin
    val watchPaired: Boolean get() = platform.hasWatch

    /** What a widget placed now starts as: the page's choice, or water and doses. */
    fun defaultConfig(): WidgetConfig = platform.store.defaultConfig()

    fun saveDefaultConfig(config: WidgetConfig) {
        platform.store.setDefaultConfig(config)
        platform.reload()
    }

    fun pin(config: WidgetConfig): Boolean = platform.requestPin(config)

    /** The page's preview, drawn from the same snapshot the widgets draw from. */
    fun preview(config: WidgetConfig): WidgetContent? {
        val snapshot = platform.store.snapshot() ?: return null
        val now = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return WidgetContent.build(snapshot, config, now)
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
