package org.example.project.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import org.example.project.design.MinTouchTarget
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.nav.Tab

/**
 * The five-tab bar.
 *
 * The AI tab is a raised gradient FAB — one of the four places the hero gradient is
 * allowed. The second tab's label follows the user's life stage, so a pregnant user
 * sees "Homilador" rather than "Sikl".
 */
@Composable
fun SadoraBottomNav(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    journeyLabel: String,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surface)
            .navigationBarsPadding()
            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tab.entries.forEach { tab ->
            val label = if (tab == Tab.Journey) journeyLabel else tab.label
            if (tab == Tab.Ai) {
                AiTabButton(
                    selected = selected == tab,
                    label = label,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                NavItem(
                    glyph = tab.glyph,
                    label = label,
                    selected = selected == tab,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    glyph: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val tint by animateColorAsState(if (selected) c.textAccent else c.muted)
    Column(
        modifier = modifier
            .clip(Radius.cardSmall)
            .noRippleClickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(glyph, style = Sadora.type.h3, color = tint)
        Text(label, style = Sadora.type.caption.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), color = tint)
    }
}

@Composable
private fun AiTabButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    Column(
        modifier = modifier
            .clip(Radius.cardSmall)
            .noRippleClickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier
                .size(MinTouchTarget)
                .clip(RoundedCornerShape(999.dp))
                .background(Brush.linearGradient(listOf(c.secondary, c.primary))),
            contentAlignment = Alignment.Center,
        ) {
            Text("✦", style = Sadora.type.h2, color = if (c.isDark) c.bg else androidx.compose.ui.graphics.Color.White)
        }
        Text(
            label,
            style = Sadora.type.caption.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
            color = if (selected) c.textAccent else c.muted,
        )
    }
}
