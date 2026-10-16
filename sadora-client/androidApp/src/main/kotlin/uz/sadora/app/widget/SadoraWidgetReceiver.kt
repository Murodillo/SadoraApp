package uz.sadora.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import uz.sadora.widget.WidgetHost

class SadoraWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SadoraWidget()

    /** A removed widget's settings go with it. */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = WidgetHost.get(context).store
        appWidgetIds.forEach { store.removeConfig(it.toString()) }
    }
}
