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
import org.example.project.design.IconSize
import org.example.project.design.PhaseColors
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import org.example.project.i18n.strings
import org.example.project.model.AppState
import org.example.project.model.CyclePhase
import org.example.project.model.Fmt
import org.example.project.model.SampleData
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
    onOpen: (Route) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(0) }

    Column(modifier) {
        SadoraTopBar("Kalendar", onBack = onClose, centered = true)

        ScreenContent {
            item {
                SegmentedControl(
                    options = listOf("Kalendar", "Tarix"),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                )
            }

            if (tab == 1) {
                cycleHistoryItems(state)
            } else {
                item { MonthGrid(state, onDayClick = { onOpen(Route.CycleDay(it.toString())) }) }

                item { PhaseKey() }

                item {
                    DisclaimerNote(
                        "Konturli kunlar — hisob-kitob natijasi, tibbiy kafolat emas.",
                    )
                }

                item { SelectedDaySummary(state, onOpen = { onOpen(Route.CycleDay(state.today.toString())) }) }

                item {
                    SadoraButton(
                        "Hayzni belgilash",
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
                contentDescription = "Oldingi oy",
                Modifier.size(IconSize.lg).noRippleClickable { offset-- },
                tint = c.muted,
            )
            Text(Fmt.monthYear(first.year, first.month.ordinal + 1), style = Sadora.type.h3, color = c.text)
            Icon(
                SadoraIcons.ChevronRight,
                contentDescription = "Keyingi oy",
                Modifier.size(IconSize.lg).noRippleClickable { offset++ },
                tint = c.muted,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SampleData.weekDays.forEach { day ->
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
            Text(
                "Hayz sanalari kiritilgach, fazalar shu yerda bo'yaladi.",
                style = Sadora.type.body,
                color = c.muted,
            )
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
    val entries = listOf(
        Triple("Hayz", PhaseColors.period, false),
        Triple("Unumdor", PhaseColors.fertile, false),
        Triple("Taxminiy", PhaseColors.period, true),
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
    SadoraCard(onClick = onOpen) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${state.cycleDay}", style = Sadora.type.h1, color = c.text)
                Text("KUN", style = Sadora.type.caption, color = c.muted)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text("Bugun", style = Sadora.type.h3, color = c.text)
                    SadoraBadge(strings.common.phase(state.currentPhase()), BadgeTone.Estimated)
                }
                Text(
                    if (state.symptoms.isEmpty()) {
                        "Simptom qayd etilmagan · kayfiyat ${strings.common.mood(state.mood).lowercase()}"
                    } else {
                        "${state.symptoms.joinToString(", ")} · kayfiyat ${strings.common.mood(state.mood).lowercase()}"
                    },
                    style = Sadora.type.body,
                    color = c.muted,
                )
            }
            Icon(SadoraIcons.ChevronRight, contentDescription = null, Modifier.size(IconSize.md), tint = c.muted2)
        }
    }
}

/** "Tarix" tab — statistics that state how much data they rest on. */
private fun androidx.compose.foundation.lazy.LazyListScope.cycleHistoryItems(state: AppState) {
    item { CycleStatsRow(state) }
    item { CycleLengthChart(state) }
    item { PreviousCyclesList(state) }
    item {
        DisclaimerNote(
            "Statistika kiritilgan sikllar asosida. Ko'proq ma'lumot yig'ilgani sari aniqlik oshadi.",
        )
    }
}

@Composable
private fun CycleStatsRow(state: AppState) {
    val c = Sadora.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        listOf(
            "O'rtacha sikl" to "${state.averageCycleLength} kun",
            "O'rtacha hayz" to "${state.averagePeriodLength} kun",
            "Muntazamlik" to if (state.cycleIsRegular) "Yaxshi" else "O'zgaruvchan",
        ).forEach { (label, value) ->
            SadoraCard(modifier = Modifier.weight(1f), padding = Spacing.sm) {
                Text(label, style = Sadora.type.body, color = c.muted)
                Text(value, style = Sadora.type.h3, color = c.text)
            }
        }
    }
}

/** The last six cycles, projected back from the anchor when no history is loaded. */
private fun recentCycleLengths(state: AppState): List<Int> {
    val observed = state.observedCycleLengths()
    if (observed.isNotEmpty()) return observed.takeLast(6)
    return List(6) { state.averageCycleLength }
}

@Composable
private fun CycleLengthChart(state: AppState) {
    val c = Sadora.colors
    val lengths = recentCycleLengths(state)
    SadoraCard {
        CardLabel(
            "Sikl uzunligi",
            trailing = { Text("oxirgi ${lengths.size} sikl", style = Sadora.type.body, color = c.muted) },
        )
        org.example.project.ui.components.WeeklyBars(
            values = lengths.map { ((it - 20) / 20f).coerceIn(0.1f, 1f) },
            labels = lengths.map { "$it" },
            color = c.primary,
        )
    }
}

@Composable
private fun PreviousCyclesList(state: AppState) {
    val c = Sadora.colors
    val anchor = state.cycleStartDate ?: state.today
    val length = state.averageCycleLength.coerceAtLeast(1)
    val cycles = (0 until 3).map { back ->
        val start = anchor.minus(length * back, DateTimeUnit.DAY)
        val end = start.plus(length - 1, DateTimeUnit.DAY)
        Triple(
            "${Fmt.dayMonth(start)} – ${Fmt.dayMonth(end)}",
            "$length kun",
            if (back == 0) "Joriy" else "hayz ${state.averagePeriodLength} kun",
        )
    }
    SadoraCard {
        CardLabel("Oldingi sikllar")
        cycles.forEach { (range, len, note) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(range, style = Sadora.type.h3, color = c.text)
                    Text("$len · $note", style = Sadora.type.body, color = c.muted)
                }
                Icon(SadoraIcons.ChevronRight, contentDescription = null, Modifier.size(IconSize.md), tint = c.muted2)
            }
        }
    }
}
