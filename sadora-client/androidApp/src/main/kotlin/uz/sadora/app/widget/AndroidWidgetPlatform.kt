package uz.sadora.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uz.sadora.app.data.widget.WidgetPlatform
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import uz.sadora.contract.TokenPair
import uz.sadora.widget.WidgetApi
import uz.sadora.widget.WidgetStore
import uz.sadora.widget.WidgetConfig
import uz.sadora.widget.WidgetHost
import uz.sadora.widget.WidgetSync
import java.util.UUID
import kotlin.time.Clock

/**
 * The widgets live in the app's own process here, so the app and the widgets share one
 * store — and with it one lock. The app syncs with its own session; a tap on the widget,
 * which may come with the app closed, uses the widgets' companion session.
 */
class AndroidWidgetPlatform(private val context: Context) : WidgetPlatform {
    private val host = WidgetHost.get(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val store = host.store
    override val widgetTokens = host.tokens

    /** Set once the Data Layer answers; a watch paired later is found on the next start. */
    @Volatile private var watchFound = false

    init {
        scope.launch {
            watchFound = runCatching {
                Wearable.getCapabilityClient(context)
                    .getCapability(WATCH_CAPABILITY, CapabilityClient.FILTER_ALL)
                    .await().nodes.isNotEmpty()
            }.getOrDefault(false)
        }
    }

    override val hasWatch: Boolean get() = watchFound

    /**
     * Two data items, each replaced by the next of its kind: the session on its own path,
     * so a later snapshot cannot replace the one item that carried it before the watch
     * synced; the snapshot on another, of which only the latest matters. Urgent, so a dose
     * ticked on the phone shows on the wrist without waiting for a batch.
     */
    override fun sendToWatch(tokens: TokenPair?, snapshotJson: String?) {
        scope.launch {
            runCatching {
                val data = Wearable.getDataClient(context)
                val signOut = tokens == null && snapshotJson == null
                if (tokens != null || signOut) {
                    data.putDataItem(
                        PutDataMapRequest.create(SESSION_PATH).apply {
                            if (signOut) dataMap.putBoolean("signOut", true)
                            tokens?.let { dataMap.putString("tokens", WidgetStore.json.encodeToString(TokenPair.serializer(), it)) }
                            dataMap.putString("baseUrl", store.baseUrl().orEmpty())
                            // Without a change the Data Layer drops the item as a duplicate.
                            dataMap.putLong("sentAt", System.currentTimeMillis())
                        }.asPutDataRequest().setUrgent(),
                    ).await()
                }
                if (snapshotJson != null) {
                    data.putDataItem(
                        PutDataMapRequest.create(STATE_PATH).apply {
                            dataMap.putString("snapshot", snapshotJson)
                            dataMap.putString("baseUrl", store.baseUrl().orEmpty())
                            dataMap.putLong("sentAt", System.currentTimeMillis())
                        }.asPutDataRequest().setUrgent(),
                    ).await()
                }
            }
        }
    }

    override fun sync(appApi: WidgetApi) =
        WidgetSync(appApi, host.store, { Clock.System.now() }, { UUID.randomUUID().toString() })

    override fun reload() {
        scope.launch { runCatching { SadoraWidget.redrawAll(context) } }
    }

    override val canPin: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    override fun requestPin(config: WidgetConfig): Boolean {
        if (!canPin) return false
        // The new widget takes the page's choice; there is no id to key it by until placed.
        store.setDefaultConfig(config)
        return AppWidgetManager.getInstance(context)
            .requestPinAppWidget(ComponentName(context, SadoraWidgetReceiver::class.java), null, null)
    }

    private companion object {
        /** Kept equal to the Wear OS app's `PhoneListenerService` and its wear.xml. */
        const val SESSION_PATH = "/sadora/session"
        const val STATE_PATH = "/sadora/state"
        const val WATCH_CAPABILITY = "sadora_watch"
    }
}
