package uz.sadora.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uz.sadora.app.design.SadoraColors

/*
 * The "clay" finish the progress rings, bars and charts share with the 3D icons.
 *
 * The icons are soft glossy objects lit from the top left: a lighter face, a darker
 * underside and a white streak where the light catches. Flat fills next to them read
 * as a different app, so every measured shape here is drawn the same way — a body that
 * shades across its thickness, a highlight streak along it, and a track that sits a
 * little sunk into the card rather than printed on it.
 */

/** The lit face of a clay shape in [base]. */
internal fun clayLight(base: Color): Color = lerp(base, Color.White, 0.36f)

/** The shaded underside of a clay shape in [base]. */
internal fun clayDark(base: Color): Color = lerp(base, Color.Black, 0.12f)

/**
 * Paints a clay body in [color] across the element, which the caller has already
 * clipped to its shape.
 *
 * [horizontal] is the direction the shape runs: a progress bar runs across, so it
 * shades top to bottom; a chart column runs up, so it shades left to right.
 */
fun Modifier.clayFill(color: Color, horizontal: Boolean = true): Modifier =
    drawBehind { drawRect(color) }.clayGloss(horizontal)

/**
 * The clay shading alone, over whatever the element already paints — the hero
 * gradient bar uses it, so the gradient keeps its colours and still reads as an object.
 */
fun Modifier.clayGloss(horizontal: Boolean = true): Modifier = drawBehind {
    val shade = if (horizontal) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.34f),
            0.5f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.12f),
        )
    } else {
        Brush.horizontalGradient(
            0f to Color.White.copy(alpha = 0.32f),
            0.5f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.12f),
        )
    }
    drawRect(shade)
    // The streak: a short pill of light along the lit side, kept off the rounded ends.
    if (horizontal) {
        val thick = size.height
        if (size.width > thick * 1.6f && thick >= 7f) {
            drawRoundRect(
                Color.White.copy(alpha = 0.45f),
                topLeft = Offset(thick * 0.55f, thick * 0.17f),
                size = Size(size.width - thick * 1.1f, thick * 0.2f),
                cornerRadius = CornerRadius(thick * 0.1f),
            )
        }
    } else {
        val thick = size.width
        if (size.height > thick * 1.2f && thick >= 7f) {
            drawRoundRect(
                Color.White.copy(alpha = 0.4f),
                topLeft = Offset(thick * 0.17f, thick * 0.45f),
                size = Size(thick * 0.2f, size.height - thick * 0.9f),
                cornerRadius = CornerRadius(thick * 0.1f),
            )
        }
    }
}

/**
 * A track sunk into the card: the track colour with a soft shade falling from its
 * top edge, as a groove would hold it.
 */
fun Modifier.clayTrack(colors: SadoraColors, horizontal: Boolean = true): Modifier = drawBehind {
    drawRect(colors.surface2)
    val shade = colors.shadow.copy(alpha = if (colors.isDark) 0.28f else 0.08f)
    drawRect(
        if (horizontal) {
            Brush.verticalGradient(0f to shade, 0.55f to Color.Transparent)
        } else {
            Brush.horizontalGradient(0f to shade, 0.55f to Color.Transparent)
        },
    )
}

/** Top-left and size of a circle of [radius] around [center], for drawArc. */
private fun circleBox(center: Offset, radius: Float) =
    Offset(center.x - radius, center.y - radius) to Size(radius * 2, radius * 2)

/** The sunk groove a clay ring runs in. */
internal fun DrawScope.clayRingTrack(
    center: Offset,
    radius: Float,
    stroke: Float,
    colors: SadoraColors,
    trackColor: Color,
) {
    val (tl, sz) = circleBox(center, radius)
    drawArc(trackColor, 0f, 360f, false, tl, sz, style = Stroke(stroke))
    // The groove's shade lies along its inner wall, where the card overhangs it.
    val (itl, isz) = circleBox(center, radius - stroke * 0.3f)
    drawArc(
        colors.shadow.copy(alpha = if (colors.isDark) 0.30f else 0.07f),
        0f, 360f, false, itl, isz,
        style = Stroke(stroke * 0.38f),
    )
}

/**
 * One clay arc: a soft shadow under it, the body, a darker outer edge, a highlight
 * along the inner side and — when [bead] — a glossy bead where it ends, which is the
 * part of the ring the eye goes to first.
 */
internal fun DrawScope.clayArc(
    center: Offset,
    radius: Float,
    stroke: Float,
    start: Float,
    sweep: Float,
    color: Color,
    bead: Boolean,
) {
    if (sweep <= 0f) return
    val round = Stroke(stroke, cap = StrokeCap.Round)
    val (tl, sz) = circleBox(center, radius)

    // Shadow: the same arc a little lower, in the colour's own shade.
    drawArc(
        clayDark(color).copy(alpha = 0.22f),
        start, sweep, false,
        tl + Offset(0f, stroke * 0.22f), sz,
        style = Stroke(stroke * 1.05f, cap = StrokeCap.Round),
    )
    drawArc(color, start, sweep, false, tl, sz, style = round)

    // Underside: the outer third, darker.
    val (otl, osz) = circleBox(center, radius + stroke * 0.3f)
    drawArc(
        clayDark(color).copy(alpha = 0.55f),
        start, sweep, false, otl, osz,
        style = Stroke(stroke * 0.36f, cap = StrokeCap.Round),
    )
    // Lit face and streak along the inner side.
    val (ftl, fsz) = circleBox(center, radius - stroke * 0.12f)
    drawArc(
        clayLight(color).copy(alpha = 0.55f),
        start, sweep, false, ftl, fsz,
        style = Stroke(stroke * 0.5f, cap = StrokeCap.Round),
    )
    if (sweep > 14f) {
        val (htl, hsz) = circleBox(center, radius - stroke * 0.2f)
        drawArc(
            Color.White.copy(alpha = 0.5f),
            start + 5f, sweep - 10f, false, htl, hsz,
            style = Stroke(stroke * 0.16f, cap = StrokeCap.Round),
        )
    }

    if (bead) {
        val end = (start + sweep) * kotlin.math.PI / 180.0
        val at = Offset(
            center.x + radius * kotlin.math.cos(end).toFloat(),
            center.y + radius * kotlin.math.sin(end).toFloat(),
        )
        clayBead(at, stroke * 0.62f, color, lift = stroke * 0.16f)
    }
}

/**
 * A glossy clay bead: a soft shadow under it, a body lit from the top left and a
 * specular dot. [lift] is how far the shadow falls below it.
 */
internal fun DrawScope.clayBead(at: Offset, r: Float, color: Color, lift: Float = r * 0.26f) {
    if (r <= 0f) return
    drawCircle(clayDark(color).copy(alpha = 0.28f * color.alpha), r * 1.04f, at + Offset(0f, lift))
    drawCircle(
        Brush.radialGradient(
            0f to clayLight(color),
            0.7f to color,
            1f to clayDark(color),
            center = at + Offset(-r * 0.3f, -r * 0.3f),
            radius = r * 1.3f,
        ),
        r,
        at,
    )
    drawCircle(Color.White.copy(alpha = 0.75f * color.alpha), r * 0.26f, at + Offset(-r * 0.32f, -r * 0.34f))
}

// ---------------------------------------------------------------- surfaces

/**
 * A clay surface — a card, a button, a header disc — in the icons' light: [fill] with a
 * lit face along the top and a breath of shade at the bottom, a rim that is bright where
 * the light catches the top edge and settles into the line colour underneath, and, on
 * the light theme, a soft shadow tinted [shadowTint] below. Clips to [shape].
 *
 * [streak] adds the white highlight pill the icons carry — for buttons, which should
 * read as pressable objects rather than painted rectangles.
 */
fun Modifier.claySurface(
    colors: SadoraColors,
    shape: Shape,
    fill: Brush,
    elevation: Dp = 10.dp,
    shadowTint: Color = colors.shadow,
    gloss: Float = 1f,
    streak: Boolean = false,
): Modifier = this
    .then(
        if (colors.isDark || elevation <= 0.dp) {
            Modifier
        } else {
            Modifier.shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = shadowTint.copy(alpha = 0.10f),
                spotColor = shadowTint.copy(alpha = 0.18f),
            )
        },
    )
    .clip(shape)
    .background(fill)
    .drawBehind {
        // The light and the shade stay near the edges, measured in dp rather than as a
        // share of the height: a tall card would otherwise go grey all down its lower half.
        val lit = minOf(size.height * 0.5f, 30.dp.toPx())
        val under = minOf(size.height * 0.45f, 22.dp.toPx())
        drawRect(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = (if (colors.isDark) 0.07f else 0.42f) * gloss),
                1f to Color.Transparent,
                startY = 0f,
                endY = lit,
            ),
            size = Size(size.width, lit),
        )
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                1f to Color.Black.copy(alpha = (if (colors.isDark) 0.20f else 0.05f) * gloss),
                startY = size.height - under,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - under),
            size = Size(size.width, under),
        )
        if (streak) {
            val h = size.height
            if (size.width > h * 1.4f) {
                drawRoundRect(
                    Color.White.copy(alpha = 0.32f * gloss),
                    topLeft = Offset(h * 0.45f, h * 0.11f),
                    size = Size(size.width - h * 0.9f, h * 0.15f),
                    cornerRadius = CornerRadius(h * 0.075f),
                )
            }
        }
    }
    .border(
        1.dp,
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (colors.isDark) 0.16f else 0.95f),
                colors.line.copy(alpha = if (colors.isDark) 0.9f else 0.6f),
            ),
        ),
        shape,
    )

/** [claySurface] in one flat colour. */
fun Modifier.claySurface(
    colors: SadoraColors,
    shape: Shape,
    fill: Color,
    elevation: Dp = 10.dp,
    shadowTint: Color = colors.shadow,
    gloss: Float = 1f,
    streak: Boolean = false,
): Modifier = claySurface(colors, shape, SolidColor(fill), elevation, shadowTint, gloss, streak)

/**
 * A round clay bead in [color] — the icons' own shape for a round button: a body lit
 * from the top left, a darker underside, a specular dot and a soft shadow.
 */
fun Modifier.clayBeadSurface(colors: SadoraColors, color: Color, elevation: Dp = 6.dp): Modifier = this
    .then(
        if (colors.isDark || elevation <= 0.dp) {
            Modifier
        } else {
            Modifier.shadow(
                elevation,
                CircleShape,
                ambientColor = clayDark(color).copy(alpha = 0.18f),
                spotColor = clayDark(color).copy(alpha = 0.28f),
            )
        },
    )
    .clip(CircleShape)
    .drawBehind {
        val r = size.minDimension / 2f
        val lit = if (colors.isDark) lerp(color, Color.White, 0.12f) else clayLight(color)
        val shade = if (colors.isDark) lerp(color, Color.Black, 0.25f) else clayDark(color)
        drawCircle(
            Brush.radialGradient(
                0f to lit,
                0.7f to color,
                1f to shade,
                center = center + Offset(-r * 0.3f, -r * 0.35f),
                radius = r * 1.4f,
            ),
            r,
        )
        drawCircle(
            Color.White.copy(alpha = if (colors.isDark) 0.18f else 0.55f),
            r * 0.13f,
            center + Offset(-r * 0.42f, -r * 0.46f),
        )
    }
    .border(
        1.dp,
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (colors.isDark) 0.14f else 0.85f),
                colors.line.copy(alpha = if (colors.isDark) 0.8f else 0.4f),
            ),
        ),
        CircleShape,
    )

/**
 * The ground every screen stands on: the page colour lit like the icons' studio — a
 * lighter wash from the top and three large, very soft pools of the brand colours, so
 * clay cards and buttons sit in a room rather than on a flat sheet. Drawn, not shipped,
 * and fixed in place: it does not scroll or move.
 */
fun Modifier.clayBackdrop(colors: SadoraColors): Modifier = drawBehind {
    drawRect(colors.bg)
    val strength = if (colors.isDark) 0.55f else 1f
    drawRect(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (colors.isDark) 0.03f else 0.55f),
            0.35f to Color.Transparent,
        ),
    )
    val w = size.width
    val h = size.height
    fun pool(at: Offset, radius: Float, tint: Color, alpha: Float) = drawCircle(
        Brush.radialGradient(
            0f to tint.copy(alpha = alpha * strength),
            1f to Color.Transparent,
            center = at,
            radius = radius,
        ),
        radius,
        at,
    )
    pool(Offset(w * 0.05f, h * 0.06f), w * 0.85f, colors.primary, 0.10f)
    pool(Offset(w * 1.0f, h * 0.42f), w * 0.8f, colors.secondary, 0.08f)
    pool(Offset(w * 0.1f, h * 0.92f), w * 0.9f, colors.accent, 0.07f)
}
