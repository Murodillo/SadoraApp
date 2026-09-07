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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import kotlin.time.Clock
import org.example.project.data.HealthController
import org.example.project.i18n.strings
import org.example.project.model.Fmt
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.ChipFlowRow
import org.example.project.ui.components.DisclaimerNote
import org.example.project.ui.components.EmptyState
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import uz.sadora.contract.ProviderStatus

/**
 * "Ma'lumot manbalari" — connected devices and services.
 *
 * Status, device, sync time and granted permissions are all visible per source,
 * and expired authorisations are surfaced rather than failing silently.
 */
@Composable
fun DataSourcesScreen(
    health: HealthController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.modules
    val dates = strings.dates
    val sources = health.sources
    val connected = sources.count { it.connected }
    val lastSync = sources.mapNotNull { it.lastSampleAt }.maxOrNull()

    LaunchedEffect(Unit) { health.loadSources() }

    Column(modifier) {
        SadoraTopBar(t.sourcesTitle, onBack = onClose)

        ScreenContent {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(Radius.cardSmall)
                        .background(c.success.copy(alpha = 0.14f))
                        .padding(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text("✓", style = Sadora.type.h3, color = c.success)
                    Column {
                        Text(
                            t.sourcesConnected(connected),
                            style = Sadora.type.h3,
                            color = c.success,
                        )
                        Text(
                            // The real age of the newest sample, or nothing — a fixed
                            // "12:40" told everyone their watch had just synced.
                            lastSync?.let { t.lastSample(dates.ago(it, Clock.System.now())) }
                                ?: t.noSampleYet,
                            style = Sadora.type.body,
                            color = c.muted,
                        )
                    }
                }
            }

            if (sources.isEmpty()) {
                item {
                    EmptyState(
                        title = t.sourcesEmpty,
                        body = t.sourcesEmptyBody,
                        actionText = null,
                        onAction = {},
                        glyph = "⌚",
                    )
                }
            }

            items(sources.size) { index ->
                SourceCard(sources[index])
            }

            item {
                DisclaimerNote(t.sourcesNote)
            }
        }
    }
}

@Composable
private fun SourceCard(source: ProviderStatus) {
    val c = Sadora.colors
    val t = strings.modules
    val dates = strings.dates
    val name = source.provider.name.lowercase().split('_').joinToString(" ") { part ->
        part.replaceFirstChar { it.uppercase() }
    }
    SadoraCard(padding = Spacing.sm) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(c.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Text(name.take(1), style = Sadora.type.h3, color = c.secondary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = Sadora.type.h3, color = c.text)
                Text(
                    listOfNotNull(
                        t.samples(Fmt.int(source.sampleCount.toInt())),
                        source.lastSampleAt?.let { dates.ago(it, Clock.System.now()) },
                    ).joinToString(" · "),
                    style = Sadora.type.body,
                    color = c.muted,
                )
            }
            if (source.connected) {
                SadoraBadge(t.connected, BadgeTone.Connected)
            } else {
                SadoraBadge(t.notConnected, BadgeTone.Neutral)
            }
        }

        if (source.metrics.isNotEmpty()) {
            ChipFlowRow(horizontalGap = Spacing.xxs, verticalGap = Spacing.xxs) {
                source.metrics.forEach { metric ->
                    SadoraBadge(t.metric(metric), BadgeTone.Neutral)
                }
            }
        }
    }
}
