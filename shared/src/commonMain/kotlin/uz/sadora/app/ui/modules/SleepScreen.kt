package uz.sadora.app.ui.modules

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
import uz.sadora.app.data.HealthController
import uz.sadora.app.data.InsightsController
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.ModuleStrings
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.model.Fmt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import uz.sadora.app.ui.components.SadoraBottomSheet
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraTextField
import uz.sadora.app.ui.components.acceptDigits
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.EmptyState
import uz.sadora.app.ui.components.ProgressRing
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SettingsRow
import uz.sadora.app.ui.components.StackedBar
import uz.sadora.app.ui.components.TrendBars
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
    onToast: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val t = strings.modules
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    var showManual by remember { mutableStateOf(false) }
    val today = health.wearableToday
    val week = insights.summary(7)?.trend(TrendMetric.SLEEP_MINUTES)
    val minutes = today?.value(HealthMetric.SLEEP_DURATION)?.roundToInt()

    LaunchedEffect(Unit) {
        health.refreshWearables()
        insights.load(7)
    }

    Column(modifier) {
        SadoraTopBar(t.sleepTitle, onBack = onClose)

        ScreenContent {
            if (minutes == null && week?.hasData != true) {
                item {
                    EmptyState(
                        title = t.sleepEmptyTitle,
                        body = t.sleepEmptyBody,
                        actionText = null,
                        onAction = {},
                        glyph = "🌙",
                    )
                }
            }

            if (minutes != null) {
                item { LastNightCard(minutes, today) }
                stagesCard(today, minutes, t)?.let { item { it() } }
            }

            if (week != null && week.hasData) {
                item {
                    SadoraCard {
                        CardLabel(
                            t.sleepWeek,
                            trailing = {
                                week.averageLabel(strings.modules, strings.common)?.let {
                                    Text(t.average(it), style = Sadora.type.body, color = c.muted)
                                }
                            },
                        )
                        TrendBars(
                            values = week.barValues(),
                            labels = week.barLabels(strings.dates),
                            color = c.accent,
                            highlightLast = true,
                        )
                        Text(
                            t.daysRecorded(week.daysWithData, week.points.size),
                            style = Sadora.type.body,
                            color = c.muted2,
                        )
                    }
                }
            }

            item {
                SadoraCard(padding = Spacing.xs) {
                    SettingsRow(SadoraIcons.Pencil, t.sleepManual, showChevron = true) { showManual = true }
                }
            }
        }
    }

    ManualSleepSheet(
        visible = showManual,
        busy = health.busy,
        onDismiss = { showManual = false },
        onSave = { total ->
            scope.launch {
                if (health.logSleep(total)) {
                    showManual = false
                    onToast(t.sleepSaved)
                }
            }
        },
    )
}

/**
 * Hours and minutes for a night no device recorded.
 *
 * A sheet rather than a screen: it is two numbers, and it is answered from the sleep
 * screen without leaving it. Saved through the same path a watch uses, so the night
 * lands in the daily aggregate, the Balance ring and the doctor page alike.
 */
@Composable
private fun ManualSleepSheet(
    visible: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (minutes: Int) -> Unit,
) {
    val t = strings.modules
    val c = Sadora.colors
    var hours by remember { mutableStateOf("7") }
    var minutes by remember { mutableStateOf("30") }
    val total = (hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)
    val valid = total in 1..(16 * 60) && (minutes.toIntOrNull() ?: 0) < 60
    SadoraBottomSheet(visible = visible, title = t.sleepManual, onDismiss = onDismiss) {
        Text(t.sleepManualBody, style = Sadora.type.body, color = c.muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SadoraTextField(
                hours,
                { hours = acceptDigits(it, 2) },
                label = t.sleepHours,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            SadoraTextField(
                minutes,
                { minutes = acceptDigits(it, 2) },
                label = t.sleepMinutesLabel,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
        SadoraButton(
            if (busy) strings.common.saving else strings.common.save,
            enabled = valid && !busy,
            onClick = { onSave(total) },
        )
    }
}

/** Duration against the goal, plus the resting heart rate when the device sent one. */
@Composable
private fun LastNightCard(minutes: Int, today: DailyHealth?) {
    val t = strings.modules
    val c = Sadora.colors
    val resting = today?.value(HealthMetric.RESTING_HEART_RATE)?.roundToInt()
    val providers = today?.metrics
        ?.firstOrNull { it.metric == HealthMetric.SLEEP_DURATION }
        ?.providers
        ?.map { strings.devices.provider(it) }
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
                    Text(minutesLabel(minutes, strings.modules, strings.common), style = Sadora.type.h2, color = c.text)
                    Text(t.goalFrom(SleepGoalMinutes / 60), style = Sadora.type.body, color = c.muted)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t.lastNight, style = Sadora.type.body, color = c.muted)
                if (resting != null) {
                    Text(t.restingPulse(resting), style = Sadora.type.h3, color = c.text)
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
private fun stagesCard(today: DailyHealth?, totalMinutes: Int, t: ModuleStrings): (@Composable () -> Unit)? {
    val deep = today?.value(HealthMetric.SLEEP_DEEP)?.roundToInt() ?: return null
    val rem = today.value(HealthMetric.SLEEP_REM)?.roundToInt() ?: return null
    // A strap that reports light sleep itself is believed; otherwise light is what the
    // total has left after deep and REM.
    val light = today.value(HealthMetric.SLEEP_LIGHT)?.roundToInt() ?: (totalMinutes - deep - rem)
    val awake = today.value(HealthMetric.SLEEP_AWAKE)?.roundToInt()
    val performance = today.value(HealthMetric.SLEEP_PERFORMANCE)?.roundToInt()
    if (light < 0) return null

    return {
        val c = Sadora.colors
        val stages = listOfNotNull(
            Triple(t.deep, deep, c.primary),
            Triple("REM", rem, c.secondary),
            Triple(t.light, light, c.accent),
            awake?.takeIf { it > 0 }?.let { Triple(t.metric(HealthMetric.SLEEP_AWAKE), it, c.muted2) },
        )
        SadoraCard {
            CardLabel(
                t.stages,
                trailing = performance?.let { { Text("${t.metric(HealthMetric.SLEEP_PERFORMANCE)} $it%", style = Sadora.type.body, color = c.muted) } },
            )
            StackedBar(
                segments = stages.map { (_, value, colour) ->
                    (value.toFloat() / (totalMinutes + (awake ?: 0)).coerceAtLeast(1)) to colour
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
                        Text(minutesLabel(value, strings.modules, strings.common), style = Sadora.type.body, color = c.text)
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
