package uz.sadora.app.ui.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.SadoraBottomSheet
import uz.sadora.app.ui.components.SadoraButton
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
import uz.sadora.app.design.IconSize
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.NutritionStrings
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.model.Fmt
import uz.sadora.app.model.Meal
import uz.sadora.app.nav.Route
import uz.sadora.app.ui.components.AiSummaryCard
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.CircleIconButton
import uz.sadora.app.ui.components.IconTile
import uz.sadora.app.ui.components.MealThumb
import uz.sadora.app.ui.components.MiniRing
import uz.sadora.app.ui.components.PillButton
import uz.sadora.app.ui.components.RoundIconButton
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SectionHeader

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
    /** What the "+250 ml" pill does; the sheet of other sizes stays behind [onAddWater]. */
    onQuickWater: (Int) -> Unit = { onAddWater() },
    /** Drawn between the top bar and the content: the Mind / Food switch on a free account. */
    underBar: (@Composable () -> Unit)? = null,
) {
    val t = strings.nutrition
    val c = Sadora.colors
    val scanRoute = if (state.isPremium) Route.FoodScanCamera else Route.Paywall
    var pendingDelete by remember { mutableStateOf<Meal?>(null) }

    Box(modifier) {
    Column {
        SadoraTopBar(
            t.title,
            centered = true,
            trailing = {
                CircleIconButton(SadoraIcons.Calendar, contentDescription = t.insights) { onOpen(Route.Insights) }
            },
        )
        underBar?.invoke()

        ScreenContent {
            item { TodayRingCard(state) }

            item {
                SectionHeader(
                    t.meals,
                    trailing = {
                        RoundIconButton(SadoraIcons.Plus, onClick = { onOpen(Route.FoodSearch) }, filled = false, size = 36.dp, contentDescription = t.addMeal)
                    },
                )
            }

            if (state.meals.isEmpty()) {
                item {
                    SadoraCard(onClick = { onOpen(Route.FoodSearch) }) {
                        Text(t.emptyTitle, style = Sadora.type.h3, color = c.text)
                        Text(t.emptyBody, style = Sadora.type.body, color = c.muted)
                    }
                }
            } else {
                items(state.meals.size) { index ->
                    val meal = state.meals[index]
                    MealRow(meal, onClick = { pendingDelete = meal })
                }
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
                            Text(t.water, style = Sadora.type.body, color = c.muted)
                            Text(
                                t.waterOfGoal(Fmt.litres(state.waterMl), Fmt.litres(state.waterGoalMl)),
                                style = Sadora.type.h3,
                                color = c.text,
                            )
                        }
                        PillButton(t.addWater(250), { onQuickWater(250) })
                    }
                }
            }

            item {
                AiSummaryCard(
                    label = t.aiAnalysis,
                    body = macroNote(state, t),
                    footnote = t.aiBasis,
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
                            Text(t.scanner, style = Sadora.type.h3, color = c.text)
                            Text(
                                t.scannerHint,
                                style = Sadora.type.body,
                                color = c.muted,
                            )
                        }
                        if (!state.isPremium) SadoraBadge(strings.modules.premiumCapsBadge, BadgeTone.Premium)
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
                            Text(t.balance, style = Sadora.type.h3, color = c.text)
                            Text(
                                t.balanceHint,
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

    // Kept mounted through the exit animation so the sheet does not blank as it closes.
    val lastPending = remember { mutableStateOf<Meal?>(null) }
    pendingDelete?.let { lastPending.value = it }
    SadoraBottomSheet(
        visible = pendingDelete != null,
        title = t.deleteMealTitle,
        onDismiss = { pendingDelete = null },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(t.deleteMealBody, style = Sadora.type.body, color = c.muted)
            lastPending.value?.let { meal ->
                Text(
                    "${meal.description} · ${t.kcal(meal.calories)}",
                    style = Sadora.type.body,
                    color = c.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SadoraButton(
                    strings.common.cancel,
                    onClick = { pendingDelete = null },
                    tone = ButtonTone.Secondary,
                    modifier = Modifier.weight(1f),
                )
                SadoraButton(
                    strings.common.delete,
                    onClick = {
                        pendingDelete?.let(state::deleteMeal)
                        pendingDelete = null
                    },
                    tone = ButtonTone.Destructive,
                    modifier = Modifier.weight(1f),
                )
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
    val t = strings.nutrition
    SadoraCard {
        Text(t.today, style = Sadora.type.body, color = c.muted)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            MacroRing(strings.common.kcal, state.caloriesEaten, state.calorieGoal, c.primary, Modifier.weight(1f), delayMillis = 0)
            MacroRing(t.protein, state.proteinG, state.proteinGoalG, c.protein, Modifier.weight(1f), unit = "g", delayMillis = 90)
            MacroRing(t.fat, state.fatG, state.fatGoalG, c.fat, Modifier.weight(1f), unit = "g", delayMillis = 180)
            MacroRing(t.carbs, state.carbsG, state.carbsGoalG, c.carbs, Modifier.weight(1f), unit = "g", delayMillis = 270)
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
private fun macroNote(state: AppState, t: NutritionStrings): String {
    fun gap(value: Int, goal: Int): Float =
        if (goal <= 0) 0f else 1f - (value / goal.toFloat()).coerceIn(0f, 1f)

    val (name, largest) = listOf(
        t.proteinInline to gap(state.proteinG, state.proteinGoalG),
        t.fatInline to gap(state.fatG, state.fatGoalG),
        t.carbsInline to gap(state.carbsG, state.carbsGoalG),
    ).maxBy { it.second }
    val kcalLeft = (state.calorieGoal - state.caloriesEaten).coerceAtLeast(0)

    // With nothing eaten every gap is 100%, and "protein is what you are short of" is
    // only the first item of a three-way tie.
    if (state.meals.isEmpty()) return t.nothingLoggedNote

    return if (largest < 0.1f) {
        t.balanced(kcalLeft)
    } else {
        t.shortOf(name)
    }
}

/**
 * One meal: photo tile, what it was, "Kechki ovqat • 20:13 • 180 kkal", and the macros
 * as coloured letters. The title is the dish — it used to be the slot, so a scanned
 * "Qovurilgan tuxum" and a typed "Osh" were both just "Kechki ovqat". Tapping offers
 * to delete it, which nothing on the screen could do before.
 */
@Composable
private fun MealRow(meal: Meal, onClick: () -> Unit) {
    val t = strings.nutrition
    val c = Sadora.colors
    SadoraCard(padding = Spacing.sm, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            MealThumb(meal.emoji, size = 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    meal.description.ifBlank { t.mealSlot(meal.slot) },
                    style = Sadora.type.h3,
                    color = c.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(
                        t.mealSlot(meal.slot).takeIf { meal.description.isNotBlank() }.orEmpty(),
                        meal.time,
                        t.kcal(meal.calories),
                    ).filter { it.isNotBlank() }.joinToString(" • "),
                    style = Sadora.type.body,
                    color = c.muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    MacroLetter(strings.modules.proteinInitial, meal.protein, c.protein)
                    MacroLetter(strings.modules.fatInitial, meal.fat, c.fat)
                    MacroLetter(strings.modules.carbsInitial, meal.carbs, c.carbs)
                }
            }
        }
    }
}

@Composable
private fun MacroLetter(letter: String, grams: Int, color: Color) {
    val t = strings.nutrition
    val c = Sadora.colors
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(letter, style = Sadora.type.body.copy(fontWeight = FontWeight.Bold), color = color)
        Text(t.grams(grams), style = Sadora.type.body, color = c.muted)
    }
}
