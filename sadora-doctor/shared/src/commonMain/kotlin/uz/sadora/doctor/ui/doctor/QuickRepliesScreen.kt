package uz.sadora.doctor.ui.doctor

import uz.sadora.doctor.resources.Res
import uz.sadora.doctor.resources.ic3d_bulb
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.sadora.contract.Limits
import uz.sadora.contract.QuickReply
import uz.sadora.doctor.data.WorkController
import uz.sadora.doctor.data.readable
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.SadoraIcons
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.ui.components.ButtonTone
import uz.sadora.doctor.ui.components.CircleIconButton
import uz.sadora.doctor.ui.components.EmptyState
import uz.sadora.doctor.ui.components.ErrorStrip
import uz.sadora.doctor.ui.components.SadoraBottomSheet
import uz.sadora.doctor.ui.components.SadoraButton
import uz.sadora.doctor.ui.components.SadoraCard
import uz.sadora.doctor.ui.components.SadoraDialog
import uz.sadora.doctor.ui.components.SadoraTextField
import uz.sadora.doctor.ui.components.SadoraTopBar
import uz.sadora.doctor.ui.components.ScreenContent
import uz.sadora.doctor.ui.components.Skeleton
import uz.sadora.doctor.ui.components.acceptText

/** The most quick replies the server keeps for one doctor. */
const val QuickRepliesMax = 30

/** The longest title the server takes. */
private const val ReplyTitleMax = 60

/**
 * "Tayyor javoblar": her saved answers, to add, change and delete. A tap on one opens it
 * in a sheet; the "+" adds a new one. What she saves here is what the composer's sheet
 * offers in every consultation.
 */
@Composable
fun QuickRepliesScreen(
    work: WorkController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val w = strings.work
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val calls = work.replyCalls

    LaunchedEffect(Unit) {
        calls.clearError()
        work.loadQuickReplies()
    }
    val replies = work.quickReplies
    val full = replies.size >= QuickRepliesMax

    /** The reply open in the sheet: a new one while [editingNew], else [editing]. */
    var editing by remember { mutableStateOf<QuickReply?>(null) }
    var editingNew by remember { mutableStateOf(false) }
    val startNew = { editing = null; editingNew = true }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SadoraTopBar(
                w.quickReplies,
                onBack = onClose,
                trailing = if (!full) {
                    { CircleIconButton(SadoraIcons.Plus, contentDescription = w.addReply, onClick = startNew) }
                } else null,
            )
            ScreenContent(animateItems = true) {
                item(key = "intro") { Text(w.quickRepliesBody, style = Sadora.type.body, color = c.muted) }
                calls.error?.takeIf { editing == null && !editingNew }?.let { failure ->
                    item(key = "error") { ErrorStrip(failure.readable(), onRetry = { scope.launch { work.loadQuickReplies() } }) }
                }
                if (!work.quickRepliesLoaded && calls.error == null) {
                    items(3, key = { "skeleton-$it" }) { Skeleton(Modifier.fillMaxWidth().height(72.dp), shape = Radius.card) }
                }
                if (work.quickRepliesLoaded && replies.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(title = w.quickRepliesEmpty, body = w.quickRepliesEmptyBody, actionText = w.addReply, onAction = startNew, art = Res.drawable.ic3d_bulb)
                    }
                }
                items(replies.size, key = { replies[it].id }) { index ->
                    val reply = replies[index]
                    ReplyCard(reply, onClick = { editingNew = false; editing = reply })
                }
                if (full) {
                    item(key = "full") {
                        Text(w.repliesMax(QuickRepliesMax), style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
                    }
                }
            }
        }

        ReplyEditor(
            visible = editingNew || editing != null,
            reply = editing,
            work = work,
            onDone = {
                editing = null
                editingNew = false
            },
        )
    }
}

/** A saved reply as the lists show it: its title, and the start of its text. */
@Composable
internal fun ReplyCard(reply: QuickReply, onClick: () -> Unit) {
    val c = Sadora.colors
    SadoraCard(onClick = onClick, padding = Spacing.sm, verticalGap = Spacing.xxs) {
        Text(reply.title, style = Sadora.type.h3, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(reply.body, style = Sadora.type.body, color = c.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The sheet a reply is written in: title and text, saved together; an existing reply
 * can also be deleted, after a confirmation. A refusal keeps the sheet open with the
 * server's reason under the fields.
 */
@Composable
private fun ReplyEditor(visible: Boolean, reply: QuickReply?, work: WorkController, onDone: () -> Unit) {
    val w = strings.work
    val scope = rememberCoroutineScope()
    val calls = work.replyCalls
    // Refilled each time the sheet opens, for the reply it opened on.
    var title by remember(visible, reply?.id) { mutableStateOf(reply?.title.orEmpty()) }
    var body by remember(visible, reply?.id) { mutableStateOf(reply?.body.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (visible) calls.clearError() }

    SadoraBottomSheet(visible = visible, title = if (reply == null) w.addReply else w.editReply, onDismiss = onDone) {
        SadoraTextField(
            value = title,
            onValueChange = { title = acceptText(it, ReplyTitleMax) },
            label = w.replyTitle,
            placeholder = w.replyTitleHint,
        )
        SadoraTextField(
            value = body,
            onValueChange = { body = acceptText(it, Limits.MESSAGE_MAX) },
            label = w.replyBody,
            placeholder = w.replyBodyHint,
            singleLine = false,
        )
        calls.error?.let { ErrorStrip(it.readable()) }
        SadoraButton(
            if (saving) strings.common.saving else strings.doctors.save,
            enabled = title.isNotBlank() && body.isNotBlank() && !saving,
            onClick = {
                saving = true
                scope.launch {
                    val ok = work.saveQuickReply(reply?.id, title.trim(), body.trim())
                    saving = false
                    if (ok) onDone()
                }
            },
        )
        if (reply != null) {
            SadoraButton(w.deleteReply, onClick = { confirmDelete = true }, tone = ButtonTone.Destructive, enabled = !saving)
        }
    }

    SadoraDialog(
        visible = confirmDelete,
        title = w.deleteReplyTitle,
        body = w.deleteReplyBody,
        confirmText = w.deleteReply,
        onConfirm = {
            confirmDelete = false
            val id = reply?.id ?: return@SadoraDialog
            scope.launch { if (work.deleteQuickReply(id)) onDone() }
        },
        onDismiss = { confirmDelete = false },
    )
}
