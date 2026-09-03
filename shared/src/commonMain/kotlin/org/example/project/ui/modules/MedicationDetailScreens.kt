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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.MedStatus
import org.example.project.model.Medication
import org.example.project.model.SampleData
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.ChipFlowRow
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTextField
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.SegmentedControl
import org.example.project.ui.components.SelectChip
import org.example.project.ui.components.noRippleClickable

/**
 * "Dori qo'shish" — the add-medication form.
 *
 * Stock and end date are optional; the app tracks supply only if the user opts in
 * by filling them.
 */
@Composable
fun AddMedicationScreen(
    state: AppState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("mg") }
    var time by remember { mutableStateOf("20:00") }
    val days = remember { mutableStateListOf(*SampleData.weekDays.toTypedArray()) }
    var withFood by remember { mutableStateOf("Keyin") }
    var stock by remember { mutableStateOf("") }

    Column(modifier) {
        SadoraTopBar("Dori qo'shish", onBack = onClose)

        ScreenContent {
            item {
                SadoraCard {
                    SadoraTextField(name, { name = it }, label = "Nomi", placeholder = "Temir")
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SadoraTextField(
                            dose,
                            { dose = it },
                            label = "Doza",
                            placeholder = "30",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        )
                        SadoraTextField(
                            unit,
                            { unit = it },
                            label = "Birlik",
                            trailing = "▾",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel("Qabul vaqti")
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Box(
                            Modifier
                                .clip(Radius.field)
                                .background(c.surface2)
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        ) {
                            Text(time, style = Sadora.type.h2, color = c.text)
                        }
                        Text(
                            "+ Vaqt",
                            style = Sadora.type.body,
                            color = c.textAccent,
                            modifier = Modifier.noRippleClickable {},
                        )
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel("Kunlar")
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SampleData.weekDays.forEach { day ->
                            val on = day in days
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(Radius.chip)
                                    .background(if (on) c.primary else c.surface2)
                                    .noRippleClickable { if (!days.remove(day)) days.add(day) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    day,
                                    style = Sadora.type.caption,
                                    color = if (on) c.onPrimary else c.muted,
                                )
                            }
                        }
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel("Ovqatga nisbatan")
                    ChipFlowRow {
                        listOf("Oldin", "Bilan", "Keyin").forEach { option ->
                            SelectChip(
                                label = option,
                                selected = withFood == option,
                                onClick = { withFood = option },
                            )
                        }
                    }
                }
            }

            item {
                SadoraCard {
                    SadoraTextField(
                        stock,
                        { stock = it },
                        label = "Zaxira",
                        placeholder = "30",
                        suffix = "dona",
                        keyboardType = KeyboardType.Number,
                    )
                    SadoraTextField("Yo'q", {}, label = "Tugash sanasi", trailing = "▾")
                }
            }

            item {
                SadoraButton(
                    "Saqlash",
                    enabled = name.isNotBlank(),
                    onClick = {
                        state.medications.add(
                            Medication(
                                id = "user-${state.medications.size}",
                                emoji = "💊",
                                name = "$name $dose $unit".trim(),
                                time = time,
                                schedule = if (days.size == 7) "Har kuni" else days.joinToString(", "),
                                note = "Ovqatdan $withFood".lowercase(),
                                status = MedStatus.Pending,
                                stockDays = stock.toIntOrNull(),
                            ),
                        )
                        onClose()
                    },
                )
            }
        }
    }
}

private data class DoseRecord(val name: String, val whenLabel: String, val status: MedStatus)

/**
 * "Qabul tarixi" — adherence at a glance.
 *
 * The 14-day grid pairs colour with a written key, so the pattern is readable
 * without relying on colour perception.
 */
@Composable
fun MedicationHistoryScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors

    val grid = listOf(
        MedStatus.Taken, MedStatus.Taken, MedStatus.Skipped, MedStatus.Taken,
        MedStatus.Taken, MedStatus.Taken, MedStatus.Taken, MedStatus.Pending,
        MedStatus.Taken, MedStatus.Taken, MedStatus.Taken, MedStatus.Skipped,
        MedStatus.Taken, MedStatus.Taken,
    )
    val records = listOf(
        DoseRecord("Temir 30 mg", "Bugun 20:04", MedStatus.Taken),
        DoseRecord("Folik kislota", "Bugun 08:12", MedStatus.Taken),
        DoseRecord("Temir 30 mg", "17-avgust", MedStatus.Skipped),
    )

    fun colorFor(status: MedStatus) = when (status) {
        MedStatus.Taken -> c.success
        MedStatus.Pending -> c.warning
        MedStatus.Skipped -> c.danger
    }

    fun labelFor(status: MedStatus) = when (status) {
        MedStatus.Taken -> "Qabul qilingan"
        MedStatus.Pending -> "Kechiktirilgan"
        MedStatus.Skipped -> "O'tkazilgan"
    }

    Column(modifier) {
        SadoraTopBar("Qabul tarixi", onBack = onClose)

        ScreenContent {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf(
                        "Qabul qilingan" to "24",
                        "O'tkazilgan" to "3",
                        "30 kun" to "89%",
                    ).forEach { (label, value) ->
                        SadoraCard(modifier = Modifier.weight(1f), padding = Spacing.sm) {
                            Text(label, style = Sadora.type.body, color = c.muted)
                            Text(value, style = Sadora.type.h2, color = c.text)
                        }
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel("Oxirgi 14 kun")
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        grid.take(7).forEach { status ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(Radius.xs))
                                    .background(colorFor(status).copy(alpha = 0.85f)),
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        grid.drop(7).forEach { status ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(Radius.xs))
                                    .background(colorFor(status).copy(alpha = 0.85f)),
                            )
                        }
                    }
                    // Colour alone is never the indicator — the key spells it out.
                    ChipFlowRow(horizontalGap = Spacing.xs, verticalGap = Spacing.xxs) {
                        listOf(MedStatus.Taken, MedStatus.Pending, MedStatus.Skipped)
                            .forEach { status ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .size(10.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(colorFor(status)),
                                    )
                                    Text(
                                        labelFor(status),
                                        style = Sadora.type.body,
                                        color = c.muted,
                                    )
                                }
                            }
                    }
                }
            }

            items(records.size) { index ->
                val record = records[index]
                SadoraCard(padding = Spacing.sm) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(record.name, style = Sadora.type.h3, color = c.text)
                            Text(record.whenLabel, style = Sadora.type.body, color = c.muted)
                        }
                        SadoraBadge(
                            labelFor(record.status),
                            if (record.status == MedStatus.Taken) {
                                BadgeTone.Success
                            } else {
                                BadgeTone.Danger
                            },
                        )
                    }
                }
            }
        }
    }
}
