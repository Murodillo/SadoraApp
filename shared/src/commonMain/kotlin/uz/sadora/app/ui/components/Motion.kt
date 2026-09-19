package uz.sadora.app.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The app's motion vocabulary.
 *
 * Every animation in SADORA is built from these few values, so a ring filling, a card
 * arriving and a tab sliding all move on the same curve at related speeds. Anything
 * that reacts to a finger is fast; anything that presents information takes its time.
 */
object Motion {
    /** Press feedback and other reactions to touch — must feel instant. */
    const val Quick = 160

    /** The default: a card arriving, a colour changing, a sheet moving. */
    const val Standard = 300

    /** Values counting up, rings filling — long enough to be watched. */
    const val Slow = 720

    /** The cycle dial drawing itself in, one day at a time. */
    const val Reveal = 900

    /** Decelerating curve: quick to leave, slow to settle. Everything entering uses it. */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Symmetric curve for things that move between two places rather than arriving. */
    val Gentle: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** The one spring on the bottom bar, press feedback and anything that should overshoot. */
    val Springy = spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)
    val SpringyDp = spring<Dp>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)

    /** Delay between neighbouring cards in a staggered entrance. */
    const val Stagger = 55
}

/**
 * Clickable with SADORA's press feedback: the surface dips slightly under the finger
 * and springs back when it lifts.
 *
 * This is the indication [noRippleClickable] deliberately leaves to the caller — the
 * design signals a press with movement rather than with a ripple.
 */
fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.975f,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = Motion.Springy,
        label = "press-scale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        role = role,
        onClick = onClick,
    )
}

/**
 * A progress value that starts at zero and travels to [target] the first time it is
 * composed, then follows any later change.
 *
 * Rings and bars that simply appear at their value read as decoration; the same ring
 * filling reads as a measurement, which is what the design's ring is for.
 */
@Composable
fun animatedProgress(
    target: Float,
    durationMillis: Int = Motion.Slow,
    delayMillis: Int = 0,
): Float {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val value by animateFloatAsState(
        targetValue = if (started) target else 0f,
        animationSpec = tween(durationMillis, delayMillis, Motion.Emphasized),
        label = "progress",
    )
    return value
}

/** [animatedProgress] for a whole number — calories, scores, step counts. */
@Composable
fun animatedCount(
    target: Int,
    durationMillis: Int = Motion.Slow,
    delayMillis: Int = 0,
): Int {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val value by animateFloatAsState(
        targetValue = if (started) target.toFloat() else 0f,
        animationSpec = tween(durationMillis, delayMillis, Motion.Emphasized),
        label = "count",
    )
    return value.roundToInt()
}

/**
 * A number that counts up to its value instead of appearing at it.
 *
 * [format] keeps the caller's formatting — thousands separators, units — while the
 * animation only ever moves the number itself.
 */
@Composable
fun AnimatedNumber(
    value: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    format: (Int) -> String = { it.toString() },
) {
    Text(format(animatedCount(value, delayMillis = delayMillis)), style = style, color = color, modifier = modifier, maxLines = 1)
}

/**
 * Fade-and-rise entrance, played once when the element is first composed.
 *
 * [index] staggers a group so a screen's cards arrive in reading order rather than
 * all at once; pass the card's position in the group.
 */
fun Modifier.appearFromBelow(
    index: Int = 0,
    distance: Dp = 16.dp,
    durationMillis: Int = Motion.Standard,
    /** Overrides the stagger [index] implies — for an item that arrives on its own. */
    delayMillis: Int = index * Motion.Stagger,
): Modifier = composed {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val spec = tween<Float>(durationMillis, delayMillis, Motion.Emphasized)
    val progress by animateFloatAsState(if (started) 1f else 0f, spec, label = "appear")
    graphicsLayer {
        alpha = progress
        // GraphicsLayerScope is a Density, so the offset stays in dp at every screen scale.
        translationY = (1f - progress) * distance.toPx()
    }
}
