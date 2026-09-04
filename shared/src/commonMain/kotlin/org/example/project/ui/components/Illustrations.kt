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
 * The deck's screens carry three kinds of picture: the brand "S", a lotus on the
 * cycle screen, and meal photography. Photography comes from the camera; the other
 * two are drawn here so they scale to any density, follow the theme, and cost the
 * app no image assets. `tools/gen_images.py` can replace the lotus and the meal
 * tiles with generated bitmaps once an image-generation quota is available.
 */

/** The brand colours the mark is drawn in, whatever the theme. */
private val MarkPurple = Color(0xFF7B61FF)
private val MarkPink = Color(0xFFFF6FB8)
private val MarkCyan = Color(0xFF63D8FF)

/**
 * The SADORA "S": a light serif letter filled with the purple→cyan→pink gradient, and
 * the small dot that sits at its foot in the logo.
 */
@Composable
fun SadoraMark(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    /** Light on dark: the letter inside the AI orb, where the brand purple disappears. */
    light: Boolean = false,
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Text(
            "S",
            style = TextStyle(
                fontSize = (size.value * 0.92f).sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Light,
                brush = Brush.linearGradient(
                    if (light) {
                        listOf(Color.White, Color(0xFFEAE2FF), Color(0xFFFFD9EE))
                    } else {
                        listOf(MarkPurple, MarkCyan, MarkPink)
                    },
                    start = Offset(0f, 0f),
                    end = Offset(200f, 400f),
                ),
            ),
        )
        // The dot belongs to the letter, not to the box: an absolute corner would drift
        // away from the glyph as the mark scales, so it is offset from the centre by a
        // fraction of the size instead.
        Box(
            Modifier
                .offset(x = -size * 0.24f, y = size * 0.30f)
                .size(size * 0.09f)
                .clip(Radius.chip)
                .background(
                    Brush.linearGradient(
                        if (light) listOf(Color.White, Color(0xFFFFD9EE)) else listOf(MarkPurple, MarkPink),
                    ),
                ),
        )
    }
}

/** "S A D O R A" with the tagline under it, as on the splash and the deck's cover. */
@Composable
fun SadoraWordmark(modifier: Modifier = Modifier, tagline: Boolean = true) {
    val c = Sadora.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "SADORA",
            style = Sadora.type.h1.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.42.em,
                fontSize = 26.sp,
            ),
            color = c.text,
        )
        if (tagline) {
            Spacer(Modifier.height(6.dp))
            Text(
                "EVERY WOMAN. EVERY MOMENT.",
                style = Sadora.type.caption.copy(letterSpacing = 0.22.em),
                color = c.textAccent,
            )
        }
    }
}

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
 * The AI orb: the deck's glowing sphere with the brand "S" inside it.
 *
 * Three layers make the glow read as light rather than as a flat circle — a wide halo
 * that breathes, a coloured highlight that drifts slowly around the sphere, and the
 * sphere itself lit from the upper left. Nothing here is themed: the orb is the same
 * object on the light screens and on the navy ones, which is what makes it the app's
 * one recognisable image.
 */
@Composable
fun AiOrb(modifier: Modifier = Modifier, size: Dp = 132.dp) {
    val transition = rememberInfiniteTransition()
    // Slow enough to be felt rather than watched — this runs the whole time the screen
    // is open, and a faster orb turns into a spinner.
    val breath by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(3200), RepeatMode.Reverse),
    )
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
    )

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            val core = r * 0.66f

            // The halo: everything outside the sphere, breathing.
            drawCircle(
                Brush.radialGradient(
                    listOf(MarkPurple.copy(alpha = 0.30f), MarkPink.copy(alpha = 0.12f), Color.Transparent),
                    center = center,
                    radius = r * breath,
                ),
                radius = r * breath,
                center = center,
            )

            // The sphere, lit from the upper left.
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFB9A4FF), Color(0xFF6D4BE8), Color(0xFF2E1A66)),
                    center = Offset(center.x - core * 0.35f, center.y - core * 0.4f),
                    radius = core * 1.7f,
                ),
                radius = core * breath,
                center = center,
            )

            // A coloured highlight travelling around inside it.
            val angle = drift * PI.toFloat() / 180f
            val at = Offset(center.x + cos(angle) * core * 0.42f, center.y + sin(angle) * core * 0.42f)
            drawCircle(
                Brush.radialGradient(
                    listOf(MarkCyan.copy(alpha = 0.5f), MarkPink.copy(alpha = 0.25f), Color.Transparent),
                    center = at,
                    radius = core * 0.85f,
                ),
                radius = core * 0.85f,
                center = at,
            )
        }
        SadoraMark(size = size * 0.46f, light = true)
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
