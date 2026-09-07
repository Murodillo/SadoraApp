package org.example.project.ui.journey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlin.math.roundToInt
import org.example.project.data.HealthController
import org.example.project.data.InsightsController
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.i18n.strings
import org.example.project.model.AppState
import org.example.project.model.Fmt
import org.example.project.nav.Route
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.ChipFlowRow
import org.example.project.ui.components.DisclaimerNote
import org.example.project.ui.components.EmptyState
import org.example.project.ui.components.ProgressRing
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.SelectChip
import org.example.project.ui.components.StackedBar
import org.example.project.ui.components.WeeklyBars
import org.example.project.ui.modules.averageLabel
import org.example.project.ui.modules.barLabels
import org.example.project.ui.modules.barValues
import org.example.project.ui.modules.basisLabel
import org.example.project.ui.modules.sentence
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.TrendMetric
import org.example.project.model.DailySleepGoalMinutes

/** How far back the frequency view looks. Four whole weeks, so the bars are comparable. */
private const val WindowDays = 28

/**
 * "Menopauza · Simptomlar" — a four-week frequency view plus quick logging.
 *
 * Replaces prediction entirely for this stage: the value is in seeing patterns, not
 * in forecasting a cycle that no longer runs.
 *
 * Every number here is counted from her own records. A frequency view built on anything
 * else would be a picture of a pattern rather than a reading of one, which is the whole
 * thing this screen exists to give her.
 */
@Composable
fun StageSymptomsScreen(
    state: AppState,
    health: HealthController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.journey

    LaunchedEffect(Unit) {
        health.loadSymptoms(null)
        health.loadRecentLogs(WindowDays)
    }

    val logs = health.recentLogs
    // The catalogue names the keys; without it a count has nothing to be called.
    val labels = health.symptoms.associate { it.key to it.label }
    val counts = logs
        .flatMap { log -> log.symptoms.map { it.key } }
        .groupingBy { it }
        .eachCount()
        .toList()
        .sortedByDescending { it.second }

    val top = counts.firstOrNull()
    val weeks = top?.let { (key, _) -> weeklyCounts(logs, key, state.today) }

    Column(modifier) {
        SadoraTopBar(t.stageSymptomsTitle, onBack = onClose)

        ScreenContent {
            if (counts.isEmpty()) {
                item {
                    EmptyState(
                        title = t.noRecordsYet,
                        body = t.noRecordsYetBody,
                        actionText = null,
                        onAction = {},
                        glyph = "📋",
                    )
                }
            }

            if (top != null && weeks != null) {
                item {
                    SadoraCard {
                        CardLabel(
                            labels[top.first] ?: top.first,
                            trailing = {
                                Text(t.windowDays(WindowDays), style = Sadora.type.body, color = c.muted)
                            },
                        )
                        val peak = (weeks.maxOrNull() ?: 0).coerceAtLeast(1)
                        WeeklyBars(
                            values = weeks.map { it / peak.toFloat() },
                            labels = (1..4).map { t.weekNumber(it) },
                            color = c.warning,
                        )
                        Text(
                            t.recordedOnDays(WindowDays, top.second),
                            style = Sadora.type.body,
                            color = c.muted,
                        )
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel(t.logToday2)
                    ChipFlowRow {
                        labels.values.take(QuickLogChips).forEach { label ->
                            SelectChip(
                                label = label,
                                selected = label in state.symptoms,
                                onClick = { state.toggleSymptom(label) },
                            )
                        }
                    }
                }
            }

            if (counts.size > 1) {
                item {
                    SadoraCard {
                        CardLabel(t.mostFrequent)
                        counts.take(5).forEach { (key, days) ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                Text(
                                    labels[key] ?: key,
                                    style = Sadora.type.body,
                                    color = c.text,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(t.daysValue(days), style = Sadora.type.body, color = c.muted)
                            }
                        }
                    }
                }
            }

            item {
                DisclaimerNote(t.symptomsDisclaimer)
            }

            item { SadoraButton(strings.common.close, onClose) }
        }
    }
}

/**
 * How many days in each of the four weeks carried [key].
 *
 * Week four is the one ending today, so the bars read left to right as "then" to "now".
 */
private fun weeklyCounts(
    logs: List<uz.sadora.contract.DailyLog>,
    key: String,
    today: kotlinx.datetime.LocalDate,
): List<Int> = List(4) { week ->
    val end = today.minus((3 - week) * 7, DateTimeUnit.DAY)
    val start = end.minus(6, DateTimeUnit.DAY)
    logs.count { log ->
        log.date >= start && log.date <= end && log.symptoms.any { it.key == key }
    }
}

/**
 * How many chips the quick-log row offers.
 *
 * They come from the server's catalogue, so the list always matches what the counts
 * above are counting. There used to be a written-in fallback of six menopause symptoms,
 * which meant a woman tracking a cycle could be offered "issiqlik to'lqini" whenever the
 * catalogue was slow, and tapping it logged nothing the server knew about.
 */
private const val QuickLogChips = 8

/**
 * "Uyqu va kayfiyat" — the stage-level view of the two signals that move together.
 *
 * States the link as co-occurrence and appends "sabab-natija emas" so it cannot be
 * read as causal. The observation is one the insights service actually found; when it
 * has not found one, the card is absent rather than filled with a plausible sentence.
 */
@Composable
fun StageSleepMoodScreen(
    state: AppState,
    health: HealthController,
    insights: InsightsController,
    onOpen: (Route) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.journey
    val modules = strings.modules
    val stageColors = listOf(c.secondary, c.accent, c.muted2)

    LaunchedEffect(Unit) {
        health.refreshWearables()
        insights.load(7)
    }

    val today = health.wearableToday
    val minutes = today?.value(HealthMetric.SLEEP_DURATION)?.roundToInt()
    val summary = insights.summary(7)
    val mood = summary?.trend(TrendMetric.MOOD)
    val finding = summary?.findings?.firstOrNull()

    Column(modifier) {
        SadoraTopBar(t.sleepMoodTitle, onBack = onClose)

        ScreenContent {
            if (minutes == null && mood?.hasData != true) {
                item {
                    EmptyState(
                        title = t.notEnoughData,
                        body = t.notEnoughDataBody,
                        actionText = null,
                        onAction = {},
                        glyph = "🌙",
                    )
                }
            }

            if (minutes != null) {
                item {
                    SadoraCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            val goal = DailySleepGoalMinutes
                            ProgressRing(
                                progress = (minutes / goal.toFloat()).coerceIn(0f, 1f),
                                size = 112.dp,
                                strokeWidth = 11.dp,
                                color = c.accent,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "${(minutes * 100 / goal).coerceIn(0, 100)}",
                                        style = Sadora.type.data,
                                        color = c.text,
                                    )
                                    Text(t.scoreCaps, style = Sadora.type.caption, color = c.muted)
                                }
                            }
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(state.sleepLabel(minutes, strings.common::hoursMinutes), style = Sadora.type.h1, color = c.text)
                                Text(
                                    t.sleepGoal(strings.common.hoursMinutes(goal / 60, goal % 60)),
                                    style = Sadora.type.body,
                                    color = c.muted,
                                )
                            }
                        }
                        sleepStages(today, minutes, strings.modules)?.let { stages ->
                            StackedBar(
                                segments = stages.mapIndexed { index, (_, value) ->
                                    (value.toFloat() / minutes.coerceAtLeast(1)) to stageColors[index]
                                },
                                height = 12.dp,
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                stages.forEach { (name, value) ->
                                    Text(
                                        "$name $value${strings.common.minutesShort}",
                                        style = Sadora.type.body,
                                        color = c.muted,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (mood != null && mood.hasData) {
                item {
                    SadoraCard {
                        CardLabel(
                            t.moodWeek7,
                            trailing = {
                                mood.averageLabel(strings.modules, strings.common)?.let {
                                    Text(it, style = Sadora.type.body, color = c.muted)
                                }
                            },
                        )
                        WeeklyBars(
                            values = mood.barValues().map { it ?: 0f }.map { (it / 5f).coerceIn(0f, 1f) },
                            labels = mood.barLabels(strings.dates),
                            color = c.secondary,
                            highlightIndex = mood.points.lastIndex,
                        )
                    }
                }
            }

            finding?.sentence(modules)?.let { sentence ->
                item {
                    SadoraCard {
                        CardLabel(t.noticed, color = c.textAccent)
                        Text(sentence, style = Sadora.type.body, color = c.muted)
                        Text(finding.basisLabel(modules), style = Sadora.type.caption, color = c.muted2)
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SadoraCard(
                        modifier = Modifier.weight(1f),
                        padding = Spacing.sm,
                        onClick = { onOpen(Route.MindJournal) },
                    ) {
                        Text("🌬️", style = Sadora.type.h1)
                        Text(t.breathingCard, style = Sadora.type.h3, color = c.text)
                        Text(t.breathingCardNote, style = Sadora.type.body, color = c.muted)
                    }
                    SadoraCard(
                        modifier = Modifier.weight(1f),
                        padding = Spacing.sm,
                        onClick = { onOpen(Route.MindJournal) },
                    ) {
                        Text("📝", style = Sadora.type.h1)
                        Text(t.journalCard, style = Sadora.type.h3, color = c.text)
                        Text(t.journalCardNote, style = Sadora.type.body, color = c.muted)
                    }
                }
            }
        }
    }
}

/**
 * Deep, REM and light in minutes, or null when the source did not break the night down.
 *
 * Light is what the night has left after the two measured stages, the same way the Sleep
 * screen derives it — a provider reports deep and REM, not all three.
 */
private fun sleepStages(
    day: uz.sadora.contract.DailyHealth,
    totalMinutes: Int,
    modules: org.example.project.i18n.ModuleStrings,
): List<Pair<String, Int>>? {
    val deep = day.value(HealthMetric.SLEEP_DEEP)?.roundToInt() ?: return null
    val rem = day.value(HealthMetric.SLEEP_REM)?.roundToInt() ?: return null
    val light = totalMinutes - deep - rem
    if (light < 0) return null
    return listOf(modules.deep to deep, "REM" to rem, modules.light to light)
}
