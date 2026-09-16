package uz.sadora.server.community

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import uz.sadora.contract.Ack
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.CreateCommentRequest
import uz.sadora.contract.CreatePostRequest
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
                put("/block") {
                    call.respond(community.setBlocked(call.requireUserId(), call.alias(), blocked = true))
                }
                delete("/block") {
                    call.respond(community.setBlocked(call.requireUserId(), call.alias(), blocked = false))
                }
            }

            route("/conversations") {
                get {
                    call.respond(messaging.conversations(call.requireUserId()))
                }
                post {
                    val request = call.receive<StartConversationRequest>()
                    call.respond(HttpStatusCode.Created, messaging.start(call.requireUserId(), request))
                }
                route("/{id}") {
                    get {
                        call.respond(messaging.thread(call.requireUserId(), call.conversationId()))
                    }
                    post("/messages") {
                        val request = call.receive<SendMessageRequest>()
                        call.respond(HttpStatusCode.Created, messaging.send(call.requireUserId(), call.conversationId(), request))
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
                            limit = call.intParameter("limit", default = 50, max = 100),
                            offset = call.intParameter("offset", default = 0, max = Int.MAX_VALUE).toLong(),
                        ),
                    )
                }

                post {
                    val request = call.receive<CreatePostRequest>()
                    call.respond(HttpStatusCode.Created, community.createPost(call.requireUserId(), request))
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
                        call.respond(community.comments(call.requireUserId(), call.postId()))
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

private fun io.ktor.server.application.ApplicationCall.postId() = parseUuid(parameters["id"].orEmpty(), "id")
private fun io.ktor.server.application.ApplicationCall.commentId() = parseUuid(parameters["id"].orEmpty(), "id")
private fun io.ktor.server.application.ApplicationCall.conversationId() = parseUuid(parameters["id"].orEmpty(), "id")

private fun io.ktor.server.application.ApplicationCall.alias(): String {
    val alias = parameters["alias"].orEmpty().trim()
    if (alias.isEmpty() || alias.length > 64) throw uz.sadora.server.core.ValidationException("alias", "Taxallus noto'g'ri")
    return alias
}
