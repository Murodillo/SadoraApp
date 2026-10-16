package uz.sadora.app.data.widget

import uz.sadora.contract.TokenPair
import uz.sadora.widget.CompanionTokens
import uz.sadora.widget.MemoryKeyValues
import uz.sadora.widget.WidgetApi
import uz.sadora.widget.WidgetStore
import uz.sadora.widget.WidgetSync

/**
 * The home-screen widgets and the watch, as each platform has them. Built by the
 * platform's entry point, like the token storage; [None] for tests and previews.
 */
interface WidgetPlatform {
    /** Where the snapshot lives that every widget draws from. */
    val store: WidgetStore

    /** The widgets' own session (see `POST /v1/auth/companion`). */
    val widgetTokens: CompanionTokens

    /** A watch is paired and has the app: worth a session of its own. */
    val hasWatch: Boolean get() = false

    /**
     * The sync the app drives. Android's widgets live in the app's process and share one
     * with it; iOS's widget extension is another process, so the app syncs with its own
     * session through [appApi] and leaves the widgets' tokens to the extension.
     */
    fun sync(appApi: WidgetApi): WidgetSync

    /** Asks the OS to redraw every widget now. */
    fun reload()

    /** The launcher can place a widget from inside the app (Android 8+); iOS cannot. */
    val canPin: Boolean get() = false

    /** Asks the launcher to place a widget set up as [config]; false when it would not. */
    fun requestPin(config: uz.sadora.widget.WidgetConfig): Boolean = false

    /**
     * Hands the watch its session (when [tokens] is set) and the latest snapshot. Both
     * null means sign out. Over WatchConnectivity on iOS and the Wearable Data Layer on
     * Android.
     */
    fun sendToWatch(tokens: TokenPair?, snapshotJson: String?) {}

    object None : WidgetPlatform {
        override val store = WidgetStore(MemoryKeyValues())
        override val widgetTokens = CompanionTokens(MemoryKeyValues())
        override fun sync(appApi: WidgetApi) = WidgetSync(appApi, store, { kotlin.time.Clock.System.now() }, { "none" })
        override fun reload() {}
    }
}
