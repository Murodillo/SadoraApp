package uz.sadora.doctor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.design.IconSize
import uz.sadora.doctor.design.MinTouchTarget
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.SadoraIcons
import uz.sadora.doctor.design.Spacing

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
 * Standard scrollable screen body: 20dp side padding, 12dp rhythm, and bottom padding
 * that keeps the last card clear of the gesture bar and of a toast.
 *
 * Every entry arrives with the app's rise-and-fade, staggered in reading order, so a
 * screen opens the way the client app's do rather than snapping into place. [stagger]
 * is off for the forms, where the fields should simply be there.
 *
 * [animateItems] is for the lists that change while she watches — the work list losing
 * a question she answered: an entry that leaves fades out and the rest slide up into its
 * place, instead of the list jumping. Entries it is used for need stable keys.
 */
@Composable
fun ScreenContent(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        start = Spacing.screen,
        end = Spacing.screen,
        top = Spacing.xs,
        bottom = 96.dp,
    ),
    verticalGap: androidx.compose.ui.unit.Dp = Spacing.sm,
    stagger: Boolean = true,
    animateItems: Boolean = false,
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
            if (stagger || animateItems) StaggeredListScope(this, stagger, animateItems).content() else content()
        }
    }
}

/** Entries past this one all arrive together; a longer queue would read as lag. */
private const val MaxStaggered = 6

/**
 * A [LazyListScope] that wraps each entry in [appearFromBelow], numbering them as they
 * are declared, and — when asked — in `animateItem`, which has to sit on the entry's
 * root to work. Delegation keeps every extension — `items(list)`, `itemsIndexed` —
 * working unchanged, since they all end in the two members overridden here.
 */
private class StaggeredListScope(
    private val inner: LazyListScope,
    private val stagger: Boolean,
    private val animateItems: Boolean,
) : LazyListScope by inner {
    private var declared = 0

    private fun delayFor(position: Int): Int = position.coerceAtMost(MaxStaggered) * Motion.Stagger

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        // Read here, outside the item's lambda: the lazy DSL does not let an item reach
        // back to the scope it was declared in.
        val animate = animateItems
        val delay = if (stagger) delayFor(declared++) else null
        inner.item(key, contentType) {
            MotionEntry(animate, delay) { content() }
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
        val animate = animateItems
        val delays = if (stagger) IntArray(count) { delayFor(first + it) } else null
        inner.items(count, key, contentType) { index ->
            MotionEntry(animate, delays?.get(index)) { itemContent(index) }
        }
    }
}

/**
 * One entry's motion: `animateItem` on its root when the list asks for it, and the
 * staggered rise-and-fade when there is a [delayMillis] for it.
 */
@Composable
private fun LazyItemScope.MotionEntry(animate: Boolean, delayMillis: Int?, content: @Composable () -> Unit) {
    Box(
        Modifier
            .then(
                if (animate) {
                    Modifier.animateItem(
                        fadeInSpec = tween(Motion.Standard),
                        placementSpec = tween(Motion.Standard, easing = Motion.Emphasized),
                        fadeOutSpec = tween(Motion.Standard),
                    )
                } else {
                    Modifier
                },
            )
            .then(if (delayMillis != null) Modifier.appearFromBelow(delayMillis = delayMillis) else Modifier),
    ) { content() }
}

typealias LazyListScopeContent = androidx.compose.foundation.lazy.LazyListScope.() -> Unit
