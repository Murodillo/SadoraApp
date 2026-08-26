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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.design.MinTouchTarget
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing

enum class ButtonTone { Primary, Secondary, Ghost, Destructive }

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
    fillWidth: Boolean = true,
) {
    val c = Sadora.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.98f else 1f)

    val background = when (tone) {
        ButtonTone.Primary -> c.primary
        ButtonTone.Secondary -> c.surface2
        ButtonTone.Ghost -> Color.Transparent
        ButtonTone.Destructive -> Color.Transparent
    }
    val content = when (tone) {
        ButtonTone.Primary -> c.onPrimary
        ButtonTone.Secondary -> c.text
        ButtonTone.Ghost -> c.muted
        ButtonTone.Destructive -> c.danger
    }

    Box(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .scale(scale)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(Radius.field)
            .background(background)
            .then(
                if (tone == ButtonTone.Destructive) {
                    Modifier.border(1.dp, c.danger.copy(alpha = 0.5f), Radius.field)
                } else Modifier,
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
            if (leading != null) {
                Text(leading, color = content, style = Sadora.type.h3)
            }
            Text(
                text = text,
                color = content,
                style = Sadora.type.h3.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

/**
 * Premium call-to-action. One of only four places the hero gradient is allowed.
 */
@Composable
fun PremiumCtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Sadora.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.98f else 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(Radius.field)
            .background(Brush.linearGradient(listOf(c.secondary, c.primary)))
            .defaultMinSize(minHeight = MinTouchTarget)
            .noRippleClickable(enabled = enabled, interactionSource = interaction, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (c.isDark) c.bg else Color.White,
            style = Sadora.type.h3.copy(fontWeight = FontWeight.Bold),
        )
    }
}

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
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .defaultMinSize(minHeight = 36.dp)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold))
    }
}
