package org.example.project.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing

/** Selectable chip — symptoms, goals, filters. Selected state uses a tinted fill. */
@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: String? = null,
) {
    val c = Sadora.colors
    val bg by animateColorAsState(
        if (selected) c.primary.copy(alpha = if (c.isDark) 0.22f else 0.12f) else c.surface,
    )
    val border by animateColorAsState(if (selected) c.primary else c.line)
    val fg by animateColorAsState(if (selected) c.textAccent else c.text)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(999.dp))
            .defaultMinSize(minHeight = 38.dp)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leading != null) Text(leading, style = Sadora.type.body, color = fg)
            Text(label, style = Sadora.type.body.copy(fontWeight = FontWeight.Medium), color = fg)
            if (selected) Text("✓", style = Sadora.type.body, color = fg)
        }
    }
}

enum class BadgeTone { Premium, Connected, Estimated, Success, Warning, Danger, Neutral }

/**
 * Small status badge: PREMIUM, ULANGAN, TAXMINIY and the semantic tones.
 *
 * "TAXMINIY" appears next to every prediction — the design requires estimates to be
 * labelled wherever they are shown.
 */
@Composable
fun SadoraBadge(
    text: String,
    tone: BadgeTone = BadgeTone.Neutral,
    modifier: Modifier = Modifier,
    leading: String? = null,
) {
    val c = Sadora.colors
    val (bg, fg) = when (tone) {
        BadgeTone.Premium -> c.secondary.copy(alpha = if (c.isDark) 0.24f else 0.14f) to c.secondary
        BadgeTone.Connected, BadgeTone.Success -> c.success.copy(alpha = 0.16f) to c.success
        BadgeTone.Estimated, BadgeTone.Neutral -> c.surface2 to c.muted
        BadgeTone.Warning -> c.warning.copy(alpha = 0.16f) to c.warning
        BadgeTone.Danger -> c.danger.copy(alpha = 0.14f) to c.danger
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = Spacing.xs, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (leading != null) Text(leading, style = Sadora.type.caption, color = fg)
            Text(text, style = Sadora.type.caption, color = fg, maxLines = 1, softWrap = false)
        }
    }
}

/** The gradient PREMIUM badge used on hero surfaces. */
@Composable
fun PremiumGradientBadge(modifier: Modifier = Modifier, text: String = "PREMIUM") {
    val c = Sadora.colors
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Brush.linearGradient(listOf(c.secondary, c.primary)))
            .padding(horizontal = Spacing.xs, vertical = 4.dp),
    ) {
        Text(text, style = Sadora.type.caption, color = if (c.isDark) c.bg else Color.White)
    }
}

/**
 * Segmented control — "7 kun / 30 kun / 90 kun", "Bugun / Barchasi / Tarix".
 * Options may be locked, which renders a padlock and blocks selection.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    lockedIndices: Set<Int> = emptySet(),
) {
    val c = Sadora.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(c.surface2)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val locked = index in lockedIndices
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) c.surface else Color.Transparent)
                    .noRippleClickable(enabled = !locked) { onSelect(index) }
                    .padding(vertical = Spacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        option,
                        style = Sadora.type.body.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = when {
                            locked -> c.muted2
                            selected -> c.text
                            else -> c.muted
                        },
                    )
                    if (locked) Text("🔒", style = Sadora.type.caption, color = c.muted2)
                }
            }
        }
    }
}

/** Row of chips that wraps to as many lines as it needs. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChipFlowRow(
    modifier: Modifier = Modifier,
    horizontalGap: androidx.compose.ui.unit.Dp = Spacing.xs,
    verticalGap: androidx.compose.ui.unit.Dp = Spacing.xs,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(horizontalGap),
        verticalArrangement = Arrangement.spacedBy(verticalGap),
    ) { content() }
}
