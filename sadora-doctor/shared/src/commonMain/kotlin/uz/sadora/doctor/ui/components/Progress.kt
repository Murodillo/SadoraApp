package uz.sadora.doctor.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora

/** A 0..1 value that fills from zero to [target] the first time it is shown. */
@Composable
fun animatedProgress(
    target: Float,
    durationMillis: Int = Motion.Slow,
    delayMillis: Int = 0,
): Float {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val value by animateFloatAsState(
        targetValue = if (started) target else 0f,
        animationSpec = tween(durationMillis, delayMillis, Motion.Emphasized),
        label = "progress",
    )
    return value
}

/** A count that climbs from zero to [target] the first time it is shown. */
@Composable
fun animatedCount(
    target: Int,
    durationMillis: Int = Motion.Slow,
    delayMillis: Int = 0,
): Int {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val value by animateFloatAsState(
        targetValue = if (started) target.toFloat() else 0f,
        animationSpec = tween(durationMillis, delayMillis, Motion.Emphasized),
        label = "count",
    )
    return value.roundToInt()
}

/** A number that counts up to its value instead of appearing at it. */
@Composable
fun AnimatedNumber(
    value: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    format: (Int) -> String = { it.toString() },
) {
    Text(format(animatedCount(value, delayMillis = delayMillis)), style = style, color = color, modifier = modifier, maxLines = 1)
}

/** Horizontal progress bar in the clay finish, on a groove in the card. */
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
            .clayTrack(c),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animatedProgress(progress.coerceIn(0f, 1f)))
                .fillMaxHeight()
                .clip(Radius.chip)
                .then(
                    if (gradient) {
                        Modifier.background(Brush.horizontalGradient(c.heroColors)).clayGloss()
                    } else {
                        Modifier.clayFill(color ?: c.primary)
                    },
                ),
        )
    }
}
