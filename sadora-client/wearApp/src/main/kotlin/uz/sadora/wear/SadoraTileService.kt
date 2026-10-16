package uz.sadora.wear

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.CircularProgressIndicator
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.EdgeContentLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.concurrent.futures.ResolvableFuture
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import uz.sadora.widget.WidgetHost

/**
 * The Tile: the cycle day (or week) in a ring, and the two buttons she uses most. A tap
 * comes back here as the clickable's id, so water and doses are recorded without opening
 * the app.
 */
class SadoraTileService : TileService() {

    override fun onTileRequest(request: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val host = WidgetHost.get(this)
        val clicked = request.currentState.lastClickableId
        // A tap: recorded in the queue at once, sent within the Tile's few seconds.
        runBlocking {
            withTimeoutOrNull(TAP_BUDGET_MS) {
                when {
                    clicked == WATER -> host.content(GlanceConfig)?.water?.let { host.tapWater(it.stepMl) }
                    clicked.startsWith(DOSE) -> host.content(GlanceConfig)?.doses?.next?.let {
                        host.tapDose(it.medicationId, it.dueOn.toString(), it.dueAt.toString().take(5), taken = true)
                    }
                    else -> Unit
                }
            }
        }
        val content = host.content(GlanceConfig)
        val stage = content?.stage
        val ring = CircularProgressIndicator.Builder()
            .setProgress(stage?.progress ?: 0f)
            .setCircularProgressIndicatorColors(
                androidx.wear.protolayout.material.ProgressIndicatorColors(argb(0xFFFF63C8.toInt()), argb(0xFF333333.toInt())),
            )
            .build()
        val center = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(Text.Builder(this, stage?.big ?: "—").setTypography(Typography.TYPOGRAPHY_DISPLAY2).setColor(argb(0xFFFFFFFF.toInt())).build())
            .addContent(Text.Builder(this, stage?.line2 ?: stage?.title ?: "Sadora").setTypography(Typography.TYPOGRAPHY_CAPTION2).setColor(argb(0xFFB2A6FF.toInt())).setMaxLines(1).build())
            .apply {
                content?.water?.let { water ->
                    addContent(chip(water.stepLabel, WATER, 0xFF4FC3FF.toInt()))
                }
                content?.doses?.next?.let { addContent(chip("✓ ${it.time}", DOSE + it.medicationId, 0xFF7B61FF.toInt())) }
            }
            .build()
        val layout = EdgeContentLayout.Builder(request.deviceConfiguration)
            .setEdgeContent(ring)
            .setPrimaryLabelTextContent(Text.Builder(this, "Sadora").setTypography(Typography.TYPOGRAPHY_CAPTION1).setColor(argb(0xFFB2A6FF.toInt())).build())
            .setContent(center)
            .build()
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES)
            .setFreshnessIntervalMillis(FRESHNESS_MS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
        return done(tile)
    }

    private fun chip(label: String, id: String, color: Int) =
        CompactChip.Builder(
            this,
            label,
            ModifiersBuilders.Clickable.Builder().setId(id).setOnClick(ActionBuilders.LoadAction.Builder().build()).build(),
            androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters.Builder().build(),
        )
            .setChipColors(androidx.wear.protolayout.material.ChipColors(argb(color), argb(0xFFFFFFFF.toInt())))
            .build()

    @Deprecated("Superseded in newer Tiles; kept for Wear OS 3")
    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        done(ResourceBuilders.Resources.Builder().setVersion(RESOURCES).build())

    private fun <T> done(value: T): ListenableFuture<T> = ResolvableFuture.create<T>().apply { set(value) }

    private companion object {
        const val RESOURCES = "1"
        const val WATER = "water"
        const val DOSE = "dose:"
        const val TAP_BUDGET_MS = 6_000L
        const val FRESHNESS_MS = 30 * 60 * 1000L
    }
}
