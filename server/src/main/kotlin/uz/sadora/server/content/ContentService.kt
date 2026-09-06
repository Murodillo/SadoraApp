package uz.sadora.server.content

import kotlin.uuid.Uuid
import uz.sadora.contract.AdminArticle
import uz.sadora.contract.Article
import uz.sadora.contract.ArticleBlock
import uz.sadora.contract.ArticleCategory
import uz.sadora.contract.ArticleFeed
import uz.sadora.contract.ArticleSummary
import uz.sadora.contract.CreateArticleRequest
import uz.sadora.contract.FeatureKeys
import uz.sadora.contract.SaveArticleRequest
import uz.sadora.server.core.ConflictException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.entitlement.EntitlementService
import uz.sadora.server.user.UserRepository

/**
 * The Bilim library.
 *
 * The one rule with any subtlety in it is what "premium" withholds. A premium article is
 * listed for everyone, with its title, category and length, because that listing is part
 * of what Premium is selling; what is withheld is the body past its opening paragraph.
 * Whether a reader is past that line is decided here from `learn_premium` and never by
 * the app, which only draws the [ArticleSummary.locked] flag it is handed.
 */
class ContentService(
    private val repository: ContentRepository,
    private val users: UserRepository,
    private val entitlements: EntitlementService,
) {

    // ---------------------------------------------------------------- app

    suspend fun feed(userId: Uuid, categoryKey: String?): ArticleFeed {
        val unlocked = hasPremiumLearn(userId)
        val articles = repository.articles(publishedOnly = true, categoryKey = categoryKey)
        val labels = repository.categories().associate { it.key to it.label }

        // Counts are over the whole library rather than the filtered slice, so a chip
        // keeps its number when another chip is selected.
        val all = if (categoryKey == null) articles else repository.articles(publishedOnly = true)
        val categories = repository.categories().map { category ->
            ArticleCategory(
                key = category.key,
                label = category.label,
                count = all.count { it.categoryKey == category.key },
            )
        }

        return ArticleFeed(
            categories = categories,
            articles = articles.map { it.toSummary(labels, unlocked) },
        )
    }

    suspend fun article(userId: Uuid, slug: String): Article {
        val record = repository.article(slug, publishedOnly = true)
            ?: throw NotFoundException("Maqola topilmadi")
        val unlocked = hasPremiumLearn(userId)
        val labels = repository.categories().associate { it.key to it.label }
        val locked = record.premium && !unlocked

        return Article(
            summary = record.toSummary(labels, unlocked),
            author = record.author,
            authorRole = record.authorRole,
            blocks = if (locked) record.blocks.preview() else record.blocks,
            truncated = locked,
            disclaimer = record.disclaimer,
            updatedAt = record.updatedAt,
        )
    }

    /**
     * Reading a free article never fails on entitlements, so this asks rather than
     * requires: an operator switching `learn_premium` off must lock premium bodies, not
     * take the library away.
     */
    private suspend fun hasPremiumLearn(userId: Uuid): Boolean {
        val user = users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")
        val feature = entitlements.resolve(userId, user.timezone).feature(FeatureKeys.LEARN_PREMIUM)
        return feature?.enabled == true
    }

    // ---------------------------------------------------------------- admin

    suspend fun adminList(): List<AdminArticle> =
        repository.articles(publishedOnly = false).map { it.toAdmin() }

    suspend fun adminArticle(slug: String): AdminArticle =
        repository.article(slug, publishedOnly = false)?.toAdmin()
            ?: throw NotFoundException("Maqola topilmadi")

    suspend fun create(request: CreateArticleRequest): AdminArticle {
        val slug = request.slug.trim().lowercase()
        if (!SLUG.matches(slug)) {
            throw ValidationException("slug", "Faqat kichik harf, raqam va chiziqcha")
        }
        if (repository.exists(slug)) throw ConflictException("Bu slug band")

        val record = request.article.toRecord(slug, published = false, publishedAt = null)
        repository.create(record)
        return adminArticle(slug)
    }

    suspend fun update(slug: String, request: SaveArticleRequest): AdminArticle {
        val existing = repository.article(slug, publishedOnly = false)
            ?: throw NotFoundException("Maqola topilmadi")
        repository.update(
            request.toRecord(slug, published = existing.published, publishedAt = existing.publishedAt),
        )
        return adminArticle(slug)
    }

    suspend fun setPublished(slug: String, published: Boolean): AdminArticle {
        if (!repository.setPublished(slug, published)) throw NotFoundException("Maqola topilmadi")
        return adminArticle(slug)
    }

    suspend fun delete(slug: String) {
        if (!repository.delete(slug)) throw NotFoundException("Maqola topilmadi")
    }

    suspend fun categories(): List<ArticleCategory> {
        val counts = repository.articles(publishedOnly = false)
        return repository.categories().map {
            ArticleCategory(it.key, it.label, counts.count { article -> article.categoryKey == it.key })
        }
    }

    private suspend fun SaveArticleRequest.toRecord(
        slug: String,
        published: Boolean,
        publishedAt: kotlin.time.Instant?,
    ): ArticleRecord {
        if (title.isBlank()) throw ValidationException("title", "Sarlavha bo'sh bo'lmasin")
        if (!repository.categoryExists(categoryKey)) {
            throw ValidationException("categoryKey", "Bunday kategoriya yo'q")
        }
        return ArticleRecord(
            slug = slug,
            kind = kind,
            categoryKey = categoryKey,
            title = title.trim(),
            excerpt = excerpt.trim(),
            blocks = blocks,
            readMinutes = readMinutes ?: estimateReadMinutes(blocks),
            premium = premium,
            published = published,
            publishedAt = publishedAt,
            reviewedBy = reviewedBy?.trim()?.takeIf { it.isNotBlank() },
            author = author?.trim()?.takeIf { it.isNotBlank() },
            authorRole = authorRole?.trim()?.takeIf { it.isNotBlank() },
            disclaimer = disclaimer?.trim()?.takeIf { it.isNotBlank() },
            updatedAt = uz.sadora.server.core.now(),
        )
    }

    private companion object {
        val SLUG = Regex("^[a-z0-9][a-z0-9-]{1,80}$")
    }
}

/** The opening paragraph, which is what a locked article shows above its paywall. */
internal fun List<ArticleBlock>.preview(): List<ArticleBlock> =
    listOfNotNull(firstOrNull { it is ArticleBlock.Paragraph })

/**
 * A reading time nobody has to maintain by hand.
 *
 * 140 words a minute is deliberately slower than the usual English figure: this content
 * is Uzbek and often clinical, and a card that under-promises is the harmless direction
 * to be wrong in. Always at least a minute — "0 daqiqa" is not a length.
 */
internal fun estimateReadMinutes(blocks: List<ArticleBlock>): Int {
    val words = blocks.sumOf { block ->
        when (block) {
            is ArticleBlock.Heading -> block.text.wordCount()
            is ArticleBlock.Paragraph -> block.text.wordCount()
            is ArticleBlock.Note -> block.text.wordCount()
            is ArticleBlock.Bullets -> block.items.sumOf { it.wordCount() }
        }
    }
    return ((words + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE).coerceIn(1, 240)
}

private const val WORDS_PER_MINUTE = 140

private fun String.wordCount(): Int = split(' ', '\n', '\t').count { it.isNotBlank() }

private fun ArticleRecord.toSummary(labels: Map<String, String>, unlocked: Boolean) = ArticleSummary(
    slug = slug,
    kind = kind,
    categoryKey = categoryKey,
    categoryLabel = labels[categoryKey] ?: categoryKey,
    title = title,
    excerpt = excerpt,
    readMinutes = readMinutes,
    premium = premium,
    locked = premium && !unlocked,
    reviewedBy = reviewedBy,
    publishedAt = publishedAt,
)

private fun ArticleRecord.toAdmin() = AdminArticle(
    slug = slug,
    kind = kind,
    categoryKey = categoryKey,
    title = title,
    excerpt = excerpt,
    readMinutes = readMinutes,
    premium = premium,
    published = published,
    reviewedBy = reviewedBy,
    author = author,
    authorRole = authorRole,
    disclaimer = disclaimer,
    blocks = blocks,
    publishedAt = publishedAt,
    updatedAt = updatedAt,
)
