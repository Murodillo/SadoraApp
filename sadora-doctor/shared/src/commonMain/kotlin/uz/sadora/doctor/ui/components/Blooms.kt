package uz.sadora.doctor.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uz.sadora.doctor.design.Sadora
import kotlin.math.PI
import kotlin.math.sin

// ---------------------------------------------------------------- bloom field

/**
 * One drawn flower.
 *
 * Positions are fractions of the canvas so the field keeps its composition on a
 * small phone and a tablet alike; everything else is in dp and converted at draw
 * time.
 */
private data class Bloom(
    val x: Float,
    val y: Float,
    /** Petal length, dp. */
    val radius: Float,
    val petals: Int,
    /** Resting rotation, degrees. */
    val tilt: Float,
    /** Index into the palette built by [BloomField]. */
    val tone: Int,
    /** 0..1 offset into the shared drift cycle, so no two blooms move together. */
    val phase: Float,
    /** Vertical travel, dp. */
    val bob: Float,
    /** Rotation travel, degrees. */
    val sway: Float,
    /** Stem length in dp; 0 draws no stem. */
    val stem: Float = 0f,
)

/**
 * The composition from the deck's splash: blooms gather in the bottom-left corner
 * and thin out toward the top right, leaving the middle clear for the mark.
 */
private val blooms = listOf(
    Bloom(0.10f, 0.07f, 18f, 5, -12f, 3, 0.00f, 6f, 5f),
    Bloom(0.88f, 0.10f, 16f, 8, 8f, 3, 0.28f, 7f, 4f),
    Bloom(0.92f, 0.30f, 14f, 4, -20f, 2, 0.15f, 6f, 6f),
    Bloom(0.08f, 0.66f, 26f, 6, 18f, 0, 0.55f, 8f, 7f, stem = 30f),
    Bloom(0.20f, 0.78f, 34f, 6, -10f, 0, 0.36f, 9f, 5f),
    Bloom(0.34f, 0.90f, 24f, 5, 22f, 1, 0.71f, 7f, 6f, stem = 28f),
    Bloom(0.12f, 0.92f, 30f, 7, -16f, 1, 0.22f, 10f, 4f),
    Bloom(0.52f, 0.95f, 18f, 5, 12f, 3, 0.50f, 6f, 6f),
    Bloom(0.84f, 0.88f, 22f, 6, -24f, 2, 0.86f, 8f, 5f, stem = 26f),
    Bloom(0.70f, 0.82f, 14f, 5, 30f, 3, 0.93f, 5f, 8f),
)

/** How long one full drift cycle takes. Long and prime-ish so repeats stay unnoticed. */
private const val DriftMillis = 11_000

/**
 * The drifting flower field behind the splash and the onboarding pauses.
 *
 * Two animations run at once and deliberately stay separate: a one-shot entry that
 * fades and scales each bloom in with a stagger, and a single shared infinite phase
 * that every bloom reads at its own offset. Sharing one phase — rather than giving
 * each bloom its own `infiniteRepeatable` — is what keeps the flowers on one frame
 * clock, so nothing stutters against anything else, and driving the motion through
 * `sin` means the loop closes on itself with no seam at the wrap.
 */
@Composable
fun BloomField(
    modifier: Modifier = Modifier,
    entryDelayMillis: Int = 0,
    /** Overall opacity. Below 1 the field reads as a wash behind copy. */
    fieldAlpha: Float = 1f,
) {
    val c = Sadora.colors
    val palette = remember(c) {
        listOf(
            c.secondary.copy(alpha = 0.75f),
            c.primary.copy(alpha = 0.6f),
            c.accent.copy(alpha = 0.5f),
            c.secondary.copy(alpha = 0.35f),
        )
    }
    val core = c.onPrimary.copy(alpha = 0.7f)
    val stemColor = c.primary.copy(alpha = 0.3f)

    val entry = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(entryDelayMillis.toLong())
        entry.animateTo(1f, tween(durationMillis = 1500, easing = FastOutSlowInEasing))
    }

    val drift = rememberInfiniteTransition(label = "bloom-drift")
    val phase by drift.animateFloatUnlessReduced(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(DriftMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "bloom-phase",
    )

    Canvas(modifier) {
        blooms.forEachIndexed { index, bloom ->
            // Stagger: each bloom consumes the same 0..1 entry ramp, offset so the
            // field opens outward instead of appearing all at once.
            val start = index * 0.045f
            val local = ((entry.value - start) / (1f - start)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEachIndexed

            val angle = ((phase + bloom.phase) * 2f * PI).toFloat()
            val bobbing = sin(angle) * bloom.bob.dp.toPx()
            val swaying = sin(angle * 0.7f) * bloom.sway

            drawBloom(
                center = Offset(
                    x = size.width * bloom.x,
                    y = size.height * bloom.y + bobbing,
                ),
                radius = bloom.radius.dp.toPx(),
                petals = bloom.petals,
                rotation = bloom.tilt + swaying,
                color = palette[bloom.tone],
                coreColor = core,
                stem = bloom.stem.dp.toPx(),
                stemColor = stemColor,
                // The last stretch of the ramp is pure fade, so a bloom settles at
                // full size a beat before it reaches full opacity.
                alpha = local * fieldAlpha,
                scale = 0.6f + 0.4f * local,
            )
        }
    }
}

/**
 * One flower: an optional curved stem, [petals] teardrops around a centre disc.
 *
 * Shared with the consent illustration, so the bloom standing in the shield is
 * literally the same flower that drifts across the splash.
 */
internal fun DrawScope.drawBloom(
    center: Offset,
    radius: Float,
    petals: Int,
    rotation: Float,
    color: Color,
    coreColor: Color,
    stem: Float,
    stemColor: Color,
    alpha: Float,
    scale: Float,
) {
    val r = radius * scale

    if (stem > 0f) {
        val path = Path().apply {
            moveTo(center.x, center.y)
            quadraticTo(
                center.x - r * 0.55f,
                center.y + stem * 0.55f,
                center.x - r * 0.2f,
                center.y + stem * scale,
            )
        }
        drawPath(
            path,
            stemColor.copy(alpha = stemColor.alpha * alpha),
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round),
        )
    }

    // Fewer petals read best wide, many petals best narrow, so the width follows
    // the count rather than being a fixed fraction of the radius.
    val width = r * (0.62f - petals * 0.028f).coerceAtLeast(0.24f)
    val petal = Path().apply {
        moveTo(0f, 0f)
        cubicTo(width, -r * 0.22f, width * 0.8f, -r * 0.86f, 0f, -r)
        cubicTo(-width * 0.8f, -r * 0.86f, -width, -r * 0.22f, 0f, 0f)
        close()
    }

    val step = 360f / petals
    repeat(petals) { i ->
        withTransform({
            translate(center.x, center.y)
            rotate(rotation + step * i, Offset.Zero)
        }) {
            drawPath(petal, color.copy(alpha = color.alpha * alpha))
        }
    }

    drawCircle(
        coreColor.copy(alpha = coreColor.alpha * alpha),
        radius = r * 0.19f,
        center = center,
    )
}
