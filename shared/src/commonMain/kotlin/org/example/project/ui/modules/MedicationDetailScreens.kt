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
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.ChipFlowRow
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTextField
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.SelectChip
import org.example.project.ui.components.noRippleClickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import org.example.project.data.HealthController
import org.example.project.i18n.strings
import org.example.project.model.Fmt
import org.example.project.ui.components.EmptyState
import org.example.project.ui.components.ErrorStrip
import uz.sadora.contract.DoseStatus
import uz.sadora.contract.FoodRelation
import uz.sadora.contract.MedicationSchedule
import uz.sadora.contract.SaveMedicationRequest
import uz.sadora.contract.ScheduleKind
import uz.sadora.contract.Weekday

/**
 * "Dori qo'shish" — the add-medication form.
 *
 * It saves to the server. It used to append a row to the in-memory store and close,
 * so the course she had just entered was gone the next time the app started — and the
 * reminders she was promised had nothing to fire from.
 *
 * Stock and end date are optional; the app tracks supply only if the user opts in
 * by filling them.
 */
@Composable
fun AddMedicationScreen(
    health: HealthController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.modules
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("mg") }
    var time by remember { mutableStateOf("20:00") }
    val weekdays = remember { mutableStateListOf(*Weekday.entries.toTypedArray()) }
    var withFood by remember { mutableStateOf(FoodRelation.AFTER) }
    var stock by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    val at = remember(time) { runCatching { LocalTime.parse(time) }.getOrNull() }

    Column(modifier) {
        SadoraTopBar(t.addMedTitle, onBack = onClose)

        ScreenContent {
            item {
                SadoraCard {
                    SadoraTextField(name, { name = it }, label = t.medName, placeholder = t.medNameHint)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SadoraTextField(
                            dose,
                            { dose = it },
                            label = t.medDose,
                            placeholder = "30",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        )
                        SadoraTextField(
                            unit,
                            { unit = it },
                            label = t.medUnit,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel(t.medTime)
                    SadoraTextField(
                        time,
                        { time = it },
                        label = t.medTime,
                        placeholder = "20:00",
                        error = if (at == null) t.medTimeInvalid else null,
                    )
                }
            }

            item {
                SadoraCard {
                    CardLabel(t.medDays)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Weekday.entries.forEachIndexed { index, weekday ->
                            val on = weekday in weekdays
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(Radius.chip)
                                    .background(if (on) c.primary else c.surface2)
                                    .noRippleClickable {
                                        if (!weekdays.remove(weekday)) weekdays.add(weekday)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    strings.dates.weekdaysShort[index],
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
                    CardLabel(t.medFoodRelation)
                    ChipFlowRow {
                        FoodRelation.entries.forEach { option ->
                            SelectChip(
                                label = t.foodRelation(option),
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
                        label = t.medStock,
                        placeholder = "30",
                        suffix = t.medStockUnit,
                        keyboardType = KeyboardType.Number,
                    )
                }
            }

            item {
                health.error?.let { ErrorStrip(it) }
                SadoraButton(
                    if (saving) strings.common.saving else strings.common.save,
                    enabled = !saving && name.isNotBlank() && at != null && weekdays.isNotEmpty(),
                    onClick = {
                        val chosen = at ?: return@SadoraButton
                        saving = true
                        scope.launch {
                            val saved = health.addMedication(
                                SaveMedicationRequest(
                                    name = name.trim(),
                                    emoji = "💊",
                                    dosage = dose.trim().takeIf { it.isNotEmpty() },
                                    unit = unit.trim().takeIf { it.isNotEmpty() },
                                    foodRelation = withFood,
                                    schedule = MedicationSchedule(
                                        // Every day is "daily" rather than seven weekdays:
                                        // the server derives the doses from the kind, and
                                        // the two are not the same rule to it.
                                        kind = if (weekdays.size == Weekday.entries.size) {
                                            ScheduleKind.DAILY
                                        } else {
                                            ScheduleKind.WEEKDAYS
                                        },
                                        times = listOf(chosen),
                                        weekdays = weekdays.sortedBy { it.ordinal },
                                    ),
                                    stockUnits = stock.toIntOrNull(),
                                ),
                            )
                            saving = false
                            if (saved) onClose()
                        }
                    },
                )
            }
        }
    }
}

/**
 * "Qabul tarixi" — adherence at a glance, from the doses that were actually recorded.
 *
 * It used to draw a fourteen-square grid, "24 taken / 3 skipped / 89%", and three named
 * doses, all written into the file: the same numbers on every phone, including one that
 * had never recorded a dose. The grid pairs colour with a written key, so the pattern is
 * readable without relying on colour perception.
 */
@Composable
fun MedicationHistoryScreen(
    health: HealthController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.modules

    LaunchedEffect(Unit) { health.loadDoseHistory(HistoryDays) }

    val days = health.doseHistory
    val doses = days.flatMap { it.doses }
    val taken = doses.count { it.status == DoseStatus.TAKEN }
    val skipped = doses.count { it.status == DoseStatus.SKIPPED }
    val adherence = if (doses.isEmpty()) null else taken * 100 / doses.size

    fun colorFor(status: DoseStatus) = when (status) {
        DoseStatus.TAKEN -> c.success
        DoseStatus.PENDING -> c.warning
        DoseStatus.SKIPPED -> c.danger
    }

    /** A day is as good as its worst dose: one skipped marks the square. */
    fun statusOf(day: uz.sadora.contract.MedicationDay): DoseStatus = when {
        day.doses.any { it.status == DoseStatus.SKIPPED } -> DoseStatus.SKIPPED
        day.doses.any { it.status == DoseStatus.PENDING } -> DoseStatus.PENDING
        else -> DoseStatus.TAKEN
    }

    Column(modifier) {
        SadoraTopBar(t.doseHistoryTitle, onBack = onClose)

        ScreenContent {
            if (doses.isEmpty()) {
                item {
                    EmptyState(
                        title = t.noDoseHistory,
                        body = t.noDoseHistoryBody,
                        actionText = null,
                        onAction = {},
                        glyph = "💊",
                    )
                }
                return@ScreenContent
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOfNotNull(
                        t.takenCount to "$taken",
                        t.skippedCount to "$skipped",
                        adherence?.let { t.adherenceOver(HistoryDays) to "$it%" },
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
                    CardLabel(t.lastDays(HistoryDays))
                    val squares = days.takeLast(HistoryDays).map { statusOf(it) }
                    squares.chunked(7).forEach { week ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            week.forEach { status ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(Radius.xs))
                                        .background(colorFor(status).copy(alpha = 0.85f)),
                                )
                            }
                            repeat(7 - week.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                    // Colour alone is never the indicator — the key spells it out.
                    ChipFlowRow(horizontalGap = Spacing.xs, verticalGap = Spacing.xxs) {
                        DoseStatus.entries.forEach { status ->
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
                                Text(t.doseStatus(status), style = Sadora.type.body, color = c.muted)
                            }
                        }
                    }
                }
            }

            val recorded = doses
                .filter { it.status != DoseStatus.PENDING }
                .sortedByDescending { it.dueOn }
                .take(RecentDoses)
            items(recorded.size) { index ->
                val dose = recorded[index]
                SadoraCard(padding = Spacing.sm) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(dose.name, style = Sadora.type.h3, color = c.text)
                            Text(
                                strings.dates.relativeDay(dose.dueOn, days.last().date) +
                                    " " + Fmt.clock(dose.dueAt),
                                style = Sadora.type.body,
                                color = c.muted,
                            )
                        }
                        SadoraBadge(
                            t.doseStatus(dose.status),
                            if (dose.status == DoseStatus.TAKEN) BadgeTone.Success else BadgeTone.Danger,
                        )
                    }
                }
            }
        }
    }
}

/** The window the screen reports on, and how many recorded doses it lists under it. */
private const val HistoryDays = 14
private const val RecentDoses = 20
