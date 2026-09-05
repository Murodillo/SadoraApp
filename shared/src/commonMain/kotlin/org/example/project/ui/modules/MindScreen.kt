package org.example.project.ui.modules

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import org.example.project.data.InsightsController
import org.example.project.model.AppState
import org.example.project.model.Fmt
import org.example.project.model.Mood
import org.example.project.model.PracticeKind
import org.example.project.ui.components.AiSummaryCard
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.Motion
import org.example.project.ui.components.ProgressRing
import org.example.project.ui.components.RoundIconButton
import org.example.project.ui.components.SadoraBottomSheet
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraProgressBar
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import uz.sadora.contract.TrendMetric
import org.example.project.ui.components.TrendBars
import org.example.project.ui.components.noRippleClickable
import org.example.project.ui.components.pressable

/** One practice the Mind tab can start. */
private data class Practice(
    val kind: PracticeKind,
    val title: String,
    val subtitle: String,
    val minutes: Int,
    val purpose: String,
)

private val breathing = Practice(PracticeKind.Breathing, "Nafas", "4-7-8", 5, "Stressni kamaytirish")
private val meditation = Practice(PracticeKind.Meditation, "Meditatsiya", "Xotirjam ong", 10, "Dam olish")

/**
 * "Ong va kayfiyat" — the deck's emotional-wellbeing screen, and now a root tab.
 *
 * Mood, stress and energy are logged in seconds and sent up as one check-in; the
 * breathing and meditation practices work without AI; and the language is
 * deliberately non-judgemental — moods are described, never scored as good or bad
 * behaviour.
 */
@Composable
fun MindScreen(
    state: AppState,
    insights: InsightsController,
    onClose: (() -> Unit)?,
    onOpenAi: () -> Unit,
    onOpenJournal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    var running by remember { mutableStateOf<Practice?>(null) }
    val moodWeek = insights.summary(7)?.trend(TrendMetric.MOOD)

    LaunchedEffect(Unit) { insights.load(7) }

    Box(modifier) {
        Column {
            SadoraTopBar("Ong va kayfiyat", onBack = onClose, centered = true)

            ScreenContent {
                item {
                    Text(
                        "Bugun · ${Fmt.dayMonth(state.today)}",
                        style = Sadora.type.body,
                        color = c.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item { MoodCard(state) }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        DialCard(
                            label = "Stress",
                            level = state.stress,
                            words = listOf("Juda past", "Past", "O'rtacha", "Yuqori", "Juda yuqori"),
                            color = c.primary,
                            modifier = Modifier.weight(1f),
                            onLevel = { state.setCheckIn(stress = it) },
                        )
                        DialCard(
                            label = "Energiya",
                            level = state.energy,
                            words = listOf("Juda past", "Past", "O'rtacha", "Yuqori", "Juda yuqori"),
                            color = c.primary,
                            modifier = Modifier.weight(1f),
                            onLevel = { state.setCheckIn(energy = it) },
                        )
                    }
                }

                item { PracticeCard(breathing, onStart = { running = breathing }) }

                item {
                    SadoraCard(onClick = onOpenJournal) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Jurnal", style = Sadora.type.h3, color = c.text)
                                Text("O'zingizni qanday his qilyapsiz?", style = Sadora.type.body, color = c.muted)
                                Text("Fikr va his-tuyg'ularingizni yozing", style = Sadora.type.body, color = c.muted2)
                            }
                            RoundIconButton(SadoraIcons.Pencil, onClick = onOpenJournal, contentDescription = "Jurnal")
                        }
                    }
                }

                item { PracticeCard(meditation, onStart = { running = meditation }) }

                // Only drawn once something has been checked in: a week of constants
                // dressed up as her own week was the version this replaces.
                if (moodWeek != null && moodWeek.hasData) {
                    item {
                        SadoraCard {
                            CardLabel(
                                "7 kunlik kayfiyat",
                                trailing = {
                                    moodWeek.averageLabel()?.let {
                                        Text("O'rtacha $it", style = Sadora.type.body, color = c.muted)
                                    }
                                },
                            )
                            TrendBars(
                                values = moodWeek.barValues(),
                                labels = moodWeek.barLabels(),
                                color = c.primary,
                                highlightLast = true,
                            )
                        }
                    }
                }

                item {
                    AiSummaryCard(
                        label = "Ong yordamchisi",
                        body = if (state.isPremium) {
                            "Kayfiyat va uyqu bog'liqliklari haqida suhbatlashing"
                        } else {
                            "Premium'da: qo'llab-quvvatlovchi suhbat — terapevt emas"
                        },
                        showPremiumBadge = true,
                        onClick = onOpenAi,
                    )
                }
            }
        }

        val active = running
        PracticeSheet(
            practice = active,
            onFinish = { seconds ->
                active?.let { state.logPractice(it.kind, seconds) }
                running = null
            },
            onDismiss = { running = null },
        )
    }
}

/** The big face, its reading, and the five faces to pick from. */
@Composable
private fun MoodCard(state: AppState) {
    val c = Sadora.colors
    SadoraCard {
        Text("Kayfiyat", style = Sadora.type.body, color = c.muted)
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(state.mood.emoji, style = TextStyle(fontSize = 64.sp))
            Text(state.mood.label, style = Sadora.type.h2, color = c.text)
            Text(state.mood.caption, style = Sadora.type.body, color = c.muted)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // Best first, as the deck orders them; each face sits on its own colour.
            Mood.entries.reversed().forEach { mood ->
                MoodFace(mood, selected = state.mood == mood, onClick = { state.setCheckIn(mood = mood) })
            }
        }
    }
}

@Composable
private fun MoodFace(mood: Mood, selected: Boolean, onClick: () -> Unit) {
    val c = Sadora.colors
    val tint = when (mood) {
        Mood.Great -> Color(0xFF4FB8FF)
        Mood.Good -> Color(0xFF5BC97E)
        Mood.Ok -> Color(0xFFFFC145)
        Mood.Low -> Color(0xFFFF9457)
        Mood.Bad -> Color(0xFFFF5C74)
    }
    // The chosen face grows into place rather than snapping, which is what makes the
    // row feel like a dial being turned instead of five separate buttons.
    val diameter by animateDpAsState(if (selected) 48.dp else 42.dp, Motion.SpringyDp, label = "mood-size")
    val fill by animateColorAsState(
        tint.copy(alpha = if (selected) 1f else 0.55f),
        tween(Motion.Standard),
        label = "mood-fill",
    )
    val dot by animateColorAsState(
        if (selected) c.primary else Color.Transparent,
        tween(Motion.Standard),
        label = "mood-dot",
    )

    Column(
        Modifier.pressable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(diameter)
                .clip(Radius.chip)
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Text(mood.emoji, style = TextStyle(fontSize = if (selected) 24.sp else 20.sp))
        }
        Box(Modifier.size(6.dp).clip(Radius.chip).background(dot))
    }
}

/**
 * Stress or energy: the word, a percent ring, and a five-step bar that is also the
 * input — tapping a step sets the level.
 */
@Composable
private fun DialCard(
    label: String,
    level: Int,
    words: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    onLevel: (Int) -> Unit,
) {
    val c = Sadora.colors
    val fraction = level / 5f
    SadoraCard(modifier = modifier, padding = Spacing.sm) {
        Text(label, style = Sadora.type.body, color = c.muted)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(words[level - 1], style = Sadora.type.h3, color = c.text, modifier = Modifier.weight(1f))
            ProgressRing(progress = fraction, size = 44.dp, strokeWidth = 5.dp, color = color) {
                Text(
                    "${(fraction * 100).toInt()}%",
                    style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                    color = c.text,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            (1..5).forEach { step ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(Radius.chip)
                        .background(if (step <= level) color else c.surface2)
                        .noRippleClickable { onLevel(step) },
                )
            }
        }
    }
}

@Composable
private fun PracticeCard(practice: Practice, onStart: () -> Unit) {
    val c = Sadora.colors
    SadoraCard(onClick = onStart) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(practice.title, style = Sadora.type.h3, color = c.text)
                Text(practice.subtitle, style = Sadora.type.body, color = c.muted)
                Text("${practice.minutes} daq • ${practice.purpose}", style = Sadora.type.body, color = c.muted2)
            }
            RoundIconButton(SadoraIcons.Play, onClick = onStart, contentDescription = "Boshlash")
        }
    }
}

/** Seconds per phase of the 4-7-8 pattern; meditation is one long "breathe" phase. */
private val breathPhases = listOf("Nafas oling" to 4, "Ushlab turing" to 7, "Chiqaring" to 8)

/**
 * The running practice, as a sheet over the tab.
 *
 * A one-second ticker drives both the phase prompt and the elapsed count; closing
 * early still logs what was done, because two minutes of breathing is two minutes
 * of breathing.
 */
@Composable
private fun PracticeSheet(
    practice: Practice?,
    onFinish: (seconds: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Sadora.colors
    // Kept mounted through the exit animation so the sheet does not blank as it closes.
    val last = remember { mutableStateOf<Practice?>(null) }
    practice?.let { last.value = it }
    val shown = last.value

    var elapsed by remember(practice) { mutableStateOf(0) }
    LaunchedEffect(practice) {
        if (practice == null) return@LaunchedEffect
        val total = practice.minutes * 60
        while (elapsed < total) {
            delay(1000)
            elapsed++
        }
        onFinish(elapsed)
    }

    SadoraBottomSheet(
        visible = practice != null,
        title = shown?.title ?: "",
        onDismiss = { if (elapsed > 0) onFinish(elapsed) else onDismiss() },
    ) {
        val isBreathing = shown?.kind == PracticeKind.Breathing
        val cycle = breathPhases.sumOf { it.second }
        val inCycle = elapsed % cycle
        var acc = 0
        val (phaseLabel, phaseLeft) = breathPhases.firstNotNullOfOrNull { (label, secs) ->
            val end = acc + secs
            val hit = inCycle < end
            val left = end - inCycle
            acc = end
            if (hit) label to left else null
        } ?: ("Nafas oling" to 4)

        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            val total = (shown?.minutes ?: 1) * 60
            ProgressRing(
                progress = elapsed / total.toFloat(),
                size = 160.dp,
                strokeWidth = 12.dp,
                color = c.primary,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (isBreathing) "$phaseLeft" else "${(total - elapsed) / 60}:${((total - elapsed) % 60).toString().padStart(2, '0')}",
                        style = Sadora.type.data,
                        color = c.text,
                    )
                    Text(
                        if (isBreathing) phaseLabel else "Xotirjam ong",
                        style = Sadora.type.body,
                        color = c.muted,
                    )
                }
            }
            Text(
                if (isBreathing) "4 soniya oling · 7 soniya ushlang · 8 soniya chiqaring" else "Ko'zingizni yuming va nafasingizni kuzating",
                style = Sadora.type.body,
                color = c.muted,
                textAlign = TextAlign.Center,
            )
            SadoraProgressBar(elapsed / total.toFloat(), gradient = true, height = 6.dp)
            SadoraButton(
                if (elapsed > 0) "Tugatish" else "Yopish",
                onClick = { if (elapsed > 0) onFinish(elapsed) else onDismiss() },
                tone = ButtonTone.Secondary,
            )
        }
    }
}
