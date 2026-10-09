package uz.sadora.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import uz.sadora.contract.AvatarFrames

/**
 * An avatar with the frame she wears around it.
 *
 * With no frame — or a key this build does not know, from a frame added after it was
 * made — the avatar is drawn at [size] exactly as before. With one, the avatar shrinks to
 * [InnerFraction] of [size] and the ring fills the rest, so a framed face takes the same
 * room as a plain one and no row moves when she puts a frame on.
 *
 * The animated frames move only from [AnimateFrom] up: in a feed of thirty-two-pixel
 * faces a spinning ring is noise, and twenty of them would be work for nothing.
 */
@Composable
fun FramedAvatar(
    frame: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (inner: Dp) -> Unit,
) {
    val entry = AvatarFrames.byKey(frame)
    if (entry == null) {
        Box(modifier.size(size), contentAlignment = Alignment.Center) { content(size) }
        return
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        content(size * InnerFraction)
        FrameRing(entry.key, animated = entry.animated && size >= AnimateFrom, modifier = Modifier.fillMaxSize())
    }
}

/** The ring alone, for the frames page's tiles and anywhere a frame is shown without a face. */
@Composable
fun FrameRing(key: String, animated: Boolean, modifier: Modifier = Modifier) {
    val phase = if (animated) {
        val transition = rememberInfiniteTransition(label = "frame")
        val value by transition.animateFloatUnlessReduced(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(LoopMillis, easing = LinearEasing), RepeatMode.Restart),
            label = "framePhase",
        )
        value
    } else {
        0f
    }
    Canvas(modifier) { drawFrame(key, phase) }
}

/** How much of the frame's box the face keeps. */
const val InnerFraction = 0.82f

/** The smallest frame that animates. */
val AnimateFrom: Dp = 40.dp

private const val LoopMillis = 6_000

// ---------------------------------------------------------------- drawing

/**
 * One frame, drawn in the clay look of the app's icons: a soft band lit from the top
 * left, darker toward the bottom right, with its ornaments on top. [phase] runs 0..1 for
 * the animated ones and stays 0 for the rest.
 */
private fun DrawScope.drawFrame(key: String, phase: Float) {
    val outer = size.minDimension / 2f
    val inner = outer * InnerFraction
    val band = outer - inner
    val mid = (outer + inner) / 2f
    val ring = Ring(center, mid, band, outer)
    when (key) {
        AvatarFrames.TULIP -> {
            ring.band(this, listOf(Color(0xFFFFB3C4), Color(0xFFF06A86), Color(0xFFFFB3C4)))
            listOf(65f, 90f, 115f).forEachIndexed { i, angle ->
                val at = ring.at(angle, outer * 0.02f)
                leaf(at, band * 1.3f, angle + if (i == 1) 0f else (angle - 90f) * 1.4f, Color(0xFF6FBF73))
            }
            listOf(68f to 0xFFE53958, 90f to 0xFFFF7A59, 112f to 0xFFEC4F8C).forEach { (angle, color) ->
                tulip(ring.at(angle, -band * 0.1f), band * 0.95f, Color(color))
            }
        }
        AvatarFrames.LAVENDER -> {
            ring.band(this, listOf(Color(0xFFDCCBFA), Color(0xFF9B7BE0), Color(0xFFDCCBFA)))
            listOf(125f, 55f).forEach { angle -> lavenderSprig(ring, angle, band) }
        }
        AvatarFrames.SAKURA -> {
            ring.band(this, listOf(Color(0xFFFFE1EA), Color(0xFFF7A8C0), Color(0xFFFFE1EA)))
            listOf(30f to 1f, 150f to 0.85f, 250f to 0.75f, 320f to 1f).forEach { (angle, scale) ->
                flower(ring.at(angle), band * 0.85f * scale, 5, Color(0xFFFFC2D4), Color(0xFFF06292))
            }
        }
        AvatarFrames.MOON -> {
            ring.band(this, listOf(Color(0xFF3A4A8C), Color(0xFF1F2A5C), Color(0xFF6B7BD6), Color(0xFF3A4A8C)))
            crescent(ring.at(305f), band * 1.25f, Color(0xFFFFD970))
            listOf(20f, 70f, 140f, 200f, 245f).forEachIndexed { i, angle ->
                sparkle(ring.at(angle), band * (if (i % 2 == 0) 0.45f else 0.3f), Color(0xFFFFF4C2))
            }
        }
        AvatarFrames.ROSE -> {
            ring.band(this, listOf(Color(0xFFFF8FB1), Color(0xFFC2185B), Color(0xFFFF8FB1)))
            val turn = phase * 360f
            listOf(0f, 120f, 240f).forEach { base -> rose(ring.at(base + turn), band * 0.95f) }
            listOf(60f, 180f, 300f).forEachIndexed { i, base ->
                val twinkle = twinkle(phase, i)
                sparkle(ring.at(base + turn), band * 0.4f * twinkle, Color.White.copy(alpha = 0.9f * twinkle))
            }
        }
        AvatarFrames.GOLD_FLAME -> {
            val flames = 12
            for (i in 0 until flames) {
                val angle = i * 360f / flames + phase * 30f
                val flicker = 0.75f + 0.25f * sin((phase * 4f + i * 0.37f) * 2f * PI.toFloat())
                flame(ring.at(angle, -band * 0.1f), band * 0.85f * flicker, angle, Color(0xFFFF8F00), Color(0xFFFFD54F))
            }
            rotate(phase * 360f) {
                ring.band(this, listOf(Color(0xFFFFE082), Color(0xFFFF8F00), Color(0xFFFFC107), Color(0xFFFF6F00), Color(0xFFFFE082)))
            }
        }
        AvatarFrames.GOLD_LAUREL -> {
            ring.band(this, listOf(Color(0xFFFFE9A8), Color(0xFFC9971C), Color(0xFFFFE9A8)), width = band * 0.55f)
            val leaves = 7
            for (i in 0 until leaves) {
                val left = 110f + i * 20f
                val right = 70f - i * 20f
                leaf(ring.at(left), band * 1.25f, left + 120f, Color(0xFFE0B232))
                leaf(ring.at(right), band * 1.25f, right - 120f, Color(0xFFE0B232))
            }
            shine(ring, phase, Color.White)
        }
        AvatarFrames.RAINBOW -> {
            rotate(phase * 360f) {
                ring.band(
                    this,
                    listOf(
                        Color(0xFFFF6B6B), Color(0xFFFFB86B), Color(0xFFFFE66B), Color(0xFF7BE07B),
                        Color(0xFF6BC5FF), Color(0xFFA78BFA), Color(0xFFFF6B6B),
                    ),
                )
            }
            listOf(40f, 160f, 280f).forEachIndexed { i, angle ->
                val twinkle = twinkle(phase, i)
                sparkle(ring.at(angle - phase * 120f), band * 0.55f * twinkle, Color.White.copy(alpha = twinkle))
            }
        }
        AvatarFrames.HUMO_WING -> {
            val spread = 0.9f + 0.1f * sin(phase * 2f * PI.toFloat())
            for (i in 0 until 5) {
                val left = 105f + i * 14f * spread
                val right = 75f - i * 14f * spread
                val color = if (i % 2 == 0) Color(0xFFFFC94D) else Color(0xFF2EC4B6)
                leaf(ring.at(left, band * 0.1f), band * (1.5f - i * 0.15f), left, color)
                leaf(ring.at(right, band * 0.1f), band * (1.5f - i * 0.15f), right, color)
            }
            rotate(phase * 360f) {
                ring.band(this, listOf(Color(0xFFFFE08A), Color(0xFF2EC4B6), Color(0xFFFFC94D), Color(0xFF1B9AAA), Color(0xFFFFE08A)))
            }
            sparkle(ring.at(270f), band * 0.7f, Color(0xFFFFF4C2))
        }
        else -> ring.band(this, listOf(Color.LightGray, Color.Gray, Color.LightGray))
    }
}

/** The ring's geometry: [mid] is the middle of the band, angles in degrees clockwise from three o'clock. */
private class Ring(val center: Offset, val mid: Float, val band: Float, val outer: Float) {
    fun at(degrees: Float, outward: Float = 0f): Offset {
        val r = (degrees * PI / 180.0).toFloat()
        return Offset(center.x + (mid + outward) * cos(r), center.y + (mid + outward) * sin(r))
    }

    /** The band itself, lit from the top left. */
    fun band(scope: DrawScope, colors: List<Color>, width: Float = band) = with(scope) {
        drawCircle(Brush.sweepGradient(colors, center), radius = mid, center = center, style = Stroke(width))
        // Clay: a soft light along the top left and a shade along the bottom right.
        val box = Size(mid * 2, mid * 2)
        val corner = Offset(center.x - mid, center.y - mid)
        drawArc(Color.White.copy(alpha = 0.45f), 195f, 80f, false, corner, box, style = Stroke(width * 0.28f, cap = StrokeCap.Round))
        drawArc(Color.Black.copy(alpha = 0.12f), 15f, 80f, false, corner, box, style = Stroke(width * 0.35f, cap = StrokeCap.Round))
    }
}

/** 0..1..0 once a loop, offset per ornament so they do not blink together. */
private fun twinkle(phase: Float, index: Int): Float {
    val local = (phase * 2f + index / 3f) % 1f
    return 0.35f + 0.65f * sin(local * PI.toFloat())
}

private fun DrawScope.leaf(at: Offset, length: Float, degrees: Float, color: Color) {
    rotate(degrees, pivot = at) {
        drawOval(color, Offset(at.x - length / 2f, at.y - length / 5f), Size(length, length / 2.5f))
        drawOval(Color.White.copy(alpha = 0.3f), Offset(at.x - length / 3f, at.y - length / 8f), Size(length / 2f, length / 8f))
    }
}

private fun DrawScope.tulip(at: Offset, size: Float, color: Color) {
    val w = size * 0.42f
    drawOval(color, Offset(at.x - w * 1.1f, at.y - size * 0.45f), Size(w * 1.2f, size * 0.9f))
    drawOval(color, Offset(at.x - w * 0.1f, at.y - size * 0.45f), Size(w * 1.2f, size * 0.9f))
    drawOval(color.copy(red = (color.red * 1.1f).coerceAtMost(1f)), Offset(at.x - w * 0.55f, at.y - size * 0.55f), Size(w * 1.1f, size))
    drawOval(Color.White.copy(alpha = 0.35f), Offset(at.x - w * 0.35f, at.y - size * 0.4f), Size(w * 0.35f, size * 0.4f))
}

private fun DrawScope.lavenderSprig(ring: Ring, angle: Float, band: Float) {
    leaf(ring.at(angle + 6f, band * 0.2f), band * 1.4f, angle + 90f, Color(0xFF8DBF7A))
    for (i in 0 until 6) {
        val at = ring.at(angle - 4f - i * 6.5f, band * (0.1f + (i % 2) * 0.25f))
        drawCircle(Color(0xFF8E5CD9), radius = band * (0.32f - i * 0.025f), center = at)
        drawCircle(Color.White.copy(alpha = 0.35f), radius = band * 0.1f, center = at + Offset(-band * 0.08f, -band * 0.08f))
    }
}

private fun DrawScope.flower(at: Offset, size: Float, petals: Int, petal: Color, heart: Color) {
    val r = size * 0.32f
    for (i in 0 until petals) {
        val a = (i * 2 * PI / petals - PI / 2).toFloat()
        drawCircle(petal, radius = r, center = Offset(at.x + cos(a) * r, at.y + sin(a) * r))
    }
    drawCircle(Color.White.copy(alpha = 0.4f), radius = r * 0.5f, center = at + Offset(-r * 0.5f, -r * 0.9f))
    drawCircle(heart, radius = r * 0.45f, center = at)
}

private fun DrawScope.rose(at: Offset, size: Float) {
    drawCircle(Color(0xFFAD1457), radius = size * 0.5f, center = at)
    drawCircle(Color(0xFFE91E63), radius = size * 0.36f, center = at + Offset(-size * 0.04f, -size * 0.04f))
    drawCircle(Color(0xFFF48FB1), radius = size * 0.2f, center = at + Offset(-size * 0.06f, -size * 0.08f))
    drawCircle(Color.White.copy(alpha = 0.45f), radius = size * 0.08f, center = at + Offset(-size * 0.16f, -size * 0.2f))
}

private fun DrawScope.crescent(at: Offset, size: Float, color: Color) {
    val r = size / 2f
    val path = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(at.x - r, at.y - r, at.x + r, at.y + r))
    }
    val bite = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(at.x - r * 0.45f, at.y - r * 1.2f, at.x + r * 1.35f, at.y + r * 0.6f))
    }
    val moon = Path().apply { op(path, bite, androidx.compose.ui.graphics.PathOperation.Difference) }
    drawPath(moon, color)
    drawCircle(Color.White.copy(alpha = 0.4f), radius = r * 0.18f, center = at + Offset(-r * 0.55f, r * 0.1f))
}

/** A four-pointed glint. */
private fun DrawScope.sparkle(at: Offset, size: Float, color: Color) {
    if (size <= 0f) return
    val s = size
    val k = s * 0.22f
    val path = Path().apply {
        moveTo(at.x, at.y - s)
        quadraticTo(at.x + k, at.y - k, at.x + s, at.y)
        quadraticTo(at.x + k, at.y + k, at.x, at.y + s)
        quadraticTo(at.x - k, at.y + k, at.x - s, at.y)
        quadraticTo(at.x - k, at.y - k, at.x, at.y - s)
        close()
    }
    drawPath(path, color)
}

/** A flame tongue pointing outward along [degrees]. */
private fun DrawScope.flame(at: Offset, length: Float, degrees: Float, outer: Color, inner: Color) {
    rotate(degrees + 90f, pivot = at) {
        val w = length * 0.45f
        val tongue = Path().apply {
            moveTo(at.x, at.y - length)
            quadraticTo(at.x + w, at.y - length * 0.3f, at.x, at.y + length * 0.2f)
            quadraticTo(at.x - w, at.y - length * 0.3f, at.x, at.y - length)
            close()
        }
        drawPath(tongue, outer)
        scale(0.55f, pivot = at) { drawPath(tongue, inner) }
    }
}

/** A bright arc travelling round the band once a loop. */
private fun DrawScope.shine(ring: Ring, phase: Float, color: Color) {
    val box = Size(ring.mid * 2, ring.mid * 2)
    val corner = Offset(ring.center.x - ring.mid, ring.center.y - ring.mid)
    drawArc(color.copy(alpha = 0.7f), phase * 360f, 28f, false, corner, box, style = Stroke(ring.band * 0.35f, cap = StrokeCap.Round))
}
