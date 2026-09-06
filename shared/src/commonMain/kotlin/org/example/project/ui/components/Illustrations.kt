package org.example.project.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Drawn illustrations.
 *
 * The deck's screens carry three kinds of picture: the brand mark, a lotus on the
 * cycle screen, and meal photography. Photography comes from the camera; the mark and
 * its motion live in `Brand.kt`; the rest is drawn here so it scales to any density,
 * follows the theme, and costs the app no image assets. `tools/gen_images.py` can replace the lotus and the meal
 * tiles with generated bitmaps once an image-generation quota is available.
 */

/**
 * The two brand hues the drawings borrow. The mark itself lives in `Brand.kt` and owns
 * the full gradient; these are here for the illustrations that only need a wash of it.
 */
private val MarkPurple = Color(0xFFB06CFF)
private val MarkPink = Color(0xFFF68CCB)

/**
 * The lotus from the cycle screen's "Bugun" card.
 *
 * Three tiers of petals opening from one point at the bottom, pink deepening toward
 * the centre, on a soft radial glow. Nothing here is themed on purpose — the flower
 * is a photograph's stand-in, and a photograph does not change with the theme.
 */
@Composable
fun LotusIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val base = Offset(w * 0.5f, h * 0.82f)
        val r = minOf(w, h) * 0.5f

        drawCircle(
            Brush.radialGradient(
                listOf(Color(0x66FFB1D8), Color(0x00FFB1D8)),
                center = Offset(w * 0.5f, h * 0.52f),
                radius = r * 1.15f,
            ),
            radius = r * 1.15f,
            center = Offset(w * 0.5f, h * 0.52f),
        )

        // Outer tier: wide, pale, leaning outward.
        petalTier(base, r * 0.95f, count = 7, spread = 150f, width = 0.42f, from = Color(0xFFFFD1E6), to = Color(0xFFFF9BCB))
        // Middle tier.
        petalTier(base, r * 0.78f, count = 5, spread = 110f, width = 0.46f, from = Color(0xFFFFB9DB), to = Color(0xFFFF7FC0))
        // Inner tier: the bud.
        petalTier(base, r * 0.56f, count = 3, spread = 60f, width = 0.5f, from = Color(0xFFFF9FD0), to = Color(0xFFFF5FB0))

        // Heart of the flower.
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFFE6A3), Color(0xFFFFC46B)), center = Offset(base.x, base.y - r * 0.18f), radius = r * 0.14f),
            radius = r * 0.11f,
            center = Offset(base.x, base.y - r * 0.18f),
        )
    }
}

/** One fan of petals around [base], [spread] degrees wide and [length] tall. */
private fun DrawScope.petalTier(
    base: Offset,
    length: Float,
    count: Int,
    spread: Float,
    width: Float,
    from: Color,
    to: Color,
) {
    val petal = Path().apply {
        val wHalf = length * width
        moveTo(0f, 0f)
        cubicTo(wHalf, -length * 0.25f, wHalf * 0.8f, -length * 0.85f, 0f, -length)
        cubicTo(-wHalf * 0.8f, -length * 0.85f, -wHalf, -length * 0.25f, 0f, 0f)
        close()
    }
    val step = if (count == 1) 0f else spread / (count - 1)
    repeat(count) { i ->
        val angle = -spread / 2f + step * i
        withTransform({
            translate(base.x, base.y)
            rotate(angle, Offset.Zero)
        }) {
            drawPath(
                petal,
                Brush.verticalGradient(listOf(to, from), startY = -length, endY = 0f),
            )
            drawPath(petal, Color.White.copy(alpha = 0.35f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f))
        }
    }
}

/**
 * The meal photo tile. A warm gradient with the dish's emoji until a real photo
 * exists for the meal.
 */
@Composable
fun MealThumb(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    shape: Shape = Radius.cardSmall,
) {
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFFFE3C4), Color(0xFFFFC2D8)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, style = TextStyle(fontSize = (size.value * 0.5f).sp))
    }
}

/**
 * The brand mark on a dark ground with the deck's ripple of light under it — the
 * header of the AI assistant screen.
 */
@Composable
fun AiMarkHeader(modifier: Modifier = Modifier, markSize: Dp = 96.dp) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h * 0.62f
            // Wide, faint sine waves fanning out under the mark.
            for (i in 0 until 4) {
                val amp = h * (0.05f + i * 0.03f)
                val path = Path()
                var x = 0f
                while (x <= w) {
                    val t = (x - cx) / w * 2f
                    val y = cy + amp * sin(t * PI.toFloat() * 1.6f + i * 0.7f) * (1f - t * t).coerceAtLeast(0f)
                    if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                    x += 6f
                }
                drawPath(
                    path,
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, MarkPurple.copy(alpha = 0.55f - i * 0.1f), MarkPink.copy(alpha = 0.45f - i * 0.1f), Color.Transparent),
                    ),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f),
                )
            }
        }
        // The orb brings its own light, so the header does not paint a second glow
        // under it.
        AiOrb(size = markSize * 1.3f)
    }
}

/**
 * The welcome illustration: a lavender orb with the mark in it and sparkles around.
 * Stands in for the deck's portrait until an image is generated for it.
 */
@Composable
fun WelcomeIllustration(modifier: Modifier = Modifier, colors: SadoraColors = Sadora.colors) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(
                Brush.radialGradient(
                    listOf(MarkPink.copy(alpha = 0.32f), MarkPurple.copy(alpha = 0.22f), Color.Transparent),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
            // Leaves curling up either side.
            listOf(-1f, 1f).forEach { side ->
                val leaf = Path().apply {
                    moveTo(center.x + side * r * 0.55f, center.y + r * 0.55f)
                    quadraticTo(center.x + side * r * 0.95f, center.y + r * 0.1f, center.x + side * r * 0.6f, center.y - r * 0.4f)
                    quadraticTo(center.x + side * r * 0.5f, center.y + r * 0.1f, center.x + side * r * 0.55f, center.y + r * 0.55f)
                    close()
                }
                drawPath(leaf, Brush.verticalGradient(listOf(MarkPurple.copy(alpha = 0.35f), MarkPink.copy(alpha = 0.3f))))
            }
            // Sparkles.
            listOf(Offset(0.82f, 0.18f), Offset(0.2f, 0.28f), Offset(0.78f, 0.78f)).forEachIndexed { i, p ->
                sparkle(Offset(size.width * p.x, size.height * p.y), r * (0.06f + i * 0.015f), colors.primary.copy(alpha = 0.7f))
            }
        }
        SadoraMark(size = 120.dp)
    }
}

/** A four-point star. */
private fun DrawScope.sparkle(center: Offset, radius: Float, color: Color) {
    val path = Path()
    for (i in 0 until 8) {
        val angle = (i * 45f - 90f) * PI.toFloat() / 180f
        val rr = if (i % 2 == 0) radius else radius * 0.32f
        val p = Offset(center.x + cos(angle) * rr, center.y + sin(angle) * rr)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color)
}
