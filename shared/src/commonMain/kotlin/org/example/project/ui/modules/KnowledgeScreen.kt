package org.example.project.ui.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.project.data.LearnController
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.nav.Route
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.ChipFlowRow
import org.example.project.ui.components.EmptyState
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraSearchField
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.SelectChip
import org.example.project.ui.components.Skeleton
import org.example.project.ui.components.appearFromBelow
import uz.sadora.contract.ArticleKind
import uz.sadora.contract.ArticleSummary

/** Drawn first, and the only category the app names itself — the rest are the server's. */
private const val AllCategories = "Barchasi"

/**
 * "Bilim" — the library.
 *
 * Every card here is a row an editor wrote in the admin panel: the app carries no
 * articles of its own any more, so a library with nothing published says so rather than
 * showing three examples that were compiled in. Search and the category chips filter what
 * arrived; a premium piece is listed for everyone and marked, because being able to see
 * what Premium holds is the point of listing it.
 */
@Composable
fun KnowledgeScreen(
    state: AppState,
    learn: LearnController,
    onClose: () -> Unit,
    onOpen: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(AllCategories) }
    val feed = learn.feed

    LaunchedEffect(Unit) { learn.loadFeed() }

    val visible = feed?.articles.orEmpty().filter { article ->
        (category == AllCategories || article.categoryKey == category) &&
            (query.isBlank() || article.matches(query))
    }

    Column(modifier) {
        SadoraTopBar("Bilim", onBack = onClose)

        ScreenContent {
            item {
                SadoraSearchField(query, { query = it }, placeholder = "Qidirish")
            }

            if (feed != null && feed.categories.isNotEmpty()) {
                item {
                    ChipFlowRow {
                        SelectChip(
                            label = AllCategories,
                            selected = category == AllCategories,
                            onClick = { category = AllCategories },
                        )
                        // A category with nothing published in it would be a chip that
                        // only ever leads to an empty list.
                        feed.categories.filter { it.count > 0 }.forEach { option ->
                            SelectChip(
                                label = option.label,
                                selected = option.key == category,
                                onClick = { category = option.key },
                            )
                        }
                    }
                }
            }

            when {
                feed == null && learn.busy -> item { KnowledgeSkeleton() }

                feed == null -> item {
                    EmptyState(
                        title = "Kutubxona ochilmadi",
                        body = learn.error
                            ?: "Ma'lumotlar yuklanmadi. Internetni tekshirib, qayta urinib ko'ring.",
                        actionText = null,
                        onAction = {},
                    )
                }

                feed.articles.isEmpty() -> item {
                    EmptyState(
                        title = "Kutubxona hozircha bo'sh",
                        body = "Yangi maqolalar chiqqanda shu yerda paydo bo'ladi.",
                        actionText = null,
                        onAction = {},
                    )
                }

                visible.isEmpty() -> item {
                    EmptyState(
                        title = "Hech narsa topilmadi",
                        body = "Boshqa kalit so'z yoki kategoriya bilan urinib ko'ring.",
                        actionText = "Filtrlarni tozalash",
                        onAction = {
                            query = ""
                            category = AllCategories
                        },
                    )
                }

                else -> items(visible.size) { index ->
                    val article = visible[index]
                    Box(Modifier.appearFromBelow(index)) {
                        KnowledgeCard(article, onClick = { onOpen(Route.Article(article.slug)) })
                    }
                }
            }
        }
    }
}

/** Title and excerpt both, so a search finds a piece by what it is about. */
private fun ArticleSummary.matches(query: String): Boolean =
    title.contains(query, ignoreCase = true) || excerpt.contains(query, ignoreCase = true)

@Composable
private fun KnowledgeCard(article: ArticleSummary, onClick: () -> Unit) {
    val c = Sadora.colors
    SadoraCard(padding = Spacing.sm, onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            SadoraBadge(article.kind.label(), BadgeTone.Neutral)
            SadoraBadge(article.categoryLabel.uppercase(), BadgeTone.Neutral)
            SadoraBadge("${article.readMinutes} DAQIQA", BadgeTone.Neutral)
            if (article.premium) SadoraBadge("PREMIUM", BadgeTone.Premium)
        }
        Text(article.title, style = Sadora.type.h3, color = c.text)
        if (article.excerpt.isNotBlank()) {
            Text(
                article.excerpt,
                style = Sadora.type.body,
                color = c.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        article.reviewedBy?.let {
            Text(it, style = Sadora.type.caption, color = c.muted2)
        }
    }
}

internal fun ArticleKind.label(): String = when (this) {
    ArticleKind.ARTICLE -> "MAQOLA"
    ArticleKind.COURSE -> "KURS"
    ArticleKind.VIDEO -> "VIDEO"
}

@Composable
private fun KnowledgeSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        repeat(3) { Skeleton(Modifier.fillMaxWidth().height(132.dp)) }
    }
}
