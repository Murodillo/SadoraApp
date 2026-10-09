package uz.sadora.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import uz.sadora.app.resources.Res
import uz.sadora.app.resources.frame_gold_flame
import uz.sadora.app.resources.frame_gold_laurel
import uz.sadora.app.resources.frame_humo_wing
import uz.sadora.app.resources.frame_lavender
import uz.sadora.app.resources.frame_moon
import uz.sadora.app.resources.frame_rainbow
import uz.sadora.app.resources.frame_rose
import uz.sadora.app.resources.frame_sakura
import uz.sadora.app.resources.frame_tulip
import uz.sadora.contract.AvatarFrames

/**
 * An avatar with the frame she wears around it.
 *
 * With no frame — or a key this build does not know, from a frame added after it was
 * made — the avatar is drawn at [size] exactly as before. With one, the avatar shrinks to
 * [InnerFraction] of [size] and the ring fills the rest, so a framed face takes the same
 * room as a plain one and no row moves when she puts a frame on. Ornaments (wings, a
 * bow, flames) may reach a little past [size]; they are drawn, not measured.
 *
 * The animated frames move only from [AnimateFrom] up: in a feed of thirty-two-pixel
 * faces a moving ring is noise, and twenty of them would be work for nothing.
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

/**
 * The frame alone, sized by the avatar box it goes around: for the frames page, the badge
 * unlock, and anywhere a frame is shown without a face.
 *
 * The art is Gemini's (design/frames, cut by tools/cut_frame_sheet.py): each image covers
 * [ArtOver] times the box with the ring's hole at the face's edge. The motion is drawn over
 * it here.
 */
@Composable
fun FrameRing(key: String, animated: Boolean, modifier: Modifier = Modifier) {
    val art = frameArt(key) ?: return
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
    Box(modifier, contentAlignment = Alignment.Center) {
        Image(
            painterResource(art),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val pulse = if (key == AvatarFrames.GOLD_FLAME) 1f + 0.025f * sin(phase * 6f * PI.toFloat()) else 1f
                scaleX = ArtOver * pulse
                scaleY = ArtOver * pulse
                // The rainbow is the one frame round enough to turn.
                rotationZ = if (key == AvatarFrames.RAINBOW) phase * 360f else 0f
            },
        )
        if (animated) Canvas(Modifier.fillMaxSize()) { drawMotion(key, phase) }
    }
}

/** How much of the frame's box the face keeps. */
const val InnerFraction = 0.82f

/** The smallest frame that animates. */
val AnimateFrom: Dp = 40.dp

/** The art covers this many avatar boxes; see tools/cut_frame_sheet.py (OVER). */
private const val ArtOver = 1.3f

private const val LoopMillis = 6_000

private fun frameArt(key: String): DrawableResource? = when (key) {
    AvatarFrames.TULIP -> Res.drawable.frame_tulip
    AvatarFrames.LAVENDER -> Res.drawable.frame_lavender
    AvatarFrames.SAKURA -> Res.drawable.frame_sakura
    AvatarFrames.MOON -> Res.drawable.frame_moon
    AvatarFrames.ROSE -> Res.drawable.frame_rose
    AvatarFrames.GOLD_FLAME -> Res.drawable.frame_gold_flame
    AvatarFrames.GOLD_LAUREL -> Res.drawable.frame_gold_laurel
    AvatarFrames.RAINBOW -> Res.drawable.frame_rainbow
    AvatarFrames.HUMO_WING -> Res.drawable.frame_humo_wing
    else -> null
}

// ---------------------------------------------------------------- motion

/**
 * The life on top of an animated frame: glints that come and go along the ring, and on
 * the gold ones a shine that travels round it. [phase] runs 0..1 once a loop.
 */
private fun DrawScope.drawMotion(key: String, phase: Float) {
    val box = size.minDimension
    // The band's middle: the hole sits at 0.40 of the box, the band is about 0.07 thick.
    val ring = box * 0.435f
    val glint = box * 0.055f
    when (key) {
        AvatarFrames.ROSE -> glints(ring, glint, phase, listOf(60f, 180f, 300f), drift = 40f)
        AvatarFrames.GOLD_FLAME -> glints(ring * 1.08f, glint, phase, listOf(20f, 140f, 260f), drift = 60f, color = Color(0xFFFFF2B3))
        AvatarFrames.GOLD_LAUREL -> {
            shine(ring, box * 0.03f, phase)
            glints(ring, glint, phase, listOf(150f, 30f), drift = 0f, color = Color(0xFFFFF2B3))
        }
        AvatarFrames.RAINBOW -> glints(ring, glint, phase, listOf(40f, 160f, 280f), drift = -90f)
        AvatarFrames.HUMO_WING -> {
            shine(ring, box * 0.035f, phase)
            // The gem at the top catches the light.
            glints(ring * 1.12f, glint * 1.2f, phase, listOf(270f), drift = 0f, color = Color(0xFFB8FFF6))
        }
    }
}

private fun DrawScope.glints(radius: Float, size: Float, phase: Float, angles: List<Float>, drift: Float, color: Color = Color.White) {
    angles.forEachIndexed { i, base ->
        val local = (phase * 2f + i / angles.size.toFloat()) % 1f
        val twinkle = sin(local * PI.toFloat())
        val a = ((base + drift * phase) * PI / 180.0).toFloat()
        sparkle(Offset(center.x + radius * cos(a), center.y + radius * sin(a)), size * twinkle, color.copy(alpha = twinkle))
    }
}

/** A four-pointed glint. */
private fun DrawScope.sparkle(at: Offset, size: Float, color: Color) {
    if (size <= 0f) return
    val k = size * 0.22f
    val path = Path().apply {
        moveTo(at.x, at.y - size)
        quadraticTo(at.x + k, at.y - k, at.x + size, at.y)
        quadraticTo(at.x + k, at.y + k, at.x, at.y + size)
        quadraticTo(at.x - k, at.y + k, at.x - size, at.y)
        quadraticTo(at.x - k, at.y - k, at.x, at.y - size)
        close()
    }
    drawPath(path, color)
}

/** A soft bright arc travelling round the band once a loop. */
private fun DrawScope.shine(radius: Float, width: Float, phase: Float) {
    val corner = Offset(center.x - radius, center.y - radius)
    drawArc(
        Color.White.copy(alpha = 0.55f),
        phase * 360f,
        26f,
        false,
        corner,
        Size(radius * 2, radius * 2),
        style = Stroke(width, cap = StrokeCap.Round),
    )
}
