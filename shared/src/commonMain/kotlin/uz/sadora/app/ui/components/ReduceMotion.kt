package uz.sadora.app.ui.components

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the phone asks for less motion: Android's "Remove animations", iOS's
 * "Reduce Motion". Read once at the root and provided through [LocalReduceMotion].
 */
@Composable
expect fun systemReducesMotion(): Boolean

/** True when the endless decorations — breathing orbs, drifting petals, spinners — hold still. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * An endless animation that stands still when the phone asks for less motion.
 *
 * Every infinite loop in the app goes through here. Someone with a vestibular disorder
 * who has turned motion off should not find a breathing orb on the welcome screen and
 * petals drifting behind a reward; the one-off transitions stay, the loops stop at
 * [still], which defaults to where each would start.
 */
@Composable
fun InfiniteTransition.animateFloatUnlessReduced(
    initialValue: Float,
    targetValue: Float,
    animationSpec: InfiniteRepeatableSpec<Float>,
    label: String = "FloatAnimation",
    still: Float = initialValue,
): State<Float> =
    if (LocalReduceMotion.current) {
        remember(still) { mutableFloatStateOf(still) }
    } else {
        animateFloat(initialValue, targetValue, animationSpec, label)
    }
