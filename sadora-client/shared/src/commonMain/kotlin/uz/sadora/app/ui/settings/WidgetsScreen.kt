package uz.sadora.app.ui.settings

import androidx.compose.foundation.background
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
                    WidgetPreview(content, config.size, state.lifeStage.isWarm())
                }
            }
        }

        item {
            SadoraCard {
                CardLabel(t.blocks)
                BlockRow(t.stageBlock, t.stageBlockNote, checked = true, enabled = false) {}
                BlockRow(t.waterBlock, null, WidgetBlock.WATER in config.blocks, premium) { widgets.toggle(WidgetBlock.WATER) }
                BlockRow(t.dosesBlock, null, WidgetBlock.DOSES in config.blocks, premium) { widgets.toggle(WidgetBlock.DOSES) }
                BlockRow(t.healthBlock, null, WidgetBlock.HEALTH in config.blocks, premium) { widgets.toggle(WidgetBlock.HEALTH) }
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

@Composable
private fun BlockRow(title: String, note: String?, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val c = Sadora.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Sadora.type.h3, color = if (enabled || checked) c.text else c.muted)
            note?.let { Text(it, style = Sadora.type.body, color = c.muted) }
        }
        SadoraSwitch(checked, { if (enabled) onToggle() })
    }
}

private fun uz.sadora.app.model.LifeStage.isWarm() =
    this == uz.sadora.app.model.LifeStage.Pregnancy || this == uz.sadora.app.model.LifeStage.Postpartum

private const val MAX_METRICS = 2

// -------------------------------------------------------------------- the preview

/**
 * The widget as the home screen will draw it — the same [WidgetContent] the Glance and
 * SwiftUI renderers lay out, so the preview cannot promise what the widget will not show.
 */
@Composable
fun WidgetPreview(content: WidgetContent, size: WidgetSize, warm: Boolean, modifier: Modifier = Modifier) {
    val height = if (size == WidgetSize.LARGE) 300.dp else 150.dp
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier
            .fillMaxWidth(if (size == WidgetSize.SMALL) 0.48f else 1f)
            .height(height)
            .clip(shape)
            .background(Sadora.colors.surface2)
            .padding(10.dp),
    ) {
        when (size) {
            WidgetSize.SMALL -> StageTile(content, warm, Modifier.fillMaxWidth().fillMaxHeight(), big = 52)
            WidgetSize.MEDIUM -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StageTile(content, warm, Modifier.weight(1f).fillMaxHeight(), big = 40)
                SideColumn(content)
            }
            WidgetSize.LARGE -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StageTile(content, warm, Modifier.fillMaxWidth().height(96.dp), big = 40)
                content.water?.let { Tile(it.title, it.label, it.stepLabel, Sadora.colors.accent) }
                content.doses?.let { doses ->
                    doses.rows.take(3).forEach { row ->
                        Tile(row.name, row.time, if (row.status == uz.sadora.contract.DoseStatus.TAKEN) "✓" else "○", Sadora.colors.primary)
                    }
                }
                content.health?.let { health ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (health.connectPrompt) Tile(health.connectText, "", "", Sadora.colors.warning, Modifier.weight(1f))
                        health.items.forEach { Tile(it.label, it.value, "", Sadora.colors.secondary, Modifier.weight(1f)) }
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
        content.water?.let { Tile(it.title, it.label, it.stepLabel, Sadora.colors.accent, Modifier.weight(1f)) }
        content.doses?.let { doses ->
            val next = doses.next
            Tile(next?.let { "${it.name} · ${it.time}" } ?: doses.title, doses.label, "✓", Sadora.colors.primary, Modifier.weight(1f))
        }
        content.health?.takeIf { content.water == null || content.doses == null }?.let { health ->
            val item = health.items.firstOrNull()
            Tile(item?.label ?: health.connectText, item?.value.orEmpty(), "", Sadora.colors.secondary, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StageTile(content: WidgetContent, warm: Boolean, modifier: Modifier, big: Int) {
    val stage = content.stage
    val colors = when {
        warm -> listOf(StagePalettes.pregnancy.start, StagePalettes.pregnancy.end)
        stage.phase == CyclePhase.PERIOD -> listOf(Color(0xFFD42A78), Color(0xFFFF6FB8))
        else -> listOf(Color(0xFF6A4FF0), Color(0xFFC93C88))
    }
    val ink = if (warm) StagePalettes.warmInk else Color.White
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(colors))
            .padding(12.dp)
            .semantics { contentDescription = listOfNotNull(stage.title, stage.big, stage.unit, stage.line1, stage.line2).joinToString(", ") },
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stage.title, color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stage.big, color = ink, fontSize = big.sp, fontWeight = FontWeight.Black, lineHeight = big.sp)
            stage.unit?.let { Text(it, color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
        Column {
            stage.line1?.let { Text(it, color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
            stage.line2?.let { Text(it, color = ink.copy(alpha = 0.9f), fontSize = 11.sp, maxLines = 1) }
        }
    }
}

@Composable
private fun ColumnScope.Tile(title: String, value: String, action: String, accent: Color, modifier: Modifier = Modifier) =
    TileBox(title, value, action, accent, modifier)

@Composable
private fun RowScope.Tile(title: String, value: String, action: String, accent: Color, modifier: Modifier = Modifier) =
    TileBox(title, value, action, accent, modifier)

@Composable
private fun TileBox(title: String, value: String, action: String, accent: Color, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Sadora.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (value.isNotEmpty()) Text(value, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Sadora.colors.text, maxLines = 1)
        }
        if (action.isNotEmpty()) {
            Box(Modifier.clip(Radius.chip).background(accent).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(action, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
        }
    }
}

@Composable
private fun LockTile(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Sadora.colors.surface).padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("🔒 $text", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Sadora.colors.textAccent)
    }
}

/** Today's one-time card: the widget exists, and here is the door to it. */
@Composable
fun WidgetPromoCard(state: AppState, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val t = strings.widgets
    SadoraCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF6A4FF0), Color(0xFFC93C88))))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(state.cycleDay.takeIf { it > 0 }?.toString() ?: "✿", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
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
