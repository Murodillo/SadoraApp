package org.example.project.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTextField
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent

private data class JournalEntry(val whenLabel: String, val time: String, val mood: String, val text: String)

/**
 * "Ong · kundalik va nafas".
 *
 * The privacy label is shown on the surface itself, not buried in settings — the
 * journal is the most sensitive thing the app stores.
 */
@Composable
fun MindJournalScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    var draft by remember { mutableStateOf("") }
    val entries = remember {
        mutableStateListOf(
            JournalEntry(
                "Bugun",
                "21:40",
                "🙂",
                "Ish kuni zich bo'ldi, lekin kechqurun yurish yaxshi ta'sir qildi.",
            ),
            JournalEntry("Kecha", "22:05", "😐", "Kechqurun bosh og'rig'i bezovta qildi."),
        )
    }

    Column(modifier) {
        SadoraTopBar("Kundalik va praktika", onBack = onClose)

        ScreenContent {
            item {
                SadoraCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(Radius.md))
                                .background(c.surface2),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("🌬️", style = Sadora.type.h2)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("4-7-8 · Nafas", style = Sadora.type.h3, color = c.text)
                            Text(
                                "4 soniya oling · 7 soniya ushlang · 8 soniya chiqaring",
                                style = Sadora.type.body,
                                color = c.muted,
                            )
                        }
                    }
                    SadoraButton("3 daqiqa boshlash", onClick = {})
                }
            }

            item {
                CardLabel(
                    "Kundalik",
                    trailing = { SadoraBadge("FAQAT SIZ KO'RASIZ", BadgeTone.Neutral, leading = "🔒") },
                )
            }

            items(entries.size) { index ->
                val entry = entries[index]
                SadoraCard(padding = Spacing.sm) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(entry.mood, style = Sadora.type.h3)
                        Text(
                            entry.whenLabel,
                            style = Sadora.type.h3,
                            color = c.text,
                            modifier = Modifier.weight(1f),
                        )
                        Text(entry.time, style = Sadora.type.body, color = c.muted2)
                    }
                    Text(entry.text, style = Sadora.type.body, color = c.muted)
                }
            }

            item {
                SadoraTextField(
                    draft,
                    { draft = it },
                    placeholder = "Yangi yozuv qo'shish…",
                    singleLine = false,
                )
            }

            item {
                SadoraButton(
                    "Saqlash",
                    onClick = {
                        if (draft.isNotBlank()) {
                            entries.add(0, JournalEntry("Bugun", "hozir", "🙂", draft))
                            draft = ""
                        }
                    },
                    enabled = draft.isNotBlank(),
                )
            }
        }
    }
}
