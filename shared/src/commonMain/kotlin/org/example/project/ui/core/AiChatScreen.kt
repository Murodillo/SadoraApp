package org.example.project.ui.core

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.example.project.design.IconSize
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import kotlinx.coroutines.launch
import org.example.project.data.AiController
import org.example.project.i18n.strings
import org.example.project.model.AppState
import org.example.project.model.Fmt
import org.example.project.model.nowTimeLabel
import org.example.project.ui.components.AiMarkHeader
import org.example.project.ui.components.SadoraBottomSheet
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.CircleIconButton
import org.example.project.ui.components.appearFromBelow
import org.example.project.ui.components.noRippleClickable

private data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val time: String,
    /** A refusal — out of questions, section closed — drawn quieter than an answer. */
    val isNotice: Boolean = false,
)

/**
 * "SADORA AI" — the conversation view, drawn on the deck's navy ground.
 *
 * The caller wraps it in `SadoraDarkSurface`, so everything here reads the dark
 * palette through the ordinary tokens. Two safety rails stay on screen: the note that
 * SADORA is not a diagnostic tool, and the daily question allowance.
 */
@Composable
fun AiChatScreen(
    state: AppState,
    ai: AiController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.ai
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()

    // The allowance is the server's; the header shows it as soon as it is known.
    LaunchedEffect(ai) { ai.loadQuota() }

    fun ask(question: String) {
        val text = question.trim()
        if (text.isEmpty() || ai.busy || !ai.canAsk) return
        messages += ChatMessage(true, text, nowTimeLabel())
        draft = ""
        scope.launch {
            val answer = ai.ask(text)
            messages += if (answer != null) {
                ChatMessage(false, answer.text, nowTimeLabel())
            } else {
                // The refusal reads as a reply rather than a banner: it is what the
                // assistant has to say about this question.
                ChatMessage(false, ai.error ?: t.answerFailed, nowTimeLabel(), isNotice = true)
            }
        }
    }

    LaunchedEffect(messages.size, ai.busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
    }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(SadoraIcons.ChevronLeft, contentDescription = t.back, onClick = onClose)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    t.title,
                    style = Sadora.type.h3.copy(letterSpacing = 0.22.em, fontWeight = FontWeight.SemiBold),
                    color = c.text,
                )
                Text(t.subtitle, style = Sadora.type.body, color = c.muted)
            }
            CircleIconButton(SadoraIcons.More, contentDescription = t.menu, onClick = { showMenu = true })
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { AiMarkHeader(Modifier.fillMaxWidth().height(150.dp)) }

            item {
                Text(
                    t.basis(
                        state.cycleDay,
                        state.sleepLabel(format = strings.common::hoursMinutes),
                        Fmt.litres(state.waterMl),
                    ) + quotaLabel(ai, t),
                    style = Sadora.type.caption.copy(letterSpacing = 0.02.em),
                    color = c.muted2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (messages.isEmpty()) {
                item {
                    Text(
                        t.emptyPrompt,
                        style = Sadora.type.body,
                        color = c.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                    )
                }
            }

            items(messages.size) { index -> ChatBubble(messages[index]) }

            if (ai.busy) {
                item { TypingBubble() }
            }

            item {
                Text(
                    t.medicalDisclaimer,
                    style = Sadora.type.caption.copy(letterSpacing = 0.02.em),
                    color = c.muted2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                )
            }
        }

        // Topic chips — one tap asks a ready question in that area.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            t.topics.forEach { (label, question) ->
                Box(
                    Modifier
                        .clip(Radius.chip)
                        .background(c.surface2)
                        .noRippleClickable { ask(question) }
                        .padding(horizontal = 14.dp, vertical = Spacing.xs),
                ) {
                    Text(label, style = Sadora.type.body.copy(fontWeight = FontWeight.Medium), color = c.text)
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(Radius.chip)
                    .background(c.surface2)
                    .padding(horizontal = Spacing.md, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (draft.isEmpty()) {
                    Text(t.inputHint, style = Sadora.type.body, color = c.muted2)
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = Sadora.type.body.copy(color = c.text),
                    cursorBrush = SolidColor(c.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val canSend = draft.isNotBlank() && ai.canAsk && !ai.busy
            Box(
                Modifier
                    .size(48.dp)
                    .clip(Radius.chip)
                    .background(if (canSend) c.heroGradient else androidx.compose.ui.graphics.Brush.linearGradient(listOf(c.surface2, c.surface2)))
                    .noRippleClickable(enabled = canSend) { ask(draft) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    SadoraIcons.Send,
                    contentDescription = t.send,
                    Modifier.size(IconSize.md),
                    tint = if (canSend) c.onPrimary else c.muted2,
                )
            }
        }
    }

    SadoraBottomSheet(visible = showMenu, title = t.title, onDismiss = { showMenu = false }) {
        Text(
            t.sessionOnly,
            style = Sadora.type.body,
            color = c.muted,
        )
        Text(strings.ai.medicalDisclaimer, style = Sadora.type.caption, color = c.muted2)
        SadoraButton(
            t.clearChat,
            onClick = {
                messages.clear()
                showMenu = false
            },
            tone = ButtonTone.Secondary,
            enabled = messages.isNotEmpty(),
        )
    }
}

/** " · 3/20 savol qoldi", or nothing while the allowance is unknown or unmetered. */
private fun quotaLabel(ai: AiController, t: org.example.project.i18n.AiStrings): String {
    val quota = ai.quota ?: return ""
    val limit = quota.dailyLimit ?: return ""
    val left = quota.remainingToday ?: return ""
    return t.questionsLeft(left, limit)
}

/** Three dots that breathe while the answer is on its way. */
@Composable
private fun TypingBubble() {
    val c = Sadora.colors
    val transition = rememberInfiniteTransition()
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
    )
    Row(
        Modifier
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp))
            .background(c.surface2)
            .padding(horizontal = Spacing.md, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(3) { index ->
            val on = phase.toInt() % 3 == index
            Box(
                Modifier
                    .size(8.dp)
                    .clip(Radius.chip)
                    .background(if (on) c.primary else c.muted2.copy(alpha = 0.5f)),
            )
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val c = Sadora.colors
    val shape = if (message.fromUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
    }
    Row(
        Modifier.fillMaxWidth().appearFromBelow(distance = 10.dp),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.82f)
                .clip(shape)
                .background(
                    when {
                        message.fromUser -> c.primary
                        message.isNotice -> c.warningSoft.copy(alpha = if (c.isDark) 0.18f else 0.14f)
                        else -> c.surface2
                    },
                )
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(
                message.text,
                style = Sadora.type.body,
                color = if (message.fromUser) c.onPrimary else c.text,
            )
            Text(
                message.time,
                style = Sadora.type.caption.copy(letterSpacing = 0.02.em),
                color = if (message.fromUser) c.onPrimary.copy(alpha = 0.7f) else c.muted2,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}