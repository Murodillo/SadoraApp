package uz.sadora.doctor.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** How much larger a picked colour icon rests than an unpicked one. */
private const val PickedArtScale = 1.1f

/** How much colour an idle icon keeps when [SelectableArt] is asked to dim it. */
private const val IdleSaturation = 0.45f
private const val IdleAlpha = 0.85f

/**
 * A colour icon that jumps when it is picked: it hops, pops past full size and wiggles,
 * then settles a little larger than the unpicked ones, so a picked tile still reads as
 * picked once the motion is over. Unpicking only dips and settles back.
 *
 * [dimWhenIdle] drains most of the colour from an unpicked icon — the tab bar needs
 * that, because five full-colour icons in a row would leave nothing saying which one
 * is open. Answer tiles keep their colour either way; their ring says it.
 *
 * Nothing moves on the first frame — a screen opened with answers already chosen
 * shows them at rest — and under Reduce Motion the size simply changes.
 */
@Composable
fun SelectableArt(
    art: DrawableResource,
    size: Dp,
    selected: Boolean,
    modifier: Modifier = Modifier,
    dimWhenIdle: Boolean = false,
) {
    val reduceMotion = LocalReduceMotion.current
    val scale = remember { Animatable(if (selected) PickedArtScale else 1f) }
    val tilt = remember { Animatable(0f) }
    val hop = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(selected) }

    LaunchedEffect(selected) {
        if (selected == shown) return@LaunchedEffect
        shown = selected
        if (reduceMotion) {
            scale.snapTo(if (selected) PickedArtScale else 1f)
            return@LaunchedEffect
        }
        if (selected) {
            launch {
                scale.animateTo(1.32f, tween(130, easing = FastOutSlowInEasing))
                scale.animateTo(PickedArtScale, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
            }
            launch {
                hop.animateTo(1f, tween(140, easing = FastOutSlowInEasing))
                hop.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
            }
            tilt.animateTo(
                0f,
                keyframes {
                    durationMillis = 520
                    -14f at 90
                    11f at 200
                    -7f at 310
                    3f at 410
                },
            )
        } else {
            launch { tilt.animateTo(0f, tween(120)) }
            scale.animateTo(0.88f, tween(110, easing = FastOutSlowInEasing))
            scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
        }
    }

    val colour by animateFloatAsState(
        targetValue = if (selected || !dimWhenIdle) 1f else 0f,
        animationSpec = tween(if (reduceMotion) 0 else 260),
        label = "art-colour",
    )
    val filter = if (colour >= 1f) null else ColorFilter.colorMatrix(
        ColorMatrix().apply { setToSaturation(IdleSaturation + (1f - IdleSaturation) * colour) },
    )

    val lift = with(LocalDensity.current) { 9.dp.toPx() }
    Image(
        painterResource(art),
        contentDescription = null,
        colorFilter = filter,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                rotationZ = tilt.value
                translationY = -lift * hop.value
                alpha = IdleAlpha + (1f - IdleAlpha) * colour
            },
    )
}
