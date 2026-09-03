package org.example.project.ui.core

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.project.design.IconSize
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.Fmt
import org.example.project.model.SampleData
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.PremiumGradientBadge
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTextField
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.noRippleClickable

/**
 * "AI" — the app's most distinctive screen.
 *
 * Two things stay visible at all times: the context strip showing which of the
 * user's data the answer will be based on, and the note that SADORA is not a
 * diagnostic tool.
 */
@Composable
fun AiScreen(
    state: AppState,
    onOpenChat: () -> Unit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    var draft by remember { mutableStateOf("") }

    Column(modifier) {
        SadoraTopBar(
            "SADORA AI",
            trailing = { if (state.isPremium) PremiumGradientBadge() },
        )

        Box(Modifier.weight(1f)) {
            ScreenContent {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = Spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        AiOrb()
                        Text(
                            "Sizga qanday yordam bera olaman?",
                            style = Sadora.type.h2,
                            color = c.text,
                            textAlign = TextAlign.Center,
                        )
                        ContextStrip(state)
                    }
                }

                item { CardLabel("Taklif qilingan savollar") }

                items(SampleData.suggestedQuestions.size) { index ->
                    val question = SampleData.suggestedQuestions[index]
                    SadoraCard(padding = Spacing.sm, onClick = onOpenChat) {
                        Text(question, style = Sadora.type.h3, color = c.text)
                    }
                }

                item { CardLabel("So'nggi suhbatlar") }

                items(SampleData.recentConversations.size) { index ->
                    val conversation = SampleData.recentConversations[index]
                    SadoraCard(padding = Spacing.sm, onClick = onOpenChat) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Icon(
                                SadoraIcons.Clock,
                                contentDescription = null,
                                Modifier.size(IconSize.md),
                                tint = c.muted,
                            )
                            Text(
                                conversation.title,
                                style = Sadora.type.body,
                                color = c.text,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                conversation.whenLabel,
                                style = Sadora.type.body,
                                color = c.muted2,
                            )
                        }
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(c.surface)
                .imePadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
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
                            draft = ""
                            onOpenChat()
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
            Text(
                SampleData.medicalDisclaimer,
                style = Sadora.type.body,
                color = c.muted2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The breathing gradient orb — the single strongest colour source on this screen. */
@Composable
private fun AiOrb(modifier: Modifier = Modifier) {
    val c = Sadora.colors
    val transition = rememberInfiniteTransition()
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
    )
    Box(
        modifier
            .size(96.dp)
            .scale(pulse)
            .clip(Radius.chip)
            .background(Brush.linearGradient(listOf(c.secondary, c.primary))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            SadoraIcons.Sparkle,
            contentDescription = null,
            Modifier.size(34.dp),
            tint = c.onPrimary,
        )
    }
}

/** "Sikl 14-kun · Uyqu 6s 40d · Suv 1,2 L asosida" */
@Composable
private fun ContextStrip(state: AppState) {
    val c = Sadora.colors
    Row(
        Modifier
            .clip(Radius.chip)
            .background(c.surface2)
            .padding(horizontal = Spacing.sm, vertical = 6.dp),
    ) {
        Text(
            "Sikl ${state.cycleDay}-kun · Uyqu ${state.sleepLabel()} · " +
                "Suv ${Fmt.litres(state.waterMl)} L asosida",
            style = Sadora.type.body,
            color = c.muted,
        )
    }
}
