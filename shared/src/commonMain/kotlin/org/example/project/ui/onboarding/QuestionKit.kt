package org.example.project.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.project.design.IconSize
import org.example.project.design.MinTouchTarget
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import org.example.project.model.AppLanguage
import org.example.project.ui.components.noRippleClickable
import kotlin.math.abs
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

// ---------------------------------------------------------------- reveal

/**
 * The shared entry ramp for a question page.
 *
 * Every page builds one of these and hands it to [Reveal] blocks, so a whole screen
 * assembles from a single animation clock. That matters more than it sounds: with a
 * timer per block, a slow first frame after the page transition would scatter the
 * reveal, and the staggering would drift against the incoming slide.
 */
@Composable
fun rememberPageEntry(durationMillis: Int = 900): Animatable<Float, *> {
    val entry = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entry.animateTo(1f, tween(durationMillis, easing = LinearOutSlowInEasing))
    }
    return entry
}

/**
 * Fades and lifts [content] once the page's [entry] ramp passes [from].
 *
 * The lift is a layer translation, never padding, so nothing below reflows while the
 * reveal plays — each block holds its final position from the first frame.
 */
@Composable
fun Reveal(
    entry: Float,
    from: Float,
    modifier: Modifier = Modifier,
    lift: Int = 16,
    content: @Composable () -> Unit,
) {
    val span = (1f - from).coerceAtLeast(0.15f)
    val progress = ((entry - from) / span).coerceIn(0f, 1f)
    val liftPx = with(LocalDensity.current) { lift.dp.toPx() }
    Box(
        modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * liftPx
        },
    ) {
        content()
    }
}

/** Where the nth option in a list starts on the entry ramp. */
fun optionStart(index: Int, base: Float = 0.34f, step: Float = 0.07f) =
    (base + index * step).coerceAtMost(0.88f)

// ---------------------------------------------------------------- scaffold

/**
 * The frame every onboarding question shares: progress, back, skip, a staged
 * headline, a scrolling body, and a footer that appears once there is an answer.
 *
 * [progress] is the position in the whole question sequence, 0..1. It animates
 * rather than jumping, which is the one piece of continuity across a page
 * transition — everything else on screen is replaced.
 */
@Composable
fun QuestionScaffold(
    title: String,
    progress: Float,
    onBack: (() -> Unit)?,
    onSkip: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    entry: Animatable<Float, *> = rememberPageEntry(),
    /** Sits in the top-right corner, above the progress bar. Used for the language switch. */
    topEnd: @Composable (() -> Unit)? = null,
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Sadora.colors
    val e = entry.value
    val focus = LocalFocusManager.current

    Column(
        modifier
            .fillMaxSize()
            // Whichever is taller. Padding for both would double-count, because the
            // keyboard's inset already covers the navigation bar it sits on top of.
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            // A tap on the empty space around the fields puts the keyboard away.
            // Fields and buttons consume the press themselves, so this only ever fires
            // where there is nothing to press — which is exactly where someone taps
            // when they want the keyboard gone.
            .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } },
    ) {
        if (topEnd != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs),
                horizontalArrangement = Arrangement.End,
            ) {
                topEnd()
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // The chevron holds its slot even on the first page, so the progress bar
            // does not shift sideways between questions.
            Box(Modifier.size(MinTouchTarget), contentAlignment = Alignment.Center) {
                if (onBack != null) {
                    Icon(
                        SadoraIcons.ChevronLeft,
                        contentDescription = "Ortga",
                        Modifier
                            .size(IconSize.lg)
                            .noRippleClickable(onClick = onBack),
                        tint = c.text,
                    )
                }
            }
            ProgressLine(progress, Modifier.weight(1f))
            Text(
                "O'tkazish",
                style = Sadora.type.body,
                color = if (onSkip != null) c.muted else Color.Transparent,
                modifier = Modifier
                    .defaultMinSize(minHeight = MinTouchTarget)
                    .noRippleClickable(enabled = onSkip != null) { onSkip?.invoke() }
                    .padding(top = 12.dp),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Spacer(Modifier.height(Spacing.md))
            Reveal(e, from = 0.05f) {
                Text(
                    title,
                    style = Sadora.type.h1,
                    color = c.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (subtitle != null) {
                Reveal(e, from = 0.16f) {
                    Text(
                        subtitle,
                        style = Sadora.type.body,
                        color = c.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            content()
            Spacer(Modifier.height(Spacing.lg))
        }

        // fillMaxWidth matters: on the pages whose primary button is still hidden the
        // column would otherwise shrink to its only child and strand the secondary
        // link against the left edge.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            footer()
        }
    }
}

/** The hairline progress bar across the top of every question. */
@Composable
private fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    val width by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "progress",
    )
    Box(
        modifier
            .height(3.dp)
            .clip(Radius.chip)
            .background(c.line),
    ) {
        Box(
            Modifier
                .fillMaxWidth(width)
                .height(3.dp)
                .clip(Radius.chip)
                .background(c.text),
        )
    }
}

/**
 * The footer button, which slides up the first time there is something to confirm.
 *
 * Questions that answer themselves on tap have no footer at all; this is for the
 * ones that need a deliberate second action.
 */
@Composable
fun ColumnScope.AnswerFooter(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 2 } +
            fadeIn(tween(220)),
        exit = fadeOut(tween(140)),
    ) {
        content()
    }
}

// ---------------------------------------------------------------- answers

/**
 * One answer in a single-choice question.
 *
 * Selecting fills the row and, when the answer carries a [note], expands it into
 * view. The expansion is the reason the row owns its own layout animation: the rows
 * below have to slide down around it, and doing that with a size animation keeps
 * the whole list moving as one piece rather than snapping.
 */
@Composable
fun AnswerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    leading: String? = null,
    /**
     * Keeps [note] on screen while the row is unselected.
     *
     * Permissions need this: what a permission is for has to be readable *before*
     * granting it, not as a reward for having granted it.
     */
    noteAlwaysVisible: Boolean = false,
) {
    val c = Sadora.colors
    val background by animateColorAsState(
        if (selected) c.primary else c.surface2,
        tween(260, easing = FastOutSlowInEasing),
        label = "answer-bg",
    )
    val label1 by animateColorAsState(
        if (selected) c.onPrimary else c.text,
        tween(260),
        label = "answer-fg",
    )

    Column(
        modifier
            .fillMaxWidth()
            .clip(Radius.cardSmall)
            .background(background)
            .noRippleClickable(onClick = onClick)
            .animateContentSize(tween(300, easing = FastOutSlowInEasing))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            Modifier.defaultMinSize(minHeight = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (leading != null) Text(leading, style = Sadora.type.h2, color = label1)
            Text(label, style = Sadora.type.h3, color = label1, modifier = Modifier.weight(1f))
        }
        if (note != null) {
            val noteColour by animateColorAsState(
                if (selected) c.onPrimary.copy(alpha = 0.88f) else c.muted,
                tween(260),
                label = "answer-note",
            )
            AnimatedVisibility(
                visible = selected || noteAlwaysVisible,
                enter = expandVertically(tween(300, easing = FastOutSlowInEasing)) +
                    fadeIn(tween(240, delayMillis = 80)),
                exit = shrinkVertically(tween(220)) + fadeOut(tween(120)),
            ) {
                Text(
                    note,
                    style = Sadora.type.body,
                    color = noteColour,
                    modifier = Modifier.padding(bottom = Spacing.xxs),
                )
            }
        }
    }
}

/**
 * One tile in a multi-choice grid.
 *
 * Selection is carried by a ring and a tinted disc rather than a filled card: with
 * eight tiles on screen, filling them would leave the grid unreadable once more
 * than two are picked.
 */
@Composable
fun AnswerTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val ring by animateColorAsState(
        if (selected) c.primary else Color.Transparent,
        tween(240),
        label = "tile-ring",
    )
    val disc by animateColorAsState(
        if (selected) c.primary.copy(alpha = 0.18f) else c.surface2,
        tween(240),
        label = "tile-disc",
    )
    val scale by animateFloatAsState(
        if (selected) 1f else 0.97f,
        tween(240, easing = FastOutSlowInEasing),
        label = "tile-scale",
    )

    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(Radius.card)
            .background(c.surface)
            .border(1.5.dp, ring, Radius.card)
            .noRippleClickable(onClick = onClick)
            .padding(vertical = Spacing.md, horizontal = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            Modifier.size(56.dp).clip(Radius.chip).background(disc),
            contentAlignment = Alignment.Center,
        ) {
            val tint by animateColorAsState(
                if (selected) c.primary else c.secondary,
                tween(240),
                label = "tile-icon",
            )
            Icon(icon, contentDescription = null, Modifier.size(26.dp), tint = tint)
        }
        // Two lines always, so a one-word label and a wrapping one produce tiles of the
        // same height. Reserving the line is what keeps the grid even; sizing the row
        // to its tallest tile would still leave the rows uneven against each other.
        Text(
            label,
            style = Sadora.type.body.copy(fontWeight = FontWeight.Medium),
            color = c.text,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
        )
    }
}

// ---------------------------------------------------------------- wheel

/**
 * A snapping value wheel, used wherever the answer is a number on a known scale.
 *
 * The centred row is read back from the list's own scroll position rather than
 * tracked separately, so a fling that overshoots and settles still reports the row
 * it actually lands on.
 */
@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
) {
    val c = Sadora.colors
    val rowHeight = 46.dp
    val visibleRows = 5
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val fling = rememberSnapFlingBehavior(listState)

    val centred by remember {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            if (listState.firstVisibleItemScrollOffset > rowPx / 2f) first + 1 else first
        }
    }
    LaunchedEffect(centred) { onSelect(centred.coerceIn(0, items.lastIndex)) }

    Box(modifier.height(rowHeight * visibleRows), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .clip(Radius.field)
                .background(c.surface2),
        )
        LazyColumn(
            state = listState,
            flingBehavior = fling,
            modifier = Modifier.fillMaxSize(),
            // Half the visible rows above and below, so index 0 can sit on the centre line.
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                vertical = rowHeight * (visibleRows / 2),
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(items.size) { index ->
                val distance = abs(index - centred)
                val focused = distance == 0
                Box(
                    Modifier.fillMaxWidth().height(rowHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            items[index],
                            style = if (focused) Sadora.type.h1 else Sadora.type.h2,
                            color = c.text.copy(
                                alpha = when (distance) {
                                    0 -> 1f
                                    1 -> 0.45f
                                    2 -> 0.22f
                                    else -> 0.12f
                                },
                            ),
                        )
                        if (focused && suffix != null) {
                            Text(
                                suffix,
                                style = Sadora.type.body,
                                color = c.muted,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- calendar

private val monthNames = listOf(
    "Yanvar", "Fevral", "Mart", "Aprel", "May", "Iyun",
    "Iyul", "Avgust", "Sentabr", "Oktabr", "Noyabr", "Dekabr",
)

private val weekdayInitials = listOf("D", "S", "C", "P", "J", "S", "Y")

/** Today, in the device's own zone. The onboarding calendar has no server date yet. */
fun deviceToday(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

/**
 * A scrolling run of month grids for picking one date.
 *
 * Months are rendered rather than paged because the answer is almost always within a
 * few weeks either side of today, and a pager would make the common case — glance,
 * tap — into a navigation exercise. Days outside [range] are drawn but not tappable,
 * so the shape of the month stays readable while the invalid half is obviously inert.
 */
@Composable
fun CalendarPicker(
    isSelected: (LocalDate) -> Boolean,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = deviceToday(),
    monthsBack: Int = 2,
    monthsForward: Int = 0,
    range: ClosedRange<LocalDate> = today.minus(2, DateTimeUnit.MONTH)..today,
) {
    val c = Sadora.colors
    val first = remember(today, monthsBack) {
        LocalDate(today.year, today.month, 1).minus(monthsBack, DateTimeUnit.MONTH)
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(Modifier.fillMaxWidth()) {
            weekdayInitials.forEach { initial ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(initial, style = Sadora.type.caption, color = c.muted2)
                }
            }
        }
        repeat(monthsBack + monthsForward + 1) { offset ->
            MonthBlock(
                month = first.plus(offset, DateTimeUnit.MONTH),
                today = today,
                isSelected = isSelected,
                range = range,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun MonthBlock(
    month: LocalDate,
    today: LocalDate,
    isSelected: (LocalDate) -> Boolean,
    range: ClosedRange<LocalDate>,
    onSelect: (LocalDate) -> Unit,
) {
    val c = Sadora.colors
    val days = month.daysInMonth()
    // Monday is column 0, matching the header above.
    val lead = month.dayOfWeek.isoDayNumber - 1

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "${monthNames[month.month.ordinal]} ${month.year}",
            style = Sadora.type.h3,
            color = c.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xxs),
        )
        val cells = List(lead) { null } + (1..days).map { LocalDate(month.year, month.month, it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    if (date == null) {
                        Box(Modifier.weight(1f))
                    } else {
                        DayCell(
                            date = date,
                            isToday = date == today,
                            isSelected = isSelected(date),
                            enabled = date in range,
                            onSelect = onSelect,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    enabled: Boolean,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val fill by animateColorAsState(
        when {
            isSelected -> c.primary
            isToday -> c.surface2
            else -> Color.Transparent
        },
        tween(220),
        label = "day-fill",
    )
    val label by animateColorAsState(
        when {
            isSelected -> c.onPrimary
            !enabled -> c.muted2.copy(alpha = 0.4f)
            else -> c.text
        },
        tween(220),
        label = "day-label",
    )

    Box(
        modifier.height(44.dp).padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(Radius.chip)
                .background(fill)
                .noRippleClickable(enabled = enabled) { onSelect(date) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.day.toString(),
                style = if (isSelected || isToday) {
                    Sadora.type.body.copy(fontWeight = FontWeight.Bold)
                } else {
                    Sadora.type.body
                },
                color = label,
            )
        }
    }
}

/** Days in the month [this] falls in. */
private fun LocalDate.daysInMonth(): Int =
    LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day

// ---------------------------------------------------------------- language

/**
 * The three-way language switch, small enough to live in a page's top corner.
 *
 * It is a switch rather than a question because language is the one answer someone
 * needs *before* she can read the rest: burying it behind a step of its own asks her
 * to navigate a flow she may not understand yet.
 *
 * The pill slides between segments rather than cutting, and it is the only thing that
 * moves — the labels stay put and only their colour crosses over.
 */
@Composable
fun LanguageSwitch(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val options = remember { AppLanguage.entries.toList() }
    val index = options.indexOf(selected).coerceAtLeast(0)
    val segment = 44.dp

    val offset by animateDpAsState(
        targetValue = segment * index,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "language-offset",
    )

    Box(
        modifier
            .clip(Radius.chip)
            .background(c.surface2)
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(width = segment, height = 30.dp)
                .clip(Radius.chip)
                .background(c.surface),
        )
        Row {
            options.forEach { language ->
                val active = language == selected
                val label by animateColorAsState(
                    if (active) c.text else c.muted2,
                    tween(320),
                    label = "language-label",
                )
                Box(
                    Modifier
                        .size(width = segment, height = 30.dp)
                        .clip(Radius.chip)
                        .noRippleClickable { onSelect(language) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        language.code,
                        style = Sadora.type.caption.copy(
                            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        ),
                        color = label,
                    )
                }
            }
        }
    }
}
