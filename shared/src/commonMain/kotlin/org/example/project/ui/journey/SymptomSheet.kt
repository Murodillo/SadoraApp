package org.example.project.ui.journey

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
import org.example.project.data.HealthController
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.Fmt
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.ChipFlowRow
import org.example.project.ui.components.SadoraBottomSheet
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraTextField
import org.example.project.ui.components.SelectChip
import org.example.project.ui.components.noRippleClickable
import uz.sadora.contract.SymptomCategory
import uz.sadora.contract.SymptomDefinition
import uz.sadora.contract.SymptomEntry
import uz.sadora.contract.SymptomSeverity
import org.example.project.i18n.strings

/** Severity 1–5, each step explained in words rather than left as a bare number. */
private val severityWords = listOf(
    "Sezilmaydi",
    "Yengil — kunlik ishlarga to'sqinlik qilmaydi",
    "O'rtacha — ba'zan chalg'itadi",
    "Kuchli — ishni qiyinlashtiradi",
    "Juda kuchli — odatdagi ishni bajara olmayman",
)

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
    SymptomCategory.PAIN to "Og'riq",
    SymptomCategory.BLEEDING to "Ajralma",
    SymptomCategory.MOOD to "Kayfiyat",
    SymptomCategory.SLEEP to "Uyqu",
    SymptomCategory.ENERGY to "Energiya",
    SymptomCategory.DIGESTION to "Hazm",
    SymptomCategory.SKIN to "Teri",
    SymptomCategory.OTHER to "Boshqa",
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
) {
    val c = Sadora.colors
    val scope = rememberCoroutineScope()

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        health.loadSymptoms(null)
        health.loadDay(state.today)
    }

    val catalogue = health.symptoms
    val today = health.day
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

    SadoraBottomSheet(visible = visible, title = "Simptom qo'shish", onDismiss = onDismiss) {
        Text(strings.dates.dayMonth(state.today), style = Sadora.type.body, color = c.muted)

        if (catalogue.isEmpty()) {
            Text(
                "Belgilar ro'yxati yuklanmoqda…",
                style = Sadora.type.body,
                color = c.muted2,
            )
        }

        categoryOrder.forEach { (category, label) ->
            val group = catalogue.filter { it.category == category }
            if (group.isEmpty()) return@forEach
            CardLabel(label)
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
            CardLabel("Og'riq darajasi")
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
            Text(severityWords[severity - 1], style = Sadora.type.body, color = c.muted)
        }

        SadoraTextField(note, { note = it }, placeholder = "Izoh qo'shish…", singleLine = false)

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SadoraButton(
                "Bekor",
                onClick = onDismiss,
                tone = ButtonTone.Secondary,
                modifier = Modifier.weight(1f),
            )
            SadoraButton(
                if (saving) "Saqlanmoqda…" else "Saqlash",
                onClick = {
                    saving = true
                    scope.launch {
                        health.saveDay(
                            date = state.today,
                            symptomKeys = selected.map { SymptomEntry(it, severity.toSeverity()) },
                            note = note.trim().takeIf { it.isNotEmpty() },
                        )
                        saving = false
                        onDismiss()
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
