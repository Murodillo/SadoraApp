package uz.sadora.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora

/** The ring's default corners when the caller does not pass the element's own shape. */
val FocusRingShape: Shape = RoundedCornerShape(Radius.sm)

/**
 * A 2dp ring while [source] holds keyboard / D-pad focus (WCAG 2.4.7).
 *
 * The press feedback here is a scale, not a ripple, so without this someone moving with a
 * keyboard cannot see where they are. Touch never focuses a clickable, so the ring only
 * appears for keyboard and switch users.
 */
@Composable
internal fun Modifier.focusRing(source: MutableInteractionSource, shape: Shape): Modifier {
    val focused by source.collectIsFocusedAsState()
    return if (focused) border(2.dp, Sadora.colors.primary, shape) else this
}

/**
 * Clickable without the Material ripple.
 *
 * SADORA signals press with a scale/opacity change instead of a ripple, so the
 * indication is dropped here and applied by the calling component.
 */
fun Modifier.noRippleClickable(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    focusShape: Shape = FocusRingShape,
    onClick: () -> Unit,
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    focusRing(source, focusShape).clickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick,
    )
}

/**
 * One option out of several — a tab, a segment, a chip in a single-choice row. A screen
 * reader hears the role and "selected", which a bare click handler never tells it. Put
 * `selectableGroup()` on the row that holds the options.
 */
fun Modifier.noRippleSelectable(
    selected: Boolean,
    role: Role,
    enabled: Boolean = true,
    focusShape: Shape = FocusRingShape,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    focusRing(source, focusShape).selectable(
        selected = selected,
        interactionSource = source,
        indication = null,
        enabled = enabled,
        role = role,
        onClick = onClick,
    )
}

/** On or off — a switch, a checkbox, a multi-select chip — announced with its state. */
fun Modifier.noRippleToggleable(
    value: Boolean,
    role: Role,
    enabled: Boolean = true,
    focusShape: Shape = FocusRingShape,
    onValueChange: (Boolean) -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    focusRing(source, focusShape).toggleable(
        value = value,
        interactionSource = source,
        indication = null,
        enabled = enabled,
        role = role,
        onValueChange = onValueChange,
    )
}

/** Convenience so components can call `Modifier.clip(Radius.card)` in one import. */
@Composable
internal fun rememberInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }
