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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.project.design.IconSize
import org.example.project.design.MinTouchTarget
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing

/**
 * Screen top bar. Supports the two shapes in the design: a plain title with an
 * optional back chevron, and a title with a trailing action.
 *
 * [centered] draws the title in the middle of the bar, which is how the deck's inner
 * screens ("Mening siklim", "Ovqatlanish", "Skan natijasi") read.
 */
@Composable
fun SadoraTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    /** Step indicator such as "3/9" shown next to the back chevron. */
    step: String? = null,
    centered: Boolean = false,
    subtitle: String? = null,
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
            CircleIconButton(SadoraIcons.ChevronLeft, contentDescription = "Ortga", onClick = onBack)
        }
        if (step != null) {
            Text(step, style = Sadora.type.body, color = c.muted)
        }
        if (title.isNotEmpty()) {
            Column(
                Modifier.weight(1f),
                horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                Text(
                    title,
                    style = if (centered) Sadora.type.h2 else Sadora.type.h1,
                    color = c.text,
                    textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                    maxLines = 1,
                )
                if (subtitle != null) {
                    Text(subtitle, style = Sadora.type.body, color = c.muted, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        trailing?.invoke()
    }
}

/** A round pale button with an icon — back, calendar, info, more. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    val c = Sadora.colors
    Box(
        modifier
            .size(MinTouchTarget)
            .clip(Radius.chip)
            .background(c.surface2)
            .pressable(pressedScale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, Modifier.size(IconSize.md), tint = c.text)
    }
}

/**
 * Standard scrollable screen body: 20dp side padding, 12dp rhythm, and bottom
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
 * Greeting header on the Today screen — the deck's "Salom, Alina!" with a line of
 * encouragement under it and the bell on the right.
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
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(
                if (name.isBlank()) "Salom!" else "Salom, $name!",
                style = Sadora.type.h1,
                color = c.text,
            )
            Text(greeting, style = Sadora.type.body, color = c.muted)
        }
        // The deck puts the face next to the bell: the avatar is the way into the
        // profile, so the greeting itself is left as plain text.
        Avatar(name, size = MinTouchTarget, onClick = onAvatarClick)
        Box {
            CircleIconButton(SadoraIcons.Bell, contentDescription = "Bildirishnomalar", onClick = onNotificationsClick)
            if (hasUnread) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(8.dp)
                        .clip(Radius.chip)
                        .background(c.secondary),
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
            .clip(Radius.chip)
            .background(c.heroGradient)
            .then(if (onClick != null) Modifier.pressable(pressedScale = 0.9f, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).uppercase(),
            style = Sadora.type.h3.copy(fontWeight = FontWeight.Bold),
            color = c.onPrimary,
        )
    }
}

/** Spacer sized to the design's vertical rhythm. */
@Composable
fun VGap(height: androidx.compose.ui.unit.Dp = Spacing.md) = Spacer(Modifier.height(height))
