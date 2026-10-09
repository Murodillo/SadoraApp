package uz.sadora.app.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import uz.sadora.app.design.IconSize
import uz.sadora.app.design.MinTouchTarget
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing

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

    val content = when (tone) {
        ButtonTone.Primary -> c.onPrimary
        ButtonTone.Secondary -> c.text
        ButtonTone.Outline -> c.textAccent
        ButtonTone.Ghost -> c.muted
        ButtonTone.Destructive -> c.dangerText
    }
    // Pressed, a clay button sinks: it drops a little and its shadow tightens under it.
    val sink by animateFloatAsState(if (pressed && enabled) 1f else 0f, label = "button-sink")

    Box(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .scale(scale)
            .graphicsLayer { translationY = sink * 2.dp.toPx() }
            .alpha(if (enabled) 1f else 0.45f)
            .then(
                when (tone) {
                    ButtonTone.Primary -> Modifier.claySurface(
                        c, Radius.field, c.heroGradient,
                        elevation = 12.dp - 8.dp * sink,
                        shadowTint = c.primary,
                        gloss = HeroLabelGloss,
                        streak = true,
                    )
                    ButtonTone.Secondary -> Modifier.claySurface(c, Radius.field, c.surface2, elevation = 6.dp - 4.dp * sink, streak = true)
                    ButtonTone.Outline -> Modifier
                        .claySurface(c, Radius.field, c.surface, elevation = 6.dp - 4.dp * sink)
                        .border(1.5.dp, c.primary, Radius.field)
                    ButtonTone.Destructive -> Modifier
                        .claySurface(c, Radius.field, c.surface, elevation = 4.dp - 3.dp * sink)
                        .border(1.dp, c.danger, Radius.field)
                    ButtonTone.Ghost -> Modifier.clip(Radius.field)
                },
            )
            .defaultMinSize(minHeight = MinTouchTarget)
            .noRippleClickable(enabled = enabled, interactionSource = interaction, role = Role.Button, focusShape = Radius.field, onClick = onClick)
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
                // Two lines rather than one: at a large system font a single line was cut
                // off mid-word with nothing to say so. The button grows to fit.
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * How much of the clay light a white-labelled button on the hero keeps. The full lit
 * face is a 42% white wash along the top that fades over the upper half, and the
 * label's first line sits halfway into it: at the old 0.9 white there fell to 3.6:1.
 * At this the wash behind the label stays near 3%, which holds 4.5:1 across the hero.
 */
internal const val HeroLabelGloss = 0.15f

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
    /** Off while the action it fires is in flight: a pill that stayed live sent the request twice. */
    enabled: Boolean = true,
) {
    val c = Sadora.colors
    // Filled, the pill takes the deep end of the hero rather than [primary]: white on
    // primary was 4.2:1 on light and 3.3:1 on dark, and 5.3:1 on this in both.
    val bg = if (tone == ButtonTone.Primary) c.heroColors.first() else c.surface2
    val fg = if (tone == ButtonTone.Primary) c.onPrimary else c.text
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .claySurface(
                c, Radius.chip, bg,
                elevation = if (tone == ButtonTone.Primary) 6.dp else 3.dp,
                shadowTint = if (tone == ButtonTone.Primary) c.primary else c.shadow,
                gloss = if (tone == ButtonTone.Primary) HeroLabelGloss else 1f,
                streak = true,
            )
            // The pill itself stays compact; the touch target around it is the full 44dp.
            .defaultMinSize(minWidth = MinTouchTarget, minHeight = MinTouchTarget)
            .pressable(enabled = enabled, role = Role.Button, focusShape = Radius.chip, onClick = onClick)
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
            overflow = TextOverflow.Ellipsis,
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
            .pressable(pressedScale = 0.9f, role = Role.Button, focusShape = CircleShape, onClick = onClick)
            .clayBeadSurface(c, if (filled) c.heroColors.first() else c.surface2),
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
