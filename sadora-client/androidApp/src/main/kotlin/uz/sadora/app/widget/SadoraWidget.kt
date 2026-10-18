package uz.sadora.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.RowScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.withTimeoutOrNull
import uz.sadora.app.MainActivity
import uz.sadora.app.R
import uz.sadora.contract.DoseStatus
import uz.sadora.widget.DoseLine
import uz.sadora.widget.HealthItem
import uz.sadora.widget.StageLook
import uz.sadora.widget.WidgetConfig
import uz.sadora.widget.WidgetContent
import uz.sadora.widget.WidgetHost
import uz.sadora.widget.WidgetSize
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * The Android home-screen widget. One widget that the launcher may resize: Glance picks the
 * small, medium or large layout from the cells it is given, and every word comes from
 * widgetcore's [WidgetContent], so it says what the iOS widget and the app say.
 */
class SadoraWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val host = WidgetHost.get(context)
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id).toString()
        // A snapshot older than half an hour is refreshed before the first frame, within
        // the few seconds a widget update is given.
        val saved = host.store.snapshot()?.savedAt
        if (saved == null || Clock.System.now() - saved > STALE) {
            withTimeoutOrNull(REFRESH_BUDGET) { host.sync.refresh() }
        }
        provideContent {
            // Read so that a bump in [redrawAll] recomposes: Glance redraws on state, and
            // the snapshot lives outside it.
            @Suppress("UNUSED_VARIABLE")
            val version = currentState<Preferences>()[VERSION]
            val config = host.store.config(widgetId) ?: host.store.defaultConfig()
            val content = host.content(config)
            WidgetBody(content, sizeOf(LocalSize.current), config, host.signInText())
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 260.dp)
        private val STALE = 30.minutes
        private const val REFRESH_BUDGET = 10_000L

        private val VERSION = longPreferencesKey("snapshot_version")

        /** Every placed widget, redrawn from the snapshot as it is now. */
        suspend fun redrawAll(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(SadoraWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { it[VERSION] = System.nanoTime() }
                SadoraWidget().update(context, id)
            }
        }

        private fun sizeOf(size: DpSize): WidgetSize = when {
            size.height >= LARGE.height -> WidgetSize.LARGE
            size.width >= MEDIUM.width -> WidgetSize.MEDIUM
            else -> WidgetSize.SMALL
        }
    }
}

// -------------------------------------------------------------------- layout
//
// The clay look of the app: every surface a gradient with a bright band on top and a soft
// shade at the bottom (the widget_* drawables), and the app's clay icons beside each block.

private val Ink = ColorProvider(Color(0xFF1A1630))
private val Muted = ColorProvider(Color(0xFF66617F))
private val White = ColorProvider(Color.White)
private val WarmInk = ColorProvider(Color(0xFF2A1145))
private val WaterInk = ColorProvider(Color(0xFF146A94))
private val DoseInk = ColorProvider(Color(0xFF6247E0))
private val StepsInk = ColorProvider(Color(0xFF7F5100))

@Composable
private fun WidgetBody(content: WidgetContent?, size: WidgetSize, config: WidgetConfig, signIn: String) {
    val card = GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_tile_card)).cornerRadius(28.dp).padding(9.dp)
    if (content == null) {
        Column(card.clickable(openApp("today")), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            ClayIcon(R.drawable.widget_clay_heart, 44)
            Spacer(GlanceModifier.height(6.dp))
            Text(signIn, style = TextStyle(color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        }
        return
    }
    when (size) {
        WidgetSize.SMALL -> StageTile(content, big = 54, GlanceModifier.fillMaxSize())
        WidgetSize.MEDIUM -> Row(card) {
            StageTile(content, big = 42, GlanceModifier.defaultWeight().fillMaxHeight())
            Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.defaultWeight().fillMaxHeight()) { SideBlocks(content, config, compact = true) }
        }
        WidgetSize.LARGE -> Column(card) {
            WideStageTile(content, GlanceModifier.fillMaxWidth().height(86.dp))
            Spacer(GlanceModifier.height(8.dp))
            SideBlocks(content, config, compact = false)
        }
    }
}

private fun StageLook.background(): Int = when (this) {
    StageLook.CYCLE -> R.drawable.widget_stage_cycle
    StageLook.PERIOD -> R.drawable.widget_stage_period
    StageLook.TTC -> R.drawable.widget_stage_ttc
    StageLook.PREGNANCY -> R.drawable.widget_stage_pregnancy
    StageLook.POSTPARTUM -> R.drawable.widget_stage_postpartum
    StageLook.PERIMENOPAUSE -> R.drawable.widget_stage_perimenopause
    StageLook.MENOPAUSE -> R.drawable.widget_stage_menopause
    StageLook.DISCREET -> R.drawable.widget_stage_discreet
}

private fun StageLook.icon(): Int = when (this) {
    StageLook.CYCLE -> R.drawable.widget_clay_cycle
    StageLook.PERIOD -> R.drawable.widget_clay_period
    StageLook.TTC -> R.drawable.widget_clay_ttc
    StageLook.PREGNANCY -> R.drawable.widget_clay_pregnancy
    StageLook.POSTPARTUM -> R.drawable.widget_clay_postpartum
    StageLook.PERIMENOPAUSE -> R.drawable.widget_clay_perimenopause
    StageLook.MENOPAUSE -> R.drawable.widget_clay_menopause
    StageLook.DISCREET -> R.drawable.widget_clay_heart
}

/** Pregnancy and postpartum gradients are too light for white type, as in the app. */
private fun StageLook.ink() = if (this == StageLook.PREGNANCY || this == StageLook.POSTPARTUM) WarmInk else White

@Composable
private fun ClayIcon(res: Int, size: Int) {
    Image(ImageProvider(res), contentDescription = null, modifier = GlanceModifier.size(size.dp), contentScale = ContentScale.Fit)
}

@Composable
private fun StageTile(content: WidgetContent, big: Int, modifier: GlanceModifier) {
    val stage = content.stage
    val ink = stage.look.ink()
    Column(
        modifier
            .background(ImageProvider(stage.look.background()))
            .cornerRadius(24.dp)
            .padding(12.dp)
            .clickable(openApp("journey"))
            .semantics { contentDescription = listOfNotNull(stage.title, stage.big, stage.unit, stage.line1, stage.line2).joinToString(", ") },
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(stage.title, style = TextStyle(color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            ClayIcon(stage.look.icon(), 38)
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stage.big, style = TextStyle(color = ink, fontSize = big.sp, fontWeight = FontWeight.Bold))
            stage.unit?.let { Text(" $it", style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)) }
        }
        Spacer(GlanceModifier.defaultWeight())
        stage.line1?.let { Text(it, style = TextStyle(color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1) }
        stage.line2?.let { Text(it, style = TextStyle(color = ink, fontSize = 11.sp), maxLines = 1) }
    }
}

/** The large widget's banner: icon, the words, and the number on the right. */
@Composable
private fun WideStageTile(content: WidgetContent, modifier: GlanceModifier) {
    val stage = content.stage
    val ink = stage.look.ink()
    Row(
        modifier.background(ImageProvider(stage.look.background())).cornerRadius(24.dp).padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(openApp("journey"))
            .semantics { contentDescription = listOfNotNull(stage.title, stage.big, stage.unit, stage.line1, stage.line2).joinToString(", ") },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClayIcon(stage.look.icon(), 48)
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(stage.title, style = TextStyle(color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            stage.line1?.let { Text(it, style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1) }
            stage.line2?.let { Text(it, style = TextStyle(color = ink, fontSize = 12.sp), maxLines = 1) }
        }
        Text(stage.big, style = TextStyle(color = ink, fontSize = 40.sp, fontWeight = FontWeight.Bold))
        stage.unit?.let { Text(" $it", style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)) }
    }
}

@Composable
private fun ColumnScope.SideBlocks(content: WidgetContent, config: WidgetConfig, compact: Boolean) {
    if (content.locked) {
        Tile(R.drawable.widget_tile_premium, GlanceModifier.defaultWeight(), openApp("today")) {
            ClayIcon(R.drawable.widget_clay_crown, 34)
            Spacer(GlanceModifier.width(8.dp))
            Text(content.lockText, style = TextStyle(color = StepsInk, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 2, modifier = GlanceModifier.defaultWeight())
        }
        return
    }
    content.water?.let { water ->
        // Beside the stage the tiles share the height; under it each keeps its own.
        Tile(R.drawable.widget_tile_water, if (compact) GlanceModifier.defaultWeight() else GlanceModifier, openApp("water")) {
            ClayIcon(R.drawable.widget_clay_water, if (compact) 28 else 32)
            Spacer(GlanceModifier.width(6.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(water.title, style = TextStyle(color = WaterInk, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                Text(water.label, style = TextStyle(color = Ink, fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            ClayButton(
                if (compact) "+${water.stepMl}" else water.stepLabel,
                R.drawable.widget_button_water,
                actionRunCallback<AddWaterAction>(actionParametersOf(AddWaterAction.Ml to water.stepMl)),
                wide = !compact,
            )
        }
        Spacer(GlanceModifier.height(6.dp))
    }
    content.doses?.let { doses ->
        if (compact || doses.rows.isEmpty()) {
            val next = doses.next
            Tile(R.drawable.widget_tile_dose, GlanceModifier.defaultWeight(), openApp("medications")) {
                ClayIcon(R.drawable.widget_clay_meds, 28)
                Spacer(GlanceModifier.width(6.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(next?.let { "${it.name} · ${it.time}" } ?: doses.title, style = TextStyle(color = DoseInk, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(doses.label, style = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                }
                next?.let { ClayButton("✓", R.drawable.widget_button_dose, markDose(it)) }
            }
        } else {
            Tile(R.drawable.widget_tile_dose, GlanceModifier.fillMaxWidth(), openApp("medications")) {
                Column(GlanceModifier.defaultWeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ClayIcon(R.drawable.widget_clay_meds, 24)
                        Spacer(GlanceModifier.width(6.dp))
                        Text("${doses.title} · ${doses.label}", style = TextStyle(color = DoseInk, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    }
                    doses.rows.take(MAX_ROWS).forEach { row -> DoseRow(row) }
                }
            }
        }
        Spacer(GlanceModifier.height(6.dp))
    }
    content.health?.let { health ->
        if (compact && content.water != null && content.doses != null) return@let
        if (health.connectPrompt) {
            Tile(R.drawable.widget_tile_sleep, GlanceModifier.defaultWeight(), openApp("devices")) {
                ClayIcon(R.drawable.widget_clay_watch, 30)
                Spacer(GlanceModifier.width(8.dp))
                Text(health.connectText, style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            }
        } else {
            Row(GlanceModifier.fillMaxWidth()) {
                health.items.forEachIndexed { index, item ->
                    if (index > 0) Spacer(GlanceModifier.width(6.dp))
                    HealthTile(item, GlanceModifier.defaultWeight())
                }
            }
        }
    }
    if (content.needsRefresh && content.doses == null && WidgetBlockDoses in config.blocks) {
        Text(content.updatingText, style = TextStyle(color = Muted, fontSize = 11.sp))
    }
}

@Composable
private fun HealthTile(item: HealthItem, modifier: GlanceModifier) {
    val (tile, icon, ink) = when (item.metric) {
        uz.sadora.contract.HealthMetric.STEPS -> Triple(R.drawable.widget_tile_steps, R.drawable.widget_clay_active, StepsInk)
        uz.sadora.contract.HealthMetric.SLEEP_DURATION -> Triple(R.drawable.widget_tile_sleep, R.drawable.widget_clay_sleep, DoseInk)
        else -> Triple(R.drawable.widget_tile_dose, R.drawable.widget_clay_heart, DoseInk)
    }
    Tile(tile, modifier, openApp("today")) {
        ClayIcon(icon, 28)
        Spacer(GlanceModifier.width(6.dp))
        Column {
            Text(item.label, style = TextStyle(color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(item.value, style = TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }
}

private val WidgetBlockDoses = uz.sadora.widget.WidgetBlock.DOSES
private const val MAX_ROWS = 3

@Composable
private fun DoseRow(row: DoseLine) {
    Row(GlanceModifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${row.name} · ${row.time}", style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        if (row.status == DoseStatus.TAKEN) {
            Box(GlanceModifier.size(32.dp).background(ImageProvider(R.drawable.widget_button_taken)).cornerRadius(16.dp), contentAlignment = Alignment.Center) {
                Text("✓", style = TextStyle(color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            }
        } else {
            ClayButton("✓", R.drawable.widget_button_dose, markDose(row))
        }
    }
}

@Composable
private fun Tile(background: Int, modifier: GlanceModifier, onTap: androidx.glance.action.Action, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().background(ImageProvider(background)).cornerRadius(20.dp).padding(horizontal = 9.dp, vertical = 6.dp).clickable(onTap),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** A raised clay pill, at least 44dp: a widget's button is pressed with a thumb, mid-stride. */
@Composable
private fun ClayButton(label: String, background: Int, action: androidx.glance.action.Action, wide: Boolean = false) {
    Box(
        GlanceModifier.size(width = if (wide) 76.dp else 48.dp, height = 44.dp).background(ImageProvider(background)).cornerRadius(22.dp).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle(color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold))
    }
}

private fun markDose(row: DoseLine) = actionRunCallback<MarkDoseAction>(
    actionParametersOf(
        MarkDoseAction.Medication to row.medicationId,
        MarkDoseAction.Day to row.dueOn.toString(),
        MarkDoseAction.Time to row.dueAt.toString(),
    ),
)

/** An explicit intent: the app's activity, with the screen to open as its data. */
private fun openApp(target: String) = actionStartActivity(
    Intent(Intent.ACTION_VIEW, Uri.parse("sadora://open/$target")).setClassName(uz.sadora.app.BuildConfig.APPLICATION_ID, MainActivity::class.java.name),
)
