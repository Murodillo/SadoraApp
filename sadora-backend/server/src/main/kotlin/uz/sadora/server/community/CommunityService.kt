package uz.sadora.server.community

import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid
import uz.sadora.contract.AccountStatus
import uz.sadora.contract.BlockState
import uz.sadora.contract.CommunityComment
import uz.sadora.contract.CommunityIdentity
import uz.sadora.contract.CommunityPost
import uz.sadora.contract.CommunityProfile
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.CreateCommentRequest
import uz.sadora.contract.CreatePostRequest
import uz.sadora.contract.DoctorAuthor
import uz.sadora.contract.DoctorProfile
import uz.sadora.contract.DoctorListItem
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.NotificationStatus
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.LikeState
import uz.sadora.contract.Page
import uz.sadora.contract.ReportRequest
import uz.sadora.contract.SaveState
import uz.sadora.contract.UpdateIdentityRequest
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
import uz.sadora.server.notify.NotificationRepository
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
    /** Null in tests that never touch messages; the unread count is then zero. */
    private val messaging: MessagingRepository? = null,
    /** Where "a doctor answered you" is queued; null sends nothing. */
    private val notifications: NotificationRepository? = null,
    /** The doctor directory and public pages; null in tests that have no doctors. */
    private val doctors: uz.sadora.server.doctor.DoctorRepository? = null,
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
        val identity = ensureIdentity(userId)
        val stats = repository.activityFor(listOf(userId))[userId]
        return identity.toDto(
            badges = stats?.let { CommunityBadges.of(it, now()) }.orEmpty(),
            unread = messaging?.unreadTotal(userId) ?: 0,
        )
    }

    /** Her bio and her door. The bio is trimmed; blank clears it. */
    suspend fun updateIdentity(userId: Uuid, request: UpdateIdentityRequest): CommunityIdentity {
        requireOpen(userId)
        ensureIdentity(userId)
        val bio = request.bio?.trim()
        if (bio != null && bio.length > Limits.BIO_MAX) throw ValidationException("bio", "Eng ko'pi ${Limits.BIO_MAX} belgi")
        repository.updateIdentity(
            userId = userId,
            bio = bio?.takeIf { it.isNotEmpty() },
            keepBio = request.bio == null,
            dmOpen = request.dmOpen,
        )
        return identity(userId)
    }

    // ---------------------------------------------------------------- profiles

    /**
     * An alias's page. Her own reads the same way, with [CommunityProfile.isMe] set so
     * the app draws "edit" where it would draw "message".
     */
    suspend fun profile(viewer: Uuid, alias: String): CommunityProfile {
        requireOpen(viewer)
        val identity = repository.identityByAlias(alias) ?: throw NotFoundException("Taxallus topilmadi")
        val stats = repository.activityFor(listOf(identity.userId))[identity.userId]
            ?: ActivityStats(memberSince = identity.createdAt)
        val isMe = identity.userId == viewer
        val blocked = !isMe && repository.isBlocked(viewer, identity.userId)
        val blockedEitherWay = !isMe && (blocked || repository.isBlocked(identity.userId, viewer))
        val posts = repository.postsBy(identity.userId, PROFILE_POSTS)
        return CommunityProfile(
            alias = identity.alias,
            tint = identity.tint,
            bio = identity.bio,
            badges = CommunityBadges.of(stats, now()),
            postCount = stats.posts,
            commentCount = stats.comments,
            likesReceived = stats.likesReceived,
            memberSince = identity.createdAt,
            isMe = isMe,
            canMessage = !isMe && identity.dmOpen && !blockedEitherWay,
            blocked = blocked,
            posts = project(viewer, posts),
        )
    }

    suspend fun setBlocked(viewer: Uuid, alias: String, blocked: Boolean): BlockState {
        requireOpen(viewer)
        val identity = repository.identityByAlias(alias) ?: throw NotFoundException("Taxallus topilmadi")
        if (identity.userId == viewer) throw ValidationException("alias", "O'zingizni bloklab bo'lmaydi")
        repository.setBlocked(viewer, identity.userId, blocked)
        return BlockState(blocked)
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
        doctorsOnly: Boolean = false,
    ): Page<CommunityPost> {
        requireOpen(userId)
        val (posts, total) = repository.listPosts(userId, topic, savedOnly, limit, offset, doctorsOnly)
        return Page(project(userId, posts), total, limit, offset.toInt())
    }

    suspend fun post(userId: Uuid, postId: Uuid): CommunityPost {
        requireOpen(userId)
        val post = repository.readablePostById(postId) ?: throw NotFoundException("Post topilmadi")
        return project(userId, listOf(post)).single()
    }

    private suspend fun project(viewer: Uuid, posts: List<PostRecord>): List<CommunityPost> {
        if (posts.isEmpty()) return emptyList()
        val identities = repository.identitiesFor(posts.filter { it.doctorId == null }.map { it.userId })
        val bylines = repository.doctorBylines(posts.mapNotNull { it.doctorId })
        val reactions = repository.reactionsFor(viewer, posts.map { it.id })
        val badges = badgesFor(posts.filter { it.doctorId == null }.map { it.userId })
        return posts.map { post ->
            // A doctor post carries her name and nothing of her alias — not the tint,
            // not the badges — so the two can never be matched up on a screen.
            val byline = post.doctorId?.let { bylines[it] }
            val identity = if (post.doctorId == null) identities[post.userId] else null
            CommunityPost(
                id = post.id.toString(),
                topic = post.topic,
                alias = byline?.fullName ?: identity?.alias ?: FALLBACK_ALIAS,
                tint = identity?.tint ?: 0,
                body = post.body,
                createdAt = post.createdAt,
                likeCount = reactions.likeCounts[post.id] ?: 0,
                commentCount = reactions.commentCounts[post.id] ?: 0,
                liked = post.id in reactions.liked,
                saved = post.id in reactions.saved,
                isMine = post.userId == viewer,
                badges = if (post.doctorId == null) badges[post.userId].orEmpty() else emptyList(),
                doctor = byline?.toAuthor(),
                doctorAnswers = reactions.doctorAnswers[post.id] ?: 0,
            )
        }
    }

    private fun DoctorByline.toAuthor() = DoctorAuthor(id.toString(), fullName, specialty)

    private suspend fun badgesFor(userIds: List<Uuid>): Map<Uuid, List<uz.sadora.contract.CommunityBadge>> {
        val at = now()
        return repository.activityFor(userIds).mapValues { (_, stats) -> CommunityBadges.of(stats, at) }
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
        // An approved doctor always writes as herself; that is what she applied for.
        val doctor = repository.approvedDoctorOf(userId)
        val record = repository.insertPost(userId, request.topic, body, doctor?.id)
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
        val identities = repository.identitiesFor(comments.filter { it.doctorId == null }.map { it.userId })
        val bylines = repository.doctorBylines(comments.mapNotNull { it.doctorId })
        val badges = badgesFor(comments.filter { it.doctorId == null }.map { it.userId })
        // A doctor's answer is the one the asker came for, so answers lead the thread;
        // each group keeps its own order.
        return comments
            .sortedBy { if (it.doctorId != null) 0 else 1 }
            .map { comment ->
                val byline = comment.doctorId?.let { bylines[it] }
                if (byline != null) {
                    comment.toDto(null, viewer = userId).copy(alias = byline.fullName, doctor = byline.toAuthor())
                } else {
                    comment.toDto(identities[comment.userId], viewer = userId, badges = badges[comment.userId].orEmpty())
                }
            }
    }

    suspend fun addComment(userId: Uuid, postId: Uuid, request: CreateCommentRequest): CommunityComment {
        requireOpen(userId)
        requireNotRestricted(userId)
        val post = requireVisiblePost(postId)
        val body = request.body.trim()
        validateBody(body, MAX_COMMENT_LENGTH)
        val doctor = repository.approvedDoctorOf(userId)
        // Answering is a doctor's work here, so her ceiling is a higher one.
        val ceiling = if (doctor != null) MAX_DOCTOR_COMMENTS_PER_DAY else MAX_COMMENTS_PER_DAY
        if (repository.commentsSince(userId, now() - 24.hours) >= ceiling) {
            throw RateLimitedException("Bir kunda $ceiling tadan ko'p izoh yozib bo'lmaydi")
        }
        if (doctor != null) {
            val record = repository.insertComment(postId, userId, body, doctor.id)
            if (post.userId != userId) notifyDoctorAnswer(post, doctor, record)
            return record.toDto(null, viewer = userId).copy(alias = doctor.fullName, doctor = doctor.toAuthor())
        }
        val identity = ensureIdentity(userId)
        return repository.insertComment(postId, userId, body).toDto(identity, viewer = userId)
    }

    suspend fun deleteComment(userId: Uuid, commentId: Uuid) {
        requireOpen(userId)
        if (!repository.deleteOwnComment(userId, commentId)) throw NotFoundException("Izoh topilmadi")
    }

    /**
     * Tells the asker a doctor answered. Named by the doctor, since she is public; the
     * asker's own alias is never in it. Her notification switches apply.
     */
    private suspend fun notifyDoctorAnswer(post: PostRecord, doctor: DoctorByline, comment: CommentRecord) {
        val outbox = notifications ?: return
        val settings = outbox.settingsOf(post.userId)
        if (!settings.enabled || !settings.isCategoryEnabled(NotificationCategory.SYSTEM)) return
        outbox.enqueue(
            userId = post.userId,
            category = NotificationCategory.SYSTEM,
            title = "Shifokor javob berdi",
            body = "${doctor.fullName}: ${comment.body.take(PUSH_PREVIEW)}",
            scheduledFor = comment.createdAt,
            dedupeKey = "doctor_answer:${comment.id}",
            status = NotificationStatus.QUEUED,
            suppressedReason = null,
        )
    }

    // ---------------------------------------------------------------- doctors

    /** The verified doctors, most answers first. */
    suspend fun doctors(viewer: Uuid): List<DoctorListItem> {
        requireOpen(viewer)
        val list = doctors?.approved() ?: return emptyList()
        val activity = repository.doctorActivity(list.map { it.id })
        return list.map { doctor ->
            DoctorListItem(
                id = doctor.id.toString(),
                fullName = doctor.fullName,
                specialty = doctor.specialty,
                workplace = doctor.workplace,
                experienceYears = doctor.experienceYears,
                answerCount = activity[doctor.id]?.second ?: 0,
            )
        }.sortedByDescending { it.answerCount }
    }

    /** A verified doctor's public page. A pending or suspended one is not there. */
    suspend fun doctorProfile(viewer: Uuid, doctorId: Uuid): DoctorProfile {
        requireOpen(viewer)
        val doctor = doctors?.byId(doctorId)?.takeIf { it.status == uz.sadora.contract.DoctorStatus.APPROVED }
            ?: throw NotFoundException("Shifokor topilmadi")
        val (posts, answers) = repository.doctorActivity(listOf(doctor.id))[doctor.id] ?: (0 to 0)
        return DoctorProfile(
            id = doctor.id.toString(),
            fullName = doctor.fullName,
            specialty = doctor.specialty,
            workplace = doctor.workplace,
            experienceYears = doctor.experienceYears,
            bio = doctor.bio,
            verifiedSince = doctor.verifiedAt ?: doctor.submittedAt,
            postCount = posts,
            answerCount = answers,
            isMe = doctor.userId == viewer,
            posts = project(viewer, repository.doctorPosts(doctor.id, PROFILE_POSTS)),
        )
    }

    /**
     * The doctor panel's work list: recent questions nobody with a check mark has
     * answered yet. Only an approved doctor may ask for it.
     */
    suspend fun doctorQuestions(viewer: Uuid, topic: CommunityTopic?, limit: Int): List<CommunityPost> {
        requireOpen(viewer)
        repository.approvedDoctorOf(viewer) ?: throw ForbiddenException(message = "Faqat tasdiqlangan shifokorlar uchun")
        return project(viewer, repository.unansweredQuestions(topic, now() - QUESTION_WINDOW_DAYS.days, limit))
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
            throw ForbiddenException(message = "Chatda yozish vaqtincha cheklangan")
        }
    }

    private suspend fun requireVisiblePost(postId: Uuid): PostRecord =
        repository.readablePostById(postId) ?: throw NotFoundException("Post topilmadi")

    private fun validateBody(body: String, max: Int) {
        if (body.length < MIN_BODY_LENGTH) throw ValidationException("body", "Kamida $MIN_BODY_LENGTH ta belgi")
        if (body.length > max) throw ValidationException("body", "Eng ko'pi $max belgi")
    }

    private fun IdentityRecord.toDto(badges: List<uz.sadora.contract.CommunityBadge>, unread: Int) = CommunityIdentity(
        alias = alias,
        tint = tint,
        bio = bio,
        dmOpen = dmOpen,
        badges = badges,
        unreadMessages = unread,
    )

    private fun CommentRecord.toDto(
        identity: IdentityRecord?,
        viewer: Uuid,
        badges: List<uz.sadora.contract.CommunityBadge> = emptyList(),
    ) = CommunityComment(
        id = id.toString(),
        postId = postId.toString(),
        alias = identity?.alias ?: FALLBACK_ALIAS,
        tint = identity?.tint ?: 0,
        body = body,
        createdAt = createdAt,
        isMine = userId == viewer,
        badges = badges,
    )

    /** For the messaging service, which shares the gates and the alias. */
    internal suspend fun openIdentity(userId: Uuid): IdentityRecord {
        requireOpen(userId)
        return ensureIdentity(userId)
    }

    internal suspend fun requireCanWrite(userId: Uuid) = requireNotRestricted(userId)

    companion object {
        const val COMMUNITY_FLAG = "community"
        const val MIN_BODY_LENGTH = Limits.POST_MIN
        const val MAX_POST_LENGTH = Limits.POST_MAX
        const val MAX_COMMENT_LENGTH = Limits.COMMENT_MAX
        const val MAX_NOTE_LENGTH = Limits.REPORT_NOTE_MAX
        const val MAX_POSTS_PER_DAY = 10
        const val MAX_COMMENTS_PER_DAY = 60
        const val MAX_DOCTOR_COMMENTS_PER_DAY = 300
        const val QUESTION_WINDOW_DAYS = 30
        const val PUSH_PREVIEW = 120
        const val AUTO_HIDE_REPORTS = 5
        const val AUTO_HIDE_REASON = "auto_reports"
        const val FALLBACK_ALIAS = "Anonim"
        const val PROFILE_POSTS = 20
        private const val MAX_ALIAS_ATTEMPTS = 12
    }
}
