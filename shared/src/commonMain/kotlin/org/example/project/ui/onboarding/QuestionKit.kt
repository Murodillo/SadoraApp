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
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import org.example.project.model.Fmt
import org.example.project.model.deviceToday
import org.example.project.ui.components.SadoraMark
import org.example.project.ui.components.cardSurface
import org.example.project.ui.components.noRippleClickable
import kotlin.math.abs
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.example.project.i18n.strings

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
 * The brand block every registration page opens with: the mark, the wordmark, and a
 * blossom drifting in the top-right corner.
 *
 * It is the deck's signature on these screens — the same three elements on every
 * question, so a run of twenty pages reads as one flow rather than as a form.
 */
@Composable
private fun QuestionBrand(modifier: Modifier = Modifier) {
    val c = Sadora.colors
    Box(modifier.fillMaxWidth().height(96.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            // Two soft blooms in the corner, drawn from the same flower as the splash.
            drawBloom(
                center = Offset(size.width * 0.88f, size.height * 0.30f),
                radius = 26.dp.toPx(),
                petals = 6,
                rotation = 12f,
                color = c.secondary.copy(alpha = 0.30f),
                coreColor = c.secondary.copy(alpha = 0.45f),
                stem = 0f,
                stemColor = Color.Transparent,
                alpha = 1f,
                scale = 1f,
            )
            drawBloom(
                center = Offset(size.width * 0.97f, size.height * 0.62f),
                radius = 16.dp.toPx(),
                petals = 5,
                rotation = -20f,
                color = c.primary.copy(alpha = 0.22f),
                coreColor = c.primary.copy(alpha = 0.35f),
                stem = 0f,
                stemColor = Color.Transparent,
                alpha = 1f,
                scale = 1f,
            )
        }
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SadoraMark(size = 56.dp)
            Text(
                "SADORA",
                style = Sadora.type.caption.copy(
                    letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
                    fontWeight = FontWeight.Medium,
                ),
                color = c.muted,
            )
        }
    }
}

/**
 * The frame every onboarding question shares: back, progress, the brand block, a
 * staged headline, a scrolling body, and a footer that carries the primary button
 * with the skip link under it.
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
    /** The wording of the skip link under the button. */
    skipLabel: String = "O'tkazish",
    /** Set false on the pages that need every pixel for their own content. */
    brand: Boolean = true,
    /** Sits in the top-right corner, level with the progress bar. */
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
            if (topEnd != null) topEnd() else Spacer(Modifier.size(Spacing.xs))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (brand) {
                Reveal(e, from = 0.0f) { QuestionBrand() }
            } else {
                Spacer(Modifier.height(Spacing.md))
            }
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
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            footer()
            // The skip sits under the button, the way the deck draws it: declining is
            // an ordinary choice, not something hidden in a corner of the header.
            if (onSkip != null) {
                Text(
                    skipLabel,
                    style = Sadora.type.body,
                    color = c.muted,
                    modifier = Modifier
                        .defaultMinSize(minHeight = MinTouchTarget)
                        .noRippleClickable(onClick = onSkip)
                        .padding(top = Spacing.xs),
                )
            }
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
            .height(6.dp)
            .clip(Radius.chip)
            .background(c.line),
    ) {
        Box(
            Modifier
                .fillMaxWidth(width)
                .height(6.dp)
                .clip(Radius.chip)
                .background(c.heroGradient),
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
 * The radio on the right of an answer row: a ring while unselected, a filled disc
 * with a tick once chosen.
 */
@Composable
private fun AnswerRadio(selected: Boolean) {
    val c = Sadora.colors
    val fill by animateColorAsState(
        if (selected) c.primary else Color.Transparent,
        tween(220),
        label = "radio-fill",
    )
    val ring by animateColorAsState(
        if (selected) c.primary else c.line,
        tween(220),
        label = "radio-ring",
    )
    Box(
        Modifier
            .size(24.dp)
            .clip(Radius.chip)
            .background(fill)
            .border(2.dp, ring, Radius.chip),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(selected, enter = fadeIn(tween(160)), exit = fadeOut(tween(120))) {
            Icon(
                SadoraIcons.Check,
                contentDescription = null,
                Modifier.size(14.dp),
                tint = c.onPrimary,
            )
        }
    }
}

/**
 * One answer in a single-choice question — the deck's option card.
 *
 * A white card with a tinted icon disc, the label, and a radio on the right; the
 * purple border and the filled radio are what carry selection, so a chosen row still
 * reads as the same object rather than flipping to a solid block. Selecting expands
 * the answer's [note] into view, and the rows below slide down around it.
 */
@Composable
fun AnswerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    /** An emoji shown in the leading disc. [icon] is preferred where one exists. */
    leading: String? = null,
    icon: ImageVector? = null,
    /** Colours the leading disc; the primary colour when not set. */
    tint: Color? = null,
    /**
     * Keeps [note] on screen while the row is unselected.
     *
     * Permissions need this: what a permission is for has to be readable *before*
     * granting it, not as a reward for having granted it.
     */
    noteAlwaysVisible: Boolean = false,
) {
    val c = Sadora.colors
    val discColour = tint ?: c.primary
    val border by animateColorAsState(
        if (selected) c.primary else c.line.copy(alpha = if (c.isDark) 1f else 0.7f),
        tween(240),
        label = "answer-border",
    )
    val borderWidth by animateDpAsState(
        if (selected) 1.5.dp else 1.dp,
        tween(240),
        label = "answer-border-width",
    )

    Column(
        modifier
            .fillMaxWidth()
            .clip(Radius.tile)
            .background(c.surface)
            .border(borderWidth, border, Radius.tile)
            .noRippleClickable(onClick = onClick)
            .animateContentSize(tween(300, easing = FastOutSlowInEasing))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            Modifier.defaultMinSize(minHeight = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            when {
                icon != null -> Box(
                    Modifier
                        .size(38.dp)
                        .clip(Radius.chip)
                        .background(discColour.copy(alpha = if (c.isDark) 0.24f else 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, Modifier.size(IconSize.md), tint = discColour)
                }

                leading != null -> Box(
                    Modifier
                        .size(38.dp)
                        .clip(Radius.chip)
                        .background(discColour.copy(alpha = if (c.isDark) 0.24f else 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(leading, style = Sadora.type.h3)
                }
            }
            Text(
                label,
                style = Sadora.type.h3.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = c.text,
                modifier = Modifier.weight(1f),
            )
            AnswerRadio(selected)
        }
        if (note != null) {
            AnimatedVisibility(
                visible = selected || noteAlwaysVisible,
                enter = expandVertically(tween(300, easing = FastOutSlowInEasing)) +
                    fadeIn(tween(240, delayMillis = 80)),
                exit = shrinkVertically(tween(220)) + fadeOut(tween(120)),
            ) {
                Text(
                    note,
                    style = Sadora.type.body,
                    color = c.muted,
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
        if (selected) c.primary else c.line.copy(alpha = if (c.isDark) 1f else 0.7f),
        tween(240),
        label = "tile-ring",
    )
    val disc by animateColorAsState(
        if (selected) c.primary.copy(alpha = if (c.isDark) 0.3f else 0.16f) else c.surface2,
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
            .clip(Radius.tile)
            .background(c.surface)
            .border(if (selected) 1.5.dp else 1.dp, ring, Radius.tile)
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
                if (selected) c.primary else c.muted,
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
                .clip(Radius.tile)
                .background(c.primary.copy(alpha = if (c.isDark) 0.24f else 0.1f)),
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
                            color = if (focused) {
                                c.textAccent
                            } else {
                                c.text.copy(
                                    alpha = when (distance) {
                                        1 -> 0.45f
                                        2 -> 0.22f
                                        else -> 0.12f
                                    },
                                )
                            },
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

private val weekdayInitials = listOf("Du", "Se", "Ch", "Pa", "Ju", "Sh", "Ya")

/**
 * One month at a time, with arrows either side of the month's name — the deck's
 * cycle-setup calendar.
 *
 * Paging beats scrolling here: the answer is almost always in the current month or
 * the one before it, and a single grid keeps the day cells large enough to hit.
 * [monthsBack] and [monthsForward] bound where the arrows can go; days outside
 * [range] are drawn but inert, so the shape of the month stays readable while the
 * invalid half is obviously not tappable.
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
    val thisMonth = remember(today) { LocalDate(today.year, today.month, 1) }
    val firstMonth = remember(thisMonth, monthsBack) { thisMonth.minus(monthsBack, DateTimeUnit.MONTH) }
    val lastMonth = remember(thisMonth, monthsForward) { thisMonth.plus(monthsForward, DateTimeUnit.MONTH) }
    // Forward-only pickers (a due date) open on the first month they can offer.
    var month by remember { mutableStateOf(if (monthsForward > 0 && monthsBack == 0) thisMonth else thisMonth) }

    Column(
        modifier.fillMaxWidth().cardSurface(c).padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val canGoBack = month > firstMonth
            val canGoForward = month < lastMonth
            Icon(
                SadoraIcons.ChevronLeft,
                contentDescription = "Oldingi oy",
                Modifier
                    .size(IconSize.lg)
                    .noRippleClickable(enabled = canGoBack) {
                        month = month.minus(1, DateTimeUnit.MONTH)
                    },
                tint = if (canGoBack) c.text else c.muted2.copy(alpha = 0.4f),
            )
            Text(
                strings.dates.monthYear(month.year, month.month.ordinal + 1),
                style = Sadora.type.h3,
                color = c.text,
            )
            Icon(
                SadoraIcons.ChevronRight,
                contentDescription = "Keyingi oy",
                Modifier
                    .size(IconSize.lg)
                    .noRippleClickable(enabled = canGoForward) {
                        month = month.plus(1, DateTimeUnit.MONTH)
                    },
                tint = if (canGoForward) c.text else c.muted2.copy(alpha = 0.4f),
            )
        }

        Row(Modifier.fillMaxWidth()) {
            weekdayInitials.forEach { initial ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        initial,
                        style = Sadora.type.caption.copy(
                            letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
                        ),
                        color = c.muted2,
                    )
                }
            }
        }

        MonthGrid(
            month = month,
            today = today,
            isSelected = isSelected,
            range = range,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun MonthGrid(
    month: LocalDate,
    today: LocalDate,
    isSelected: (LocalDate) -> Boolean,
    range: ClosedRange<LocalDate>,
    onSelect: (LocalDate) -> Unit,
) {
    val days = month.daysInMonth()
    // Monday is column 0, matching the header above.
    val lead = month.dayOfWeek.isoDayNumber - 1

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
    val label by animateColorAsState(
        when {
            isSelected -> c.onPrimary
            !enabled -> c.muted2.copy(alpha = 0.4f)
            else -> c.text
        },
        tween(220),
        label = "day-label",
    )
    val ring by animateColorAsState(
        if (isToday && !isSelected) c.primary else Color.Transparent,
        tween(220),
        label = "day-ring",
    )

    Box(
        modifier.height(44.dp).padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(Radius.chip)
                .then(
                    // Selected days carry the brand gradient, the way the deck marks
                    // the chosen date; today is a ring so the two never read alike.
                    if (isSelected) Modifier.background(c.heroGradient) else Modifier,
                )
                .border(1.5.dp, ring, Radius.chip)
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
