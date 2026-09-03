package org.example.project.ui.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.example.project.design.IconSize
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.Fmt
import org.example.project.nav.Route
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.ImagePlaceholder
import org.example.project.ui.components.LabeledProgress
import org.example.project.ui.components.PillButton
import org.example.project.ui.components.PremiumGradientBadge
import org.example.project.ui.components.ProgressRing
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.SectionHeader
import org.example.project.ui.components.noRippleClickable

/**
 * "Ovqatlanish" — calorie ring, macros, water and the day's meals.
 *
 * The food scanner is a Premium feature but is never a blocker: manual entry stays
 * available and the scan card is an invitation rather than a wall.
 */
@Composable
fun NutritionScreen(
    state: AppState,
    onOpen: (Route) -> Unit,
    onAddWater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors

    Column(modifier) {
        SadoraTopBar(
            "Ovqatlanish",
            trailing = {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(Radius.chip)
                        .background(c.surface2)
                        .noRippleClickable { onOpen(Route.FoodScanCamera) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("＋", style = Sadora.type.h2, color = c.text)
                }
            },
        )

        ScreenContent {
            item {
                SadoraCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        ProgressRing(
                            progress = state.caloriesEaten / state.calorieGoal.toFloat(),
                            size = 128.dp,
                            strokeWidth = 12.dp,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    Fmt.int(state.caloriesEaten),
                                    style = Sadora.type.h1,
                                    color = c.text,
                                )
                                Text("KKAL", style = Sadora.type.caption, color = c.muted)
                            }
                        }
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "Maqsad ${Fmt.int(state.calorieGoal)} kkal",
                                style = Sadora.type.body,
                                color = c.muted,
                            )
                            Text(
                                "qoldi ${Fmt.int(state.calorieGoal - state.caloriesEaten)}",
                                style = Sadora.type.h2,
                                color = c.text,
                            )
                        }
                    }
                }
            }

            item {
                SadoraCard {
                    LabeledProgress(
                        "Oqsil",
                        "${state.proteinG} / ${state.proteinGoalG} g",
                        state.proteinG / state.proteinGoalG.toFloat(),
                        color = c.secondary,
                    )
                    LabeledProgress(
                        "Yog'",
                        "${state.fatG} / ${state.fatGoalG} g",
                        state.fatG / state.fatGoalG.toFloat(),
                        color = c.primary,
                    )
                    LabeledProgress(
                        "Uglevod",
                        "${state.carbsG} / ${state.carbsGoalG} g",
                        state.carbsG / state.carbsGoalG.toFloat(),
                        color = c.accent,
                    )
                }
            }

            item {
                SadoraCard(padding = Spacing.sm) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text("💧", style = Sadora.type.h3)
                        Text(
                            "Suv ${Fmt.litres(state.waterMl)} / ${Fmt.litres(state.waterGoalMl)} L",
                            style = Sadora.type.h3,
                            color = c.text,
                            modifier = Modifier.weight(1f),
                        )
                        PillButton("+250 ml", onAddWater)
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
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(Radius.md))
                                .background(c.surface2),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("◍", style = Sadora.type.h2, color = c.accentText)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Balans", style = Sadora.type.h3, color = c.text)
                            Text(
                                "Ovqat, suv, faollik va uyqu — to'rt yo'nalish",
                                style = Sadora.type.body,
                                color = c.muted,
                            )
                        }
                        Icon(
    SadoraIcons.ChevronRight,
    contentDescription = null,
    Modifier.size(IconSize.md),
    tint = c.muted2,
)
                    }
                }
            }

            item { SectionHeader("Bugungi ovqatlar", action = "Barchasi") }

            items(state.meals.size) { index ->
                val meal = state.meals[index]
                SadoraCard(padding = Spacing.sm) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        ImagePlaceholder(Modifier.size(64.dp), shape = Radius.field)
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(meal.slot, style = Sadora.type.h3, color = c.text)
                                Text(meal.time, style = Sadora.type.body, color = c.muted)
                            }
                            Text(meal.description, style = Sadora.type.body, color = c.muted)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "${meal.calories} kkal",
                                    style = Sadora.type.body,
                                    color = c.text,
                                )
                                Text(
                                    "O ${meal.protein}g · Y ${meal.fat}g · U ${meal.carbs}g",
                                    style = Sadora.type.body,
                                    color = c.muted2,
                                )
                            }
                        }
                    }
                }
            }

            item {
                SadoraCard(padding = Spacing.sm, onClick = { onOpen(Route.FoodSearch) }) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(Radius.md))
                                .background(c.surface2),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("＋", style = Sadora.type.h2, color = c.secondary)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Kechki ovqat", style = Sadora.type.h3, color = c.text)
                            Text("Qo'shish", style = Sadora.type.body, color = c.muted)
                        }
                    }
                }
            }

            item {
                SadoraCard(onClick = { onOpen(Route.FoodScanCamera) }) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(Radius.md))
                                .background(c.surface2),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("◎", style = Sadora.type.h2, color = c.secondary)
                        }
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text("Ovqat skaneri", style = Sadora.type.h3, color = c.text)
                            Text(
                                "Rasmga oling — kaloriya va makrolar taxminan aniqlanadi",
                                style = Sadora.type.body,
                                color = c.muted,
                            )
                        }
                        if (!state.isPremium) SadoraBadge("PREMIUM", BadgeTone.Premium)
                    }
                }
            }
        }
    }
}
