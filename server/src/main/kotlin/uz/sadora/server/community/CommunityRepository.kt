package uz.sadora.server.community

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.ReportReason
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.CommunityComments
import uz.sadora.server.db.CommunityIdentities
import uz.sadora.server.db.CommunityPostLikes
import uz.sadora.server.db.CommunityPostSaves
import uz.sadora.server.db.CommunityPosts
import uz.sadora.server.db.CommunityReports
import uz.sadora.server.db.CommunityRestrictions
import uz.sadora.server.db.ContentStatus
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

data class IdentityRecord(val userId: Uuid, val alias: String, val tint: Int)

data class PostRecord(
    val id: Uuid,
    val userId: Uuid,
    val topic: CommunityTopic,
    val body: String,
    val status: ContentStatus,
    val hiddenReason: String?,
    val createdAt: Instant,
)

data class CommentRecord(
    val id: Uuid,
    val postId: Uuid,
    val userId: Uuid,
    val body: String,
    val status: ContentStatus,
    val hiddenReason: String?,
    val createdAt: Instant,
)

/** A post as the moderation queue sees it: alias, counts, and no user id anywhere. */
data class ModerationPostRow(
    val id: Uuid,
    val alias: String,
    val tint: Int,
    val topic: CommunityTopic,
    val body: String,
    val status: ContentStatus,
    val hiddenReason: String?,
    val createdAt: Instant,
    val likeCount: Int,
    val commentCount: Int,
    val openReports: Int,
)

data class ModerationCommentRow(
    val id: Uuid,
    val postId: Uuid,
    val alias: String,
    val tint: Int,
    val body: String,
    val status: ContentStatus,
    val hiddenReason: String?,
    val createdAt: Instant,
    val openReports: Int,
)

data class ReportRecord(
    val id: Uuid,
    val postId: Uuid?,
    val commentId: Uuid?,
    val reason: ReportReason,
    val note: String?,
    val createdAt: Instant,
    val resolvedAt: Instant?,
    val resolution: String?,
    /** The reported text, so the queue can be read without a second request. */
    val excerpt: String,
    val targetStatus: ContentStatus,
)

data class RestrictionRecord(val reason: String, val until: Instant?)

data class CommunityStats(
    val postsTotal: Long,
    val postsToday: Long,
    val hiddenPosts: Long,
    val openReports: Long,
)

/**
 * Every read and write against the secret chat's tables.
 *
 * Two kinds of method live here and the difference is deliberate. The reader-facing
 * ones return [PostRecord] and [CommentRecord] with the author's id, because the
 * service needs it to mark her own posts and to enforce her limits. The moderation ones
 * return [ModerationPostRow] and friends, which carry the alias and no id: the admin
 * service is built on those alone, and [restrictAuthorOf] is how a moderator reaches an
 * author without ever being told who she is.
 */
class CommunityRepository {

    // ---------------------------------------------------------------- identity

    suspend fun identityOf(userId: Uuid): IdentityRecord? = dbQuery {
        CommunityIdentities.selectAll()
            .where { CommunityIdentities.userId eq userId }
            .singleOrNull()
            ?.toIdentity()
    }

    suspend fun aliasTaken(alias: String): Boolean = dbQuery {
        CommunityIdentities.selectAll().where { CommunityIdentities.alias eq alias }.count() > 0
    }

    suspend fun createIdentity(userId: Uuid, alias: String, tint: Int): IdentityRecord = dbQuery {
        CommunityIdentities.insert {
            it[CommunityIdentities.userId] = userId
            it[CommunityIdentities.alias] = alias
            it[CommunityIdentities.tint] = tint
            it[createdAt] = now().toOffsetDateTime()
        }
        IdentityRecord(userId, alias, tint)
    }

    private fun identitiesIn(userIds: Collection<Uuid>): Map<Uuid, IdentityRecord> {
        if (userIds.isEmpty()) return emptyMap()
        return CommunityIdentities.selectAll()
            .where { CommunityIdentities.userId inList userIds.distinct() }
            .associate { row -> row[CommunityIdentities.userId] to row.toIdentity() }
    }

    suspend fun identitiesFor(userIds: Collection<Uuid>): Map<Uuid, IdentityRecord> =
        dbQuery { identitiesIn(userIds) }

    // ---------------------------------------------------------------- posts

    /**
     * The feed: visible posts, newest first, optionally one topic, optionally only the
     * ones [viewer] saved.
     */
    suspend fun listPosts(
        viewer: Uuid,
        topic: CommunityTopic?,
        savedOnly: Boolean,
        limit: Int,
        offset: Long,
    ): Pair<List<PostRecord>, Long> = dbQuery {
        var query = CommunityPosts.selectAll()
            .where { CommunityPosts.status eq ContentStatus.VISIBLE.dbValue() }
        topic?.let { query = query.andWhere { CommunityPosts.topic eq it.dbValue() } }
        if (savedOnly) {
            val savedIds = CommunityPostSaves.select(CommunityPostSaves.postId)
                .where { CommunityPostSaves.userId eq viewer }
                .map { it[CommunityPostSaves.postId] }
            if (savedIds.isEmpty()) return@dbQuery emptyList<PostRecord>() to 0L
            query = query.andWhere { CommunityPosts.id inList savedIds }
        }
        val total = query.count()
        val page = query.orderBy(CommunityPosts.createdAt to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map { it.toPost() }
        page to total
    }

    suspend fun postById(id: Uuid): PostRecord? = dbQuery {
        CommunityPosts.selectAll().where { CommunityPosts.id eq id }.singleOrNull()?.toPost()
    }

    suspend fun insertPost(userId: Uuid, topic: CommunityTopic, body: String): PostRecord = dbQuery {
        val id = Uuid.random()
        val timestamp = now()
        CommunityPosts.insert {
            it[CommunityPosts.id] = id
            it[CommunityPosts.userId] = userId
            it[CommunityPosts.topic] = topic.dbValue()
            it[CommunityPosts.body] = body
            it[status] = ContentStatus.VISIBLE.dbValue()
            it[createdAt] = timestamp.toOffsetDateTime()
            it[updatedAt] = timestamp.toOffsetDateTime()
        }
        PostRecord(id, userId, topic, body, ContentStatus.VISIBLE, null, timestamp)
    }

    /** Only the author may delete, and the ownership check is in the WHERE clause. */
    suspend fun deleteOwnPost(userId: Uuid, id: Uuid): Boolean = dbQuery {
        CommunityPosts.deleteWhere { (CommunityPosts.id eq id) and (CommunityPosts.userId eq userId) } > 0
    }

    suspend fun postsSince(userId: Uuid, since: Instant): Long = dbQuery {
        CommunityPosts.selectAll()
            .where { (CommunityPosts.userId eq userId) and (CommunityPosts.createdAt greaterEq since.toOffsetDateTime()) }
            .count()
    }

    // ---------------------------------------------------------------- comments

    suspend fun commentsOf(postId: Uuid): List<CommentRecord> = dbQuery {
        CommunityComments.selectAll()
            .where {
                (CommunityComments.postId eq postId) and
                    (CommunityComments.status eq ContentStatus.VISIBLE.dbValue())
            }
            .orderBy(CommunityComments.createdAt to SortOrder.ASC)
            .map { it.toComment() }
    }

    suspend fun commentById(id: Uuid): CommentRecord? = dbQuery {
        CommunityComments.selectAll().where { CommunityComments.id eq id }.singleOrNull()?.toComment()
    }

    suspend fun insertComment(postId: Uuid, userId: Uuid, body: String): CommentRecord = dbQuery {
        val id = Uuid.random()
        val timestamp = now()
        CommunityComments.insert {
            it[CommunityComments.id] = id
            it[CommunityComments.postId] = postId
            it[CommunityComments.userId] = userId
            it[CommunityComments.body] = body
            it[status] = ContentStatus.VISIBLE.dbValue()
            it[createdAt] = timestamp.toOffsetDateTime()
        }
        CommentRecord(id, postId, userId, body, ContentStatus.VISIBLE, null, timestamp)
    }

    suspend fun deleteOwnComment(userId: Uuid, id: Uuid): Boolean = dbQuery {
        CommunityComments.deleteWhere {
            (CommunityComments.id eq id) and (CommunityComments.userId eq userId)
        } > 0
    }

    suspend fun commentsSince(userId: Uuid, since: Instant): Long = dbQuery {
        CommunityComments.selectAll()
            .where {
                (CommunityComments.userId eq userId) and
                    (CommunityComments.createdAt greaterEq since.toOffsetDateTime())
            }
            .count()
    }

    // ---------------------------------------------------------------- reactions

    suspend fun setLiked(postId: Uuid, userId: Uuid, liked: Boolean): Unit = dbQuery {
        if (liked) {
            CommunityPostLikes.insertIgnore {
                it[CommunityPostLikes.postId] = postId
                it[CommunityPostLikes.userId] = userId
                it[createdAt] = now().toOffsetDateTime()
            }
        } else {
            CommunityPostLikes.deleteWhere {
                (CommunityPostLikes.postId eq postId) and (CommunityPostLikes.userId eq userId)
            }
        }
    }

    suspend fun setSaved(postId: Uuid, userId: Uuid, saved: Boolean): Unit = dbQuery {
        if (saved) {
            CommunityPostSaves.insertIgnore {
                it[CommunityPostSaves.postId] = postId
                it[CommunityPostSaves.userId] = userId
                it[createdAt] = now().toOffsetDateTime()
            }
        } else {
            CommunityPostSaves.deleteWhere {
                (CommunityPostSaves.postId eq postId) and (CommunityPostSaves.userId eq userId)
            }
        }
    }

    /** Everything the feed needs about the viewer's relationship to a page of posts, in four queries. */
    suspend fun reactionsFor(viewer: Uuid, postIds: List<Uuid>): PostReactions = dbQuery {
        if (postIds.isEmpty()) return@dbQuery PostReactions()
        PostReactions(
            likeCounts = countBy(CommunityPostLikes.postId, postIds),
            commentCounts = CommunityComments
                .select(CommunityComments.postId, CommunityComments.id.count())
                .where {
                    (CommunityComments.postId inList postIds) and
                        (CommunityComments.status eq ContentStatus.VISIBLE.dbValue())
                }
                .groupBy(CommunityComments.postId)
                .associate { it[CommunityComments.postId] to it[CommunityComments.id.count()].toInt() },
            liked = CommunityPostLikes.select(CommunityPostLikes.postId)
                .where { (CommunityPostLikes.userId eq viewer) and (CommunityPostLikes.postId inList postIds) }
                .map { it[CommunityPostLikes.postId] }
                .toSet(),
            saved = CommunityPostSaves.select(CommunityPostSaves.postId)
                .where { (CommunityPostSaves.userId eq viewer) and (CommunityPostSaves.postId inList postIds) }
                .map { it[CommunityPostSaves.postId] }
                .toSet(),
        )
    }

    suspend fun likeCount(postId: Uuid): Int = dbQuery {
        CommunityPostLikes.selectAll().where { CommunityPostLikes.postId eq postId }.count().toInt()
    }

    private fun countBy(column: Column<Uuid>, ids: List<Uuid>): Map<Uuid, Int> {
        val counter = column.count()
        return column.table.select(column, counter)
            .where { column inList ids }
            .groupBy(column)
            .associate { it[column] to it[counter].toInt() }
    }

    // ---------------------------------------------------------------- reports

    /** False when this reader already reported this target — a second tap is not a second complaint. */
    suspend fun addReport(
        reporterId: Uuid,
        postId: Uuid?,
        commentId: Uuid?,
        reason: ReportReason,
        note: String?,
    ): Boolean = dbQuery {
        val duplicate = CommunityReports.selectAll()
            .where {
                (CommunityReports.reporterId eq reporterId) and
                    (if (postId != null) CommunityReports.postId eq postId else CommunityReports.commentId eq commentId)
            }
            .count() > 0
        if (duplicate) return@dbQuery false
        CommunityReports.insert {
            it[id] = Uuid.random()
            it[CommunityReports.reporterId] = reporterId
            it[CommunityReports.postId] = postId
            it[CommunityReports.commentId] = commentId
            it[CommunityReports.reason] = reason.dbValue()
            it[CommunityReports.note] = note
            it[createdAt] = now().toOffsetDateTime()
        }
        true
    }

    suspend fun openReportsFor(postId: Uuid?, commentId: Uuid?): Long = dbQuery {
        CommunityReports.selectAll()
            .where {
                CommunityReports.resolvedAt.isNull() and
                    (if (postId != null) CommunityReports.postId eq postId else CommunityReports.commentId eq commentId)
            }
            .count()
    }

    // ---------------------------------------------------------------- status

    suspend fun setPostStatus(id: Uuid, status: ContentStatus, reason: String?): Boolean = dbQuery {
        CommunityPosts.update({ CommunityPosts.id eq id }) {
            it[CommunityPosts.status] = status.dbValue()
            it[hiddenReason] = reason
            it[updatedAt] = now().toOffsetDateTime()
        } > 0
    }

    suspend fun setCommentStatus(id: Uuid, status: ContentStatus, reason: String?): Boolean = dbQuery {
        CommunityComments.update({ CommunityComments.id eq id }) {
            it[CommunityComments.status] = status.dbValue()
            it[hiddenReason] = reason
        } > 0
    }

    // ---------------------------------------------------------------- restrictions

    suspend fun restrictionOf(userId: Uuid): RestrictionRecord? = dbQuery {
        CommunityRestrictions.selectAll()
            .where { CommunityRestrictions.userId eq userId }
            .singleOrNull()
            ?.let { RestrictionRecord(it[CommunityRestrictions.reason], it[CommunityRestrictions.until]?.toKotlinInstant()) }
    }

    /**
     * Silences whoever wrote [postId]. The author is looked up and written inside the
     * query, so the caller — the admin service — never holds her id.
     */
    suspend fun restrictAuthorOf(postId: Uuid, reason: String, until: Instant?, by: Uuid): Boolean = dbQuery {
        val author = CommunityPosts.select(CommunityPosts.userId)
            .where { CommunityPosts.id eq postId }
            .singleOrNull()
            ?.get(CommunityPosts.userId)
            ?: return@dbQuery false
        CommunityRestrictions.upsert(CommunityRestrictions.userId) {
            it[userId] = author
            it[CommunityRestrictions.reason] = reason
            it[CommunityRestrictions.until] = until?.toOffsetDateTime()
            it[createdBy] = by
            it[createdAt] = now().toOffsetDateTime()
        }
        true
    }

    // ---------------------------------------------------------------- moderation reads

    suspend fun listForModeration(
        status: ContentStatus?,
        topic: CommunityTopic?,
        reportedOnly: Boolean,
        limit: Int,
        offset: Long,
    ): Pair<List<ModerationPostRow>, Long> = dbQuery {
        var query = CommunityPosts.selectAll()
        status?.let { query = query.andWhere { CommunityPosts.status eq it.dbValue() } }
        topic?.let { query = query.andWhere { CommunityPosts.topic eq it.dbValue() } }
        if (reportedOnly) {
            val reported = CommunityReports.select(CommunityReports.postId)
                .where { CommunityReports.resolvedAt.isNull() and CommunityReports.postId.isNotNull() }
                .mapNotNull { it[CommunityReports.postId] }
                .distinct()
            if (reported.isEmpty()) return@dbQuery emptyList<ModerationPostRow>() to 0L
            query = query.andWhere { CommunityPosts.id inList reported }
        }
        val total = query.count()
        val posts = query.orderBy(CommunityPosts.createdAt to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map { it.toPost() }
        if (posts.isEmpty()) return@dbQuery emptyList<ModerationPostRow>() to total

        val ids = posts.map { it.id }
        val identities = identitiesIn(posts.map { it.userId })
        val likes = countBy(CommunityPostLikes.postId, ids)
        val comments = countBy(CommunityComments.postId, ids)
        val reports = openReportCounts(CommunityReports.postId, ids)

        posts.map { post ->
            val identity = identities[post.userId]
            ModerationPostRow(
                id = post.id,
                alias = identity?.alias ?: "Anonim",
                tint = identity?.tint ?: 0,
                topic = post.topic,
                body = post.body,
                status = post.status,
                hiddenReason = post.hiddenReason,
                createdAt = post.createdAt,
                likeCount = likes[post.id] ?: 0,
                commentCount = comments[post.id] ?: 0,
                openReports = reports[post.id] ?: 0,
            )
        } to total
    }

    suspend fun commentsForModeration(postId: Uuid): List<ModerationCommentRow> = dbQuery {
        val comments = CommunityComments.selectAll()
            .where { CommunityComments.postId eq postId }
            .orderBy(CommunityComments.createdAt to SortOrder.ASC)
            .map { it.toComment() }
        if (comments.isEmpty()) return@dbQuery emptyList()
        val identities = identitiesIn(comments.map { it.userId })
        val reports = openReportCounts(CommunityReports.commentId, comments.map { it.id })
        comments.map { comment ->
            val identity = identities[comment.userId]
            ModerationCommentRow(
                id = comment.id,
                postId = comment.postId,
                alias = identity?.alias ?: "Anonim",
                tint = identity?.tint ?: 0,
                body = comment.body,
                status = comment.status,
                hiddenReason = comment.hiddenReason,
                createdAt = comment.createdAt,
                openReports = reports[comment.id] ?: 0,
            )
        }
    }

    private fun openReportCounts(column: Column<Uuid?>, ids: List<Uuid>): Map<Uuid, Int> {
        val counter = CommunityReports.id.count()
        return CommunityReports.select(column, counter)
            .where { CommunityReports.resolvedAt.isNull() and (column inList ids) }
            .groupBy(column)
            .mapNotNull { row -> row[column]?.let { it to row[counter].toInt() } }
            .toMap()
    }

    suspend fun listReports(openOnly: Boolean, limit: Int, offset: Long): Pair<List<ReportRecord>, Long> = dbQuery {
        var query = CommunityReports.selectAll()
        if (openOnly) query = query.andWhere { CommunityReports.resolvedAt.isNull() }
        val total = query.count()
        val rows = query.orderBy(CommunityReports.createdAt to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .toList()

        val postIds = rows.mapNotNull { it[CommunityReports.postId] }
        val commentIds = rows.mapNotNull { it[CommunityReports.commentId] }
        val posts = if (postIds.isEmpty()) emptyMap() else CommunityPosts.selectAll()
            .where { CommunityPosts.id inList postIds }
            .associate { it[CommunityPosts.id] to it.toPost() }
        val comments = if (commentIds.isEmpty()) emptyMap() else CommunityComments.selectAll()
            .where { CommunityComments.id inList commentIds }
            .associate { it[CommunityComments.id] to it.toComment() }

        rows.map { row ->
            val post = row[CommunityReports.postId]?.let { posts[it] }
            val comment = row[CommunityReports.commentId]?.let { comments[it] }
            ReportRecord(
                id = row[CommunityReports.id],
                postId = row[CommunityReports.postId],
                commentId = row[CommunityReports.commentId],
                reason = enumFromDb(row[CommunityReports.reason], ReportReason.OTHER),
                note = row[CommunityReports.note],
                createdAt = row[CommunityReports.createdAt].toKotlinInstant(),
                resolvedAt = row[CommunityReports.resolvedAt]?.toKotlinInstant(),
                resolution = row[CommunityReports.resolution],
                excerpt = (post?.body ?: comment?.body).orEmpty().take(EXCERPT_LENGTH),
                targetStatus = post?.status ?: comment?.status ?: ContentStatus.HIDDEN,
            )
        } to total
    }

    suspend fun reportById(id: Uuid): ReportRecord? = dbQuery {
        val row = CommunityReports.selectAll().where { CommunityReports.id eq id }.singleOrNull()
            ?: return@dbQuery null
        val post = row[CommunityReports.postId]?.let { postId ->
            CommunityPosts.selectAll().where { CommunityPosts.id eq postId }.singleOrNull()?.toPost()
        }
        val comment = row[CommunityReports.commentId]?.let { commentId ->
            CommunityComments.selectAll().where { CommunityComments.id eq commentId }.singleOrNull()?.toComment()
        }
        ReportRecord(
            id = row[CommunityReports.id],
            postId = row[CommunityReports.postId],
            commentId = row[CommunityReports.commentId],
            reason = enumFromDb(row[CommunityReports.reason], ReportReason.OTHER),
            note = row[CommunityReports.note],
            createdAt = row[CommunityReports.createdAt].toKotlinInstant(),
            resolvedAt = row[CommunityReports.resolvedAt]?.toKotlinInstant(),
            resolution = row[CommunityReports.resolution],
            excerpt = (post?.body ?: comment?.body).orEmpty().take(EXCERPT_LENGTH),
            targetStatus = post?.status ?: comment?.status ?: ContentStatus.HIDDEN,
        )
    }

    /** Resolves every open report on the same target at once, so the queue empties as one. */
    suspend fun resolveReportsOn(postId: Uuid?, commentId: Uuid?, resolution: String, by: Uuid): Int = dbQuery {
        CommunityReports.update({
            CommunityReports.resolvedAt.isNull() and
                (if (postId != null) CommunityReports.postId eq postId else CommunityReports.commentId eq commentId)
        }) {
            it[resolvedAt] = now().toOffsetDateTime()
            it[resolvedBy] = by
            it[CommunityReports.resolution] = resolution
        }
    }

    suspend fun stats(today: LocalDate): CommunityStats = dbQuery {
        val startOfDay = today.atStartOfDayIn(TimeZone.UTC).toOffsetDateTime()
        CommunityStats(
            postsTotal = CommunityPosts.selectAll().count(),
            postsToday = CommunityPosts.selectAll().where { CommunityPosts.createdAt greaterEq startOfDay }.count(),
            hiddenPosts = CommunityPosts.selectAll()
                .where { CommunityPosts.status eq ContentStatus.HIDDEN.dbValue() }
                .count(),
            openReports = CommunityReports.selectAll().where { CommunityReports.resolvedAt.isNull() }.count(),
        )
    }

    // ---------------------------------------------------------------- mapping

    private fun ResultRow.toIdentity() = IdentityRecord(
        userId = this[CommunityIdentities.userId],
        alias = this[CommunityIdentities.alias],
        tint = this[CommunityIdentities.tint],
    )

    private fun ResultRow.toPost() = PostRecord(
        id = this[CommunityPosts.id],
        userId = this[CommunityPosts.userId],
        topic = enumFromDb(this[CommunityPosts.topic], CommunityTopic.WELLBEING),
        body = this[CommunityPosts.body],
        status = enumFromDb(this[CommunityPosts.status], ContentStatus.VISIBLE),
        hiddenReason = this[CommunityPosts.hiddenReason],
        createdAt = this[CommunityPosts.createdAt].toKotlinInstant(),
    )

    private fun ResultRow.toComment() = CommentRecord(
        id = this[CommunityComments.id],
        postId = this[CommunityComments.postId],
        userId = this[CommunityComments.userId],
        body = this[CommunityComments.body],
        status = enumFromDb(this[CommunityComments.status], ContentStatus.VISIBLE),
        hiddenReason = this[CommunityComments.hiddenReason],
        createdAt = this[CommunityComments.createdAt].toKotlinInstant(),
    )

    private companion object {
        const val EXCERPT_LENGTH = 200
    }
}

data class PostReactions(
    val likeCounts: Map<Uuid, Int> = emptyMap(),
    val commentCounts: Map<Uuid, Int> = emptyMap(),
    val liked: Set<Uuid> = emptySet(),
    val saved: Set<Uuid> = emptySet(),
)
