package uz.sadora.doctor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import uz.sadora.doctor.design.LocalSadoraColors

/**
 * A visible ring while [source] holds keyboard or D-pad focus — and only then.
 *
 * The custom clickables drop the ripple (`indication = null`), which also dropped the
 * only sign of where focus was; someone driving the app from a keyboard, a switch or a
 * TV remote could not see which control Enter would press (WCAG 2.4.7). Touch never
 * focuses these, so a finger never sees the ring.
 */
fun Modifier.focusRing(source: InteractionSource): Modifier = composed {
    val focused by source.collectIsFocusedAsState()
    val ring = LocalSadoraColors.current.textAccent
    if (!focused) {
        this
    } else {
        drawWithContent {
            drawContent()
            val inset = 2.dp.toPx()
            val stroke = 2.5.dp.toPx()
            val radius = (minOf(size.width, size.height) / 2f).coerceAtMost(24.dp.toPx())
            drawRoundRect(
                color = ring,
                topLeft = Offset(-inset, -inset),
                size = Size(size.width + inset * 2, size.height + inset * 2),
                cornerRadius = CornerRadius(radius + inset),
                style = Stroke(stroke),
            )
        }
    }
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
    /** A button unless said otherwise — nearly every tap target in the app is one. */
    role: Role? = Role.Button,
    /** What the action does, when the element's own text does not say it ("Close"). */
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    focusRing(source).clickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        role = role,
        onClickLabel = onClickLabel,
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
    val source = remember { MutableInteractionSource() }
    focusRing(source).selectable(
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
    onValueChange: (Boolean) -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    focusRing(source).toggleable(
        value = value,
        interactionSource = source,
        indication = null,
        enabled = enabled,
        role = role,
        onValueChange = onValueChange,
    )
}
