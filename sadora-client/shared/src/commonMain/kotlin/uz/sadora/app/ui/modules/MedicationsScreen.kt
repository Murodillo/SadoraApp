package uz.sadora.app.ui.modules

import uz.sadora.app.ui.core.AddPrescriptionSheet
import uz.sadora.app.ui.core.PrescriptionCard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import uz.sadora.app.data.HealthController
import uz.sadora.app.data.courseTitle
import uz.sadora.app.design.Radius
import uz.sadora.app.design.IconSize
import uz.sadora.app.design.SadoraIcons
import uz.sadora.contract.Medication as Course
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
import uz.sadora.app.ui.components.SectionHeader
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SegmentedControl
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.app.resources.*

/**
 * "Dorilar" — schedule, adherence and stock.
 *
 * "Bugun" is today's doses, "Barchasi" every course she takes, and "Tarix" opens the
 * adherence page. The first two used to be one list under a segment that did nothing,
 * and no course could be opened again once saved. Tapping a dose or a course now opens
 * it for editing or deleting.
 *
 * Each dose offers exactly three responses. Critically, the app never tells the
 * user what to do about a missed dose — it points them at their prescription or a
 * pharmacist instead.
 */
@Composable
fun MedicationsScreen(
    state: AppState,
    health: HealthController,
    onClose: () -> Unit,
    onOpen: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.modules
    val c = Sadora.colors
    var tab by remember { mutableStateOf(TodayTab) }
    LaunchedEffect(Unit) {
        health.refreshMedications()
        health.loadPrescriptions()
    }
    var adding by remember { mutableStateOf<uz.sadora.contract.Prescription?>(null) }
    // Ones she has not added yet are offered on the Today tab too, so they are not missed.
    val prescriptions = health.prescriptions.orEmpty()
    val waiting = prescriptions.filter { it.addedAt == null && it.cancelledAt == null }
    val edit = { medicationId: String -> onOpen(Route.EditMedication(medicationId)) }
    // t.later hides the next-dose card for this visit; the dose itself stays due,
    // because snoozing is not the same as skipping.
    var snoozedId by remember { mutableStateOf<String?>(null) }

    Box(modifier) {
    Column(Modifier.fillMaxSize()) {
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
                    // History is a page of its own; the segment opens it and stays where
                    // it was, so coming back does not land on an empty third tab.
                    onSelect = { if (it == HistoryTab) onOpen(Route.MedicationHistory) else tab = it },
                )
            }

            if (tab == AllTab) {
                val courses = health.medications
                if (courses.isEmpty()) {
                    item {
                        EmptyState(
                            title = t.medsEmpty,
                            body = t.medsEmptyBody,
                            actionText = t.addMedication,
                            onAction = { onOpen(Route.AddMedication) },
                            art = Res.drawable.ic3d_meds,
                        )
                    }
                } else {
                    items(courses.size) { index ->
                        CourseRow(courses[index]) { edit(courses[index].id) }
                    }
                }
                if (prescriptions.isNotEmpty()) {
                    item(key = "rx-title") { SectionHeader(strings.prescriptions.listTitle) }
                    items(prescriptions.size, key = { "rx-" + prescriptions[it].id }) { index ->
                        val prescription = prescriptions[index]
                        PrescriptionCard(
                            prescription,
                            modifier = Modifier.fillMaxWidth(),
                            onAdd = { adding = prescription }.takeIf { prescription.addedAt == null && prescription.cancelledAt == null },
                        )
                    }
                }
                return@ScreenContent
            }

            items(waiting.size, key = { "rx-waiting-" + waiting[it].id }) { index ->
                PrescriptionCard(waiting[index], modifier = Modifier.fillMaxWidth(), onAdd = { adding = waiting[index] })
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
                        art = Res.drawable.ic3d_meds,
                    )
                }
            } else {
                items(state.medications.size) { index ->
                    val dose = state.medications[index]
                    // A dose's id is "<course>@<time>"; the course is what opens.
                    MedicationRow(dose) { edit(dose.id.substringBefore('@')) }
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

    AddPrescriptionSheet(
        prescription = adding,
        health = health,
        onAdded = { adding = null },
        onDismiss = { adding = null },
    )
    }
}

@Composable
private fun MedicationRow(medication: Medication, onClick: () -> Unit) {
    val t = strings.modules
    val c = Sadora.colors
    SadoraCard(padding = Spacing.sm, onClick = onClick) {
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

/** One course in "Barchasi": what it is, when it is due, and what is left in the pack. */
@Composable
private fun CourseRow(course: Course, onClick: () -> Unit) {
    val t = strings.modules
    val c = Sadora.colors
    SadoraCard(padding = Spacing.sm, onClick = onClick) {
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
                Text(course.emoji ?: "💊", style = Sadora.type.h3)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    courseTitle(course.name, course.dosage, course.unit),
                    style = Sadora.type.h3,
                    color = c.text,
                )
                course.prescribedBy?.let {
                    Text(strings.prescriptions.prescribedBy(it), style = Sadora.type.caption, color = c.textAccent)
                }
                Text(
                    listOf(
                        course.schedule.times.joinToString(", ") { it.toString().take(5) },
                        t.scheduleKind(course.schedule.kind),
                        t.doseCaption(course.note, course.foodRelation),
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = Sadora.type.body,
                    color = c.muted,
                )
                course.stockDaysLeft?.let {
                    Text(t.stockDays(it), style = Sadora.type.body, color = c.warning)
                }
            }
            Icon(SadoraIcons.ChevronRight, contentDescription = null, Modifier.size(IconSize.md), tint = c.muted2)
        }
    }
}

private const val TodayTab = 0
private const val AllTab = 1
private const val HistoryTab = 2
