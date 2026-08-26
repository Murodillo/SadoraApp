package org.example.project.ui.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.DisclaimerNote
import org.example.project.ui.components.LabeledProgress
import org.example.project.ui.components.ProgressRing
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent

/**
 * "Balans" — four directions in one score.
 *
 * The framing matters as much as the numbers here: the design explicitly rejects
 * debt/burn-off language, so the copy says food is not a debt to be worked off and
 * the score is measured against the user's own goals, not a population norm.
 */
@Composable
fun BalanceScreen(
    state: AppState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors

    val directions = listOf(
        Quad("🍽", "Ovqatlanish", 0.67f, c.primary),
        Quad("💧", "Suv", 0.60f, c.accent),
        Quad("👟", "Faollik", 0.82f, c.secondary),
        Quad("💤", "Uyqu", 0.74f, c.success),
    )
    val score = (directions.map { it.value }.average() * 100).toInt()

    Column(modifier) {
        SadoraTopBar("Balans", onBack = onClose)

        ScreenContent {
            item {
                SadoraCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        ProgressRing(
                            progress = score / 100f,
                            size = 124.dp,
                            strokeWidth = 12.dp,
                            segments = directions.map { it.value / 4f to it.color },
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$score", style = Sadora.type.data, color = c.text)
                                Text("BALANS", style = Sadora.type.caption, color = c.muted)
                            }
                        }
                        Text(
                            "Bugun to'rt yo'nalish ham muvozanatda. Ovqat \"yoqib " +
                                "yuborilishi\" kerak bo'lgan qarz emas.",
                            style = Sadora.type.body,
                            color = c.muted,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel("To'rt yo'nalish")
                    directions.forEach { direction ->
                        LabeledProgress(
                            label = "${direction.emoji}  ${direction.label}",
                            value = "${(direction.value * 100).toInt()}%",
                            progress = direction.value,
                            color = direction.color,
                        )
                    }
                }
            }

            item {
                DisclaimerNote(
                    "Balans balli o'zingiz belgilagan maqsadlarga nisbatan hisoblanadi. " +
                        "Bu ball tibbiy ko'rsatkich emas.",
                )
            }
        }
    }
}

private data class Quad(
    val emoji: String,
    val label: String,
    val value: Float,
    val color: Color,
)
