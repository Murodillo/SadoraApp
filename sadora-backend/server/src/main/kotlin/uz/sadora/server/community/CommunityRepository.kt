package uz.sadora.server.community

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.notInSubQuery
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.countDistinct
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.DoctorSpecialty
import uz.sadora.contract.DoctorStatus
import uz.sadora.contract.ReportReason
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.CommunityBlocks
import uz.sadora.server.db.CommunityComments
import uz.sadora.server.db.CommunityIdentities
import uz.sadora.server.db.CommunityMessages
import uz.sadora.server.db.CommunityPostLikes
import uz.sadora.server.db.CommunityPostSaves
import uz.sadora.server.db.CommunityPosts
import uz.sadora.server.db.CommunityReports
import uz.sadora.server.db.CommunityRestrictions
import uz.sadora.server.db.ContentStatus
import uz.sadora.server.db.DoctorProfiles
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

data class IdentityRecord(
    val userId: Uuid,
    val alias: String,
    val tint: Int,
    val createdAt: Instant,
    val bio: String? = null,
    val dmOpen: Boolean = true,
)

/** What a badge is decided from: the counts behind one alias, and when she arrived. */
data class ActivityStats(
    val posts: Int = 0,
    val comments: Int = 0,
    val likesReceived: Int = 0,
    val memberSince: Instant,
    /** Whether her alias is among the room's first [CommunityBadges.EARLY_ALIASES]. */
    val early: Boolean = false,
)

data class PostRecord(
    val id: Uuid,
    val userId: Uuid,
    val topic: CommunityTopic,
    val body: String,
    val status: ContentStatus,
    val hiddenReason: String?,
    val createdAt: Instant,
    /** The doctor profile it was written as, or null for an alias post. */
    val doctorId: Uuid? = null,
)

/** A verified doctor as a byline: the public id, the name, the specialty. */
data class DoctorByline(
    val id: Uuid,
    val userId: Uuid,
    val fullName: String,
    val specialty: DoctorSpecialty,
)

data class CommentRecord(
    val id: Uuid,
    val postId: Uuid,
    val userId: Uuid,
    val body: String,
    val status: ContentStatus,
    val hiddenReason: String?,
    val createdAt: Instant,
    val doctorId: Uuid? = null,
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
    /** Written as a verified doctor; [alias] then holds her name. */
    val byDoctor: Boolean = false,
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
    val byDoctor: Boolean = false,
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
    val messageId: Uuid? = null,
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

    /** The account behind an alias — kept inside the server; the routes speak alias only. */
    suspend fun identityByAlias(alias: String): IdentityRecord? = dbQuery {
        CommunityIdentities.selectAll()
            .where { CommunityIdentities.alias eq alias }
            .singleOrNull()
            ?.toIdentity()
    }

    suspend fun updateIdentity(userId: Uuid, bio: String?, keepBio: Boolean, dmOpen: Boolean?): Boolean = dbQuery {
        CommunityIdentities.update({ CommunityIdentities.userId eq userId }) {
            if (!keepBio) it[CommunityIdentities.bio] = bio
            dmOpen?.let { open -> it[CommunityIdentities.dmOpen] = open }
        } > 0
    }

    /**
     * The counts a badge and a profile are read from, for a batch of accounts in four
     * queries whatever the batch size — the feed asks for a page of authors at once.
     */
    suspend fun activityFor(userIds: Collection<Uuid>): Map<Uuid, ActivityStats> = dbQuery {
        val ids = userIds.distinct()
        if (ids.isEmpty()) return@dbQuery emptyMap()
        val identities = identitiesIn(ids)
        val visible = ContentStatus.VISIBLE.dbValue()

        val postCounter = CommunityPosts.id.count()
        val posts = CommunityPosts.select(CommunityPosts.userId, postCounter)
            .where { (CommunityPosts.userId inList ids) and (CommunityPosts.status eq visible) and CommunityPosts.doctorId.isNull() }
            .groupBy(CommunityPosts.userId)
            .associate { it[CommunityPosts.userId] to it[postCounter].toInt() }

        val commentCounter = CommunityComments.id.count()
        val comments = CommunityComments.select(CommunityComments.userId, commentCounter)
            .where { (CommunityComments.userId inList ids) and (CommunityComments.status eq visible) and CommunityComments.doctorId.isNull() }
            .groupBy(CommunityComments.userId)
            .associate { it[CommunityComments.userId] to it[commentCounter].toInt() }

        val likeCounter = CommunityPostLikes.userId.count()
        val likes = (CommunityPostLikes innerJoin CommunityPosts)
            .select(CommunityPosts.userId, likeCounter)
            .where { (CommunityPosts.userId inList ids) and (CommunityPosts.status eq visible) and CommunityPosts.doctorId.isNull() }
            .groupBy(CommunityPosts.userId)
            .associate { it[CommunityPosts.userId] to it[likeCounter].toInt() }

        // The moment the room stopped being new: whoever arrived before it is early.
        val earlyUntil = CommunityIdentities.select(CommunityIdentities.createdAt)
            .orderBy(CommunityIdentities.createdAt to SortOrder.ASC)
            .limit(1)
            .offset((CommunityBadges.EARLY_ALIASES - 1).toLong())
            .singleOrNull()
            ?.get(CommunityIdentities.createdAt)
            ?.toKotlinInstant()

        identities.mapValues { (userId, identity) ->
            ActivityStats(
                posts = posts[userId] ?: 0,
                comments = comments[userId] ?: 0,
                likesReceived = likes[userId] ?: 0,
                memberSince = identity.createdAt,
                early = earlyUntil == null || identity.createdAt <= earlyUntil,
            )
        }
    }

    /**
     * Her visible alias posts, newest first, for the profile page. What she wrote as a
     * doctor is left out: on her alias page it would put her name next to her alias.
     */
    suspend fun postsBy(userId: Uuid, limit: Int): List<PostRecord> = dbQuery {
        CommunityPosts.selectAll()
            .where {
                (CommunityPosts.userId eq userId) and
                    (CommunityPosts.status eq ContentStatus.VISIBLE.dbValue()) and
                    CommunityPosts.doctorId.isNull()
            }
            .orderBy(CommunityPosts.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toPost() }
    }

    // ---------------------------------------------------------------- blocks

    suspend fun setBlocked(blockerId: Uuid, blockedId: Uuid, blocked: Boolean): Unit = dbQuery {
        if (blocked) {
            CommunityBlocks.insertIgnore {
                it[CommunityBlocks.blockerId] = blockerId
                it[CommunityBlocks.blockedId] = blockedId
                it[createdAt] = now().toOffsetDateTime()
            }
        } else {
            CommunityBlocks.deleteWhere {
                (CommunityBlocks.blockerId eq blockerId) and (CommunityBlocks.blockedId eq blockedId)
            }
        }
    }

    /** True when [a] blocked [b]. */
    suspend fun isBlocked(a: Uuid, b: Uuid): Boolean = dbQuery {
        CommunityBlocks.selectAll()
            .where { (CommunityBlocks.blockerId eq a) and (CommunityBlocks.blockedId eq b) }
            .count() > 0
    }

    /** True when either has blocked the other. */
    suspend fun blockedEitherWay(a: Uuid, b: Uuid): Boolean = dbQuery {
        CommunityBlocks.selectAll()
            .where {
                ((CommunityBlocks.blockerId eq a) and (CommunityBlocks.blockedId eq b)) or
                    ((CommunityBlocks.blockerId eq b) and (CommunityBlocks.blockedId eq a))
            }
            .count() > 0
    }

    suspend fun createIdentity(userId: Uuid, alias: String, tint: Int): IdentityRecord = dbQuery {
        val timestamp = now()
        CommunityIdentities.insert {
            it[CommunityIdentities.userId] = userId
            it[CommunityIdentities.alias] = alias
            it[CommunityIdentities.tint] = tint
            it[createdAt] = timestamp.toOffsetDateTime()
            it[dmOpen] = true
        }
        IdentityRecord(userId, alias, tint, timestamp)
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
        doctorsOnly: Boolean = false,
    ): Pair<List<PostRecord>, Long> = dbQuery {
        var query = CommunityPosts.selectAll().where { readerVisiblePost() }
        if (doctorsOnly) query = query.andWhere { CommunityPosts.doctorId.isNotNull() }
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

    suspend fun insertPost(userId: Uuid, topic: CommunityTopic, body: String, doctorId: Uuid? = null): PostRecord = dbQuery {
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
            it[CommunityPosts.doctorId] = doctorId
        }
        PostRecord(id, userId, topic, body, ContentStatus.VISIBLE, null, timestamp, doctorId)
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
            .where { (CommunityComments.postId eq postId) and readerVisibleComment() }
            .orderBy(CommunityComments.createdAt to SortOrder.ASC)
            .map { it.toComment() }
    }

    suspend fun commentById(id: Uuid): CommentRecord? = dbQuery {
        CommunityComments.selectAll().where { CommunityComments.id eq id }.singleOrNull()?.toComment()
    }

    suspend fun insertComment(postId: Uuid, userId: Uuid, body: String, doctorId: Uuid? = null): CommentRecord = dbQuery {
        val id = Uuid.random()
        val timestamp = now()
        CommunityComments.insert {
            it[CommunityComments.id] = id
            it[CommunityComments.postId] = postId
            it[CommunityComments.userId] = userId
            it[CommunityComments.body] = body
            it[status] = ContentStatus.VISIBLE.dbValue()
            it[createdAt] = timestamp.toOffsetDateTime()
            it[CommunityComments.doctorId] = doctorId
        }
        CommentRecord(id, postId, userId, body, ContentStatus.VISIBLE, null, timestamp, doctorId)
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
                .where { (CommunityComments.postId inList postIds) and readerVisibleComment() }
                .groupBy(CommunityComments.postId)
                .associate { it[CommunityComments.postId] to it[CommunityComments.id.count()].toInt() },
            doctorAnswers = CommunityComments
                .select(CommunityComments.postId, CommunityComments.doctorId.countDistinct())
                .where {
                    (CommunityComments.postId inList postIds) and readerVisibleComment() and
                        CommunityComments.doctorId.isNotNull()
                }
                .groupBy(CommunityComments.postId)
                .associate { it[CommunityComments.postId] to it[CommunityComments.doctorId.countDistinct()].toInt() },
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
        messageId: Uuid? = null,
    ): Boolean = dbQuery {
        val duplicate = CommunityReports.selectAll()
            .where {
                (CommunityReports.reporterId eq reporterId) and
                    when {
                        postId != null -> CommunityReports.postId eq postId
                        commentId != null -> CommunityReports.commentId eq commentId
                        else -> CommunityReports.messageId eq messageId
                    }
            }
            .count() > 0
        if (duplicate) return@dbQuery false
        CommunityReports.insert {
            it[id] = Uuid.random()
            it[CommunityReports.reporterId] = reporterId
            it[CommunityReports.postId] = postId
            it[CommunityReports.commentId] = commentId
            it[CommunityReports.messageId] = messageId
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
        val doctorNames = doctorNamesIn(posts.mapNotNull { it.doctorId })
        val likes = countBy(CommunityPostLikes.postId, ids)
        val comments = countBy(CommunityComments.postId, ids)
        val reports = openReportCounts(CommunityReports.postId, ids)

        posts.map { post ->
            val identity = identities[post.userId]
            // A doctor post shows her name, never her alias: the queue must not be
            // where the two meet.
            val doctorName = post.doctorId?.let { doctorNames[it] }
            ModerationPostRow(
                id = post.id,
                alias = doctorName ?: identity?.alias ?: "Anonim",
                tint = identity?.tint ?: 0,
                topic = post.topic,
                body = post.body,
                status = post.status,
                hiddenReason = post.hiddenReason,
                createdAt = post.createdAt,
                likeCount = likes[post.id] ?: 0,
                commentCount = comments[post.id] ?: 0,
                openReports = reports[post.id] ?: 0,
                byDoctor = post.doctorId != null,
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
        val doctorNames = doctorNamesIn(comments.mapNotNull { it.doctorId })
        val reports = openReportCounts(CommunityReports.commentId, comments.map { it.id })
        comments.map { comment ->
            val identity = identities[comment.userId]
            val doctorName = comment.doctorId?.let { doctorNames[it] }
            ModerationCommentRow(
                id = comment.id,
                postId = comment.postId,
                alias = doctorName ?: identity?.alias ?: "Anonim",
                tint = identity?.tint ?: 0,
                body = comment.body,
                status = comment.status,
                hiddenReason = comment.hiddenReason,
                createdAt = comment.createdAt,
                openReports = reports[comment.id] ?: 0,
                byDoctor = comment.doctorId != null,
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
        val messageIds = rows.mapNotNull { it[CommunityReports.messageId] }
        val messages = if (messageIds.isEmpty()) emptyMap() else CommunityMessages
            .select(CommunityMessages.id, CommunityMessages.body, CommunityMessages.status)
            .where { CommunityMessages.id inList messageIds }
            .associate {
                it[CommunityMessages.id] to (it[CommunityMessages.body] to enumFromDb(it[CommunityMessages.status], ContentStatus.VISIBLE))
            }

        rows.map { row ->
            val post = row[CommunityReports.postId]?.let { posts[it] }
            val comment = row[CommunityReports.commentId]?.let { comments[it] }
            val message = row[CommunityReports.messageId]?.let { messages[it] }
            ReportRecord(
                id = row[CommunityReports.id],
                postId = row[CommunityReports.postId],
                commentId = row[CommunityReports.commentId],
                messageId = row[CommunityReports.messageId],
                reason = enumFromDb(row[CommunityReports.reason], ReportReason.OTHER),
                note = row[CommunityReports.note],
                createdAt = row[CommunityReports.createdAt].toKotlinInstant(),
                resolvedAt = row[CommunityReports.resolvedAt]?.toKotlinInstant(),
                resolution = row[CommunityReports.resolution],
                excerpt = (post?.body ?: comment?.body ?: message?.first).orEmpty().take(EXCERPT_LENGTH),
                targetStatus = post?.status ?: comment?.status ?: message?.second ?: ContentStatus.HIDDEN,
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
        val message = row[CommunityReports.messageId]?.let { messageId ->
            CommunityMessages.select(CommunityMessages.body, CommunityMessages.status)
                .where { CommunityMessages.id eq messageId }
                .singleOrNull()
                ?.let { it[CommunityMessages.body] to enumFromDb(it[CommunityMessages.status], ContentStatus.VISIBLE) }
        }
        ReportRecord(
            id = row[CommunityReports.id],
            postId = row[CommunityReports.postId],
            commentId = row[CommunityReports.commentId],
            messageId = row[CommunityReports.messageId],
            reason = enumFromDb(row[CommunityReports.reason], ReportReason.OTHER),
            note = row[CommunityReports.note],
            createdAt = row[CommunityReports.createdAt].toKotlinInstant(),
            resolvedAt = row[CommunityReports.resolvedAt]?.toKotlinInstant(),
            resolution = row[CommunityReports.resolution],
            excerpt = (post?.body ?: comment?.body ?: message?.first).orEmpty().take(EXCERPT_LENGTH),
            targetStatus = post?.status ?: comment?.status ?: message?.second ?: ContentStatus.HIDDEN,
        )
    }

    /** Resolves every open report on the same target at once, so the queue empties as one. */
    suspend fun resolveReportsOn(postId: Uuid?, commentId: Uuid?, resolution: String, by: Uuid, messageId: Uuid? = null): Int = dbQuery {
        CommunityReports.update({
            CommunityReports.resolvedAt.isNull() and
                when {
                    postId != null -> CommunityReports.postId eq postId
                    commentId != null -> CommunityReports.commentId eq commentId
                    else -> CommunityReports.messageId eq messageId
                }
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

    // ---------------------------------------------------------------- doctors

    /**
     * What a reader may see: visible, and — for a doctor post — written by a doctor who
     * is approved right now. A suspension takes her posts off the feed with her.
     */
    private fun readerVisiblePost(): Op<Boolean> =
        (CommunityPosts.status eq ContentStatus.VISIBLE.dbValue()) and
            (CommunityPosts.doctorId.isNull() or (CommunityPosts.doctorId inSubQuery approvedDoctorIds()))

    private fun readerVisibleComment(): Op<Boolean> =
        (CommunityComments.status eq ContentStatus.VISIBLE.dbValue()) and
            (CommunityComments.doctorId.isNull() or (CommunityComments.doctorId inSubQuery approvedDoctorIds()))

    private fun approvedDoctorIds() = DoctorProfiles.select(DoctorProfiles.id)
        .where { DoctorProfiles.status eq DoctorStatus.APPROVED.dbValue() }

    /** A post a reader may open: visible, and not by a doctor who has since been suspended. */
    suspend fun readablePostById(id: Uuid): PostRecord? = dbQuery {
        CommunityPosts.selectAll()
            .where { (CommunityPosts.id eq id) and readerVisiblePost() }
            .singleOrNull()
            ?.toPost()
    }

    /** Bylines for approved doctors only; a suspended one has none to give. */
    suspend fun doctorBylines(ids: Collection<Uuid>): Map<Uuid, DoctorByline> = dbQuery {
        val distinct = ids.distinct()
        if (distinct.isEmpty()) return@dbQuery emptyMap()
        DoctorProfiles.selectAll()
            .where { (DoctorProfiles.id inList distinct) and (DoctorProfiles.status eq DoctorStatus.APPROVED.dbValue()) }
            .associate { it[DoctorProfiles.id] to it.toByline() }
    }

    /** The account's doctor byline if she is approved right now, else null. */
    suspend fun approvedDoctorOf(userId: Uuid): DoctorByline? = dbQuery {
        DoctorProfiles.selectAll()
            .where { (DoctorProfiles.userId eq userId) and (DoctorProfiles.status eq DoctorStatus.APPROVED.dbValue()) }
            .singleOrNull()
            ?.toByline()
    }

    suspend fun doctorPosts(doctorId: Uuid, limit: Int): List<PostRecord> = dbQuery {
        CommunityPosts.selectAll()
            .where { (CommunityPosts.doctorId eq doctorId) and (CommunityPosts.status eq ContentStatus.VISIBLE.dbValue()) }
            .orderBy(CommunityPosts.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toPost() }
    }

    /** Visible posts and distinct threads answered, per doctor. */
    suspend fun doctorActivity(doctorIds: Collection<Uuid>): Map<Uuid, Pair<Int, Int>> = dbQuery {
        val ids = doctorIds.distinct()
        if (ids.isEmpty()) return@dbQuery emptyMap()
        val visible = ContentStatus.VISIBLE.dbValue()
        val postCounter = CommunityPosts.id.count()
        val posts = CommunityPosts.select(CommunityPosts.doctorId, postCounter)
            .where { (CommunityPosts.doctorId inList ids) and (CommunityPosts.status eq visible) }
            .groupBy(CommunityPosts.doctorId)
            .mapNotNull { row -> row[CommunityPosts.doctorId]?.let { it to row[postCounter].toInt() } }
            .toMap()
        val answerCounter = CommunityComments.postId.countDistinct()
        val answers = CommunityComments.select(CommunityComments.doctorId, answerCounter)
            .where { (CommunityComments.doctorId inList ids) and (CommunityComments.status eq visible) }
            .groupBy(CommunityComments.doctorId)
            .mapNotNull { row -> row[CommunityComments.doctorId]?.let { it to row[answerCounter].toInt() } }
            .toMap()
        ids.associateWith { (posts[it] ?: 0) to (answers[it] ?: 0) }
    }

    /**
     * Questions still waiting for a doctor: visible alias posts since [since] that no
     * doctor has answered, newest first. The doctor panel's work list.
     */
    suspend fun unansweredQuestions(topic: CommunityTopic?, since: Instant, limit: Int): List<PostRecord> = dbQuery {
        val answered = CommunityComments.select(CommunityComments.postId)
            .where { CommunityComments.doctorId.isNotNull() and (CommunityComments.status eq ContentStatus.VISIBLE.dbValue()) }
        var query = CommunityPosts.selectAll()
            .where {
                (CommunityPosts.status eq ContentStatus.VISIBLE.dbValue()) and
                    CommunityPosts.doctorId.isNull() and
                    (CommunityPosts.createdAt greaterEq since.toOffsetDateTime()) and
                    (CommunityPosts.id notInSubQuery answered)
            }
        topic?.let { query = query.andWhere { CommunityPosts.topic eq it.dbValue() } }
        query.orderBy(CommunityPosts.createdAt to SortOrder.DESC).limit(limit).map { it.toPost() }
    }

    private fun doctorNamesIn(ids: Collection<Uuid>): Map<Uuid, String> {
        if (ids.isEmpty()) return emptyMap()
        return DoctorProfiles.select(DoctorProfiles.id, DoctorProfiles.fullName)
            .where { DoctorProfiles.id inList ids.distinct() }
            .associate { it[DoctorProfiles.id] to it[DoctorProfiles.fullName] }
    }

    private fun ResultRow.toByline() = DoctorByline(
        id = this[DoctorProfiles.id],
        userId = this[DoctorProfiles.userId],
        fullName = this[DoctorProfiles.fullName],
        specialty = enumFromDb(this[DoctorProfiles.specialty], DoctorSpecialty.OTHER),
    )

    // ---------------------------------------------------------------- mapping

    private fun ResultRow.toIdentity() = IdentityRecord(
        userId = this[CommunityIdentities.userId],
        alias = this[CommunityIdentities.alias],
        tint = this[CommunityIdentities.tint],
        createdAt = this[CommunityIdentities.createdAt].toKotlinInstant(),
        bio = this[CommunityIdentities.bio],
        dmOpen = this[CommunityIdentities.dmOpen],
    )

    private fun ResultRow.toPost() = PostRecord(
        id = this[CommunityPosts.id],
        userId = this[CommunityPosts.userId],
        topic = enumFromDb(this[CommunityPosts.topic], CommunityTopic.WELLBEING),
        body = this[CommunityPosts.body],
        status = enumFromDb(this[CommunityPosts.status], ContentStatus.VISIBLE),
        hiddenReason = this[CommunityPosts.hiddenReason],
        createdAt = this[CommunityPosts.createdAt].toKotlinInstant(),
        doctorId = this[CommunityPosts.doctorId],
    )

    private fun ResultRow.toComment() = CommentRecord(
        id = this[CommunityComments.id],
        postId = this[CommunityComments.postId],
        userId = this[CommunityComments.userId],
        body = this[CommunityComments.body],
        status = enumFromDb(this[CommunityComments.status], ContentStatus.VISIBLE),
        hiddenReason = this[CommunityComments.hiddenReason],
        createdAt = this[CommunityComments.createdAt].toKotlinInstant(),
        doctorId = this[CommunityComments.doctorId],
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
    /** Distinct verified doctors who answered, per post. */
    val doctorAnswers: Map<Uuid, Int> = emptyMap(),
)
