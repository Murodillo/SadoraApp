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
import uz.sadora.contract.CyclePhase
import uz.sadora.contract.DoseStatus
import uz.sadora.widget.DoseLine
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
            val config = host.store.config(widgetId) ?: defaultConfig(host)
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

        /** A widget she has not set up starts as the settings page's choice. */
        fun defaultConfig(host: WidgetHost): WidgetConfig =
            host.store.config(DEFAULT_CONFIG) ?: WidgetConfig(WidgetSize.MEDIUM, listOf(uz.sadora.widget.WidgetBlock.WATER, uz.sadora.widget.WidgetBlock.DOSES))

        const val DEFAULT_CONFIG = "default"

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

private val Ink = ColorProvider(Color(0xFF1A1630))
private val Muted = ColorProvider(Color(0xFF66617F))
private val White = ColorProvider(Color.White)
private val WarmInk = ColorProvider(Color(0xFF2A1145))
private val Surface = ColorProvider(Color(0xFFFFFFFF))
private val WaterTint = Color(0xFFE8F6FF)
private val WaterAccent = Color(0xFF1C88C8)
private val DoseTint = Color(0xFFF1EDFF)
private val DoseAccent = Color(0xFF6247E0)
private val HealthTint = Color(0xFFFFE9F4)

@Composable
private fun WidgetBody(content: WidgetContent?, size: WidgetSize, config: WidgetConfig, signIn: String) {
    val root = GlanceModifier.fillMaxSize().background(Surface).cornerRadius(26.dp).padding(8.dp)
    if (content == null) {
        Box(root.clickable(openApp("today")), contentAlignment = Alignment.Center) {
            Text(signIn, style = TextStyle(color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
        return
    }
    val warm = content.stage.unit != null && content.stage.phase == null && !content.discreet
    when (size) {
        WidgetSize.SMALL -> Box(GlanceModifier.fillMaxSize().cornerRadius(26.dp)) { StageTile(content, warm, big = 52, GlanceModifier.fillMaxSize()) }
        WidgetSize.MEDIUM -> Row(root) {
            StageTile(content, warm, big = 40, GlanceModifier.defaultWeight().fillMaxHeight())
            Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.defaultWeight().fillMaxHeight()) { SideBlocks(content, config, compact = true) }
        }
        WidgetSize.LARGE -> Column(root) {
            StageTile(content, warm, big = 40, GlanceModifier.fillMaxWidth().height(92.dp))
            Spacer(GlanceModifier.height(8.dp))
            SideBlocks(content, config, compact = false)
        }
    }
}

@Composable
private fun StageTile(content: WidgetContent, warm: Boolean, big: Int, modifier: GlanceModifier) {
    val stage = content.stage
    val bg = when {
        warm -> R.drawable.widget_stage_warm_bg
        stage.phase == CyclePhase.PERIOD -> R.drawable.widget_stage_period_bg
        else -> R.drawable.widget_stage_bg
    }
    val ink = if (warm) WarmInk else White
    Column(
        modifier
            .background(ImageProvider(bg))
            .cornerRadius(22.dp)
            .padding(12.dp)
            .clickable(openApp("journey"))
            .semantics { contentDescription = listOfNotNull(stage.title, stage.big, stage.unit, stage.line1, stage.line2).joinToString(", ") },
    ) {
        Text(stage.title, style = TextStyle(color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
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

@Composable
private fun ColumnScope.SideBlocks(content: WidgetContent, config: WidgetConfig, compact: Boolean) {
    if (content.locked) {
        Tile(Color(0xFFF7F5FF), GlanceModifier.defaultWeight(), openApp("today")) {
            Text("🔒 ${content.lockText}", style = TextStyle(color = ColorProvider(DoseAccent), fontSize = 12.sp, fontWeight = FontWeight.Bold))
        }
        return
    }
    content.water?.let { water ->
        Tile(WaterTint, GlanceModifier.defaultWeight(), openApp("water")) {
            Column(GlanceModifier.defaultWeight()) {
                Text(water.title, style = TextStyle(color = ColorProvider(WaterAccent), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                Text(water.label, style = TextStyle(color = Ink, fontSize = if (compact) 15.sp else 17.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            ActionPill(
                if (compact) "+${water.stepMl}" else water.stepLabel,
                WaterAccent,
                actionRunCallback<AddWaterAction>(actionParametersOf(AddWaterAction.Ml to water.stepMl)),
            )
        }
        Spacer(GlanceModifier.height(6.dp))
    }
    content.doses?.let { doses ->
        if (compact || doses.rows.isEmpty()) {
            val next = doses.next
            Tile(DoseTint, GlanceModifier.defaultWeight(), openApp("medications")) {
                Column(GlanceModifier.defaultWeight()) {
                    Text(next?.let { "${it.name} · ${it.time}" } ?: doses.title, style = TextStyle(color = ColorProvider(DoseAccent), fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(doses.label, style = TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                }
                next?.let { ActionPill("✓", DoseAccent, markDose(it)) }
            }
        } else {
            Tile(DoseTint, GlanceModifier.fillMaxWidth(), openApp("medications")) {
                Column(GlanceModifier.defaultWeight()) {
                    Text("${doses.title} · ${doses.label}", style = TextStyle(color = ColorProvider(DoseAccent), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                    doses.rows.take(MAX_ROWS).forEach { row -> DoseRow(row) }
                }
            }
        }
        Spacer(GlanceModifier.height(6.dp))
    }
    content.health?.let { health ->
        if (compact && (content.water != null && content.doses != null)) return@let
        Tile(HealthTint, GlanceModifier.defaultWeight(), openApp(if (health.connectPrompt) "devices" else "today")) {
            if (health.connectPrompt) {
                Text(health.connectText, style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            } else {
                health.items.forEach { item ->
                    Column(GlanceModifier.defaultWeight()) {
                        Text(item.label, style = TextStyle(color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        Text(item.value, style = TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                }
            }
        }
    }
    if (content.needsRefresh && content.doses == null && WidgetBlockDoses in config.blocks) {
        Text(content.updatingText, style = TextStyle(color = Muted, fontSize = 11.sp))
    }
}

private val WidgetBlockDoses = uz.sadora.widget.WidgetBlock.DOSES
private const val MAX_ROWS = 3

@Composable
private fun DoseRow(row: DoseLine) {
    Row(GlanceModifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${row.name} · ${row.time}", style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        if (row.status == DoseStatus.TAKEN) {
            Box(GlanceModifier.size(30.dp).background(Color(0xFF2BA57A)).cornerRadius(15.dp), contentAlignment = Alignment.Center) {
                Text("✓", style = TextStyle(color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            }
        } else {
            ActionPill("✓", DoseAccent, markDose(row))
        }
    }
}

@Composable
private fun Tile(tint: Color, modifier: GlanceModifier, onTap: androidx.glance.action.Action, content: @Composable androidx.glance.layout.RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().background(tint).cornerRadius(18.dp).padding(horizontal = 10.dp, vertical = 6.dp).clickable(onTap),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** At least 44dp: a widget's button is pressed with a thumb, mid-stride. */
@Composable
private fun ActionPill(label: String, color: Color, action: androidx.glance.action.Action) {
    Box(
        GlanceModifier.size(width = 52.dp, height = 44.dp).background(color).cornerRadius(22.dp).clickable(action),
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
