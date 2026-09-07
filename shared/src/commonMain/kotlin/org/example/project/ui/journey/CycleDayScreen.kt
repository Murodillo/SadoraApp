package org.example.project.ui.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import org.example.project.data.HealthController
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.i18n.strings
import org.example.project.model.AppState
import org.example.project.model.Fmt
import org.example.project.data.toAppMood
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import uz.sadora.contract.DailyLog

/**
 * "Sikl · kun tafsiloti" — everything recorded for one day.
 *
 * [date] is the ISO date the calendar tapped. The day is read from the server rather
 * than from the store, because the store only ever holds today: before this the screen
 * told anyone who opened last Tuesday that nothing had been logged, however much she
 * had written that day. It is read without applying it, so opening a past day does not
 * replace today's entries everywhere else in the app.
 *
 * Device-sourced figures are grouped separately and carry their source badge, so it
 * is always clear what the user entered and what a wearable supplied.
 */
@Composable
fun CycleDayScreen(
    state: AppState,
    health: HealthController,
    date: String,
    onOpenSymptomSheet: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.journey
    val common = strings.common
    val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: state.today
    val isToday = day == state.today
    val cycleDay = if (isToday) state.cycleDay else state.cycleDayFor(day)
    val phase = if (isToday) state.currentPhase() else state.phaseForDate(day)

    var log by remember(day) { mutableStateOf<DailyLog?>(null) }
    LaunchedEffect(day) {
        health.loadSymptoms(null)
        log = health.dayAt(day)
    }
    val entry = log

    Column(modifier) {
        SadoraTopBar("", onBack = onClose)

        ScreenContent {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(strings.dates.dayMonthWeekday(day), style = Sadora.type.h1, color = c.text)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        if (cycleDay != null) {
                            Text(t.cycleDayOrdinal(cycleDay), style = Sadora.type.body, color = c.muted)
                        }
                        if (phase != null) SadoraBadge(common.phase(phase), BadgeTone.Estimated)
                    }
                }
            }

            if (cycleDay != null && phase != null) {
                item {
                    SadoraCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(t.cycleDayCaps, style = Sadora.type.caption, color = c.muted)
                                Text("$cycleDay", style = Sadora.type.data, color = c.text)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(common.phase(phase), style = Sadora.type.h2, color = c.text)
                                Text(common.phaseEnergy(phase), style = Sadora.type.body, color = c.muted)
                            }
                        }
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel(if (isToday) t.loggedToday else t.logged)
                    if (entry == null || entry.isEmpty) {
                        Text(
                            if (isToday) t.noSymptomsLogged else t.nothingLoggedForDay,
                            style = Sadora.type.body,
                            color = c.muted,
                        )
                    } else {
                        entry.symptoms.forEach { symptom ->
                            LoggedLine("•", health.symptoms.labelFor(symptom.key))
                        }
                        entry.mood?.let { level ->
                            val mood = level.toAppMood()
                            LoggedLine(mood.emoji, t.moodLine(common.mood(mood).lowercase()))
                        }
                        entry.energy?.let { LoggedLine("⚡", t.energyLine(it)) }
                        entry.note?.takeIf { it.isNotBlank() }?.let { LoggedLine("📝", it) }
                    }
                }
            }

            if (isToday) {
                item {
                    // Wearable data is kept visually distinct from self-reported entries.
                    SadoraCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Text("⌚", style = Sadora.type.h3)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    t.sleepAndSteps(
                                        state.sleepLabel(format = common::hoursMinutes),
                                        Fmt.int(state.steps),
                                    ),
                                    style = Sadora.type.h3,
                                    color = c.text,
                                )
                            }
                        }
                        SadoraBadge(t.fromDevice, BadgeTone.Connected)
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SadoraButton(
                        t.editEntry,
                        onClick = onOpenSymptomSheet,
                        tone = ButtonTone.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                    SadoraButton(
                        t.addSymptom,
                        onClick = onOpenSymptomSheet,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LoggedLine(emoji: String, text: String) {
    val c = Sadora.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            Modifier.clip(Radius.chip).background(c.surface2).padding(6.dp),
        ) {
            Text(emoji, style = Sadora.type.body)
        }
        Text(text, style = Sadora.type.body, color = c.text)
    }
}
