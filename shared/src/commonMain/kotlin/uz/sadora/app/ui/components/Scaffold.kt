package uz.sadora.app.ui.components

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
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
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
import uz.sadora.app.i18n.strings
import uz.sadora.app.design.IconSize
import uz.sadora.app.design.MinTouchTarget
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing

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
            CircleIconButton(SadoraIcons.ChevronLeft, contentDescription = strings.common.back, onClick = onBack)
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
 *
 * Every entry arrives with the app's rise-and-fade, staggered in reading order, so a
 * screen opens the way Today does rather than snapping into place. [stagger] is off for
 * the few screens that time their own cards.
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
    stagger: Boolean = true,
    content: LazyListScopeContent,
) {
    // The entrance belongs to the first screenful. Whatever a scroll brings in later is
    // drawn in place — see [EntranceGate] for what it looked like when it was not.
    val listState = rememberLazyListState()
    EntranceGated(listState) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(verticalGap),
        ) {
            if (stagger) StaggeredListScope(this).content() else content()
        }
    }
}

/** Entries past this one all arrive together; a longer queue would read as lag. */
private const val MaxStaggered = 6

/**
 * A [LazyListScope] that wraps each entry in [appearFromBelow], numbering them as they
 * are declared. Delegation keeps every extension — `items(list)`, `itemsIndexed` — working
 * unchanged, since they all end in the two members overridden here.
 */
private class StaggeredListScope(private val inner: LazyListScope) : LazyListScope by inner {
    private var declared = 0

    private fun delayFor(position: Int): Int = position.coerceAtMost(MaxStaggered) * Motion.Stagger

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        val position = declared++
        inner.item(key, contentType) {
            Box(Modifier.appearFromBelow(delayMillis = this@StaggeredListScope.delayFor(position))) { content() }
        }
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
    ) {
        val first = declared
        declared += count
        inner.items(count, key, contentType) { index ->
            Box(Modifier.appearFromBelow(delayMillis = this@StaggeredListScope.delayFor(first + index))) {
                itemContent(index)
            }
        }
    }
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
                strings.today.hello(name),
                style = Sadora.type.h1,
                color = c.text,
            )
            // Blank when the caller draws its own line under the header — Today does,
            // because its greeting animates and this Column would clip the movement.
            if (greeting.isNotEmpty()) {
                Text(greeting, style = Sadora.type.body, color = c.muted)
            }
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
