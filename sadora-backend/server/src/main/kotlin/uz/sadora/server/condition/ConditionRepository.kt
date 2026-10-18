package uz.sadora.server.condition

import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import uz.sadora.contract.ConditionStatus
import uz.sadora.contract.HealthCondition
import uz.sadora.contract.PainArea
import uz.sadora.contract.PainKind
import uz.sadora.contract.PainLog
import uz.sadora.contract.SavePainLogRequest
import uz.sadora.contract.UserCondition
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.PainLogs
import uz.sadora.server.db.UserConditions
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

/** Her conditions and her pain diary, scoped to her in every query. */
class ConditionRepository {

    suspend fun conditionsOf(userId: Uuid): List<UserCondition> = dbQuery {
        UserConditions.selectAll()
            .where { UserConditions.userId eq userId }
            .orderBy(UserConditions.condition to SortOrder.ASC)
            .mapNotNull { it.toCondition() }
    }

    suspend fun has(userId: Uuid, condition: HealthCondition): Boolean = dbQuery {
        UserConditions.selectAll()
            .where { (UserConditions.userId eq userId) and (UserConditions.condition eq condition.dbValue()) }
            .empty()
            .not()
    }

    suspend fun set(userId: Uuid, condition: HealthCondition, status: ConditionStatus, diagnosedOn: LocalDate?) = dbQuery {
        val at = now().toOffsetDateTime()
        UserConditions.upsert(onUpdateExclude = listOf(UserConditions.createdAt)) {
            it[UserConditions.userId] = userId
            it[UserConditions.condition] = condition.dbValue()
            it[UserConditions.status] = status.dbValue()
            it[UserConditions.diagnosedOn] = diagnosedOn
            it[createdAt] = at
            it[updatedAt] = at
        }
    }

    suspend fun remove(userId: Uuid, condition: HealthCondition): Boolean = dbQuery {
        UserConditions.deleteWhere { (UserConditions.userId eq userId) and (UserConditions.condition eq condition.dbValue()) } > 0
    }

    // ---------------------------------------------------------------- pain

    suspend fun pain(userId: Uuid, from: LocalDate, to: LocalDate): List<PainLog> = dbQuery {
        PainLogs.selectAll()
            .where { (PainLogs.userId eq userId) and (PainLogs.day greaterEq from) and (PainLogs.day lessEq to) }
            .orderBy(PainLogs.day to SortOrder.DESC)
            .map { it.toPain() }
    }

    suspend fun savePain(userId: Uuid, day: LocalDate, request: SavePainLogRequest) = dbQuery {
        PainLogs.upsert {
            it[PainLogs.userId] = userId
            it[PainLogs.day] = day
            it[level] = request.level
            it[areas] = request.areas.distinct().joinToString(",") { a -> a.dbValue() }
            it[kinds] = request.kinds.distinct().joinToString(",") { k -> k.dbValue() }
            it[painkiller] = request.painkiller
            it[painkillerName] = request.painkillerName
            it[missedActivities] = request.missedActivities
            it[note] = request.note
            it[updatedAt] = now().toOffsetDateTime()
        }
    }

    suspend fun deletePain(userId: Uuid, day: LocalDate): Boolean = dbQuery {
        PainLogs.deleteWhere { (PainLogs.userId eq userId) and (PainLogs.day eq day) } > 0
    }

    private fun ResultRow.toCondition(): UserCondition? {
        val condition = enumFromDb<HealthCondition>(this[UserConditions.condition]) ?: return null
        return UserCondition(
            condition = condition,
            status = enumFromDb(this[UserConditions.status], ConditionStatus.SUSPECTED),
            diagnosedOn = this[UserConditions.diagnosedOn],
            updatedAt = this[UserConditions.updatedAt].toKotlinInstant(),
        )
    }

    private fun ResultRow.toPain() = PainLog(
        date = this[PainLogs.day],
        level = this[PainLogs.level],
        // A word this build does not know is from a newer one; it is skipped, not fatal.
        areas = this[PainLogs.areas].split(',').filter { it.isNotBlank() }.mapNotNull { enumFromDb<PainArea>(it) },
        kinds = this[PainLogs.kinds].split(',').filter { it.isNotBlank() }.mapNotNull { enumFromDb<PainKind>(it) },
        painkiller = this[PainLogs.painkiller],
        painkillerName = this[PainLogs.painkillerName],
        missedActivities = this[PainLogs.missedActivities],
        note = this[PainLogs.note],
        updatedAt = this[PainLogs.updatedAt].toKotlinInstant(),
    )
}
