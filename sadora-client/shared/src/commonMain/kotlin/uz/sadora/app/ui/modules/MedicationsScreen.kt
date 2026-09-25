package uz.sadora.app.ui.modules

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
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.model.MedStatus
import uz.sadora.app.model.Medication
import uz.sadora.app.nav.Route
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.DisclaimerNote
import uz.sadora.app.ui.components.EmptyState
import uz.sadora.app.ui.components.PillButton
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SegmentedControl
import uz.sadora.app.ui.components.noRippleClickable

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
    val t = strings.modules
    val c = Sadora.colors
    var tab by remember { mutableStateOf(0) }
    // t.later hides the next-dose card for this visit; the dose itself stays due,
    // because snoozing is not the same as skipping.
    var snoozedId by remember { mutableStateOf<String?>(null) }

    Column(modifier) {
        SadoraTopBar(
            t.medsTitle,
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
                    options = listOf(t.today, strings.journey.filterAll, t.history),
                    selectedIndex = tab,
                    onSelect = {
                        tab = it
                        if (it == 2) onOpen(Route.MedicationHistory)
                    },
                )
            }

            val next = state.medications.firstOrNull { it.status == MedStatus.Pending && it.id != snoozedId }
            if (next != null) {
                item {
                    SadoraCard {
                        CardLabel(
                            t.nextDose,
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
                                    t.oneTabletWith(t.doseCaption(next.note, next.foodRelation)),
                                    style = Sadora.type.body,
                                    color = c.muted,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            PillButton(
                                t.take,
                                { state.markMedicationTaken(next.id) },
                                tone = ButtonTone.Primary,
                            )
                            PillButton(t.later, { snoozedId = next.id })
                            PillButton(t.skip, { state.markMedicationSkipped(next.id) })
                        }
                    }
                }
            }

            if (state.medications.isEmpty()) {
                item {
                    EmptyState(
                        title = t.medsEmpty,
                        body = t.medsEmptyBody,
                        actionText = t.addMedication,
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
                    t.medsDisclaimer,
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
                            t.stockLeft(lowStock.name.substringBefore(' '), lowStock.stockDays ?: 0),
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
    val t = strings.modules
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
                    listOf(
                        medication.time,
                        t.scheduleKind(medication.schedule),
                        t.doseCaption(medication.note, medication.foodRelation),
                    ).joinToString(" · "),
                    style = Sadora.type.body,
                    color = c.muted,
                )
                if (medication.stockDays != null) {
                    Text(
                        t.stockDays(medication.stockDays),
                        style = Sadora.type.body,
                        color = c.warning,
                    )
                }
            }
            when (medication.status) {
                MedStatus.Taken -> Text("✓", style = Sadora.type.h2, color = c.successText)
                MedStatus.Pending -> SadoraBadge(t.pending, BadgeTone.Neutral)
                MedStatus.Skipped -> SadoraBadge(t.skipped, BadgeTone.Neutral)
            }
        }
    }
}
