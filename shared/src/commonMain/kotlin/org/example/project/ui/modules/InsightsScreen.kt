package org.example.project.ui.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.SampleData
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.DisclaimerNote
import org.example.project.ui.components.LockedBlock
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.SegmentedControl
import org.example.project.ui.components.WeeklyBars

/**
 * "Tahlillar" — trends and the AI narrative.
 *
 * Free accounts get 7 days; the 30- and 90-day ranges are visible but locked, and
 * the narrative block stays on screen dimmed rather than being hidden, so the user
 * can see exactly what Premium adds. Findings are always phrased as co-occurrence.
 */
@Composable
fun InsightsScreen(
    state: AppState,
    onClose: () -> Unit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    var range by remember { mutableStateOf(0) }
    val locked = if (state.isPremium) emptySet() else setOf(1, 2)

    Column(modifier) {
        SadoraTopBar("Tahlillar", onBack = onClose)

        ScreenContent {
            item {
                SegmentedControl(
                    options = listOf("7 kun", "30 kun", "90 kun"),
                    selectedIndex = range,
                    onSelect = { range = it },
                    lockedIndices = locked,
                )
            }

            item {
                SadoraCard {
                    CardLabel(
                        "Uyqu trendi",
                        trailing = {
                            Text("+12 daqiqa", style = Sadora.type.body, color = c.success)
                        },
                    )
                    WeeklyBars(
                        values = listOf(0.6f, 0.72f, 0.55f, 0.85f, 0.68f, 0.9f, 0.78f),
                        labels = SampleData.weekDays,
                        color = c.accent,
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SadoraCard(modifier = Modifier.weight(1f), padding = Spacing.sm) {
                        CardLabel("Kayfiyat")
                        Text("Barqaror", style = Sadora.type.h2, color = c.text)
                    }
                    SadoraCard(modifier = Modifier.weight(1f), padding = Spacing.sm) {
                        CardLabel("Faollik")
                        Text("+8%", style = Sadora.type.h2, color = c.success)
                    }
                }
            }

            item { CardLabel("AI hikoyaviy tahlil") }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SadoraCard(
                        modifier = if (state.isPremium) Modifier else Modifier.alpha(0.35f),
                    ) {
                        Text(
                            "Oxirgi 30 kunda uyqu davomiyligi ortgan kunlarda energiya " +
                                "darajasi ham yuqori bo'lgan. Bu holatlar ko'pincha birga " +
                                "kuzatilgan.",
                            style = Sadora.type.body,
                            color = c.text,
                        )
                        Text(
                            "Suv iste'moli 2 L dan oshgan kunlarda bosh og'rig'i kamroq " +
                                "qayd etilgan.",
                            style = Sadora.type.body,
                            color = c.text,
                        )
                    }
                    if (!state.isPremium) {
                        LockedBlock("Premium bilan ochiladi", onUnlock = onUpgrade)
                    }
                }
            }

            item { DisclaimerNote(SampleData.correlationDisclaimer) }
        }
    }
}
