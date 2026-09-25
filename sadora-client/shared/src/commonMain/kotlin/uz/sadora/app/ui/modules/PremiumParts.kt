package uz.sadora.app.ui.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraDivider

private data class PlanFeature(val name: String, val free: String, val premium: String)

/** The comparison rows, built per language because every row of it is read. */
@Composable
private fun planFeatures(): List<PlanFeature> = strings.modules.let {
    listOf(
        PlanFeature(it.featureCycleMood, "✓", "✓"),
        PlanFeature(it.featureFoodDiary, "✓", "✓"),
        PlanFeature(it.featureAiChat, "—", it.perDayCount(20)),
        PlanFeature(it.featureScanner, "—", it.perMonthCount(30)),
        PlanFeature(it.featureLongInsights, "—", "✓"),
    )
}

/**
 * Free against Premium, one row per feature.
 *
 * Shared by the Premium tab and the paywall so the two can never disagree about what
 * is included — the table is honest about limits (20 chats a day, 30 scans a month)
 * and states plainly that nothing on the free plan is taken away.
 */
@Composable
fun PremiumComparison(modifier: Modifier = Modifier) {
    val t = strings.modules
    val c = Sadora.colors
    SadoraCard(modifier = modifier, padding = Spacing.sm) {
        Row(Modifier.fillMaxWidth()) {
            Text(t.featureCaps, style = Sadora.type.caption, color = c.muted, modifier = Modifier.weight(1f))
            Text(t.freeCaps, style = Sadora.type.caption, color = c.muted, modifier = Modifier.width(64.dp), textAlign = TextAlign.Center)
            Text(t.premiumCapsBadge, style = Sadora.type.caption, color = c.textAccent, modifier = Modifier.width(64.dp), textAlign = TextAlign.Center)
        }
        SadoraDivider()
        planFeatures().forEach { feature ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(feature.name, style = Sadora.type.body, color = c.text, modifier = Modifier.weight(1f))
                Text(feature.free, style = Sadora.type.body, color = c.muted, modifier = Modifier.width(64.dp), textAlign = TextAlign.Center)
                Text(
                    feature.premium,
                    style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = c.text,
                    modifier = Modifier.width(64.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
