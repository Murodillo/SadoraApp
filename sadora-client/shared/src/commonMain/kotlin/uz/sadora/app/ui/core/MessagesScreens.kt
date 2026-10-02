package uz.sadora.app.ui.core

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import uz.sadora.app.data.MessagesController
import uz.sadora.app.data.readable
import uz.sadora.app.design.IconSize
import uz.sadora.app.design.MinTouchTarget
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.ConsultationWindow
import uz.sadora.app.model.Conversation
import uz.sadora.app.model.DirectMessage
import uz.sadora.app.model.MessageKind
import uz.sadora.app.ui.components.ResizeForKeyboard
import uz.sadora.app.ui.components.BadgeRow
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CapturedPhoto
import uz.sadora.app.ui.components.CircleIconButton
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.LoadMoreRow
import uz.sadora.app.ui.components.RoundIconButton
import uz.sadora.app.ui.components.SadoraBottomSheet
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraDialog
import uz.sadora.app.ui.components.SadoraTextField
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.Skeleton
import uz.sadora.app.ui.components.SystemBackHandler
import uz.sadora.app.ui.components.acceptText
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.app.ui.components.pressable
import uz.sadora.app.ui.components.rememberPhotoCapture
import uz.sadora.contract.ConsultationPayment
import uz.sadora.contract.DoctorSummary
import uz.sadora.contract.Limits

/**
 * Her private threads, most recently written first, with what is unread in each.
 *
 * A consultation sits in the same list as an alias thread — it is the same kind of
 * conversation to her — but reads differently: the doctor's avatar and check mark, her
 * specialty, and whether the window is still open.
 */
@Composable
fun ConversationsScreen(
    messages: MessagesController,
    onOpen: (Conversation) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.community
    val c = Sadora.colors
    val errors = strings.errors

    // Read on arrival and then now and again while the list is up, quietly, so a
    // doctor's reply moves her thread to the top without a pull.
    LaunchedEffect(messages) {
        messages.load()
        while (true) {
            delay(ListRefreshMillis)
            messages.load()
        }
    }
    val retryScope = rememberCoroutineScope()

    Column(modifier) {
        SadoraTopBar(t.messagesTitle, onBack = onClose, subtitle = t.messagesSubtitle)
        ScreenContent {
            messages.error?.let { failure ->
                // "Retry" retries. It used to clear the banner and ask for nothing.
                item { ErrorStrip(failure.readable(errors), onRetry = { retryScope.launch { messages.load() } }) }
            }
            if (!messages.loaded && messages.busy) {
                items(3) {
                    SadoraCard(padding = Spacing.sm) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                            Skeleton(Modifier.defaultMinSize(44.dp, 44.dp), shape = Radius.chip)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Skeleton(Modifier.height(14.dp).fillMaxWidth(0.4f))
                                Skeleton(Modifier.height(12.dp).fillMaxWidth(0.7f))
                            }
                        }
                    }
                }
            } else if (messages.conversations.isEmpty() && !messages.loaded && messages.error != null) {
                // The strip above says why; "no messages" would be a second, false, answer.
            } else if (messages.conversations.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = Spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    ) {
                        Icon(SadoraIcons.Message, contentDescription = null, Modifier.defaultMinSize(28.dp, 28.dp), tint = c.secondary)
                        Text(t.noMessages, style = Sadora.type.h3, color = c.text, textAlign = TextAlign.Center)
                        Text(t.noMessagesBody, style = Sadora.type.body, color = c.muted, textAlign = TextAlign.Center)
                    }
                }
            } else {
                items(messages.conversations.size, key = { messages.conversations[it].id }) { index ->
                    ConversationRow(messages.conversations[index], onClick = { onOpen(messages.conversations[index]) })
                }
                if (messages.hasMoreConversations) {
                    item(key = "more") {
                        LoadMoreRow(messages.conversations.size, onLoadMore = { messages.loadMoreConversations() })
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(thread: Conversation, onClick: () -> Unit) {
    val c = Sadora.colors
    val t = strings.community
    val d = strings.doctors
    val unread = thread.unread > 0
    val doctor = thread.doctor
    SadoraCard(padding = Spacing.sm, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (doctor != null) DoctorAvatar(thread.alias, size = 44.dp) else AliasAvatar(thread.alias, thread.tint, size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(
                        thread.alias,
                        style = Sadora.type.h3,
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (doctor != null) VerifiedMark(size = 14.dp) else BadgeRow(thread.badges, max = 1, compact = true)
                }
                if (doctor != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(
                            d.specialty(doctor.specialty),
                            style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                            color = c.textAccent,
                            maxLines = 1,
                        )
                        thread.consultation?.let { ConsultationChip(it.open) }
                    }
                }
                LastLine(thread, unread)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    strings.dates.ago(thread.lastMessageAt, Clock.System.now()),
                    style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                    color = c.muted2,
                )
                if (unread) {
                    Box(
                        Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp).clip(Radius.chip).background(c.primary).padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${thread.unread}",
                            style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified, fontWeight = FontWeight.Bold),
                            color = c.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The preview under the name: the last line, or what it was when it was not words —
 * "Rasm", "Tibbiy karta" — with the double tick when it was hers and has been read.
 */
@Composable
private fun LastLine(thread: Conversation, unread: Boolean) {
    val c = Sadora.colors
    val t = strings.community
    val colour = if (unread) c.text else c.muted
    val style = Sadora.type.body.copy(fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal)
    if (thread.blocked) {
        Text(t.conversationBlocked, style = style, color = colour, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    val caption = thread.lastMessage.orEmpty()
    val text = when (thread.lastMessageKind) {
        MessageKind.Text -> caption
        MessageKind.Image -> if (caption.isBlank()) t.photo else "${t.photo} · $caption"
        MessageKind.Record -> t.record
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (thread.lastMessageRead) ReadTicks(read = true, tint = c.textAccent)
        when (thread.lastMessageKind) {
            MessageKind.Image -> Icon(SadoraIcons.Camera, contentDescription = null, Modifier.size(14.dp), tint = colour)
            MessageKind.Record -> Icon(SadoraIcons.Document, contentDescription = null, Modifier.size(14.dp), tint = colour)
            MessageKind.Text -> Unit
        }
        Text(text, style = style, color = colour, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Ochiq" / "Yopiq" beside a consultation in the list. */
@Composable
private fun ConsultationChip(open: Boolean) {
    val c = Sadora.colors
    val d = strings.doctors
    val tone = if (open) c.successText else c.muted
    Text(
        if (open) d.chipOpen else d.chipClosed,
        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified, fontWeight = FontWeight.SemiBold),
        color = tone,
        modifier = Modifier
            .clip(Radius.chip)
            .background((if (open) c.success else c.muted).copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/**
 * One thread: her lines on the right, the other side's on the left, the field pinned
 * above the keyboard.
 *
 * Polled every few seconds while open — there is no push channel into a running app,
 * and a reply that arrives while she is looking at the thread should simply appear,
 * with the ticks on her own lines and the "yozmoqda…" row riding the same read. The
 * poll is silent; a failed one is the next one's problem.
 *
 * In a consultation the header is the doctor — name, check mark, specialty — and a
 * banner under it says how long the window has left, or that it has shut and how to
 * open it again. The composer then waits behind that banner rather than taking a line
 * the server would refuse.
 */
@Composable
fun ConversationScreen(
    conversationId: String?,
    alias: String,
    messages: MessagesController,
    /** The language code her record is read in, "UZ" / "RU" / "EN". */
    language: String,
    onOpenProfile: (String) -> Unit,
    onOpenDoctor: (String) -> Unit,
    onOpenMenu: () -> Unit,
    /** A new window with a doctor who charges: the shell's pay sheet, by her doctor id. */
    onPay: (String) -> Unit,
    /** The thread was read: the unread count in the chat header and on the tab bar follows. */
    onRead: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.community
    val d = strings.doctors
    val c = Sadora.colors
    val errors = strings.errors
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()

    var showAttach by remember { mutableStateOf(false) }
    var pendingPhoto by remember { mutableStateOf<CapturedPhoto?>(null) }
    var confirmRecord by remember { mutableStateOf(false) }
    var recordFor by remember { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<DirectMessage?>(null) }
    var reopening by remember { mutableStateOf(false) }
    /**
     * The window she rated from this screen, by its session, so it can say thank you once
     * the stars go. By session and not by thread: a new paid window in the same thread is
     * rated again, and a thank-you keyed to the thread hid its stars.
     */
    var ratedHere by remember { mutableStateOf<String?>(null) }
    // Its own flag: the controller's busy also blinks with every silent poll of the thread.
    var sendingRating by remember { mutableStateOf(false) }
    val picker = rememberPhotoCapture { pendingPhoto = it }
    ResizeForKeyboard()

    LaunchedEffect(conversationId, alias) {
        messages.open(conversationId, alias)
        if (conversationId != null) onRead()
    }
    DisposableEffect(messages) {
        onDispose {
            messages.close()
            onRead()
        }
    }

    // The id appears with the first send, and the poll starts with it.
    val liveId = messages.current?.id?.takeIf { it.isNotEmpty() }
    LaunchedEffect(liveId) {
        if (liveId == null) return@LaunchedEffect
        while (true) {
            delay(PollMillis)
            messages.refreshThread(liveId)
        }
    }
    val otherTyping = messages.otherTyping
    // Older lines may be read in only once the thread has come to rest at its newest line;
    // before that the top of the list is on screen for a frame and would ask for them.
    var settled by remember(conversationId) { mutableStateOf(false) }
    // Keyed on the newest line, not the count: a page of older lines read in above must
    // not throw her back to the bottom.
    LaunchedEffect(messages.messages.lastOrNull()?.id, otherTyping) {
        if (messages.messages.isEmpty()) return@LaunchedEffect
        // Counted rather than read from the layout, which has not caught up with the
        // new line yet when this runs: the spinner, the note, the lines, "yozmoqda", the end spacer.
        val older = if (messages.hasOlder) 1 else 0
        val note = if (messages.current?.consultation != null) 1 else 0
        val typing = if (otherTyping) 1 else 0
        list.animateScrollToItem(older + note + messages.messages.size + typing)
        settled = true
    }
    // Near the top, the page above. Prepended lines keep the one she is reading in place,
    // so the index jumps past the threshold and this does not ask again until she scrolls.
    LaunchedEffect(list) {
        snapshotFlow { Triple(settled && list.firstVisibleItemIndex <= OlderThreshold, messages.hasOlder, messages.current?.id) }
            .collect { (nearTop, more, _) -> if (nearTop && more) messages.loadOlder() }
    }

    // The window's clock, for the banner. The server's own verdict arrives with each poll.
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(ClockTickMillis)
            now = Clock.System.now()
        }
    }

    val thread = messages.current
    val doctor = thread?.doctor
    val window = thread?.consultation
    val name = thread?.alias ?: alias
    val canAttachRecord = window?.open == true && doctor != null

    Box(modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            ConversationHeader(
                thread = thread,
                name = name,
                typing = otherTyping,
                onBack = onClose,
                onOpenWho = {
                    if (doctor != null) onOpenDoctor(doctor.id) else onOpenProfile(name)
                },
                onOpenMenu = onOpenMenu.takeIf { liveId != null },
            )

            if (window != null) {
                ConsultationBanner(
                    window = window,
                    now = now,
                    reopening = reopening,
                    onReopen = {
                        reopening = true
                        scope.launch {
                            // A doctor who charges answers the start with 402: that is her
                            // pay sheet, not an error under the banner.
                            if (!messages.reopenConsultation() && messages.takePaymentRequired()) doctor?.let { onPay(it.id) }
                            reopening = false
                        }
                    },
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xxs),
                )
                window.summary?.let { summary ->
                    DoctorSummaryCard(
                        summary = summary,
                        modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xxs),
                    )
                }
            }

            messages.error?.let { failure ->
                ErrorStrip(failure.readable(errors), onRetry = messages::clearError, modifier = Modifier.padding(horizontal = Spacing.screen))
            }

            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (messages.hasOlder) {
                    item(key = "older") { LoadMoreRow(Unit, onLoadMore = {}) }
                }
                if (window != null) {
                    item(key = "note") {
                        Text(
                            d.threadNote,
                            style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                            color = c.muted2,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xxs),
                        )
                    }
                }
                if (messages.messages.isEmpty() && liveId == null) {
                    item(key = "new") {
                        Text(
                            t.newConversation,
                            style = Sadora.type.body,
                            color = c.muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
                        )
                    }
                }
                items(messages.messages, key = { it.id }) { message ->
                    Bubble(
                        message = message,
                        conversationId = liveId,
                        messages = messages,
                        onOpenImage = { viewing = message },
                        onOpenRecord = { recordFor = message.id },
                    )
                }
                if (otherTyping) {
                    item(key = "typing") { TypingRow(name) }
                }
                val sessionKey = window?.sessionId
                if (sessionKey != null && (window?.canRate == true || ratedHere == sessionKey)) {
                    item(key = "rate") {
                        if (window?.canRate == true && ratedHere != sessionKey) {
                            RateConsultationCard(
                                sending = sendingRating,
                                onSend = { stars, review ->
                                    sendingRating = true
                                    scope.launch {
                                        if (messages.rate(stars, review)) ratedHere = sessionKey
                                        sendingRating = false
                                    }
                                },
                            )
                        } else {
                            RatedThanks()
                        }
                    }
                }
                item(key = "end") { Spacer(Modifier.height(Spacing.xs)) }
            }

            val composerModifier = Modifier
                .background(c.surface)
                .padding(horizontal = Spacing.screen, vertical = Spacing.xs)
                .navigationBarsPadding()
                .imePadding()
            when {
                thread?.blocked == true -> ClosedComposer(t.conversationBlocked)
                window != null && !window.open -> ClosedComposer(d.composerClosed)
                else -> MessageComposer(
                    onSend = { body -> messages.send(body) },
                    onTyping = { scope.launch { messages.typing() } },
                    // A first line to an alias opens the thread and is words; attachments come after.
                    onAttach = { showAttach = true }.takeIf { liveId != null && (picker.available || canAttachRecord) },
                    sendingAttachment = messages.sendingAttachment,
                    modifier = composerModifier,
                )
            }
        }

        SadoraBottomSheet(visible = showAttach, title = t.attach, onDismiss = { showAttach = false }) {
            if (picker.available) {
                AttachOption(SadoraIcons.Camera, t.photo, t.photoNote) {
                    showAttach = false
                    picker.pickFromGallery()
                }
            }
            if (canAttachRecord) {
                AttachOption(SadoraIcons.Document, t.attachRecord, t.attachRecordNote) {
                    showAttach = false
                    confirmRecord = true
                }
            }
        }

        SadoraBottomSheet(visible = pendingPhoto != null, title = t.sendPhoto, onDismiss = { pendingPhoto = null }) {
            pendingPhoto?.let { photo ->
                PhotoSendContent(
                    photo = photo,
                    sending = messages.sendingAttachment,
                    onSend = { caption ->
                        scope.launch {
                            if (messages.sendImage(photo.base64, photo.mimeType, caption)) pendingPhoto = null
                        }
                    },
                )
            }
        }

        SadoraBottomSheet(
            visible = recordFor != null,
            title = t.recordTitle,
            onDismiss = {
                recordFor = null
                messages.clearRecord()
            },
        ) {
            val id = recordFor
            var failed by remember(id) { mutableStateOf(false) }
            LaunchedEffect(id) { if (id != null) failed = !messages.loadRecord(id, language) }
            messages.error?.takeIf { failed }?.let { ErrorStrip(it.readable(errors)) }
            if (!failed) RecordContent(messages.record)
        }

        SadoraDialog(
            visible = confirmRecord,
            title = t.attachRecordConfirmTitle,
            body = t.attachRecordConfirmBody,
            confirmText = t.attachRecordConfirm,
            destructive = false,
            onConfirm = {
                confirmRecord = false
                scope.launch { messages.attachRecord() }
            },
            onDismiss = { confirmRecord = false },
        )

        viewing?.let { message ->
            val id = liveId
            if (id != null) ImageViewer(message, id, messages, onDismiss = { viewing = null })
        }
    }
}

/**
 * Who she is writing to. The whole name is a button: to the doctor's page in a
 * consultation, to the alias page otherwise. While the other side types, the second
 * line says so, the way every messenger she already uses does.
 */
@Composable
private fun ConversationHeader(
    thread: Conversation?,
    name: String,
    typing: Boolean,
    onBack: () -> Unit,
    onOpenWho: () -> Unit,
    onOpenMenu: (() -> Unit)?,
) {
    val c = Sadora.colors
    val t = strings.community
    val d = strings.doctors
    val doctor = thread?.doctor
    val subtitle = when {
        typing -> t.typingShort
        doctor != null -> d.specialty(doctor.specialty)
        else -> thread?.badges?.firstOrNull()?.let(t::badge)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        CircleIconButton(SadoraIcons.ChevronLeft, contentDescription = strings.common.back, onClick = onBack)
        Row(
            Modifier
                .weight(1f)
                .clip(Radius.cardSmall)
                .noRippleClickable(role = Role.Button, onClick = onOpenWho),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (doctor != null) DoctorAvatar(name, size = 36.dp) else AliasAvatar(name, thread?.tint ?: 0, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        name,
                        style = Sadora.type.h3,
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (doctor != null) VerifiedMark(size = 16.dp)
                }
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                        color = if (typing || doctor != null) c.textAccent else c.muted,
                        maxLines = 1,
                    )
                }
            }
        }
        if (onOpenMenu != null) {
            RoundIconButton(
                SadoraIcons.More,
                onClick = onOpenMenu,
                filled = false,
                contentDescription = t.conversationMenu,
            )
        }
    }
}

/**
 * The consultation's state under the header. Open, it is one quiet line with the time
 * left; shut, it says whether the day ran out or the doctor ended it, and offers the
 * one thing that helps — a new window on the same thread.
 */
@Composable
private fun ConsultationBanner(
    window: ConsultationWindow,
    now: kotlin.time.Instant,
    reopening: Boolean,
    onReopen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val d = strings.doctors
    val paid = window.payment == ConsultationPayment.PAID
    if (window.open) {
        Row(
            modifier
                .fillMaxWidth()
                .clip(Radius.cardSmall)
                .background(c.success.copy(alpha = if (c.isDark) 0.18f else 0.10f))
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Box(Modifier.size(8.dp).clip(Radius.chip).background(c.success))
            Text(
                d.consultationOpen,
                style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = c.successText,
                // The title takes what the chip and the clock leave, on one line: sharing
                // the row half and half with a spacer broke it mid-word beside the chip.
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (paid) PaidChip()
            Icon(SadoraIcons.Clock, contentDescription = null, Modifier.size(IconSize.sm), tint = c.muted)
            Text(
                d.timeLeft(window.remaining(now)),
                style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                color = c.muted,
            )
        }
    } else {
        Column(
            modifier
                .fillMaxWidth()
                .clip(Radius.cardSmall)
                .background(c.surface2)
                .padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    if (window.closedByDoctor) d.consultationClosed else d.consultationExpired,
                    style = Sadora.type.h3,
                    color = c.text,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (paid) PaidChip()
            }
            // A paid window the doctor never answered: the money is on its way back, and
            // the thread is where she looks for it.
            when (window.payment) {
                ConsultationPayment.REFUND_DUE -> Text(d.refundDue, style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.warning)
                ConsultationPayment.REFUNDED -> Text(d.refunded, style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.successText)
                else -> Unit
            }
            Text(
                if (window.doctorPriceMinor > 0) d.consultationClosedBodyPaid else d.consultationClosedBody,
                style = Sadora.type.body,
                color = c.muted,
            )
            SadoraButton(d.reopen, onClick = onReopen, enabled = !reopening)
        }
    }
}

/**
 * The doctor's advice, pinned under the banner: what she wrote when she closed the
 * window, kept where the patient will look for it rather than scrolled away with the
 * thread. Three lines until she asks for the rest.
 */
@Composable
private fun DoctorSummaryCard(summary: String, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    val d = strings.doctors
    var expanded by remember(summary) { mutableStateOf(false) }
    var overflows by remember(summary) { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(Radius.cardSmall)
            .background(c.primary.copy(alpha = if (c.isDark) 0.18f else 0.08f))
            .noRippleClickable(enabled = overflows || expanded, role = Role.Button) { expanded = !expanded }
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(SadoraIcons.Document, contentDescription = null, Modifier.size(IconSize.sm), tint = c.textAccent)
            Text(
                d.summaryTitle,
                style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = c.textAccent,
                modifier = Modifier.weight(1f),
            )
            if (overflows || expanded) {
                Text(
                    if (expanded) d.showLess else d.showMore,
                    style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified, fontWeight = FontWeight.SemiBold),
                    color = c.textAccent,
                )
            }
        }
        Text(
            summary,
            style = Sadora.type.body,
            color = c.text,
            maxLines = if (expanded) Int.MAX_VALUE else SummaryLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
    }
}

/**
 * Five stars and an optional line, once the doctor has answered. Anonymous on her page,
 * and said so, because a rating a patient thinks the doctor will read is not honest.
 */
@Composable
private fun RateConsultationCard(sending: Boolean, onSend: (Int, String?) -> Unit) {
    val c = Sadora.colors
    val d = strings.doctors
    var stars by remember { mutableStateOf(0) }
    var review by remember { mutableStateOf("") }
    SadoraCard(padding = Spacing.md, verticalGap = Spacing.xs) {
        Text(d.rateTitle, style = Sadora.type.h3, color = c.text)
        Text(d.rateBody, style = Sadora.type.body, color = c.muted)
        StarRow(stars, size = 30.dp, onSelect = { stars = it }, modifier = Modifier.align(Alignment.CenterHorizontally))
        if (stars > 0) {
            SadoraTextField(
                value = review,
                onValueChange = { review = it.take(ReviewMax) },
                placeholder = d.reviewPlaceholder,
                singleLine = false,
                imeAction = androidx.compose.ui.text.input.ImeAction.Default,
            )
            SadoraButton(d.rateSend, onClick = { onSend(stars, review) }, enabled = !sending)
        }
    }
}

@Composable
private fun RatedThanks() {
    val c = Sadora.colors
    Text(
        strings.doctors.rateThanks,
        style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
        color = c.successText,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
    )
}

/** Lines of the doctor's advice shown before "Batafsil". */
private const val SummaryLines = 3

/** The server's own limit on a review. */
private const val ReviewMax = 1000

/** Where the field would be, when nothing can be sent: says why, instead of failing on send. */
@Composable
private fun ClosedComposer(text: String) {
    val c = Sadora.colors
    Text(
        text,
        style = Sadora.type.body,
        color = c.muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().background(c.surface).padding(Spacing.sm).navigationBarsPadding(),
    )
}

/**
 * The field, the attach button and the send button.
 *
 * [onTyping] fires on every change that leaves text in the field; the controller keeps
 * it to one call every few seconds. The text is cleared only once the line has gone —
 * one that failed offline stays in the field rather than being lost.
 */
@Composable
private fun MessageComposer(
    onSend: suspend (String) -> Boolean,
    onTyping: () -> Unit,
    onAttach: (() -> Unit)?,
    sendingAttachment: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.community
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val sendScope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        if (sendingAttachment) {
            Text(t.sending, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted)
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (onAttach != null) {
                RoundIconButton(SadoraIcons.Plus, onClick = onAttach, filled = false, contentDescription = t.attach)
            }
            SadoraTextField(
                value = draft,
                onValueChange = {
                    val next = acceptText(it, Limits.MESSAGE_MAX)
                    if (next != draft && next.isNotBlank()) onTyping()
                    draft = next
                },
                placeholder = t.messageHint,
                modifier = Modifier.weight(1f),
            )
            AnimatedVisibility(
                visible = draft.isNotBlank(),
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140)),
            ) {
                Box(
                    Modifier
                        .size(MinTouchTarget)
                        .clip(Radius.chip)
                        .background(c.primary)
                        .noRippleClickable(enabled = !sending, role = Role.Button) {
                            val text = draft
                            sending = true
                            sendScope.launch {
                                if (onSend(text) && draft == text) draft = ""
                                sending = false
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(SadoraIcons.ArrowUp, contentDescription = t.send, Modifier.size(IconSize.md), tint = c.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun AttachOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, note: String, onClick: () -> Unit) {
    val c = Sadora.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radius.cardSmall)
            .background(c.surface2)
            .pressable(onClick = onClick)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            Modifier.size(40.dp).clip(Radius.chip).background(c.primary.copy(alpha = if (c.isDark) 0.24f else 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, Modifier.size(IconSize.md), tint = c.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Sadora.type.h3, color = c.text)
            Text(note, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted)
        }
    }
}

/** The photo she picked, a caption if she wants one, and the button that sends both. */
@Composable
private fun PhotoSendContent(photo: CapturedPhoto, sending: Boolean, onSend: (String) -> Unit) {
    val t = strings.community
    var caption by remember(photo) { mutableStateOf("") }
    val preview by produceState<ImageBitmap?>(null, photo) {
        value = withContext(Dispatchers.Default) { decodeBase64(photo.base64)?.let(::decodeImage) }
    }
    Box(
        Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(Radius.cardSmall),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = preview
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp))
        } else {
            Skeleton(Modifier.fillMaxWidth().height(200.dp))
        }
    }
    SadoraTextField(
        value = caption,
        onValueChange = { caption = acceptText(it, Limits.MESSAGE_MAX) },
        placeholder = t.photoCaptionHint,
    )
    SadoraButton(if (sending) t.sending else t.sendPhoto, onClick = { onSend(caption) }, enabled = !sending, icon = SadoraIcons.Send)
}

/** One line. Hers on the right in the brand colour; the other side's on the left on a surface. */
@Composable
private fun Bubble(
    message: DirectMessage,
    conversationId: String?,
    messages: MessagesController,
    onOpenImage: () -> Unit,
    onOpenRecord: () -> Unit,
) {
    val c = Sadora.colors
    val t = strings.community
    val mine = message.isMine
    val shape = if (mine) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }
    val content = if (mine) c.onPrimary else c.text
    val meta = if (mine) c.onPrimary.copy(alpha = 0.85f) else c.muted2
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(if (mine) c.heroColors.first() else c.surface)
                .padding(
                    horizontal = if (message.kind == MessageKind.Image) 4.dp else Spacing.sm,
                    vertical = if (message.kind == MessageKind.Image) 4.dp else 10.dp,
                ),
        ) {
            when (message.kind) {
                MessageKind.Text -> Text(message.body, style = Sadora.type.body, color = content)
                MessageKind.Image -> {
                    PhotoInBubble(message, conversationId, messages, onOpenImage)
                    if (message.body.isNotBlank()) {
                        Text(
                            message.body,
                            style = Sadora.type.body,
                            color = content,
                            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs).widthIn(max = PhotoWidth),
                        )
                    }
                }
                MessageKind.Record -> RecordInBubble(mine, onOpen = onOpenRecord.takeIf { mine })
            }
            Row(
                Modifier
                    .align(Alignment.End)
                    .padding(top = 2.dp, end = if (message.kind == MessageKind.Image) Spacing.xs else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    strings.dates.ago(message.createdAt, Clock.System.now()),
                    style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                    color = meta,
                )
                if (mine) ReadTicks(read = message.read, tint = meta)
            }
        }
    }
}

/**
 * The photo, sized from the width and height the line carries so the bubble takes its
 * room before the picture arrives and nothing below it jumps when it does.
 */
@Composable
private fun PhotoInBubble(
    message: DirectMessage,
    conversationId: String?,
    messages: MessagesController,
    onOpen: () -> Unit,
) {
    val c = Sadora.colors
    val t = strings.community
    val aspect = message.image?.aspect ?: 1f
    val photo = rememberMessagePhoto(message.id, conversationId, messages)
    Box(
        Modifier
            .width(PhotoWidth)
            .aspectRatio(1f / aspect)
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface2)
            .semantics { contentDescription = t.photo }
            .noRippleClickable(enabled = photo.bitmap != null, role = Role.Image, onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = photo.bitmap
        when {
            bitmap != null -> Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            photo.failed -> Text(t.photoFailed, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted, textAlign = TextAlign.Center)
            else -> Skeleton(Modifier.fillMaxSize(), shape = RoundedCornerShape(14.dp))
        }
    }
}

/** "Tibbiy karta biriktirildi": a card in the bubble. Hers opens what she sent. */
@Composable
private fun RecordInBubble(mine: Boolean, onOpen: (() -> Unit)?) {
    val c = Sadora.colors
    val t = strings.community
    val content = if (mine) c.onPrimary else c.text
    Row(
        Modifier.then(if (onOpen != null) Modifier.noRippleClickable(role = Role.Button, onClick = onOpen) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            Modifier.size(36.dp).clip(Radius.chip).background(content.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SadoraIcons.Document, contentDescription = null, Modifier.size(IconSize.md), tint = content)
        }
        Column(Modifier.widthIn(max = 220.dp)) {
            Text(t.recordAttached, style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = content)
            Text(t.recordAttachedNote, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = content.copy(alpha = 0.85f))
        }
    }
}

/** ✓ sent, ✓✓ read — two marks drawn over each other, as every messenger draws them. */
@Composable
private fun ReadTicks(read: Boolean, tint: Color) {
    val t = strings.community
    val label = if (read) t.tickRead else t.tickSent
    Box(Modifier.width(if (read) 18.dp else 13.dp).semantics { contentDescription = label }) {
        Icon(SadoraIcons.Check, contentDescription = null, Modifier.size(13.dp), tint = tint)
        if (read) Icon(SadoraIcons.Check, contentDescription = null, Modifier.size(13.dp).offset(x = 5.dp), tint = tint)
    }
}

/** "Dr Nodira yozmoqda…" as a soft line on her side of the thread. */
@Composable
private fun TypingRow(name: String) {
    val c = Sadora.colors
    Text(
        strings.community.typing(name),
        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
        color = c.muted,
        modifier = Modifier
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp))
            .background(c.surface)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}

/**
 * A photo opened over the thread: fitted to the screen, pinched or dragged to look
 * closer, closed by the button, a tap outside, or back.
 */
@Composable
private fun ImageViewer(message: DirectMessage, conversationId: String, messages: MessagesController, onDismiss: () -> Unit) {
    val photo = rememberMessagePhoto(message.id, conversationId, messages)
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { _, zoom, move, _ ->
        scale = (scale * zoom).coerceIn(1f, 4f)
        pan = if (scale == 1f) Offset.Zero else pan + move
    }
    SystemBackHandler(enabled = true, onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .noRippleClickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        photo.bitmap?.let { bitmap ->
            Image(
                bitmap,
                contentDescription = strings.community.photo,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformable(transform)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = pan.x
                        translationY = pan.y
                    },
            )
        }
        if (message.body.isNotBlank()) {
            Text(
                message.body,
                style = Sadora.type.body,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(Spacing.lg),
            )
        }
        Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(Spacing.md)) {
            CircleIconButton(SadoraIcons.Plus, contentDescription = strings.common.close, onClick = onDismiss, modifier = Modifier.graphicsLayer { rotationZ = 45f })
        }
    }
}

/** What she attached, as the doctor reads it: who, the headline numbers, and what repeats. */
@Composable
private fun RecordContent(record: DoctorSummary?) {
    val c = Sadora.colors
    val t = strings.community
    if (record == null) {
        Skeleton(Modifier.fillMaxWidth().height(18.dp))
        Skeleton(Modifier.fillMaxWidth(0.7f).height(14.dp))
        Skeleton(Modifier.fillMaxWidth().height(64.dp))
        return
    }
    val person = record.person
    Text(
        listOfNotNull(person.name, person.age?.let(t::recordAge)).joinToString(" · "),
        style = Sadora.type.h3,
        color = c.text,
    )
    Text(t.recordNote, style = Sadora.type.body, color = c.muted)
    val facts = buildList {
        record.cycle?.cycleDay?.let { add(t.recordCycleDay(it)) }
        record.pregnancy?.week?.let { add(t.recordPregnancyWeek(it)) }
        if (record.days.isNotEmpty()) add(t.recordDays(record.days.size))
    }
    val symptoms = record.symptomCounts.take(5).map { it.label }
    val medications = record.medications.filter { it.active }.map { it.name }
    if (facts.isEmpty() && symptoms.isEmpty() && medications.isEmpty()) {
        Text(t.recordEmpty, style = Sadora.type.body, color = c.muted)
        return
    }
    facts.forEach { Text("• $it", style = Sadora.type.body, color = c.text) }
    if (symptoms.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(t.recordSymptoms, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
            Text(symptoms.joinToString(", "), style = Sadora.type.body, color = c.text)
        }
    }
    if (medications.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(t.recordMedications, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
            Text(medications.joinToString(", "), style = Sadora.type.body, color = c.text)
        }
    }
}

/** A photo on its way: the picture once decoded, or that it could not be had. */
private class MessagePhoto(val bitmap: ImageBitmap?, val failed: Boolean)

/**
 * Fetches and decodes a message's photo. The bytes are cached by the controller, so a
 * bubble scrolled away and back decodes again but never downloads again.
 */
@Composable
private fun rememberMessagePhoto(messageId: String, conversationId: String?, messages: MessagesController): MessagePhoto {
    val state by produceState(MessagePhoto(null, failed = false), messageId, conversationId) {
        if (conversationId == null) return@produceState
        val bytes = messages.imageBytes(conversationId, messageId)
        val bitmap = bytes?.let { withContext(Dispatchers.Default) { decodeImage(it) } }
        value = MessagePhoto(bitmap, failed = bitmap == null)
    }
    return state
}

@OptIn(ExperimentalResourceApi::class)
private fun decodeImage(bytes: ByteArray): ImageBitmap? = runCatching { bytes.decodeToImageBitmap() }.getOrNull()

@OptIn(ExperimentalEncodingApi::class)
private fun decodeBase64(value: String): ByteArray? = runCatching { Base64.decode(value) }.getOrNull()

private val PhotoWidth = 232.dp
private const val PollMillis = 3_000L

/** How close to the top of a thread, in items, the page above is asked for. */
private const val OlderThreshold = 4
private const val ListRefreshMillis = 20_000L
private const val ClockTickMillis = 30_000L
