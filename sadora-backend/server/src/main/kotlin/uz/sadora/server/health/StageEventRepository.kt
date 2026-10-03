package uz.sadora.server.health

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import uz.sadora.contract.StageEvent
import uz.sadora.contract.StageEventKind
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.StageEventsTable
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

/** Stage events, scoped to their owner in every query. Newest first. */
class StageEventRepository {

    suspend fun list(userId: Uuid, kind: StageEventKind?, since: Instant, limit: Int): List<StageEvent> = dbQuery {
        StageEventsTable.selectAll()
            .where {
                val mine = (StageEventsTable.userId eq userId) and
                    (StageEventsTable.startedAt greaterEq since.toOffsetDateTime())
                if (kind == null) mine else mine and (StageEventsTable.kind eq kind.dbValue())
            }
            .orderBy(StageEventsTable.startedAt to SortOrder.DESC)
            .limit(limit)
            .mapNotNull { it.toEvent() }
    }

    suspend fun add(
        userId: Uuid,
        kind: StageEventKind,
        startedAt: Instant,
        durationSeconds: Int?,
        value: Int?,
        detail: String?,
    ): StageEvent = dbQuery {
        val id = Uuid.random()
        val created = now()
        StageEventsTable.insert {
            it[StageEventsTable.id] = id
            it[StageEventsTable.userId] = userId
            it[StageEventsTable.kind] = kind.dbValue()
            it[StageEventsTable.startedAt] = startedAt.toOffsetDateTime()
            it[StageEventsTable.durationSeconds] = durationSeconds
            it[StageEventsTable.value] = value
            it[StageEventsTable.detail] = detail
            it[createdAt] = created.toOffsetDateTime()
        }
        StageEvent(id.toString(), kind, startedAt, durationSeconds, value, detail, created)
    }

    suspend fun delete(userId: Uuid, id: Uuid): Boolean = dbQuery {
        StageEventsTable.deleteWhere { (StageEventsTable.id eq id) and (StageEventsTable.userId eq userId) } > 0
    }

    // A kind this build does not know is a row from a newer one, not an error.
    private fun ResultRow.toEvent(): StageEvent? {
        val kind = enumFromDb<StageEventKind>(this[StageEventsTable.kind]) ?: return null
        return StageEvent(
            id = this[StageEventsTable.id].toString(),
            kind = kind,
            startedAt = this[StageEventsTable.startedAt].toKotlinInstant(),
            durationSeconds = this[StageEventsTable.durationSeconds],
            value = this[StageEventsTable.value],
            detail = this[StageEventsTable.detail],
            createdAt = this[StageEventsTable.createdAt].toKotlinInstant(),
        )
    }
}
