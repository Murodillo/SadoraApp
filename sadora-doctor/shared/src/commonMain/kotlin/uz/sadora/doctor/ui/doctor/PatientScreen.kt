package uz.sadora.doctor.ui.doctor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.sadora.contract.ConsultationSession
import uz.sadora.doctor.data.DoctorController
import uz.sadora.doctor.data.WorkController
import uz.sadora.doctor.data.readable
import uz.sadora.doctor.design.IconSize
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.SadoraIcons
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.ui.components.ChipFlowRow
import uz.sadora.doctor.ui.components.ErrorStrip
import uz.sadora.doctor.ui.components.PillButton
import uz.sadora.doctor.ui.components.SadoraButton
import uz.sadora.doctor.ui.components.SadoraCard
import uz.sadora.doctor.ui.components.SadoraTextField
import uz.sadora.doctor.ui.components.SadoraTopBar
import uz.sadora.doctor.ui.components.ScreenContent
import uz.sadora.doctor.ui.components.SectionHeader
import uz.sadora.doctor.ui.components.Skeleton
import uz.sadora.doctor.ui.components.SystemBackHandler
import uz.sadora.doctor.ui.components.acceptText

/** The longest note the server keeps. */
private const val NoteMax = 4_000

/**
 * "Bemor": the patient of a consultation, from the doctor's side. Her private note on
 * the patient — hers alone, never shown to the patient or to staff — and every
 * consultation they have had: when, paid or free, how it ended, the advice she left,
 * the patient's rating, and the records attached in it.
 *
 * The note is saved with its button, and on the way out if it changed: Back waits for
 * the save, so a note is not lost to a tap. If that save fails she stays, with the
 * reason; a second Back leaves without it.
 */
@Composable
fun PatientScreen(
    conversationId: String,
    work: WorkController,
    doctors: DoctorController,
    onClose: () -> Unit,
    onOpenRecord: (messageId: String) -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val w = strings.work
    val t = strings.tabs
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val calls = work.patientCalls

    LaunchedEffect(conversationId) {
        calls.clearError()
        work.openPatient(conversationId)
    }
    val loaded = work.patientFor == conversationId
    val note = work.note.takeIf { loaded }
    val history = work.history.takeIf { loaded }
    val conversation = doctors.conversations.firstOrNull { it.id == conversationId }
        ?: doctors.openConversation?.conversation?.takeIf { it.id == conversationId }

    // What she is typing, kept across a record opened from the history and back.
    var draft by rememberSaveable(conversationId) { mutableStateOf<String?>(null) }
    LaunchedEffect(note != null) {
        if (draft == null && note != null) draft = note.body
    }
    val changed = note != null && draft != null && draft!!.trim() != note.body.trim()
    var saving by remember { mutableStateOf(false) }
    var leaveFailed by remember { mutableStateOf(false) }

    val save: (leaving: Boolean) -> Unit = { leaving ->
        val body = draft.orEmpty().trim()
        saving = true
        scope.launch {
            val ok = work.saveNote(conversationId, body)
            saving = false
            when {
                ok && leaving -> onClose()
                ok -> onToast(w.noteSaved)
                leaving -> leaveFailed = true
            }
        }
    }
    val leave: () -> Unit = {
        if (changed && !leaveFailed && !saving) save(true) else if (!saving) onClose()
    }
    SystemBackHandler(enabled = true, onBack = leave)

    Column(modifier) {
        SadoraTopBar(w.patientTitle, onBack = leave)
        ScreenContent(stagger = false) {
            calls.error?.let { failure ->
                item(key = "error") {
                    ErrorStrip(failure.readable(), onRetry = if (note == null) { { scope.launch { work.openPatient(conversationId) } } } else null)
                }
            }

            item(key = "who") {
                // The list's copy carries her age; the history's may not.
                val patient = conversation?.patient ?: history?.patient
                val name = patient?.name ?: conversation?.alias.orEmpty()
                SadoraCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        PatientAvatar(name, photoUrl = patient?.photoUrl, size = 56.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                            Text(name, style = Sadora.type.h2, color = c.text)
                            patient?.let { p ->
                                Text(
                                    listOfNotNull(p.age?.let(t::age), t.lifeStage(p.lifeStage)).joinToString(" · "),
                                    style = Sadora.type.body,
                                    color = c.muted,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "note") {
                SadoraCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(w.noteTitle, style = Sadora.type.h3, color = c.text, modifier = Modifier.weight(1f))
                        Icon(SadoraIcons.Shield, contentDescription = null, Modifier.size(IconSize.sm), tint = c.muted2)
                        Text(w.notePrivate, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
                    }
                    val text = draft
                    if (text == null) {
                        if (calls.error == null) Skeleton(Modifier.fillMaxWidth().height(96.dp), shape = Radius.cardSmall)
                    } else {
                        SadoraTextField(
                            value = text,
                            onValueChange = {
                                draft = acceptText(it, NoteMax)
                                leaveFailed = false
                            },
                            placeholder = w.noteHint,
                            singleLine = false,
                        )
                        SadoraButton(
                            if (saving) strings.common.saving else strings.doctors.save,
                            enabled = changed && !saving,
                            onClick = { save(false) },
                        )
                    }
                }
            }

            item(key = "history-title") { SectionHeader(w.historyTitle) }
            if (history == null) {
                if (calls.error == null) item(key = "history-loading") { Skeleton(Modifier.fillMaxWidth().height(120.dp), shape = Radius.card) }
            } else if (history.sessions.isEmpty()) {
                item(key = "history-empty") { Text(w.historyEmpty, style = Sadora.type.body, color = c.muted) }
            } else {
                // The server lists them oldest first; the latest is what she wants on top.
                val sessions = history.sessions.reversed()
                items(sessions.size, key = { sessions[it].id }) { index ->
                    SessionCard(sessions[index], onOpenRecord)
                }
            }
        }
    }
}

/** One window with this patient, as the history lists it. */
@Composable
private fun SessionCard(session: ConsultationSession, onOpenRecord: (String) -> Unit) {
    val w = strings.work
    val c = Sadora.colors
    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                session.openedAt?.let { dayMonthTime(it) }.orEmpty(),
                style = Sadora.type.h3,
                color = c.text,
                modifier = Modifier.weight(1f),
            )
            PaymentChip(session.payment)
        }
        Text(
            listOfNotNull(
                session.priceMinor.takeIf { it > 0 }?.let { somText(it) },
                w.closedReason(session.closedReason),
            ).joinToString(" · "),
            style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
            color = c.muted,
        )
        session.summary?.takeIf { it.isNotBlank() }?.let { summary ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(w.summaryLabel, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
                Text(summary, style = Sadora.type.body, color = c.text)
            }
        }
        session.rating?.let { rating ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(w.reviewLabel, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
                Stars(rating)
                session.review?.takeIf { it.isNotBlank() }?.let { Text(it, style = Sadora.type.body, color = c.text) }
            }
        }
        if (session.recordMessageIds.isNotEmpty()) {
            Text(w.recordsLabel, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
            ChipFlowRow {
                session.recordMessageIds.forEachIndexed { index, messageId ->
                    PillButton(w.recordNumber(index + 1), onClick = { onOpenRecord(messageId) })
                }
            }
        }
    }
}

/** "★★★★☆", read out as "4 / 5". */
@Composable
internal fun Stars(rating: Int, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    val filled = rating.coerceIn(0, 5)
    Text(
        "★".repeat(filled) + "☆".repeat(5 - filled),
        style = Sadora.type.h3,
        color = c.warning,
        modifier = modifier.semantics { contentDescription = "$filled / 5" },
    )
}
