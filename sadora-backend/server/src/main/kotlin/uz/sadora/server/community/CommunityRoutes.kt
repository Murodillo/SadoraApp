package uz.sadora.server.community

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
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
import uz.sadora.contract.Ack
import uz.sadora.contract.CloseConsultationRequest
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.CreateCommentRequest
import uz.sadora.contract.CreatePostRequest
import uz.sadora.contract.Language
import uz.sadora.contract.PostViewsRequest
import uz.sadora.contract.ReportRequest
import uz.sadora.contract.SendMessageRequest
import uz.sadora.contract.StartConversationRequest
import uz.sadora.contract.UpdateIdentityRequest
import uz.sadora.server.api.enumParameter
import uz.sadora.server.api.intParameter
import uz.sadora.server.api.requireUserId
import uz.sadora.server.core.parseUuid
import uz.sadora.server.plugins.USER_AUTH

/**
 * The secret chat. Every route is the caller's own view of the room: her alias, the
 * feed with her reactions folded in, and writes under her alias. No route takes or
 * returns a user id.
 */
fun Route.communityRoutes(community: CommunityService, messaging: MessagingService) {
    authenticate(USER_AUTH) {
        route("/community") {

            get("/me") {
                call.respond(community.identity(call.requireUserId()))
            }

            /** Her bio and whether her door is open. */
            put("/me") {
                val request = call.receive<UpdateIdentityRequest>()
                call.respond(community.updateIdentity(call.requireUserId(), request))
            }

            // An alias's page, and the viewer's block on it. Addressed by alias — the
            // one public, permanent name an account has in the room.
            route("/profiles/{alias}") {
                get {
                    call.respond(community.profile(call.requireUserId(), call.alias()))
                }
                /** Her posts a page at a time; the page above carries only the first few. */
                get("/posts") {
                    call.respond(
                        community.profilePosts(
                            viewer = call.requireUserId(),
                            alias = call.alias(),
                            limit = call.intParameter("limit", default = CommunityService.PROFILE_POSTS, max = CommunityService.MAX_PROFILE_POSTS),
                            offset = call.intParameter("offset", default = 0, max = Int.MAX_VALUE).toLong(),
                        ),
                    )
                }
                put("/block") {
                    call.respond(community.setBlocked(call.requireUserId(), call.alias(), blocked = true))
                }
                delete("/block") {
                    call.respond(community.setBlocked(call.requireUserId(), call.alias(), blocked = false))
                }
            }

            route("/conversations") {
                /** `scope=patients` is the doctor app's list; `personal` everything else. */
                get {
                    val scope = when (call.request.queryParameters["scope"]) {
                        "patients" -> ConversationScope.PATIENTS
                        "personal" -> ConversationScope.PERSONAL
                        else -> ConversationScope.ALL
                    }
                    call.respond(
                        messaging.conversations(
                            userId = call.requireUserId(),
                            scope = scope,
                            limit = call.intParameter("limit", default = MessagingService.MAX_THREADS, max = MessagingService.MAX_THREADS),
                            before = call.instantParameter("before"),
                        ),
                    )
                }
                post {
                    val request = call.receive<StartConversationRequest>()
                    call.respond(HttpStatusCode.Created, messaging.start(call.requireUserId(), request))
                }
                route("/{id}") {
                    /** The latest `limit` lines; older ones come from `/messages?before=`. */
                    get {
                        val limit = call.intParameter("limit", default = MessagingService.MAX_MESSAGES, max = MessagingService.MAX_MESSAGES)
                        call.respond(messaging.thread(call.requireUserId(), call.conversationId(), limit))
                    }
                    /** Scrolling up: the `limit` lines before the message `before`. */
                    get("/messages") {
                        val before = parseUuid(call.request.queryParameters["before"].orEmpty(), "before")
                        val limit = call.intParameter("limit", default = MESSAGE_PAGE, max = MessagingService.MAX_MESSAGES)
                        call.respond(messaging.olderMessages(call.requireUserId(), call.conversationId(), before, limit))
                    }
                    post("/messages") {
                        val request = call.receive<SendMessageRequest>()
                        call.respond(HttpStatusCode.Created, messaging.send(call.requireUserId(), call.conversationId(), request))
                    }
                    /** A photo in the thread. Private, so never cached on the way. */
                    get("/messages/{messageId}/image") {
                        val image = messaging.image(call.requireUserId(), call.conversationId(), call.messageId())
                        call.response.header(HttpHeaders.CacheControl, "private, no-store")
                        call.respondBytes(image.bytes, ContentType.parse(image.mimeType))
                    }
                    /** The record a patient attached, assembled now, in the asked language. */
                    get("/messages/{messageId}/record") {
                        val language = call.request.queryParameters["lang"]
                            ?.let { raw -> Language.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
                        call.response.header(HttpHeaders.CacheControl, "no-store")
                        call.respond(messaging.record(call.requireUserId(), call.conversationId(), call.messageId(), language))
                    }
                    post("/typing") {
                        messaging.typing(call.requireUserId(), call.conversationId())
                        call.respond(Ack())
                    }
                    /** The doctor ends a consultation, with her advice for the patient if she writes one. */
                    post("/close") {
                        val request = runCatching { call.receive<CloseConsultationRequest>() }.getOrDefault(CloseConsultationRequest())
                        call.respond(messaging.close(call.requireUserId(), call.conversationId(), request.summary))
                    }
                    post("/report") {
                        val request = call.receive<ReportRequest>()
                        messaging.report(call.requireUserId(), call.conversationId(), request)
                        call.respond(Ack())
                    }
                }
            }

            route("/posts") {
                get {
                    call.respond(
                        community.feed(
                            userId = call.requireUserId(),
                            topic = call.enumParameter<CommunityTopic>("topic"),
                            savedOnly = call.request.queryParameters["saved"].toBoolean(),
                            doctorsOnly = call.request.queryParameters["doctors"].toBoolean(),
                            limit = call.intParameter("limit", default = 50, max = 100),
                            offset = call.intParameter("offset", default = 0, max = Int.MAX_VALUE).toLong(),
                        ),
                    )
                }

                post {
                    val request = call.receive<CreatePostRequest>()
                    call.respond(HttpStatusCode.Created, community.createPost(call.requireUserId(), request))
                }

                /** The posts that were on her screen; see [CommunityService.recordViews]. */
                post("/views") {
                    val request = call.receive<PostViewsRequest>()
                    community.recordViews(call.requireUserId(), request.ids)
                    call.respond(Ack())
                }

                route("/{id}") {
                    get {
                        call.respond(community.post(call.requireUserId(), call.postId()))
                    }

                    delete {
                        community.deletePost(call.requireUserId(), call.postId())
                        call.respond(Ack())
                    }

                    get("/comments") {
                        call.respond(
                            community.comments(
                                userId = call.requireUserId(),
                                postId = call.postId(),
                                limit = call.intParameter("limit", default = CommunityService.MAX_COMMENTS, max = CommunityService.MAX_COMMENTS),
                                offset = call.intParameter("offset", default = 0, max = Int.MAX_VALUE).toLong(),
                            ),
                        )
                    }

                    post("/comments") {
                        val request = call.receive<CreateCommentRequest>()
                        call.respond(
                            HttpStatusCode.Created,
                            community.addComment(call.requireUserId(), call.postId(), request),
                        )
                    }

                    put("/like") {
                        call.respond(community.setLiked(call.requireUserId(), call.postId(), liked = true))
                    }

                    delete("/like") {
                        call.respond(community.setLiked(call.requireUserId(), call.postId(), liked = false))
                    }

                    put("/save") {
                        call.respond(community.setSaved(call.requireUserId(), call.postId(), saved = true))
                    }

                    delete("/save") {
                        call.respond(community.setSaved(call.requireUserId(), call.postId(), saved = false))
                    }

                    post("/report") {
                        val request = call.receive<ReportRequest>()
                        community.reportPost(call.requireUserId(), call.postId(), request)
                        call.respond(Ack())
                    }
                }
            }

            route("/comments/{id}") {
                delete {
                    community.deleteComment(call.requireUserId(), call.commentId())
                    call.respond(Ack())
                }

                post("/report") {
                    val request = call.receive<ReportRequest>()
                    community.reportComment(call.requireUserId(), call.commentId(), request)
                    call.respond(Ack())
                }
            }
        }
    }
}

private const val MESSAGE_PAGE = 50

/** An ISO-8601 instant in the query, or null when it is absent. */
private fun io.ktor.server.application.ApplicationCall.instantParameter(name: String): kotlin.time.Instant? {
    val raw = request.queryParameters[name]?.takeIf { it.isNotBlank() } ?: return null
    return runCatching { kotlin.time.Instant.parse(raw) }.getOrElse {
        throw uz.sadora.server.core.ValidationException(name, "Sana noto'g'ri")
    }
}

private fun io.ktor.server.application.ApplicationCall.postId() = parseUuid(parameters["id"].orEmpty(), "id")
private fun io.ktor.server.application.ApplicationCall.commentId() = parseUuid(parameters["id"].orEmpty(), "id")
private fun io.ktor.server.application.ApplicationCall.conversationId() = parseUuid(parameters["id"].orEmpty(), "id")
private fun io.ktor.server.application.ApplicationCall.messageId() = parseUuid(parameters["messageId"].orEmpty(), "messageId")

private fun io.ktor.server.application.ApplicationCall.alias(): String {
    val alias = parameters["alias"].orEmpty().trim()
    if (alias.isEmpty() || alias.length > 64) throw uz.sadora.server.core.ValidationException("alias", "Taxallus noto'g'ri")
    return alias
}
