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
        SadoraTopBar("Simptomlar", onBack = onClose)

        ScreenContent {
            if (counts.isEmpty()) {
                item {
                    EmptyState(
                        title = "Hali yozuv yo'q",
                        body = "Quyidan bugungi belgilarni belgilang. Bir necha kundan keyin " +
                            "shu yerda qaysi belgi qanchalik tez-tez uchrashi ko'rinadi.",
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
                            trailing = { Text("$WindowDays kun", style = Sadora.type.body, color = c.muted) },
                        )
                        val peak = (weeks.maxOrNull() ?: 0).coerceAtLeast(1)
                        WeeklyBars(
                            values = weeks.map { it / peak.toFloat() },
                            labels = listOf("1-hafta", "2-hafta", "3-hafta", "4-hafta"),
                            color = c.warning,
                        )
                        Text(
                            "$WindowDays kun ichida ${top.second} kun qayd etilgan.",
                            style = Sadora.type.body,
                            color = c.muted,
                        )
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel("Bugun qayd etish")
                    ChipFlowRow {
                        quickLogOptions(labels.values.toList()).forEach { label ->
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
                        CardLabel("Eng ko'p uchraganlar")
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
                                Text("$days kun", style = Sadora.type.body, color = c.muted)
                            }
                        }
                    }
                }
            }

            item {
                DisclaimerNote(
                    "Simptomlar ro'yxati kuzatuv uchun. Yangi yoki kuchayib borayotgan " +
                        "belgilar bo'lsa shifokor bilan maslahatlashing.",
                )
            }

            item { SadoraButton("Yopish", onClose) }
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
 * The chips offered for a quick log.
 *
 * Taken from the server's catalogue when it has arrived, so the list matches what the
 * counts above are counting; the fallback is the stage's usual six, because a chip row
 * with nothing in it teaches her the screen is broken.
 */
private fun quickLogOptions(catalogue: List<String>): List<String> =
    catalogue.take(8).ifEmpty {
        listOf(
            "Issiqlik to'lqini",
            "Tungi terlash",
            "Uyqusizlik",
            "Bo'g'im og'rig'i",
            "Quruqlik",
            "Yurak tez urishi",
        )
    }

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
        SadoraTopBar("Uyqu va kayfiyat", onBack = onClose)

        ScreenContent {
            if (minutes == null && mood?.hasData != true) {
                item {
                    EmptyState(
                        title = "Ma'lumot yetarli emas",
                        body = "Uyqu soat yoki telefondan keladi, kayfiyat esa kunlik " +
                            "check-in'dan. Bir necha kundan keyin bu yerda ikkalasi " +
                            "birga ko'rinadi.",
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
                            val goal = 480
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
                                    Text("BALL", style = Sadora.type.caption, color = c.muted)
                                }
                            }
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(state.sleepLabel(minutes, strings.common::hoursMinutes), style = Sadora.type.h1, color = c.text)
                                Text("Maqsad · 8s", style = Sadora.type.body, color = c.muted)
                            }
                        }
                        sleepStages(today, minutes)?.let { stages ->
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
                                        "$name ${value}d",
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
                            "7 kunlik kayfiyat",
                            trailing = {
                                mood.averageLabel()?.let {
                                    Text(it, style = Sadora.type.body, color = c.muted)
                                }
                            },
                        )
                        WeeklyBars(
                            values = mood.barValues().map { it ?: 0f }.map { (it / 5f).coerceIn(0f, 1f) },
                            labels = mood.barLabels(),
                            color = c.secondary,
                            highlightIndex = mood.points.lastIndex,
                        )
                    }
                }
            }

            finding?.sentence()?.let { sentence ->
                item {
                    SadoraCard {
                        CardLabel("Kuzatish", color = c.textAccent)
                        Text(sentence, style = Sadora.type.body, color = c.muted)
                        Text(finding.basisLabel(), style = Sadora.type.caption, color = c.muted2)
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
                        Text("Nafas mashqi", style = Sadora.type.h3, color = c.text)
                        Text("Uyqu oldidan · 4 daqiqa", style = Sadora.type.body, color = c.muted)
                    }
                    SadoraCard(
                        modifier = Modifier.weight(1f),
                        padding = Spacing.sm,
                        onClick = { onOpen(Route.MindJournal) },
                    ) {
                        Text("📝", style = Sadora.type.h1)
                        Text("Kundalik", style = Sadora.type.h3, color = c.text)
                        Text("Faqat siz ko'rasiz", style = Sadora.type.body, color = c.muted)
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
private fun sleepStages(day: uz.sadora.contract.DailyHealth, totalMinutes: Int): List<Pair<String, Int>>? {
    val deep = day.value(HealthMetric.SLEEP_DEEP)?.roundToInt() ?: return null
    val rem = day.value(HealthMetric.SLEEP_REM)?.roundToInt() ?: return null
    val light = totalMinutes - deep - rem
    if (light < 0) return null
    return listOf("Chuqur" to deep, "REM" to rem, "Yengil" to light)
}
