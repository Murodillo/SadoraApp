package uz.sadora.doctor.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora

/** Shimmering placeholder used by the first-load skeleton state. */
@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = Radius.cardSmall,
) {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloatUnlessReduced(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
    )
    Box(modifier.clip(shape).alpha(alpha).background(Sadora.colors.surface2))
}
