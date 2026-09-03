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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.AppState
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

    Column(modifier) {
        SadoraTopBar("", onBack = onClose)

        ScreenContent {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("19-avgust, chorshanba", style = Sadora.type.h1, color = c.text)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            "Sikl ${state.cycleDay}-kuni",
                            style = Sadora.type.body,
                            color = c.muted,
                        )
                        SadoraBadge("Ovulyatsiya", BadgeTone.Estimated)
                    }
                }
            }

            item {
                SadoraCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("SIKL KUNI", style = Sadora.type.caption, color = c.muted)
                            Text("${state.cycleDay}", style = Sadora.type.data, color = c.text)
                        }
                        Text("Ovulyatsiya", style = Sadora.type.h2, color = c.text)
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel("Bugun qayd etilgan")
                    LoggedLine("💧", "Ajralma — tuxum oqi kabi")
                    LoggedLine("🙂", "Kayfiyat — yaxshi")
                    LoggedLine("⚡", "Energiya — 3 / 5")
                }
            }

            item {
                SadoraCard {
                    CardLabel("Izoh")
                    Text(
                        "Kechqurun boshim og'ridi, erta yotdim. Ertaga suvni ko'paytiraman.",
                        style = Sadora.type.body,
                        color = c.text,
                    )
                }
            }

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
                                "Uyqu ${state.sleepLabel()} · Puls 58",
                                style = Sadora.type.h3,
                                color = c.text,
                            )
                        }
                    }
                    SadoraBadge("Oura Ring · 07:05", BadgeTone.Connected)
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
