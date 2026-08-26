package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.design.MinTouchTarget
import org.example.project.design.Sadora
import org.example.project.design.Spacing

/**
 * Screen top bar. Supports the two shapes in the design: a plain title with an
 * optional back chevron, and a title with a trailing action.
 */
@Composable
fun SadoraTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    /** Step indicator such as "3/9" shown next to the back chevron. */
    step: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Sadora.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(MinTouchTarget)
                    .clip(RoundedCornerShape(999.dp))
                    .background(c.surface2)
                    .noRippleClickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", style = Sadora.type.h2, color = c.text)
            }
        }
        if (step != null) {
            Text(step, style = Sadora.type.body, color = c.muted)
        }
        if (title.isNotEmpty()) {
            Text(
                title,
                style = Sadora.type.h1,
                color = c.text,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        trailing?.invoke()
    }
}

/**
 * Standard scrollable screen body: 20dp side padding, 16dp rhythm, and bottom
 * padding that clears the tab bar.
 */
@Composable
fun ScreenContent(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        start = Spacing.screen,
        end = Spacing.screen,
        top = Spacing.xs,
        bottom = 120.dp,
    ),
    verticalGap: androidx.compose.ui.unit.Dp = Spacing.sm,
    content: LazyListScopeContent,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(verticalGap),
    ) { content() }
}

typealias LazyListScopeContent = androidx.compose.foundation.lazy.LazyListScope.() -> Unit

/** A non-scrolling screen body with the same paddings — used by onboarding steps. */
@Composable
fun StaticScreenContent(
    modifier: Modifier = Modifier,
    verticalGap: androidx.compose.ui.unit.Dp = Spacing.md,
    content: @Composable ColumnScope.() -> Unit,
) = Column(
    modifier = modifier
        .fillMaxSize()
        .padding(horizontal = Spacing.screen),
    verticalArrangement = Arrangement.spacedBy(verticalGap),
    content = content,
)

/**
 * Greeting header on the Today screen: avatar, "Xayrli tong · Malika", bell.
 */
@Composable
fun GreetingHeader(
    greeting: String,
    name: String,
    modifier: Modifier = Modifier,
    onAvatarClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    hasUnread: Boolean = true,
) {
    val c = Sadora.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Avatar(name, onClick = onAvatarClick)
        Column(Modifier.weight(1f)) {
            Text(greeting, style = Sadora.type.body, color = c.muted)
            Text(name, style = Sadora.type.h1, color = c.text)
        }
        Box(
            Modifier
                .size(MinTouchTarget)
                .clip(RoundedCornerShape(999.dp))
                .background(c.surface2)
                .noRippleClickable(onClick = onNotificationsClick),
            contentAlignment = Alignment.Center,
        ) {
            Text("🔔", style = Sadora.type.h3)
            if (hasUnread) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(11.dp)
                        .size(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(c.primary),
                )
            }
        }
    }
}

/** Gradient initial avatar. */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    onClick: (() -> Unit)? = null,
) {
    val c = Sadora.colors
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(999.dp))
            .background(Brush.linearGradient(listOf(c.secondary, c.primary)))
            .then(if (onClick != null) Modifier.noRippleClickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).uppercase(),
            style = Sadora.type.h3.copy(fontWeight = FontWeight.Bold),
            color = if (c.isDark) c.bg else Color.White,
        )
    }
}

/** Spacer sized to the design's vertical rhythm. */
@Composable
fun VGap(height: androidx.compose.ui.unit.Dp = Spacing.md) = Spacer(Modifier.height(height))
