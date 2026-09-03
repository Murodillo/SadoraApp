package org.example.project.ui.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.design.IconSize
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.Fmt
import org.example.project.model.MedStatus
import org.example.project.model.SampleData
import org.example.project.nav.Route
import org.example.project.ui.components.AiSummaryCard
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.EmptyState
import org.example.project.ui.components.GreetingHeader
import org.example.project.ui.components.PillButton
import org.example.project.ui.components.ProgressRing
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraProgressBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.Skeleton
import org.example.project.ui.components.noRippleClickable

/**
 * "Bugun" — only what matters today.
 *
 * Deliberately not a catalogue of every feature: the design calls for the day's
 * summary, the journey card, and the handful of metrics the user set goals around.
 */
@Composable
fun TodayScreen(
    state: AppState,
    onOpen: (Route) -> Unit,
    onAddWater: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
) {
    val c = Sadora.colors

    Column(modifier) {
        GreetingHeader(
            greeting = "Xayrli tong",
            name = state.name,
            onAvatarClick = { onOpen(Route.PersonalDetails) },
            onNotificationsClick = { onOpen(Route.Notifications) },
        )

        if (isLoading) {
            TodaySkeleton()
            return@Column
        }

        if (state.isNewUser) {
            TodayEmpty(onStart = { onOpen(Route.Mind) })
            return@Column
        }

        ScreenContent {
            item {
                if (state.isPremium) {
                    AiSummaryCard(
                        body = "Kecha ${state.sleepLabel()} uxlagansiz va energiyangiz " +
                            "odatdagidan pastroq. Bugun suvni ko'proq iching va yengil " +
                            "yurishni rejalashtiring.",
                        onClick = { onOpen(Route.AiChat) },
                    )
                } else {
                    FreeSummaryCard(state, onUpgrade = { onOpen(Route.Paywall) })
                }
            }

            item { JourneyCard(state) }

            item {
                // Side-by-side cards hold different amounts of text, so the row is
                // measured to the taller one and both stretch to fill it.
                Row(
                    Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    MindCard(
                        state,
                        Modifier.weight(1f).fillMaxHeight(),
                        onOpen = { onOpen(Route.Mind) },
                    )
                    WaterCard(state, Modifier.weight(1f).fillMaxHeight(), onAdd = onAddWater)
                }
            }

            item { NutritionSummaryCard(state) }

            item {
                Row(
                    Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    ActivityCard(state, Modifier.weight(1f).fillMaxHeight())
                    SleepCard(
                        state,
                        Modifier.weight(1f).fillMaxHeight(),
                        onOpen = { onOpen(Route.Sleep) },
                    )
                }
            }

            val nextMed = state.medications.firstOrNull { it.status == MedStatus.Pending }
            if (nextMed != null) {
                item {
                    MedicationCard(
                        emoji = nextMed.emoji,
                        name = nextMed.name,
                        time = nextMed.time,
                        note = nextMed.note,
                        onTake = { state.markMedicationTaken(nextMed.id) },
                        onOpen = { onOpen(Route.Medications) },
                    )
                }
            }
        }
    }
}

/** Free-plan summary: a plain card, with the AI version offered but not nagged. */
@Composable
private fun FreeSummaryCard(state: AppState, onUpgrade: () -> Unit) {
    val c = Sadora.colors
    SadoraCard {
        CardLabel(
            "Bugungi xulosa",
            trailing = {
                Icon(
                    SadoraIcons.Clock,
                    contentDescription = null,
                    Modifier.size(IconSize.md),
                    tint = c.muted,
                )
            },
        )
        Text(
            "Sikl ${state.cycleDay}-kuni — unumdor davr. Kechki dori 20:00 da.",
            style = Sadora.type.h3,
            color = c.text,
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(
                SadoraIcons.Sparkle,
                contentDescription = null,
                Modifier.size(18.dp),
                tint = c.secondary,
            )
            Text(
                "AI shaxsiy xulosasi Premium'da",
                style = Sadora.type.body,
                color = c.muted,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Ko'rish",
                style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = c.textAccent,
                modifier = Modifier.noRippleClickable(onClick = onUpgrade),
            )
        }
    }
}

/**
 * The journey card. Cycle-based stages show a day count and prediction (always
 * badged "Taxminiy"); other stages show their own headline instead.
 */
@Composable
private fun JourneyCard(state: AppState) {
    val c = Sadora.colors
    val stage = state.lifeStage
    SadoraCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // The ring needs room for its caption; anything smaller clips "SIKL KUNI".
            ProgressRing(
                progress = state.cycleDay / state.averageCycleLength.toFloat(),
                size = 108.dp,
                strokeWidth = 10.dp,
                color = stage.palette.tint,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (stage.predictsCycle) "SIKL KUNI" else "HAFTA",
                        style = Sadora.type.caption,
                        color = c.muted,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        if (stage.predictsCycle) "${state.cycleDay}" else "${state.pregnancyWeek}",
                        style = Sadora.type.h1,
                        color = c.text,
                    )
                }
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    if (stage.predictsCycle) "Ovulyatsiya davri" else stage.title,
                    style = Sadora.type.h3,
                    color = c.text,
                )
                if (stage.predictsCycle) {
                    Text("Unumdor kunlar · 12–16-kun", style = Sadora.type.body, color = c.muted)
                    Text(
                        "Keyingi hayz — 14 kun",
                        style = Sadora.type.body,
                        color = c.muted,
                    )
                    SadoraBadge("TAXMINIY", BadgeTone.Estimated, icon = SadoraIcons.Clock)
                } else {
                    Text(stage.subtitle, style = Sadora.type.body, color = c.muted)
                }
            }
        }
    }
}

@Composable
private fun MindCard(state: AppState, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val c = Sadora.colors
    SadoraCard(modifier = modifier, padding = Spacing.sm, onClick = onOpen) {
        CardLabel("Ong")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(state.mood.emoji, style = Sadora.type.h1)
            Text(state.mood.label, style = Sadora.type.h3, color = c.text)
        }
        Text("Stress past", style = Sadora.type.body, color = c.muted)
        Text("Energiya 3/5", style = Sadora.type.body, color = c.muted)
    }
}

@Composable
private fun WaterCard(state: AppState, modifier: Modifier = Modifier, onAdd: () -> Unit) {
    val c = Sadora.colors
    SadoraCard(modifier = modifier, padding = Spacing.sm) {
        CardLabel("Suv")
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.litres(state.waterMl), style = Sadora.type.h1, color = c.accentText)
            Text(
                " / ${Fmt.litres(state.waterGoalMl)} L",
                style = Sadora.type.body,
                color = c.muted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        SadoraProgressBar(
            state.waterMl / state.waterGoalMl.toFloat(),
            color = c.accent,
            height = 6.dp,
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            // Weighted, so the button is measured first and keeps its full width; the
            // label gives up space instead of squeezing "+250" onto two lines.
            Text(
                "Yana ${state.waterGoalMl - state.waterMl} ml",
                style = Sadora.type.body,
                color = c.muted,
                modifier = Modifier.weight(1f),
            )
            PillButton("+250", onAdd)
        }
    }
}

@Composable
private fun NutritionSummaryCard(state: AppState) {
    val c = Sadora.colors
    SadoraCard {
        CardLabel("Ovqatlanish")
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.int(state.caloriesEaten), style = Sadora.type.h1, color = c.text)
            Text(
                " / ${Fmt.int(state.calorieGoal)} kkal",
                style = Sadora.type.body,
                color = c.muted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        SadoraProgressBar(state.caloriesEaten / state.calorieGoal.toFloat(), height = 6.dp)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Oqsil ${state.proteinG}g", style = Sadora.type.body, color = c.muted)
            Text("Yog' ${state.fatG}g", style = Sadora.type.body, color = c.muted)
            Text("Uglevod ${state.carbsG}g", style = Sadora.type.body, color = c.muted)
        }
    }
}

@Composable
private fun ActivityCard(state: AppState, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    SadoraCard(modifier = modifier, padding = Spacing.sm) {
        CardLabel("Faollik")
        Text(Fmt.int(state.steps), style = Sadora.type.h1, color = c.text)
        Text("qadam", style = Sadora.type.body, color = c.muted)
        SadoraBadge("Apple Watch · 12:40", BadgeTone.Neutral)
    }
}

@Composable
private fun SleepCard(state: AppState, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val c = Sadora.colors
    SadoraCard(modifier = modifier, padding = Spacing.sm, onClick = onOpen) {
        CardLabel("Uyqu")
        Text(state.sleepLabel(), style = Sadora.type.h1, color = c.text)
        Spacer(Modifier.height(2.dp))
        SadoraBadge("Oura · 07:05", BadgeTone.Neutral)
    }
}

/**
 * Next dose. Three actions are offered elsewhere; on Today only the confirming
 * action appears, and the app never tells the user what to do about a missed dose.
 */
@Composable
private fun MedicationCard(
    emoji: String,
    name: String,
    time: String,
    note: String,
    onTake: () -> Unit,
    onOpen: () -> Unit,
) {
    val c = Sadora.colors
    SadoraCard(onClick = onOpen) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(Radius.sm)).background(c.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, style = Sadora.type.h3)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = Sadora.type.h3, color = c.text)
                Text("$time · $note", style = Sadora.type.body, color = c.muted)
            }
            PillButton("Qabul qildim", onTake, tone = ButtonTone.Primary)
        }
    }
}

/**
 * Empty Today — a brand-new account with nothing logged yet.
 *
 * Shows what the screen will become rather than an apology for being blank, and
 * offers exactly one action so there is no choice to make.
 */
@Composable
private fun TodayEmpty(onStart: () -> Unit) {
    val c = Sadora.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SadoraCard {
            CardLabel("Bugungi xulosa")
            Text(
                "Hozircha ma'lumot yo'q. Birinchi belgini qo'shsangiz, bu yerda kunlik " +
                    "xulosa va grafiklar paydo bo'ladi.",
                style = Sadora.type.body,
                color = c.muted,
            )
        }

        EmptyState(
            title = "Bugundan boshlaymizmi?",
            body = "Kayfiyat, suv yoki ovqat — qaysi biridan boshlash sizga qulay bo'lsa.",
            actionText = "Birinchi belgini qo'shish",
            onAction = onStart,
        )
    }
}

/** First-load skeleton — the design's fourth Today state. */
@Composable
private fun TodaySkeleton() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Skeleton(Modifier.fillMaxWidth().height(108.dp))
        Skeleton(Modifier.fillMaxWidth().height(132.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Skeleton(Modifier.weight(1f).height(120.dp))
            Skeleton(Modifier.weight(1f).height(120.dp))
        }
        Skeleton(Modifier.fillMaxWidth().height(96.dp))
    }
}
