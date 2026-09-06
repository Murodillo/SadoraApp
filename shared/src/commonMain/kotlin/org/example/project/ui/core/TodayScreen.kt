package org.example.project.ui.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import org.example.project.design.PhaseColors
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.CyclePhase
import org.example.project.model.Fmt
import org.example.project.model.LifeStage
import org.example.project.model.MedStatus
import org.example.project.model.deviceNow
import org.example.project.model.greetingFor
import org.example.project.nav.Route
import org.example.project.nav.Tab
import org.example.project.nav.aiRoute
import org.example.project.ui.components.AiSummaryCard
import org.example.project.ui.components.AnimatedNumber
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.EmojiTile
import org.example.project.ui.components.EmptyState
import org.example.project.ui.components.GreetingHeader
import org.example.project.ui.components.IconTile
import org.example.project.ui.components.PillButton
import org.example.project.ui.components.ProgressRing
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraProgressBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.Skeleton
import org.example.project.ui.components.appearFromBelow
import org.example.project.ui.components.noRippleClickable
import org.example.project.ui.components.pressable

/**
 * "Bugun" — the deck's daily companion: the assistant's read on today, the health
 * score with the four signals behind it, the life stage, and what is still to do.
 *
 * Deliberately not a catalogue of every feature: only what matters today.
 */
@Composable
fun TodayScreen(
    state: AppState,
    onOpen: (Route) -> Unit,
    onSelectTab: (Tab) -> Unit,
    onAddWater: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
) {
    Column(modifier) {
        GreetingHeader(
            greeting = "${greetingFor(deviceNow().hour)} — bugun o'zingizga g'amxo'rlik qilish uchun ajoyib kun 🌸",
            name = state.name,
            onAvatarClick = { onOpen(Route.PersonalDetails) },
            onNotificationsClick = { onOpen(Route.Notifications) },
            hasUnread = state.medications.any { it.status == MedStatus.Pending },
        )

        if (isLoading) {
            TodaySkeleton()
            return@Column
        }

        if (state.isNewUser) {
            TodayEmpty(onStart = { onSelectTab(Tab.Mind) })
            return@Column
        }

        // The deck reads top to bottom as: what the assistant makes of today, how today
        // is going, and what is still to do. The cards arrive in that order too.
        ScreenContent(stagger = false) {
            item {
                Box(Modifier.appearFromBelow(0)) {
                    if (state.isPremium) {
                        AiSummaryCard(
                            body = premiumSummary(state),
                            showPremiumBadge = true,
                            footnote = "Ma'lumotlaringiz asosida · AI tomonidan yaratilgan",
                            onClick = { onOpen(state.aiRoute()) },
                        )
                    } else {
                        AiSummaryCard(
                            body = "Salomatlik va kayfiyat haqida istalgan savolingizni bering",
                            onClick = { onOpen(state.aiRoute()) },
                        )
                    }
                }
            }

            item {
                Box(Modifier.appearFromBelow(1)) {
                    HealthScoreCard(
                        state = state,
                        onOpenMind = { onSelectTab(Tab.Mind) },
                        onAddWater = onAddWater,
                        onOpenBalance = { onOpen(Route.Balance) },
                        onOpenSleep = { onOpen(Route.Sleep) },
                    )
                }
            }

            item {
                Box(Modifier.appearFromBelow(2)) {
                    StageCard(state, onOpen = { onSelectTab(Tab.Journey) })
                }
            }

            item {
                Box(Modifier.appearFromBelow(3)) {
                    TodayPlanCard(
                        state = state,
                        onOpenMedications = { onOpen(Route.Medications) },
                        onAddWater = onAddWater,
                    )
                }
            }

            item {
                Box(Modifier.appearFromBelow(4)) {
                    QuickActions(onOpen = onOpen, onSelectTab = onSelectTab)
                }
            }

            item { Box(Modifier.appearFromBelow(5)) { RuleSummaryCard(state) } }
        }
    }
}

/**
 * The stage card at the top — "Sikl · Follikulyar faza · Kun 6 / 28" with a ring on
 * the right. Stages that do not predict a cycle show their own headline instead, and
 * a stage the server cannot predict yet says so rather than drawing a confident ring.
 */
@Composable
private fun StageCard(state: AppState, onOpen: () -> Unit) {
    val c = Sadora.colors
    val stage = state.lifeStage
    val cycle = stage.predictsCycle
    val phase = state.currentPhase()
    // Each stage counts in its own unit: a cycle in days, a pregnancy in weeks out of
    // forty, a recovery in weeks out of twelve. Reading the pregnancy week for every
    // non-cycling stage put "24-hafta" on a postpartum card that had never been asked.
    val weeks = if (stage == LifeStage.Postpartum) state.postpartumWeek else state.pregnancyWeek
    val progress = when {
        cycle -> state.cycleDay / state.averageCycleLength.coerceAtLeast(1).toFloat()
        stage == LifeStage.Postpartum -> state.postpartumWeek / 12f
        stage == LifeStage.Pregnancy -> state.pregnancyWeek / 40f
        // Perimenopause and menopause have no countdown at all; the ring stays a
        // full, quiet circle rather than implying progress toward something.
        else -> 1f
    }

    SadoraCard(onClick = onOpen) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(if (cycle) "Sikl" else stage.title, style = Sadora.type.body, color = c.muted)
                Text(
                    when {
                        // The eyebrow above already names the stage; the headline says
                        // what it is about.
                        !cycle -> stage.subtitle
                        !state.hasCyclePrediction -> "Prognoz uchun ma'lumot yetarli emas"
                        else -> phase.label
                    },
                    style = Sadora.type.h2,
                    color = c.text,
                )
                val caption = when {
                    cycle -> "Kun ${state.cycleDay} / ${state.averageCycleLength}"
                    stage == LifeStage.Pregnancy || stage == LifeStage.Postpartum -> "$weeks-hafta"
                    else -> stage.subtitle
                }
                Text(caption, style = Sadora.type.body, color = c.muted)
            }
            ProgressRing(
                progress = progress.coerceIn(0f, 1f),
                size = 64.dp,
                strokeWidth = 7.dp,
                color = if (cycle) phase.color() else stage.palette.tint,
            ) {
                IconTile(
                    if (cycle) SadoraIcons.Journey else SadoraIcons.Heart,
                    tint = if (cycle) phase.color() else stage.palette.tint,
                    size = 30.dp,
                    iconSize = 14.dp,
                )
            }
        }
        SadoraProgressBar(progress.coerceIn(0f, 1f), gradient = true, height = 6.dp)
    }
}

/** The dial colour of a phase, as the deck's legend draws it. */
internal fun CyclePhase.color() = when (this) {
    CyclePhase.Period -> PhaseColors.period
    CyclePhase.Follicular -> PhaseColors.follicular
    CyclePhase.Fertile -> PhaseColors.fertile
    CyclePhase.Luteal -> PhaseColors.luteal
}

/** "Tezkor amallar" — the four shortcuts under the grid. */
@Composable
private fun QuickActions(onOpen: (Route) -> Unit, onSelectTab: (Tab) -> Unit) {
    val c = Sadora.colors
    SadoraCard {
        Text("Tezkor amallar", style = Sadora.type.h3, color = c.text)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            QuickAction(SadoraIcons.Document, "Jurnal", Modifier.weight(1f)) { onOpen(Route.MindJournal) }
            QuickAction(SadoraIcons.Meditation, "Meditatsiya", Modifier.weight(1f)) { onSelectTab(Tab.Mind) }
            QuickAction(SadoraIcons.Wind, "Nafas", Modifier.weight(1f)) { onSelectTab(Tab.Mind) }
            QuickAction(SadoraIcons.Bell, "Eslatmalar", Modifier.weight(1f)) { onOpen(Route.Medications) }
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = Sadora.colors
    Column(
        modifier.noRippleClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconTile(icon, tint = c.primary, size = 52.dp, iconSize = 22.dp, shape = org.example.project.design.Radius.tile)
        Text(
            label,
            style = Sadora.type.caption.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
            color = c.muted,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * The free plan's summary: rule-based, and honest about being so. It reads the same
 * numbers the tiles show and says what they add up to.
 */
@Composable
private fun RuleSummaryCard(state: AppState) {
    val c = Sadora.colors
    SadoraCard {
        Text("Bugungi xulosa", style = Sadora.type.h3, color = c.text)
        Text(ruleSummary(state), style = Sadora.type.body, color = c.muted)
    }
}

/** Plain arithmetic over today's numbers — no model, no guessing. */
internal fun ruleSummary(state: AppState): String {
    val lines = mutableListOf<String>()
    if (state.lifeStage.predictsCycle && state.hasCyclePrediction) {
        lines += "Sikl ${state.cycleDay}-kuni — ${state.currentPhase().label.lowercase()}."
    }
    val remaining = state.waterRemainingMl
    lines += if (remaining > 0) "Suv: yana $remaining ml ichish kerak." else "Suv maqsadi bajarildi."
    val pending = state.medications.firstOrNull { it.status == MedStatus.Pending }
    if (pending != null) lines += "${pending.name} — ${pending.time} da."
    return lines.joinToString(" ")
}

/** What the Premium card says on Today. Stands in for the AI daily summary. */
private fun premiumSummary(state: AppState): String =
    "Kecha ${state.sleepLabel()} uxlagansiz va energiyangiz ${if (state.energy >= 4) "yaxshi" else "odatdagidan pastroq"}. " +
        "Bugun suvni ko'proq iching va yengil yurishni rejalashtiring."

/**
 * "Bugungi reja" — the deck's checklist of what today still asks for.
 *
 * Built from the doses that have not been taken and, once those are done, the water
 * that is still short of the goal. When there is nothing left the card says so rather
 * than inventing a task.
 */
@Composable
private fun TodayPlanCard(
    state: AppState,
    onOpenMedications: () -> Unit,
    onAddWater: () -> Unit,
) {
    val c = Sadora.colors
    val pending = state.medications.filter { it.status == MedStatus.Pending }
    val waterLeft = state.waterRemainingMl

    SadoraCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Bugungi reja", style = Sadora.type.h3, color = c.text)
            // "0 / 0" on an account with no medications reads as a failure rather than
            // as nothing to count.
            if (state.dosesDue > 0) {
                Text("${state.dosesTaken} / ${state.dosesDue}", style = Sadora.type.body, color = c.muted)
            }
        }

        if (pending.isEmpty() && waterLeft == 0) {
            Text("Bugungi hamma narsa bajarildi \uD83C\uDF38", style = Sadora.type.body, color = c.muted)
        } else {
            // Three is what fits without the card becoming the screen; the rest are one
            // tap away in Medications.
            pending.take(3).forEach { med ->
                PlanRow(
                    emoji = med.emoji,
                    title = med.name,
                    caption = med.note,
                    time = med.time,
                    tint = c.secondary,
                    actionText = "Qabul qildim",
                    onAction = { state.markMedicationTaken(med.id) },
                    onClick = onOpenMedications,
                )
            }

            if (waterLeft > 0) {
                PlanRow(
                    emoji = "\uD83D\uDCA7",
                    title = "Suv",
                    caption = "yana $waterLeft ml",
                    time = null,
                    tint = c.accent,
                    actionText = "+250 ml",
                    onAction = onAddWater,
                    onClick = onAddWater,
                )
            }
        }
    }
}

/** One line of the plan: what it is, when it is due, and the one action that clears it. */
@Composable
private fun PlanRow(
    emoji: String,
    title: String,
    caption: String,
    time: String?,
    tint: Color,
    actionText: String,
    onAction: () -> Unit,
    onClick: () -> Unit,
) {
    val c = Sadora.colors
    Row(
        Modifier.fillMaxWidth().noRippleClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        EmojiTile(emoji, tint = tint, size = 38.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Sadora.type.h3, color = c.text, maxLines = 1)
            Text(
                listOfNotNull(time, caption.takeIf { it.isNotBlank() }).joinToString(" \u00B7 "),
                style = Sadora.type.body,
                color = c.muted,
                maxLines = 1,
            )
        }
        PillButton(actionText, onAction, tone = ButtonTone.Primary)
    }
}

/**
 * "Salomatlik ko'rsatkichi" — the deck's score ring with the day's four signals beside it.
 *
 * The number is plain arithmetic over what the app already knows (see [healthScore]),
 * and the four tiles next to it are exactly the four inputs, so the score is never a
 * verdict the user cannot trace back to her own day.
 */
@Composable
private fun HealthScoreCard(
    state: AppState,
    onOpenMind: () -> Unit,
    onAddWater: () -> Unit,
    onOpenBalance: () -> Unit,
    onOpenSleep: () -> Unit,
) {
    val c = Sadora.colors
    val score = healthScore(state)
    val tint = when {
        score >= 80 -> c.success
        score >= 60 -> c.primary
        else -> c.warningSoft
    }

    SadoraCard {
        Text("Salomatlik ko'rsatkichi", style = Sadora.type.body, color = c.muted)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ProgressRing(
                progress = score / 100f,
                size = 104.dp,
                strokeWidth = 9.dp,
                color = tint,
                glow = true,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedNumber(score, Sadora.type.h1, c.text)
                    Text(scoreLabel(score), style = Sadora.type.body, color = c.muted, maxLines = 1)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SignalTile("Uyqu", state.sleepLabel(), Modifier.weight(1f), onClick = onOpenSleep)
                    SignalTile("Kayfiyat", state.mood.label, Modifier.weight(1f), emoji = state.mood.emoji, onClick = onOpenMind)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SignalTile("Suv", "${Fmt.litres(state.waterMl)} l", Modifier.weight(1f), onClick = onAddWater)
                    SignalTile("Qadam", Fmt.int(state.steps), Modifier.weight(1f), onClick = onOpenBalance)
                }
            }
        }
    }
}

/** One of the four small signals beside the score ring. */
@Composable
private fun SignalTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    onClick: () -> Unit,
) {
    val c = Sadora.colors
    Column(
        modifier
            .clip(Radius.cardSmall)
            .background(c.surface2)
            .pressable(onClick = onClick)
            .padding(horizontal = Spacing.xs, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            label,
            style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
            color = c.muted,
            maxLines = 1,
        )
        // These four sit two-up beside the ring, so the value is the smaller step and
        // still elides rather than clipping mid-word at a large display size.
        Text(
            if (emoji != null) "$emoji $value" else value,
            style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The score, as plain arithmetic over the four signals the card shows.
 *
 * Each signal contributes a quarter and is capped at its own goal, so overshooting one
 * cannot mask another being missed. Nothing here is a model or a medical measure — it
 * is a summary of what was logged today.
 */
internal fun healthScore(state: AppState): Int {
    fun ratio(value: Int, goal: Int): Float =
        if (goal <= 0) 0f else (value / goal.toFloat()).coerceIn(0f, 1f)

    val sleep = ratio(state.sleepMinutes, SleepGoalMinutes)
    val water = ratio(state.waterMl, state.waterGoalMl)
    val steps = ratio(state.steps, StepGoal)
    // Mood runs 1..5; a neutral day should not read as a failing quarter.
    val mood = ((state.mood.score - 1) / 4f).coerceIn(0f, 1f)
    return ((sleep + water + steps + mood) / 4f * 100f).toInt().coerceIn(0, 100)
}

private const val SleepGoalMinutes = 480
private const val StepGoal = 8000

private fun scoreLabel(score: Int): String = when {
    score >= 80 -> "Ajoyib"
    score >= 60 -> "Yaxshi"
    score >= 40 -> "O'rtacha"
    else -> "Past"
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
            Text("Bugungi xulosa", style = Sadora.type.h3, color = c.text)
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
        // The blocks are the heights the real cards settle at, in their order: the AI
        // summary, the score card, the stage card, the plan.
        Skeleton(Modifier.fillMaxWidth().height(112.dp))
        Skeleton(Modifier.fillMaxWidth().height(164.dp))
        Skeleton(Modifier.fillMaxWidth().height(120.dp))
        Skeleton(Modifier.fillMaxWidth().height(132.dp))
    }
}
