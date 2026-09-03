package org.example.project.ui.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import org.example.project.design.IconSize
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.Fmt
import org.example.project.model.SampleData
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.ChipFlowRow
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraTextField
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.noRippleClickable

private data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    /** Which of the user's metrics the answer drew on. */
    val sources: List<String> = emptyList(),
)

/**
 * "AI chat · Premium" — the conversation view.
 *
 * Two Premium-specific affordances the design calls for: a running count of the
 * daily question allowance, and per-answer source tags naming the data used.
 */
@Composable
fun AiChatScreen(
    state: AppState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    var draft by remember { mutableStateOf("") }
    var used by remember { mutableStateOf(3) }
    val messages = remember {
        mutableStateListOf(
            ChatMessage(true, "Nega o'zimni charchagan his qilyapman?"),
            ChatMessage(
                false,
                "Oxirgi uch kunda uyqu odatdagidan qisqa bo'lgan va suv iste'moli " +
                    "pasaygan. Shu kunlarda energiya ham past qayd etilgan.\n\n" +
                    "Bugun ikki qadam: tushga qadar 700 ml suv va 23:00 gacha yotish.",
                sources = listOf("Uyqu", "Suv", "Energiya"),
            ),
        )
    }

    Column(modifier) {
        SadoraTopBar(
            "SADORA AI",
            onBack = onClose,
            trailing = {
                SadoraBadge("$used/20 bugun", BadgeTone.Premium)
            },
        )

        Box(Modifier.weight(1f)) {
            ScreenContent {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(Radius.chip)
                            .background(c.surface2)
                            .padding(horizontal = Spacing.sm, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "Sikl ${state.cycleDay}-kun · Uyqu ${state.sleepLabel()} · " +
                                "Suv ${Fmt.litres(state.waterMl)} L asosida",
                            style = Sadora.type.body,
                            color = c.muted,
                        )
                    }
                }

                items(messages.size) { index -> ChatBubble(messages[index]) }

                item {
                    Text(
                        SampleData.medicalDisclaimer,
                        style = Sadora.type.body,
                        color = c.muted2,
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(c.surface)
                .imePadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SadoraTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = "SADORA AI'dan so'rang…",
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(48.dp)
                    .clip(Radius.chip)
                    .background(
                        if (draft.isBlank()) {
                            Brush.linearGradient(listOf(c.surface2, c.surface2))
                        } else {
                            Brush.linearGradient(listOf(c.secondary, c.primary))
                        },
                    )
                    .noRippleClickable(enabled = draft.isNotBlank()) {
                        messages.add(ChatMessage(true, draft))
                        messages.add(
                            ChatMessage(
                                false,
                                "Ma'lumotlaringizni ko'rib chiqdim. Bu umumiy salomatlik " +
                                    "ma'lumoti — tashxis emas.",
                                sources = listOf("Sikl", "Uyqu"),
                            ),
                        )
                        draft = ""
                        used++
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
    SadoraIcons.ArrowUp,
    contentDescription = "Yuborish",
    Modifier.size(IconSize.lg),
    tint = if (draft.isBlank()) c.muted else c.onPrimary,
)
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val c = Sadora.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.88f)
                .clip(Radius.card)
                .background(if (message.fromUser) c.surface2 else c.surface)
                .padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(message.text, style = Sadora.type.body, color = c.text)
            if (message.sources.isNotEmpty()) {
                // Source tags make the basis of every answer inspectable.
                ChipFlowRow(horizontalGap = Spacing.xxs, verticalGap = Spacing.xxs) {
                    message.sources.forEach { source ->
                        SadoraBadge(source, BadgeTone.Neutral)
                    }
                }
            }
        }
    }
}
