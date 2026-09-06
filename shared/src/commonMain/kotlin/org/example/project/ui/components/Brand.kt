package org.example.project.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.example.project.design.Sadora

/*
 * The brand: the mark, the wordmark, the orb and the loader.
 *
 * All four are drawn from the vectors in the brand deck rather than from bitmaps, so
 * they are sharp at any size, follow the theme where the deck says they should, and
 * carry their motion with them. The path data below is the deck's, unchanged; the
 * timings are the deck's too, and the comments name them where they are used.
 */

// ---------------------------------------------------------------- the vectors

/**
 * The "S", as an outline. The deck draws the letter as a filled shape and reveals it
 * by wiping a thick stroke along [MarkStroke]; the same trick is what makes the letter
 * look written rather than faded in.
 */
private const val MarkOutline =
    "M162.5,31.7L161.2,29.1L159.6,26.5L157.8,24.1L155.9,21.7L153.7,19.5L151.4,17.4L148.9,15.4" +
        "L146.2,13.5L143.4,11.8L140.4,10.2L137.3,8.8L134.1,7.5L130.7,6.3L127.3,5.3L123.8,4.5" +
        "L120.2,3.8L116.6,3.3L112.9,3.0L109.2,2.9L105.4,2.9L101.6,3.1L97.8,3.5L94.0,4.1L90.3,4.9" +
        "L86.5,5.9L82.8,7.1L79.2,8.5L75.6,10.1L72.1,12.0L68.6,14.2L65.3,16.5L62.2,19.2L59.2,22.0" +
        "L56.4,25.0L54.0,28.2L51.8,31.5L49.9,35.0L48.2,38.6L46.9,42.3L45.8,46.0L45.1,49.9" +
        "L44.6,53.7L44.4,57.7L44.4,61.6L44.8,65.6L45.4,69.6L46.2,73.5L47.3,77.4L48.7,81.3" +
        "L50.3,85.2L52.1,89.0L54.1,92.7L56.3,96.4L58.8,100.0L61.5,103.6L64.3,107.0L67.4,110.4" +
        "L70.6,113.7L74.0,116.8L77.6,119.9L81.4,122.9L85.4,125.7L89.5,128.4L93.7,130.9" +
        "L97.4,133.1L100.9,135.2L104.3,137.5L107.6,139.7L110.7,142.1L113.8,144.4L116.6,146.8" +
        "L119.4,149.2L122.0,151.7L124.4,154.1L126.7,156.6L128.8,159.0L130.7,161.5L132.5,163.9" +
        "L134.1,166.3L135.4,168.7L136.6,171.0L137.6,173.3L138.5,175.6L139.1,177.8L139.6,180.1" +
        "L139.9,182.2L140.0,184.4L139.9,186.6L139.6,188.9L139.1,191.1L138.4,193.5L137.4,195.8" +
        "L136.1,198.3L134.5,200.8L132.6,203.4L130.4,206.0L128.7,207.8L126.7,209.5L124.6,211.2" +
        "L122.2,212.9L119.7,214.4L117.1,215.8L114.3,217.1L111.4,218.3L108.4,219.3L105.3,220.2" +
        "L102.1,221.0L98.9,221.5L95.5,221.9L92.2,222.1L88.8,222.2L85.4,222.0L82.0,221.6" +
        "L78.5,221.1L75.1,220.3L71.8,219.2L68.4,218.0L65.1,216.5L61.9,214.7L58.8,212.7" +
        "L55.8,210.4L52.8,207.8L50.0,204.9L47.4,201.7L44.9,198.2L42.5,194.4L40.4,190.3" +
        "L38.6,185.8L37.4,186.2L39.2,190.8L41.1,195.2L43.1,199.2L45.4,203.1L47.9,206.6" +
        "L50.5,209.9L53.3,213.0L56.3,215.8L59.4,218.4L62.6,220.8L66.0,222.9L69.4,224.8" +
        "L73.0,226.4L76.6,227.8L80.4,229.0L84.1,230.0L87.9,230.7L91.8,231.3L95.7,231.6" +
        "L99.5,231.7L103.4,231.6L107.2,231.4L111.0,230.9L114.8,230.2L118.5,229.3L122.1,228.3" +
        "L125.6,227.1L129.0,225.6L132.4,224.0L135.6,222.2L138.7,220.3L141.6,218.0L145.0,215.0" +
        "L148.1,211.8L150.9,208.4L153.3,205.0L155.4,201.4L157.2,197.7L158.6,193.9L159.7,190.0" +
        "L160.4,186.1L160.7,182.2L160.7,178.2L160.4,174.2L159.7,170.3L158.8,166.4L157.6,162.6" +
        "L156.1,158.8L154.3,155.1L152.3,151.5L150.2,148.0L147.8,144.5L145.2,141.1L142.4,137.8" +
        "L139.4,134.5L136.3,131.3L133.0,128.2L129.6,125.2L126.0,122.3L122.3,119.5L118.4,116.7" +
        "L114.5,114.1L110.4,111.5L106.3,109.1L102.8,107.0L99.5,104.9L96.3,102.7L93.3,100.4" +
        "L90.4,98.0L87.7,95.6L85.1,93.1L82.7,90.6L80.4,88.1L78.3,85.5L76.3,82.9L74.5,80.3" +
        "L72.8,77.7L71.4,75.1L70.1,72.4L68.9,69.8L67.9,67.2L67.1,64.6L66.4,62.1L65.9,59.6" +
        "L65.6,57.1L65.4,54.6L65.4,52.1L65.6,49.7L65.9,47.3L66.4,44.9L67.1,42.5L68.0,40.2" +
        "L69.1,37.8L70.4,35.5L72.0,33.1L73.8,30.8L75.9,28.6L78.1,26.5L80.4,24.5L82.9,22.7" +
        "L85.5,20.9L88.2,19.4L91.0,18.0L93.9,16.7L97.0,15.5L100.1,14.6L103.2,13.7L106.4,13.1" +
        "L109.7,12.5L113.0,12.2L116.2,12.0L119.5,11.9L122.8,12.0L126.0,12.3L129.2,12.7" +
        "L132.4,13.2L135.5,14.0L138.5,14.8L141.4,15.9L144.2,17.1L146.9,18.4L149.5,19.9" +
        "L151.9,21.6L154.2,23.4L156.3,25.4L158.3,27.5L160.0,29.8L161.5,32.3Z"

/** The centre line of the letter: the pen's path, from the top hook to the tail. */
private const val MarkStroke =
    "M162,32C148,4 95,-2 68,25C41,52 58,96 100,120C142,144 168,182 136,212C112,234 56,236 38,186"

/** The dot at the foot of the S. It is part of the mark, and it pops rather than fades. */
private const val MarkDot =
    "M55,149 C54.6,154 54.2,158 53.4,161 A8.5,8.5 0 1 1 43.1,151.7 C46.5,150 50.5,149.4 55,149 Z"

/** S · A · D · O · R · A, drawn as six monoline strokes. Never set in a typeface. */
private val WordStrokes = listOf(
    "M30,7 C27,2 17,0 10,3 C3,6 3,14 10,17 L24,23 C31,26 31,35 24,38 C17,41 7,39 4,34",
    "M60,40 L77,0 L94,40",
    "M124,0 V40 H134 C161,40 161,0 134,0 Z",
    "M197,0 A16,20 0 1,0 197,40 A16,20 0 1,0 197,0",
    "M244,40 V0 H258 C274,0 274,22 258,22 H244 M258,22 L272,40",
    "M300,40 L317,0 L334,40",
)

// The mark's own coordinate space, from the deck's viewBox.
private const val MarkVbX = 20f
private const val MarkVbY = -10f
private const val MarkVbW = 160f
private const val MarkVbH = 250f

// The wordmark's, likewise.
private const val WordVbX = -2f
private const val WordVbY = -3f
private const val WordVbW = 338f
private const val WordVbH = 47f

/** The brand gradient: violet at the top, iris through the waist, pink at the tail. */
private val BrandGradient = listOf(Color(0xFFB06CFF), Color(0xFF8A8CFF), Color(0xFFF68CCB))
private val BrandGradientStops = listOf(0f, 0.45f, 1f)
private val DotViolet = Color(0xFFB06CFF)

/** The orb's sphere, lit from the upper left. */
private val OrbGradient = listOf(
    Color(0xFFC9A1FF),
    Color(0xFF8E5CFF),
    Color(0xFF5B3FE0),
    Color(0xFF3A2A9A),
)
private val OrbHaloBlue = Color(0xFF7BB6FF)

// ---------------------------------------------------------------- the timeline

/**
 * The deck's motion, to the millisecond: the S is drawn in one stroke, the dot pops
 * before the line has quite settled, the letters rise one after another, and the
 * tagline arrives last.
 */
private const val DrawMillis = 1500
private const val DotDelay = 1250
private const val DotMillis = 600
private const val LetterDelay = 1200
private const val LetterStagger = 80
private const val LetterMillis = 500
private const val TaglineDelay = 1900
private const val TaglineMillis = 700

/** How long the whole reveal takes, tagline included. Callers wait this long. */
const val LogoRevealMillis = TaglineDelay + TaglineMillis

private val DrawEasing = CubicBezierEasing(0.65f, 0f, 0.25f, 1f)

/** Overshoots past 1 on purpose — the dot lands by bouncing, which is why it reads as a pop. */
private val PopEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
private val OutEasing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

private fun phase(clock: Float, delay: Int, duration: Int): Float =
    ((clock - delay) / duration).coerceIn(0f, 1f)

// ---------------------------------------------------------------- the mark

/** How the mark is painted: in the brand gradient, or in one flat colour. */
enum class MarkTone { Gradient, White, Ink }

@Composable
private fun rememberPath(data: String): Path = remember(data) {
    PathParser().parsePathString(data).toPath()
}

/**
 * The SADORA "S".
 *
 * [progress] is how much of the letter has been written: 1 draws the finished mark, and
 * anything less wipes it along the pen's path. [dotProgress] is separate because the dot
 * pops while the line is still settling.
 */
@Composable
fun SadoraMark(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    tone: MarkTone = MarkTone.Gradient,
    progress: Float = 1f,
    dotProgress: Float = 1f,
) {
    val outline = rememberPath(MarkOutline)
    val stroke = rememberPath(MarkStroke)
    val dot = rememberPath(MarkDot)
    val measure = remember(stroke) { PathMeasure().apply { setPath(stroke, false) } }
    val wipe = remember { Path() }

    Canvas(modifier.size(width = size * (MarkVbW / MarkVbH), height = size)) {
        val scale = this.size.height / MarkVbH
        withTransform({
            scale(scale, scale, pivot = Offset.Zero)
            translate(-MarkVbX, -MarkVbY)
        }) {
            drawMark(outline, dot, measure, wipe, tone, progress, dotProgress)
        }
    }
}

private fun DrawScope.drawMark(
    outline: Path,
    dot: Path,
    measure: PathMeasure,
    wipe: Path,
    tone: MarkTone,
    progress: Float,
    dotProgress: Float,
) {
    val brush = when (tone) {
        MarkTone.Gradient -> Brush.verticalGradient(
            colorStops = BrandGradientStops.zip(BrandGradient).toTypedArray(),
            startY = 0f,
            endY = MarkVbH,
        )

        MarkTone.White -> Brush.verticalGradient(listOf(Color.White, Color.White))
        MarkTone.Ink -> Brush.verticalGradient(listOf(Color(0xFF141414), Color(0xFF141414)))
    }

    if (progress >= 1f) {
        drawPath(outline, brush)
    } else if (progress > 0f) {
        // The letter is masked by the pen: draw the whole S, then keep only the part the
        // pen has already covered. A layer is needed because DstIn works on what is
        // already in the buffer, and the buffer must hold the letter alone.
        wipe.reset()
        measure.getSegment(0f, measure.length * progress, wipe, startWithMoveTo = true)
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset(MarkVbX, MarkVbY), Size(MarkVbW, MarkVbH)), Paint())
            drawPath(outline, brush)
            drawPath(
                wipe,
                color = Color.Black,
                style = Stroke(width = 44f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                blendMode = BlendMode.DstIn,
            )
            canvas.restore()
        }
    }

    if (dotProgress > 0f) {
        val dotColor = when (tone) {
            MarkTone.Gradient -> DotViolet
            MarkTone.White -> Color.White
            MarkTone.Ink -> Color(0xFF141414)
        }
        // The dot scales about its own centre, which is where the deck's `transform-box:
        // fill-box` puts the origin.
        val bounds = dot.getBounds()
        withTransform({ scale(dotProgress, dotProgress, pivot = bounds.center) }) {
            drawPath(dot, dotColor, alpha = dotProgress.coerceIn(0f, 1f))
        }
    }
}

// ---------------------------------------------------------------- the wordmark

/**
 * "SADORA", drawn stroke by stroke.
 *
 * [letters] is how far the reveal has got: each letter rises and fades in on its own
 * eighth of a second, so the word arrives left to right the way it would be written.
 */
@Composable
fun SadoraWordmark(
    modifier: Modifier = Modifier,
    tagline: Boolean = true,
    width: Dp = 174.dp,
    letters: List<Float> = List(WordStrokes.size) { 1f },
    taglineProgress: Float = 1f,
) {
    val c = Sadora.colors
    val paths = WordStrokes.map { rememberPath(it) }

    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(width * 0.075f),
    ) {
        Canvas(Modifier.width(width).height(width * (WordVbH / WordVbW))) {
            val scale = this.size.width / WordVbW
            withTransform({
                scale(scale, scale, pivot = Offset.Zero)
                translate(-WordVbX, -WordVbY)
            }) {
                paths.forEachIndexed { index, path ->
                    val t = letters.getOrElse(index) { 1f }
                    if (t <= 0f) return@forEachIndexed
                    // Eight units up is the deck's 8px rise, in the wordmark's own space.
                    withTransform({ translate(0f, (1f - t) * 8f) }) {
                        drawPath(
                            path,
                            color = c.text,
                            alpha = t,
                            style = Stroke(
                                width = 3.2f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                    }
                }
            }
        }
        if (tagline && taglineProgress > 0f) {
            Text(
                "EVERY WOMAN. EVERY MOMENT.",
                style = Sadora.type.caption.copy(
                    letterSpacing = 0.18.em,
                    fontSize = (width.value * 0.058f).sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = c.textAccent.copy(alpha = taglineProgress),
            )
        }
    }
}

// ---------------------------------------------------------------- the lockup

/**
 * The vertical lockup, playing the deck's reveal: the S is written, the dot pops, the
 * letters rise, and the tagline follows them.
 *
 * Changing [play] runs it again, which is what the splash does when the session takes
 * longer to resolve than the animation takes to finish.
 */
@Composable
fun SadoraLogoReveal(
    modifier: Modifier = Modifier,
    size: Dp = 148.dp,
    tagline: Boolean = true,
    animated: Boolean = true,
    play: Int = 0,
) {
    val clock = remember { Animatable(if (animated) 0f else LogoRevealMillis.toFloat()) }
    LaunchedEffect(play, animated) {
        if (!animated) {
            clock.snapTo(LogoRevealMillis.toFloat())
            return@LaunchedEffect
        }
        clock.snapTo(0f)
        clock.animateTo(
            LogoRevealMillis.toFloat(),
            tween(LogoRevealMillis, easing = LinearEasing),
        )
    }

    val now = clock.value
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(size * 0.2f),
    ) {
        SadoraMark(
            size = size,
            progress = DrawEasing.transform(phase(now, 0, DrawMillis)),
            dotProgress = PopEasing.transform(phase(now, DotDelay, DotMillis)),
        )
        SadoraWordmark(
            width = size * 1.45f,
            tagline = tagline,
            letters = List(WordStrokes.size) { index ->
                OutEasing.transform(phase(now, LetterDelay + index * LetterStagger, LetterMillis))
            },
            taglineProgress = OutEasing.transform(phase(now, TaglineDelay, TaglineMillis)),
        )
    }
}

// ---------------------------------------------------------------- orb and loader

/**
 * The AI orb: the brand sphere, breathing on the deck's 3.2s cycle with a highlight
 * travelling around its rim.
 *
 * Nothing here is themed. The orb is the same object on the light screens and on the
 * dark ones, which is what makes it the app's one recognisable image.
 */
@Composable
fun AiOrb(
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    /** The mark inside it. Off for the tab bar, where the sphere alone is the icon. */
    mark: Boolean = true,
) {
    val transition = rememberInfiniteTransition()
    val breath by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
    )
    val halo by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
    )

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawOrb(breath, halo, spin)
        }
        // The sphere on its own is a glow, not a logo. The mark in white is what makes
        // the welcome screen and the assistant recognisably SADORA.
        if (mark) SadoraMark(size = size * 0.46f, tone = MarkTone.White)
    }
}

private fun DrawScope.drawOrb(breath: Float, halo: Float, spin: Float) {
    val r = size.minDimension / 2f

    drawCircle(
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to DotViolet.copy(alpha = 0.55f),
                0.45f to OrbHaloBlue.copy(alpha = 0.25f),
                0.72f to Color.Transparent,
            ),
            center = center,
            radius = r * 1.18f * halo,
        ),
        radius = r * 1.18f * halo,
        center = center,
    )

    val sphere = r * breath
    drawCircle(
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to OrbGradient[0],
                0.35f to OrbGradient[1],
                0.70f to OrbGradient[2],
                1f to OrbGradient[3],
            ),
            center = Offset(center.x - r * 0.4f, center.y - r * 0.5f),
            radius = sphere * 1.2f,
        ),
        radius = sphere,
        center = center,
    )

    // The rim light: one bright arc and one pink one, turning slowly.
    withTransform({ rotate(spin, center) }) {
        drawCircle(
            Brush.sweepGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.12f to Color.White.copy(alpha = 0.55f),
                    0.30f to Color.Transparent,
                    0.60f to Color.Transparent,
                    0.72f to Color(0xFFF68CCB).copy(alpha = 0.5f),
                    0.90f to Color.Transparent,
                    1f to Color.Transparent,
                ),
                center = center,
            ),
            radius = sphere - r * 0.035f,
            center = center,
            style = Stroke(width = r * 0.07f),
        )
    }

    // The sphere's own edge, so it reads as glass rather than as a gradient patch.
    drawCircle(
        color = Color.White.copy(alpha = 0.35f),
        radius = sphere,
        center = center,
        style = Stroke(width = r * 0.02f),
    )
}

/**
 * The loading state: the orb, small and quick, inside a ring that turns once a second.
 *
 * The deck calls this "Sadora is thinking" — it is the same sphere as the assistant's,
 * which is what tells her the app is working rather than stuck.
 */
@Composable
fun SadoraLoader(modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val transition = rememberInfiniteTransition()
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
    )
    val breath by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
    )

    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val ball = r * 0.72f * breath

        // The glow first, so the ball sits in light rather than on the background.
        drawCircle(
            Brush.radialGradient(
                colorStops = arrayOf(
                    0f to DotViolet.copy(alpha = 0.45f),
                    1f to Color.Transparent,
                ),
                center = center,
                radius = r,
            ),
            radius = r,
            center = center,
        )
        drawCircle(
            Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color(0xFFE7D6FF),
                    0.30f to OrbGradient[0],
                    0.62f to OrbGradient[1],
                    1f to OrbGradient[2],
                ),
                center = Offset(center.x - ball * 0.42f, center.y - ball * 0.52f),
                radius = ball * 1.6f,
            ),
            radius = ball,
            center = center,
        )

        withTransform({ rotate(spin, center) }) {
            drawArc(
                color = DotViolet,
                startAngle = -90f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(center.x - r + r * 0.06f, center.y - r + r * 0.06f),
                size = Size((r - r * 0.06f) * 2f, (r - r * 0.06f) * 2f),
                style = Stroke(width = r * 0.14f, cap = StrokeCap.Round),
            )
            drawArc(
                color = Color(0xFFF68CCB).copy(alpha = 0.8f),
                startAngle = 40f,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = Offset(center.x - r + r * 0.06f, center.y - r + r * 0.06f),
                size = Size((r - r * 0.06f) * 2f, (r - r * 0.06f) * 2f),
                style = Stroke(width = r * 0.14f, cap = StrokeCap.Round),
            )
        }
    }
}

/** The mark on the app's own dark tile — the icon, drawn, for anywhere the app shows itself. */
@Composable
fun SadoraAppTile(modifier: Modifier = Modifier, size: Dp = 64.dp) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension
            drawRoundRect(
                Brush.radialGradient(
                    listOf(Color(0xFF2A1D5C), Color(0xFF0E0B1F)),
                    center = Offset(this.size.width * 0.5f, this.size.height * 0.3f),
                    radius = r * 0.8f,
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.225f),
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(DotViolet.copy(alpha = 0.55f), Color.Transparent),
                    center = center,
                    radius = r * 0.45f,
                ),
                radius = r * 0.45f,
                center = center,
            )
        }
        SadoraMark(size = size * 0.6f)
    }
}
