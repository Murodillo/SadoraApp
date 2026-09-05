package uz.sadora.server.db

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb
import uz.sadora.contract.ArticleBlock

/**
 * The Bilim library's tables.
 *
 * Editorial content, not health data: the same rows for every reader, no user column,
 * and nothing here needs the health boundary the other table files carry. It is a
 * separate file only so the content service's imports say plainly what it touches.
 */

/** [encodeDefaults] so a block's optional fields survive the round trip through JSONB. */
private val blockJson = Json { encodeDefaults = true }

object ContentCategories : Table("content_categories") {
    val key = text("key")
    val label = text("label")
    val position = integer("position")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(key)
}

object ContentArticles : Table("content_articles") {
    val slug = text("slug")
    val kind = text("kind")
    val categoryKey = text("category_key").references(ContentCategories.key)
    val title = text("title")
    val excerpt = text("excerpt")
    val body = jsonb<List<ArticleBlock>>("body", blockJson)
    val readMinutes = integer("read_minutes")
    val premium = bool("premium")
    val published = bool("published")
    val publishedAt = timestampWithTimeZone("published_at").nullable()
    val reviewedBy = text("reviewed_by").nullable()
    val author = text("author").nullable()
    val authorRole = text("author_role").nullable()
    val disclaimer = text("disclaimer").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(slug)
}
