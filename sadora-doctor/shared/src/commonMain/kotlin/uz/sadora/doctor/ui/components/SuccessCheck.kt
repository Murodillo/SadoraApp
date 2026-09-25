package uz.sadora.doctor.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.sadora.doctor.design.Sadora

/**
 * "Done": the brand gradient disc pops in on the app's spring and the tick is written
 * into it, the way the logo's S is written — a stroke, not a fade.
 *
 * It plays once per [key], so an answer sent twice celebrates twice. With the phone's
 * less-motion setting on it is simply drawn, finished.
 */
@Composable
fun SuccessCheck(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    key: Any? = Unit,
) {
    val c = Sadora.colors
    val still = LocalReduceMotion.current
    val pop = remember(key) { Animatable(if (still) 1f else 0f) }
    val draw = remember(key) { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(key, still) {
        if (still) return@LaunchedEffect
        launch { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
        launch {
            delay(Motion.Quick.toLong())
            draw.animateTo(1f, tween(Motion.Standard + 60, easing = Motion.Emphasized))
        }
    }
    val tick = remember { Path() }
    val measure = remember { PathMeasure() }
    val segment = remember { Path() }

    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                alpha = pop.value.coerceIn(0f, 1f)
            },
    ) {
        val w = this.size.width
        drawCircle(Brush.linearGradient(c.heroColors), radius = w / 2f)
        tick.reset()
        tick.moveTo(w * 0.29f, w * 0.52f)
        tick.lineTo(w * 0.44f, w * 0.67f)
        tick.lineTo(w * 0.72f, w * 0.37f)
        measure.setPath(tick, false)
        segment.reset()
        measure.getSegment(0f, measure.length * draw.value, segment, startWithMoveTo = true)
        drawPath(
            segment,
            color = c.onPrimary,
            style = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
