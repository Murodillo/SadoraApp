package uz.sadora.app.ui.components

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import uz.sadora.app.design.Sadora
import uz.sadora.app.resources.*
import uz.sadora.contract.Badges

/**
 * A badge, drawn as a medal: a metal rim, a soft face, and the 3D icon living on it.
 *
 * Three things move, each on its own clock so a board of fifteen never pulses in step:
 * the icon has a gesture of its own (the flame flickers, the drop bobs and squashes, the
 * hearts beat lub-dub), the rim's metal catches a slowly turning light, and a band of
 * shine crosses the face every few seconds. A gold or single-moment badge also has
 * sparkles twinkling at its edge, so the tiers read apart before the label does.
 *
 * Locked, it is a pale disc with the icon in grey and the way to its first tier drawn as
 * an arc around the rim — and it holds still: something she has not earned yet should
 * not compete for her eye with what she has.
 *
 * Every transform is read inside a `graphicsLayer` or draw lambda, so the animation
 * redraws without recomposing; and [LocalReduceMotion] stops every loop.
 */
@Composable
fun BadgeMedal(
    key: String,
    tier: Int,
    maxTier: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    /** Toward the first tier, drawn on a locked medal's rim. */
    progress: Float = 0f,
    animate: Boolean = true,
    /** Desynchronises neighbours on a board: pass the medal's index. */
    phase: Int = 0,
    /** The glow behind it; off where the medal sits inline beside text. */
    halo: Boolean = true,
) {
    val c = Sadora.colors
    val metal = BadgeMetal.of(tier, maxTier)
    val palette = metal.palette(c.isDark)
    val earned = metal != BadgeMetal.Locked
    val moving = animate && earned && !LocalReduceMotion.current
    val gesture = remember(key) { BadgeGesture.of(key) }

    // One loop for the icon's own gesture, one slower one for the light on the metal.
    val transition = rememberInfiniteTransition(label = "badge-$key")
    val clock: State<Float> = if (moving) {
        transition.animateFloat(
            0f, 1f,
            loop(gesture.periodMillis, phase * 173),
            label = "badge-gesture",
        )
    } else remember { mutableFloatStateOf(0f) }
    val light: State<Float> = if (moving) {
        transition.animateFloat(0f, 1f, loop(SHINE_PERIOD, phase * 411), label = "badge-light")
    } else remember { mutableFloatStateOf(0.5f) }

    val track = c.line
    val arc = c.primary
    val faceBase = c.surface

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        // The glow behind the medal breathes with the light clock.
        if (earned && halo) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val pulse = 0.5f + 0.5f * sin(light.value * TAU * 2f)
                        drawCircle(
                            Brush.radialGradient(
                                0f to palette.glow.copy(alpha = 0.42f + 0.18f * pulse),
                                0.7f to palette.glow.copy(alpha = 0.10f),
                                1f to Color.Transparent,
                                center = center,
                                radius = this.size.minDimension * 0.62f,
                            ),
                            radius = this.size.minDimension * 0.62f,
                        )
                    },
            )
        }

        // The medal itself: rim, face, bevel and the shine band, in one draw.
        Canvas(
            Modifier
                .size(size * 0.92f)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            val r = this.size.minDimension / 2f
            val faceR = r * 0.84f
            if (earned) {
                rotate(light.value * 360f) {
                    drawCircle(
                        Brush.sweepGradient(
                            listOf(palette.light, palette.dark, palette.mid, palette.light, palette.dark, palette.mid, palette.light),
                            center,
                        ),
                        radius = r,
                    )
                }
                // A thin highlight on the rim's top edge: the metal is lit from above.
                drawCircle(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                        startY = 0f, endY = r,
                    ),
                    radius = r,
                    style = Stroke(r * 0.05f),
                )
                drawCircle(faceBase, radius = faceR)
                drawCircle(
                    Brush.radialGradient(
                        listOf(lerp(palette.light, Color.White, if (c.isDark) 0.1f else 0.55f), palette.face),
                        center = Offset(center.x - r * 0.3f, center.y - r * 0.35f),
                        radius = faceR * 1.6f,
                    ),
                    radius = faceR,
                )
                // The bevel between rim and face.
                drawCircle(palette.dark.copy(alpha = 0.35f), radius = faceR, style = Stroke(r * 0.035f))
                drawShine(light.value, faceR)
            } else {
                drawCircle(track.copy(alpha = 0.55f), radius = r)
                drawCircle(faceBase, radius = faceR)
                drawCircle(track.copy(alpha = 0.35f), radius = faceR)
                if (progress > 0f) {
                    val stroke = r - faceR
                    drawArc(
                        arc,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = Offset(center.x - (r - stroke / 2f), center.y - (r - stroke / 2f)),
                        size = androidx.compose.ui.geometry.Size((r - stroke / 2f) * 2f, (r - stroke / 2f) * 2f),
                        style = Stroke(stroke * 0.7f, cap = StrokeCap.Round),
                    )
                }
            }
        }

        // The icon, with its gesture.
        val art = badgeArt(key)
        if (art != null) {
            Image(
                painterResource(art),
                contentDescription = null,
                colorFilter = if (earned) null else GREYSCALE,
                modifier = Modifier
                    .size(size * 0.6f)
                    .graphicsLayer {
                        val m = gesture.at(clock.value)
                        val h = this.size.height
                        translationX = m.dx * h
                        translationY = m.dy * h
                        rotationZ = m.rotation
                        rotationY = m.rotationY
                        scaleX = m.scaleX
                        scaleY = m.scaleY
                        transformOrigin = TransformOrigin(m.pivotX, m.pivotY)
                        cameraDistance = 12f * density
                        alpha = if (earned) 1f else 0.42f
                    },
            )
        }

        // Sparkles at the edge, for gold and for the single moments.
        if (earned && (metal == BadgeMetal.Gold || metal == BadgeMetal.Rose)) {
            Canvas(Modifier.fillMaxSize()) {
                val r = this.size.minDimension / 2f
                SPARKLES.forEachIndexed { i, s ->
                    val local = (light.value * 3f + s.phase) % 1f
                    // A quick twinkle and a long rest, so they glint rather than throb.
                    val twinkle = if (local < 0.28f) sin(local / 0.28f * PI.toFloat()) else 0f
                    if (twinkle <= 0.01f && moving) return@forEachIndexed
                    val angle = s.angle
                    val at = Offset(center.x + cos(angle) * r * s.reach, center.y + sin(angle) * r * s.reach)
                    drawSparkle(at, r * s.size * (if (moving) twinkle else 0.8f), if (i % 2 == 0) Color.White else palette.light)
                }
            }
        }

        if (!earned) {
            Image(
                painterResource(Res.drawable.ic3d_lock),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-2).dp, y = (-2).dp)
                    .size(size * 0.26f),
            )
        }
    }
}

/** The colour icon behind each badge key; null for a key this release does not draw. */
fun badgeArt(key: String): DrawableResource? = when (key) {
    Badges.FIRST_STEP -> Res.drawable.badge_first_step
    Badges.STREAK -> Res.drawable.badge_streak
    Badges.LOYAL -> Res.drawable.badge_loyal
    Badges.CYCLE -> Res.drawable.badge_cycle
    Badges.DAILY_LOG -> Res.drawable.badge_daily_log
    Badges.WATER -> Res.drawable.badge_water
    Badges.MEDS -> Res.drawable.badge_meds
    Badges.MEALS -> Res.drawable.badge_meals
    Badges.JOURNAL -> Res.drawable.badge_journal
    Badges.MIND -> Res.drawable.badge_mind
    Badges.READER -> Res.drawable.badge_reader
    Badges.FRIENDS -> Res.drawable.badge_friends
    Badges.PARTNER -> Res.drawable.badge_partner
    Badges.DOCTOR -> Res.drawable.badge_doctor
    Badges.COMMUNITY -> Res.drawable.badge_community
    Badges.SYMPTOMS -> Res.drawable.badge_symptoms
    Badges.MOOD -> Res.drawable.badge_mood
    Badges.CALM_MINUTES -> Res.drawable.badge_calm_minutes
    Badges.SCANNER -> Res.drawable.badge_scanner
    Badges.DEVICES -> Res.drawable.badge_devices
    Badges.HYDRO -> Res.drawable.badge_hydro
    Badges.CURIOUS -> Res.drawable.badge_curious
    Badges.SHARE -> Res.drawable.badge_share
    Badges.HELPER -> Res.drawable.badge_helper
    Badges.LOVED -> Res.drawable.badge_loved
    Badges.SHOPPER -> Res.drawable.badge_shopper
    Badges.GARDENER -> Res.drawable.badge_gardener
    else -> null
}

/** Which metal a tier is struck in. A one-moment badge is rose gold. */
enum class BadgeMetal {
    Locked, Bronze, Silver, Gold, Rose;

    fun palette(dark: Boolean): MetalPalette = when (this) {
        Bronze -> MetalPalette(Color(0xFFFFDCC4), Color(0xFFE9A47C), Color(0xFFB86A44), Color(0xFFFFB48C), face(dark, 0xFFFBE3D3))
        Silver -> MetalPalette(Color(0xFFF7F5FC), Color(0xFFCDC6E0), Color(0xFF9188AE), Color(0xFFD8CFFF), face(dark, 0xFFEDEAF6))
        Gold -> MetalPalette(Color(0xFFFFF2BF), Color(0xFFF4C84A), Color(0xFFC4861A), Color(0xFFFFD45E), face(dark, 0xFFFFF4D3))
        Rose -> MetalPalette(Color(0xFFFFE3EC), Color(0xFFF3A6C2), Color(0xFFCF678F), Color(0xFFFFB0CC), face(dark, 0xFFFFE6EF))
        Locked -> MetalPalette(Color(0xFFE6E2EA), Color(0xFFCFCAD6), Color(0xFFA9A3B2), Color.Transparent, Color(0xFFEDEAF0))
    }

    companion object {
        fun of(tier: Int, maxTier: Int): BadgeMetal = when {
            tier <= 0 -> Locked
            maxTier == 1 -> Rose
            tier == 1 -> Bronze
            tier == 2 -> Silver
            else -> Gold
        }

        private fun face(dark: Boolean, light: Long): Color =
            if (dark) Color(light).copy(alpha = 0.22f) else Color(light)
    }
}

@Immutable
data class MetalPalette(val light: Color, val mid: Color, val dark: Color, val glow: Color, val face: Color)

// ------------------------------------------------------------------ gestures

/** One frame of an icon's gesture, as fractions of its own size and degrees. */
@Immutable
data class Move(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val rotation: Float = 0f,
    val rotationY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val pivotX: Float = 0.5f,
    val pivotY: Float = 0.5f,
)

/**
 * Each badge's idle animation, as a pure function of a 0..1 clock.
 *
 * Pure so it can be read inside a draw lambda without allocating state, and so the same
 * clock value always draws the same frame — the unlock overlay can scrub through it.
 */
enum class BadgeGesture(val periodMillis: Int) {
    /** A flame: two out-of-step flickers stretching it up from its base. */
    Flicker(1500) {
        override fun at(t: Float) = Move(
            rotation = 2.5f * wave(t) + 1.2f * wave(t * 3f + 0.2f),
            scaleY = 1f + 0.055f * wave(t * 2f) + 0.025f * wave(t * 3f + 0.3f),
            scaleX = 1f - 0.03f * wave(t * 2f),
            pivotY = 0.95f,
        )
    },

    /** Little feet taking steps: a rock from side to side with a hop on each. */
    Toddle(1600) {
        override fun at(t: Float) = Move(
            rotation = 7f * wave(t),
            dy = -0.06f * abs(wave(t)),
            pivotY = 0.9f,
        )
    },

    /** The sun: a slow sway and a warm swell, never a full turn — it has a face. */
    Shine(5200) {
        override fun at(t: Float) = Move(
            rotation = 9f * wave(t),
            scaleX = 1f + 0.045f * wave(t * 2f),
            scaleY = 1f + 0.045f * wave(t * 2f),
        )
    },

    /** The moon rocks like a cradle on its lower curve. */
    Cradle(3400) {
        override fun at(t: Float) = Move(rotation = 10f * wave(t), pivotY = 0.88f)
    },

    /** The calendar's tick: a pop with a little overshoot, then a rest. */
    Tick(2600) {
        override fun at(t: Float): Move {
            val pop = bump(t, 0f, 0.16f) - 0.35f * bump(t, 0.16f, 0.3f)
            return Move(scaleX = 1f + 0.12f * pop, scaleY = 1f + 0.12f * pop, rotation = -6f * bump(t, 0f, 0.22f))
        }
    },

    /** A drop bobbing on the surface: up, and a squash when it lands. */
    Drip(2000) {
        override fun at(t: Float): Move {
            val w = wave(t)
            val squash = max(0f, -w)
            return Move(
                dy = -0.07f * w,
                scaleX = 1f + 0.06f * squash,
                scaleY = 1f - 0.06f * squash + 0.03f * max(0f, w),
                pivotY = 1f,
            )
        }
    },

    /** The capsule tips one way and the other as it floats. */
    Tumble(2800) {
        override fun at(t: Float) = Move(rotation = 14f * wave(t), dy = -0.03f * wave(t * 2f))
    },

    /** The bowl gives a quick jiggle and settles, as if just set down. */
    Jiggle(2600) {
        override fun at(t: Float): Move {
            val decay = (1f - t * 2.6f).coerceAtLeast(0f)
            return Move(rotation = 6f * wave(t * 5f) * decay, dy = -0.03f * bump(t, 0f, 0.18f), pivotY = 0.9f)
        }
    },

    /** The notebook turns in space, a little each way. */
    Turn(3200) {
        override fun at(t: Float) = Move(rotationY = 22f * wave(t), dy = -0.02f * wave(t))
    },

    /** The lotus breathes: in for two seconds, out for two. */
    Breathe(4200) {
        override fun at(t: Float) = Move(
            scaleX = 1f + 0.065f * wave(t),
            scaleY = 1f + 0.065f * wave(t),
            dy = -0.02f * wave(t),
            pivotY = 0.85f,
        )
    },

    /** The book floats, tilting as it goes. */
    Hover(3000) {
        override fun at(t: Float) = Move(dy = -0.055f * wave(t), rotation = 3.5f * wave(t + 0.25f))
    },

    /** Two hearts: a heartbeat, lub-dub, then a rest. */
    Heartbeat(1400) {
        override fun at(t: Float): Move {
            val s = 1f + 0.11f * bump(t, 0f, 0.14f) + 0.07f * bump(t, 0.2f, 0.34f)
            return Move(scaleX = s, scaleY = s)
        }
    },

    /** Hands lifting a heart: a softer beat while they float. */
    Offer(1800) {
        override fun at(t: Float): Move {
            val s = 1f + 0.07f * bump(t, 0f, 0.16f) + 0.045f * bump(t, 0.22f, 0.36f)
            return Move(scaleX = s, scaleY = s, dy = -0.025f * wave(t))
        }
    },

    /** The stethoscope swings from its earpieces like a pendulum. */
    Swing(2600) {
        override fun at(t: Float) = Move(rotation = 9f * wave(t), pivotX = 0.4f, pivotY = 0.05f)
    },

    /** A message arriving: a pop with a tilt, then a gentle wobble. */
    Chatter(2400) {
        override fun at(t: Float): Move {
            val pop = bump(t, 0f, 0.2f)
            return Move(
                scaleX = 1f + 0.09f * pop,
                scaleY = 1f + 0.09f * pop,
                rotation = -6f * pop + 2f * wave(t * 2f) * (1f - pop),
                dy = -0.035f * pop,
                pivotX = 0.3f,
                pivotY = 0.9f,
            )
        }
    },

    /** The magnifier searching: a small circle, the glass tilting with it. */
    Orbit(2800) {
        override fun at(t: Float) = Move(
            dx = 0.045f * cos(t * TAU),
            dy = 0.045f * sin(t * TAU),
            rotation = -6f * cos(t * TAU),
        )
    },

    /** A cloud drifting: across a little and back, rising as it goes. */
    Drift(4200) {
        override fun at(t: Float) = Move(dx = 0.05f * wave(t), dy = -0.035f * wave(t * 2f + 0.25f), rotation = 2f * wave(t))
    },

    /** The hourglass turns over, rests while the sand runs, and turns again. */
    Flip(5200) {
        override fun at(t: Float): Move {
            // Two eased half-turns per loop, a long rest after each; 360 lands where 0 began.
            val turn = 180f * (ease((t / 0.14f).coerceIn(0f, 1f)) + ease(((t - 0.5f) / 0.14f).coerceIn(0f, 1f)))
            val lift = bump(t, 0f, 0.14f) + bump(t, 0.5f, 0.64f)
            return Move(rotation = turn, scaleX = 1f - 0.06f * lift, scaleY = 1f - 0.06f * lift)
        }
    },

    /** A bag hopping with excitement: up, and a squash on landing. */
    Bounce(1700) {
        override fun at(t: Float): Move {
            val hop = bump(t, 0f, 0.42f)
            val land = bump(t, 0.42f, 0.56f)
            return Move(
                dy = -0.09f * hop,
                scaleX = 1f + 0.07f * land - 0.03f * hop,
                scaleY = 1f - 0.07f * land + 0.04f * hop,
                pivotY = 1f,
            )
        }
    };

    abstract fun at(t: Float): Move

    companion object {
        fun of(key: String): BadgeGesture = when (key) {
            Badges.STREAK -> Flicker
            Badges.FIRST_STEP -> Toddle
            Badges.LOYAL -> Shine
            Badges.CYCLE -> Cradle
            Badges.DAILY_LOG -> Tick
            Badges.WATER -> Drip
            Badges.MEDS -> Tumble
            Badges.MEALS -> Jiggle
            Badges.JOURNAL -> Turn
            Badges.MIND -> Breathe
            Badges.READER -> Hover
            Badges.FRIENDS -> Heartbeat
            Badges.PARTNER -> Offer
            Badges.DOCTOR -> Swing
            Badges.COMMUNITY -> Chatter
            Badges.SYMPTOMS -> Orbit
            Badges.MOOD -> Drift
            Badges.CALM_MINUTES -> Flip
            Badges.SCANNER -> Turn
            Badges.DEVICES -> Offer
            Badges.HYDRO -> Jiggle
            Badges.CURIOUS -> Shine
            Badges.SHARE -> Turn
            Badges.HELPER -> Chatter
            Badges.LOVED -> Heartbeat
            Badges.SHOPPER -> Bounce
            Badges.GARDENER -> Cradle
            else -> Hover
        }
    }
}

// ------------------------------------------------------------------ drawing helpers

private const val TAU = (2 * PI).toFloat()
private const val SHINE_PERIOD = 4200

/** sin over one turn of [t]. */
private fun wave(t: Float): Float = sin(t * TAU)

/** Smooth in, smooth out, over 0..1. */
private fun ease(x: Float): Float = x * x * (3f - 2f * x)

/** A single smooth hump between [from] and [to], zero elsewhere. */
private fun bump(t: Float, from: Float, to: Float): Float =
    if (t < from || t > to) 0f else sin((t - from) / (to - from) * PI.toFloat())

private fun loop(periodMillis: Int, offsetMillis: Int): InfiniteRepeatableSpec<Float> =
    infiniteRepeatable(
        tween(periodMillis, easing = LinearEasing),
        RepeatMode.Restart,
        initialStartOffset = StartOffset(offsetMillis % periodMillis),
    )

/**
 * The band of light that crosses the face: a quarter of the loop crossing, the rest
 * resting, so it reads as a glint rather than as a stripe going round.
 */
private fun DrawScope.drawShine(t: Float, faceR: Float) {
    val local = t / 0.3f
    if (local > 1f) return
    val travel = faceR * 2.8f
    val x = center.x - travel / 2f + travel * local
    drawCircle(
        Brush.linearGradient(
            0f to Color.Transparent,
            0.42f to Color.Transparent,
            0.5f to Color.White.copy(alpha = 0.55f),
            0.58f to Color.Transparent,
            1f to Color.Transparent,
            start = Offset(x - faceR, center.y - faceR),
            end = Offset(x + faceR, center.y + faceR),
        ),
        radius = faceR,
        blendMode = BlendMode.SrcAtop,
    )
}

/** A four-pointed sparkle: two crossed, pinched diamonds. */
internal fun DrawScope.drawSparkle(at: Offset, radius: Float, color: Color) {
    if (radius <= 0.5f) return
    val pinch = radius * 0.22f
    val path = Path().apply {
        moveTo(at.x, at.y - radius)
        quadraticTo(at.x + pinch * 0.4f, at.y - pinch * 0.4f, at.x + radius, at.y)
        quadraticTo(at.x + pinch * 0.4f, at.y + pinch * 0.4f, at.x, at.y + radius)
        quadraticTo(at.x - pinch * 0.4f, at.y + pinch * 0.4f, at.x - radius, at.y)
        quadraticTo(at.x - pinch * 0.4f, at.y - pinch * 0.4f, at.x, at.y - radius)
        close()
    }
    drawCircle(color.copy(alpha = 0.35f), radius * 0.55f, at)
    drawPath(path, color)
}

private class SparkleSpot(val angle: Float, val reach: Float, val size: Float, val phase: Float)

private val SPARKLES = listOf(
    SparkleSpot(angle = -2.2f, reach = 0.86f, size = 0.16f, phase = 0f),
    SparkleSpot(angle = -0.6f, reach = 0.92f, size = 0.11f, phase = 0.37f),
    SparkleSpot(angle = 0.75f, reach = 0.9f, size = 0.13f, phase = 0.68f),
)

private val GREYSCALE = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
