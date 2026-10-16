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
import uz.sadora.widget.WidgetApi
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
        store.putConfig(SadoraWidget.DEFAULT_CONFIG, config)
        return AppWidgetManager.getInstance(context)
            .requestPinAppWidget(ComponentName(context, SadoraWidgetReceiver::class.java), null, null)
    }
}
