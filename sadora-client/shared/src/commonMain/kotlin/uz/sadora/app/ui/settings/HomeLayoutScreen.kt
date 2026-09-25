package uz.sadora.app.ui.settings

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.sadora.app.data.RewardsController
import uz.sadora.app.design.IconSize
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraSwitch
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.contract.HomeWidgets

/**
 * "Which blocks do I want on the home screen, and in what order?"
 *
 * Reordering is by two arrows rather than by dragging. A drag reorder inside a lazy
 * list is a good deal of gesture code, and on a list of eleven rows the arrows are
 * faster to use and reachable with one thumb — which matters more here than the
 * gesture being fashionable. The row still moves with a spring, so the change is
 * something she watches happen rather than something that has already happened.
 *
 * The AI card cannot be hidden; its row says so instead of offering a switch that does
 * nothing.
 */
@Composable
fun HomeLayoutScreen(
    state: AppState,
    rewards: RewardsController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.homeLayout
    val c = Sadora.colors
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { rewards.loadHomeLayout() }

    val widgets = state.homeLayout.widgets.sortedBy { it.position }

    Column(modifier) {
        SadoraTopBar(t.title, onBack = onClose)

        ScreenContent {
            item {
                SadoraCard {
                    Text(t.subtitle, style = Sadora.type.body, color = c.muted)
                }
            }

            itemsIndexed(widgets, key = { _, widget -> widget.key }) { index, widget ->
                // `animateItem` is what turns the swap into a movement: the row slides
                // to its new place instead of the list redrawing in a new order.
                Box(Modifier.animateItem(placementSpec = spring(stiffness = Spring.StiffnessMediumLow))) {
                    WidgetRow(
                        key = widget.key,
                        visible = widget.visible,
                        canMoveUp = index > 0,
                        canMoveDown = index < widgets.lastIndex,
                        onToggle = { scope.launch { rewards.setWidgetVisible(widget.key, it) } },
                        onMove = { by -> scope.launch { rewards.moveWidget(widget.key, by) } },
                    )
                }
            }

            item {
                SadoraButton(
                    t.reset,
                    { scope.launch { rewards.resetHomeLayout() } },
                    tone = ButtonTone.Outline,
                )
            }
        }
    }
}

@Composable
private fun WidgetRow(
    key: String,
    visible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
) {
    val t = strings.homeLayout
    val c = Sadora.colors
    val required = key in HomeWidgets.required

    SadoraCard(padding = Spacing.sm) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MoveArrow(up = true, enabled = canMoveUp) { onMove(-1) }
                MoveArrow(up = false, enabled = canMoveDown) { onMove(1) }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t.widget(key), style = Sadora.type.h3, color = c.text, maxLines = 1)
                Text(
                    t.widgetNote(key),
                    style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                    color = c.muted,
                    maxLines = 1,
                )
            }

            // A card that must stay says so, rather than showing a switch that refuses.
            if (required) {
                SadoraBadge(t.alwaysOn, tone = BadgeTone.Neutral)
            } else {
                SadoraSwitch(checked = visible, onCheckedChange = onToggle)
            }
        }
    }
}

/** One of the two reorder arrows. Disabled at the ends rather than hidden. */
@Composable
private fun MoveArrow(up: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val t = strings.homeLayout
    val c = Sadora.colors
    Box(
        Modifier
            .size(28.dp)
            .noRippleClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            SadoraIcons.ArrowUp,
            contentDescription = if (up) t.moveUp else t.moveDown,
            modifier = Modifier
                .size(IconSize.md)
                // One vector, turned over for the second arrow: two glyphs that had to
                // stay visually identical would drift the first time one was retouched.
                .graphicsLayer { rotationZ = if (up) 0f else 180f },
            tint = if (enabled) c.textAccent else c.muted2.copy(alpha = 0.4f),
        )
    }
}
