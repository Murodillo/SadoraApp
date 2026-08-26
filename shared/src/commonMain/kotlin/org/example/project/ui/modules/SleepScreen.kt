package org.example.project.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.SampleData
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.ProgressRing
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.SettingsRow
import org.example.project.ui.components.StackedBar
import org.example.project.ui.components.WeeklyBars

/**
 * "Uyqu" — score, stage breakdown, weekly trend.
 *
 * Every synced figure names its source and sync time, and manual entry stays
 * available for users with no wearable.
 */
@Composable
fun SleepScreen(
    state: AppState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val stageColors = listOf(c.secondary, c.accent, c.primary, c.muted2)

    Column(modifier) {
        SadoraTopBar("Uyqu", onBack = onClose)

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
                            size = 116.dp,
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
                            Text(state.sleepLabel(), style = Sadora.type.h1, color = c.text)
                            Text("23:40 → 06:20", style = Sadora.type.body, color = c.muted)
                            SadoraBadge("Oura Ring · 07:05", BadgeTone.Connected)
                        }
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel("Bosqichlar")
                    StackedBar(
                        segments = SampleData.sleepStages.mapIndexed { index, stage ->
                            stage.fraction to stageColors[index % stageColors.size]
                        },
                        height = 12.dp,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        SampleData.sleepStages.forEachIndexed { index, stage ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    LegendDot(stageColors[index % stageColors.size])
                                    Text(stage.label, style = Sadora.type.body, color = c.muted)
                                }
                                Text(stage.duration, style = Sadora.type.body, color = c.text)
                            }
                        }
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel(
                        "7 kunlik davomiylik",
                        trailing = {
                            Text("O'rtacha 6s 52d", style = Sadora.type.body, color = c.muted)
                        },
                    )
                    WeeklyBars(
                        values = listOf(0.8f, 0.65f, 0.9f, 0.7f, 0.55f, 0.95f, 0.72f),
                        labels = SampleData.weekDays,
                        color = c.accent,
                        highlightIndex = 6,
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SadoraCard(modifier = Modifier.weight(1f), padding = Spacing.sm) {
                        CardLabel("Sifat")
                        Text("Yaxshi", style = Sadora.type.h2, color = c.text)
                    }
                    SadoraCard(modifier = Modifier.weight(1f), padding = Spacing.sm) {
                        CardLabel("Tinch pulsi")
                        Text("58 bpm", style = Sadora.type.h2, color = c.text)
                    }
                }
            }

            item {
                SadoraCard(padding = Spacing.xs) {
                    SettingsRow("✎", "Uyquni qo'lda kiritish") {}
                }
            }
        }
    }
}

/** Small colour key next to a stage label. */
@Composable
private fun LegendDot(color: Color) {
    Box(
        Modifier
            .size(8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(color),
    )
}
