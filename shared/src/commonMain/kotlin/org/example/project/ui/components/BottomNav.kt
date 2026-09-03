package org.example.project.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.example.project.design.IconSize
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.nav.Tab

private val BarHeight = 64.dp
private val BarCorner = 28.dp
private val CentreSize = 56.dp
/** How far the centre button's top sits above the bar's top edge. */
private val CentreLift = 20.dp
/** Clearance between the button and the cut-out around it. */
private val NotchGap = 6.dp
private val IndicatorWidth = 22.dp
private val IndicatorHeight = 3.dp

/** One spring for everything that moves on the bar, so nothing arrives out of step. */
private val BarSpring = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
private val BarSpringDp = spring<Dp>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

/**
 * A rounded bar with a circle cut out of its top edge.
 *
 * The cut-out is subtracted from the bar's own outline rather than faked by painting
 * a matching colour, so it stays right over whatever scrolls beneath — including this
 * app's gradient cards. [notchCentreY] is where the button's centre actually lands
 * relative to the bar's top; centring the circle on the edge itself would leave the
 * gap uneven around the button.
 */
private class NotchedBarShape(
    private val notchRadius: Float,
    private val notchCentreY: Float,
    private val corner: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val bar = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner)))
        }
        val notch = Path().apply {
            addOval(Rect(center = Offset(size.width / 2f, notchCentreY), radius = notchRadius))
        }
        return Outline.Generic(Path().apply { op(bar, notch, PathOperation.Difference) })
    }
}

/**
 * The five-tab bar.
 *
 * Four destinations sit on the bar; the fifth, the secret chat, is the raised centre
 * button — the shape of the reference design and the honest hierarchy, since it is
 * the destination the app most wants opened.
 *
 * One indicator marks the selected tab and slides between slots on a spring, so the
 * movement itself says which way the selection went. The slot centres are computed
 * from the bar's width rather than measured from the items, which is what lets the
 * indicator be a single element instead of four that hand off to each other.
 */
@Composable
fun SadoraBottomNav(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    journeyLabel: String,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val sideTabs = remember { Tab.entries.filter { it != Tab.Chat } }
    val density = LocalDensity.current
    val barShape = remember(density) {
        with(density) {
            NotchedBarShape(
                notchRadius = (CentreSize / 2 + NotchGap).toPx(),
                notchCentreY = (CentreSize / 2 - CentreLift).toPx(),
                corner = BarCorner.toPx(),
            )
        }
    }
    val gap = CentreSize + NotchGap * 2

    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = Spacing.sm, end = Spacing.sm, bottom = Spacing.xs),
        contentAlignment = Alignment.BottomCenter,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .shadow(
                    elevation = 16.dp,
                    shape = barShape,
                    ambientColor = c.text.copy(alpha = 0.25f),
                    spotColor = c.text.copy(alpha = 0.25f),
                )
                .background(c.surface, RectangleShape),
        ) {
            // Four equal slots either side of the gap the centre button sits in.
            val slot = (maxWidth - gap) / 4
            val selectedIndex = sideTabs.indexOf(selected)
            val indicatorX by animateDpAsState(
                targetValue = when {
                    selectedIndex < 0 -> maxWidth / 2 - IndicatorWidth / 2
                    selectedIndex < 2 -> slot * selectedIndex + slot / 2 - IndicatorWidth / 2
                    else -> slot * 2 + gap + slot * (selectedIndex - 2) + slot / 2 - IndicatorWidth / 2
                },
                animationSpec = BarSpringDp,
                label = "indicator-x",
            )
            // The indicator hides under the centre button rather than jumping there:
            // the button has its own selected state, and two markers for one tab read
            // as a mistake.
            val indicatorAlpha by animateFloatAsState(
                targetValue = if (selectedIndex < 0) 0f else 1f,
                animationSpec = BarSpring,
                label = "indicator-alpha",
            )

            Row(Modifier.fillMaxSize()) {
                sideTabs.forEachIndexed { index, tab ->
                    NavItem(
                        icon = tab.icon,
                        selected = selected == tab,
                        onClick = { onSelect(tab) },
                        modifier = Modifier.width(slot).fillMaxHeight(),
                    )
                    if (index == 1) Spacer(Modifier.width(gap))
                }
            }

            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = indicatorX)
                    .padding(bottom = 8.dp)
                    .size(width = IndicatorWidth, height = IndicatorHeight)
                    .graphicsLayer { alpha = indicatorAlpha }
                    .clip(Radius.chip)
                    .background(c.text),
            )
        }

        CentreButton(
            selected = selected == Tab.Chat,
            onClick = { onSelect(Tab.Chat) },
            modifier = Modifier.offset(y = -CentreLift),
        )
    }
}

/**
 * One of the four icons on the bar.
 *
 * The icon is centred in the bar's height and the indicator lives on the bar, not in
 * the item, so selecting a tab never shifts its icon to make room for a marker.
 */
@Composable
private fun NavItem(
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val tint by animateColorAsState(if (selected) c.text else c.muted2, label = "tab-tint")
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = BarSpring,
        label = "tab-scale",
    )
    val lift by animateFloatAsState(
        targetValue = if (selected) -3f else 0f,
        animationSpec = BarSpring,
        label = "tab-lift",
    )
    val liftPx = with(LocalDensity.current) { lift.dp.toPx() }

    Box(
        modifier.noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            Modifier
                .size(IconSize.lg)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = liftPx
                },
            tint = tint,
        )
    }
}

/**
 * The raised centre destination.
 *
 * It carries the hero gradient — one of the four places the design allows it — and
 * lifts a little further on a spring when it becomes the current tab, which is the
 * only movement the bar makes above its own edge.
 */
@Composable
private fun CentreButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "centre-scale",
    )
    val rise by animateFloatAsState(
        targetValue = if (selected) -4f else 0f,
        animationSpec = BarSpring,
        label = "centre-rise",
    )
    val risePx = with(LocalDensity.current) { rise.dp.toPx() }
    // The gradient's glow belongs to the selected state; under the white resting button
    // a coloured shadow read as a smudge rather than as lift.
    val glow by animateColorAsState(
        if (selected) c.primary else c.text.copy(alpha = 0.35f),
        label = "centre-glow",
    )
    val elevation by animateDpAsState(
        targetValue = if (selected) 16.dp else 10.dp,
        animationSpec = BarSpringDp,
        label = "centre-elevation",
    )
    val tint by animateColorAsState(if (selected) c.onPrimary else c.text, label = "centre-tint")

    Box(
        modifier
            .size(CentreSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = risePx
            }
            .shadow(elevation, Radius.chip, ambientColor = glow, spotColor = glow)
            .clip(Radius.chip)
            .background(
                if (selected) {
                    Brush.linearGradient(listOf(c.secondary, c.primary))
                } else {
                    Brush.linearGradient(listOf(c.surface, c.surface))
                },
            )
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Tab.Chat.icon, contentDescription = Tab.Chat.label, Modifier.size(24.dp), tint = tint)
    }
}
