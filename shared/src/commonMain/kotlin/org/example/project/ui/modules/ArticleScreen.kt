package org.example.project.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.project.data.LearnController
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.DisclaimerNote
import org.example.project.ui.components.EmptyState
import org.example.project.ui.components.LockedBlock
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.Skeleton
import uz.sadora.contract.Article
import uz.sadora.contract.ArticleBlock
import org.example.project.i18n.strings

/**
 * "Maqola" — the reader.
 *
 * The screen used to be one article typed into the source; it now renders whatever the
 * server sends for this slug, block by block. A premium piece arrives with its opening
 * paragraph and nothing else, and the paywall sits where the rest of the body would have
 * been — so what is being sold is visible, but no part of it is drawn that she has not
 * bought.
 */
@Composable
fun ArticleScreen(
    slug: String,
    learn: LearnController,
    onClose: () -> Unit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.modules
    val article = learn.article(slug)

    LaunchedEffect(slug) { learn.loadArticle(slug) }

    Column(modifier) {
        SadoraTopBar("", onBack = onClose)

        ScreenContent {
            when {
                article == null && learn.busy -> item { ArticleSkeleton() }

                article == null -> item {
                    EmptyState(
                        title = t.articleFailed,
                        body = learn.error
                            ?: t.articleFailedBody,
                        actionText = null,
                        onAction = {},
                    )
                }

                else -> articleBody(article, t, onUpgrade)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.articleBody(
    article: Article,
    t: org.example.project.i18n.ModuleStrings,
    onUpgrade: () -> Unit,
) {
    val summary = article.summary

    item {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            SadoraBadge(summary.categoryLabel.uppercase(), BadgeTone.Neutral)
            SadoraBadge(t.readMinutesCaps(summary.readMinutes), BadgeTone.Neutral)
            if (summary.premium) SadoraBadge(t.premiumCaps, BadgeTone.Premium)
        }
    }

    item {
        Text(summary.title, style = Sadora.type.h1, color = Sadora.colors.text)
    }

    // Author and reviewer carry equal visual weight, and the card is skipped entirely
    // when the piece names neither rather than drawing two empty slots.
    if (article.author != null || summary.reviewedBy != null) {
        item {
            SadoraCard(padding = Spacing.sm) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    article.author?.let {
                        Byline(
                            initials = it.initials(),
                            name = it,
                            role = article.authorRole ?: t.author,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    summary.reviewedBy?.let {
                        Byline(
                            initials = it.initials(),
                            name = it,
                            role = t.reviewed,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    items(article.blocks.size) { index -> BlockView(article.blocks[index]) }

    if (article.truncated) {
        item {
            LockedBlock(t.restIsPremium, onUnlock = onUpgrade)
        }
    }

    article.disclaimer?.let { item { DisclaimerNote(it) } }
}

@Composable
private fun BlockView(block: ArticleBlock) {
    val c = Sadora.colors
    when (block) {
        is ArticleBlock.Heading -> Text(block.text, style = Sadora.type.h2, color = c.text)

        is ArticleBlock.Paragraph -> Text(block.text, style = Sadora.type.body, color = c.text)

        is ArticleBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            block.items.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("•", style = Sadora.type.body, color = c.textAccent)
                    Text(line, style = Sadora.type.body, color = c.text)
                }
            }
        }

        is ArticleBlock.Note -> SadoraCard(padding = Spacing.sm) {
            Text(block.text, style = Sadora.type.body, color = c.text)
        }
    }
}

/** "Nilufar Karimova" → "NK". A single-word name keeps one letter rather than repeating it. */
private fun String.initials(): String =
    split(' ')
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")

@Composable
private fun Byline(
    initials: String,
    name: String,
    role: String,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(Radius.chip)
                .background(c.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials, style = Sadora.type.caption, color = c.secondary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Both are clipped rather than wrapped — two bylines share the row — so they
            // end in an ellipsis instead of a half-word or a dangling separator.
            Text(
                name,
                style = Sadora.type.body,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                role,
                style = Sadora.type.caption,
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ArticleSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Skeleton(Modifier.fillMaxWidth().height(40.dp))
        Skeleton(Modifier.fillMaxWidth().height(96.dp))
        Skeleton(Modifier.fillMaxWidth().height(160.dp))
    }
}
