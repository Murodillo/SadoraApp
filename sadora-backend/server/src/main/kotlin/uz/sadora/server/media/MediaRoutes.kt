package uz.sadora.server.media

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.Dispatchers
import uz.sadora.contract.Ack
import uz.sadora.contract.CompleteMediaUploadRequest
import uz.sadora.contract.StartMediaUploadRequest
import uz.sadora.server.api.requireUserId
import uz.sadora.server.core.ForbiddenException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.parseUuid
import uz.sadora.server.plugins.USER_AUTH

/**
 * Uploads and reading. Everything but the signed file is behind the session; the signed
 * file is open to whoever holds its address for the few minutes it is good, because a
 * video player cannot send the session's header.
 */
fun Route.mediaRoutes(media: MediaService) {
    authenticate(USER_AUTH) {
        route("/media") {
            route("/uploads") {
                post {
                    val request = call.receive<StartMediaUploadRequest>()
                    call.respond(media.start(call.requireUserId(), request))
                }
                /** Where an upload stands: what to send next after a dropped connection. */
                get("/{id}") {
                    call.respond(media.progress(call.requireUserId(), call.mediaId()))
                }
                /** One piece, as raw bytes; `offset` is where in the file it belongs. */
                put("/{id}") {
                    val offset = call.request.queryParameters["offset"]?.toLongOrNull()
                        ?: throw ValidationException("offset", "Butun son bo'lishi kerak")
                    call.respond(media.append(call.requireUserId(), call.mediaId(), offset, call.receive<ByteArray>()))
                }
                post("/{id}/complete") {
                    val request = runCatching { call.receive<CompleteMediaUploadRequest>() }.getOrDefault(CompleteMediaUploadRequest())
                    call.respond(media.complete(call.requireUserId(), call.mediaId(), request.posterBase64))
                }
                delete("/{id}") {
                    media.cancel(call.requireUserId(), call.mediaId())
                    call.respond(Ack())
                }
            }
            /** A thumbnail or a poster. Private, so never cached on the way. */
            get("/{id}/thumb") {
                val bytes = media.thumb(call.requireUserId(), call.mediaId())
                call.response.header(HttpHeaders.CacheControl, "private, no-store")
                call.respondBytes(bytes, ContentType.Image.JPEG)
            }
            /** A signed address for the photo itself or the video: `v=full` (default) or `thumb`. */
            get("/{id}/link") {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.respond(media.link(call.requireUserId(), call.mediaId(), call.variant()))
            }
        }
    }

    route("/media/{id}/file") {
        // Ranges are answered: a player seeks, and Safari asks for a range before it plays.
        install(PartialContent)
        get {
            val expires = call.request.queryParameters["exp"]?.toLongOrNull() ?: throw ForbiddenException(message = MediaService.LINK_EXPIRED)
            val signature = call.request.queryParameters["sig"].orEmpty()
            val content = media.signed(call.mediaId(), call.variant(), expires, signature)
            // The address expires on its own; a phone may keep what it fetched no longer than that.
            call.response.header(HttpHeaders.CacheControl, "private, max-age=600")
            call.respond(SealedContent(content))
        }
    }
}

/** A sealed file streamed as plaintext, a range at a time when one is asked for. */
private class SealedContent(private val content: MediaContent) : OutgoingContent.ReadChannelContent() {
    override val contentType: ContentType = ContentType.parse(content.mimeType)
    override val contentLength: Long = content.sizeBytes
    override fun readFrom(): ByteReadChannel = readFrom(0 until content.sizeBytes)
    override fun readFrom(range: LongRange): ByteReadChannel =
        if (range.isEmpty()) ByteReadChannel.Empty else content.open(range).toByteReadChannel(Dispatchers.IO)
}

private fun io.ktor.server.application.ApplicationCall.mediaId() = parseUuid(parameters["id"].orEmpty(), "id")

private fun io.ktor.server.application.ApplicationCall.variant(): MediaVariant =
    when (request.queryParameters["v"]) {
        null, "", MediaVariant.FULL.extension -> MediaVariant.FULL
        MediaVariant.THUMB.extension -> MediaVariant.THUMB
        else -> throw ValidationException("v", "full yoki thumb")
    }
