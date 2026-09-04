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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.Fmt
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent

/**
 * "Sikl · kun tafsiloti" — everything recorded for one day.
 *
 * [date] is the ISO date the calendar tapped. Only today's entries live in the store,
 * so another day shows its phase and an invitation to log rather than borrowed data.
 * Device-sourced figures are grouped separately and carry their source badge, so it
 * is always clear what the user entered and what a wearable supplied.
 */
@Composable
fun CycleDayScreen(
    state: AppState,
    date: String,
    onOpenSymptomSheet: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: state.today
    val isToday = day == state.today
    val cycleDay = if (isToday) state.cycleDay else state.cycleDayFor(day)
    val phase = if (isToday) state.currentPhase() else state.phaseForDate(day)

    Column(modifier) {
        SadoraTopBar("", onBack = onClose)

        ScreenContent {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(Fmt.dayMonthWeekday(day), style = Sadora.type.h1, color = c.text)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        if (cycleDay != null) {
                            Text("Sikl $cycleDay-kuni", style = Sadora.type.body, color = c.muted)
                        }
                        if (phase != null) SadoraBadge(phase.label, BadgeTone.Estimated)
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
                                Text("SIKL KUNI", style = Sadora.type.caption, color = c.muted)
                                Text("$cycleDay", style = Sadora.type.data, color = c.text)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(phase.label, style = Sadora.type.h2, color = c.text)
                                Text(phase.energyNote, style = Sadora.type.body, color = c.muted)
                            }
                        }
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel(if (isToday) "Bugun qayd etilgan" else "Qayd etilgan")
                    if (isToday) {
                        if (state.symptoms.isEmpty()) {
                            Text("Simptom qayd etilmagan", style = Sadora.type.body, color = c.muted)
                        } else {
                            state.symptoms.forEach { LoggedLine("•", it) }
                        }
                        LoggedLine(state.mood.emoji, "Kayfiyat — ${state.mood.label.lowercase()}")
                        LoggedLine("⚡", "Energiya — ${state.energy} / 5")
                    } else {
                        Text("Bu kun uchun yozuv yo'q.", style = Sadora.type.body, color = c.muted)
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
                                    "Uyqu ${state.sleepLabel()} · ${Fmt.int(state.steps)} qadam",
                                    style = Sadora.type.h3,
                                    color = c.text,
                                )
                            }
                        }
                        SadoraBadge("Qurilmadan", BadgeTone.Connected)
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SadoraButton(
                        "Tahrirlash",
                        onClick = onOpenSymptomSheet,
                        tone = ButtonTone.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                    SadoraButton(
                        "Simptom qo'shish",
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
