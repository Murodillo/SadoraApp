package uz.sadora.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uz.sadora.app.design.MinTouchTarget
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing

/**
 * Selectable card row used by language, life stage and permission lists: leading
 * glyph, title, subtitle, and a check on the right when chosen.
 */
@Composable
fun OptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Sadora.colors
    val border by animateColorAsState(if (selected) c.primary else c.line)
    val bg by animateColorAsState(
        if (selected) c.primary.copy(alpha = if (c.isDark) 0.14f else 0.07f) else c.surface,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(bg)
            .border(if (selected) 1.5.dp else 1.dp, border, Radius.card)
            .defaultMinSize(minHeight = MinTouchTarget)
            .noRippleSelectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (leading != null) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(Radius.sm))
                    .background(if (selected) c.primary.copy(alpha = 0.18f) else c.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Text(leading, style = Sadora.type.h3, color = if (selected) c.textAccent else c.secondary)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = Sadora.type.h3.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = c.text,
            )
            if (subtitle != null) Text(subtitle, style = Sadora.type.body, color = c.muted)
        }
        when {
            trailing != null -> trailing()
            // The row already says "selected"; the tick is only for the eye.
            selected -> Text(
                "✓",
                style = Sadora.type.h3,
                color = c.textAccent,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/**
 * Square checkbox used by consent lists and "Eslab qolish".
 *
 * On its own it is a 44dp target around the 22dp box, announced as a checkbox with its
 * state. Inside a row that toggles as a whole, pass a null [onCheckedChange] and it is
 * drawn only — the row carries the semantics, so the reader hears one control, not two.
 */
@Composable
fun SadoraCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val bg by animateColorAsState(if (checked) c.primary else Color.Transparent)
    // The unchecked outline is the control's only edge, so it needs 3:1 — [c.line] was 1.2.
    val border by animateColorAsState(if (checked) c.primary else c.muted)
    val box = @Composable {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(bg)
                .border(1.5.dp, border, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text("✓", style = Sadora.type.caption, color = c.onPrimary, modifier = Modifier.clearAndSetSemantics {})
            }
        }
    }
    if (onCheckedChange == null) {
        Box(modifier) { box() }
    } else {
        Box(
            modifier
                .size(MinTouchTarget)
                .noRippleToggleable(checked, role = Role.Checkbox, onValueChange = onCheckedChange),
            contentAlignment = Alignment.Center,
        ) { box() }
    }
}

/** Consent / permission row: checkbox, title, explanation. */
@Composable
fun ConsentRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean = false,
) {
    val c = Sadora.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MinTouchTarget)
            .noRippleToggleable(checked, role = Role.Checkbox, enabled = !required, onValueChange = onCheckedChange)
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SadoraCheckbox(checked, onCheckedChange = null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Sadora.type.h3, color = c.text)
            Text(body, style = Sadora.type.body, color = c.muted)
        }
    }
}

/** Pill toggle used for settings such as theme and reminders. */
@Composable
fun SadoraSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val track by animateColorAsState(if (checked) c.primary else c.surface2)
    Box(
        modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(Radius.chip)
            .background(track)
            .noRippleToggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(Radius.chip)
                .background(if (checked) c.onPrimary else c.muted),
        )
    }
}

/** Two-up tab switch, e.g. "Telefon / E-mail". */
@Composable
fun TabSwitch(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Column(
                Modifier.noRippleSelectable(selected, role = Role.Tab) { onSelect(index) },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    option,
                    style = Sadora.type.h3.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (selected) c.text else c.muted,
                )
                Box(
                    Modifier
                        .size(width = 28.dp, height = 2.dp)
                        .background(if (selected) c.primary else Color.Transparent),
                )
            }
        }
    }
}
