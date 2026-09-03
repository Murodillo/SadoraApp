package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.example.project.design.IconSize
import org.example.project.design.MinTouchTarget
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing

/**
 * Labelled text field.
 *
 * States from the design: default, focus (primary border), error (danger border plus
 * message) and disabled. The label is a caption eyebrow above the box.
 */
@Composable
fun SadoraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    enabled: Boolean = true,
    error: String? = null,
    leading: String? = null,
    /** Preferred over [leading]: the "+998" prefix stays text, an icon becomes a vector. */
    leadingIcon: ImageVector? = null,
    trailing: String? = null,
    /** Fixed unit rendered inside the box, e.g. "sm" or "kg". */
    suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    /**
     * What the keyboard's action key does.
     *
     * The default leaves Compose's own behaviour alone — Next walks to the following
     * field — so only the screens that end on a field override it, and they use it to
     * put the keyboard away rather than leaving a key that does nothing.
     */
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
) {
    val c = Sadora.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val borderColor = when {
        error != null -> c.danger
        focused -> c.primary
        else -> c.line
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (label != null) {
            Text(label.uppercase(), style = Sadora.type.caption, color = c.muted)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.5f)
                .clip(Radius.field)
                .background(c.surface)
                .border(if (focused || error != null) 1.5.dp else 1.dp, borderColor, Radius.field)
                .defaultMinSize(minHeight = MinTouchTarget)
                .padding(horizontal = Spacing.md, vertical = 13.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (leadingIcon != null) {
                    Icon(
                        leadingIcon,
                        contentDescription = null,
                        Modifier.size(IconSize.md),
                        tint = c.muted,
                    )
                } else if (leading != null) {
                    Text(leading, style = Sadora.type.h3, color = c.muted)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = Sadora.type.h3, color = c.muted2)
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        singleLine = singleLine,
                        interactionSource = interaction,
                        textStyle = Sadora.type.h3.copy(color = c.text),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.primary),
                        visualTransformation = if (isPassword) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = keyboardType,
                            imeAction = imeAction,
                        ),
                        keyboardActions = keyboardActions,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (suffix != null) Text(suffix, style = Sadora.type.body, color = c.muted)
                if (trailing != null) Text(trailing, style = Sadora.type.h3, color = c.muted)
            }
        }
        if (error != null) {
            Text(error, style = Sadora.type.body, color = c.danger)
        }
    }
}

/** Search box — leading icon, no label. */
@Composable
fun SadoraSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) = SadoraTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    placeholder = placeholder,
    leadingIcon = SadoraIcons.Search,
    imeAction = ImeAction.Search,
)

/**
 * Six-box OTP entry. Filled boxes get a primary border; the active box is the first
 * empty one.
 */
@Composable
fun OtpInput(
    code: String,
    length: Int = 6,
    isError: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        repeat(length) { index ->
            val char = code.getOrNull(index)?.toString() ?: ""
            val active = index == code.length
            val border = when {
                isError -> c.danger
                char.isNotEmpty() -> c.primary
                active -> c.secondary
                else -> c.line
            }
            Box(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 56.dp)
                    .clip(Radius.field)
                    .background(c.surface)
                    .border(if (char.isNotEmpty() || active) 1.5.dp else 1.dp, border, Radius.field),
                contentAlignment = Alignment.Center,
            ) {
                Text(char, style = Sadora.type.h2, color = c.text)
            }
        }
    }
}
