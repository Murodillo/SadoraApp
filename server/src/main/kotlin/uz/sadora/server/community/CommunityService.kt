package uz.sadora.server.community

import kotlin.random.Random
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid
import uz.sadora.contract.AccountStatus
import uz.sadora.contract.CommunityComment
import uz.sadora.contract.CommunityIdentity
import uz.sadora.contract.CommunityPost
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.CreateCommentRequest
import uz.sadora.contract.CreatePostRequest
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.LikeState
import uz.sadora.contract.Page
import uz.sadora.contract.ReportRequest
import uz.sadora.contract.SaveState
import uz.sadora.contract.Limits
import uz.sadora.server.config.Environment
import uz.sadora.server.core.ConflictException
import uz.sadora.server.core.FeatureDisabledException
import uz.sadora.server.core.ForbiddenException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.RateLimitedException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.now
import uz.sadora.server.db.ContentStatus
import uz.sadora.server.flags.FeatureFlagService
import uz.sadora.server.flags.FlagContext
import uz.sadora.server.user.UserRecord
import uz.sadora.server.user.UserRepository

/**
 * The secret chat as the reader uses it.
 *
 * Three rules shape every write. The room is behind the `community` flag, so an
 * operator can close it without a release. A silenced author is refused before her
 * text is validated, so the refusal is the same whatever she typed. And there is a
 * per-day ceiling on posts and comments per account — not a product limit, a floor
 * under the moderation queue.
 */
class CommunityService(
    private val repository: CommunityRepository,
    private val users: UserRepository,
    private val flags: FeatureFlagService,
    private val environment: Environment,
    private val random: Random = Random.Default,
) {

    // ---------------------------------------------------------------- identity

    /**
     * The alias she posts under, made on first use.
     *
     * A handful of candidates are tried against the table; the generator adds a suffix
     * once the plain forms start colliding, so this loop ends quickly however full the
     * room gets.
     */
    suspend fun identity(userId: Uuid): CommunityIdentity {
        requireOpen(userId)
        return ensureIdentity(userId).toDto()
    }

    private suspend fun ensureIdentity(userId: Uuid): IdentityRecord {
        repository.identityOf(userId)?.let { return it }
        repeat(MAX_ALIAS_ATTEMPTS) { attempt ->
            val candidate = AliasGenerator.candidate(random, attempt)
            if (!repository.aliasTaken(candidate)) {
                return repository.createIdentity(userId, candidate, AliasGenerator.tint(random))
            }
        }
        throw ConflictException("Taxallus tanlab bo'lmadi, qayta urinib ko'ring")
    }

    // ---------------------------------------------------------------- feed

    suspend fun feed(
        userId: Uuid,
        topic: CommunityTopic?,
        savedOnly: Boolean,
        limit: Int,
        offset: Long,
    ): Page<CommunityPost> {
        requireOpen(userId)
        val (posts, total) = repository.listPosts(userId, topic, savedOnly, limit, offset)
        return Page(project(userId, posts), total, limit, offset.toInt())
    }

    suspend fun post(userId: Uuid, postId: Uuid): CommunityPost {
        requireOpen(userId)
        val post = repository.postById(postId)?.takeIf { it.status == ContentStatus.VISIBLE }
            ?: throw NotFoundException("Post topilmadi")
        return project(userId, listOf(post)).single()
    }

    private suspend fun project(viewer: Uuid, posts: List<PostRecord>): List<CommunityPost> {
        if (posts.isEmpty()) return emptyList()
        val identities = repository.identitiesFor(posts.map { it.userId })
        val reactions = repository.reactionsFor(viewer, posts.map { it.id })
        return posts.map { post ->
            val identity = identities[post.userId]
            CommunityPost(
                id = post.id.toString(),
                topic = post.topic,
                alias = identity?.alias ?: FALLBACK_ALIAS,
                tint = identity?.tint ?: 0,
                body = post.body,
                createdAt = post.createdAt,
                likeCount = reactions.likeCounts[post.id] ?: 0,
                commentCount = reactions.commentCounts[post.id] ?: 0,
                liked = post.id in reactions.liked,
                saved = post.id in reactions.saved,
                isMine = post.userId == viewer,
            )
        }
    }

    // ---------------------------------------------------------------- posting

    suspend fun createPost(userId: Uuid, request: CreatePostRequest): CommunityPost {
        requireOpen(userId)
        requireNotRestricted(userId)
        val body = request.body.trim()
        validateBody(body, MAX_POST_LENGTH)
        if (repository.postsSince(userId, now() - 24.hours) >= MAX_POSTS_PER_DAY) {
            throw RateLimitedException("Bir kunda $MAX_POSTS_PER_DAY tadan ko'p post yozib bo'lmaydi")
        }
        ensureIdentity(userId)
        val record = repository.insertPost(userId, request.topic, body)
        return project(userId, listOf(record)).single()
    }

    suspend fun deletePost(userId: Uuid, postId: Uuid) {
        requireOpen(userId)
        if (!repository.deleteOwnPost(userId, postId)) throw NotFoundException("Post topilmadi")
    }

    // ---------------------------------------------------------------- comments

    suspend fun comments(userId: Uuid, postId: Uuid): List<CommunityComment> {
        requireOpen(userId)
        requireVisiblePost(postId)
        val comments = repository.commentsOf(postId)
        val identities = repository.identitiesFor(comments.map { it.userId })
        return comments.map { it.toDto(identities[it.userId], viewer = userId) }
    }

    suspend fun addComment(userId: Uuid, postId: Uuid, request: CreateCommentRequest): CommunityComment {
        requireOpen(userId)
        requireNotRestricted(userId)
        requireVisiblePost(postId)
        val body = request.body.trim()
        validateBody(body, MAX_COMMENT_LENGTH)
        if (repository.commentsSince(userId, now() - 24.hours) >= MAX_COMMENTS_PER_DAY) {
            throw RateLimitedException("Bir kunda $MAX_COMMENTS_PER_DAY tadan ko'p izoh yozib bo'lmaydi")
        }
        val identity = ensureIdentity(userId)
        return repository.insertComment(postId, userId, body).toDto(identity, viewer = userId)
    }

    suspend fun deleteComment(userId: Uuid, commentId: Uuid) {
        requireOpen(userId)
        if (!repository.deleteOwnComment(userId, commentId)) throw NotFoundException("Izoh topilmadi")
    }

    // ---------------------------------------------------------------- reactions

    suspend fun setLiked(userId: Uuid, postId: Uuid, liked: Boolean): LikeState {
        requireOpen(userId)
        requireVisiblePost(postId)
        repository.setLiked(postId, userId, liked)
        return LikeState(liked = liked, likeCount = repository.likeCount(postId))
    }

    suspend fun setSaved(userId: Uuid, postId: Uuid, saved: Boolean): SaveState {
        requireOpen(userId)
        requireVisiblePost(postId)
        repository.setSaved(postId, userId, saved)
        return SaveState(saved)
    }

    // ---------------------------------------------------------------- reports

    /**
     * Files a report, and hides the target on its own once enough readers agree.
     *
     * The threshold is a safety net, not the moderation policy: a moderator still sees
     * the post in the queue and can restore it. Reporting one's own content is refused —
     * deleting it is the honest way to take it back.
     */
    suspend fun reportPost(userId: Uuid, postId: Uuid, request: ReportRequest) {
        requireOpen(userId)
        val post = requireVisiblePost(postId)
        if (post.userId == userId) throw ValidationException("postId", "O'z postingizga shikoyat qilib bo'lmaydi")
        if (!repository.addReport(userId, postId, null, request.reason, request.note?.trim()?.take(MAX_NOTE_LENGTH))) {
            throw ConflictException("Bu post allaqachon shikoyat qilingan")
        }
        if (repository.openReportsFor(postId, null) >= AUTO_HIDE_REPORTS) {
            repository.setPostStatus(postId, ContentStatus.HIDDEN, AUTO_HIDE_REASON)
        }
    }

    suspend fun reportComment(userId: Uuid, commentId: Uuid, request: ReportRequest) {
        requireOpen(userId)
        val comment = repository.commentById(commentId)?.takeIf { it.status == ContentStatus.VISIBLE }
            ?: throw NotFoundException("Izoh topilmadi")
        if (comment.userId == userId) throw ValidationException("commentId", "O'z izohingizga shikoyat qilib bo'lmaydi")
        if (!repository.addReport(userId, null, commentId, request.reason, request.note?.trim()?.take(MAX_NOTE_LENGTH))) {
            throw ConflictException("Bu izoh allaqachon shikoyat qilingan")
        }
        if (repository.openReportsFor(null, commentId) >= AUTO_HIDE_REPORTS) {
            repository.setCommentStatus(commentId, ContentStatus.HIDDEN, AUTO_HIDE_REASON)
        }
    }

    // ---------------------------------------------------------------- gates

    /** The room is open to this account: the account is active and the flag says so for her. */
    private suspend fun requireOpen(userId: Uuid): UserRecord {
        val user = users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")
        if (user.status != AccountStatus.ACTIVE) {
            throw ForbiddenException(ErrorCodes.ACCOUNT_BLOCKED, "Hisob faol emas")
        }
        val context = FlagContext(
            userId = userId,
            environment = environment,
            language = user.language,
            lifeStage = user.lifeStage,
        )
        if (!flags.isEnabled(COMMUNITY_FLAG, context)) throw FeatureDisabledException(COMMUNITY_FLAG)
        return user
    }

    private suspend fun requireNotRestricted(userId: Uuid) {
        val restriction = repository.restrictionOf(userId) ?: return
        val until = restriction.until
        if (until == null || until > now()) {
            throw ForbiddenException(message = "Maxfiy chatda yozish vaqtincha cheklangan")
        }
    }

    private suspend fun requireVisiblePost(postId: Uuid): PostRecord =
        repository.postById(postId)?.takeIf { it.status == ContentStatus.VISIBLE }
            ?: throw NotFoundException("Post topilmadi")

    private fun validateBody(body: String, max: Int) {
        if (body.length < MIN_BODY_LENGTH) throw ValidationException("body", "Kamida $MIN_BODY_LENGTH ta belgi")
        if (body.length > max) throw ValidationException("body", "Eng ko'pi $max belgi")
    }

    private fun IdentityRecord.toDto() = CommunityIdentity(alias, tint)

    private fun CommentRecord.toDto(identity: IdentityRecord?, viewer: Uuid) = CommunityComment(
        id = id.toString(),
        postId = postId.toString(),
        alias = identity?.alias ?: FALLBACK_ALIAS,
        tint = identity?.tint ?: 0,
        body = body,
        createdAt = createdAt,
        isMine = userId == viewer,
    )

    companion object {
        const val COMMUNITY_FLAG = "community"
        const val MIN_BODY_LENGTH = Limits.POST_MIN
        const val MAX_POST_LENGTH = Limits.POST_MAX
        const val MAX_COMMENT_LENGTH = Limits.COMMENT_MAX
        const val MAX_NOTE_LENGTH = Limits.REPORT_NOTE_MAX
        const val MAX_POSTS_PER_DAY = 10
        const val MAX_COMMENTS_PER_DAY = 60
        const val AUTO_HIDE_REPORTS = 5
        const val AUTO_HIDE_REASON = "auto_reports"
        const val FALLBACK_ALIAS = "Anonim"
        private const val MAX_ALIAS_ATTEMPTS = 12
    }
}
