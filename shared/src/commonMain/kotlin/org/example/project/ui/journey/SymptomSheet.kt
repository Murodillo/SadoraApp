package org.example.project.ui.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.ChipFlowRow
import org.example.project.ui.components.SadoraBottomSheet
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraTextField
import org.example.project.ui.components.SelectChip
import org.example.project.ui.components.noRippleClickable

/** Severity 1–5, each step explained in words rather than left as a bare number. */
private val severityWords = listOf(
    "Sezilmaydi",
    "Yengil — kunlik ishlarga to'sqinlik qilmaydi",
    "O'rtacha — ba'zan chalg'itadi",
    "Kuchli — ishni qiyinlashtiradi",
    "Juda kuchli — odatdagi ishni bajara olmayman",
)

/**
 * "Simptom qo'shish" — the logging bottom sheet.
 *
 * The severity scale is always accompanied by wording, so "3" never has to be
 * interpreted by the user on its own.
 */
@Composable
fun SymptomSheet(
    visible: Boolean,
    state: AppState,
    onDismiss: () -> Unit,
) {
    val c = Sadora.colors
    val pains = remember { mutableStateListOf<String>() }
    var severity by remember { mutableStateOf(2) }
    var discharge by remember { mutableStateOf<String?>(null) }
    val others = remember { mutableStateListOf<String>() }
    var note by remember { mutableStateOf("") }

    SadoraBottomSheet(visible = visible, title = "Simptom qo'shish", onDismiss = onDismiss) {
        Text("19 avg", style = Sadora.type.body, color = c.muted)

        CardLabel("Og'riq")
        ChipFlowRow {
            listOf("Bosh og'rig'i", "Qorin", "Bel", "Ko'krak").forEach { pain ->
                SelectChip(
                    label = pain,
                    selected = pain in pains,
                    onClick = { if (!pains.remove(pain)) pains.add(pain) },
                )
            }
        }

        CardLabel("Og'riq darajasi")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            (1..5).forEach { level ->
                val selected = level == severity
                Box(
                    Modifier
                        .weight(1f)
                        .clip(Radius.field)
                        .background(if (selected) c.primary else c.surface2)
                        .noRippleClickable { severity = level }
                        .padding(vertical = Spacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$level",
                        style = Sadora.type.h3.copy(fontWeight = FontWeight.SemiBold),
                        color = if (selected) c.onPrimary else c.text,
                    )
                }
            }
        }
        Text(severityWords[severity - 1], style = Sadora.type.body, color = c.muted)

        CardLabel("Ajralma")
        ChipFlowRow {
            listOf("Tuxum oqi kabi", "Quyuq", "Yo'q").forEach { option ->
                SelectChip(
                    label = option,
                    selected = discharge == option,
                    onClick = { discharge = if (discharge == option) null else option },
                )
            }
        }

        CardLabel("Boshqa")
        ChipFlowRow {
            listOf("Ko'ngil aynishi", "Shish", "Uyqusizlik").forEach { option ->
                SelectChip(
                    label = option,
                    selected = option in others,
                    onClick = { if (!others.remove(option)) others.add(option) },
                )
            }
            SelectChip("+ O'zim yozaman", selected = false, onClick = {})
        }

        SadoraTextField(note, { note = it }, placeholder = "Izoh qo'shish…")

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SadoraButton(
                "Bekor",
                onClick = onDismiss,
                tone = ButtonTone.Secondary,
                modifier = Modifier.weight(1f),
            )
            SadoraButton(
                "Saqlash",
                onClick = {
                    pains.forEach { state.toggleSymptom(it) }
                    discharge?.let { state.toggleSymptom("Ajralma") }
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
