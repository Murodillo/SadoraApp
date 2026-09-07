package org.example.project.ui.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import androidx.compose.runtime.LaunchedEffect
import org.example.project.data.HealthController
import org.example.project.design.IconSize
import org.example.project.design.PhaseColors
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import org.example.project.i18n.strings
import org.example.project.model.AppState
import org.example.project.model.CyclePhase
import org.example.project.nav.Route
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.DisclaimerNote
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.SegmentedControl
import org.example.project.ui.components.noRippleClickable

/**
 * "Sikl · Kalendar" — the full month view.
 *
 * Past days are filled; predicted days are outlined so a forecast never looks like a
 * recorded fact. Colour is never the only indicator — each state also carries a
 * label in the legend.
 */
@Composable
fun CycleCalendarScreen(
    state: AppState,
    health: HealthController,
    onOpen: (Route) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.journey
    var tab by remember { mutableStateOf(0) }

    // The History tab is the server's cycles, not a projection, so it is asked for.
    LaunchedEffect(Unit) { health.loadHistory() }

    Column(modifier) {
        SadoraTopBar(t.calendarTitle, onBack = onClose, centered = true)

        ScreenContent {
            item {
                SegmentedControl(
                    options = listOf(t.calendarTitle, t.history),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                )
            }

            if (tab == 1) {
                cycleHistoryItems(state, health)
            } else {
                item { MonthGrid(state, onDayClick = { onOpen(Route.CycleDay(it.toString())) }) }

                item { PhaseKey() }

                item { DisclaimerNote(t.predictedNote) }

                item { SelectedDaySummary(state, onOpen = { onOpen(Route.CycleDay(state.today.toString())) }) }

                item {
                    SadoraButton(
                        t.markPeriodDay,
                        onClick = { onOpen(Route.CycleDay(state.today.toString())) },
                        icon = SadoraIcons.Pencil,
                    )
                }
            }
        }
    }
}

/** The number of days in the month [date] falls in. */
private fun LocalDate.daysInMonth(): Int =
    LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day

/**
 * A 7-column month grid with phase colouring.
 *
 * Phases are derived from the cycle anchor the store holds, so the grid shows the
 * month around today rather than a fixed sample. With no anchor the month is drawn
 * plain — a predicted period on a cycle the app knows nothing about would be exactly
 * the kind of unmarked guess the design rules forbid.
 */
@Composable
private fun MonthGrid(state: AppState, onDayClick: (LocalDate) -> Unit) {
    val c = Sadora.colors
    val t = strings.journey
    var offset by remember { mutableStateOf(0) }

    val first = LocalDate(state.today.year, state.today.month, 1).plus(offset, DateTimeUnit.MONTH)
    val hasData = state.cycleStartDate != null && state.hasCyclePrediction

    SadoraCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                SadoraIcons.ChevronLeft,
                contentDescription = t.previousMonth,
                Modifier.size(IconSize.lg).noRippleClickable { offset-- },
                tint = c.muted,
            )
            Text(strings.dates.monthYear(first.year, first.month.ordinal + 1), style = Sadora.type.h3, color = c.text)
            Icon(
                SadoraIcons.ChevronRight,
                contentDescription = t.nextMonth,
                Modifier.size(IconSize.lg).noRippleClickable { offset++ },
                tint = c.muted,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            strings.dates.weekdaysShort.forEach { day ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(day, style = Sadora.type.caption, color = c.muted2)
                }
            }
        }

        // Monday-first, so the lead-in comes from the previous month's tail.
        val lead = first.dayOfWeek.ordinal
        val cells: List<LocalDate> =
            (lead downTo 1).map { first.minus(it, DateTimeUnit.DAY) } +
                (0 until first.daysInMonth()).map { first.plus(it, DateTimeUnit.DAY) }

        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date ->
                    val outside = date.month != first.month
                    DayCell(
                        day = date.day,
                        outside = outside,
                        phase = if (outside || !hasData) null else state.phaseForDate(date),
                        isToday = date == state.today,
                        predicted = date > state.today,
                        modifier = Modifier.weight(1f),
                        onClick = { onDayClick(date) },
                    )
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }

        if (!hasData) {
            Text(t.phaseNotColouredYet, style = Sadora.type.body, color = c.muted)
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    outside: Boolean,
    phase: CyclePhase?,
    isToday: Boolean,
    predicted: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = Sadora.colors
    val phaseColor = when (phase) {
        CyclePhase.Period -> PhaseColors.period
        CyclePhase.Fertile -> PhaseColors.fertile
        else -> null
    }

    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(Radius.sm))
            .then(
                when {
                    // Recorded days are filled; predicted days only get an outline.
                    phaseColor != null && !predicted ->
                        Modifier.background(phaseColor.copy(alpha = if (c.isDark) 0.85f else 0.9f))
                    phaseColor != null && predicted ->
                        Modifier.border(1.5.dp, phaseColor, RoundedCornerShape(Radius.sm))
                    else -> Modifier
                },
            )
            .then(if (isToday) Modifier.border(2.dp, c.primary, RoundedCornerShape(Radius.sm)) else Modifier)
            .noRippleClickable(enabled = !outside, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$day",
            style = Sadora.type.body.copy(
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            ),
            color = when {
                outside -> c.muted2.copy(alpha = 0.5f)
                phaseColor != null && !predicted -> c.onPrimary
                else -> c.text
            },
        )
    }
}

/** Legend — colour plus wording, never colour alone. */
@Composable
private fun PhaseKey() {
    val c = Sadora.colors
    val t = strings.journey
    val entries = listOf(
        Triple(t.keyPeriod, PhaseColors.period, false),
        Triple(t.keyFertile, PhaseColors.fertile, false),
        Triple(t.keyPredicted, PhaseColors.period, true),
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        entries.forEach { (label, color, outlined) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .then(
                            if (outlined) {
                                Modifier.border(1.5.dp, color, RoundedCornerShape(4.dp))
                            } else {
                                Modifier.background(color)
                            },
                        ),
                )
                Text(label, style = Sadora.type.body, color = c.muted)
            }
        }
    }
}

@Composable
private fun SelectedDaySummary(state: AppState, onOpen: () -> Unit) {
    val c = Sadora.colors
    val t = strings.journey
    SadoraCard(onClick = onOpen) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${state.cycleDay}", style = Sadora.type.h1, color = c.text)
                Text(t.dayCaps, style = Sadora.type.caption, color = c.muted)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(t.today, style = Sadora.type.h3, color = c.text)
                    SadoraBadge(strings.common.phase(state.currentPhase()), BadgeTone.Estimated)
                }
                Text(
                    if (state.symptoms.isEmpty()) {
                        t.noSymptomsAndMood(strings.common.mood(state.mood).lowercase())
                    } else {
                        t.symptomsAndMood(
                            state.symptoms.joinToString(", "),
                            strings.common.mood(state.mood).lowercase(),
                        )
                    },
                    style = Sadora.type.body,
                    color = c.muted,
                )
            }
            Icon(SadoraIcons.ChevronRight, contentDescription = null, Modifier.size(IconSize.md), tint = c.muted2)
        }
    }
}

/**
 * "Tarix" tab — the cycles the server actually recorded.
 *
 * Nothing here is projected. The previous version drew six identical bars from the
 * average and listed three "previous cycles" counted backwards from the anchor, which
 * is a picture of arithmetic rather than of anything that happened.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.cycleHistoryItems(
    state: AppState,
    health: HealthController,
) {
    item { CycleStatsRow(state) }
    item { CycleHistoryCards(health) }
    item { DisclaimerNote(strings.journey.statsNote) }
}

@Composable
private fun CycleStatsRow(state: AppState) {
    val c = Sadora.colors
    val t = strings.journey
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        listOf(
            t.averageCycle to t.daysValue(state.averageCycleLength),
            t.averagePeriod to t.daysValue(state.averagePeriodLength),
            t.regularity to if (state.cycleIsRegular) t.regularSteady else t.regularVaries,
        ).forEach { (label, value) ->
            SadoraCard(modifier = Modifier.weight(1f), padding = Spacing.sm) {
                Text(label, style = Sadora.type.body, color = c.muted)
                Text(value, style = Sadora.type.h3, color = c.text)
            }
        }
    }
}

/** The chart and the list, or one honest sentence when there is no history yet. */
@Composable
private fun CycleHistoryCards(health: HealthController) {
    val c = Sadora.colors
    val t = strings.journey
    val cycles = health.history?.cycles.orEmpty().takeLast(6)

    if (cycles.isEmpty()) {
        SadoraCard {
            CardLabel(t.previousCycles)
            Text(t.noHistoryYet, style = Sadora.type.h3, color = c.text)
            Text(t.noHistoryYetBody, style = Sadora.type.body, color = c.muted)
        }
        return
    }

    SadoraCard {
        CardLabel(
            t.cycleLength,
            trailing = {
                Text(t.lastNCycles(cycles.size), style = Sadora.type.body, color = c.muted)
            },
        )
        val longest = cycles.maxOf { it.cycleLength }.coerceAtLeast(1)
        org.example.project.ui.components.WeeklyBars(
            values = cycles.map { it.cycleLength / longest.toFloat() },
            labels = cycles.map { "${it.cycleLength}" },
            color = c.primary,
        )
    }

    SadoraCard {
        CardLabel(t.previousCycles)
        cycles.reversed().forEachIndexed { index, cycle ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${strings.dates.dayMonth(cycle.startedOn)} – " +
                            strings.dates.dayMonth(cycle.endedOn),
                        style = Sadora.type.h3,
                        color = c.text,
                    )
                    Text(
                        listOfNotNull(
                            t.daysValue(cycle.cycleLength),
                            if (index == 0) t.currentCycle else null,
                            cycle.periodLength?.let { t.periodOfDays(it) },
                        ).joinToString(" · "),
                        style = Sadora.type.body,
                        color = c.muted,
                    )
                }
                Icon(
                    SadoraIcons.ChevronRight,
                    contentDescription = null,
                    Modifier.size(IconSize.md),
                    tint = c.muted2,
                )
            }
        }
    }
}
