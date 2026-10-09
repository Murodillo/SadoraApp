package uz.sadora.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uz.sadora.app.design.MinTouchTarget
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import org.jetbrains.compose.resources.DrawableResource

/** Selectable chip — symptoms, goals, filters. Selected state uses a tinted fill. */
@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: String? = null,
    /** A colour icon before the label, preferred over [leading]; it hops when picked. */
    art: DrawableResource? = null,
) {
    val c = Sadora.colors
    val bg by animateColorAsState(
        if (selected) c.primary.copy(alpha = if (c.isDark) 0.22f else 0.12f) else c.surface,
    )
    // Unpicked, the outline is the chip's only edge on a white card, so it needs 3:1.
    val border by animateColorAsState(if (selected) c.primary else c.lineStrong)
    val fg by animateColorAsState(if (selected) c.textAccent else c.text)

    Box(
        modifier = modifier
            .clip(Radius.chip)
            .background(bg)
            .border(1.dp, border, Radius.chip)
            .defaultMinSize(minHeight = MinTouchTarget)
            .noRippleToggleable(selected, role = Role.Checkbox) { onClick() }
            .padding(horizontal = 14.dp, vertical = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (art != null) SelectableArt(art, 22.dp, selected)
            else if (leading != null) Text(leading, style = Sadora.type.body, color = fg)
            Text(label, style = Sadora.type.body.copy(fontWeight = FontWeight.Medium), color = fg)
            if (selected) Text("✓", style = Sadora.type.body, color = fg, modifier = Modifier.clearAndSetSemantics {})
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
    /** Preferred over [leading]: a vector follows the tint, an emoji does not. */
    icon: ImageVector? = null,
) {
    val c = Sadora.colors
    val (bg, fg) = when (tone) {
        // The wash keeps the tone's own colour; the word takes its text shade, since the
        // fills read at 2.2–4.1:1 on their own wash on light.
        BadgeTone.Premium -> c.secondary.copy(alpha = if (c.isDark) 0.24f else 0.14f) to c.secondaryText
        BadgeTone.Connected, BadgeTone.Success -> c.success.copy(alpha = 0.16f) to c.successText
        BadgeTone.Estimated, BadgeTone.Neutral -> c.surface2 to c.muted
        BadgeTone.Warning -> c.warning.copy(alpha = 0.16f) to c.warningText
        BadgeTone.Danger -> c.danger.copy(alpha = 0.14f) to c.dangerText
    }
    Box(
        modifier = modifier
            .clip(Radius.chip)
            .background(bg)
            .padding(horizontal = Spacing.xs, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, Modifier.size(13.dp), tint = fg)
            } else if (leading != null) {
                Text(leading, style = Sadora.type.caption, color = fg)
            }
            Text(text, style = Sadora.type.caption, color = fg, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The gradient PREMIUM badge used on hero surfaces. */
@Composable
fun PremiumGradientBadge(modifier: Modifier = Modifier, text: String = "PREMIUM") {
    val c = Sadora.colors
    Box(
        modifier
            .clip(Radius.chip)
            .background(c.heroGradient)
            .padding(horizontal = Spacing.xs, vertical = 4.dp),
    ) {
        // A badge is sized to its word; wrapping it is always a layout bug at the call
        // site, so it never wraps here.
        Text(text, style = Sadora.type.caption, color = c.onPrimary, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
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
            .clip(Radius.chip)
            .background(c.surface2)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val locked = index in lockedIndices
            Box(
                Modifier
                    .weight(1f)
                    .clip(Radius.chip)
                    .background(if (selected) c.surface else Color.Transparent)
                    // A locked segment reads as a disabled tab; the padlock is for the eye.
                    .noRippleSelectable(selected, role = Role.Tab, enabled = !locked) { onSelect(index) }
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
                    if (locked) Text("🔒", style = Sadora.type.caption, color = c.muted2, modifier = Modifier.clearAndSetSemantics {})
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
