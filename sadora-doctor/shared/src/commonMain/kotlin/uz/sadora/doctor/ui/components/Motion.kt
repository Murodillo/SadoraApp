package uz.sadora.doctor.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.first

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
 * Whether a screen is still opening.
 *
 * The rise-and-fade is an entrance, and an entrance happens once: a card that a fling
 * brings into view a second later is not arriving, it was always there. A lazy list
 * composes such a card fresh, though, so without this every one of them started
 * transparent and spent [Motion.Standard] fading in — at speed the list read as blank
 * cards catching up with the finger. The gate closes at the first scroll or when the
 * opening has had its time, whichever is sooner, and everything after that is simply drawn.
 */
class EntranceGate {
    private val born = TimeSource.Monotonic.markNow()

    /** Set at the first drag or fling; deliberately not snapshot state, nothing redraws on it. */
    var scrolled = false

    val open: Boolean
        get() = !scrolled && born.elapsedNow().inWholeMilliseconds <= OpeningMillis

    private companion object {
        /** How long after a screen opens its entries still count as the opening. */
        const val OpeningMillis = 900L
    }
}

/** Null outside a scrolling list — a lone card on a static screen always plays its entrance. */
val LocalEntranceGate = staticCompositionLocalOf<EntranceGate?> { null }

/** Provides an [EntranceGate] that closes the first time [listState] moves. */
@Composable
fun EntranceGated(listState: LazyListState, content: @Composable () -> Unit) {
    val gate = remember { EntranceGate() }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.first { it }
        gate.scrolled = true
    }
    CompositionLocalProvider(LocalEntranceGate provides gate, content = content)
}

/**
 * Fade-and-rise entrance, played once when the element is first composed.
 *
 * [index] staggers a group so a screen's cards arrive in reading order rather than
 * all at once; pass the card's position in the group. Inside a list whose
 * [EntranceGate] has closed it does nothing at all.
 */
fun Modifier.appearFromBelow(
    index: Int = 0,
    distance: Dp = 16.dp,
    durationMillis: Int = Motion.Standard,
    /** Overrides the stagger [index] implies — for an item that arrives on its own. */
    delayMillis: Int = index * Motion.Stagger,
    /** False draws the element in place — a chat's history, as opposed to its newest line. */
    animate: Boolean = true,
): Modifier = composed {
    val gate = LocalEntranceGate.current
    // Decided once, at first composition: an entrance already under way finishes.
    val plays = remember { animate && gate?.open != false }
    if (!plays) return@composed this
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
