package uz.sadora.app.ui.modules

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
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.model.AppState
import uz.sadora.app.model.Fmt
import uz.sadora.app.model.Meal
import uz.sadora.app.model.deviceNow
import uz.sadora.app.model.mealSlotForHour
import uz.sadora.app.model.nowTimeLabel
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.ImagePlaceholder
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.app.i18n.strings
import uz.sadora.contract.FoodScanResult

/**
 * "Skan natijasi" — what the model made of the photo.
 *
 * Three things the design insists on: the estimate carries a confidence figure, it is
 * labelled as approximate, and the portion can be corrected before anything reaches
 * the diary. The question at the bottom separates eating it from merely checking —
 * which is the whole reason the scan writes nothing on its own.
 */
@Composable
fun FoodScanScreen(
    scan: FoodScanResult,
    state: AppState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.modules
    val n = strings.nutrition
    var portion by remember(scan) { mutableStateOf(1.0f) }

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
                protein = scaled(scan.proteinG),
                fat = scaled(scan.fatG),
                carbs = scaled(scan.carbsG),
            ),
        )
        onClose()
    }

    Column(modifier) {
        SadoraTopBar(t.scanResult, onBack = onClose, centered = true)

        ScreenContent {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(scan.dish, style = Sadora.type.h2, color = c.text, modifier = Modifier.weight(1f))
                        SadoraBadge(
                            t.scanConfidence(scan.confidence),
                            if (scan.confidence >= ConfidentEnough) BadgeTone.Success else BadgeTone.Warning,
                        )
                    }
                    Text(
                        t.portionAndKcal(Fmt.oneDecimal(portion), Fmt.int(scaled(scan.kcal))),
                        style = Sadora.type.body,
                        color = c.muted,
                    )
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MacroCell(n.protein, n.grams(scaled(scan.proteinG)), c.protein, Modifier.weight(1f))
                    MacroCell(n.fat, n.grams(scaled(scan.fatG)), c.fat, Modifier.weight(1f))
                    MacroCell(n.carbs, n.grams(scaled(scan.carbsG)), c.carbs, Modifier.weight(1f))
                }
            }

            // Only what the model actually returned. The "ko'proq ko'rsatish" row used
            // to unfold potassium, omega-3 and iron, none of which anything measured.
            val extra = listOfNotNull(
                scan.fibreG?.let { t.fibre to n.grams(scaled(it)) },
                scan.sugarG?.let { t.sugar to n.grams(scaled(it)) },
                scan.sodiumMg?.let { t.sodium to "${scaled(it)} mg" },
            )
            if (extra.isNotEmpty()) {
                item {
                    SadoraCard {
                        Text(t.nutrients, style = Sadora.type.h3, color = c.text)
                        extra.forEach { (label, value) -> NutrientLine(label, value) }
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
                        Column {
                            Text(t.portion, style = Sadora.type.h3, color = c.text)
                            Text(t.portionHint, style = Sadora.type.body, color = c.muted)
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
                    Text(t.didYouEatIt, style = Sadora.type.h2, color = c.text, textAlign = TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SadoraButton(
                            strings.common.no,
                            onClick = onClose,
                            tone = ButtonTone.Outline,
                            modifier = Modifier.weight(1f),
                        )
                        SadoraButton(t.yesIAte, onClick = ::logIt, modifier = Modifier.weight(1f))
                    }
                    SadoraButton(t.planningToEat, onClick = onClose, tone = ButtonTone.Ghost)
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

/** Above this the identification is shown as confident; below it, as a guess. */
private const val ConfidentEnough = 80
