package uz.sadora.server.promo

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import uz.sadora.server.api.requestContext
import uz.sadora.server.api.requireAdminRole
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.plugins.ADMIN_AUTH
import uz.sadora.server.plugins.AdminPrincipal
import uz.sadora.server.plugins.AdminRole

@Serializable
data class PromoVideoView(
    /** The public page the video plays on. */
    val pageUrl: String,
    val video: PromoVideoFile?,
    val maxBytes: Long,
    val chunkBytes: Int,
)

@Serializable
data class PromoVideoFile(
    /** The API's own copy of the file, for the panel's preview. */
    val url: String,
    val mimeType: String,
    val sizeBytes: Long,
    val updatedAt: Instant,
)

@Serializable
data class StartPromoUploadRequest(val fileName: String, val sizeBytes: Long)

@Serializable
data class PromoUploadProgress(val uploadId: String, val receivedBytes: Long)

/**
 * The video itself. Public, as the page it plays on is; the panel's preview reads it
 * from here because a laptop has no nginx beside the API. Ranges are answered, which
 * Safari requires of anything it is asked to play.
 */
fun Route.promoVideoRoutes(store: PromoVideoStore) {
    route("/promo-video/file") {
        install(PartialContent)
        get {
            val (file, manifest) = store.currentFile() ?: throw NotFoundException("Video yo'q")
            // The address carries the upload time, so a new video is a new address.
            call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
            call.respond(LocalFileContent(file, ContentType.parse(manifest.mimeType)))
        }
    }
}

fun Route.adminPromoVideoRoutes(store: PromoVideoStore, audit: AuditService, pageUrl: String) {
    suspend fun view() = PromoVideoView(
        pageUrl = pageUrl,
        video = store.current()?.let {
            PromoVideoFile("/v1/promo-video/file?v=${it.updatedAt.toEpochMilliseconds()}", it.mimeType, it.sizeBytes, it.updatedAt)
        },
        maxBytes = PromoVideoStore.MAX_BYTES,
        chunkBytes = PromoVideoStore.CHUNK_BYTES,
    )

    suspend fun ApplicationCall.record(admin: AdminPrincipal, action: String, metadata: Map<String, String> = emptyMap()) {
        val context = requestContext()
        audit.record(
            AuditEntry(
                actorType = ActorType.ADMIN,
                actorId = admin.adminId,
                action = action,
                entityType = "promo_video",
                metadata = metadata,
                ip = context.ip,
                userAgent = context.userAgent,
            ),
        )
    }

    authenticate(ADMIN_AUTH) {
        route("/admin/promo-video") {
            get {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT, AdminRole.ANALYST)
                call.respond(view())
            }
            delete {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                if (store.remove()) call.record(admin, AuditActions.PROMO_VIDEO_REMOVED)
                call.respond(view())
            }
            route("/uploads") {
                post {
                    call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                    val request = call.receive<StartPromoUploadRequest>()
                    call.respond(PromoUploadProgress(store.start(request.fileName, request.sizeBytes), 0))
                }
                /** One piece, as raw bytes; `offset` is where in the file it belongs. */
                put("/{id}") {
                    call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                    val id = call.parameters["id"].orEmpty()
                    val offset = call.request.queryParameters["offset"]?.toLongOrNull()
                        ?: throw ValidationException("offset", "Butun son bo'lishi kerak")
                    call.respond(PromoUploadProgress(id, store.append(id, offset, call.receive<ByteArray>())))
                }
                post("/{id}/complete") {
                    val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                    val manifest = store.complete(call.parameters["id"].orEmpty())
                    call.record(
                        admin,
                        AuditActions.PROMO_VIDEO_UPLOADED,
                        mapOf("file" to manifest.file, "sizeBytes" to manifest.sizeBytes.toString()),
                    )
                    call.respond(view())
                }
            }
        }
    }
}
