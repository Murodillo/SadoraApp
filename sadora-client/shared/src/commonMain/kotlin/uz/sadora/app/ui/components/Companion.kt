package uz.sadora.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uz.sadora.app.design.Sadora
import uz.sadora.contract.PetKind
import uz.sadora.contract.PetPose
import kotlin.math.PI
import kotlin.math.sin
import uz.sadora.app.data.Win

/**
 * Her companion as the rest of the app sees it: which pet, and how many small wins it
 * has cheered so far this session. Null when her plan has no pet — then every place that
 * would show it draws what it drew before.
 */
data class Companion(
    val pet: PetKind,
    val cheers: Int = 0,
    val lastWin: Win? = null,
    /** Tells the pet a card showed cheer number n, so no bubble repeats it. */
    val onCheered: (Int) -> Unit = {},
)

// Dynamic, not static: a cheer recomposes the few places that read it, not the whole app.
val LocalCompanion = compositionLocalOf<Companion?> { null }

/** The companion in [pose], or [fallback] for an account without one. */
@Composable
fun CompanionOr(
    pose: PetPose,
    size: Dp,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit = {},
) {
    val companion = LocalCompanion.current
    if (companion != null) PetImage(companion.pet, pose, size, modifier) else fallback()
}

/**
 * The companion for a moment that just went well — a water goal, a dose taken, a badge.
 * When [LocalCompanion]'s cheer count moves on it hops in [PetPose.HAPPY] for a couple of
 * seconds. With a [resting] pose it stands there the rest of the time; without one it is
 * invisible until the win and pops up for it. No bubble, no text: the card it stands on
 * already says what happened.
 */
@Composable
fun CompanionCheer(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    resting: PetPose? = null,
    /** The wins this place cheers; null cheers every one. */
    wins: Set<Win>? = null,
) {
    val companion = LocalCompanion.current ?: return
    // The count at first sight is the baseline: a card scrolled into view later must not
    // replay a win from before it existed.
    val seen = remember { mutableStateOf(companion.cheers) }
    var cheering by remember { mutableStateOf(false) }
    LaunchedEffect(companion.cheers) {
        if (companion.cheers == seen.value) return@LaunchedEffect
        seen.value = companion.cheers
        if (wins != null && companion.lastWin !in wins) return@LaunchedEffect
        companion.onCheered(companion.cheers)
        cheering = true
        delay(CheerMillis)
        cheering = false
    }
    if (resting != null) {
        Box(modifier) { CheeringPet(companion.pet, if (cheering) PetPose.HAPPY else resting, size, hops = cheering) }
        return
    }
    AnimatedVisibility(
        visible = cheering,
        modifier = modifier,
        enter = scaleIn(initialScale = 0.4f) + slideInVertically { it / 2 },
        exit = fadeOut(),
    ) {
        CheeringPet(companion.pet, PetPose.HAPPY, size, hops = true)
    }
}

@Composable
private fun CheeringPet(pet: PetKind, pose: PetPose, size: Dp, hops: Boolean) {
    val transition = rememberInfiniteTransition()
    val phase by transition.animateFloatUnlessReduced(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
    )
    val hop = if (hops) sin(phase * PI.toFloat()).coerceAtLeast(0f) else 0f
    PetImage(pet, pose, size, Modifier.graphicsLayer { translationY = -hop * 6.dp.toPx() })
}

/**
 * Three clay beads that hop one after another — the wait, in the clay look. With reduced
 * motion they simply rest.
 */
@Composable
fun ClayHopDots(modifier: Modifier = Modifier, bead: Dp = 11.dp) {
    val c = Sadora.colors
    val transition = rememberInfiniteTransition()
    val phase by transition.animateFloatUnlessReduced(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(bead * 0.64f)) {
        val beads = c.heroColors
        repeat(3) { index ->
            // Each bead hops in the first half of its own turn and rests in the second.
            val local = ((phase - index * 0.16f) % 1f + 1f) % 1f
            val hop = if (local < 0.5f) sin(local * 2f * PI.toFloat()) else 0f
            Box(
                Modifier
                    .graphicsLayer { translationY = -hop * bead.toPx() * 0.64f }
                    .size(bead)
                    .clayBeadSurface(c, beads[index * (beads.size - 1) / 2], elevation = 2.dp),
            )
        }
    }
}

private const val CheerMillis = 2_600L
