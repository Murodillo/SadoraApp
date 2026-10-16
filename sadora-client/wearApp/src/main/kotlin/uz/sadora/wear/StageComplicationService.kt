package uz.sadora.wear

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import uz.sadora.widget.WidgetHost

/** The cycle day (or week) on the watch face, discreet like the lock screen. */
class StageComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(type, "14", 0.5f)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val stage = WidgetHost.get(this).content(GlanceConfig)?.stage
        return data(request.complicationType, stage?.big ?: "—", stage?.progress ?: 0f)
    }

    private fun data(type: ComplicationType, big: String, progress: Float): ComplicationData? {
        val text = PlainComplicationText.Builder(big).build()
        val label = PlainComplicationText.Builder("Sadora").build()
        val open = PendingIntent.getActivity(this, 0, Intent(this, WatchActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return when (type) {
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(progress, 0f, 1f, label)
                .setText(text).setTapAction(open).build()
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(text, label)
                .setTapAction(open).build()
            else -> null
        }
    }
}
