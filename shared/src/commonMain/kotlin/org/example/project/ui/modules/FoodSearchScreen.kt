package org.example.project.ui.modules

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
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.FoodItem
import org.example.project.model.Meal
import org.example.project.model.SampleData
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.ChipFlowRow
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraSearchField
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.SegmentedControl
import org.example.project.ui.components.SelectChip
import org.example.project.ui.components.noRippleClickable

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
    slot: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    var query by remember { mutableStateOf("osh") }
    var tab by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<FoodItem?>(SampleData.foods.first()) }
    var grams by remember { mutableStateOf(250) }

    val results = SampleData.foods.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true)
    }

    val chosen = selected
    val factor = grams / 100f

    Column(modifier) {
        SadoraTopBar(slot, onBack = onClose)

        ScreenContent {
            item {
                SadoraSearchField(query, { query = it }, placeholder = "Taom qidirish")
            }

            item {
                SegmentedControl(
                    options = listOf("Barchasi", "Tez-tez", "Retseptlar"),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                )
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
                        CardLabel("Porsiya")
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Stepper("−") { grams = (grams - 50).coerceAtLeast(50) }
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("$grams", style = Sadora.type.h1, color = c.text)
                                Text(
                                    if (chosen.perPiece) "dona × 100" else "gramm",
                                    style = Sadora.type.body,
                                    color = c.muted,
                                )
                            }
                            Stepper("+") { grams = (grams + 50).coerceAtMost(2000) }
                        }
                        // Quick presets sit next to the numeric stepper, not instead of it.
                        ChipFlowRow {
                            listOf(100 to "100 g", 250 to "1 kosa", 500 to "2 kosa")
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
                            Text("Jami", style = Sadora.type.h3, color = c.muted)
                            Text(
                                "${(chosen.kcal * factor).roundToInt()} kkal",
                                style = Sadora.type.h1,
                                color = c.text,
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "O ${(chosen.protein * factor).roundToInt()}",
                                style = Sadora.type.body,
                                color = c.muted,
                            )
                            Text(
                                "Y ${(chosen.fat * factor).roundToInt()}",
                                style = Sadora.type.body,
                                color = c.muted,
                            )
                            Text(
                                "U ${(chosen.carbs * factor).roundToInt()}",
                                style = Sadora.type.body,
                                color = c.muted,
                            )
                        }
                    }
                }

                item {
                    SadoraButton("Kundalikka qo'shish", onClick = {
                        state.logMeal(
                            Meal(
                                id = "search-${state.meals.size}",
                                slot = slot,
                                time = "hozir",
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
                    "${food.kcal} kkal / ${if (food.perPiece) "dona" else "100 g"} · " +
                        "O ${food.protein} · Y ${food.fat} · U ${food.carbs}",
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
