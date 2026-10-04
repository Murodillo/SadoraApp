package uz.sadora.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
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

/** The pill's spring; the icons hop on [SelectableArt]'s own. */
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
    /** The stage tab's icon, for the same reason; [Tab.Journey]'s own when not given. */
    journeyArt: DrawableResource = Tab.Journey.art,
    /** "Ong" alone, or "Ong · Ovqat" while the food diary lives inside the Mind tab. */
    mindLabel: String,
    /** Unread private messages; above zero, the Chat tab wears a dot. */
    chatUnread: Int = 0,
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
                        art = if (tab == Tab.Journey) journeyArt else tab.art,
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
                        unread = if (tab == Tab.SecretChat && chatUnread > 0) t.community.unreadCount(chatUnread) else null,
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
 * selected — only colour, the icon's hop and scale (drawn, not laid out) and the
 * label's colour change — so selecting a tab never nudges its neighbours.
 *
 * [unread], when set, draws a dot on the icon's shoulder and is what a screen reader
 * says after the label: "Chat, 2 ta o'qilmagan". A dot, not a number — the count is one
 * tap away, and a number on a 24dp icon is noise at the size it would have to be.
 */
@Composable
private fun NavItem(
    art: DrawableResource,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unread: String? = null,
) {
    val c = Sadora.colors
    val labelColor by animateColorAsState(if (selected) c.textAccent else c.muted2, label = "tab-label")

    Box(
        modifier
            // One element per tab: "Bugun, tab, selected", not an icon and a word.
            .noRippleSelectable(selected, role = Role.Tab, onClick = onClick)
            .then(if (unread != null) Modifier.semantics { stateDescription = unread } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // The colour icon carries the selection itself: full colour and a hop on
            // the open tab, most of the colour drained from the other four.
            Box {
                SelectableArt(art, 28.dp, selected, dimWhenIdle = true)
                if (unread != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 1.dp, y = 1.dp)
                            .size(9.dp)
                            .clip(Radius.chip)
                            .background(c.surface)
                            .padding(1.5.dp)
                            .clip(Radius.chip)
                            .background(c.secondary),
                    )
                }
            }
            Text(
                label,
                style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                color = labelColor,
                maxLines = 1,
                // A fifth of the bar is ~65dp; "После родов" at 11sp is wider. Clipping
                // mid-letter looked broken, an ellipsis reads as a label.
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}
