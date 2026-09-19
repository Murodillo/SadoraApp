package uz.sadora.app.ui.core

import androidx.compose.runtime.LaunchedEffect
import uz.sadora.app.data.readable
import uz.sadora.app.ui.components.ErrorStrip
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.model.AppState
import uz.sadora.app.model.CommunityPost
import uz.sadora.app.model.CommunityTopic
import uz.sadora.app.model.ReportReason
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import uz.sadora.app.data.CommunityController
import uz.sadora.app.data.MessagesController
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraSwitch
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.ui.components.pressable
import uz.sadora.app.i18n.strings
import uz.sadora.contract.Limits

/**
 * The composer, raised by the shell over the tab bar.
 *
 * It opens on the room she was reading — "Hammasi" is a filter, not a room, so it
 * falls back to the first real one — and says which alias the post will carry, since
 * that is the whole reassurance the room offers.
 */
@Composable
fun ComposePostSheetContent(
    state: AppState,
    onPosted: () -> Unit,
    /** Null in a preview; with it, "sent" is said only once the server has the post. */
    community: CommunityController? = null,
) {
    val c = Sadora.colors
    val t = strings.community
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    // A failure left over from the feed is not this sheet's failure.
    LaunchedEffect(Unit) { community?.clearError() }
    val rooms = remember { CommunityTopic.entries.filter { it != CommunityTopic.All } }
    var topic by remember {
        mutableStateOf(state.communityTopic.takeIf { it != CommunityTopic.All } ?: rooms.first())
    }
    var body by remember { mutableStateOf("") }
    val canPost = body.trim().length >= MinPostLength && body.length <= MaxPostLength

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            state.communityAlias?.let { t.postsAs(it) } ?: t.postsAnonymously,
            style = Sadora.type.body,
            color = c.muted,
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            rooms.forEach { room ->
                SelectChip(label = t.topic(room), selected = topic == room, onClick = { topic = room })
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(Radius.cardSmall)
                .background(c.surface2)
                .padding(Spacing.sm)
                .heightIn(min = 120.dp),
        ) {
            if (body.isEmpty()) {
                Text(t.whatIsOnYourMind, style = Sadora.type.body, color = c.muted2)
            }
            BasicTextField(
                value = body,
                // Trimmed to fit, not refused: a paste one character over used to do nothing.
                onValueChange = { body = it.take(MaxPostLength) },
                textStyle = Sadora.type.body.copy(color = c.text),
                cursorBrush = SolidColor(c.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${body.length} / $MaxPostLength", style = Sadora.type.body, color = c.muted2)
        }

        // The daily limit, a restricted account, no network: the reason, with her text
        // still in the field. The sheet used to close on "Yuborildi" and the draft with it.
        community?.error?.let { ErrorStrip(it.readable()) }

        SadoraButton(
            if (sending) strings.common.saving else t.send,
            onClick = {
                if (community == null) {
                    state.createPost(topic, body)
                    onPosted()
                } else {
                    sending = true
                    scope.launch {
                        val sent = community.createPost(topic, body.trim())
                        sending = false
                        if (sent) onPosted()
                    }
                }
            },
            enabled = canPost && !sending,
        )
    }
}

/**
 * What can be done to a post besides reacting to it: report it, or — for her own —
 * take it down. The reasons are the closed list the moderation queue sorts by.
 */
/**
 * Behind the info button: what anonymity means here, her alias, and the five rules.
 *
 * Nothing is remembered about having read it — the sheet is a tap away whenever the
 * question comes back, which it does the first time a post makes her uneasy.
 */
@Composable
fun CommunityRulesSheetContent(state: AppState, onDone: () -> Unit) {
    val c = Sadora.colors
    val t = strings.community
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        state.communityAlias?.let { alias ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                AliasAvatar(alias, state.communityTint)
                Text(t.anonymousAs(alias), style = Sadora.type.h3, color = c.text)
            }
        }
        Text(t.rulesIntro, style = Sadora.type.body, color = c.muted)
        t.rules.forEach { rule ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("•", style = Sadora.type.body, color = c.primary)
                Text(rule, style = Sadora.type.body, color = c.text)
            }
        }
        Spacer(Modifier.width(0.dp))
        SadoraButton(t.rulesButton, onClick = onDone)
    }
}

/**
 * Her bio and her door, edited in a sheet over whatever screen asked.
 *
 * The bio is one line about her under the alias — capped short so it cannot become
 * the paragraph that identifies her; the hint says what to leave out.
 */
@Composable
fun EditBioSheetContent(
    state: AppState,
    community: CommunityController,
    onSaved: () -> Unit,
) {
    val c = Sadora.colors
    val t = strings.community
    val scope = rememberCoroutineScope()
    var bio by remember { mutableStateOf(state.communityBio.orEmpty()) }
    var dmOpen by remember { mutableStateOf(state.communityDmOpen) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(t.bioHint, style = Sadora.type.body, color = c.muted)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Radius.cardSmall)
                .background(c.surface2)
                .padding(Spacing.sm)
                .heightIn(min = 72.dp),
        ) {
            if (bio.isEmpty()) Text(t.noBio, style = Sadora.type.body, color = c.muted2)
            BasicTextField(
                value = bio,
                onValueChange = { if (it.length <= Limits.BIO_MAX) bio = it },
                textStyle = Sadora.type.body.copy(color = c.text),
                cursorBrush = SolidColor(c.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text("${bio.length} / ${Limits.BIO_MAX}", style = Sadora.type.body, color = c.muted2)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Column(Modifier.weight(1f)) {
                Text(t.acceptMessages, style = Sadora.type.h3, color = c.text)
                Text(t.acceptMessagesHint, style = Sadora.type.body, color = c.muted)
            }
            SadoraSwitch(checked = dmOpen, onCheckedChange = { dmOpen = it })
        }

        SadoraButton(
            if (community.busy) strings.common.saving else t.saveProfile,
            enabled = !community.busy,
            onClick = {
                scope.launch {
                    if (community.updateProfile(bio = bio, dmOpen = dmOpen)) onSaved()
                }
            },
        )
    }
}

/**
 * The "···" on a thread: her page, the block, and a report on her latest line. The
 * block asks once, in the same sheet, rather than opening a dialog over a sheet.
 */
@Composable
fun ConversationMenuSheetContent(
    messages: MessagesController,
    community: CommunityController,
    onOpenProfile: (String) -> Unit,
    onDone: (message: String?) -> Unit,
) {
    val c = Sadora.colors
    val t = strings.community
    val scope = rememberCoroutineScope()
    val thread = messages.current ?: return
    var reason by remember { mutableStateOf<ReportReason?>(null) }
    var confirmingBlock by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SadoraButton(t.viewProfile, tone = ButtonTone.Secondary, onClick = { onDone(null); onOpenProfile(thread.alias) })

        if (confirmingBlock) {
            Text(t.blockConfirmBody, style = Sadora.type.body, color = c.muted)
            SadoraButton(
                t.block,
                tone = ButtonTone.Destructive,
                onClick = {
                    scope.launch {
                        val ok = community.setBlocked(thread.alias, blocked = true)
                        if (ok) messages.refreshThread(thread.id)
                        onDone(if (ok) t.blocked else null)
                    }
                },
            )
        } else {
            SadoraButton(t.block, tone = ButtonTone.Secondary, onClick = { confirmingBlock = true })
        }

        Text(t.reportConversation, style = Sadora.type.h3, color = c.text)
        Text(t.reportNote, style = Sadora.type.body, color = c.muted)
        ReportReason.entries.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Radius.cardSmall)
                    .background(if (reason == option) c.primary.copy(alpha = 0.12f) else c.surface2)
                    .pressable { reason = option }
                    .padding(horizontal = Spacing.sm, vertical = 12.dp),
            ) {
                Text(
                    t.reportReason(option),
                    style = Sadora.type.body.copy(fontWeight = if (reason == option) FontWeight.SemiBold else FontWeight.Normal),
                    color = c.text,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        SadoraButton(
            t.sendReport,
            enabled = reason != null && !messages.busy,
            onClick = {
                scope.launch {
                    val ok = reason?.let { messages.report(it, null) } ?: false
                    onDone(if (ok) t.reportSent else null)
                }
            },
        )
    }
}

@Composable
fun PostMenuSheetContent(
    state: AppState,
    post: CommunityPost,
    onDone: (message: String?) -> Unit,
    /** Null in a preview; with it, the toast waits for the server's answer. */
    community: CommunityController? = null,
) {
    val c = Sadora.colors
    val t = strings.community
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf<ReportReason?>(null) }
    var armed by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { community?.clearError() }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (post.isMine) {
            Text(t.yourOwnPost, style = Sadora.type.body, color = c.muted)
            community?.error?.let { ErrorStrip(it.readable()) }
            SadoraButton(
                // Two taps: it cannot be undone, and one stray tap used to be enough.
                if (armed) t.deletePostConfirm else t.deletePost,
                onClick = {
                    when {
                        !armed -> armed = true
                        community == null -> {
                            state.deletePost(post.id)
                            onDone(t.postDeleted)
                        }
                        else -> {
                            working = true
                            scope.launch {
                                // Said only once it is gone: a delete that failed used to
                                // toast "o'chirildi" and the post came back on refresh.
                                val gone = community.deletePost(post.id)
                                working = false
                                if (gone) onDone(t.postDeleted)
                            }
                        }
                    }
                },
                enabled = !working,
                tone = ButtonTone.Destructive,
            )
            return@Column
        }

        Text(t.reportReasonTitle, style = Sadora.type.h3, color = c.text)
        Text(
            t.reportNote,
            style = Sadora.type.body,
            color = c.muted,
        )
        ReportReason.entries.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Radius.cardSmall)
                    .background(if (reason == option) c.primary.copy(alpha = 0.12f) else c.surface2)
                    .pressable { reason = option }
                    .padding(horizontal = Spacing.sm, vertical = 12.dp),
            ) {
                Text(
                    t.reportReason(option),
                    style = Sadora.type.body.copy(fontWeight = if (reason == option) FontWeight.SemiBold else FontWeight.Normal),
                    color = c.text,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        community?.error?.let { ErrorStrip(it.readable()) }
        SadoraButton(
            t.sendReport,
            onClick = {
                val chosen = reason ?: return@SadoraButton
                if (community == null) {
                    state.reportPost(post.id, chosen, null)
                    onDone(t.reportSent)
                } else {
                    working = true
                    scope.launch {
                        // "Already reported" is a refusal with a reason, not a success.
                        val sent = community.reportPost(post.id, chosen, null)
                        working = false
                        if (sent) onDone(t.reportSent)
                    }
                }
            },
            enabled = reason != null && !working,
        )
    }
}

private const val MinPostLength = Limits.POST_MIN
private const val MaxPostLength = Limits.POST_MAX
