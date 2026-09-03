package org.example.project.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.unit.dp
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.Fmt
import org.example.project.model.Meal
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.DisclaimerNote
import org.example.project.ui.components.ImagePlaceholder
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.noRippleClickable

/**
 * "Ovqat skaneri" — the scan result screen.
 *
 * Three things the design insists on: the estimate carries a confidence figure, it
 * is labelled as approximate, and the user can always correct it. The three actions
 * distinguish eating now, planning to eat, and merely checking.
 */
@Composable
fun FoodScanScreen(
    state: AppState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    var portion by remember { mutableStateOf(1.0f) }

    val baseCalories = 520
    val baseProtein = 34
    val baseFat = 19
    val baseCarbs = 48

    fun scaled(value: Int) = (value * portion).toInt()

    Column(modifier) {
        SadoraTopBar("", onBack = onClose)

        ScreenContent {
            item {
                CardLabel("Suratga olingan taom")
                ImagePlaceholder(Modifier.fillMaxWidth().aspectRatio(1.3f), shape = Radius.card)
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SadoraButton(
                        "Qayta suratga olish",
                        onClick = {},
                        tone = ButtonTone.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                    SadoraButton(
                        "Galereyadan",
                        onClick = {},
                        tone = ButtonTone.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                SadoraCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Tovuqli salat, non", style = Sadora.type.h2, color = c.text)
                        SadoraBadge("Ishonch 78%", BadgeTone.Warning)
                    }
                    Text(
                        "AI baholashi taxminiy. Miqdorni o'zingiz to'g'rilashingiz mumkin.",
                        style = Sadora.type.body,
                        color = c.muted,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            Fmt.int(scaled(baseCalories)),
                            style = Sadora.type.data,
                            color = c.text,
                        )
                        Text(
                            "  kkal · taxminan",
                            style = Sadora.type.body,
                            color = c.muted,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        MacroCell("Oqsil", "${scaled(baseProtein)} g", Modifier.weight(1f))
                        MacroCell("Yog'", "${scaled(baseFat)} g", Modifier.weight(1f))
                        MacroCell("Uglevod", "${scaled(baseCarbs)} g", Modifier.weight(1f))
                    }
                }
            }

            item {
                SadoraCard(padding = Spacing.sm) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Porsiya", style = Sadora.type.h3, color = c.text)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            StepperButton("−") {
                                portion = (portion - 0.5f).coerceAtLeast(0.5f)
                            }
                            Text(
                                Fmt.oneDecimal(portion),
                                style = Sadora.type.h2,
                                color = c.text,
                            )
                            StepperButton("+") {
                                portion = (portion + 0.5f).coerceAtMost(5f)
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SadoraButton("Ha, buni yedim", onClick = {
                        state.logMeal(
                            Meal(
                                id = "scan-${state.meals.size}",
                                slot = "Skanerlangan",
                                time = "hozir",
                                description = "Tovuqli salat, non",
                                calories = scaled(baseCalories),
                                protein = scaled(baseProtein),
                                fat = scaled(baseFat),
                                carbs = scaled(baseCarbs),
                            ),
                        )
                        onClose()
                    })
                    SadoraButton(
                        "Yeyishni rejalashtiryapman",
                        onClick = onClose,
                        tone = ButtonTone.Secondary,
                    )
                    SadoraButton(
                        "Shunchaki tekshirdim",
                        onClick = onClose,
                        tone = ButtonTone.Ghost,
                    )
                }
            }

            item {
                DisclaimerNote("Natijani to'g'rilash", icon = "✎")
            }
        }
    }
}

@Composable
private fun MacroCell(label: String, value: String, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    Column(
        modifier
            .clip(Radius.field)
            .background(c.surface2)
            .padding(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = Sadora.type.body, color = c.muted)
        Text(value, style = Sadora.type.h3, color = c.text)
    }
}

@Composable
private fun StepperButton(glyph: String, onClick: () -> Unit) {
    val c = Sadora.colors
    Box(
        Modifier
            .size(36.dp)
            .clip(Radius.chip)
            .background(c.surface2)
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = Sadora.type.h2, color = c.text)
    }
}
