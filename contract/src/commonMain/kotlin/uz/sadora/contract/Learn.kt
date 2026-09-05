package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The Bilim library.
 *
 * An article is written in the admin panel and read in the app, so both ends speak this
 * file. The body is a list of [ArticleBlock]s rather than a blob of markup: the reader is
 * Compose on two platforms, and a closed set of blocks is what lets it lay each one out
 * properly instead of guessing at HTML.
 */

/** What the card says the piece is. */
@Serializable
enum class ArticleKind {
    @SerialName("article") ARTICLE,
    @SerialName("course") COURSE,
    @SerialName("video") VIDEO,
}

/**
 * A section of the library.
 *
 * The key is stable and the label is editorial, so a category can be renamed in the
 * admin panel without every article moving — and the app draws the server's labels
 * rather than a list of its own that would drift out of date.
 */
@Serializable
data class ArticleCategory(
    val key: String,
    val label: String,
    /** How many published articles the caller can see in it, so the chip can say so. */
    val count: Int = 0,
)

/** One piece of an article body. */
@Serializable
sealed interface ArticleBlock {
    @Serializable
    @SerialName("heading")
    data class Heading(val text: String) : ArticleBlock

    @Serializable
    @SerialName("paragraph")
    data class Paragraph(val text: String) : ArticleBlock

    @Serializable
    @SerialName("bullets")
    data class Bullets(val items: List<String>) : ArticleBlock

    /** A callout — practical advice, set apart from the argument around it. */
    @Serializable
    @SerialName("note")
    data class Note(val text: String) : ArticleBlock
}

/**
 * A card in the library.
 *
 * [locked] is the server's answer, not the app's: a premium article is listed for
 * everyone — it is part of what Premium sells — and only the body is withheld.
 */
@Serializable
data class ArticleSummary(
    val slug: String,
    val kind: ArticleKind,
    val categoryKey: String,
    val categoryLabel: String,
    val title: String,
    val excerpt: String,
    val readMinutes: Int,
    val premium: Boolean = false,
    val locked: Boolean = false,
    val reviewedBy: String? = null,
    val publishedAt: Instant? = null,
)

/**
 * The reader's view.
 *
 * A locked article still arrives with its opening paragraph in [blocks] and
 * [truncated] set, because a paywall over a blank page tells her nothing about what
 * she would be buying.
 */
@Serializable
data class Article(
    val summary: ArticleSummary,
    val author: String? = null,
    val authorRole: String? = null,
    val blocks: List<ArticleBlock> = emptyList(),
    val truncated: Boolean = false,
    /** The boundary note every clinical piece closes with. */
    val disclaimer: String? = null,
    val updatedAt: Instant? = null,
)

/** The library screen in one response: the chips and the cards behind them. */
@Serializable
data class ArticleFeed(
    val categories: List<ArticleCategory>,
    val articles: List<ArticleSummary>,
)

// ---------------------------------------------------------------- admin

/**
 * What the admin panel writes.
 *
 * [slug] is the article's identity on the wire and in every link, so it is set once at
 * creation and never edited — renaming a title must not break a link somebody saved.
 */
@Serializable
data class SaveArticleRequest(
    val kind: ArticleKind,
    val categoryKey: String,
    val title: String,
    val excerpt: String,
    val blocks: List<ArticleBlock>,
    val premium: Boolean = false,
    val reviewedBy: String? = null,
    val author: String? = null,
    val authorRole: String? = null,
    val disclaimer: String? = null,
    /** Null keeps the stored estimate; the service computes one when it is absent. */
    val readMinutes: Int? = null,
)

@Serializable
data class CreateArticleRequest(
    val slug: String,
    val article: SaveArticleRequest,
)

/** An article as the admin list shows it, drafts included. */
@Serializable
data class AdminArticle(
    val slug: String,
    val kind: ArticleKind,
    val categoryKey: String,
    val title: String,
    val excerpt: String,
    val readMinutes: Int,
    val premium: Boolean,
    val published: Boolean,
    val reviewedBy: String? = null,
    val author: String? = null,
    val authorRole: String? = null,
    val disclaimer: String? = null,
    val blocks: List<ArticleBlock> = emptyList(),
    val publishedAt: Instant? = null,
    val updatedAt: Instant? = null,
)

@Serializable
data class PublishArticleRequest(val published: Boolean)

@Serializable
data class SaveCategoryRequest(val key: String, val label: String, val position: Int = 0)
