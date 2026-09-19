package uz.sadora.app.ui.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.sadora.app.data.MessagesController
import uz.sadora.app.data.readable
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.Conversation
import uz.sadora.app.model.DirectMessage
import uz.sadora.app.ui.components.BadgeRow
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.RoundIconButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.Skeleton
import uz.sadora.app.ui.components.noRippleClickable

/** Her private threads, most recently written first, with what is unread in each. */
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

    LaunchedEffect(messages) { messages.load() }

    Column(modifier) {
        SadoraTopBar(t.messagesTitle, onBack = onClose, subtitle = t.messagesSubtitle)
        ScreenContent {
            messages.error?.let { failure ->
                item { ErrorStrip(failure.readable(errors), onRetry = messages::clearError) }
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
            }
        }
    }
}

@Composable
private fun ConversationRow(thread: Conversation, onClick: () -> Unit) {
    val c = Sadora.colors
    val t = strings.community
    val unread = thread.unread > 0
    SadoraCard(padding = Spacing.sm, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            AliasAvatar(thread.alias, thread.tint, size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(thread.alias, style = Sadora.type.h3, color = c.text, maxLines = 1)
                    BadgeRow(thread.badges, max = 1, compact = true)
                }
                Text(
                    if (thread.blocked) t.conversationBlocked else thread.lastMessage.orEmpty(),
                    style = Sadora.type.body.copy(fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (unread) c.text else c.muted,
                    maxLines = 1,
                )
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
 * One thread: her lines on the right, the other alias's on the left, the field pinned
 * above the keyboard.
 *
 * Polled every few seconds while open — there is no push channel into a running app,
 * and a reply that arrives while she is looking at the thread should simply appear.
 * The poll is silent; a failed one is the next one's problem.
 */
@Composable
fun ConversationScreen(
    conversationId: String?,
    alias: String,
    messages: MessagesController,
    onOpenProfile: (String) -> Unit,
    onOpenMenu: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.community
    val c = Sadora.colors
    val errors = strings.errors
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()

    LaunchedEffect(conversationId, alias) { messages.open(conversationId, alias) }
    DisposableEffect(messages) { onDispose { messages.close() } }

    // The id appears with the first send, and the poll starts with it.
    val liveId = messages.current?.id?.takeIf { it.isNotEmpty() }
    LaunchedEffect(liveId) {
        if (liveId == null) return@LaunchedEffect
        while (true) {
            delay(PollMillis)
            messages.refreshThread(liveId)
        }
    }
    LaunchedEffect(messages.messages.size) {
        if (messages.messages.isNotEmpty()) list.animateScrollToItem(messages.messages.size - 1)
    }

    val thread = messages.current
    Column(modifier.fillMaxSize().background(c.bg)) {
        SadoraTopBar(
            title = thread?.alias ?: alias,
            onBack = onClose,
            subtitle = thread?.badges?.firstOrNull()?.let(t::badge),
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    RoundIconButton(SadoraIcons.Profile, onClick = { onOpenProfile(thread?.alias ?: alias) }, filled = false, contentDescription = t.viewProfile)
                    if (liveId != null) {
                        RoundIconButton(SadoraIcons.More, onClick = onOpenMenu, filled = false, contentDescription = t.conversationMenu)
                    }
                }
            },
        )

        messages.error?.let { failure ->
            ErrorStrip(failure.readable(errors), onRetry = messages::clearError, modifier = Modifier.padding(horizontal = Spacing.screen))
        }

        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (messages.messages.isEmpty() && liveId == null) {
                item {
                    Text(
                        t.newConversation,
                        style = Sadora.type.body,
                        color = c.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
                    )
                }
            }
            items(messages.messages, key = { it.id }) { message -> Bubble(message) }
            item { Spacer(Modifier.height(Spacing.xs)) }
        }

        if (thread?.blocked == true) {
            Text(
                t.conversationBlocked,
                style = Sadora.type.body,
                color = c.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(c.surface).padding(Spacing.sm).navigationBarsPadding(),
            )
        } else {
            CommentInput(
                onSend = { body -> scope.launch { messages.send(body) } },
                placeholder = t.messageHint,
                modifier = Modifier
                    .background(c.surface)
                    .padding(horizontal = Spacing.screen, vertical = Spacing.xs)
                    .navigationBarsPadding()
                    .imePadding(),
            )
        }
    }
}

/** One line. Hers on the right in the brand colour; the other alias's on the left on a surface. */
@Composable
private fun Bubble(message: DirectMessage) {
    val c = Sadora.colors
    val mine = message.isMine
    val shape = if (mine) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(if (mine) c.heroColors.first() else c.surface)
                .padding(horizontal = Spacing.sm, vertical = 10.dp),
        ) {
            Text(message.body, style = Sadora.type.body, color = if (mine) c.onPrimary else c.text)
            Text(
                strings.dates.ago(message.createdAt, Clock.System.now()),
                style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                color = if (mine) c.onPrimary else c.muted2,
                modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
            )
        }
    }
}

private const val PollMillis = 4_000L
