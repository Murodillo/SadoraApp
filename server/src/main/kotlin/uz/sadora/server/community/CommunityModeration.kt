package uz.sadora.server.community

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import uz.sadora.contract.Ack
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.Page
import uz.sadora.contract.ReportReason
import uz.sadora.server.api.enumParameter
import uz.sadora.server.api.intParameter
import uz.sadora.server.api.requestContext
import uz.sadora.server.api.requireAdminRole
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.auth.RequestContext
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.core.parseUuid
import uz.sadora.server.db.ContentStatus
import uz.sadora.server.plugins.ADMIN_AUTH
import uz.sadora.server.plugins.AdminPrincipal
import uz.sadora.server.plugins.AdminRole

// ---------------------------------------------------------------- views
//
// Note what these carry and what they do not. Alias, text, counts, timestamps — and no
// user id, no phone, no name. A moderator can hide a post and silence its author, and
// still never learn who wrote it. That is the whole point of the room.

@Serializable
data class ModerationPostView(
    val id: String,
    val alias: String,
    val tint: Int,
    val topic: CommunityTopic,
    val body: String,
    val hidden: Boolean,
    val hiddenReason: String? = null,
    val createdAt: Instant,
    val likeCount: Int,
    val commentCount: Int,
    val openReports: Int,
)

@Serializable
data class ModerationCommentView(
    val id: String,
    val postId: String,
    val alias: String,
    val tint: Int,
    val body: String,
    val hidden: Boolean,
    val hiddenReason: String? = null,
    val createdAt: Instant,
    val openReports: Int,
)

@Serializable
data class ModerationReportView(
    val id: String,
    val postId: String? = null,
    val commentId: String? = null,
    val reason: ReportReason,
    val note: String? = null,
    val excerpt: String,
    val targetHidden: Boolean,
    val createdAt: Instant,
    val resolvedAt: Instant? = null,
    val resolution: String? = null,
)

@Serializable
data class CommunityStatsView(
    val postsTotal: Long,
    val postsToday: Long,
    val hiddenPosts: Long,
    val openReports: Long,
)

@Serializable
data class HideRequest(
    val hidden: Boolean,
    /** Required when hiding; the audit log keeps it. */
    val reason: String? = null,
)

@Serializable
data class ResolveReportRequest(
    /** `dismiss` leaves the content up; `hide` takes it down and closes every report on it. */
    val action: String,
    val reason: String? = null,
)

@Serializable
data class RestrictAuthorRequest(
    val reason: String,
    /** Null means until a moderator lifts it. */
    val days: Int? = null,
)

/**
 * Moderation, built on the repository's alias-only rows.
 *
 * Every action names the post or comment it touched and the reason, and lands in the
 * audit log — a hidden post with no record of why is a moderation decision nobody can
 * review.
 */
class CommunityModerationService(
    private val repository: CommunityRepository,
    private val audit: AuditService,
) {

    suspend fun posts(
        hidden: Boolean?,
        topic: CommunityTopic?,
        reportedOnly: Boolean,
        limit: Int,
        offset: Long,
    ): Page<ModerationPostView> {
        val status = hidden?.let { if (it) ContentStatus.HIDDEN else ContentStatus.VISIBLE }
        val (rows, total) = repository.listForModeration(status, topic, reportedOnly, limit, offset)
        return Page(rows.map { it.toView() }, total, limit, offset.toInt())
    }

    suspend fun comments(postId: Uuid): List<ModerationCommentView> =
        repository.commentsForModeration(postId).map { it.toView() }

    suspend fun reports(openOnly: Boolean, limit: Int, offset: Long): Page<ModerationReportView> {
        val (rows, total) = repository.listReports(openOnly, limit, offset)
        return Page(rows.map { it.toView() }, total, limit, offset.toInt())
    }

    suspend fun stats(): CommunityStatsView {
        val stats = repository.stats(now().dayIn("UTC"))
        return CommunityStatsView(stats.postsTotal, stats.postsToday, stats.hiddenPosts, stats.openReports)
    }

    suspend fun setPostHidden(postId: Uuid, request: HideRequest, admin: AdminPrincipal, context: RequestContext) {
        val reason = request.reasonOrThrow()
        val status = if (request.hidden) ContentStatus.HIDDEN else ContentStatus.VISIBLE
        if (!repository.setPostStatus(postId, status, reason)) throw NotFoundException("Post topilmadi")
        if (request.hidden) repository.resolveReportsOn(postId, null, RESOLUTION_HIDDEN, admin.adminId)
        audit.record(
            admin.entry(
                action = if (request.hidden) AuditActions.COMMUNITY_POST_HIDDEN else AuditActions.COMMUNITY_POST_RESTORED,
                entityType = "community_post",
                entityId = postId.toString(),
                reason = reason,
                context = context,
            ),
        )
    }

    suspend fun setCommentHidden(commentId: Uuid, request: HideRequest, admin: AdminPrincipal, context: RequestContext) {
        val reason = request.reasonOrThrow()
        val status = if (request.hidden) ContentStatus.HIDDEN else ContentStatus.VISIBLE
        if (!repository.setCommentStatus(commentId, status, reason)) throw NotFoundException("Izoh topilmadi")
        if (request.hidden) repository.resolveReportsOn(null, commentId, RESOLUTION_HIDDEN, admin.adminId)
        audit.record(
            admin.entry(
                action = if (request.hidden) AuditActions.COMMUNITY_COMMENT_HIDDEN else AuditActions.COMMUNITY_COMMENT_RESTORED,
                entityType = "community_comment",
                entityId = commentId.toString(),
                reason = reason,
                context = context,
            ),
        )
    }

    suspend fun resolveReport(reportId: Uuid, request: ResolveReportRequest, admin: AdminPrincipal, context: RequestContext) {
        val report = repository.reportById(reportId) ?: throw NotFoundException("Shikoyat topilmadi")
        if (report.resolvedAt != null) throw ValidationException("id", "Bu shikoyat allaqachon ko'rib chiqilgan")
        when (request.action) {
            ACTION_DISMISS -> repository.resolveReportsOn(report.postId, report.commentId, RESOLUTION_DISMISSED, admin.adminId)
            ACTION_HIDE -> {
                val reason = request.reason?.trim()?.takeIf { it.isNotEmpty() } ?: "Shikoyat bo'yicha yashirildi"
                report.postId?.let { repository.setPostStatus(it, ContentStatus.HIDDEN, reason) }
                report.commentId?.let { repository.setCommentStatus(it, ContentStatus.HIDDEN, reason) }
                repository.resolveReportsOn(report.postId, report.commentId, RESOLUTION_HIDDEN, admin.adminId)
            }
            else -> throw ValidationException("action", "dismiss yoki hide")
        }
        audit.record(
            admin.entry(
                action = AuditActions.COMMUNITY_REPORT_RESOLVED,
                entityType = if (report.postId != null) "community_post" else "community_comment",
                entityId = (report.postId ?: report.commentId).toString(),
                reason = request.reason,
                metadata = mapOf("reportId" to reportId.toString(), "resolution" to request.action),
                context = context,
            ),
        )
    }

    /**
     * Silences the author of [postId]. The lookup happens inside the repository, so the
     * audit entry — and the moderator — refer to the post, never to the account.
     */
    suspend fun restrictAuthor(postId: Uuid, request: RestrictAuthorRequest, admin: AdminPrincipal, context: RequestContext) {
        val reason = request.reason.trim()
        if (reason.isEmpty()) throw ValidationException("reason", "Sabab ko'rsatilishi shart")
        request.days?.let { if (it !in 1..365) throw ValidationException("days", "1–365 kun oralig'ida") }
        val until = request.days?.let { now() + it.days }
        if (!repository.restrictAuthorOf(postId, reason, until, admin.adminId)) throw NotFoundException("Post topilmadi")
        audit.record(
            admin.entry(
                action = AuditActions.COMMUNITY_AUTHOR_RESTRICTED,
                entityType = "community_post",
                entityId = postId.toString(),
                reason = reason,
                metadata = mapOf("until" to (until?.toString() ?: "indefinite")),
                context = context,
            ),
        )
    }

    private fun HideRequest.reasonOrThrow(): String? {
        val trimmed = reason?.trim()?.takeIf { it.isNotEmpty() }
        if (hidden && trimmed == null) throw ValidationException("reason", "Sabab ko'rsatilishi shart")
        return trimmed
    }

    private fun ModerationPostRow.toView() = ModerationPostView(
        id = id.toString(),
        alias = alias,
        tint = tint,
        topic = topic,
        body = body,
        hidden = status == ContentStatus.HIDDEN,
        hiddenReason = hiddenReason,
        createdAt = createdAt,
        likeCount = likeCount,
        commentCount = commentCount,
        openReports = openReports,
    )

    private fun ModerationCommentRow.toView() = ModerationCommentView(
        id = id.toString(),
        postId = postId.toString(),
        alias = alias,
        tint = tint,
        body = body,
        hidden = status == ContentStatus.HIDDEN,
        hiddenReason = hiddenReason,
        createdAt = createdAt,
        openReports = openReports,
    )

    private fun ReportRecord.toView() = ModerationReportView(
        id = id.toString(),
        postId = postId?.toString(),
        commentId = commentId?.toString(),
        reason = reason,
        note = note,
        excerpt = excerpt,
        targetHidden = targetStatus == ContentStatus.HIDDEN,
        createdAt = createdAt,
        resolvedAt = resolvedAt,
        resolution = resolution,
    )

    companion object {
        const val ACTION_DISMISS = "dismiss"
        const val ACTION_HIDE = "hide"
        const val RESOLUTION_DISMISSED = "dismissed"
        const val RESOLUTION_HIDDEN = "hidden"
    }
}

private fun AdminPrincipal.entry(
    action: String,
    entityType: String,
    entityId: String,
    reason: String? = null,
    metadata: Map<String, String> = emptyMap(),
    context: RequestContext,
) = AuditEntry(
    actorType = ActorType.ADMIN,
    actorId = adminId,
    actorLabel = role.name.lowercase(),
    action = action,
    entityType = entityType,
    entityId = entityId,
    reason = reason,
    metadata = metadata,
    ip = context.ip,
    userAgent = context.userAgent,
)

/** Page 13 of the admin panel: the moderation queue. Support may read; Owner and Admin act. */
fun Route.adminCommunityRoutes(moderation: CommunityModerationService) {
    authenticate(ADMIN_AUTH) {
        route("/admin/community") {
            get("/stats") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT, AdminRole.ANALYST)
                call.respond(moderation.stats())
            }

            get("/posts") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT, AdminRole.ANALYST)
                call.respond(
                    moderation.posts(
                        hidden = call.request.queryParameters["hidden"]?.toBooleanStrictOrNull(),
                        topic = call.enumParameter<CommunityTopic>("topic"),
                        reportedOnly = call.request.queryParameters["reported"].toBoolean(),
                        limit = call.intParameter("limit", default = 50, max = 200),
                        offset = call.intParameter("offset", default = 0, max = Int.MAX_VALUE).toLong(),
                    ),
                )
            }

            get("/posts/{id}/comments") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT, AdminRole.ANALYST)
                call.respond(moderation.comments(parseUuid(call.parameters["id"].orEmpty(), "id")))
            }

            post("/posts/{id}/hide") {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val request = call.receive<HideRequest>()
                moderation.setPostHidden(parseUuid(call.parameters["id"].orEmpty(), "id"), request, admin, call.requestContext())
                call.respond(Ack())
            }

            post("/posts/{id}/restrict-author") {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val request = call.receive<RestrictAuthorRequest>()
                moderation.restrictAuthor(parseUuid(call.parameters["id"].orEmpty(), "id"), request, admin, call.requestContext())
                call.respond(Ack())
            }

            post("/comments/{id}/hide") {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val request = call.receive<HideRequest>()
                moderation.setCommentHidden(parseUuid(call.parameters["id"].orEmpty(), "id"), request, admin, call.requestContext())
                call.respond(Ack())
            }

            get("/reports") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT, AdminRole.ANALYST)
                call.respond(
                    moderation.reports(
                        openOnly = call.request.queryParameters["open"]?.toBooleanStrictOrNull() ?: true,
                        limit = call.intParameter("limit", default = 50, max = 200),
                        offset = call.intParameter("offset", default = 0, max = Int.MAX_VALUE).toLong(),
                    ),
                )
            }

            post("/reports/{id}/resolve") {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val request = call.receive<ResolveReportRequest>()
                moderation.resolveReport(parseUuid(call.parameters["id"].orEmpty(), "id"), request, admin, call.requestContext())
                call.respond(Ack())
            }
        }
    }
}
