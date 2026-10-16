package uz.sadora.wear

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import uz.sadora.widget.WidgetBlock
import uz.sadora.widget.WidgetConfig
import uz.sadora.widget.WidgetSize

/** The watch shows every block; Premium and discreet mode are applied by widgetcore. */
internal val WatchConfig = WidgetConfig(
    size = WidgetSize.LARGE,
    blocks = listOf(WidgetBlock.WATER, WidgetBlock.DOSES, WidgetBlock.HEALTH),
)

/** A face and a Tile are in public view: discreet unless the account is not. */
internal val GlanceConfig = WidgetConfig(size = WidgetSize.SMALL, blocks = listOf(WidgetBlock.WATER, WidgetBlock.DOSES), lockScreen = true)

/** The Tile and the complications, asked to read the snapshot again. */
internal fun redrawSurfaces(context: Context) {
    TileService.getUpdater(context).requestUpdate(SadoraTileService::class.java)
    ComplicationDataSourceUpdateRequester
        .create(context, ComponentName(context, StageComplicationService::class.java))
        .requestUpdateAll()
}
