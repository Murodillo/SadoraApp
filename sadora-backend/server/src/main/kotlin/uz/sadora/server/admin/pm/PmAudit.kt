package uz.sadora.server.admin.pm

import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.auth.RequestContext
import uz.sadora.server.core.ForbiddenException
import uz.sadora.server.plugins.AdminPrincipal
import uz.sadora.server.plugins.AdminRole

/** The few Loyiha actions the audit log keeps: tokens, anything for good, a restore. */
internal suspend fun AuditService.recordPm(
    actor: AdminPrincipal,
    action: String,
    entityType: String,
    entityId: String,
    context: RequestContext,
    metadata: Map<String, String> = emptyMap(),
) {
    record(
        AuditEntry(
            actorType = ActorType.ADMIN,
            actorId = actor.adminId,
            actorLabel = actor.role.name.lowercase(),
            action = action,
            entityType = entityType,
            entityId = entityId,
            metadata = metadata,
            ip = context.ip,
            userAgent = context.userAgent,
        ),
    )
}

/** Deleting for good is a person's step in the panel, never a script's, even the Owner's. */
internal fun refuseForGood(actor: AdminPrincipal) {
    if (actor.role != AdminRole.OWNER) throw ForbiddenException(message = "Butunlay o'chirish faqat egasiga ruxsat etilgan")
    if (actor.viaToken) throw ForbiddenException(message = "Butunlay o'chirish faqat panelda")
}
