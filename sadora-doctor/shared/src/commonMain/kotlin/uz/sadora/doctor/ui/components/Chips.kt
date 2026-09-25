package uz.sadora.doctor.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uz.sadora.doctor.design.MinTouchTarget
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.Spacing

/** Selectable chip — symptoms, goals, filters. Selected state uses a tinted fill. */
@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: String? = null,
) {
    val c = Sadora.colors
    val bg by animateColorAsState(
        if (selected) c.primary.copy(alpha = if (c.isDark) 0.22f else 0.12f) else c.surface,
    )
    val border by animateColorAsState(if (selected) c.primary else c.line)
    val fg by animateColorAsState(if (selected) c.textAccent else c.text)

    Box(
        modifier = modifier
            .clip(Radius.chip)
            .background(bg)
            .border(1.dp, border, Radius.chip)
            .defaultMinSize(minHeight = MinTouchTarget)
            .noRippleToggleable(selected, role = Role.Checkbox) { onClick() }
            .padding(horizontal = 14.dp, vertical = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leading != null) Text(leading, style = Sadora.type.body, color = fg)
            Text(label, style = Sadora.type.body.copy(fontWeight = FontWeight.Medium), color = fg)
            if (selected) Text("✓", style = Sadora.type.body, color = fg, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

/** Row of chips that wraps to as many lines as it needs. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChipFlowRow(
    modifier: Modifier = Modifier,
    horizontalGap: androidx.compose.ui.unit.Dp = Spacing.xs,
    verticalGap: androidx.compose.ui.unit.Dp = Spacing.xs,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(horizontalGap),
        verticalArrangement = Arrangement.spacedBy(verticalGap),
    ) { content() }
}
