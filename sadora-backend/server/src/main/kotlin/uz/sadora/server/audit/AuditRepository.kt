package uz.sadora.server.audit

import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import uz.sadora.server.admin.AuditEntryView
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.db.AuditLog
import uz.sadora.server.db.dbQuery

/** Read side of the audit log — page 14 of the admin panel. */
class AuditRepository {

    suspend fun list(
        action: String? = null,
        actorId: Uuid? = null,
        entityType: String? = null,
        entityId: String? = null,
        limit: Int = 50,
        offset: Long = 0,
    ): Pair<List<AuditEntryView>, Long> = dbQuery {
        var query = AuditLog.selectAll()
        action?.let { value -> query = query.andWhere { AuditLog.action eq value } }
        actorId?.let { value -> query = query.andWhere { AuditLog.actorId eq value } }
        entityType?.let { value -> query = query.andWhere { AuditLog.entityType eq value } }
        entityId?.let { value -> query = query.andWhere { AuditLog.entityId eq value } }

        val total = query.count()
        val items = query
            .orderBy(AuditLog.createdAt to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map { row ->
                AuditEntryView(
                    id = row[AuditLog.id].toString(),
                    actorType = row[AuditLog.actorType],
                    actorId = row[AuditLog.actorId]?.toString(),
                    actorLabel = row[AuditLog.actorLabel],
                    action = row[AuditLog.action],
                    entityType = row[AuditLog.entityType],
                    entityId = row[AuditLog.entityId],
                    reason = row[AuditLog.reason],
                    metadata = row[AuditLog.metadata],
                    ip = row[AuditLog.ip],
                    createdAt = row[AuditLog.createdAt].toKotlinInstant(),
                )
            }
        items to total
    }
}
