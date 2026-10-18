package uz.sadora.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import uz.sadora.app.resources.Res
import uz.sadora.app.resources.ic3d_active
import uz.sadora.app.resources.ic3d_crown
import uz.sadora.app.resources.ic3d_cycle
import uz.sadora.app.resources.ic3d_heart
import uz.sadora.app.resources.ic3d_home
import uz.sadora.app.resources.ic3d_meds
import uz.sadora.app.resources.ic3d_menopause
import uz.sadora.app.resources.ic3d_period
import uz.sadora.app.resources.ic3d_perimenopause
import uz.sadora.app.resources.ic3d_postpartum
import uz.sadora.app.resources.ic3d_pregnancy
import uz.sadora.app.resources.ic3d_sleep
import uz.sadora.app.resources.ic3d_ttc
import uz.sadora.app.resources.ic3d_watch
import uz.sadora.app.resources.ic3d_water
import uz.sadora.widget.StageLook
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.sadora.app.data.widget.WidgetsController
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.design.StagePalettes
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraSwitch
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SegmentedControl
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.contract.CyclePhase
import uz.sadora.contract.LifeStage
import uz.sadora.widget.WidgetBlock
import uz.sadora.widget.WidgetConfig
import uz.sadora.widget.WidgetContent
import uz.sadora.widget.WidgetSize

/**
 * "Widgets and watch": what a newly placed widget starts as, drawn the way it will look,
 * and how to put one on the home screen.
 *
 * The OS's own editor (holding the widget on iOS, the configure screen on Android) is
 * the other door to the same settings; this page exists for the many who do not know
 * widgets are there at all.
 */
@Composable
fun WidgetsScreen(
    state: AppState,
    widgets: WidgetsController,
    onClose: () -> Unit,
    onPaywall: () -> Unit,
    onToast: (String) -> Unit,
) {
    val t = strings.widgets
    val config = widgets.config
    var showSteps by remember { mutableStateOf(false) }
    val premium = state.isPremium

    Column {
        SadoraTopBar(t.title, onBack = onClose)
        ScreenContent {
            item {
                Text(t.subtitle, style = Sadora.type.body, color = Sadora.colors.muted)
            }

            item {
                SadoraCard {
                    CardLabel(t.look)
                    val sizes = WidgetSize.entries
                    SegmentedControl(
                        options = listOf(t.small, t.medium, t.large),
                        selectedIndex = sizes.indexOf(config.size),
                        onSelect = { widgets.update(config.copy(size = sizes[it])) },
                        lockedIndices = if (premium) emptySet() else setOf(1, 2),
                    )
                    val content = widgets.preview()
                    if (content == null) {
                        Text(t.previewEmpty, style = Sadora.type.body, color = Sadora.colors.muted)
                    } else {
                        WidgetPreview(content, config.size)
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel(t.blocks)
                    BlockRow(Res.drawable.ic3d_cycle, t.stageBlock, t.stageBlockNote, checked = true, enabled = false) {}
                    BlockRow(Res.drawable.ic3d_water, t.waterBlock, null, WidgetBlock.WATER in config.blocks, premium) { widgets.toggle(WidgetBlock.WATER) }
                    BlockRow(Res.drawable.ic3d_meds, t.dosesBlock, null, WidgetBlock.DOSES in config.blocks, premium) { widgets.toggle(WidgetBlock.DOSES) }
                    BlockRow(Res.drawable.ic3d_watch, t.healthBlock, null, WidgetBlock.HEALTH in config.blocks, premium) { widgets.toggle(WidgetBlock.HEALTH) }
                    if (premium && WidgetBlock.WATER in config.blocks) {
                        CardLabel(t.waterStep)
                        ChipFlowRow {
                            WidgetConfig.WATER_STEPS.forEach { ml ->
                                SelectChip("+$ml", config.waterStepMl == ml, { widgets.update(config.copy(waterStepMl = ml)) })
                            }
                        }
                    }
                    if (premium && WidgetBlock.HEALTH in config.blocks) {
                        CardLabel(t.metrics)
                        ChipFlowRow {
                            val labels = widgets.preview()?.health?.items?.associate { it.metric to it.label }.orEmpty()
                            WidgetConfig.METRIC_CHOICES.forEach { metric ->
                                val picked = metric in config.metrics
                                SelectChip(labels[metric] ?: metric.name.lowercase().replace('_', ' '), picked, {
                                    val next = if (picked) config.metrics - metric else (config.metrics + metric).take(MAX_METRICS)
                                    if (next.isNotEmpty()) widgets.update(config.copy(metrics = next))
                                })
                            }
                        }
                    }
                }
            }

            if (!premium) {
                item {
                    SadoraCard {
                        Text(t.premiumNote, style = Sadora.type.body, color = Sadora.colors.text)
                        SadoraButton(t.premiumButton, onPaywall, tone = ButtonTone.Secondary)
                    }
                }
            }

            item {
                SadoraButton(t.addToHome, onClick = {
                    if (widgets.canPin) {
                        if (widgets.pin()) onToast(t.pinned)
                    } else {
                        showSteps = !showSteps
                    }
                })
            }

            if (showSteps) {
                item {
                    SadoraCard {
                        CardLabel(t.howToAddTitle)
                        t.howToAddSteps.forEachIndexed { index, step ->
                            Text("${index + 1}. $step", style = Sadora.type.body, color = Sadora.colors.text)
                        }
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel(t.watchTitle, trailing = {
                        Text(
                            if (widgets.watchPaired) t.watchConnected else t.watchNone,
                            style = Sadora.type.caption,
                            color = if (widgets.watchPaired) Sadora.colors.successText else Sadora.colors.muted,
                        )
                    })
                    Text(t.watchNote, style = Sadora.type.body, color = Sadora.colors.muted)
                }
            }
        }
    }
}

@Composable
private fun BlockRow(art: DrawableResource, title: String, note: String?, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val c = Sadora.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ClayIcon(art, 34)
        Column(Modifier.weight(1f)) {
            Text(title, style = Sadora.type.h3, color = if (enabled || checked) c.text else c.muted)
            note?.let { Text(it, style = Sadora.type.body, color = c.muted) }
        }
        SadoraSwitch(checked, { if (enabled) onToggle() })
    }
}

private const val MAX_METRICS = 2

// -------------------------------------------------------------------- the preview
//
// The clay look the widgets have on the home screen: gradients with a bright band on top
// and a soft shade below, and the app's clay icons beside each block.

private fun StageLook.colors(): List<Color> = when (this) {
    StageLook.PERIOD -> listOf(Color(0xFFD42A78), Color(0xFFE8478F), Color(0xFFFF6FB8))
    StageLook.TTC -> listOf(Color(0xFF7B61FF), Color(0xFFB565DE), Color(0xFFFF6FB8))
    StageLook.PREGNANCY -> listOf(Color(0xFFFFC54D), Color(0xFFFFA36B), Color(0xFFFF8E92))
    StageLook.POSTPARTUM -> listOf(Color(0xFFFF8AA3), Color(0xFFFF9E72), Color(0xFFFFB020))
    StageLook.PERIMENOPAUSE -> listOf(Color(0xFF7B61FF), Color(0xFF6F9CF0), Color(0xFF63D8FF))
    StageLook.MENOPAUSE -> listOf(Color(0xFF4FB8FF), Color(0xFF40BCC6), Color(0xFF2FBF8F))
    StageLook.DISCREET -> listOf(Color(0xFF6A4FF0), Color(0xFF7C66F8), Color(0xFF8E7BFF))
    StageLook.CYCLE -> listOf(Color(0xFF7B61FF), Color(0xFFA14FD0), Color(0xFFE0559B))
}

private fun StageLook.icon(): DrawableResource = when (this) {
    StageLook.CYCLE -> Res.drawable.ic3d_cycle
    StageLook.PERIOD -> Res.drawable.ic3d_period
    StageLook.TTC -> Res.drawable.ic3d_ttc
    StageLook.PREGNANCY -> Res.drawable.ic3d_pregnancy
    StageLook.POSTPARTUM -> Res.drawable.ic3d_postpartum
    StageLook.PERIMENOPAUSE -> Res.drawable.ic3d_perimenopause
    StageLook.MENOPAUSE -> Res.drawable.ic3d_menopause
    StageLook.DISCREET -> Res.drawable.ic3d_heart
}

private fun StageLook.ink(): Color =
    if (this == StageLook.PREGNANCY || this == StageLook.POSTPARTUM) StagePalettes.warmInk else Color.White

private val WaterTile = listOf(Color(0xFFEAF8FF), Color(0xFFC9EDFF))
private val DoseTile = listOf(Color(0xFFF3EFFF), Color(0xFFDCD3FF))
private val StepsTile = listOf(Color(0xFFFFF6E4), Color(0xFFFFDDB0))
private val SleepTile = listOf(Color(0xFFF1EDFF), Color(0xFFD6CCFF))
private val PremiumTile = listOf(Color(0xFFFFF8E6), Color(0xFFFFE7B8))
private val WaterButton = listOf(Color(0xFF6FD3FF), Color(0xFF1C88C8))
private val DoseButton = listOf(Color(0xFFA592FF), Color(0xFF6247E0))
private val TakenButton = listOf(Color(0xFF4FD8A0), Color(0xFF2BA57A))

/** The clay finish over a gradient: light from above, a soft shade below. */
private fun Modifier.clay(colors: List<Color>, radius: Int) = this
    .clip(RoundedCornerShape(radius.dp))
    .background(Brush.linearGradient(colors))
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent, Color.Black.copy(alpha = 0.06f))))

@Composable
private fun ClayIcon(art: DrawableResource, size: Int, modifier: Modifier = Modifier) {
    Image(painterResource(art), contentDescription = null, modifier = modifier.size(size.dp))
}

/**
 * The widget as the home screen will draw it — the same [WidgetContent] the Glance and
 * SwiftUI renderers lay out, so the preview cannot promise what the widget will not show.
 */
@Composable
fun WidgetPreview(content: WidgetContent, size: WidgetSize, modifier: Modifier = Modifier) {
    val height = if (size == WidgetSize.LARGE) 330.dp else 176.dp
    Box(
        modifier
            .fillMaxWidth(if (size == WidgetSize.SMALL) 0.5f else 1f)
            .height(height)
            .clay(listOf(Color.White, Color(0xFFF6F3FF)), 28),
    ) {
        when (size) {
            WidgetSize.SMALL -> StageTile(content, Modifier.fillMaxWidth().fillMaxHeight(), big = 54, radius = 28)
            WidgetSize.MEDIUM -> Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StageTile(content, Modifier.weight(1f).fillMaxHeight(), big = 42)
                SideColumn(content)
            }
            WidgetSize.LARGE -> Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WideStageTile(content, Modifier.fillMaxWidth().height(86.dp))
                content.water?.let { Tile(Res.drawable.ic3d_water, it.title, it.label, it.stepLabel, WaterTile, WaterButton, Color(0xFF146A94)) }
                content.doses?.let { doses ->
                    doses.rows.take(3).forEach { row ->
                        val taken = row.status == uz.sadora.contract.DoseStatus.TAKEN
                        Tile(Res.drawable.ic3d_meds, row.name, row.time, "✓", DoseTile, if (taken) TakenButton else DoseButton, Color(0xFF6247E0))
                    }
                }
                content.health?.let { health ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (health.connectPrompt) Tile(Res.drawable.ic3d_watch, health.connectText, "", "", SleepTile, null, Sadora.colors.text, Modifier.weight(1f))
                        health.items.forEach { item ->
                            val (art, fill) = when (item.metric) {
                                uz.sadora.contract.HealthMetric.STEPS -> Res.drawable.ic3d_active to StepsTile
                                uz.sadora.contract.HealthMetric.SLEEP_DURATION -> Res.drawable.ic3d_sleep to SleepTile
                                else -> Res.drawable.ic3d_heart to DoseTile
                            }
                            Tile(art, item.label, item.value, "", fill, null, Color(0xFF6247E0), Modifier.weight(1f))
                        }
                    }
                }
                if (content.locked) LockTile(content.lockText)
            }
        }
    }
}

@Composable
private fun RowScope.SideColumn(content: WidgetContent) {
    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (content.locked) {
            LockTile(content.lockText, Modifier.weight(1f))
            return@Column
        }
        content.water?.let { Tile(Res.drawable.ic3d_water, it.title, it.label, "+${it.stepMl}", WaterTile, WaterButton, Color(0xFF146A94), Modifier.weight(1f)) }
        content.doses?.let { doses ->
            val next = doses.next
            Tile(Res.drawable.ic3d_meds, next?.let { "${it.name} · ${it.time}" } ?: doses.title, doses.label, "✓", DoseTile, DoseButton, Color(0xFF6247E0), Modifier.weight(1f))
        }
        content.health?.takeIf { content.water == null || content.doses == null }?.let { health ->
            val item = health.items.firstOrNull()
            Tile(Res.drawable.ic3d_active, item?.label ?: health.connectText, item?.value.orEmpty(), "", StepsTile, null, Color(0xFF7F5100), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StageTile(content: WidgetContent, modifier: Modifier, big: Int, radius: Int = 22) {
    val stage = content.stage
    val ink = stage.look.ink()
    Column(
        modifier
            .clay(stage.look.colors(), radius)
            .padding(12.dp)
            .semantics { contentDescription = listOfNotNull(stage.title, stage.big, stage.unit, stage.line1, stage.line2).joinToString(", ") },
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(stage.title, color = ink, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            ClayIcon(stage.look.icon(), 40)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stage.big, color = ink, fontSize = big.sp, fontWeight = FontWeight.Black, lineHeight = big.sp)
            stage.unit?.let { Text(it, color = ink, fontSize = 13.sp, fontWeight = FontWeight.Black) }
        }
        Column {
            stage.line1?.let { Text(it, color = ink, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1) }
            stage.line2?.let { Text(it, color = ink.copy(alpha = 0.92f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
    }
}

/** The large widget's banner: the clay icon, the words, and the number on the right. */
@Composable
private fun WideStageTile(content: WidgetContent, modifier: Modifier) {
    val stage = content.stage
    val ink = stage.look.ink()
    Row(
        modifier
            .clay(stage.look.colors(), 22)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = listOfNotNull(stage.title, stage.big, stage.unit, stage.line1, stage.line2).joinToString(", ") },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClayIcon(stage.look.icon(), 48)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(stage.title, color = ink, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
            stage.line1?.let { Text(it, color = ink, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1) }
            stage.line2?.let { Text(it, color = ink.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
        Text(stage.big, color = ink, fontSize = 40.sp, fontWeight = FontWeight.Black)
        stage.unit?.let { Text(" $it", color = ink, fontSize = 13.sp, fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun Tile(
    art: DrawableResource,
    title: String,
    value: String,
    action: String,
    fill: List<Color>,
    button: List<Color>?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().clay(fill, 18).padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClayIcon(art, 30)
        Column(Modifier.weight(1f).padding(start = 7.dp)) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Black, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (value.isNotEmpty()) Text(value, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF1A1630), maxLines = 1)
        }
        if (button != null && action.isNotEmpty()) {
            Box(Modifier.clay(button, 18).padding(horizontal = 11.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(action, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
        }
    }
}

@Composable
private fun LockTile(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clay(PremiumTile, 18).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClayIcon(Res.drawable.ic3d_crown, 32)
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF7F5100), modifier = Modifier.padding(start = 8.dp))
    }
}

/** Today's one-time card: the widget exists, and here is the door to it. */
@Composable
fun WidgetPromoCard(state: AppState, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val t = strings.widgets
    SadoraCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Box(
                    Modifier.size(64.dp).clay(StageLook.CYCLE.colors(), 20),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(state.cycleDay.takeIf { it > 0 }?.toString() ?: "✿", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                }
                ClayIcon(Res.drawable.ic3d_home, 34, Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(t.cardTitle, style = Sadora.type.h3, color = Sadora.colors.text)
                Text(t.cardBody, style = Sadora.type.body, color = Sadora.colors.muted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SadoraButton(t.cardAdd, onAdd, Modifier.weight(1f))
            SadoraButton(t.cardDismiss, onDismiss, Modifier.weight(1f), tone = ButtonTone.Secondary)
        }
    }
}

/**
 * Android's configure screen for one placed widget: the same page, themed and in the
 * app's language, without the rest of the app around it. The snapshot supplies what the
 * app would — her plan and her language — because the app may not be running.
 */
@Composable
fun WidgetConfigureRoot(
    store: uz.sadora.widget.WidgetStore,
    widgets: WidgetsController,
    onDone: () -> Unit,
    onPaywall: () -> Unit,
) {
    val summary = store.snapshot()?.summary
    val state = remember(summary) {
        AppState().also { s ->
            summary?.let {
                s.isPremium = it.premium
                s.language = when (it.language) {
                    uz.sadora.contract.Language.RU -> uz.sadora.app.model.AppLanguage.Ru
                    uz.sadora.contract.Language.EN -> uz.sadora.app.model.AppLanguage.En
                    uz.sadora.contract.Language.UZ -> uz.sadora.app.model.AppLanguage.Uz
                }
                s.lifeStage = when (it.lifeStage) {
                    LifeStage.PREGNANCY -> uz.sadora.app.model.LifeStage.Pregnancy
                    LifeStage.POSTPARTUM -> uz.sadora.app.model.LifeStage.Postpartum
                    else -> s.lifeStage
                }
            }
        }
    }
    uz.sadora.app.design.SadoraTheme(darkTheme = false) {
        uz.sadora.app.i18n.ProvideStrings(state.language) {
            Column(Modifier.fillMaxWidth().fillMaxHeight().background(Sadora.colors.bg)) {
                WidgetsScreen(state, widgets, onClose = onDone, onPaywall = onPaywall, onToast = {})
            }
        }
    }
}
