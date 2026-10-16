package uz.sadora.app

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import uz.sadora.app.data.widget.WidgetPlatform
import uz.sadora.app.nav.AppLink
import uz.sadora.contract.DoseStatus
import uz.sadora.contract.TokenPair
import uz.sadora.widget.AppGroupKeyValues
import uz.sadora.widget.CompanionTokens
import uz.sadora.widget.KeychainKeyValues
import uz.sadora.widget.WidgetApi
import uz.sadora.widget.WidgetConfig
import uz.sadora.widget.WidgetHost
import uz.sadora.widget.WidgetStore
import uz.sadora.widget.WidgetSync
import kotlin.time.Clock

/**
 * WidgetKit and WatchConnectivity, reached through Swift: both are Swift-only APIs, so the
 * app registers a [WidgetKitBridge] at launch, as it does for StoreKit.
 */
interface WidgetKitBridge {
    /** `WidgetCenter.shared.reloadAllTimelines()`. */
    fun reloadWidgets()

    /** A paired watch has the Sadora app installed. */
    fun watchPaired(): Boolean

    /**
     * `WCSession.updateApplicationContext`: the watch's session as JSON (null keeps the
     * one it has), the snapshot as JSON, and the API root. Both JSONs null is sign-out.
     */
    fun sendToWatch(tokensJson: String?, snapshotJson: String?, baseUrl: String)
}

/** What the Swift side hands in and calls back. */
object IosWidgets {
    var bridge: WidgetKitBridge? = null

    /** `group.uz.sadora.app`, shared by the app and its widget extension. */
    const val APP_GROUP = "group.uz.sadora.app"

    private val scope = MainScope()

    /** A silent push said something changed: fetch, redraw, then tell iOS we are done. */
    fun refresh(done: () -> Unit) {
        scope.launch {
            runCatching { graphOrNull()?.widgets?.refresh() }
            done()
        }
    }

    /** "Taken" or "Skipped" pressed on a dose notification; [link] is its `sadora://dose/…`. */
    fun answerDose(link: String, taken: Boolean, done: () -> Unit) {
        val dose = AppLink.parse(link) as? AppLink.Dose
        if (dose == null) {
            done()
            return
        }
        scope.launch {
            runCatching {
                graphOrNull()?.widgets?.answerDose(
                    dose.medicationId,
                    LocalDate.parse(dose.day),
                    LocalTime.parse(dose.time),
                    if (taken) DoseStatus.TAKEN else DoseStatus.SKIPPED,
                )
            }
            done()
        }
    }

    /** The app's graph, built here when the app was launched in the background. */
    private fun graphOrNull(): uz.sadora.app.data.SadoraGraph? = iosGraph
}

/**
 * The app's side on iOS. The widget extension is another process: the app syncs with its
 * own session and writes the snapshot into the App Group; the extension reads it, and
 * uses the companion session in the shared Keychain group for its own refreshes and taps.
 */
class IosWidgetPlatform(keychainGroup: String?) : WidgetPlatform {
    override val store = WidgetStore(AppGroupKeyValues(IosWidgets.APP_GROUP))
    override val widgetTokens = CompanionTokens(KeychainKeyValues(WidgetHost.SERVICE, keychainGroup))

    override val hasWatch: Boolean get() = IosWidgets.bridge?.watchPaired() == true

    override fun sync(appApi: WidgetApi) =
        WidgetSync(appApi, store, { Clock.System.now() }, { platform.Foundation.NSUUID().UUIDString() })

    override fun reload() {
        IosWidgets.bridge?.reloadWidgets()
    }

    override fun sendToWatch(tokens: TokenPair?, snapshotJson: String?) {
        val tokensJson = tokens?.let { WidgetStore.json.encodeToString(TokenPair.serializer(), it) }
        IosWidgets.bridge?.sendToWatch(tokensJson, snapshotJson, store.baseUrl().orEmpty())
    }

    /** iOS has no in-app "place this widget"; the page shows the steps instead. */
    override fun requestPin(config: WidgetConfig): Boolean = false
}
