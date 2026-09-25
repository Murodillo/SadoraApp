package uz.sadora.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.Role

/**
 * Clickable without the Material ripple.
 *
 * SADORA signals press with a scale/opacity change instead of a ripple, so the
 * indication is dropped here and applied by the calling component.
 */
fun Modifier.noRippleClickable(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    clickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
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
    onClick: () -> Unit,
): Modifier = composed {
    selectable(
        selected = selected,
        interactionSource = remember { MutableInteractionSource() },
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
    onValueChange: (Boolean) -> Unit,
): Modifier = composed {
    toggleable(
        value = value,
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        enabled = enabled,
        role = role,
        onValueChange = onValueChange,
    )
}

/** Convenience so components can call `Modifier.clip(Radius.card)` in one import. */
@Composable
internal fun rememberInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }
