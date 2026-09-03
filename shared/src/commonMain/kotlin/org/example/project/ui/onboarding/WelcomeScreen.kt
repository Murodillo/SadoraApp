package org.example.project.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import org.example.project.design.Sadora
import org.example.project.design.Spacing
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
 * The composition from the reference: blooms crowd the corners and the top and
 * bottom edges, and the middle band is left clear for the headline.
 */
private val blooms = listOf(
    Bloom(0.10f, 0.07f, 26f, 5, -12f, 0, 0.00f, 7f, 5f, stem = 36f),
    Bloom(0.21f, 0.12f, 19f, 5, 24f, 0, 0.62f, 5f, 6f, stem = 26f),
    Bloom(0.88f, 0.08f, 22f, 8, 8f, 3, 0.28f, 8f, 4f, stem = 42f),
    Bloom(0.55f, 0.18f, 27f, 6, -6f, 2, 0.45f, 9f, 5f),
    Bloom(0.12f, 0.30f, 17f, 6, 30f, 3, 0.80f, 6f, 7f),
    Bloom(0.92f, 0.26f, 24f, 4, -20f, 0, 0.15f, 8f, 6f, stem = 30f),
    Bloom(0.71f, 0.35f, 13f, 5, 14f, 1, 0.93f, 5f, 8f),
    Bloom(0.30f, 0.64f, 15f, 4, -28f, 3, 0.36f, 6f, 7f),
    Bloom(0.07f, 0.67f, 19f, 3, 18f, 2, 0.55f, 7f, 9f, stem = 26f),
    Bloom(0.87f, 0.71f, 25f, 6, -10f, 3, 0.08f, 9f, 5f),
    Bloom(0.52f, 0.79f, 21f, 7, 22f, 1, 0.71f, 7f, 6f, stem = 32f),
    Bloom(0.18f, 0.89f, 29f, 6, -16f, 0, 0.22f, 10f, 4f, stem = 38f),
    Bloom(0.80f, 0.91f, 22f, 5, 12f, 0, 0.50f, 8f, 6f),
    Bloom(0.37f, 0.96f, 16f, 5, -24f, 3, 0.86f, 6f, 8f),
)

/** How long one full drift cycle takes. Long and prime-ish so repeats stay unnoticed. */
private const val DriftMillis = 11_000

/**
 * The drifting flower field behind the welcome copy.
 *
 * Two animations run at once and deliberately stay separate: a one-shot entry that
 * fades and scales each bloom in with a stagger, and a single shared infinite phase
 * that every bloom reads at its own offset. Sharing one phase — rather than giving
 * each bloom its own `infiniteRepeatable` — is what keeps fourteen flowers on one
 * frame clock, so nothing stutters against anything else, and driving the motion
 * through `sin` means the loop closes on itself with no seam at the wrap.
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
            c.primary,
            c.secondary.copy(alpha = 0.85f),
            c.accent.copy(alpha = 0.6f),
            c.line,
        )
    }
    val core = c.text.copy(alpha = if (c.isDark) 0.55f else 0.8f)
    val stemColor = c.muted2.copy(alpha = 0.45f)

    val entry = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(entryDelayMillis.toLong())
        entry.animateTo(1f, tween(durationMillis = 1500, easing = FastOutSlowInEasing))
    }

    val drift = rememberInfiniteTransition(label = "bloom-drift")
    val phase by drift.animateFloat(
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
 * literally the same flower that drifts across the welcome screen.
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

// ---------------------------------------------------------------- welcome

/**
 * First screen of the app: the bloom field drifts in behind a headline that
 * assembles itself line by line.
 *
 * It doubles as the splash — [onReady] fires once the reveal has played out, and
 * the caller holds here until the stored session has resolved too.
 */
@Composable
fun WelcomeScreen(onReady: () -> Unit, modifier: Modifier = Modifier) {
    val c = Sadora.colors

    LaunchedEffect(Unit) {
        delay(3000)
        onReady()
    }

    Box(modifier.fillMaxSize()) {
        BloomField(Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RevealLine(delayMillis = 250) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("#", style = Sadora.type.h1, color = c.text)
                    Text("1", style = Sadora.type.data, color = c.text)
                    Spacer(Modifier.padding(horizontal = 3.dp))
                    Text(
                        "ayollar uchun",
                        style = Sadora.type.h1,
                        color = c.text,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            RevealLine(delayMillis = 620) {
                Text(
                    "Shifokorlar tavsiya qiladi",
                    style = Sadora.type.h1,
                    color = c.text,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(Spacing.xxs))
            RevealLine(delayMillis = 980) {
                Text(
                    "sikl, homiladorlik va salomatlikni kuzatish ilovasi",
                    style = Sadora.type.h2,
                    color = c.text,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(Spacing.md))
            RevealLine(delayMillis = 1400) {
                Text(
                    "SADORA — har bir ayol. Har bir lahza.",
                    style = Sadora.type.body,
                    color = c.muted2,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Fades and lifts [content] into place after [delayMillis].
 *
 * The lift is applied as a layer translation rather than padding so the reveal
 * never reflows the column underneath it — every line holds its final position
 * from the first frame, and only the pixels move.
 */
@Composable
private fun RevealLine(
    delayMillis: Int,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        progress.animateTo(1f, tween(durationMillis = 650, easing = FastOutSlowInEasing))
    }
    val lift = with(LocalDensity.current) { 14.dp.toPx() }
    Box(
        Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * lift
        },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
