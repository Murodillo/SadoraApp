package uz.sadora.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import uz.sadora.app.design.IconSize
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.nav.Tab

private val BarHeight = 66.dp
private val PillHeight = 50.dp
/** One line of the tab label at the default font size. */
private val LabelLine = 14.dp
private val PillInset = 6.dp

/** One spring for everything that moves on the bar, so nothing arrives out of step. */
private val BarSpring = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
private val BarSpringDp = spring<Dp>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

/**
 * The five-tab bar from the deck: a floating white pill with five icons on it.
 *
 * The selected tab sits on a lavender pill that slides between slots on a spring, so
 * the movement itself says which way the selection went, and its label fades in under
 * the icon while the others keep only theirs greyed. Slot centres are computed from the
 * bar's width rather than measured from the items, which is what lets the pill be a
 * single element instead of five that hand off to each other.
 */
@Composable
fun SadoraBottomNav(
    /** The five to draw, from [Tab.bar]: the last slot differs between free and Premium. */
    tabs: List<Tab>,
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
    /** The stage tab's label — it is named after the life stage, so the caller says it. */
    journeyLabel: String,
    /** "Ong" alone, or "Ong · Ovqat" while the food diary lives inside the Mind tab. */
    mindLabel: String,
) {
    val c = Sadora.colors
    val t = strings

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
                // The label is one line of caption text; at a large system font that line
                // is taller, and a fixed 66dp bar cut it off. The bar grows with it.
                .height(BarHeight + LabelLine * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f))
                .shadow(
                    elevation = 18.dp,
                    shape = Radius.chip,
                    ambientColor = c.shadow.copy(alpha = if (c.isDark) 0.6f else 0.12f),
                    spotColor = c.shadow.copy(alpha = if (c.isDark) 0.6f else 0.18f),
                )
                .clip(Radius.chip)
                .background(c.surface),
        ) {
            val slot = maxWidth / tabs.size
            // A tab that just left the bar (Premium, once bought) has no slot; the
            // pill parks on the first one until the shell moves the selection.
            val selectedIndex = tabs.indexOf(selected).coerceAtLeast(0)
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
                tabs.forEach { tab ->
                    NavItem(
                        icon = tab.icon,
                        label = when (tab) {
                            Tab.Today -> t.tabs.today
                            Tab.Mind -> mindLabel
                            Tab.SecretChat -> t.tabs.secretChat
                            Tab.Journey -> journeyLabel
                            Tab.Nutrition -> t.tabs.nutrition
                            Tab.Premium -> t.tabs.premium
                        },
                        selected = selected == tab,
                        onClick = { onSelect(tab) },
                        modifier = Modifier.width(slot).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/**
 * One of the five icons on the bar.
 *
 * The icon and its label are laid out at a fixed height whether or not the tab is
 * selected — only colour, scale and the label's opacity change — so selecting a tab
 * never nudges its neighbours.
 */
@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val tint by animateColorAsState(if (selected) c.primary else c.muted2, label = "tab-tint")
    val labelColor by animateColorAsState(if (selected) c.textAccent else c.muted2, label = "tab-label")
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = BarSpring,
        label = "tab-scale",
    )
    val lift by animateFloatAsState(
        targetValue = if (selected) -1f else 0f,
        animationSpec = BarSpring,
        label = "tab-lift",
    )
    val liftPx = with(LocalDensity.current) { lift.dp.toPx() }

    Box(
        modifier
            // One element per tab: "Bugun, tab, selected", not an icon and a word.
            .noRippleSelectable(selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
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
            Text(
                label,
                style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                color = labelColor,
                maxLines = 1,
            )
        }
    }
}
