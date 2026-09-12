package uz.sadora.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import uz.sadora.app.design.Sadora

/**
 * Gul — the currency — drawn as a flower.
 *
 * Every mark in the app is drawn rather than shipped, so it is crisp at 14dp and at
 * 34dp and takes the brand's own colours in both themes. Five petals on a warm core:
 * pink to purple so it reads as SADORA's flower and not as a generic daisy.
 *
 * [bloom] is the moment it is earned: the petals open from the centre and the mark
 * settles with a small overshoot. It plays once when the composable first appears with
 * [bloom] true, and again whenever [bloomKey] changes — the balance pill passes the
 * balance, so a coin arriving makes the flower open again.
 */
@Composable
fun GulMark(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    bloom: Boolean = false,
    bloomKey: Any? = null,
) {
    val c = Sadora.colors
    val open = remember { Animatable(if (bloom) 0f else 1f) }
    LaunchedEffect(bloomKey, bloom) {
        if (!bloom) return@LaunchedEffect
        open.snapTo(0f)
        open.animateTo(1f, Motion.Springy)
    }
    val petal = c.secondary
    val petalEdge = c.primary
    val core = Color(0xFFFFD166)
    Box(
        modifier
            .size(size)
            .drawBehind {
                val progress = open.value.coerceIn(0f, 1.15f)
                drawGul(
                    center = center,
                    radius = this.size.minDimension / 2f,
                    scale = progress,
                    rotation = (1f - progress.coerceAtMost(1f)) * 40f,
                    petal = petal,
                    petalEdge = petalEdge,
                    core = core,
                    alpha = 1f,
                )
            },
    )
}

/** One flower: five petals lit pink-to-purple, a warm core. Shared by the mark and the animations. */
internal fun DrawScope.drawGul(
    center: Offset,
    radius: Float,
    scale: Float,
    rotation: Float,
    petal: Color,
    petalEdge: Color,
    core: Color,
    alpha: Float,
) {
    val r = radius * scale
    if (r <= 0f) return
    val width = r * 0.58f
    val shape = Path().apply {
        moveTo(0f, 0f)
        cubicTo(width, -r * 0.25f, width * 0.85f, -r * 0.88f, 0f, -r)
        cubicTo(-width * 0.85f, -r * 0.88f, -width, -r * 0.25f, 0f, 0f)
        close()
    }
    repeat(PETALS) { index ->
        withTransform({
            translate(center.x, center.y)
            rotate(rotation + index * (360f / PETALS), Offset.Zero)
        }) {
            drawPath(shape, petalEdge.copy(alpha = alpha * 0.9f))
            withTransform({ scale(0.78f, 0.78f, Offset.Zero) }) {
                drawPath(shape, petal.copy(alpha = alpha))
            }
        }
    }
    drawCircle(core.copy(alpha = alpha), radius = r * 0.24f, center = center)
    drawCircle(Color.White.copy(alpha = alpha * 0.55f), radius = r * 0.09f, center = center.copy(x = center.x - r * 0.06f, y = center.y - r * 0.07f))
}

private const val PETALS = 5

/**
 * The petal burst behind a celebration: petals fly out from the centre, tumble, and
 * fade — a milestone's worth of flowers rather than twelve rays.
 *
 * Drawn from one [progress] clock so it moves with the ring and the number beside it.
 * Each petal's angle, distance and spin are decided once from a seed, so the burst is
 * the same shape every time it is drawn at the same progress and never jitters.
 */
@Composable
fun PetalBurst(progress: Float, modifier: Modifier = Modifier, count: Int = 14) {
    val c = Sadora.colors
    val petals = remember(count) {
        val random = Random(7)
        List(count) { index ->
            Petal(
                angle = (index.toFloat() / count) * 2f * PI.toFloat() + random.nextFloat() * 0.3f,
                reach = 0.55f + random.nextFloat() * 0.45f,
                spin = (random.nextFloat() - 0.5f) * 540f,
                size = 0.06f + random.nextFloat() * 0.05f,
                delay = random.nextFloat() * 0.18f,
            )
        }
    }
    Box(
        modifier.drawBehind {
            val half = size.minDimension / 2f
            petals.forEachIndexed { index, petal ->
                val local = ((progress - petal.delay) / (1f - petal.delay)).coerceIn(0f, 1f)
                if (local <= 0f) return@forEachIndexed
                // Quick out, slow settle: the petals leave the centre fast and drift.
                val eased = 1f - (1f - local) * (1f - local)
                val distance = half * (0.25f + petal.reach * eased)
                val position = Offset(
                    center.x + cos(petal.angle) * distance,
                    // A little gravity as they fade, so they fall rather than hang.
                    center.y + sin(petal.angle) * distance + half * 0.18f * local * local,
                )
                val alpha = (1f - local).coerceIn(0f, 1f)
                drawGul(
                    center = position,
                    radius = half * petal.size,
                    scale = 0.7f + 0.3f * eased,
                    rotation = petal.spin * eased,
                    petal = if (index % 2 == 0) c.secondary else c.primary,
                    petalEdge = if (index % 2 == 0) c.primary else c.secondary,
                    core = Color(0xFFFFD166),
                    alpha = alpha,
                )
            }
        },
    )
}

private class Petal(val angle: Float, val reach: Float, val spin: Float, val size: Float, val delay: Float)

/**
 * A slow shower of petals over the whole screen — the once-a-day celebration's ground,
 * and the milestone's reward. Runs while [visible]; each petal falls on its own clock so
 * the shower never repeats visibly, and it is cheap: one draw call per frame.
 */
@Composable
fun PetalShower(visible: Boolean, modifier: Modifier = Modifier, count: Int = 18) {
    if (!visible) return
    val c = Sadora.colors
    val transition = rememberInfiniteTransition(label = "petal-shower")
    val clock by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "petal-clock",
    )
    val petals = remember(count) {
        val random = Random(21)
        List(count) {
            Falling(
                x = random.nextFloat(),
                phase = random.nextFloat(),
                sway = 0.02f + random.nextFloat() * 0.04f,
                size = 0.018f + random.nextFloat() * 0.02f,
                spin = (random.nextFloat() - 0.5f) * 720f,
                speed = 0.7f + random.nextFloat() * 0.6f,
            )
        }
    }
    Canvas(modifier.fillMaxSize()) {
        petals.forEachIndexed { index, petal ->
            val t = ((clock * petal.speed + petal.phase) % 1f)
            val y = -0.05f * size.height + t * size.height * 1.1f
            val x = petal.x * size.width + sin(t * 2f * PI.toFloat() * 2f + petal.phase * 6f) * petal.sway * size.width
            val alpha = when {
                t < 0.1f -> t / 0.1f
                t > 0.85f -> (1f - t) / 0.15f
                else -> 1f
            } * 0.85f
            drawGul(
                center = Offset(x, y),
                radius = size.minDimension * petal.size,
                scale = 1f,
                rotation = petal.spin * t,
                petal = if (index % 3 == 0) c.primary else c.secondary,
                petalEdge = if (index % 3 == 0) c.secondary else c.primary,
                core = Color(0xFFFFD166),
                alpha = alpha,
            )
        }
    }
}

private class Falling(val x: Float, val phase: Float, val sway: Float, val size: Float, val spin: Float, val speed: Float)

/**
 * Remembers whether [value] went up since the last composition, for one frame's worth
 * of state: the balance pill uses it to bloom only on a gain, never on a spend.
 */
@Composable
fun rememberGainKey(value: Int): Int {
    var last by remember { mutableIntStateOf(value) }
    var key by remember { mutableIntStateOf(0) }
    if (value > last) key++
    if (value != last) last = value
    return key
}
