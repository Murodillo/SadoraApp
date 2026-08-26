package org.example.project.ui.journey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.SampleData
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.ChipFlowRow
import org.example.project.ui.components.DisclaimerNote
import org.example.project.ui.components.ProgressRing
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.SelectChip
import org.example.project.ui.components.StackedBar
import org.example.project.ui.components.WeeklyBars

/**
 * "Menopauza · Simptomlar" — a 30-day frequency view plus quick logging.
 *
 * Replaces prediction entirely for this stage: the value is in seeing patterns, not
 * in forecasting a cycle that no longer runs.
 */
@Composable
fun StageSymptomsScreen(
    state: AppState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val logged = remember { mutableStateListOf<String>() }

    val quickLog = listOf(
        "Issiqlik to'lqini",
        "Tungi terlash",
        "Uyqusizlik",
        "Bo'g'im og'rig'i",
        "Quruqlik",
        "Yurak tez urishi",
    )
    val mostFrequent = listOf(
        Triple("🔥", "Issiqlik to'lqini", "22 kun"),
        Triple("💤", "Tungi terlash", "14 kun"),
        Triple("🦴", "Bo'g'im og'rig'i", "9 kun"),
    )

    Column(modifier) {
        SadoraTopBar("Simptomlar", onBack = onClose)

        ScreenContent {
            item {
                SadoraCard {
                    CardLabel(
                        "Issiqlik to'lqinlari",
                        trailing = {
                            Text("30 kun", style = Sadora.type.body, color = c.muted)
                        },
                    )
                    WeeklyBars(
                        values = listOf(0.55f, 0.8f, 0.65f, 0.9f),
                        labels = listOf("1-hafta", "2-hafta", "3-hafta", "4-hafta"),
                        color = c.warning,
                    )
                    Text(
                        "Kuniga o'rtacha 2,4 marta. Eng ko'p — kechqurun 20:00–23:00.",
                        style = Sadora.type.body,
                        color = c.muted,
                    )
                }
            }

            item {
                SadoraCard {
                    CardLabel("Bugun qayd etish")
                    ChipFlowRow {
                        quickLog.forEach { symptom ->
                            SelectChip(
                                label = symptom,
                                selected = symptom in logged,
                                onClick = { if (!logged.remove(symptom)) logged.add(symptom) },
                            )
                        }
                        SelectChip("+ Qo'shish", selected = false, onClick = {})
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel("Eng ko'p uchraganlar")
                    mostFrequent.forEach { (emoji, name, count) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Text(emoji, style = Sadora.type.h3)
                            Text(
                                name,
                                style = Sadora.type.body,
                                color = c.text,
                                modifier = Modifier.weight(1f),
                            )
                            Text(count, style = Sadora.type.body, color = c.muted)
                        }
                    }
                }
            }

            item {
                DisclaimerNote(
                    "Simptomlar ro'yxati kuzatuv uchun. Yangi yoki kuchayib borayotgan " +
                        "belgilar bo'lsa shifokor bilan maslahatlashing.",
                )
            }

            item { SadoraButton("Saqlash", onClose) }
        }
    }
}

/**
 * "Uyqu va kayfiyat" — the stage-level correlation view.
 *
 * States the link as co-occurrence and appends "sabab-natija emas" so it cannot be
 * read as causal.
 */
@Composable
fun StageSleepMoodScreen(
    state: AppState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val stageColors = listOf(c.secondary, c.accent, c.muted2)

    Column(modifier) {
        SadoraTopBar("Uyqu va kayfiyat", onBack = onClose)

        ScreenContent {
            item {
                SadoraCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        ProgressRing(
                            progress = 0.72f,
                            size = 112.dp,
                            strokeWidth = 11.dp,
                            color = c.accent,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("72", style = Sadora.type.data, color = c.text)
                                Text("BALL", style = Sadora.type.caption, color = c.muted)
                            }
                        }
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("7s 10d", style = Sadora.type.h1, color = c.text)
                            Text("23:20 → 06:30", style = Sadora.type.body, color = c.muted)
                            SadoraBadge("Galaxy Watch · 06:35", BadgeTone.Connected)
                        }
                    }
                    StackedBar(
                        segments = listOf(
                            0.16f to stageColors[0],
                            0.75f to stageColors[1],
                            0.09f to stageColors[2],
                        ),
                        height = 12.dp,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        listOf("Chuqur 1s 08d", "Yengil 3s 18d", "Uyg'oq 38d").forEach { label ->
                            Text(label, style = Sadora.type.body, color = c.muted)
                        }
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel(
                        "7 kunlik kayfiyat",
                        trailing = {
                            Text("O'rtacha 3,8", style = Sadora.type.body, color = c.muted)
                        },
                    )
                    WeeklyBars(
                        values = listOf(0.7f, 0.6f, 0.85f, 0.75f, 0.9f, 0.65f, 0.8f),
                        labels = SampleData.weekDays,
                        color = c.secondary,
                        highlightIndex = 6,
                    )
                }
            }

            item {
                SadoraCard {
                    CardLabel("Kuzatish", color = c.textAccent)
                    Text(
                        "Tungi terlash qayd etilgan kunlarda uyqu o'rtacha 40 daqiqa qisqa " +
                            "bo'lgan. Bu holatlar ko'pincha birga kuzatilgan — sabab-natija emas.",
                        style = Sadora.type.body,
                        color = c.muted,
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SadoraCard(modifier = Modifier.weight(1f), padding = Spacing.sm, onClick = {}) {
                        Text("🌬️", style = Sadora.type.h1)
                        Text("Nafas mashqi", style = Sadora.type.h3, color = c.text)
                        Text("Uyqu oldidan · 4 daqiqa", style = Sadora.type.body, color = c.muted)
                    }
                    SadoraCard(modifier = Modifier.weight(1f), padding = Spacing.sm, onClick = {}) {
                        Text("📝", style = Sadora.type.h1)
                        Text("Kundalik", style = Sadora.type.h3, color = c.text)
                        Text("Faqat siz ko'rasiz", style = Sadora.type.body, color = c.muted)
                    }
                }
            }
        }
    }
}
