package uz.sadora.server.notify

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import uz.sadora.contract.Ack
import uz.sadora.contract.FrequencyCaps
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.UpdateNotificationSettingsRequest
import uz.sadora.server.api.intParameter
import uz.sadora.server.api.requestContext
import uz.sadora.server.api.requireAdminRole
import uz.sadora.server.api.requireUserId
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.plugins.ADMIN_AUTH
import uz.sadora.server.plugins.AdminRole
import uz.sadora.server.plugins.USER_AUTH

@Serializable
data class TemplateView(
    val key: String,
    val language: String,
    val category: NotificationCategory,
    val title: String,
    val body: String,
    val active: Boolean = true,
)

fun Route.notificationRoutes(notifications: NotificationService) {
    authenticate(USER_AUTH) {
        route("/notifications") {
            get("/settings") {
                call.respond(notifications.settings(call.requireUserId()))
            }

            put("/settings") {
                val request = call.receive<UpdateNotificationSettingsRequest>()
                call.respond(notifications.updateSettings(call.requireUserId(), request))
            }

            /** What she was actually sent, so "why didn't I get it" has an answer. */
            get("/history") {
                call.respond(
                    notifications.history(call.requireUserId(), call.intParameter("limit", 50, 200)),
                )
            }
        }
    }
}

/** Page 10 of the admin panel: templates and the frequency caps. */
fun Route.adminNotificationRoutes(notifications: NotificationService, audit: AuditService) {
    authenticate(ADMIN_AUTH) {
        route("/admin/notifications") {
            get("/templates") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.ANALYST)
                call.respond(
                    notifications.templates().map {
                        TemplateView(it.key, it.language, it.category, it.title, it.body, it.active)
                    },
                )
            }

            put("/templates") {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val request = call.receive<TemplateView>()
                notifications.saveTemplate(
                    TemplateRecord(
                        key = request.key,
                        language = request.language,
                        category = request.category,
                        title = request.title,
                        body = request.body,
                        active = request.active,
                    ),
                )
                audit.record(
                    AuditEntry(
                        actorType = ActorType.ADMIN,
                        actorId = admin.adminId,
                        actorLabel = admin.role.name.lowercase(),
                        action = "notification.template_updated",
                        entityType = "notification_template",
                        entityId = "${request.key}:${request.language}",
                        ip = call.requestContext().ip,
                    ),
                )
                call.respond(Ack())
            }

            get("/caps") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.ANALYST)
                call.respond(notifications.caps())
            }

            put("/caps") {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val request = call.receive<FrequencyCaps>()
                val saved = notifications.updateCaps(request)
                audit.record(
                    AuditEntry(
                        actorType = ActorType.ADMIN,
                        actorId = admin.adminId,
                        actorLabel = admin.role.name.lowercase(),
                        action = "notification.caps_updated",
                        entityType = "notification_caps",
                        metadata = mapOf(
                            "maxPerDay" to saved.maxPerDay.toString(),
                            "maxPerWeek" to saved.maxPerWeek.toString(),
                        ),
                        ip = call.requestContext().ip,
                    ),
                )
                call.respond(saved)
            }
        }
    }
}
