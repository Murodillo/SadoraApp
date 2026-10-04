package uz.sadora.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
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
