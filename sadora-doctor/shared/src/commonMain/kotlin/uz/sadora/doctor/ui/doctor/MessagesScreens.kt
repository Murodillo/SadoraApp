package uz.sadora.doctor.ui.doctor

import uz.sadora.doctor.resources.Res
import uz.sadora.doctor.resources.ic3d_camera
import uz.sadora.doctor.resources.ic3d_meds
import uz.sadora.doctor.resources.ic3d_record
import uz.sadora.doctor.resources.ic3d_message
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.decodeToImageBitmap
import uz.sadora.contract.Consultation
import uz.sadora.contract.ConsultationPayment
import uz.sadora.contract.Conversation
import uz.sadora.contract.DirectMessage
import uz.sadora.contract.Limits
import uz.sadora.contract.MessageKind
import uz.sadora.contract.Prescription
import uz.sadora.contract.QuickReply
import uz.sadora.contract.ReportReason
import uz.sadora.doctor.data.ApiFailure
import uz.sadora.doctor.data.CapturedPhotoData
import uz.sadora.doctor.data.DoctorController
import uz.sadora.doctor.data.WorkController
import uz.sadora.doctor.data.insertReply
import uz.sadora.doctor.data.readable
import uz.sadora.doctor.design.IconSize
import uz.sadora.doctor.design.MinTouchTarget
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.SadoraIcons
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.ui.components.LoadMoreRow
import uz.sadora.doctor.ui.components.ButtonTone
import uz.sadora.doctor.ui.components.ChipFlowRow
import uz.sadora.doctor.ui.components.CircleIconButton
import uz.sadora.doctor.ui.components.EmptyState
import uz.sadora.doctor.ui.components.ErrorStrip
import uz.sadora.doctor.ui.components.PillButton
import uz.sadora.doctor.ui.components.SadoraBottomSheet
import uz.sadora.doctor.ui.components.SadoraButton
import uz.sadora.doctor.ui.components.SadoraCard
import uz.sadora.doctor.ui.components.SadoraTextField
import uz.sadora.doctor.ui.components.SadoraTopBar
import uz.sadora.doctor.ui.components.ScreenContent
import uz.sadora.doctor.ui.components.Skeleton
import uz.sadora.doctor.ui.components.acceptText
import uz.sadora.doctor.ui.components.noRippleClickable
import uz.sadora.doctor.ui.components.rememberPhotoCapture

// ---------------------------------------------------------------- the list

/**
 * The second tab: the consultations she holds, the most recent first. Each row is the
 * patient by name and age, the last line — or what it was, a photo or a record — the
 * window, and what is unread. Polled while on screen, so a new patient arrives by itself.
 */
@Composable
fun MessagesScreen(
    doctors: DoctorController,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.tabs
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        doctors.loadConversations(silent = false)
        while (true) {
            delay(ListPollMillis)
            doctors.loadConversations(silent = true)
        }
    }
    val chats = doctors.conversations

    Column(modifier) {
        SadoraTopBar(t.messages)
        ScreenContent(animateItems = true) {
            doctors.chatCalls.error?.let { failure ->
                item(key = "error") {
                    ErrorStrip(failure.readable(), onRetry = { scope.launch { doctors.loadConversations(silent = false) } })
                }
            }
            if (!doctors.conversationsLoaded && doctors.chatCalls.error == null) {
                items(3, key = { "skeleton-$it" }) { Skeleton(Modifier.fillMaxWidth().height(76.dp), shape = Radius.card) }
            }
            if (doctors.conversationsLoaded && chats.isEmpty()) {
                item(key = "empty") {
                    EmptyState(title = t.messagesEmpty, body = t.messagesEmptyBody, actionText = null, onAction = {}, art = Res.drawable.ic3d_message)
                }
            }
            items(chats.size, key = { chats[it].id }) { index ->
                val chat = chats[index]
                ConversationRow(chat, onClick = { onOpen(chat.id) })
            }
            if (doctors.hasMoreConversations) {
                item(key = "more") { LoadMoreRow(chats.size, onLoadMore = { doctors.loadMoreConversations() }) }
            }
        }
    }
}

@Composable
private fun ConversationRow(chat: Conversation, onClick: () -> Unit) {
    val c = Sadora.colors
    val t = strings.tabs
    val unread = chat.unread > 0
    val patient = chat.patient
    SadoraCard(onClick = onClick, padding = Spacing.sm) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PatientAvatar(chat.alias, photoUrl = patient?.photoUrl, size = 46.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        chat.alias,
                        style = Sadora.type.h3,
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        strings.dates.ago(chat.lastMessageAt, Clock.System.now()),
                        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                        color = if (unread) c.textAccent else c.muted2,
                    )
                }
                patient?.let {
                    Text(
                        listOfNotNull(it.age?.let(t::age), t.lifeStage(it.lifeStage)).joinToString(" · "),
                        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                        color = c.muted,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (chat.lastMessageRead) ReadTicks(read = true)
                    previewArt(chat.lastMessageKind)?.let {
                        Image(painterResource(it), contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        previewOf(chat),
                        style = Sadora.type.body,
                        color = if (unread) c.text else c.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (unread) CountPill(t.unread(chat.unread))
                }
                chat.consultation?.let { window ->
                    ChipFlowRow(horizontalGap = Spacing.xxs, verticalGap = Spacing.xxs) {
                        WindowChip(window)
                        ConsultationChips(window)
                    }
                }
            }
        }
    }
}

/** "Rasm: caption", "Tibbiy karta", or the line itself; the kind's mark is [previewArt]. */
@Composable
private fun previewOf(chat: Conversation): String {
    val t = strings.tabs
    val text = chat.lastMessage.orEmpty()
    return when (chat.lastMessageKind) {
        MessageKind.TEXT -> text
        MessageKind.IMAGE -> t.photo + text.takeIf { it.isNotEmpty() }?.let { ": $it" }.orEmpty()
        MessageKind.RECORD -> t.record
        MessageKind.PRESCRIPTION -> strings.prescriptions.title
    }
}

/** The clay icon in front of a preview that is not plain text — the app's icons, not emoji. */
private fun previewArt(kind: MessageKind): DrawableResource? = when (kind) {
    MessageKind.TEXT -> null
    MessageKind.IMAGE -> Res.drawable.ic3d_camera
    MessageKind.RECORD -> Res.drawable.ic3d_record
    MessageKind.PRESCRIPTION -> Res.drawable.ic3d_meds
}

@Composable
private fun CountPill(text: String) {
    val c = Sadora.colors
    Box(
        Modifier.clip(Radius.chip).background(c.primary).padding(horizontal = Spacing.xs, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.onPrimary)
    }
}

/** Open with the time left, or closed. */
@Composable
private fun WindowChip(window: Consultation) {
    val c = Sadora.colors
    val t = strings.tabs
    // Ticks on its own, so "5 soat qoldi" does not stand still between polls.
    val now by produceState(Clock.System.now()) {
        while (true) {
            delay(ChipTickMillis)
            value = Clock.System.now()
        }
    }
    val left = window.expiresAt - now
    val open = window.open && left.isPositive()
    val tint = if (open) c.successText else c.muted2
    Text(
        if (open) t.consultationOpen(left.inWholeHours.toInt(), (left.inWholeMinutes % 60).toInt()) else t.consultationClosed,
        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
        color = tint,
        modifier = Modifier
            .clip(Radius.chip)
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = Spacing.xs, vertical = 2.dp),
    )
}

/**
 * What else she needs to know about a window at a glance: that it was paid for — or is
 * owed back — and, while it is open, that the patient is still waiting for her first word.
 */
@Composable
private fun ConsultationChips(window: Consultation) {
    val c = Sadora.colors
    val w = strings.work
    when (window.payment) {
        ConsultationPayment.PAID -> TintChip(w.payment(ConsultationPayment.PAID), c.successText)
        ConsultationPayment.REFUND_DUE -> PaymentChip(ConsultationPayment.REFUND_DUE)
        else -> Unit
    }
    if (window.open && !window.answered) TintChip(w.awaitingReply, c.warning)
}

// ---------------------------------------------------------------- one consultation

/**
 * One consultation: the patient by name and age at the top, the window under it, the
 * messages oldest first — hers left, the doctor's right, with ticks — photos and an
 * attached record inline, "yozmoqda…" when she is typing, and the composer.
 *
 * The thread is read again every few seconds while it is on screen; each read marks it
 * read, which is what turns the patient's ticks double. When the window closes the
 * composer gives way to a line saying so; the doctor may also close it herself.
 */
@Composable
fun ConversationScreen(
    id: String,
    doctors: DoctorController,
    work: WorkController,
    onClose: () -> Unit,
    onOpenRecord: (messageId: String) -> Unit,
    onOpenPatient: () -> Unit,
    onManageReplies: () -> Unit,
    onWritePrescription: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.tabs
    val w = strings.work
    val rx = strings.prescriptions
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val calls = doctors.chatCalls

    LaunchedEffect(id) {
        calls.clearError()
        doctors.openConversation(id)
        while (true) {
            delay(ThreadPollMillis)
            doctors.openConversation(id, poll = true)
        }
    }
    val thread = doctors.openConversation?.takeIf { it.conversation.id == id }
    val conversation = thread?.conversation ?: doctors.conversations.firstOrNull { it.id == id }
    val messages = thread?.messages.orEmpty()
    val window = conversation?.consultation
    val canWrite = thread != null && conversation?.blocked != true && window?.open != false

    var menuOpen by remember { mutableStateOf(false) }
    /** The closing sheet: ending an open window, or only writing the advice a closed one lacks. */
    var summaryMode by remember { mutableStateOf<SummaryMode?>(null) }
    var repliesOpen by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    // Kept across a look at the patient's page and back, and across a quick reply dropped in.
    var draft by rememberSaveable(id) { mutableStateOf("") }
    val needsSummary = window != null && !window.open && window.summary.isNullOrBlank()
    // A failure left from an earlier send is not the closing sheet's to show.
    LaunchedEffect(summaryMode) { if (summaryMode != null) calls.clearError() }
    var reportOpen by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<DirectMessage?>(null) }
    var sendingPhoto by remember { mutableStateOf(false) }
    var attachOpen by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf<Prescription?>(null) }

    val picker = rememberPhotoCapture { photo ->
        sendingPhoto = true
        scope.launch {
            doctors.sendImage(id, CapturedPhotoData(photo.base64, photo.mimeType))
            sendingPhoto = false
        }
    }

    // The newest message is at the bottom; the list starts there and follows new ones.
    // Keyed on the newest line, not the count: older lines read in above must not throw
    // her back to the bottom.
    val list = rememberLazyListState()
    val hasOlder = thread?.hasMore == true
    // Older lines are asked for only once the thread has come to rest at its newest line;
    // before that its top is on screen for a frame.
    var settled by remember(id) { mutableStateOf(false) }
    LaunchedEffect(messages.lastOrNull()?.id, thread?.otherTyping) {
        val last = messages.size + (if (thread?.otherTyping == true) 1 else 0)
        if (last > 0) {
            list.animateScrollToItem(last + if (hasOlder) 1 else 0)
            settled = true
        }
    }
    // Near the top, the page above. Prepended lines keep the one she is reading in place,
    // so the index jumps past the threshold and this does not ask again until she scrolls.
    LaunchedEffect(list, id) {
        snapshotFlow { (settled && list.firstVisibleItemIndex <= OlderThreshold) to (doctors.openConversation?.hasMore == true) }
            .collect { (nearTop, more) -> if (nearTop && more) doctors.loadOlderMessages(id) }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SadoraTopBar(
                conversation?.alias.orEmpty(),
                leading = conversation?.let { chat ->
                    { PatientAvatar(chat.alias, photoUrl = chat.patient?.photoUrl, size = 40.dp) }
                },
                subtitle = conversation?.patient?.let { p ->
                    listOfNotNull(p.age?.let(t::age), t.lifeStage(p.lifeStage)).joinToString(" · ")
                },
                onBack = onClose,
                trailing = if (thread != null) {
                    {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            if (window != null) CircleIconButton(SadoraIcons.Info, contentDescription = w.patientInfo, onClick = onOpenPatient)
                            CircleIconButton(SadoraIcons.More, contentDescription = t.report, onClick = { menuOpen = true })
                        }
                    }
                } else null,
            )
            window?.let {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    WindowChip(it)
                    ConsultationChips(it)
                }
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = list,
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (hasOlder) {
                    item(key = "older") { LoadMoreRow(Unit, onLoadMore = {}) }
                }
                item(key = "notice") {
                    Text(
                        t.namesNotice,
                        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                        color = c.muted2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(Radius.cardSmall)
                            .background(c.surface2)
                            .padding(Spacing.sm),
                    )
                }
                if (thread == null && calls.error == null) {
                    items(4) { index ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (index % 2 == 0) Arrangement.Start else Arrangement.End) {
                            Skeleton(Modifier.fillMaxWidth(0.6f).height(44.dp), shape = Radius.card)
                        }
                    }
                }
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        conversationId = id,
                        doctors = doctors,
                        onViewImage = { viewing = message },
                        onOpenRecord = { onOpenRecord(message.id) },
                        onCancelPrescription = { cancelling = it },
                    )
                }
                if (thread?.otherTyping == true) {
                    item(key = "typing") { TypingRow() }
                }
            }

            calls.error?.let { failure ->
                val gone = failure is ApiFailure.NotFound
                ErrorStrip(
                    if (gone) strings.errors.notFound else failure.readable(),
                    onRetry = if (gone) null else { { scope.launch { doctors.openConversation(id) } } },
                    modifier = Modifier.padding(horizontal = Spacing.screen),
                )
            }

            Column(
                Modifier
                    .background(c.surface)
                    .padding(horizontal = Spacing.screen, vertical = Spacing.xs)
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                when {
                    thread == null -> Unit
                    conversation?.blocked == true ->
                        Text(t.conversationClosed, style = Sadora.type.body, color = c.muted, modifier = Modifier.padding(vertical = Spacing.sm))
                    window?.open == false -> ClosedFooter(
                        summary = window.summary,
                        onWriteSummary = if (needsSummary) { { summaryMode = SummaryMode.Write } } else null,
                    )
                    else -> Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        // "+": a photo, and in a consultation a prescription. Quick replies keep
                        // their own button beside it — they are used all day.
                        if (picker.available || window != null) {
                            Box(
                                Modifier
                                    .padding(bottom = 2.dp)
                                    .size(MinTouchTarget)
                                    .clip(Radius.chip)
                                    .background(c.surface2)
                                    .noRippleClickable(enabled = canWrite && !sendingPhoto, role = Role.Button, onClick = { attachOpen = true }),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(SadoraIcons.Plus, contentDescription = rx.attach, Modifier.size(IconSize.md), tint = c.text)
                            }
                        }
                        Box(
                            Modifier
                                .padding(bottom = 2.dp)
                                .size(MinTouchTarget)
                                .clip(Radius.chip)
                                .background(c.surface2)
                                .noRippleClickable(enabled = canWrite, role = Role.Button, onClick = { repliesOpen = true }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(SadoraIcons.Bolt, contentDescription = w.quickReplies, Modifier.size(IconSize.md), tint = c.text)
                        }
                        AnswerInput(
                            draft = draft,
                            onDraftChange = { draft = it },
                            onSend = { body ->
                                calls.clearError()
                                doctors.sendMessage(id, body)
                            },
                            onType = { scope.launch { doctors.typing(id, Clock.System.now().toEpochMilliseconds()) } },
                            placeholder = t.messageHint,
                            maxLength = Limits.MESSAGE_MAX,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // ---- the menu, its two actions, and the photo viewer, over the page

        SadoraBottomSheet(visible = menuOpen, title = conversation?.alias.orEmpty(), onDismiss = { menuOpen = false }) {
            if (window?.open == true) {
                PillButton(t.closeConsultation, onClick = { menuOpen = false; summaryMode = SummaryMode.Close }, modifier = Modifier.fillMaxWidth())
            }
            if (needsSummary) {
                PillButton(w.writeSummary, onClick = { menuOpen = false; summaryMode = SummaryMode.Write }, modifier = Modifier.fillMaxWidth())
            }
            PillButton(t.report, onClick = { menuOpen = false; reportOpen = true }, modifier = Modifier.fillMaxWidth())
        }

        SadoraBottomSheet(visible = attachOpen, title = rx.attachTitle, onDismiss = { attachOpen = false }) {
            if (picker.available) {
                AttachRow(SadoraIcons.Camera, rx.photo, onClick = { attachOpen = false; picker.pickFromGallery() })
            }
            if (window != null) {
                AttachRow(null, rx.prescription, art = Res.drawable.ic3d_meds, onClick = { attachOpen = false; onWritePrescription() })
            }
        }

        CancelPrescriptionSheet(
            target = cancelling,
            conversationId = id,
            doctors = doctors,
            onDone = {
                cancelling = null
                onToast(rx.cancelledToast)
            },
            onDismiss = { cancelling = null },
        )

        SadoraBottomSheet(visible = reportOpen, title = t.reportTitle, onDismiss = { reportOpen = false }) {
            ReportReason.entries.forEach { reason ->
                PillButton(
                    t.reportReason(reason),
                    onClick = {
                        reportOpen = false
                        scope.launch { if (doctors.reportConversation(id, reason)) onToast(t.reportSent) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        SummarySheet(
            mode = summaryMode,
            // Its own flag: the thread's calls are busy with a poll every few seconds.
            busy = closing,
            error = calls.error?.takeIf { summaryMode != null },
            onDismiss = { summaryMode = null },
            onConfirm = { mode, summary ->
                closing = true
                calls.clearError()
                scope.launch {
                    if (doctors.closeConsultation(id, summary)) {
                        summaryMode = null
                        if (mode == SummaryMode.Write || summary.isNotBlank()) onToast(w.summarySent)
                    }
                    closing = false
                }
            },
        )

        QuickReplySheet(
            visible = repliesOpen,
            work = work,
            onPick = { reply ->
                draft = insertReply(draft, reply.body, Limits.MESSAGE_MAX)
                repliesOpen = false
            },
            onManage = {
                repliesOpen = false
                onManageReplies()
            },
            onDismiss = { repliesOpen = false },
        )

        viewing?.let { message ->
            PhotoViewer(message, id, doctors, onDismiss = { viewing = null })
        }
    }
}

/** Whether the closing sheet ends the window, or only adds the advice to one already over. */
private enum class SummaryMode { Close, Write }

/**
 * Closing a consultation, with the advice the patient keeps — optional, and the same
 * sheet writes it for a window that ended without one. The field sits under the warning,
 * so what "Yakunlash" does is read before it is pressed.
 */
@Composable
private fun SummarySheet(
    mode: SummaryMode?,
    busy: Boolean,
    error: ApiFailure?,
    onDismiss: () -> Unit,
    onConfirm: (SummaryMode, String) -> Unit,
) {
    val t = strings.tabs
    val w = strings.work
    val c = Sadora.colors
    var summary by remember(mode) { mutableStateOf("") }
    SadoraBottomSheet(
        visible = mode != null,
        title = if (mode == SummaryMode.Write) w.writeSummary else t.closeConfirmTitle,
        onDismiss = onDismiss,
    ) {
        Text(if (mode == SummaryMode.Write) w.writeSummaryBody else t.closeConfirmBody, style = Sadora.type.body, color = c.muted)
        SadoraTextField(
            value = summary,
            onValueChange = { summary = acceptText(it, SummaryMax) },
            label = w.closeSummaryLabel,
            placeholder = w.closeSummaryHint,
            singleLine = false,
        )
        error?.let { ErrorStrip(it.readable()) }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SadoraButton(strings.common.cancel, onDismiss, tone = ButtonTone.Secondary, modifier = Modifier.weight(1f))
            SadoraButton(
                if (mode == SummaryMode.Write) strings.common.send else t.closeConfirm,
                onClick = { mode?.let { onConfirm(it, summary.trim()) } },
                tone = if (mode == SummaryMode.Write) ButtonTone.Primary else ButtonTone.Destructive,
                enabled = !busy && (mode != SummaryMode.Write || summary.isNotBlank()),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Under a closed window: that it is closed, her advice if she left some, or the way to write it. */
@Composable
private fun ClosedFooter(summary: String?, onWriteSummary: (() -> Unit)?) {
    val t = strings.tabs
    val w = strings.work
    val c = Sadora.colors
    Column(Modifier.padding(vertical = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(t.consultationClosedBody, style = Sadora.type.body, color = c.muted)
        summary?.takeIf { it.isNotBlank() }?.let {
            Column(
                Modifier.fillMaxWidth().clip(Radius.cardSmall).background(c.surface2).padding(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(w.yourSummary, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
                Text(it, style = Sadora.type.body, color = c.text)
            }
        }
        onWriteSummary?.let { PillButton(w.writeSummary, onClick = it, tone = ButtonTone.Primary) }
    }
}

/**
 * Her quick replies over the composer. A tap drops the reply's text into the field — it
 * is not sent: she reads it over, and may add to it, first.
 */
@Composable
private fun QuickReplySheet(
    visible: Boolean,
    work: WorkController,
    onPick: (QuickReply) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val w = strings.work
    val c = Sadora.colors
    LaunchedEffect(visible) { if (visible) work.loadQuickReplies() }
    SadoraBottomSheet(visible = visible, title = w.quickReplies, onDismiss = onDismiss) {
        val replies = work.quickReplies
        when {
            !work.quickRepliesLoaded && work.replyCalls.error == null ->
                repeat(2) { Skeleton(Modifier.fillMaxWidth().height(64.dp), shape = Radius.card) }
            !work.quickRepliesLoaded -> work.replyCalls.error?.let { ErrorStrip(it.readable()) }
            replies.isEmpty() -> {
                Text(w.quickRepliesEmpty, style = Sadora.type.h3, color = c.text)
                Text(w.quickRepliesEmptyBody, style = Sadora.type.body, color = c.muted)
            }
            else -> replies.forEach { reply -> ReplyCard(reply, onClick = { onPick(reply) }) }
        }
        PillButton(if (replies.isEmpty()) w.addReply else w.manageReplies, onClick = onManage, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun MessageBubble(
    message: DirectMessage,
    conversationId: String,
    doctors: DoctorController,
    onViewImage: () -> Unit,
    onOpenRecord: () -> Unit,
    onCancelPrescription: (Prescription) -> Unit,
) {
    val c = Sadora.colors
    val t = strings.tabs
    val mine = message.isMine
    message.prescription?.takeIf { message.kind == MessageKind.PRESCRIPTION }?.let { prescription ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            PrescriptionCard(
                prescription,
                onCancel = if (mine && !prescription.cancelled) { { onCancelPrescription(prescription) } } else null,
            )
            Text(
                clock(message.createdAt),
                style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                color = c.muted2,
                modifier = Modifier.padding(top = 2.dp, end = Spacing.xs),
            )
        }
        return
    }
    val fg = if (mine) c.onPrimary else c.text
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 290.dp)
                .clip(Radius.card)
                .background(if (mine) c.primary else c.surface)
                .padding(if (message.kind == MessageKind.IMAGE) Spacing.xxs else Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            when (message.kind) {
                MessageKind.IMAGE -> {
                    MessagePhoto(message, conversationId, doctors, Modifier.width(260.dp).clip(Radius.cardSmall).noRippleClickable(role = Role.Image, onClick = onViewImage))
                    if (message.body.isNotEmpty()) {
                        Text(message.body, style = Sadora.type.body, color = fg, modifier = Modifier.padding(horizontal = Spacing.xs))
                    }
                }
                MessageKind.RECORD -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Icon(SadoraIcons.Document, contentDescription = null, Modifier.size(IconSize.lg), tint = fg)
                        Text(t.recordCardTitle, style = Sadora.type.h3, color = fg)
                    }
                    Text(t.recordCardBody, style = Sadora.type.body, color = fg.copy(alpha = 0.85f))
                    PillButton(t.viewRecord, onClick = onOpenRecord)
                }
                // A prescription whose structured copy is missing reads as its text.
                MessageKind.TEXT, MessageKind.PRESCRIPTION -> Text(message.body, style = Sadora.type.body, color = fg)
            }
            Row(
                Modifier.align(Alignment.End).padding(horizontal = if (message.kind == MessageKind.IMAGE) Spacing.xs else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    clock(message.createdAt),
                    style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                    color = if (mine) c.onPrimary.copy(alpha = 0.75f) else c.muted2,
                )
                if (mine) ReadTicks(read = message.read, onPrimary = true)
            }
        }
    }
}

/** One choice in the "+" sheet: an icon and what it attaches. */
@Composable
private fun AttachRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    art: org.jetbrains.compose.resources.DrawableResource? = null,
    onClick: () -> Unit,
) {
    val c = Sadora.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(c.surface2)
            .noRippleClickable(role = Role.Button, onClick = onClick)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(Modifier.size(40.dp).clip(Radius.chip).background(c.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            when {
                art != null -> uz.sadora.doctor.ui.components.ArtIcon(art, 30.dp)
                icon != null -> Icon(icon, contentDescription = null, Modifier.size(IconSize.md), tint = c.primary)
            }
        }
        Text(label, style = Sadora.type.h3, color = c.text)
    }
}

/** ✓ sent, ✓✓ read — the second one appears when the patient has opened the thread since. */
@Composable
private fun ReadTicks(read: Boolean, onPrimary: Boolean = false) {
    val c = Sadora.colors
    val label = if (read) strings.tabs.read else strings.tabs.sent
    val tint = when {
        onPrimary -> c.onPrimary.copy(alpha = if (read) 1f else 0.7f)
        read -> c.primary
        else -> c.muted2
    }
    Text(
        if (read) "✓✓" else "✓",
        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
        color = tint,
        modifier = Modifier.semantics { contentDescription = label },
    )
}

/** A photo, fetched once and drawn at its own proportions; a shimmer until it arrives. */
@Composable
private fun MessagePhoto(message: DirectMessage, conversationId: String, doctors: DoctorController, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    val ratio = message.image?.let { it.width.toFloat() / it.height.coerceAtLeast(1) }?.coerceIn(0.5f, 2f) ?: 1f
    val bitmap by produceState<ImageBitmap?>(null, message.id) {
        value = doctors.image(conversationId, message.id)?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
    }
    val picture = bitmap
    if (picture != null) {
        Image(picture, contentDescription = strings.tabs.photo, contentScale = ContentScale.Crop, modifier = modifier.aspectRatio(ratio))
    } else {
        Box(modifier.aspectRatio(ratio).background(c.surface2), contentAlignment = Alignment.Center) {
            Skeleton(Modifier.fillMaxSize())
        }
    }
}

/** The photo on its own, over everything, at its full width. */
@Composable
private fun PhotoViewer(message: DirectMessage, conversationId: String, doctors: DoctorController, onDismiss: () -> Unit) {
    SystemBackHandlerFor(onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.92f))
            .noRippleClickable(onClick = onDismiss)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        MessagePhoto(message, conversationId, doctors, Modifier.fillMaxWidth())
    }
}

@Composable
private fun SystemBackHandlerFor(onBack: () -> Unit) =
    uz.sadora.doctor.ui.components.SystemBackHandler(enabled = true, onBack = onBack)

/** "yozmoqda…" in a bubble on her side, three dots breathing in turn. */
@Composable
private fun TypingRow() {
    val c = Sadora.colors
    var phase by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(380)
            phase = (phase + 1) % 3
        }
    }
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(200)) + expandVertically(tween(200)),
        exit = fadeOut(tween(160)) + shrinkVertically(tween(160)),
    ) {
        Row(
            Modifier.clip(Radius.card).background(c.surface).padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) { dot ->
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(Radius.chip)
                            .background(c.primary.copy(alpha = if (dot == phase) 1f else 0.35f)),
                    )
                }
            }
            Text(strings.tabs.typing, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted)
        }
    }
}

private fun clock(at: Instant): String {
    val time = at.toLocalDateTime(TimeZone.currentSystemDefault()).time
    return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}

/** The longest advice the server keeps. */
private const val SummaryMax = 2_000

/** How often the time left on a consultation is worked out again. */
private const val ChipTickMillis = 30_000L

/** How often the list is read again while the tab is open. */
private const val ListPollMillis = 12_000L

/** How often an open thread is read again: new lines, ticks and "yozmoqda…". */
private const val ThreadPollMillis = 3_000L

/** How close to the top of a thread, in items, the page above is asked for. */
private const val OlderThreshold = 4
