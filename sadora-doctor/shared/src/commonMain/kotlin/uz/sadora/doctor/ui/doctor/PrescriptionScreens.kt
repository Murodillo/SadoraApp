package uz.sadora.doctor.ui.doctor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import uz.sadora.doctor.resources.Res
import uz.sadora.doctor.resources.ic3d_clock
import uz.sadora.doctor.resources.ic3d_meds
import uz.sadora.doctor.ui.components.ArtIcon
import uz.sadora.doctor.ui.components.ArtTile
import uz.sadora.doctor.ui.components.clayBeadSurface
import uz.sadora.doctor.ui.components.claySurface
import uz.sadora.doctor.ui.components.noRippleClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.contract.FoodRelation
import uz.sadora.contract.Limits
import uz.sadora.contract.Prescription
import uz.sadora.contract.PrescriptionForm
import uz.sadora.doctor.data.DoctorController
import uz.sadora.doctor.data.ItemDraft
import uz.sadora.doctor.data.ItemProblem
import uz.sadora.doctor.data.PrescriptionDraft
import uz.sadora.doctor.data.acceptDayDigits
import uz.sadora.doctor.data.clockOf
import uz.sadora.doctor.data.readable
import uz.sadora.doctor.design.IconSize
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.SadoraIcons
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.ui.components.ButtonTone
import uz.sadora.doctor.ui.components.ChipFlowRow
import uz.sadora.doctor.ui.components.CircleIconButton
import uz.sadora.doctor.ui.components.ErrorStrip
import uz.sadora.doctor.ui.components.PillButton
import uz.sadora.doctor.ui.components.SadoraBottomSheet
import uz.sadora.doctor.ui.components.SadoraButton
import uz.sadora.doctor.ui.components.SadoraCard
import uz.sadora.doctor.ui.components.SadoraTextField
import uz.sadora.doctor.ui.components.SadoraTopBar
import uz.sadora.doctor.ui.components.ScreenContent
import uz.sadora.doctor.ui.components.SelectChip
import uz.sadora.doctor.ui.components.acceptText

/**
 * Writing a prescription: one card per medicine, a note under them, and the send button
 * pinned at the bottom. The form is hers until it is sent — a failed send keeps it.
 *
 * "Oldingi retseptdan nusxa" fills the form from the last one she wrote this patient,
 * which is the usual way a course is repeated or adjusted.
 */
@Composable
fun PrescriptionWriterScreen(
    conversationId: String,
    doctors: DoctorController,
    onClose: () -> Unit,
    onSent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = strings.prescriptions
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val calls = doctors.prescriptionCalls

    LaunchedEffect(conversationId) {
        calls.clearError()
        doctors.loadPrescriptions(conversationId)
    }
    val previous = doctors.prescriptions[conversationId]?.firstOrNull { !it.cancelled }

    var draft by remember { mutableStateOf(PrescriptionDraft()) }
    var tried by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    // Which medicines are open. A new one opens and folds the rest away, so a long
    // prescription reads as a list of names with the one being written under the hand.
    var open by remember { mutableStateOf(setOf(0)) }

    Column(modifier.fillMaxSize()) {
        SadoraTopBar(p.writerTitle, onBack = onClose)
        Box(Modifier.weight(1f)) {
            ScreenContent(
                stagger = false,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Spacing.screen,
                    end = Spacing.screen,
                    top = Spacing.xs,
                    bottom = Spacing.lg,
                ),
            ) {
                if (previous != null) {
                    item(key = "copy") {
                        PillButton(
                            p.copyPrevious,
                            onClick = {
                                draft = PrescriptionDraft.from(previous)
                                open = emptySet()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                draft.items.forEachIndexed { index, item ->
                    item(key = "item-${item.key}") {
                        ItemCard(
                            number = index + 1,
                            item = item,
                            expanded = item.key in open,
                            showProblems = tried,
                            canRemove = draft.items.size > 1,
                            onToggle = { open = if (item.key in open) open - item.key else open + item.key },
                            onChange = { change -> draft = draft.update(index, change) },
                            onRemove = {
                                draft = draft.removeItem(index)
                                open = open - item.key
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                if (draft.canAddItem) {
                    item(key = "add") {
                        SadoraButton(
                            p.addMedicine,
                            icon = SadoraIcons.Plus,
                            tone = ButtonTone.Outline,
                            onClick = {
                                draft = draft.addItem()
                                open = setOf(draft.lastKey)
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                item(key = "note") {
                    SadoraTextField(
                        value = draft.note,
                        onValueChange = { draft = draft.copy(note = acceptText(it, Limits.PRESCRIPTION_NOTE_MAX)) },
                        label = p.note,
                        placeholder = p.notePlaceholder,
                        singleLine = false,
                    )
                }
                item(key = "disclaimer") {
                    Text(p.disclaimer, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
                }
            }
        }
        Column(
            Modifier
                .background(c.surface)
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            calls.error?.let { ErrorStrip(it.readable()) }
            if (tried && !draft.ready) Text(p.missing, style = Sadora.type.body, color = c.danger)
            SadoraButton(
                if (sending) p.sending else p.send,
                enabled = !sending,
                onClick = {
                    tried = true
                    // What is missing is shown where it is: the unfinished ones open.
                    if (!draft.ready) {
                        open = open + draft.items.filter { it.problems().isNotEmpty() }.map { it.key }
                        return@SadoraButton
                    }
                    sending = true
                    calls.clearError()
                    scope.launch {
                        val ok = doctors.sendPrescription(conversationId, draft.toRequest(p::defaultUnit))
                        sending = false
                        if (ok) onSent()
                    }
                },
            )
        }
    }
}

/**
 * One medicine on the form, folding to its header: the number and the colour pill, the
 * name, a line of what is filled in, and whether it is ready. Tapping the header opens
 * or folds it; the fields are the same whichever way it was reached.
 */
@Composable
private fun ItemCard(
    number: Int,
    item: ItemDraft,
    expanded: Boolean,
    showProblems: Boolean,
    canRemove: Boolean,
    onToggle: () -> Unit,
    onChange: ((ItemDraft) -> ItemDraft) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = strings.prescriptions
    val c = Sadora.colors
    val problems = if (showProblems) item.problems() else emptySet()
    val required = p.required
    val ready = item.problems().isEmpty()
    val turn by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")

    SadoraCard(modifier, verticalGap = Spacing.sm) {
        Row(
            Modifier
                .fillMaxWidth()
                .noRippleClickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = if (expanded) p.collapse else p.expand },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box {
                ArtTile(Res.drawable.ic3d_meds, size = 48.dp)
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(20.dp)
                        .clayBeadSurface(c, c.primary, elevation = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$number", style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.onPrimary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.name.trim().ifEmpty { p.medicine(number) },
                    style = Sadora.type.h3,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (ready) p.summary(item.toItem(p.defaultUnit(item.form))) else if (item.name.isBlank()) p.unnamed else p.notFilled,
                    style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                    color = if (ready) c.muted else if (showProblems) c.danger else c.muted2,
                    maxLines = if (expanded) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (ready) {
                Box(Modifier.size(24.dp).clayBeadSurface(c, c.success, elevation = 2.dp), contentAlignment = Alignment.Center) {
                    Icon(SadoraIcons.Check, contentDescription = p.filled, Modifier.size(14.dp), tint = c.onPrimary)
                }
            }
            Icon(
                SadoraIcons.ChevronRight,
                contentDescription = null,
                Modifier.size(IconSize.md).rotate(turn),
                tint = c.muted,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(260)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(220)) + fadeOut(tween(160)),
        ) {
            ItemFields(item, problems, required, canRemove, onChange, onRemove)
        }
    }
}

/** The fields of an open medicine. */
@Composable
private fun ItemFields(
    item: ItemDraft,
    problems: Set<ItemProblem>,
    required: String,
    canRemove: Boolean,
    onChange: ((ItemDraft) -> ItemDraft) -> Unit,
    onRemove: () -> Unit,
) {
    val p = strings.prescriptions
    val c = Sadora.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SadoraTextField(
            value = item.name,
            onValueChange = { text -> onChange { it.copy(name = acceptText(text, Limits.MEDICATION_NAME_MAX)) } },
            label = p.name,
            placeholder = p.namePlaceholder,
            error = required.takeIf { ItemProblem.Name in problems },
        )

        Label(p.formLabel)
        ChipFlowRow {
            PrescriptionForm.entries.forEach { form ->
                SelectChip(p.form(form), selected = item.form == form, onClick = { onChange { it.copy(form = form) } })
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SadoraTextField(
                value = item.dose,
                onValueChange = { text -> onChange { it.copy(dose = acceptText(text, Limits.PRESCRIPTION_DOSE_MAX)) } },
                label = p.dose,
                placeholder = "500",
                error = required.takeIf { ItemProblem.Dose in problems },
                modifier = Modifier.weight(1f),
            )
            SadoraTextField(
                value = item.unitOr(p.defaultUnit(item.form)),
                onValueChange = { text -> onChange { it.copy(unit = acceptText(text, Limits.PRESCRIPTION_UNIT_MAX)) } },
                label = p.unit,
                modifier = Modifier.weight(1f),
            )
        }

        Label(p.when_, Res.drawable.ic3d_clock)
        ChipFlowRow {
            (1..4).forEach { count ->
                SelectChip(p.timesPerDay(count), selected = item.timesPerDay == count, onClick = { onChange { it.withTimesPerDay(count) } })
            }
        }
        item.minutes.forEachIndexed { index, minute ->
            TimeRow(
                minute = minute,
                onEarlier = { onChange { it.stepTime(index, -1) } },
                onLater = { onChange { it.stepTime(index, 1) } },
            )
        }
        ChipFlowRow {
            SelectChip(p.everyDay, selected = item.everyDays == null, onClick = { onChange { it.copy(everyDays = null) } })
            listOf(2, 3, 7).forEach { every ->
                SelectChip(p.everyDays(every), selected = item.everyDays == every, onClick = { onChange { it.copy(everyDays = every) } })
            }
        }

        Label(p.food)
        ChipFlowRow {
            listOf(FoodRelation.BEFORE, FoodRelation.WITH, FoodRelation.AFTER, FoodRelation.ANY).forEach { relation ->
                SelectChip(p.food(relation), selected = item.food == relation, onClick = { onChange { it.copy(food = relation) } })
            }
        }
        if (ItemProblem.Food in problems) Text(required, style = Sadora.type.caption, color = c.danger)

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.Bottom) {
            SadoraTextField(
                value = item.startDay.toString(),
                onValueChange = { text ->
                    val day = acceptDayDigits(text).toIntOrNull()?.coerceIn(1, Limits.PRESCRIPTION_START_DAY_MAX) ?: 1
                    onChange { it.copy(startDay = day) }
                },
                label = p.startDay,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            SadoraTextField(
                value = if (item.ongoing) "" else item.days,
                onValueChange = { text -> onChange { it.copy(days = acceptDayDigits(text), ongoing = false) } },
                label = p.days,
                placeholder = if (item.ongoing) p.ongoing else "5",
                keyboardType = KeyboardType.Number,
                error = required.takeIf { ItemProblem.Days in problems },
                modifier = Modifier.weight(1f),
            )
        }
        SelectChip(p.ongoing, selected = item.ongoing, onClick = { onChange { it.copy(ongoing = !it.ongoing) } })

        SadoraTextField(
            value = item.note,
            onValueChange = { text -> onChange { it.copy(note = acceptText(text, Limits.PRESCRIPTION_ITEM_NOTE_MAX)) } },
            label = p.itemNote,
            singleLine = false,
        )
        if (canRemove) {
            SadoraButton(p.remove, onClick = onRemove, tone = ButtonTone.Ghost)
        }
    }
}

@Composable
private fun Label(text: String, art: org.jetbrains.compose.resources.DrawableResource? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        art?.let { ArtIcon(it, 18.dp) }
        Text(text.uppercase(), style = Sadora.type.caption, color = Sadora.colors.muted)
    }
}

/** "‹ 09:00 ›": half an hour a tap. */
@Composable
private fun TimeRow(minute: Int, onEarlier: () -> Unit, onLater: () -> Unit) {
    val p = strings.prescriptions
    val c = Sadora.colors
    Row(
        Modifier.fillMaxWidth().claySurface(c, Radius.cardSmall, c.surface2, elevation = 2.dp).padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        ArtIcon(Res.drawable.ic3d_clock, 22.dp)
        Box(Modifier.weight(1f))
        CircleIconButton(SadoraIcons.ChevronLeft, contentDescription = strings.work.stepEarlier(p.when_, clockOf(minute)), onClick = onEarlier)
        Text(clockOf(minute), style = Sadora.type.h3, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 64.dp))
        CircleIconButton(SadoraIcons.ChevronRight, contentDescription = strings.work.stepLater(p.when_, clockOf(minute)), onClick = onLater)
    }
}

/**
 * A prescription as a card: in the thread, where [onCancel] offers to cancel it, and on
 * the patient's page. Always on the surface colour, even as her own line, because it is
 * a document to read rather than a bubble to skim.
 */
@Composable
fun PrescriptionCard(
    prescription: Prescription,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
) {
    val p = strings.prescriptions
    val c = Sadora.colors
    val cancelled = prescription.cancelled
    Column(
        modifier
            .widthIn(max = 320.dp)
            .clip(Radius.card)
            .background(c.surface)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ArtTile(Res.drawable.ic3d_meds, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(p.title, style = Sadora.type.h3, color = c.text)
                Text(
                    "${prescription.doctor.fullName} · ${day(prescription)}",
                    style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                    color = c.muted,
                )
            }
        }
        prescription.items.forEachIndexed { index, item ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${index + 1}. ${item.name}",
                    style = Sadora.type.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = c.text,
                    textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                )
                Text(p.summary(item), style = Sadora.type.body, color = c.muted)
                item.note?.let { Text(it, style = Sadora.type.body, color = c.muted2) }
            }
        }
        prescription.note?.let {
            Text(it, style = Sadora.type.body, color = c.text, modifier = Modifier.fillMaxWidth().clip(Radius.cardSmall).background(c.surface2).padding(Spacing.xs))
        }
        when {
            cancelled -> {
                Text(p.cancelled, style = Sadora.type.h3, color = c.danger)
                prescription.cancelReason?.let { Text(p.cancelReason(it), style = Sadora.type.body, color = c.muted) }
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (prescription.addedAt != null) {
                    Icon(SadoraIcons.Check, contentDescription = null, Modifier.size(IconSize.sm), tint = c.success)
                }
                Text(
                    if (prescription.addedAt != null) p.added else p.notAdded,
                    style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                    color = if (prescription.addedAt != null) c.success else c.muted2,
                    modifier = Modifier.weight(1f),
                )
                onCancel?.let { PillButton(p.cancel, onClick = it) }
            }
        }
        Text(p.disclaimer, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
    }
}

/** Asks why, then cancels; the patient is shown the reason. */
@Composable
fun CancelPrescriptionSheet(
    target: Prescription?,
    conversationId: String,
    doctors: DoctorController,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = strings.prescriptions
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    var reason by remember(target?.id) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    SadoraBottomSheet(visible = target != null, title = p.cancelTitle, onDismiss = onDismiss) {
        Text(p.cancelBody, style = Sadora.type.body, color = c.muted)
        SadoraTextField(
            value = reason,
            onValueChange = { reason = acceptText(it, Limits.PRESCRIPTION_CANCEL_REASON_MAX) },
            placeholder = p.reasonHint,
            singleLine = false,
        )
        doctors.prescriptionCalls.error?.let { ErrorStrip(it.readable()) }
        SadoraButton(
            p.confirmCancel,
            tone = ButtonTone.Destructive,
            enabled = reason.isNotBlank() && !busy && target != null,
            onClick = {
                val id = target?.id ?: return@SadoraButton
                busy = true
                doctors.prescriptionCalls.clearError()
                scope.launch {
                    if (doctors.cancelPrescription(conversationId, id, reason)) onDone()
                    busy = false
                }
            },
        )
    }
}

@Composable
private fun day(prescription: Prescription): String {
    val local = prescription.createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${strings.dates.dayMonth(local.date)}, ${clockOf(local.hour * 60 + local.minute)}"
}
