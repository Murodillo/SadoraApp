package uz.sadora.server.content

import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import uz.sadora.contract.ArticleBlock
import uz.sadora.contract.ArticleKind
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.ContentArticles
import uz.sadora.server.db.ContentCategories
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

/** One article row, drafts included. What the admin side sees; the app sees less. */
data class ArticleRecord(
    val slug: String,
    val kind: ArticleKind,
    val categoryKey: String,
    val title: String,
    val excerpt: String,
    val blocks: List<ArticleBlock>,
    val readMinutes: Int,
    val premium: Boolean,
    val published: Boolean,
    val publishedAt: Instant?,
    val reviewedBy: String?,
    val author: String?,
    val authorRole: String?,
    val disclaimer: String?,
    val updatedAt: Instant,
)

data class CategoryRecord(val key: String, val label: String, val position: Int)

/**
 * Reads and writes the library.
 *
 * Every read takes [publishedOnly] explicitly rather than defaulting: the app must never
 * see a draft, the admin panel must always see one, and a default would make it possible
 * to get that wrong by omission.
 */
class ContentRepository {

    suspend fun categories(): List<CategoryRecord> = dbQuery {
        ContentCategories
            .selectAll()
            .orderBy(ContentCategories.position to SortOrder.ASC, ContentCategories.label to SortOrder.ASC)
            .map { CategoryRecord(it[ContentCategories.key], it[ContentCategories.label], it[ContentCategories.position]) }
    }

    suspend fun saveCategory(key: String, label: String, position: Int): CategoryRecord = dbQuery {
        ContentCategories.upsert(ContentCategories.key) {
            it[ContentCategories.key] = key
            it[ContentCategories.label] = label
            it[ContentCategories.position] = position
            it[createdAt] = now().toOffsetDateTime()
            it[updatedAt] = now().toOffsetDateTime()
        }
        CategoryRecord(key, label, position)
    }

    suspend fun categoryExists(key: String): Boolean = dbQuery {
        ContentCategories.selectAll().where { ContentCategories.key eq key }.limit(1).any()
    }

    suspend fun articles(publishedOnly: Boolean, categoryKey: String? = null): List<ArticleRecord> = dbQuery {
        var query = ContentArticles.selectAll()
        if (publishedOnly) query = query.andWhere { ContentArticles.published eq true }
        categoryKey?.let { key -> query = query.andWhere { ContentArticles.categoryKey eq key } }
        query
            .orderBy(
                ContentArticles.publishedAt to SortOrder.DESC_NULLS_LAST,
                ContentArticles.updatedAt to SortOrder.DESC,
            )
            .map { it.toRecord() }
    }

    suspend fun article(slug: String, publishedOnly: Boolean): ArticleRecord? = dbQuery {
        ContentArticles
            .selectAll()
            .where {
                if (publishedOnly) {
                    (ContentArticles.slug eq slug) and (ContentArticles.published eq true)
                } else {
                    ContentArticles.slug eq slug
                }
            }
            .limit(1)
            .firstOrNull()
            ?.toRecord()
    }

    suspend fun exists(slug: String): Boolean = dbQuery {
        ContentArticles.selectAll().where { ContentArticles.slug eq slug }.limit(1).any()
    }

    suspend fun create(record: ArticleRecord) = dbQuery {
        ContentArticles.insert { statement ->
            statement.write(record)
            statement[ContentArticles.slug] = record.slug
            statement[createdAt] = now().toOffsetDateTime()
        }
        Unit
    }

    suspend fun update(record: ArticleRecord) = dbQuery {
        ContentArticles.update({ ContentArticles.slug eq record.slug }) { it.write(record) }
        Unit
    }

    /**
     * Publishing stamps [ContentArticles.publishedAt] once and never again, so the
     * library's order does not shuffle every time a typo is fixed.
     */
    suspend fun setPublished(slug: String, published: Boolean): Boolean = dbQuery {
        val existing = ContentArticles
            .selectAll()
            .where { ContentArticles.slug eq slug }
            .limit(1)
            .firstOrNull()
            ?: return@dbQuery false
        val firstTime = published && existing[ContentArticles.publishedAt] == null
        ContentArticles.update({ ContentArticles.slug eq slug }) {
            it[ContentArticles.published] = published
            if (firstTime) it[publishedAt] = now().toOffsetDateTime()
            it[updatedAt] = now().toOffsetDateTime()
        }
        true
    }

    suspend fun delete(slug: String): Boolean = dbQuery {
        ContentArticles.deleteWhere { ContentArticles.slug eq slug } > 0
    }
}

private fun org.jetbrains.exposed.v1.core.statements.UpdateBuilder<*>.write(record: ArticleRecord) {
    this[ContentArticles.kind] = record.kind.dbValue()
    this[ContentArticles.categoryKey] = record.categoryKey
    this[ContentArticles.title] = record.title
    this[ContentArticles.excerpt] = record.excerpt
    this[ContentArticles.body] = record.blocks
    this[ContentArticles.readMinutes] = record.readMinutes
    this[ContentArticles.premium] = record.premium
    this[ContentArticles.published] = record.published
    this[ContentArticles.reviewedBy] = record.reviewedBy
    this[ContentArticles.author] = record.author
    this[ContentArticles.authorRole] = record.authorRole
    this[ContentArticles.disclaimer] = record.disclaimer
    this[ContentArticles.updatedAt] = now().toOffsetDateTime()
}

private fun ResultRow.toRecord() = ArticleRecord(
    slug = this[ContentArticles.slug],
    kind = enumFromDb(this[ContentArticles.kind], ArticleKind.ARTICLE),
    categoryKey = this[ContentArticles.categoryKey],
    title = this[ContentArticles.title],
    excerpt = this[ContentArticles.excerpt],
    blocks = this[ContentArticles.body],
    readMinutes = this[ContentArticles.readMinutes],
    premium = this[ContentArticles.premium],
    published = this[ContentArticles.published],
    publishedAt = this[ContentArticles.publishedAt]?.toKotlinInstant(),
    reviewedBy = this[ContentArticles.reviewedBy],
    author = this[ContentArticles.author],
    authorRole = this[ContentArticles.authorRole],
    disclaimer = this[ContentArticles.disclaimer],
    updatedAt = this[ContentArticles.updatedAt].toKotlinInstant(),
)
