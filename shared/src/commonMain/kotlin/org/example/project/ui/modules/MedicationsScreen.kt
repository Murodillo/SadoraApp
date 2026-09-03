package org.example.project.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.MedStatus
import org.example.project.model.Medication
import org.example.project.nav.Route
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.DisclaimerNote
import org.example.project.ui.components.EmptyState
import org.example.project.ui.components.PillButton
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.SegmentedControl
import org.example.project.ui.components.noRippleClickable

/**
 * "Dorilar" — schedule, adherence and stock.
 *
 * Each dose offers exactly three responses. Critically, the app never tells the
 * user what to do about a missed dose — it points them at their prescription or a
 * pharmacist instead.
 */
@Composable
fun MedicationsScreen(
    state: AppState,
    onClose: () -> Unit,
    onOpen: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    var tab by remember { mutableStateOf(0) }

    Column(modifier) {
        SadoraTopBar(
            "Dorilar",
            onBack = onClose,
            trailing = {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(Radius.chip)
                        .background(c.surface2)
                        .noRippleClickable { onOpen(Route.AddMedication) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("＋", style = Sadora.type.h2, color = c.text)
                }
            },
        )

        ScreenContent {
            item {
                SegmentedControl(
                    options = listOf("Bugun", "Barchasi", "Tarix"),
                    selectedIndex = tab,
                    onSelect = {
                        tab = it
                        if (it == 2) onOpen(Route.MedicationHistory)
                    },
                )
            }

            val next = state.medications.firstOrNull { it.status == MedStatus.Pending }
            if (next != null) {
                item {
                    SadoraCard {
                        CardLabel(
                            "Keyingi qabul",
                            trailing = {
                                Text(next.time, style = Sadora.type.h3, color = c.textAccent)
                            },
                        )
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
                                Text("💊", style = Sadora.type.h2)
                            }
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(next.name, style = Sadora.type.h3, color = c.text)
                                Text(
                                    "1 tabletka · ${next.note}",
                                    style = Sadora.type.body,
                                    color = c.muted,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            PillButton(
                                "Qabul qildim",
                                { state.markMedicationTaken(next.id) },
                                tone = ButtonTone.Primary,
                            )
                            PillButton("Keyinroq", {})
                            PillButton("O'tkazish", {})
                        }
                    }
                }
            }

            if (state.medications.isEmpty()) {
                item {
                    EmptyState(
                        title = "Hali dori qo'shilmagan",
                        body = "Dori qo'shsangiz, qabul vaqtlari va zaxirasi shu yerda ko'rinadi.",
                        actionText = "Dori qo'shish",
                        onAction = { onOpen(Route.AddMedication) },
                        glyph = "💊",
                    )
                }
            } else {
                items(state.medications.size) { index ->
                    MedicationRow(state.medications[index])
                }
            }

            item {
                DisclaimerNote(
                    "O'tkazib yuborilgan qabul bo'yicha SADORA yo'riqnoma bermaydi. " +
                        "Dori qabul qilish tartibi yoki shifokor/farmatsevt tavsiyasiga amal qiling.",
                )
            }

            val lowStock = state.medications.firstOrNull { (it.stockDays ?: 99) <= 14 }
            if (lowStock != null) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(Radius.cardSmall)
                            .background(c.warning.copy(alpha = 0.14f))
                            .padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text("📦", style = Sadora.type.h3)
                        Text(
                            "${lowStock.name.substringBefore(' ')} zaxirasi " +
                                "${lowStock.stockDays} kunga qoldi",
                            style = Sadora.type.body,
                            color = c.warning,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MedicationRow(medication: Medication) {
    val c = Sadora.colors
    SadoraCard(padding = Spacing.sm) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(c.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Text(medication.emoji, style = Sadora.type.h3)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(medication.name, style = Sadora.type.h3, color = c.text)
                Text(
                    "${medication.time} · ${medication.schedule} · ${medication.note}",
                    style = Sadora.type.body,
                    color = c.muted,
                )
                if (medication.stockDays != null) {
                    Text(
                        "Zaxira ${medication.stockDays} kun",
                        style = Sadora.type.body,
                        color = c.warning,
                    )
                }
            }
            when (medication.status) {
                MedStatus.Taken -> Text("✓", style = Sadora.type.h2, color = c.success)
                MedStatus.Pending -> SadoraBadge("Kutilmoqda", BadgeTone.Neutral)
                MedStatus.Skipped -> SadoraBadge("O'tkazildi", BadgeTone.Neutral)
            }
        }
    }
}
