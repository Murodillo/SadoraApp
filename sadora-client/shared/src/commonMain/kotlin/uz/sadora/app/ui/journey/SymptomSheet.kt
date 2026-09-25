package uz.sadora.app.ui.journey

import kotlinx.datetime.LocalDate
import uz.sadora.contract.DailyLog
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.data.readable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import uz.sadora.app.data.HealthController
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.SadoraBottomSheet
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraTextField
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.contract.SymptomCategory
import uz.sadora.contract.SymptomDefinition
import uz.sadora.contract.SymptomEntry
import uz.sadora.contract.SymptomSeverity
import uz.sadora.app.i18n.strings
import uz.sadora.app.ui.components.acceptText
import uz.sadora.contract.Limits

/** The five-step scale collapses onto the wire's three; the wording carries the rest. */
private fun Int.toSeverity(): SymptomSeverity = when (this) {
    1, 2 -> SymptomSeverity.MILD
    3 -> SymptomSeverity.MODERATE
    else -> SymptomSeverity.SEVERE
}

private fun SymptomSeverity.toStep(): Int = when (this) {
    SymptomSeverity.MILD -> 2
    SymptomSeverity.MODERATE -> 3
    SymptomSeverity.SEVERE -> 4
}

/** The order the sheet reads in; anything outside it falls under "Boshqa". */
private val categoryOrder = listOf(
    SymptomCategory.PAIN,
    SymptomCategory.BLEEDING,
    SymptomCategory.MOOD,
    SymptomCategory.SLEEP,
    SymptomCategory.ENERGY,
    SymptomCategory.DIGESTION,
    SymptomCategory.SKIN,
    SymptomCategory.OTHER,
)

/**
 * "Simptom qo'shish" — the logging bottom sheet.
 *
 * The severity scale is always accompanied by wording, so "3" never has to be
 * interpreted by the user on its own.
 *
 * The chips are the server's catalogue rather than a list written here, so what she can
 * log is what the counts elsewhere in the app can count. Severity and the note are part
 * of the record — the whole day goes up in one request, which is also why the sheet
 * opens already showing what today holds instead of starting blank over the top of it.
 */
@Composable
fun SymptomSheet(
    visible: Boolean,
    state: AppState,
    health: HealthController,
    onDismiss: () -> Unit,
    /**
     * The day being written. The calendar opens this sheet for any day up to today, and
     * it used to load and save today whichever one she had tapped.
     */
    date: LocalDate = state.today,
) {
    val c = Sadora.colors
    val t = strings.journey
    val scope = rememberCoroutineScope()
    val isToday = date == state.today

    // Another day's record is held here, not in the controller: `health.day` is today's,
    // and the rest of the app reads it as such.
    var otherDay by remember(date) { mutableStateOf<DailyLog?>(null) }
    LaunchedEffect(visible, date) {
        if (!visible) return@LaunchedEffect
        health.loadSymptoms(null)
        if (isToday) health.loadDay(date) else otherDay = health.dayAt(date)
    }

    val catalogue = health.symptoms
    val today = if (isToday) health.day else otherDay
    // Re-keyed on the day so reopening the sheet shows what is on the server now, and
    // editing it removes as well as adds.
    val selected = remember(visible, today) {
        mutableStateListOf<String>().apply { addAll(today?.symptoms.orEmpty().map { it.key }) }
    }
    var severity by remember(visible, today) {
        mutableStateOf(today?.symptoms?.firstOrNull()?.severity?.toStep() ?: 3)
    }
    var note by remember(visible, today) { mutableStateOf(today?.note.orEmpty()) }
    var saving by remember { mutableStateOf(false) }

    SadoraBottomSheet(visible = visible, title = t.symptomSheetTitle, onDismiss = onDismiss) {
        Text(strings.dates.dayMonth(date), style = Sadora.type.body, color = c.muted)

        if (catalogue.isEmpty()) {
            Text(t.catalogueLoading, style = Sadora.type.body, color = c.muted2)
        }

        categoryOrder.forEach { category ->
            val group = catalogue.filter { it.category == category }
            if (group.isEmpty()) return@forEach
            CardLabel(t.categoryName(category))
            ChipFlowRow {
                group.forEach { definition ->
                    SelectChip(
                        label = definition.label,
                        selected = definition.key in selected,
                        onClick = {
                            if (!selected.remove(definition.key)) selected.add(definition.key)
                        },
                    )
                }
            }
        }

        if (selected.isNotEmpty()) {
            CardLabel(t.severity)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                (1..5).forEach { level ->
                    val isSelected = level == severity
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(Radius.field)
                            .background(if (isSelected) c.primary else c.surface2)
                            .noRippleClickable { severity = level }
                            .padding(vertical = Spacing.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$level",
                            style = Sadora.type.h3.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isSelected) c.onPrimary else c.text,
                        )
                    }
                }
            }
            Text(t.severityWords[severity - 1], style = Sadora.type.body, color = c.muted)
        }

        SadoraTextField(
            note,
            { note = acceptText(it, Limits.DAY_NOTE_MAX) },
            placeholder = t.notePlaceholder,
            singleLine = false,
        )

        health.error?.let { ErrorStrip(it.readable()) }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SadoraButton(
                strings.common.cancel,
                onClick = onDismiss,
                tone = ButtonTone.Secondary,
                modifier = Modifier.weight(1f),
            )
            SadoraButton(
                if (saving) strings.common.saving else strings.common.save,
                onClick = {
                    saving = true
                    scope.launch {
                        // Everything the sheet does not edit is passed through from the
                        // day it loaded: the save replaces the record wholesale, and the
                        // defaults would otherwise fill a past day with today's values.
                        val saved = health.saveDay(
                            date = date,
                            flow = today?.flow,
                            mood = today?.mood,
                            energy = today?.energy,
                            stress = today?.stress,
                            symptomKeys = selected.map { SymptomEntry(it, severity.toSeverity()) },
                            note = note.trim().takeIf { it.isNotEmpty() },
                            fetalMovement = today?.fetalMovement,
                        )
                        saving = false
                        // A failed save keeps the sheet, and what she typed, on screen.
                        if (saved) onDismiss()
                    }
                },
                enabled = !saving,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Kept so a caller with only the catalogue can name a key. */
internal fun List<SymptomDefinition>.labelFor(key: String): String =
    firstOrNull { it.key == key }?.label ?: key
