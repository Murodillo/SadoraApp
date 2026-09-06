package org.example.project.ui.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
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
import org.example.project.model.AppState
import org.example.project.model.SampleData
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
import org.example.project.ui.components.appearFromBelow
import uz.sadora.contract.InsightsSummary
import uz.sadora.contract.MetricTrend
import uz.sadora.contract.TrendMetric

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
    val c = Sadora.colors
    var range by remember { mutableStateOf(0) }
    val locked = if (state.isPremium) emptySet() else setOf(1, 2)
    val days = windows[range]
    val summary = insights.summary(days)

    LaunchedEffect(days) { insights.load(days) }

    Column(modifier) {
        SadoraTopBar("Tahlillar", onBack = onClose)

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
                    LockedBlock("Bu oraliq Premium bilan ochiladi", onUnlock = onUpgrade)
                }

                summary == null -> item {
                    EmptyState(
                        title = "Tahlillar hozircha yo'q",
                        body = insights.error
                            ?: "Ma'lumotlar yuklanmadi. Internetni tekshirib, qayta urinib ko'ring.",
                        actionText = null,
                        onAction = {},
                    )
                }

                summary.isEmpty -> item {
                    EmptyState(
                        title = "Bu oraliqda yozuv yo'q",
                        body = "Uyqu, kayfiyat, suv yoki ovqatni qayd etsangiz, trendlar shu " +
                            "yerda chiziladi. O'lchanmagan raqamni ko'rsatmaymiz.",
                        actionText = null,
                        onAction = {},
                    )
                }

                else -> insightsContent(summary, onUpgrade)
            }
        }
    }
}

/** The body, once there is something measured to draw. */
private fun LazyListScope.insightsContent(
    summary: InsightsSummary,
    onUpgrade: () -> Unit,
) {
    val charted = listOf(
        TrendMetric.SLEEP_MINUTES to "Uyqu trendi",
        TrendMetric.STEPS to "Faollik",
        TrendMetric.MOOD to "Kayfiyat",
    ).mapNotNull { (metric, title) ->
        summary.trend(metric)?.takeIf { it.hasData }?.let { title to it }
    }

    itemsIndexed(charted) { index, (title, trend) ->
        Box(Modifier.appearFromBelow(index)) { TrendCard(title, trend) }
    }

    if (charted.isEmpty()) {
        item {
            SadoraCard {
                Text("Grafik uchun ma'lumot yetarli emas", style = Sadora.type.h3, color = Sadora.colors.text)
                Text(
                    "Bu oraliqda uyqu, qadam va kayfiyat bo'yicha yozuv topilmadi.",
                    style = Sadora.type.body,
                    color = Sadora.colors.muted,
                )
            }
        }
    }

    item { CardLabel("Kuzatilgan bog'liqliklar") }

    item {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            when {
                !summary.findingsAvailable -> LockedBlock(
                    "Bog'liqliklar Premium bilan ochiladi",
                    onUnlock = onUpgrade,
                )

                summary.findings.isEmpty() -> SadoraCard {
                    Text(
                        "Bu oraliqda ishonchli bog'liqlik topilmadi.",
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
                    val sentence = finding.sentence()
                    if (sentence != null) {
                        SadoraCard {
                            Text(sentence, style = Sadora.type.body, color = Sadora.colors.text)
                            Text(
                                finding.basisLabel(),
                                style = Sadora.type.body,
                                color = Sadora.colors.muted2,
                            )
                        }
                    }
                }
            }
        }
    }

    item { DisclaimerNote(SampleData.correlationDisclaimer) }
}

@Composable
private fun TrendCard(title: String, trend: MetricTrend) {
    val c = Sadora.colors
    val change = trend.changeLabel()
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
            labels = trend.barLabels(),
            color = when (trend.metric) {
                TrendMetric.SLEEP_MINUTES -> c.accent
                TrendMetric.STEPS -> c.primary
                else -> c.secondary
            },
            highlightLast = true,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                trend.averageLabel()?.let { "O'rtacha $it" } ?: "O'rtacha — ",
                style = Sadora.type.body,
                color = c.muted,
            )
            Text(
                "${trend.daysWithData} / ${trend.points.size} kun",
                style = Sadora.type.body,
                color = c.muted2,
            )
        }
        trend.rangeLabel()?.takeIf { trend.barLabels().isEmpty() }?.let {
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
