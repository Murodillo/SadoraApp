package org.example.project.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.example.project.data.HealthController
import org.example.project.data.InsightsController
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.Fmt
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.EmptyState
import org.example.project.ui.components.ProgressRing
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.SettingsRow
import org.example.project.ui.components.StackedBar
import org.example.project.ui.components.TrendBars
import uz.sadora.contract.DailyHealth
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.TrendMetric

/** Eight hours: what the ring is drawn against, and stated as a goal rather than a score. */
private const val SleepGoalMinutes = 480

/**
 * "Uyqu" — last night, its stages, and the week.
 *
 * The screen used to show a "72 BALL" score, a fixed bedtime and a chart of constants,
 * none of which anything measured. It now draws what the wearable layer actually sent:
 * a duration against the eight-hour goal, the deep and REM minutes it reported, the
 * seven-day series with its gaps, and the name of the device the numbers came from. With
 * no data it says so and offers manual entry, which is the honest version of the same
 * screen for someone with no watch.
 */
@Composable
fun SleepScreen(
    state: AppState,
    health: HealthController,
    insights: InsightsController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val today = health.wearableToday
    val week = insights.summary(7)?.trend(TrendMetric.SLEEP_MINUTES)
    val minutes = today?.value(HealthMetric.SLEEP_DURATION)?.roundToInt()

    LaunchedEffect(Unit) {
        health.refreshWearables()
        insights.load(7)
    }

    Column(modifier) {
        SadoraTopBar("Uyqu", onBack = onClose)

        ScreenContent {
            if (minutes == null && week?.hasData != true) {
                item {
                    EmptyState(
                        title = "Uyqu ma'lumoti yo'q",
                        body = "Soat yoki telefon sinxronlanganda uyqu davomiyligi va bosqichlari " +
                            "shu yerda ko'rinadi.",
                        actionText = null,
                        onAction = {},
                        glyph = "🌙",
                    )
                }
            }

            if (minutes != null) {
                item { LastNightCard(minutes, today) }
                stagesCard(today, minutes)?.let { item { it() } }
            }

            if (week != null && week.hasData) {
                item {
                    SadoraCard {
                        CardLabel(
                            "7 kunlik davomiylik",
                            trailing = {
                                week.averageLabel()?.let {
                                    Text("O'rtacha $it", style = Sadora.type.body, color = c.muted)
                                }
                            },
                        )
                        TrendBars(
                            values = week.barValues(),
                            labels = week.barLabels(),
                            color = c.accent,
                            highlightLast = true,
                        )
                        Text(
                            "${week.daysWithData} / ${week.points.size} kun qayd etilgan",
                            style = Sadora.type.body,
                            color = c.muted2,
                        )
                    }
                }
            }

            item {
                SadoraCard(padding = Spacing.xs) {
                    SettingsRow(SadoraIcons.Pencil, "Uyquni qo'lda kiritish", showChevron = false) {}
                }
            }
        }
    }
}

/** Duration against the goal, plus the resting heart rate when the device sent one. */
@Composable
private fun LastNightCard(minutes: Int, today: DailyHealth?) {
    val c = Sadora.colors
    val resting = today?.value(HealthMetric.RESTING_HEART_RATE)?.roundToInt()
    val providers = today?.metrics
        ?.firstOrNull { it.metric == HealthMetric.SLEEP_DURATION }
        ?.providers
        ?.map { it.name.lowercase().replace('_', ' ') }
        .orEmpty()

    SadoraCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ProgressRing(
                progress = (minutes / SleepGoalMinutes.toFloat()).coerceIn(0f, 1f),
                size = 116.dp,
                strokeWidth = 11.dp,
                color = c.accent,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(minutesLabel(minutes), style = Sadora.type.h2, color = c.text)
                    Text("${SleepGoalMinutes / 60} soatdan", style = Sadora.type.body, color = c.muted)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Kecha", style = Sadora.type.body, color = c.muted)
                if (resting != null) {
                    Text("Tinch puls $resting bpm", style = Sadora.type.h3, color = c.text)
                }
                if (providers.isNotEmpty()) {
                    SadoraBadge(providers.joinToString(", "), BadgeTone.Connected)
                }
            }
        }
    }
}

/**
 * The stage breakdown, only when the device reported stages.
 *
 * Light sleep is what the total has left after deep and REM, so it is derived rather
 * than invented — and if that arithmetic goes negative the device disagreed with itself
 * and the card is not drawn at all.
 */
private fun stagesCard(today: DailyHealth?, totalMinutes: Int): (@Composable () -> Unit)? {
    val deep = today?.value(HealthMetric.SLEEP_DEEP)?.roundToInt() ?: return null
    val rem = today.value(HealthMetric.SLEEP_REM)?.roundToInt() ?: return null
    val light = totalMinutes - deep - rem
    if (light < 0) return null

    return {
        val c = Sadora.colors
        val stages = listOf(
            Triple("Chuqur", deep, c.primary),
            Triple("REM", rem, c.secondary),
            Triple("Yengil", light, c.accent),
        )
        SadoraCard {
            CardLabel("Bosqichlar")
            StackedBar(
                segments = stages.map { (_, value, colour) ->
                    (value.toFloat() / totalMinutes.coerceAtLeast(1)) to colour
                },
                height = 12.dp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                stages.forEach { (label, value, colour) ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            LegendDot(colour)
                            Text(label, style = Sadora.type.body, color = c.muted)
                        }
                        Text(minutesLabel(value), style = Sadora.type.body, color = c.text)
                    }
                }
            }
        }
    }
}

/** Small colour key next to a stage label. */
@Composable
private fun LegendDot(color: Color) {
    Box(
        Modifier
            .size(8.dp)
            .clip(Radius.chip)
            .background(color),
    )
}
