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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.DataSource
import org.example.project.model.SampleData
import org.example.project.model.SourceStatus
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.ChipFlowRow
import org.example.project.ui.components.DisclaimerNote
import org.example.project.ui.components.PillButton
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent

/**
 * "Ma'lumot manbalari" — connected devices and services.
 *
 * Status, device, sync time and granted permissions are all visible per source,
 * and expired authorisations are surfaced rather than failing silently.
 */
@Composable
fun DataSourcesScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val connected = SampleData.dataSources.count { it.status == SourceStatus.Connected }

    Column(modifier) {
        SadoraTopBar("Ma'lumot manbalari", onBack = onClose)

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
                            "$connected manba ulangan",
                            style = Sadora.type.h3,
                            color = c.success,
                        )
                        Text(
                            "Oxirgi sinxronlash 12:40",
                            style = Sadora.type.body,
                            color = c.muted,
                        )
                    }
                }
            }

            items(SampleData.dataSources.size) { index ->
                SourceCard(SampleData.dataSources[index])
            }

            item {
                DisclaimerNote(
                    "Har bir ko'rsatkichda manba va vaqt belgisi ko'rsatiladi. Bir xil " +
                        "ko'rsatkich bir nechta manbadan kelsa, ustuvorlik sozlamalari qo'llanadi.",
                )
            }
        }
    }
}

@Composable
private fun SourceCard(source: DataSource) {
    val c = Sadora.colors
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
                Text(
                    source.name.take(1),
                    style = Sadora.type.h3,
                    color = c.secondary,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(source.name, style = Sadora.type.h3, color = c.text)
                val detail = listOfNotNull(source.device, source.syncedAt).joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(detail, style = Sadora.type.body, color = c.muted)
                }
            }
            when (source.status) {
                SourceStatus.Connected -> SadoraBadge("Ulangan", BadgeTone.Connected)
                SourceStatus.Expired -> SadoraBadge("Muddati tugagan", BadgeTone.Warning)
                SourceStatus.Disconnected -> SadoraBadge("Ulanmagan", BadgeTone.Neutral)
            }
        }

        if (source.metrics.isNotEmpty()) {
            ChipFlowRow(horizontalGap = Spacing.xxs, verticalGap = Spacing.xxs) {
                source.metrics.forEach { metric ->
                    SadoraBadge(metric, BadgeTone.Neutral)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            when (source.status) {
                SourceStatus.Connected -> {
                    PillButton("Ruxsatlar", {})
                    PillButton("Uzish", {})
                }
                SourceStatus.Expired -> PillButton("Qayta ulash", {}, tone = ButtonTone.Primary)
                SourceStatus.Disconnected -> PillButton("Ulash", {}, tone = ButtonTone.Primary)
            }
        }
    }
}
