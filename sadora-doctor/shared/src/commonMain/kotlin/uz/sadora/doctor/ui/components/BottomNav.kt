package uz.sadora.doctor.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.Spacing

private val BarHeight = 66.dp
private val PillHeight = 50.dp
/** One line of the tab label at the default font size. */
private val LabelLine = 14.dp
private val PillInset = 6.dp
private val BadgeSize = 9.dp

/** The pill's spring; the icons hop on [SelectableArt]'s own. */
private val BarSpringDp = spring<Dp>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

/**
 * One slot on the bar: a colour clay icon, as the client app's bar has. [badge] puts a dot
 * on the icon — unread messages, questions waiting.
 */
data class NavItemSpec<T>(val key: T, val label: String, val art: DrawableResource, val badge: Boolean = false)

/**
 * The client app's tab bar: a floating pill of icons, the selected one on a lavender
 * pill that slides between slots on a spring. Slot centres come from the bar's width
 * rather than from measuring the items, which is what lets the pill be one element.
 */
@Composable
fun <T> SadoraBottomNav(
    items: List<NavItemSpec<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors

    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.xs),
        contentAlignment = Alignment.BottomCenter,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                // At a large system font the label line is taller; the bar grows with it.
                .height(BarHeight + LabelLine * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f))
                .shadow(
                    elevation = 18.dp,
                    shape = Radius.chip,
                    ambientColor = c.shadow.copy(alpha = if (c.isDark) 0.6f else 0.12f),
                    spotColor = c.shadow.copy(alpha = if (c.isDark) 0.6f else 0.18f),
                )
                // The cards' clay: a lit top and the rim that is bright along the top
                // edge and settles into the line colour underneath. The shadow above
                // stays the bar's own, which is deeper than a card's and kept on dark.
                .claySurface(c, Radius.chip, c.surface, elevation = 0.dp)
                // A firmer rim than a card's: the bar floats over everything, and the
                // cards' white-to-line rim vanished against the light page behind it.
                .border(
                    1.5.dp,
                    Brush.verticalGradient(
                        listOf(
                            lerp(c.line, c.primary, if (c.isDark) 0.18f else 0.22f),
                            lerp(c.line, c.primary, if (c.isDark) 0.32f else 0.42f),
                        ),
                    ),
                    Radius.chip,
                ),
        ) {
            val slot = maxWidth / items.size
            val selectedIndex = items.indexOfFirst { it.key == selected }.coerceAtLeast(0)
            val pillX by animateDpAsState(
                targetValue = slot * selectedIndex + PillInset,
                animationSpec = BarSpringDp,
                label = "pill-x",
            )

            // The pill is painted under the row so the icons stay on top of it.
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = pillX)
                    .width(slot - PillInset * 2)
                    .height(PillHeight)
                    .clip(Radius.chip)
                    .background(c.primary.copy(alpha = if (c.isDark) 0.20f else 0.11f)),
            )

            Row(Modifier.fillMaxSize().selectableGroup()) {
                items.forEach { item ->
                    NavItem(
                        item = item,
                        selected = item.key == selected,
                        onClick = { onSelect(item.key) },
                        modifier = Modifier.width(slot).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/**
 * One icon on the bar. Laid out at a fixed height whether or not it is selected — only
 * colour and the icon's hop (drawn, not laid out) change — so selecting a tab never
 * nudges its neighbours.
 */
@Composable
private fun <T> NavItem(
    item: NavItemSpec<T>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val labelColor by animateColorAsState(if (selected) c.textAccent else c.muted2, label = "tab-label")

    Box(
        // One element per tab: "Xabarlar, tab, selected", not an icon and a word.
        modifier.noRippleSelectable(selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // The colour icon carries the selection itself: full colour and a hop on
            // the open tab, most of the colour drained from the other four.
            Box {
                SelectableArt(item.art, 28.dp, selected, dimWhenIdle = true)
                if (item.badge) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 1.dp, y = 1.dp)
                            .size(BadgeSize)
                            .clip(Radius.chip)
                            .background(c.surface)
                            .padding(1.5.dp)
                            .clip(Radius.chip)
                            .background(c.danger),
                    )
                }
            }
            Text(
                item.label,
                style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                color = labelColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
