package org.example.project.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.design.IconSize
import org.example.project.design.MinTouchTarget
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing

/**
 * Button fills. [Primary] is the deck's purple→pink gradient pill; [Outline] is the
 * same pill hollow, for the calmer half of a yes/no pair.
 */
enum class ButtonTone { Primary, Secondary, Outline, Ghost, Destructive }

/**
 * The one button implementation. Tone picks the fill; every tone shares the same
 * height, radius and pressed behaviour so they stay interchangeable in a layout.
 */
@Composable
fun SadoraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Primary,
    enabled: Boolean = true,
    leading: String? = null,
    /** Preferred over [leading]: a vector follows the button's content colour. */
    icon: ImageVector? = null,
    fillWidth: Boolean = true,
) {
    val c = Sadora.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.98f else 1f)

    val background: Brush = when (tone) {
        ButtonTone.Primary -> c.heroGradient
        ButtonTone.Secondary -> Brush.linearGradient(listOf(c.surface2, c.surface2))
        ButtonTone.Outline, ButtonTone.Ghost, ButtonTone.Destructive ->
            Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
    }
    val content = when (tone) {
        ButtonTone.Primary -> c.onPrimary
        ButtonTone.Secondary -> c.text
        ButtonTone.Outline -> c.textAccent
        ButtonTone.Ghost -> c.muted
        ButtonTone.Destructive -> c.danger
    }

    Box(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .scale(scale)
            .alpha(if (enabled) 1f else 0.45f)
            .then(
                if (tone == ButtonTone.Primary && !c.isDark) {
                    Modifier.shadow(
                        10.dp, Radius.field,
                        ambientColor = c.primary.copy(alpha = 0.25f),
                        spotColor = c.primary.copy(alpha = 0.35f),
                    )
                } else Modifier,
            )
            .clip(Radius.field)
            .background(background)
            .then(
                when (tone) {
                    ButtonTone.Destructive -> Modifier.border(1.dp, c.danger.copy(alpha = 0.5f), Radius.field)
                    ButtonTone.Outline -> Modifier.border(1.5.dp, c.primary.copy(alpha = 0.55f), Radius.field)
                    else -> Modifier
                },
            )
            .defaultMinSize(minHeight = MinTouchTarget)
            .noRippleClickable(enabled = enabled, interactionSource = interaction, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, Modifier.size(IconSize.md), tint = content)
            } else if (leading != null) {
                Text(leading, color = content, style = Sadora.type.h3)
            }
            Text(
                text = text,
                color = content,
                style = Sadora.type.h3.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
        }
    }
}

/**
 * Premium call-to-action. Same gradient pill as the primary button; kept as its own
 * name so a paywall reads as one in the code.
 */
@Composable
fun PremiumCtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = SadoraButton(text, onClick, modifier, tone = ButtonTone.Primary, enabled = enabled)

/** A small pill action such as "+250 ml" or "Qabul qildim". */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Secondary,
) {
    val c = Sadora.colors
    val bg = if (tone == ButtonTone.Primary) c.primary else c.surface2
    val fg = if (tone == ButtonTone.Primary) c.onPrimary else c.text
    Box(
        modifier = modifier
            .clip(Radius.chip)
            .background(bg)
            .defaultMinSize(minHeight = 36.dp)
            .pressable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        // A pill is sized to its label; wrapping it onto a second line is always a
        // layout bug at the call site, so it never wraps here.
        Text(
            text,
            color = fg,
            style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * A round icon button — the deck's play, pencil and "+" buttons. [filled] paints it
 * in the primary colour; otherwise it is a pale disc.
 */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    contentDescription: String? = null,
) {
    val c = Sadora.colors
    Box(
        modifier
            .size(size)
            .clip(Radius.chip)
            .background(if (filled) c.primary else c.surface2)
            .pressable(pressedScale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            Modifier.size(IconSize.md),
            tint = if (filled) c.onPrimary else c.text,
        )
    }
}
