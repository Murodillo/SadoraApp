package org.example.project.ui.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.design.IconSize
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.Fmt
import org.example.project.model.Meal
import org.example.project.nav.Route
import org.example.project.ui.components.AiSummaryCard
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.CircleIconButton
import org.example.project.ui.components.IconTile
import org.example.project.ui.components.MealThumb
import org.example.project.ui.components.MiniRing
import org.example.project.ui.components.PillButton
import org.example.project.ui.components.RoundIconButton
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.SectionHeader

/**
 * "Ovqatlanish" — the deck's food diary: today's four rings, the meals, hydration,
 * and the scanner.
 *
 * The scanner is Premium and never a blocker: manual entry stays available and the
 * scan card is an invitation rather than a wall.
 */
@Composable
fun NutritionScreen(
    state: AppState,
    onOpen: (Route) -> Unit,
    onAddWater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val scanRoute = if (state.isPremium) Route.FoodScanCamera else Route.Paywall

    Column(modifier) {
        SadoraTopBar(
            "Ovqatlanish",
            centered = true,
            trailing = {
                CircleIconButton(SadoraIcons.Calendar, contentDescription = "Tahlillar") { onOpen(Route.Insights) }
            },
        )

        ScreenContent {
            item { TodayRingCard(state) }

            item {
                SectionHeader(
                    "Ovqatlar",
                    trailing = {
                        RoundIconButton(SadoraIcons.Plus, onClick = { onOpen(Route.FoodSearch) }, filled = false, size = 36.dp, contentDescription = "Ovqat qo'shish")
                    },
                )
            }

            if (state.meals.isEmpty()) {
                item {
                    SadoraCard(onClick = { onOpen(Route.FoodSearch) }) {
                        Text("Bugun hali ovqat qayd etilmagan", style = Sadora.type.h3, color = c.text)
                        Text("Birinchi taomni qo'shing — kaloriya va makrolar shu yerda yig'iladi.", style = Sadora.type.body, color = c.muted)
                    }
                }
            } else {
                items(state.meals.size) { index -> MealRow(state.meals[index]) }
            }

            item {
                SadoraCard(padding = Spacing.sm) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        IconTile(SadoraIcons.Drop, tint = c.accent, size = 40.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Suv", style = Sadora.type.body, color = c.muted)
                            Text(
                                "${Fmt.litres(state.waterMl)} l / ${Fmt.litres(state.waterGoalMl)} l",
                                style = Sadora.type.h3,
                                color = c.text,
                            )
                        }
                        PillButton("+250 ml", onAddWater)
                    }
                }
            }

            item {
                AiSummaryCard(
                    label = "AI tahlili",
                    body = macroNote(state),
                    footnote = "Bugungi ko'rsatkichlaringiz asosida hisoblandi",
                    onClick = { onOpen(Route.Insights) },
                )
            }

            item {
                SadoraCard(onClick = { onOpen(scanRoute) }) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        IconTile(SadoraIcons.Camera, tint = c.primary, shape = Radius.cardSmall)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Ovqat skaneri", style = Sadora.type.h3, color = c.text)
                            Text(
                                "Kamerani yo'naltiring — taom, porsiya va makrolar taxminan aniqlanadi",
                                style = Sadora.type.body,
                                color = c.muted,
                            )
                        }
                        if (!state.isPremium) SadoraBadge("PREMIUM", BadgeTone.Premium)
                    }
                }
            }

            item {
                SadoraCard(padding = Spacing.sm, onClick = { onOpen(Route.Balance) }) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        IconTile(SadoraIcons.Target, tint = c.success, shape = Radius.cardSmall)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Balans", style = Sadora.type.h3, color = c.text)
                            Text(
                                "Ovqat, suv, faollik va uyqu — to'rt yo'nalish",
                                style = Sadora.type.body,
                                color = c.muted,
                            )
                        }
                        Icon(SadoraIcons.ChevronRight, contentDescription = null, Modifier.size(IconSize.md), tint = c.muted2)
                    }
                }
            }
        }
    }
}

/**
 * "Bugun" — the deck's row of four rings: calories, then the three macros, each
 * against its own goal.
 *
 * Four rings rather than one ring and a legend because a macro that is short reads at
 * a glance as a gap in its own circle; in a legend it is a number to be compared.
 */
@Composable
private fun TodayRingCard(state: AppState) {
    val c = Sadora.colors
    SadoraCard {
        Text("Bugun", style = Sadora.type.body, color = c.muted)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            MacroRing("kkal", state.caloriesEaten, state.calorieGoal, c.primary, Modifier.weight(1f), delayMillis = 0)
            MacroRing("Oqsil", state.proteinG, state.proteinGoalG, c.protein, Modifier.weight(1f), unit = "g", delayMillis = 90)
            MacroRing("Yog'", state.fatG, state.fatGoalG, c.fat, Modifier.weight(1f), unit = "g", delayMillis = 180)
            MacroRing("Uglevod", state.carbsG, state.carbsGoalG, c.carbs, Modifier.weight(1f), unit = "g", delayMillis = 270)
        }
    }
}

/** One of the four rings: the amount inside, the goal under it, the name below. */
@Composable
private fun MacroRing(
    label: String,
    value: Int,
    goal: Int,
    color: Color,
    modifier: Modifier = Modifier,
    unit: String = "",
    delayMillis: Int = 0,
) {
    MiniRing(
        progress = if (goal <= 0) 0f else (value / goal.toFloat()).coerceIn(0f, 1f),
        value = Fmt.int(value),
        unit = "/${Fmt.int(goal)}$unit",
        label = label,
        modifier = modifier,
        color = color,
        size = 64.dp,
        delayMillis = delayMillis,
    )
}

/**
 * The rule-based read on today's plate: which macro is furthest from its goal.
 *
 * No model behind it — it names the largest gap the rings already show, which is what
 * makes it safe to put under a heading the user will read as advice.
 */
private fun macroNote(state: AppState): String {
    fun gap(value: Int, goal: Int): Float =
        if (goal <= 0) 0f else 1f - (value / goal.toFloat()).coerceIn(0f, 1f)

    val (name, largest) = listOf(
        "oqsil" to gap(state.proteinG, state.proteinGoalG),
        "yog'" to gap(state.fatG, state.fatGoalG),
        "uglevod" to gap(state.carbsG, state.carbsGoalG),
    ).maxBy { it.second }
    val kcalLeft = (state.calorieGoal - state.caloriesEaten).coerceAtLeast(0)

    return if (largest < 0.1f) {
        "Makrolar bugun muvozanatda. Qolgan $kcalLeft kkal uchun yengil taom yetarli."
    } else {
        "Bugun eng ko'p yetishmayotgani — $name. Keyingi taomda shunga e'tibor bering."
    }
}

/** One meal: photo tile, slot, "08:30 • 450 kkal", and the macros as coloured letters. */
@Composable
private fun MealRow(meal: Meal) {
    val c = Sadora.colors
    SadoraCard(padding = Spacing.sm) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            MealThumb(meal.emoji, size = 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(meal.slot, style = Sadora.type.h3, color = c.text)
                Text(
                    listOf(meal.time, "${meal.calories} kkal").filter { it.isNotBlank() }.joinToString(" • "),
                    style = Sadora.type.body,
                    color = c.muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    MacroLetter("O", meal.protein, c.protein)
                    MacroLetter("Y", meal.fat, c.fat)
                    MacroLetter("U", meal.carbs, c.carbs)
                }
            }
        }
    }
}

@Composable
private fun MacroLetter(letter: String, grams: Int, color: Color) {
    val c = Sadora.colors
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(letter, style = Sadora.type.body.copy(fontWeight = FontWeight.Bold), color = color)
        Text("$grams g", style = Sadora.type.body, color = c.muted)
    }
}
