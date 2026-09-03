package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.example.project.design.IconSize
import org.example.project.design.MinTouchTarget
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing

/** The standard surface: 22dp radius, hairline border, no drop shadow in dark. */
@Composable
fun SadoraCard(
    modifier: Modifier = Modifier,
    padding: androidx.compose.ui.unit.Dp = Spacing.md,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Sadora.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(c.surface)
            .border(1.dp, c.line, Radius.card)
            .then(if (onClick != null) Modifier.noRippleClickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        content = content,
    )
}

/** Eyebrow above a card's content, optionally with a trailing badge or action. */
@Composable
fun CardLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Sadora.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text.uppercase(), style = Sadora.type.caption, color = color ?: c.muted)
        trailing?.invoke()
    }
}

/** Section heading used between card groups on a screen. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = Sadora.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = Sadora.type.h2, color = c.text)
        if (action != null) {
            Text(
                action,
                style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = c.textAccent,
                modifier = Modifier.noRippleClickable { onAction?.invoke() },
            )
        }
    }
}

/**
 * Settings-style row: round icon tile, title, optional value, chevron.
 */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    iconTint: Color? = null,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val c = Sadora.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MinTouchTarget)
            .then(if (onClick != null) Modifier.noRippleClickable(onClick = onClick) else Modifier)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(Radius.sm)).background(c.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                Modifier.size(IconSize.md),
                tint = iconTint ?: c.secondary,
            )
        }
        Text(title, style = Sadora.type.h3, color = c.text, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = Sadora.type.body, color = c.muted)
        if (showChevron) {
            Icon(
                SadoraIcons.ChevronRight,
                contentDescription = null,
                Modifier.size(IconSize.md),
                tint = c.muted2,
            )
        }
    }
}

/** Thin divider matching the `line` token. */
@Composable
fun SadoraDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Sadora.colors.line))
}

/**
 * Compact metric tile — "Uyqu 6s 40d", "Faollik 8 240". Used in 2-up grids.
 */
@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    accent: Color? = null,
    /** Device + timestamp, e.g. "Oura · 07:05". Every synced metric shows its source. */
    source: String? = null,
) {
    val c = Sadora.colors
    SadoraCard(modifier = modifier, padding = Spacing.sm) {
        Text(label, style = Sadora.type.body, color = c.muted)
        Text(value, style = Sadora.type.h1, color = accent ?: c.text)
        if (caption != null) Text(caption, style = Sadora.type.body, color = c.muted)
        if (source != null) {
            SadoraBadge(source, BadgeTone.Neutral)
        }
    }
}

/** Two tiles side by side on the 8pt grid. */
@Composable
fun TileRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) = Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    content = content,
)

/**
 * Image stand-in. The design ships gradient placeholders instead of photography,
 * so this renders the same treatment with the "RASM" label.
 */
@Composable
fun ImagePlaceholder(
    modifier: Modifier = Modifier,
    label: String = "RASM",
    shape: androidx.compose.ui.graphics.Shape = Radius.cardSmall,
    colors: List<Color>? = null,
) {
    val c = Sadora.colors
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors ?: listOf(
                        c.secondary.copy(alpha = 0.55f),
                        c.primary.copy(alpha = 0.45f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Sadora.type.caption, color = c.onPrimary.copy(alpha = 0.85f))
    }
}

/**
 * The AI summary card. One of the four sanctioned gradient surfaces.
 */
@Composable
fun AiSummaryCard(
    body: String,
    modifier: Modifier = Modifier,
    label: String = "SADORA AI · KUNLIK XULOSA",
    footnote: String = "Ma'lumotlaringiz asosida · AI tomonidan yaratilgan",
    showPremiumBadge: Boolean = true,
    /** Opens the full AI chat. This card is the way in now that AI has no tab. */
    onClick: (() -> Unit)? = null,
) {
    val c = Sadora.colors
    val onGradient = c.onPrimary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .then(
                if (onClick != null) Modifier.noRippleClickable(onClick = onClick) else Modifier,
            )
            .background(Brush.linearGradient(listOf(c.secondary, c.primary)))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                Modifier.size(18.dp).clip(RoundedCornerShape(6.dp))
                    .background(onGradient.copy(alpha = 0.22f)),
            )
            Text(label, style = Sadora.type.caption, color = onGradient)
            Spacer(Modifier.weight(1f))
            if (showPremiumBadge) {
                Box(
                    Modifier.clip(Radius.chip)
                        .background(onGradient.copy(alpha = 0.2f))
                        .padding(horizontal = Spacing.xs, vertical = 3.dp),
                ) {
                    Text("PREMIUM", style = Sadora.type.caption, color = onGradient)
                }
            }
        }
        Text(body, style = Sadora.type.body, color = onGradient)
        Text(
            footnote,
            style = Sadora.type.caption.copy(letterSpacing = 0.02.em),
            color = onGradient.copy(alpha = 0.75f),
        )
    }
}

/**
 * A note the app shows wherever it must not be read as medical guidance.
 */
@Composable
fun DisclaimerNote(
    text: String,
    modifier: Modifier = Modifier,
    icon: String? = null,
) {
    val c = Sadora.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.cardSmall)
            .background(c.surface2)
            .padding(Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (icon != null) Text(icon, style = Sadora.type.body, color = c.muted)
        Text(text, style = Sadora.type.body, color = c.muted)
    }
}

/** Locked Premium overlay content — visible but blurred-out in the design. */
@Composable
fun LockedBlock(
    title: String,
    modifier: Modifier = Modifier,
    action: String = "Ochish",
    onUnlock: () -> Unit,
) {
    val c = Sadora.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.cardSmall)
            .background(c.surface2.copy(alpha = 0.85f))
            .border(1.dp, c.line, Radius.cardSmall)
            .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text("🔒", style = Sadora.type.h2)
        Text(title, style = Sadora.type.h3, color = c.text)
        Spacer(Modifier.height(Spacing.xxs))
        SadoraButton(action, onUnlock, fillWidth = false)
    }
}

/** Full-width horizontal spacer on the grid. */
@Composable
fun GapH(width: androidx.compose.ui.unit.Dp = Spacing.xs) = Spacer(Modifier.width(width))
