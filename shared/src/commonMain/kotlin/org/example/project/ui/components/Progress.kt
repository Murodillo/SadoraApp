package org.example.project.ui.components

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing

/**
 * Circular progress ring with a value in the middle — calories, cycle day, scores.
 *
 * [segments] lets a ring show several coloured arcs in sequence (the cycle ring shows
 * period / follicular / fertile / luteal as four arcs of one circle).
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
    content: @Composable () -> Unit = {},
) {
    val c = Sadora.colors
    val ringColor = color ?: c.primary
    val track = trackColor ?: c.surface2

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
                    val sweep = fraction.coerceIn(0f, 1f) * 360f
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
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = progress.coerceIn(0f, 1f) * 360f,
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
            .clip(RoundedCornerShape(999.dp))
            .background(c.surface2),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(999.dp))
                .then(
                    if (gradient) {
                        Modifier.background(Brush.horizontalGradient(listOf(c.secondary, c.primary)))
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
                        .fillMaxHeight(value.coerceIn(0.05f, 1f))
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
            .clip(RoundedCornerShape(999.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        segments.forEach { (fraction, color) ->
            Box(
                Modifier
                    .weight(fraction.coerceAtLeast(0.01f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
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
