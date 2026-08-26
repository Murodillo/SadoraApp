package org.example.project.ui.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.KnowledgeItem
import org.example.project.model.SampleData
import org.example.project.nav.Route
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.ChipFlowRow
import org.example.project.ui.components.ImagePlaceholder
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraSearchField
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.SelectChip

/**
 * "Bilim" — articles, courses and video.
 *
 * Each card states its type, category and length up front, and clinical content
 * names the professional who reviewed it.
 */
@Composable
fun KnowledgeScreen(
    state: AppState,
    onClose: () -> Unit,
    onOpen: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(SampleData.knowledgeCategories.first()) }

    val visible = SampleData.knowledge.filter { item ->
        (category == "Barchasi" || item.category.equals(category, ignoreCase = true)) &&
            (query.isBlank() || item.title.contains(query, ignoreCase = true))
    }

    Column(modifier) {
        SadoraTopBar("Bilim", onBack = onClose)

        ScreenContent {
            item {
                SadoraSearchField(query, { query = it }, placeholder = "Qidirish")
            }

            item {
                ChipFlowRow {
                    SampleData.knowledgeCategories.forEach { option ->
                        SelectChip(
                            label = option,
                            selected = option == category,
                            onClick = { category = option },
                        )
                    }
                }
            }

            items(visible.size) { index ->
                val item = visible[index]
                KnowledgeCard(item, onClick = { onOpen(Route.Article(item.title)) })
            }

            if (visible.isEmpty()) {
                item {
                    org.example.project.ui.components.EmptyState(
                        title = "Hech narsa topilmadi",
                        body = "Boshqa kalit so'z yoki kategoriya bilan urinib ko'ring.",
                        actionText = "Filtrlarni tozalash",
                        onAction = {
                            query = ""
                            category = SampleData.knowledgeCategories.first()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun KnowledgeCard(item: KnowledgeItem, onClick: () -> Unit) {
    val c = Sadora.colors
    SadoraCard(padding = Spacing.sm, onClick = onClick) {
        ImagePlaceholder(Modifier.fillMaxWidth().aspectRatio(2.3f))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            SadoraBadge(item.kind, BadgeTone.Neutral)
            SadoraBadge(item.category, BadgeTone.Neutral)
            SadoraBadge(item.duration, BadgeTone.Neutral)
            if (item.premium) SadoraBadge("PREMIUM", BadgeTone.Premium)
        }
        Text(item.title, style = Sadora.type.h3, color = c.text)
        if (item.reviewedBy != null) {
            Text(item.reviewedBy, style = Sadora.type.body, color = c.muted)
        }
    }
}
