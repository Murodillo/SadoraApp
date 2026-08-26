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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.CyclePhase
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
 * Past days are filled; predicted days are outlined with a dashed ring so a forecast
 * never looks like a recorded fact. Colour is never the only indicator — each state
 * also carries a label in the legend.
 */
@Composable
fun CycleCalendarScreen(
    state: AppState,
    onOpen: (Route) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    var tab by remember { mutableStateOf(0) }

    Column(modifier) {
        SadoraTopBar("Sikl", onBack = onClose)

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
                item { MonthGrid(state, onDayClick = { onOpen(Route.CycleDay(it)) }) }

                item { PhaseKey() }

                item {
                    DisclaimerNote(
                        "Punktir bilan belgilangan kunlar — hisob-kitob natijasi, tibbiy " +
                            "kafolat emas.",
                    )
                }

                item { SelectedDaySummary(state, onOpen = { onOpen(Route.CycleDay("19-avgust")) }) }

                item {
                    SadoraButton("Hayzni belgilash", onClick = {}, leading = "✎")
                }
            }
        }
    }
}

/** A 7-column month grid with phase colouring. */
@Composable
private fun MonthGrid(state: AppState, onDayClick: (String) -> Unit) {
    val c = Sadora.colors
    SadoraCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("‹", style = Sadora.type.h2, color = c.muted, modifier = Modifier.noRippleClickable {})
            Text("Avgust 2026", style = Sadora.type.h3, color = c.text)
            Text("›", style = Sadora.type.h2, color = c.muted, modifier = Modifier.noRippleClickable {})
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SampleData.weekDays.forEach { day ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(day, style = Sadora.type.caption, color = c.muted2)
                }
            }
        }

        // August 2026 starts on a Saturday, so the grid opens with 28–31 July.
        val leading = listOf(28, 29, 30, 31)
        val cells: List<Pair<Int, Boolean>> =
            leading.map { it to true } + (1..31).map { it to false }

        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { (day, outside) ->
                    DayCell(
                        day = day,
                        outside = outside,
                        phase = if (outside) null else state.phaseForDay(day),
                        isToday = !outside && day == 19,
                        predicted = !outside && day >= 20,
                        modifier = Modifier.weight(1f),
                        onClick = { onDayClick("$day-avgust") },
                    )
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
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
        CyclePhase.Period -> c.primary
        CyclePhase.Fertile -> c.accent
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
            .then(if (isToday) Modifier.border(2.dp, c.text, RoundedCornerShape(Radius.sm)) else Modifier)
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
                phaseColor != null && !predicted -> if (c.isDark) c.bg else Color.White
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
        Triple("Hayz", c.primary, false),
        Triple("Unumdor", c.accent, false),
        Triple("Taxminiy hayz", c.primary, true),
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
                    SadoraBadge("Ovulyatsiya", BadgeTone.Estimated)
                }
                Text(
                    "Ajralma qayd etilgan · kayfiyat yaxshi",
                    style = Sadora.type.body,
                    color = c.muted,
                )
            }
            Text("›", style = Sadora.type.h3, color = c.muted2)
        }
    }
}

/** "Tarix" tab — statistics that state how much data they rest on. */
private fun androidx.compose.foundation.lazy.LazyListScope.cycleHistoryItems(state: AppState) {
    item { CycleStatsRow(state) }
    item { CycleLengthChart() }
    item { PreviousCyclesList() }
    item {
        DisclaimerNote(
            "Statistika 6 sikl asosida. Ko'proq ma'lumot yig'ilgani sari aniqlik oshadi.",
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
            "Muntazamlik" to "Yaxshi",
        ).forEach { (label, value) ->
            SadoraCard(modifier = Modifier.weight(1f), padding = Spacing.sm) {
                Text(label, style = Sadora.type.body, color = c.muted)
                Text(value, style = Sadora.type.h3, color = c.text)
            }
        }
    }
}

@Composable
private fun CycleLengthChart() {
    val c = Sadora.colors
    val lengths = listOf(27, 29, 26, 30, 28, 29)
    SadoraCard {
        CardLabel(
            "Sikl uzunligi",
            trailing = { Text("oxirgi 6 sikl", style = Sadora.type.body, color = c.muted) },
        )
        org.example.project.ui.components.WeeklyBars(
            values = lengths.map { (it - 24) / 8f },
            labels = lengths.map { "$it" },
            color = c.secondary,
        )
    }
}

@Composable
private fun PreviousCyclesList() {
    val c = Sadora.colors
    val cycles = listOf(
        Triple("22 iyul – 19 avgust", "29 kun", "Joriy"),
        Triple("24 iyun – 21 iyul", "28 kun", "hayz 5 kun"),
        Triple("27 may – 23 iyun", "30 kun", "hayz 6 kun"),
    )
    SadoraCard {
        CardLabel("Oldingi sikllar")
        cycles.forEach { (range, length, note) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(range, style = Sadora.type.h3, color = c.text)
                    Text("$length · $note", style = Sadora.type.body, color = c.muted)
                }
                Text("›", style = Sadora.type.h3, color = c.muted2)
            }
        }
    }
}
