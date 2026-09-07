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
import org.example.project.model.Fmt
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

    // The same four signals Today scores, against the same goals. A second screen with
    // its own idea of how the day went would only disagree with the first one.
    fun ratio(value: Int, goal: Int): Float =
        if (goal <= 0) 0f else (value / goal.toFloat()).coerceIn(0f, 1f)

    val directions = listOf(
        Quad("🍽", "Ovqatlanish", ratio(state.caloriesEaten, state.calorieGoal), c.primary,
            "${Fmt.int(state.caloriesEaten)} / ${Fmt.int(state.calorieGoal)} kkal"),
        Quad("💧", "Suv", ratio(state.waterMl, state.waterGoalMl), c.accent,
            "${Fmt.litres(state.waterMl)} / ${Fmt.litres(state.waterGoalMl)} l"),
        Quad("👟", "Faollik", ratio(state.steps, StepGoal), c.secondary,
            "${Fmt.int(state.steps)} / ${Fmt.int(StepGoal)} qadam"),
        Quad("💤", "Uyqu", ratio(state.sleepMinutes, SleepGoalMinutes), c.success,
            "${state.sleepLabel()} / 8s"),
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
                            balanceNote(directions),
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
                            // The reading, not a percentage: "1,2 / 2,0 l" says what to do
                            // next, and "60%" does not.
                            label = "${direction.emoji}  ${direction.label}",
                            value = direction.reading,
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
    /** What was measured against what, in the screen's own words. */
    val reading: String,
)

/**
 * What the day looks like, said about the weakest direction rather than in general.
 *
 * The design rejects debt language outright, so a missed goal is described as something
 * still available today, never as something owed.
 */
private fun balanceNote(directions: List<Quad>): String {
    val weakest = directions.minByOrNull { it.value } ?: return ""
    return when {
        weakest.value >= 0.8f ->
            "Bugun to'rt yo'nalish ham muvozanatda. Ovqat \"yoqib yuborilishi\" kerak " +
                "bo'lgan qarz emas."
        weakest.value >= 0.5f ->
            "Kun yaxshi ketyapti. \"${weakest.label}\" bo'yicha biroz joy bor — " +
                "xohlasangiz shunga e'tibor bering."
        else ->
            "Bugun \"${weakest.label}\" ortda qolyapti. Kun hali tugagani yo'q, " +
                "shoshilmang."
    }
}

private const val SleepGoalMinutes = 480
private const val StepGoal = 8000
