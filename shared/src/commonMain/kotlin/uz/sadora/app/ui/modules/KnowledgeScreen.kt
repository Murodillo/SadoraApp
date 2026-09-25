package uz.sadora.app.ui.modules

import androidx.compose.foundation.layout.Arrangement
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
import uz.sadora.app.data.LearnController
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.nav.Route
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.EmptyState
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraSearchField
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.ui.components.Skeleton
import uz.sadora.contract.ArticleKind
import uz.sadora.contract.ArticleSummary
import uz.sadora.app.data.readable

/**
 * The "everything" filter: a key that no server category can collide with. Its label
 * comes from the strings — the key itself used to double as the chip's text, in Uzbek
 * whatever language the app was in.
 */
private const val AllCategories = "*"

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
    val t = strings.modules
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
        SadoraTopBar(t.knowledgeTitle, onBack = onClose)

        ScreenContent {
            item {
                SadoraSearchField(query, { query = it }, placeholder = t.search)
            }

            if (feed != null && feed.categories.isNotEmpty()) {
                item {
                    ChipFlowRow {
                        SelectChip(
                            label = strings.journey.filterAll,
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
                        title = t.libraryFailed,
                        body = learn.error?.readable()
                            ?: t.loadFailed,
                        actionText = null,
                        onAction = {},
                    )
                }

                feed.articles.isEmpty() -> item {
                    EmptyState(
                        title = t.libraryEmpty,
                        body = t.libraryEmptyBody,
                        actionText = null,
                        onAction = {},
                    )
                }

                visible.isEmpty() -> item {
                    EmptyState(
                        title = t.nothingFound,
                        body = t.nothingFoundBody,
                        actionText = t.clearFilters,
                        onAction = {
                            query = ""
                            category = AllCategories
                        },
                    )
                }

                else -> items(visible.size) { index ->
                    val article = visible[index]
                    KnowledgeCard(article, onClick = { onOpen(Route.Article(article.slug)) })
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
    val t = strings.modules
    val c = Sadora.colors
    SadoraCard(padding = Spacing.sm, onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            SadoraBadge(t.articleKind(article.kind), BadgeTone.Neutral)
            SadoraBadge(article.categoryLabel.uppercase(), BadgeTone.Neutral)
            SadoraBadge(t.readMinutes(article.readMinutes), BadgeTone.Neutral)
            if (article.premium) SadoraBadge(t.premiumCapsBadge, BadgeTone.Premium)
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

@Composable
private fun KnowledgeSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        repeat(3) { Skeleton(Modifier.fillMaxWidth().height(132.dp)) }
    }
}
