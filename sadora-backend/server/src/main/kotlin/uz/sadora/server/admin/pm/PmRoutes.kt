package uz.sadora.server.admin.pm

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import uz.sadora.contract.Ack
import uz.sadora.server.api.requestContext
import uz.sadora.server.api.requireAdminRole
import uz.sadora.server.core.ForbiddenException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.now
import uz.sadora.server.core.parseUuid
import uz.sadora.server.plugins.ADMIN_AUTH
import uz.sadora.server.plugins.AdminRole
import uz.sadora.server.plugins.PM_TOKEN_AUTH

/**
 * The "Loyiha" section's API under /v1/admin/pm. Owner, Admin and Developer reach all of
 * it; archiving an epic or a release, and anything for good, is narrower and says so.
 */
fun Route.pmRoutes(
    tasks: PmTaskService,
    docs: PmDocService,
    tokens: PmTokenService,
    commits: PmCommitSync,
    backups: PmBackupService,
) {
    // The panel's sign-in or a tools/pm token; the token provider is mounted nowhere else.
    authenticate(ADMIN_AUTH, PM_TOKEN_AUTH) {
        route("/admin/pm") {

            get("/meta") {
                val principal = call.requireAdminRole(*PM_ROLES)
                call.respond(
                    PmMeta(
                        me = tasks.person(principal.adminId),
                        people = tasks.people(),
                        epics = tasks.epics(),
                        releases = tasks.releases(),
                        commits = commits.state(),
                    ),
                )
            }

            // --- tasks ----------------------------------------------------------
            get("/tasks") {
                call.requireAdminRole(*PM_ROLES)
                call.respond(tasks.list(call.taskFilter()))
            }

            post("/tasks") {
                val principal = call.requireAdminRole(*PM_ROLES)
                call.respond(tasks.create(call.receive<CreatePmTaskRequest>(), principal))
            }

            get("/tasks/{key}") {
                call.requireAdminRole(*PM_ROLES)
                val task = tasks.get(call.key())
                call.respond(task.copy(mentionedIn = docs.mentioning(task.key)))
            }

            patch("/tasks/{key}") {
                val principal = call.requireAdminRole(*PM_ROLES)
                call.respond(tasks.update(call.key(), call.receive<UpdatePmTaskRequest>(), principal))
            }

            post("/tasks/{key}/archive") {
                val principal = call.requireAdminRole(*PM_ROLES)
                call.respond(tasks.archive(call.key(), principal))
            }

            post("/tasks/{key}/restore") {
                val principal = call.requireAdminRole(*PM_ROLES)
                call.respond(tasks.restore(call.key(), principal))
            }

            delete("/tasks/{key}") {
                val principal = call.requireAdminRole(*PM_ROLES)
                tasks.delete(call.key(), principal, call.requestContext())
                call.respond(Ack())
            }

            post("/tasks/{key}/comments") {
                val principal = call.requireAdminRole(*PM_ROLES)
                call.respond(tasks.comment(call.key(), call.receive<PmCommentRequest>(), principal))
            }

            delete("/tasks/{key}/comments/{id}") {
                val principal = call.requireAdminRole(*PM_ROLES)
                tasks.deleteComment(call.key(), parseUuid(call.parameters["id"].orEmpty()), principal)
                call.respond(Ack())
            }

            get("/search") {
                call.requireAdminRole(*PM_ROLES)
                val text = call.request.queryParameters["q"].orEmpty().trim()
                if (text.length < 2) {
                    call.respond(PmSearchResults(emptyList(), emptyList()))
                } else {
                    call.respond(PmSearchResults(tasks.list(PmTaskFilter(text = text, withSubtasks = true)).take(20), docs.search(text)))
                }
            }

            // --- docs -----------------------------------------------------------
            get("/docs") {
                call.requireAdminRole(*PM_ROLES)
                call.respond(docs.tree(call.request.queryParameters["archived"] == "true"))
            }

            post("/docs") {
                val principal = call.requireAdminRole(*PM_ROLES)
                call.respond(docs.create(call.receive<CreatePmDocRequest>(), principal))
            }

            get("/docs/{id}") {
                call.requireAdminRole(*PM_ROLES)
                call.respond(docs.get(call.id()))
            }

            put("/docs/{id}") {
                val principal = call.requireAdminRole(*PM_ROLES)
                call.respond(docs.update(call.id(), call.receive<UpdatePmDocRequest>(), principal))
            }

            get("/docs/{id}/versions") {
                call.requireAdminRole(*PM_ROLES)
                call.respond(docs.versions(call.id()))
            }

            get("/docs/{id}/versions/{version}") {
                call.requireAdminRole(*PM_ROLES)
                val version = call.parameters["version"]?.toIntOrNull() ?: throw ValidationException("version", "Versiya raqami noto'g'ri")
                call.respond(docs.version(call.id(), version))
            }

            post("/docs/{id}/archive") {
                call.requireAdminRole(*PM_ROLES)
                call.respond(docs.archive(call.id()))
            }

            post("/docs/{id}/restore") {
                call.requireAdminRole(*PM_ROLES)
                call.respond(docs.restore(call.id()))
            }

            delete("/docs/{id}") {
                val principal = call.requireAdminRole(*PM_ROLES)
                docs.delete(call.id(), principal, call.requestContext())
                call.respond(Ack())
            }

            // The raw bytes of one picture; the server decides what it is from them.
            post("/images") {
                val principal = call.requireAdminRole(*PM_ROLES)
                call.respond(docs.saveImage(call.receive<ByteArray>(), principal))
            }

            // A restored backup's picture, under its old id; see PmDocService.restoreImage.
            put("/images/{id}") {
                call.requireAdminRole(AdminRole.OWNER)
                docs.restoreImage(call.id(), call.receive<ByteArray>())
                call.respond(Ack())
            }

            get("/images/{id}") {
                call.requireAdminRole(*PM_ROLES)
                val (type, bytes) = docs.image(call.id())
                call.response.header(HttpHeaders.CacheControl, "private, max-age=86400, immutable")
                call.respondBytes(bytes, ContentType.parse(type))
            }

            // --- backup ---------------------------------------------------------
            // The whole section as one file, for the Owner; tools/pm `backup` takes it off the server.
            get("/backup") {
                call.requireAdminRole(AdminRole.OWNER)
                call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"sadora-loyiha-${now().toString().take(10)}.json\"")
                call.respondText(backups.snapshot(), ContentType.Application.Json)
            }

            // Only into an empty section; see PmBackupService.
            post("/restore") {
                val principal = call.requireAdminRole(AdminRole.OWNER)
                backups.restore(call.receiveText(), principal, call.requestContext())
                call.respond(Ack())
            }

            // --- tokens ---------------------------------------------------------
            get("/tokens") {
                val principal = call.requireAdminRole(*PM_ROLES)
                if (principal.viaToken) throw ForbiddenException(message = "Tokenlar faqat panelda boshqariladi")
                call.respond(tokens.list(principal))
            }

            post("/tokens") {
                val principal = call.requireAdminRole(*PM_ROLES)
                call.respond(tokens.create(call.receive<CreatePmTokenRequest>(), principal, call.requestContext()))
            }

            delete("/tokens/{id}") {
                val principal = call.requireAdminRole(*PM_ROLES)
                tokens.revoke(call.id(), principal, call.requestContext())
                call.respond(Ack())
            }

            // --- epics and releases ---------------------------------------------
            get("/epics") {
                call.requireAdminRole(*PM_ROLES)
                call.respond(tasks.epics(call.request.queryParameters["archived"] == "true"))
            }

            post("/epics") {
                val principal = call.requireAdminRole(*PM_ROLES)
                call.respond(tasks.createEpic(call.receive<SavePmEpicRequest>(), principal))
            }

            put("/epics/{id}") {
                call.requireAdminRole(*PM_ROLES)
                call.respond(tasks.updateEpic(call.id(), call.receive<SavePmEpicRequest>()))
            }

            post("/epics/{id}/archive") {
                call.requireAdminRole(*PM_LEAD_ROLES)
                call.respond(tasks.setEpicArchived(call.id(), true))
            }

            post("/epics/{id}/restore") {
                call.requireAdminRole(*PM_LEAD_ROLES)
                call.respond(tasks.setEpicArchived(call.id(), false))
            }

            get("/releases") {
                call.requireAdminRole(*PM_ROLES)
                call.respond(tasks.releases(call.request.queryParameters["archived"] == "true"))
            }

            post("/releases") {
                val principal = call.requireAdminRole(*PM_ROLES)
                call.respond(tasks.createRelease(call.receive<SavePmReleaseRequest>(), principal))
            }

            put("/releases/{id}") {
                call.requireAdminRole(*PM_ROLES)
                call.respond(tasks.updateRelease(call.id(), call.receive<SavePmReleaseRequest>()))
            }

            post("/releases/{id}/archive") {
                call.requireAdminRole(*PM_LEAD_ROLES)
                call.respond(tasks.setReleaseArchived(call.id(), true))
            }

            post("/releases/{id}/restore") {
                call.requireAdminRole(*PM_LEAD_ROLES)
                call.respond(tasks.setReleaseArchived(call.id(), false))
            }
        }
    }
}

private fun RoutingCall.key(): String = parameters["key"].orEmpty()

private fun RoutingCall.id() = parseUuid(parameters["id"].orEmpty())

private fun RoutingCall.taskFilter(): PmTaskFilter {
    val query = request.queryParameters
    fun <T> parse(name: String, read: (String) -> T): T? = query[name]?.takeIf { it.isNotBlank() }?.let {
        runCatching { read(it) }.getOrElse { throw ValidationException(name, "Qiymat noto'g'ri") }
    }
    return PmTaskFilter(
        statuses = query["status"]?.split(',')?.filter { it.isNotBlank() }
            ?.map { key -> runCatching { PmStatus.of(key) }.getOrElse { throw ValidationException("status", "Holat noto'g'ri") } }
            ?.toSet().orEmpty(),
        assigneeId = parse("assignee") { parseUuid(it, "assignee") },
        epicId = parse("epic") { parseUuid(it, "epic") },
        releaseId = parse("release") { parseUuid(it, "release") },
        component = query["component"]?.takeIf { it.isNotBlank() },
        label = query["label"]?.takeIf { it.isNotBlank() }?.lowercase(),
        type = parse("type", PmTaskType::of),
        priority = parse("priority", PmPriority::of),
        text = query["q"],
        archived = query["archived"] == "true",
        withSubtasks = query["subtasks"] == "true",
    )
}
