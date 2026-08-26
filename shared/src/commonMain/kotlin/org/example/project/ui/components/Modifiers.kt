package org.example.project.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Clickable without the Material ripple.
 *
 * SADORA signals press with a scale/opacity change instead of a ripple, so the
 * indication is dropped here and applied by the calling component.
 */
fun Modifier.noRippleClickable(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    clickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
}

/** Convenience so components can call `Modifier.clip(Radius.card)` in one import. */
@Composable
internal fun rememberInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }
