package org.example.project.ui.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.project.data.InsightsController
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.i18n.ModuleStrings
import org.example.project.i18n.strings
import org.example.project.model.AppState
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.DisclaimerNote
import org.example.project.ui.components.EmptyState
import org.example.project.ui.components.LockedBlock
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.SegmentedControl
import org.example.project.ui.components.Skeleton
import org.example.project.ui.components.TrendBars
import uz.sadora.contract.InsightsSummary
import uz.sadora.contract.MetricTrend
import uz.sadora.contract.TrendMetric
import org.example.project.data.readable

/** The three windows, in the order the chips draw them. */
private val windows = listOf(7, 30, 90)

/**
 * "Tahlillar" — trends and the observations drawn from them.
 *
 * Every number here is measured. The screen used to draw a fixed "+12 daqiqa" and a
 * seven-bar chart of constants, which is exactly what the product forbids elsewhere;
 * now a window with nothing in it says so, a metric with no data draws no line, and a
 * change against the previous window appears only when both windows have something in
 * them.
 *
 * Free accounts get seven days; the longer windows and the observations are Premium, and
 * both locks are the server's — the chips only draw what it decided.
 */
@Composable
fun InsightsScreen(
    state: AppState,
    insights: InsightsController,
    onClose: () -> Unit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.modules
    val c = Sadora.colors
    var range by remember { mutableStateOf(0) }
    val locked = if (state.isPremium) emptySet() else setOf(1, 2)
    val days = windows[range]
    val summary = insights.summary(days)

    LaunchedEffect(days) { insights.load(days) }

    Column(modifier) {
        SadoraTopBar(t.insightsTitle, onBack = onClose)

        ScreenContent {
            item {
                SegmentedControl(
                    options = listOf("7 kun", "30 kun", "90 kun"),
                    selectedIndex = range,
                    onSelect = { range = it },
                    lockedIndices = locked,
                )
            }

            when {
                summary == null && insights.busy -> item { InsightsSkeleton() }

                summary == null && insights.lockedWindow == days -> item {
                    LockedBlock(t.windowPremium, onUnlock = onUpgrade)
                }

                summary == null -> item {
                    EmptyState(
                        title = t.insightsEmptyTitle,
                        body = insights.error?.readable()
                            ?: t.loadFailed,
                        actionText = null,
                        onAction = {},
                    )
                }

                summary.isEmpty -> item {
                    EmptyState(
                        title = t.noRecordsInWindow,
                        body = t.noRecordsBody,
                        actionText = null,
                        onAction = {},
                    )
                }

                else -> insightsContent(summary, onUpgrade, t)
            }
        }
    }
}

/** The body, once there is something measured to draw. */
private fun LazyListScope.insightsContent(
    summary: InsightsSummary,
    onUpgrade: () -> Unit,
    t: ModuleStrings,
) {
    val charted = listOf(
        TrendMetric.SLEEP_MINUTES to t.sleepTrend,
        TrendMetric.STEPS to t.activityTrend,
        TrendMetric.MOOD to t.moodTrend,
    ).mapNotNull { (metric, title) ->
        summary.trend(metric)?.takeIf { it.hasData }?.let { title to it }
    }

    items(charted) { (title, trend) -> TrendCard(title, trend) }

    if (charted.isEmpty()) {
        item {
            SadoraCard {
                Text(t.notEnoughForChart, style = Sadora.type.h3, color = Sadora.colors.text)
                Text(
                    t.notEnoughForChartBody,
                    style = Sadora.type.body,
                    color = Sadora.colors.muted,
                )
            }
        }
    }

    item { CardLabel(t.correlations) }

    item {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            when {
                !summary.findingsAvailable -> LockedBlock(
                    t.correlationsPremium,
                    onUnlock = onUpgrade,
                )

                summary.findings.isEmpty() -> SadoraCard {
                    Text(
                        t.noCorrelation,
                        style = Sadora.type.h3,
                        color = Sadora.colors.text,
                    )
                    Text(
                        "Kamida sakkiz kunlik yozuv kerak, va farq sezilarli bo'lishi shart — " +
                            "aks holda hech narsa yozmaymiz.",
                        style = Sadora.type.body,
                        color = Sadora.colors.muted,
                    )
                }

                else -> summary.findings.forEach { finding ->
                    val sentence = finding.sentence(strings.modules)
                    if (sentence != null) {
                        SadoraCard {
                            Text(sentence, style = Sadora.type.body, color = Sadora.colors.text)
                            Text(
                                finding.basisLabel(strings.modules),
                                style = Sadora.type.body,
                                color = Sadora.colors.muted2,
                            )
                        }
                    }
                }
            }
        }
    }

    item { DisclaimerNote(strings.modules.correlationDisclaimer) }
}

@Composable
private fun TrendCard(title: String, trend: MetricTrend) {
    val t = strings.modules
    val c = Sadora.colors
    val change = trend.changeLabel(strings.modules, strings.common)
    val good = trend.changeIsGood()

    SadoraCard {
        CardLabel(
            title,
            trailing = {
                if (change != null) {
                    Text(
                        change,
                        style = Sadora.type.body,
                        color = when (good) {
                            true -> c.success
                            false -> c.warning
                            null -> c.muted
                        },
                    )
                }
            },
        )
        TrendBars(
            values = trend.barValues(),
            labels = trend.barLabels(strings.dates),
            color = when (trend.metric) {
                TrendMetric.SLEEP_MINUTES -> c.accent
                TrendMetric.STEPS -> c.primary
                else -> c.secondary
            },
            highlightLast = true,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                trend.averageLabel(strings.modules, strings.common)?.let { t.average(it) } ?: t.averagePrefix,
                style = Sadora.type.body,
                color = c.muted,
            )
            Text(
                t.daysRecorded(trend.daysWithData, trend.points.size),
                style = Sadora.type.body,
                color = c.muted2,
            )
        }
        trend.rangeLabel(strings.dates)?.takeIf { trend.barLabels(strings.dates).isEmpty() }?.let {
            Text(it, style = Sadora.type.body, color = c.muted2)
        }
    }
}

@Composable
private fun InsightsSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Skeleton(Modifier.fillMaxWidth().height(168.dp))
        Skeleton(Modifier.fillMaxWidth().height(168.dp))
        Skeleton(Modifier.fillMaxWidth().height(96.dp))
    }
}
