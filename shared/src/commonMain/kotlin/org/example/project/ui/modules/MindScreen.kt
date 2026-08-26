package org.example.project.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.Mood
import org.example.project.model.SampleData
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.WeeklyBars
import org.example.project.ui.components.noRippleClickable

/**
 * "Ong" — mood, stress and energy.
 *
 * The language here is deliberately non-judgemental: moods are described, never
 * scored as good or bad behaviour, and the journal is explicitly private.
 */
@Composable
fun MindScreen(
    state: AppState,
    onClose: () -> Unit,
    onUpgrade: () -> Unit,
    onOpenJournal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors

    Column(modifier) {
        SadoraTopBar("Ong", onBack = onClose)

        ScreenContent {
            item {
                SadoraCard {
                    Text(
                        "Bugun o'zingizni qanday his qilyapsiz?",
                        style = Sadora.type.h3,
                        color = c.text,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Mood.entries.forEach { mood ->
                            MoodOption(
                                mood = mood,
                                selected = state.mood == mood,
                                onClick = { state.mood = mood },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SadoraCard(modifier = Modifier.weight(1f), padding = Spacing.sm) {
                        CardLabel("Stress")
                        Text("Past", style = Sadora.type.h2, color = c.text)
                    }
                    SadoraCard(modifier = Modifier.weight(1f), padding = Spacing.sm) {
                        CardLabel("Energiya")
                        Text("O'rtacha", style = Sadora.type.h2, color = c.text)
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel(
                        "7 kunlik kayfiyat",
                        trailing = {
                            Text("O'rtacha 3,6", style = Sadora.type.body, color = c.muted)
                        },
                    )
                    WeeklyBars(
                        values = listOf(0.5f, 0.7f, 0.6f, 0.9f, 0.75f, 0.55f, 0.8f),
                        labels = SampleData.weekDays,
                        color = c.secondary,
                        highlightIndex = 6,
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ToolCard(
                        emoji = "🌬️",
                        title = "Nafas mashqi",
                        subtitle = "4-7-8 · 3 daqiqa",
                        onClick = onOpenJournal,
                        modifier = Modifier.weight(1f),
                    )
                    ToolCard(
                        emoji = "📝",
                        title = "Kundalik",
                        subtitle = "Faqat siz ko'rasiz",
                        onClick = onOpenJournal,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                val onGradient = if (c.isDark) c.bg else Color.White
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(Radius.card)
                        .background(Brush.linearGradient(listOf(c.secondary, c.primary)))
                        .noRippleClickable(enabled = !state.isPremium, onClick = onUpgrade)
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Text("✦", style = Sadora.type.h3, color = onGradient)
                            Text(
                                "AI Ong yordamchisi",
                                style = Sadora.type.h3,
                                color = onGradient,
                            )
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(onGradient.copy(alpha = 0.2f))
                                .padding(horizontal = Spacing.xs, vertical = 3.dp),
                        ) {
                            Text("PREMIUM", style = Sadora.type.caption, color = onGradient)
                        }
                    }
                    Text(
                        "Kayfiyat va uyqu bog'liqliklari",
                        style = Sadora.type.body,
                        color = onGradient.copy(alpha = 0.9f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodOption(
    mood: Mood,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    Column(
        modifier
            .clip(Radius.field)
            .background(if (selected) c.primary.copy(alpha = if (c.isDark) 0.2f else 0.1f) else c.surface2)
            .noRippleClickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(mood.emoji, style = Sadora.type.h2)
        Text(
            mood.label,
            style = Sadora.type.caption.copy(
                letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
            ),
            color = if (selected) c.textAccent else c.muted,
        )
    }
}

@Composable
private fun ToolCard(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    SadoraCard(modifier = modifier, padding = Spacing.sm, onClick = onClick) {
        Text(emoji, style = Sadora.type.h1)
        Text(title, style = Sadora.type.h3, color = c.text)
        Text(subtitle, style = Sadora.type.body, color = c.muted)
    }
}
