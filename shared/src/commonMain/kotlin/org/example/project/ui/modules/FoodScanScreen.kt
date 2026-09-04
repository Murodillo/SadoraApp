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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.Fmt
import org.example.project.model.Meal
import org.example.project.model.deviceNow
import org.example.project.model.mealSlotForHour
import org.example.project.model.nowTimeLabel
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.ImagePlaceholder
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.noRippleClickable

/** What the scanner recognised. Fixed until a vision model sits behind the camera. */
internal data class ScanResult(
    val dish: String,
    val emoji: String,
    val confidence: Int,
    val kcal: Int,
    val protein: Int,
    val fat: Int,
    val carbs: Int,
    val fibreG: Int,
    val sugarG: Int,
    val sodiumMg: Int,
)

internal val sampleScan = ScanResult(
    dish = "Losos, kinoa va avokado",
    emoji = "🥗",
    confidence = 82,
    kcal = 450,
    protein = 30,
    fat = 18,
    carbs = 35,
    fibreG = 6,
    sugarG = 2,
    sodiumMg = 320,
)

/**
 * "Skan natijasi" — the deck's result screen.
 *
 * Three things the design insists on: the estimate carries a confidence figure, it is
 * labelled as approximate, and the portion can be corrected before anything reaches
 * the diary. The question at the bottom separates eating it from merely checking.
 */
@Composable
fun FoodScanScreen(
    state: AppState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val scan = sampleScan
    var portion by remember { mutableStateOf(1.0f) }
    var showMore by remember { mutableStateOf(false) }

    fun scaled(value: Int) = (value * portion).toInt()

    fun logIt() {
        val now = deviceNow()
        state.logMeal(
            Meal(
                id = "scan-${state.meals.size}",
                slot = mealSlotForHour(now.hour),
                time = nowTimeLabel(),
                description = scan.dish,
                calories = scaled(scan.kcal),
                protein = scaled(scan.protein),
                fat = scaled(scan.fat),
                carbs = scaled(scan.carbs),
            ),
        )
        onClose()
    }

    Column(modifier) {
        SadoraTopBar("Skan natijasi", onBack = onClose, centered = true)

        ScreenContent {
            item {
                ImagePlaceholder(
                    Modifier.fillMaxWidth().aspectRatio(1.25f),
                    emoji = scan.emoji,
                    shape = Radius.card,
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(scan.dish, style = Sadora.type.h2, color = c.text, modifier = Modifier.weight(1f))
                        SadoraBadge("Ishonch ${scan.confidence}%", if (scan.confidence >= 80) BadgeTone.Success else BadgeTone.Warning)
                    }
                    Text(
                        "${Fmt.oneDecimal(portion)} porsiya • ${Fmt.int(scaled(scan.kcal))} kkal • taxminan",
                        style = Sadora.type.body,
                        color = c.muted,
                    )
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MacroCell("Oqsil", "${scaled(scan.protein)} g", c.protein, Modifier.weight(1f))
                    MacroCell("Yog'", "${scaled(scan.fat)} g", c.fat, Modifier.weight(1f))
                    MacroCell("Uglevod", "${scaled(scan.carbs)} g", c.carbs, Modifier.weight(1f))
                }
            }

            item {
                SadoraCard {
                    Text("Ozuqaviy qiymat", style = Sadora.type.h3, color = c.text)
                    NutrientLine("Tola", "${scaled(scan.fibreG)} g")
                    NutrientLine("Shakar", "${scaled(scan.sugarG)} g")
                    NutrientLine("Natriy", "${scaled(scan.sodiumMg)} mg")
                    if (showMore) {
                        NutrientLine("Kaliy", "${scaled(680)} mg")
                        NutrientLine("Omega-3", "${Fmt.oneDecimal(1.8f * portion)} g")
                        NutrientLine("Temir", "${Fmt.oneDecimal(2.4f * portion)} mg")
                    }
                    Text(
                        if (showMore) "Kamroq ko'rsatish" else "Ko'proq ko'rsatish",
                        style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = c.textAccent,
                        modifier = Modifier.noRippleClickable { showMore = !showMore },
                    )
                }
            }

            item {
                SadoraCard(padding = Spacing.sm) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("Porsiya", style = Sadora.type.h3, color = c.text)
                            Text("AI baholashi taxminiy — o'zingiz to'g'rilang", style = Sadora.type.body, color = c.muted)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            StepperButton("−") { portion = (portion - 0.5f).coerceAtLeast(0.5f) }
                            Text(Fmt.oneDecimal(portion), style = Sadora.type.h2, color = c.text)
                            StepperButton("+") { portion = (portion + 0.5f).coerceAtMost(5f) }
                        }
                    }
                }
            }

            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Buni yedingizmi?", style = Sadora.type.h2, color = c.text, textAlign = TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SadoraButton("Yo'q", onClick = onClose, tone = ButtonTone.Outline, modifier = Modifier.weight(1f))
                        SadoraButton("Ha, yedim", onClick = ::logIt, modifier = Modifier.weight(1f))
                    }
                    SadoraButton("Yeyishni rejalashtiryapman", onClick = onClose, tone = ButtonTone.Ghost)
                }
            }
        }
    }
}

@Composable
private fun MacroCell(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    SadoraCard(modifier = modifier, padding = Spacing.sm, verticalGap = Spacing.xxs) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).clip(Radius.chip).background(color))
            Text(label, style = Sadora.type.body, color = c.muted)
        }
        Text(value, style = Sadora.type.h2, color = c.text)
    }
}

@Composable
private fun NutrientLine(label: String, value: String) {
    val c = Sadora.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = Sadora.type.body, color = c.muted)
        Text(value, style = Sadora.type.body, color = c.text)
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
