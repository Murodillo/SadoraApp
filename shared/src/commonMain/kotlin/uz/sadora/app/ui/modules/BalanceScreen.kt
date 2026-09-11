package uz.sadora.app.ui.modules

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
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.ModuleStrings
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.model.Fmt
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.DisclaimerNote
import uz.sadora.app.ui.components.LabeledProgress
import uz.sadora.app.ui.components.ProgressRing
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.model.DailyStepGoal
import uz.sadora.app.model.DailySleepGoalMinutes

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
    val t = strings.modules
    val c = Sadora.colors

    // The same four signals Today scores, against the same goals. A second screen with
    // its own idea of how the day went would only disagree with the first one.
    fun ratio(value: Int, goal: Int): Float =
        if (goal <= 0) 0f else (value / goal.toFloat()).coerceIn(0f, 1f)

    val directions = listOf(
        Quad("🍽", t.food, ratio(state.caloriesEaten, state.calorieGoal), c.primary,
            t.ofKcal(Fmt.int(state.caloriesEaten), Fmt.int(state.calorieGoal))),
        Quad("💧", t.water, ratio(state.waterMl, state.waterGoalMl), c.accent,
            t.ofLitres(Fmt.litres(state.waterMl), Fmt.litres(state.waterGoalMl))),
        Quad("👟", t.activity, ratio(state.steps, DailyStepGoal), c.secondary,
            t.ofSteps(Fmt.int(state.steps), Fmt.int(DailyStepGoal))),
        Quad("💤", t.sleep, ratio(state.sleepMinutes, DailySleepGoalMinutes), c.success,
            t.ofSleep(state.sleepLabel(format = strings.common::hoursMinutes))),
    )
    val score = (directions.map { it.value }.average() * 100).toInt()

    Column(modifier) {
        SadoraTopBar(t.balanceTitle, onBack = onClose)

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
                                Text(t.balanceCapsWord, style = Sadora.type.caption, color = c.muted)
                            }
                        }
                        Text(
                            balanceNote(directions, t),
                            style = Sadora.type.body,
                            color = c.muted,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel(t.fourDirections)
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
                    t.balanceDisclaimer,
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
private fun balanceNote(directions: List<Quad>, t: ModuleStrings): String {
    val weakest = directions.minByOrNull { it.value } ?: return ""
    return when {
        weakest.value >= 0.8f -> t.balanced
        weakest.value >= 0.5f -> t.someRoomIn(weakest.label)
        else -> t.fallingBehind(weakest.label)
    }
}

