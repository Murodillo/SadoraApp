package org.example.project.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** "RADIUS · SPACING · SOYA · IKONKA" — the whole geometry vocabulary. */
object Radius {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 22.dp
    val xl = 26.dp

    val chip = RoundedCornerShape(999.dp)
    val card = RoundedCornerShape(lg)
    val cardSmall = RoundedCornerShape(md)
    val field = RoundedCornerShape(sm)
    val sheet = RoundedCornerShape(topStart = xl, topEnd = xl)
}

/** 8pt grid. 20dp is reserved for screen edges. */
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    /** Screen horizontal padding. */
    val screen = 20.dp
    val lg = 20.dp
    val xl = 32.dp
}

object IconSize {
    val sm = 16.dp
    val md = 20.dp
    val lg = 24.dp
}

/** Touch targets are never smaller than this. */
val MinTouchTarget = 44.dp
