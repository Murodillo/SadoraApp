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
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTextField
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.ui.components.noRippleClickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.PillButton
import uz.sadora.app.ui.components.SadoraDialog
import uz.sadora.contract.Medication
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import uz.sadora.app.data.HealthController
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.Fmt
import uz.sadora.app.ui.components.EmptyState
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.contract.DoseStatus
import uz.sadora.contract.FoodRelation
import uz.sadora.contract.MedicationSchedule
import uz.sadora.contract.SaveMedicationRequest
import uz.sadora.contract.ScheduleKind
import uz.sadora.contract.Weekday
import uz.sadora.app.data.readable
import uz.sadora.app.ui.components.acceptDigits
import uz.sadora.app.ui.components.acceptText
import uz.sadora.app.ui.components.numberError
import uz.sadora.app.ui.components.parseTypedTime
import uz.sadora.app.ui.components.requiredTextError
import uz.sadora.app.ui.components.typedTimeError
import uz.sadora.contract.Limits
import uz.sadora.app.resources.*

/**
 * "Dori qo'shish" — the add-medication form, and with [editingId] the same form filled
 * with one course, saving over it and offering to delete it.
 *
 * It saves to the server. It used to append a row to the in-memory store and close,
 * so the course she had just entered was gone the next time the app started — and the
 * reminders she was promised had nothing to fire from. There was also no way back to a
 * course once saved: a wrong time or a finished pack stayed as typed.
 *
 * Stock and end date are optional; the app tracks supply only if the user opts in
 * by filling them.
 */
@Composable
fun AddMedicationScreen(
    health: HealthController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    editingId: String? = null,
) {
    val t = strings.modules
    val existing = editingId?.let { id -> health.medications.firstOrNull { it.id == id } }

    if (editingId != null && existing == null) {
        // Opened from a link or after a restart, before the list arrived.
        LaunchedEffect(editingId) { health.refreshMedications() }
        Column(modifier) {
            SadoraTopBar(t.editMedTitle, onBack = onClose)
            ScreenContent {
                item { health.error?.let { ErrorStrip(it.readable()) } }
            }
        }
        return
    }
    // Keyed by the course, so the fields start from it once — not again on every refresh.
    key(existing?.id) { MedicationForm(health, existing, onClose, modifier) }
}

@Composable
private fun MedicationForm(
    health: HealthController,
    existing: Medication?,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val c = Sadora.colors
    val t = strings.modules
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var dose by remember { mutableStateOf(existing?.dosage.orEmpty()) }
    var unit by remember { mutableStateOf(existing?.unit ?: "mg") }
    val times = remember {
        mutableStateListOf(
            *(existing?.schedule?.times?.map { it.toString().take(TimeFieldMax) } ?: listOf("20:00")).toTypedArray(),
        )
    }
    val weekdays = remember {
        val chosen = existing?.schedule
            ?.takeIf { it.kind == ScheduleKind.WEEKDAYS }
            ?.weekdays
            ?: Weekday.entries
        mutableStateListOf(*chosen.toTypedArray())
    }
    // Every N days cannot be drawn as weekdays; a course set that way keeps its rule.
    val keepsInterval = existing?.schedule?.kind == ScheduleKind.INTERVAL
    // From a doctor's prescription: what she decided is shown, not edited — the server
    // keeps it whatever is sent — and only the times move, as many as she gave.
    val prescribed = existing?.prescriptionId != null
    var withFood by remember { mutableStateOf(existing?.foodRelation ?: FoodRelation.AFTER) }
    var stock by remember { mutableStateOf(existing?.stockUnits?.toString().orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val parsed = times.map { parseTypedTime(it) }
    val repeated = parsed.filterNotNull().let { it.size != it.distinct().size }
    val nameError = requiredTextError(name, Limits.MEDICATION_NAME_MAX)
    val stockError = numberError(stock, 1..Limits.MEDICATION_STOCK_MAX)
    val valid = name.isNotBlank() && nameError == null && stockError == null &&
        parsed.all { it != null } && !repeated && (keepsInterval || weekdays.isNotEmpty())

    Column(modifier) {
        SadoraTopBar(if (existing == null) t.addMedTitle else t.editMedTitle, onBack = onClose)

        ScreenContent {
            if (prescribed) {
                item {
                    val rx = strings.prescriptions
                    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xxs) {
                        Text(
                            existing?.prescribedBy?.let(rx::prescribedBy) ?: rx.title,
                            style = Sadora.type.h3,
                            color = c.textAccent,
                        )
                        Text(rx.lockedNote, style = Sadora.type.body, color = c.muted)
                    }
                }
            }
            item {
                SadoraCard {
                    SadoraTextField(
                        name,
                        { name = acceptText(it, Limits.MEDICATION_NAME_MAX) },
                        label = t.medName,
                        placeholder = t.medNameHint,
                        error = nameError,
                        enabled = !prescribed,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SadoraTextField(
                            dose,
                            { dose = acceptDigits(it, 5) },
                            label = t.medDose,
                            placeholder = "30",
                            keyboardType = KeyboardType.Number,
                            enabled = !prescribed,
                            modifier = Modifier.weight(1f),
                        )
                        SadoraTextField(
                            unit,
                            { unit = acceptText(it, UnitMax) },
                            label = t.medUnit,
                            enabled = !prescribed,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel(t.medTime)
                    times.forEachIndexed { index, value ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            SadoraTextField(
                                value,
                                { times[index] = acceptText(it, TimeFieldMax) },
                                label = t.medTime,
                                placeholder = "20:00",
                                // A row just added is empty, not wrong: Save waits for it
                                // without painting it red before she has typed.
                                error = typedTimeError(value, required = false)
                                    ?: t.medTimesRepeat.takeIf {
                                        repeated && parsed[index] != null &&
                                            parsed.indexOf(parsed[index]) != index
                                    },
                                modifier = Modifier.weight(1f),
                            )
                            if (times.size > 1 && !prescribed) {
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(Radius.chip)
                                        .background(c.surface2)
                                        .semantics { contentDescription = t.removeTime }
                                        .noRippleClickable { times.removeAt(index) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("✕", style = Sadora.type.body, color = c.muted)
                                }
                            }
                        }
                    }
                    if (times.size < Limits.MEDICATION_TIMES_PER_DAY_MAX && !prescribed) {
                        PillButton(t.addTime, { times.add("") })
                    }
                }
            }

            if (!keepsInterval && !prescribed) {
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
            }

            if (!prescribed) item {
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
                        { stock = acceptDigits(it, 5) },
                        label = t.medStock,
                        placeholder = "30",
                        suffix = t.medStockUnit,
                        error = stockError,
                        keyboardType = KeyboardType.Number,
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    health.error?.let { ErrorStrip(it.readable()) }
                    SadoraButton(
                        if (saving) strings.common.saving else strings.common.save,
                        enabled = !saving && valid,
                        onClick = {
                            val chosen = parsed.filterNotNull().sorted()
                            val schedule = existing?.schedule?.takeIf { keepsInterval || prescribed }?.copy(times = chosen)
                                ?: MedicationSchedule(
                                    // Every day is "daily" rather than seven weekdays: the
                                    // server derives the doses from the kind, and the two are
                                    // not the same rule to it.
                                    kind = if (weekdays.size == Weekday.entries.size) {
                                        ScheduleKind.DAILY
                                    } else {
                                        ScheduleKind.WEEKDAYS
                                    },
                                    times = chosen,
                                    weekdays = weekdays.sortedBy { it.ordinal },
                                )
                            // What the form does not show is carried over, not reset.
                            val request = SaveMedicationRequest(
                                name = name.trim(),
                                emoji = existing?.emoji ?: "💊",
                                dosage = dose.trim().takeIf { it.isNotEmpty() },
                                unit = unit.trim().takeIf { it.isNotEmpty() },
                                foodRelation = withFood,
                                note = existing?.note,
                                schedule = schedule,
                                remindersEnabled = existing?.remindersEnabled ?: true,
                                startedOn = existing?.startedOn,
                                endedOn = existing?.endedOn,
                                stockUnits = stock.toIntOrNull(),
                            )
                            saving = true
                            scope.launch {
                                val saved = if (existing == null) {
                                    health.addMedication(request)
                                } else {
                                    health.updateMedication(existing.id, request)
                                }
                                saving = false
                                if (saved) onClose()
                            }
                        },
                    )
                    if (existing != null) {
                        SadoraButton(
                            t.deleteMedication,
                            { confirmDelete = true },
                            tone = ButtonTone.Destructive,
                            enabled = !saving,
                        )
                    }
                }
            }
        }
    }

    SadoraDialog(
        visible = confirmDelete,
        title = t.deleteMedTitle,
        body = t.deleteMedBody,
        confirmText = strings.common.delete,
        onConfirm = {
            confirmDelete = false
            val id = existing?.id ?: return@SadoraDialog
            saving = true
            scope.launch {
                val deleted = health.archiveMedication(id)
                saving = false
                if (deleted) onClose()
            }
        },
        onDismiss = { confirmDelete = false },
    )
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
                        art = Res.drawable.ic3d_meds,
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

/** "20:00" — nothing longer is a time of day. */
private const val TimeFieldMax = 5


/** "mg", "mkg", "ml", "tabletka" — a unit, not a sentence. */
private const val UnitMax = 12

/** The window the screen reports on, and how many recorded doses it lists under it. */
private const val HistoryDays = 14
private const val RecentDoses = 20
