package uz.sadora.server.photo

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import uz.sadora.contract.Ack
import uz.sadora.contract.PhotoUpload
import uz.sadora.server.api.requestContext
import uz.sadora.server.api.requireAdminRole
import uz.sadora.server.api.requireUserId
import uz.sadora.server.core.parseUuid
import uz.sadora.server.plugins.ADMIN_AUTH
import uz.sadora.server.plugins.AdminRole
import uz.sadora.server.plugins.USER_AUTH

@Serializable
data class RemovePhotoRequest(val reason: String? = null)

/**
 * Profile photos. Every GET answers bytes that belong to one person, so they are cached
 * privately and only by the version in the URL: a new photo is a new URL.
 */
fun Route.photoRoutes(photos: PhotoService) {
    authenticate(USER_AUTH) {
        route("/me/photo") {
            get { call.respondPhoto(photos.mine(call.requireUserId())) }
            put {
                val request = call.receive<PhotoUpload>()
                call.respond(photos.setMine(call.requireUserId(), request))
            }
            delete {
                photos.removeMine(call.requireUserId())
                call.respond(Ack())
            }
        }
        route("/doctor/photo") {
            put {
                val request = call.receive<PhotoUpload>()
                call.respond(photos.setDoctor(call.requireUserId(), request))
            }
            delete {
                photos.removeDoctor(call.requireUserId())
                call.respond(Ack())
            }
        }
        get("/doctors/{id}/photo") {
            call.respondPhoto(photos.doctor(call.requireUserId(), parseUuid(call.parameters["id"].orEmpty(), "id")))
        }
        get("/community/conversations/{id}/photo") {
            call.respondPhoto(photos.inConversation(call.requireUserId(), parseUuid(call.parameters["id"].orEmpty(), "id")))
        }
    }
}

fun Route.adminPhotoRoutes(photos: PhotoService) {
    authenticate(ADMIN_AUTH) {
        route("/admin/doctors/{id}/photo") {
            get {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT)
                call.respondPhoto(photos.adminDoctor(parseUuid(call.parameters["id"].orEmpty(), "id")))
            }
            delete {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val reason = runCatching { call.receive<RemovePhotoRequest>() }.getOrNull()?.reason?.trim()?.takeIf { it.isNotEmpty() }?.take(300)
                photos.adminRemoveDoctor(parseUuid(call.parameters["id"].orEmpty(), "id"), reason, admin, call.requestContext())
                call.respond(Ack())
            }
        }
    }
}

private suspend fun ApplicationCall.respondPhoto(bytes: ByteArray) {
    response.header(HttpHeaders.CacheControl, "private, max-age=31536000, immutable")
    respondBytes(bytes, ContentType.Image.JPEG)
}
