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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.example.project.design.IconSize
import org.example.project.design.MinTouchTarget
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing

/**
 * The soft lavender lift under every card in the deck.
 *
 * Dark surfaces skip it — a shadow on navy is invisible and only costs a layer — and
 * keep the hairline border instead, which is what separates a card from the ground
 * there.
 */
fun Modifier.cardSurface(
    colors: org.example.project.design.SadoraColors,
    shape: Shape = Radius.card,
    elevation: Dp = 10.dp,
): Modifier = this
    .then(
        if (colors.isDark) {
            Modifier
        } else {
            Modifier.shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = colors.shadow.copy(alpha = 0.08f),
                spotColor = colors.shadow.copy(alpha = 0.12f),
            )
        },
    )
    .clip(shape)
    .background(colors.surface)
    .border(1.dp, colors.line.copy(alpha = if (colors.isDark) 1f else 0.7f), shape)

/** The standard surface: 24dp radius, soft shadow, hairline border. */
@Composable
fun SadoraCard(
    modifier: Modifier = Modifier,
    padding: Dp = Spacing.md,
    onClick: (() -> Unit)? = null,
    verticalGap: Dp = Spacing.sm,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Sadora.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            // A tappable card dips under the finger; a static one must not, or every
            // surface on the screen would look interactive.
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .cardSurface(c)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(verticalGap),
        content = content,
    )
}

/** Eyebrow above a card's content, optionally with a trailing badge or action. */
@Composable
fun CardLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    /** The deck writes card labels in sentence case; the caption style upper-cases by default. */
    uppercase: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Sadora.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (uppercase) {
            Text(text.uppercase(), style = Sadora.type.caption, color = color ?: c.muted)
        } else {
            Text(text, style = Sadora.type.body, color = color ?: c.muted)
        }
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
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Sadora.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = Sadora.type.h2, color = c.text)
        when {
            trailing != null -> trailing()
            action != null -> Text(
                action,
                style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = c.textAccent,
                modifier = Modifier.noRippleClickable { onAction?.invoke() },
            )
        }
    }
}

/**
 * A round pastel tile with an icon in it — the deck's way of leading a card row.
 * [tint] colours the icon; the disc is the same colour washed out.
 */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    size: Dp = 44.dp,
    iconSize: Dp = IconSize.md,
    shape: Shape = Radius.chip,
) {
    val c = Sadora.colors
    val colour = tint ?: c.primary
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(colour.copy(alpha = if (c.isDark) 0.22f else 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, Modifier.size(iconSize), tint = colour)
    }
}

/** The same disc with an emoji instead of a vector. */
@Composable
fun EmojiTile(
    emoji: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    size: Dp = 44.dp,
    shape: Shape = Radius.chip,
) {
    val c = Sadora.colors
    val colour = tint ?: c.primary
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(colour.copy(alpha = if (c.isDark) 0.22f else 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, style = Sadora.type.h3)
    }
}

/**
 * The deck's small stat card: a label, a value, a caption, and an icon on the right.
 * Used for the 2×2 grid on Today.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    icon: ImageVector? = null,
    emoji: String? = null,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    footer: @Composable (ColumnScope.() -> Unit)? = null,
) {
    val c = Sadora.colors
    SadoraCard(modifier = modifier, padding = Spacing.sm, onClick = onClick, verticalGap = Spacing.xs) {
        Text(label, style = Sadora.type.body, color = c.muted)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(value, style = Sadora.type.h2, color = c.text, maxLines = 1)
                if (caption != null) Text(caption, style = Sadora.type.body, color = c.muted2)
            }
            when {
                icon != null -> IconTile(icon, tint = tint, size = 40.dp)
                emoji != null -> EmojiTile(emoji, tint = tint, size = 40.dp)
            }
        }
        footer?.invoke(this)
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
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        IconTile(icon, tint = iconTint, size = 36.dp, iconSize = IconSize.md, shape = RoundedCornerShape(Radius.sm))
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
 * Image stand-in.
 *
 * Photography arrives from the scanner or the server; until then a tile shows a soft
 * lavender-to-pink wash with an optional [emoji] on it, which reads as the subject
 * rather than as a missing asset.
 */
@Composable
fun ImagePlaceholder(
    modifier: Modifier = Modifier,
    label: String? = null,
    emoji: String? = null,
    shape: Shape = Radius.cardSmall,
    colors: List<Color>? = null,
) {
    val c = Sadora.colors
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors ?: listOf(
                        c.primary.copy(alpha = if (c.isDark) 0.45f else 0.22f),
                        c.secondary.copy(alpha = if (c.isDark) 0.4f else 0.22f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            emoji != null -> Text(emoji, style = Sadora.type.display)
            label != null -> Text(label, style = Sadora.type.caption, color = c.textAccent)
        }
    }
}

/**
 * The AI summary card on Today: the deck's lavender card with the brand mark on the
 * right. Free accounts get the same card as the invitation to Premium.
 */
@Composable
fun AiSummaryCard(
    body: String,
    modifier: Modifier = Modifier,
    label: String = "SADORA AI",
    footnote: String? = null,
    showPremiumBadge: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val c = Sadora.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (c.isDark) Modifier else Modifier.shadow(
                    10.dp, Radius.card,
                    ambientColor = c.shadow.copy(alpha = 0.10f),
                    spotColor = c.shadow.copy(alpha = 0.16f),
                ),
            )
            .clip(Radius.card)
            .background(
                Brush.linearGradient(
                    listOf(
                        c.primary.copy(alpha = if (c.isDark) 0.35f else 0.14f),
                        c.secondary.copy(alpha = if (c.isDark) 0.35f else 0.18f),
                    ),
                ),
            )
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .padding(start = Spacing.md, top = Spacing.md, bottom = Spacing.md, end = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                // The badge keeps its width and the title gives way, rather than the
                // title pushing "PREMIUM" onto a second line.
                Text(
                    label,
                    style = Sadora.type.h3,
                    color = c.text,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (showPremiumBadge) PremiumGradientBadge()
            }
            Text(body, style = Sadora.type.body, color = c.muted)
            if (footnote != null) {
                Text(
                    footnote,
                    style = Sadora.type.caption.copy(letterSpacing = 0.02.em),
                    color = c.muted2,
                )
            }
        }
        SadoraMark(size = 64.dp)
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
        Icon(SadoraIcons.Lock, contentDescription = null, Modifier.size(IconSize.lg), tint = c.primary)
        Text(title, style = Sadora.type.h3, color = c.text)
        Spacer(Modifier.height(Spacing.xxs))
        SadoraButton(action, onUnlock, fillWidth = false)
    }
}

/** Full-width horizontal spacer on the grid. */
@Composable
fun GapH(width: Dp = Spacing.xs) = Spacer(Modifier.width(width))
