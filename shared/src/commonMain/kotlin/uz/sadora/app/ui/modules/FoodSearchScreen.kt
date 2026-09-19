package uz.sadora.app.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import kotlinx.coroutines.delay
import uz.sadora.app.data.HealthController
import uz.sadora.app.model.AppState
import uz.sadora.app.model.FoodItem
import uz.sadora.app.model.Meal
import uz.sadora.app.model.deviceNow
import uz.sadora.app.model.mealSlotForHour
import uz.sadora.app.model.nowTimeLabel
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraSearchField
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.contract.MealSlot
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.Fmt

/** Long enough that a fast typist sends one request, short enough to feel immediate. */
private const val SearchDebounceMillis = 250L

/** The wire item as the screen's own type; the two differ only in the field names. */
private fun uz.sadora.contract.FoodItem.toAppFood(): FoodItem = FoodItem(
    name = name,
    kcal = kcal,
    protein = proteinG,
    fat = fatG,
    carbs = carbsG,
    perPiece = perPiece,
)

/**
 * "Taom qidirish va porsiya".
 *
 * The catalogue leads with local Uzbek dishes, offers quick portion presets
 * ("1 kosa") alongside grams, and shows the running total immediately so the user
 * never has to guess what they are about to log.
 */
@Composable
fun FoodSearchScreen(
    state: AppState,
    health: HealthController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Which meal the entry belongs to; by default the one this hour falls in. */
    slot: MealSlot = mealSlotForHour(deviceNow().hour),
) {
    val c = Sadora.colors
    val t = strings.modules
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<FoodItem?>(null) }
    var grams by remember { mutableStateOf(DefaultGrams) }
    // A food sold by the piece is counted, not weighed: "250 dona tuxum" at a hundredth
    // of an egg each is what this screen said before the two were told apart.
    var pieces by remember { mutableStateOf(1) }
    var results by remember { mutableStateOf<List<FoodItem>>(emptyList()) }

    // The catalogue is the server's, and a keystroke does not send a request: the
    // search waits for a pause, so typing "somsa" is one call rather than five.
    LaunchedEffect(query) {
        if (query.isNotBlank()) delay(SearchDebounceMillis)
        results = health.searchFoods(query).map { it.toAppFood() }
        // A result list that no longer holds the selection would leave the totals card
        // describing a dish that is not on screen.
        if (results.none { it == selected }) selected = null
    }

    val chosen = selected
    // The catalogue's numbers are per piece or per 100 g, and the multiplier follows.
    val factor = if (chosen?.perPiece == true) pieces.toFloat() else grams / 100f
    // A new dish starts from its own sensible amount, not the last dish's.
    LaunchedEffect(chosen) {
        grams = DefaultGrams
        pieces = 1
    }

    Column(modifier) {
        SadoraTopBar(strings.nutrition.mealSlot(slot), onBack = onClose)

        ScreenContent {
            item {
                SadoraSearchField(query, { query = it }, placeholder = t.searchFood)
            }

            // There was an All / Frequent / Recipes switch here. Nothing read it: the
            // catalogue has one list, and a control that changes nothing is a broken one.

            if (results.isEmpty()) {
                item {
                    SadoraCard {
                        Text(
                            if (query.isBlank()) t.typeADishName else t.nothingFoundFor(query),
                            style = Sadora.type.h3,
                            color = c.text,
                        )
                        Text(
                            t.catalogueNote,
                            style = Sadora.type.body,
                            color = c.muted,
                        )
                    }
                }
            }

            items(results.size) { index ->
                val food = results[index]
                FoodRow(
                    food = food,
                    selected = food == chosen,
                    onClick = { selected = food },
                )
            }

            if (chosen != null) {
                item {
                    SadoraCard {
                        CardLabel(t.portionLabel)
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Stepper("−") {
                                if (chosen.perPiece) pieces = (pieces - 1).coerceAtLeast(1)
                                else grams = (grams - 50).coerceAtLeast(50)
                            }
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("${if (chosen.perPiece) pieces else grams}", style = Sadora.type.h1, color = c.text)
                                Text(
                                    if (chosen.perPiece) t.pieces else t.grams,
                                    style = Sadora.type.body,
                                    color = c.muted,
                                )
                            }
                            Stepper("+") {
                                if (chosen.perPiece) pieces = (pieces + 1).coerceAtMost(MaxPieces)
                                else grams = (grams + 50).coerceAtMost(2000)
                            }
                        }
                        // Quick presets sit next to the numeric stepper, not instead of it.
                        // They are weights, so a counted food has none.
                        if (!chosen.perPiece) ChipFlowRow {
                            listOf(100 to t.perHundredGrams, 250 to t.bowls(1), 500 to t.bowls(2))
                                .forEach { (value, label) ->
                                    SelectChip(
                                        label = label,
                                        selected = grams == value,
                                        onClick = { grams = value },
                                    )
                                }
                        }
                    }
                }

                item {
                    SadoraCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(t.total, style = Sadora.type.h3, color = c.muted)
                            Text(
                                t.kcalValue(Fmt.int((chosen.kcal * factor).roundToInt())),
                                style = Sadora.type.h1,
                                color = c.text,
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${t.proteinInitial} ${(chosen.protein * factor).roundToInt()}",
                                style = Sadora.type.body,
                                color = c.muted,
                            )
                            Text(
                                "${t.fatInitial} ${(chosen.fat * factor).roundToInt()}",
                                style = Sadora.type.body,
                                color = c.muted,
                            )
                            Text(
                                "${t.carbsInitial} ${(chosen.carbs * factor).roundToInt()}",
                                style = Sadora.type.body,
                                color = c.muted,
                            )
                        }
                    }
                }

                item {
                    SadoraButton(t.addToDiary, onClick = {
                        state.logMeal(
                            Meal(
                                id = "search-${state.meals.size}",
                                slot = slot,
                                time = nowTimeLabel(),
                                description = chosen.name,
                                calories = (chosen.kcal * factor).roundToInt(),
                                protein = (chosen.protein * factor).roundToInt(),
                                fat = (chosen.fat * factor).roundToInt(),
                                carbs = (chosen.carbs * factor).roundToInt(),
                            ),
                        )
                        onClose()
                    })
                }
            }
        }
    }
}

@Composable
private fun FoodRow(food: FoodItem, selected: Boolean, onClick: () -> Unit) {
    val t = strings.modules
    val c = Sadora.colors
    SadoraCard(padding = Spacing.sm, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    food.name,
                    style = Sadora.type.h3.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = c.text,
                )
                Text(
                    t.kcalValue("${food.kcal}") + " / " +
                        (if (food.perPiece) t.perPiece else t.perHundredGrams) +
                        " · ${t.proteinInitial} ${food.protein}" +
                        " · ${t.fatInitial} ${food.fat}" +
                        " · ${t.carbsInitial} ${food.carbs}",
                    style = Sadora.type.body,
                    color = c.muted,
                )
            }
            if (selected) Text("✓", style = Sadora.type.h2, color = c.textAccent)
        }
    }
}

@Composable
private fun Stepper(glyph: String, onClick: () -> Unit) {
    val c = Sadora.colors
    Box(
        Modifier
            .size(44.dp)
            .clip(Radius.chip)
            .background(c.surface2)
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = Sadora.type.h2, color = c.text)
    }
}

/** A bowl. Where the gram stepper starts for every weighed dish. */
private const val DefaultGrams = 250

/** Nobody eats more somsa than this in one sitting, and the server caps kcal at 10 000. */
private const val MaxPieces = 30
