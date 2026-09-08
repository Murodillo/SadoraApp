package uz.sadora.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing

/**
 * Circular progress ring with a value in the middle — calories, cycle day, scores.
 *
 * [segments] lets a ring show several coloured arcs in sequence (the cycle ring shows
 * period / follicular / fertile / luteal as four arcs of one circle).
 *
 * The arc sweeps up from zero the first time the ring appears and follows every later
 * change on the same curve, so a ring always reads as a measurement being taken rather
 * than as a picture that was already there. [animate] turns that off for the few places
 * that redraw many rings per frame.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    strokeWidth: Dp = 12.dp,
    color: Color? = null,
    trackColor: Color? = null,
    segments: List<Pair<Float, Color>>? = null,
    animate: Boolean = true,
    /** Staggers a row of rings so they fill one after another. */
    delayMillis: Int = 0,
    /** A wider, fainter copy of the arc under it, as the deck draws the score ring. */
    glow: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    val c = Sadora.colors
    val ringColor = color ?: c.primary
    val track = trackColor ?: c.surface2
    // One factor drives both the plain arc and the segmented one, so a segmented ring
    // grows as a single stroke instead of its parts appearing out of step.
    val grow = if (animate) animatedProgress(1f, delayMillis = delayMillis) else 1f

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            if (segments != null) {
                var start = -90f
                segments.forEach { (fraction, segColor) ->
                    val sweep = fraction.coerceIn(0f, 1f) * 360f * grow
                    drawArc(
                        color = segColor,
                        startAngle = start + 1.5f,
                        sweepAngle = (sweep - 3f).coerceAtLeast(0f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    start += sweep
                }
            } else {
                val sweep = progress.coerceIn(0f, 1f) * 360f * grow
                // The glow is a wider, fainter copy of the arc itself rather than a
                // disc behind the ring — a disc tints everything the ring encloses,
                // including the number in the middle.
                if (glow) {
                    drawArc(
                        color = ringColor.copy(alpha = 0.18f),
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke * 2.2f, cap = StrokeCap.Round),
                    )
                }
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/**
 * A small ring with a value inside and a label under it — the row of four across the
 * top of the deck's nutrition screen ("1200 / 1600 kcal", "90 / 100 g Protein").
 */
@Composable
fun MiniRing(
    progress: Float,
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    size: Dp = 68.dp,
    delayMillis: Int = 0,
) {
    val c = Sadora.colors
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ProgressRing(
            progress = progress,
            size = size,
            strokeWidth = 6.dp,
            color = color,
            delayMillis = delayMillis,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    value,
                    style = Sadora.type.h3,
                    color = c.text,
                    maxLines = 1,
                )
                Text(
                    unit,
                    style = Sadora.type.caption.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
                    color = c.muted2,
                    maxLines = 1,
                )
            }
        }
        Text(
            label,
            style = Sadora.type.caption.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
            color = c.muted,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** Horizontal progress bar on the `surface-2` track. */
@Composable
fun SadoraProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color? = null,
    height: Dp = 8.dp,
    gradient: Boolean = false,
) {
    val c = Sadora.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(Radius.chip)
            .background(c.surface2),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animatedProgress(progress.coerceIn(0f, 1f)))
                .fillMaxHeight()
                .clip(Radius.chip)
                .then(
                    if (gradient) {
                        Modifier.background(Brush.horizontalGradient(c.heroColors))
                    } else {
                        Modifier.background(color ?: c.primary)
                    },
                ),
        )
    }
}

/** A labelled macro/goal bar: "Oqsil · 61 / 85 g". */
@Composable
fun LabeledProgress(
    label: String,
    value: String,
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val c = Sadora.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = Sadora.type.body, color = c.muted)
            Text(value, style = Sadora.type.body, color = c.text)
        }
        SadoraProgressBar(progress, color = color, height = 6.dp)
    }
}

/**
 * Weekly bar chart. Values are 0..1; [highlightIndex] marks today.
 */
@Composable
fun WeeklyBars(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color? = null,
    highlightIndex: Int? = null,
    barHeight: Dp = 72.dp,
) {
    val c = Sadora.colors
    val fill = color ?: c.secondary
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            Modifier.fillMaxWidth().height(barHeight),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { index, value ->
                Box(
                    Modifier
                        .weight(1f)
                        // Bars grow left to right rather than all at once, which is what
                        // makes a week read as a sequence of days.
                        .fillMaxHeight(
                            animatedProgress(
                                value.coerceIn(0.05f, 1f),
                                delayMillis = index * 40,
                            ),
                        )
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (index == highlightIndex) c.primary else fill.copy(alpha = 0.75f)),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.forEach { label ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(label, style = Sadora.type.caption, color = c.muted2)
                }
            }
        }
    }
}

/**
 * A measured series, drawn as bars with the gaps left as gaps.
 *
 * The difference from [WeeklyBars] is the whole point: a day with nothing recorded is
 * null, not zero, and is drawn as an empty track. A week off then looks like a week off
 * rather than like a week of zeroes.
 *
 * Bars are scaled against the largest value in the window, so the shape is comparable
 * within a window and never implies a goal the series does not have.
 */
@Composable
fun TrendBars(
    values: List<Float?>,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    color: Color? = null,
    highlightLast: Boolean = false,
    barHeight: Dp = 72.dp,
) {
    val c = Sadora.colors
    val fill = color ?: c.primary
    val peak = values.filterNotNull().maxOrNull()?.takeIf { it > 0f } ?: 1f

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            Modifier.fillMaxWidth().height(barHeight),
            horizontalArrangement = Arrangement.spacedBy(if (values.size > 14) 2.dp else 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { index, value ->
                val last = highlightLast && index == values.lastIndex
                if (value == null) {
                    // The track alone: something is drawn in the slot, but nothing that
                    // reads as a measurement.
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(0.06f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(c.surface2),
                    )
                } else {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(
                                animatedProgress(
                                    (value / peak).coerceIn(0.06f, 1f),
                                    delayMillis = (index * 30).coerceAtMost(400),
                                ),
                            )
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (last) c.primary else fill.copy(alpha = 0.75f)),
                    )
                }
            }
        }
        if (labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.forEach { label ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(label, style = Sadora.type.caption, color = c.muted2, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * Stacked proportional bar — sleep stages, free-vs-premium ranges.
 */
@Composable
fun StackedBar(
    segments: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(Radius.chip),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        segments.forEach { (fraction, color) ->
            Box(
                Modifier
                    .weight(fraction.coerceAtLeast(0.01f))
                    .fillMaxHeight()
                    .clip(Radius.chip)
                    .background(color),
            )
        }
    }
}

/** Shimmering placeholder used by the first-load skeleton state. */
@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = Radius.cardSmall,
) {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
    )
    Box(modifier.clip(shape).alpha(alpha).background(Sadora.colors.surface2))
}
