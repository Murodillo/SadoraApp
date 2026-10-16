package uz.sadora.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import uz.sadora.app.MainActivity
import uz.sadora.app.data.widget.WidgetsController
import uz.sadora.app.ui.settings.WidgetConfigureRoot
import uz.sadora.widget.WidgetHost

/**
 * Opened by the launcher when she places the widget, and again from "Edit widget". The
 * widget is placed either way (configuration is optional): leaving this screen keeps
 * whatever she chose so far.
 */
class WidgetConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // The launcher reads this result; OK from the start, since every choice is saved as made.
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        val host = WidgetHost.get(this)
        val controller = WidgetsController.forWidget(
            store = host.store,
            widgetId = appWidgetId.toString(),
            fallback = host.store.defaultConfig(),
            onSaved = { lifecycleScope.launch { SadoraWidget.redrawAll(this@WidgetConfigureActivity) } },
        )
        // Saved straight away, so a widget placed and left unconfigured keeps the default it showed.
        controller.update(controller.config)
        setContent {
            WidgetConfigureRoot(
                store = host.store,
                widgets = controller,
                onDone = { finish() },
                onPaywall = {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("sadora://open/today")).setClass(this, MainActivity::class.java))
                    finish()
                },
            )
        }
    }
}
