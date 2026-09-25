package uz.sadora.server.content

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
import uz.sadora.contract.CreateArticleRequest
import uz.sadora.contract.PublishArticleRequest
import uz.sadora.contract.SaveArticleRequest
import uz.sadora.server.api.requireAdminRole
import uz.sadora.server.api.requireUserId
import uz.sadora.server.core.ValidationException
import uz.sadora.server.plugins.ADMIN_AUTH
import uz.sadora.server.plugins.AdminRole
import uz.sadora.server.plugins.USER_AUTH

/**
 * The library, read by the app and written by the admin panel.
 *
 * The two sides are separate routes on purpose rather than one route with a role check:
 * the app's routes cannot return a draft at all, because they never ask for one.
 */
fun Route.contentRoutes(content: ContentService) {
    authenticate(USER_AUTH) {
        route("/articles") {
            get {
                val category = call.request.queryParameters["category"]?.takeIf { it.isNotBlank() }
                call.respond(content.feed(call.requireUserId(), category))
            }

            get("/{slug}") {
                call.respond(content.article(call.requireUserId(), call.slug()))
            }
        }
    }
}

/**
 * Content administration. Reading the library is open to every admin role; writing it is
 * [AdminRole.canManageContent], which is what that method was named for.
 */
fun Route.adminContentRoutes(content: ContentService) {
    authenticate(ADMIN_AUTH) {
        route("/admin/content") {

            get("/categories") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT, AdminRole.ANALYST)
                call.respond(content.categories())
            }

            route("/articles") {
                get {
                    call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT, AdminRole.ANALYST)
                    call.respond(content.adminList())
                }

                post {
                    call.requireContentManager()
                    val request = call.receive<CreateArticleRequest>()
                    call.respond(HttpStatusCode.Created, content.create(request))
                }

                route("/{slug}") {
                    get {
                        call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT, AdminRole.ANALYST)
                        call.respond(content.adminArticle(call.slug()))
                    }

                    put {
                        call.requireContentManager()
                        val request = call.receive<SaveArticleRequest>()
                        call.respond(content.update(call.slug(), request))
                    }

                    put("/published") {
                        call.requireContentManager()
                        val request = call.receive<PublishArticleRequest>()
                        call.respond(content.setPublished(call.slug(), request.published))
                    }

                    delete {
                        call.requireContentManager()
                        content.delete(call.slug())
                        call.respond(Ack())
                    }
                }
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.requireContentManager() =
    requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)

private fun io.ktor.server.application.ApplicationCall.slug(): String =
    parameters["slug"]?.takeIf { it.isNotBlank() }
        ?: throw ValidationException("slug", "Ko'rsatilishi shart")
