package uz.sadora.server.health

import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import uz.sadora.contract.DailyLog
import uz.sadora.contract.FlowLevel
import uz.sadora.contract.LifeStage
import uz.sadora.contract.MoodLevel
import uz.sadora.contract.SaveDailyLogRequest
import uz.sadora.contract.SymptomCategory
import uz.sadora.contract.SymptomDefinition
import uz.sadora.contract.SymptomEntry
import uz.sadora.contract.SymptomSeverity
import uz.sadora.server.core.ConflictException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.CyclePeriods
import uz.sadora.server.db.CycleBaselines
import uz.sadora.server.db.DailyLogs
import uz.sadora.server.db.DailySymptoms
import uz.sadora.server.db.SymptomDefinitions
import uz.sadora.server.db.SymptomLifeStages
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

/**
 * The health tables.
 *
 * Every query here is scoped to one `userId` — there is no method that reads across
 * users, and nothing in the admin service can reach this class. That is what makes the
 * TZ's section 17 boundary structural rather than a convention.
 */
class HealthRepository {

    // ---------------------------------------------------------------- periods

    suspend fun periodsOf(userId: Uuid): List<RecordedPeriod> = dbQuery {
        CyclePeriods.selectAll()
            .where { CyclePeriods.userId eq userId }
            .orderBy(CyclePeriods.startedOn to SortOrder.ASC)
            .map { it.toRecordedPeriod() }
    }

    suspend fun periodById(userId: Uuid, id: Uuid): RecordedPeriod? = dbQuery {
        CyclePeriods.selectAll()
            .where { (CyclePeriods.id eq id) and (CyclePeriods.userId eq userId) }
            .singleOrNull()
            ?.toRecordedPeriod()
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toRecordedPeriod() = RecordedPeriod(
        id = this[CyclePeriods.id],
        startedOn = this[CyclePeriods.startedOn],
        endedOn = this[CyclePeriods.endedOn],
        createdAt = this[CyclePeriods.createdAt].toKotlinInstant(),
    )

    suspend fun addPeriod(userId: Uuid, startedOn: LocalDate, endedOn: LocalDate?): Uuid = dbQuery {
        val clash = CyclePeriods.selectAll()
            .where { (CyclePeriods.userId eq userId) and (CyclePeriods.startedOn eq startedOn) }
            .empty()
            .not()
        if (clash) throw ConflictException("Bu sana uchun hayz allaqachon qayd etilgan")

        val id = Uuid.random()
        val timestamp = now().toOffsetDateTime()
        CyclePeriods.insert {
            it[CyclePeriods.id] = id
            it[CyclePeriods.userId] = userId
            it[CyclePeriods.startedOn] = startedOn
            it[CyclePeriods.endedOn] = endedOn
            it[createdAt] = timestamp
            it[updatedAt] = timestamp
        }
        id
    }

    suspend fun updatePeriod(
        userId: Uuid,
        id: Uuid,
        startedOn: LocalDate?,
        endedOn: LocalDate?,
        clearEnd: Boolean,
    ): Unit = dbQuery {
        val exists = CyclePeriods.selectAll()
            .where { (CyclePeriods.id eq id) and (CyclePeriods.userId eq userId) }
            .empty()
            .not()
        if (!exists) throw NotFoundException("Hayz yozuvi topilmadi")

        startedOn?.let { moved ->
            val clash = CyclePeriods.selectAll()
                .where {
                    (CyclePeriods.userId eq userId) and
                        (CyclePeriods.startedOn eq moved) and
                        (CyclePeriods.id neq id)
                }
                .empty()
                .not()
            if (clash) throw ConflictException("Bu sana uchun hayz allaqachon qayd etilgan")
        }

        CyclePeriods.update({ (CyclePeriods.id eq id) and (CyclePeriods.userId eq userId) }) { statement ->
            startedOn?.let { statement[CyclePeriods.startedOn] = it }
            // `clearEnd` reopens a period ended by mistake; without it a null `endedOn`
            // means "unchanged", the same as everywhere else in the API.
            if (clearEnd) statement[CyclePeriods.endedOn] = null
            else endedOn?.let { statement[CyclePeriods.endedOn] = it }
            statement[updatedAt] = now().toOffsetDateTime()
        }
    }

    suspend fun deletePeriod(userId: Uuid, id: Uuid): Boolean = dbQuery {
        CyclePeriods.deleteWhere { (CyclePeriods.id eq id) and (CyclePeriods.userId eq userId) } > 0
    }

    suspend fun baselineOf(userId: Uuid): CycleBaselineData? = dbQuery {
        CycleBaselines.selectAll()
            .where { CycleBaselines.userId eq userId }
            .singleOrNull()
            ?.let {
                CycleBaselineData(
                    lastPeriodStart = it[CycleBaselines.lastPeriodStart],
                    averageCycleLength = it[CycleBaselines.averageCycleLength],
                    averagePeriodLength = it[CycleBaselines.averagePeriodLength],
                    isRegular = it[CycleBaselines.isRegular],
                )
            }
    }

    // ---------------------------------------------------------------- daily logs

    suspend fun logsBetween(userId: Uuid, from: LocalDate, to: LocalDate): List<DailyLog> = dbQuery {
        val symptoms = DailySymptoms.selectAll()
            .where {
                (DailySymptoms.userId eq userId) and
                    (DailySymptoms.logDate greaterEq from) and
                    (DailySymptoms.logDate lessEq to)
            }
            .groupBy({ it[DailySymptoms.logDate] }) { row ->
                SymptomEntry(
                    key = row[DailySymptoms.symptomKey],
                    severity = enumFromDb(row[DailySymptoms.severity], SymptomSeverity.MODERATE),
                )
            }

        DailyLogs.selectAll()
            .where {
                (DailyLogs.userId eq userId) and
                    (DailyLogs.logDate greaterEq from) and
                    (DailyLogs.logDate lessEq to)
            }
            .orderBy(DailyLogs.logDate to SortOrder.ASC)
            .map { row ->
                val date = row[DailyLogs.logDate]
                DailyLog(
                    date = date,
                    flow = enumFromDb<FlowLevel>(row[DailyLogs.flow]),
                    mood = enumFromDb<MoodLevel>(row[DailyLogs.mood]),
                    energy = row[DailyLogs.energy],
                    symptoms = symptoms[date].orEmpty(),
                    note = row[DailyLogs.note],
                    updatedAt = row[DailyLogs.updatedAt].toKotlinInstant(),
                )
            }
    }

    suspend fun logOn(userId: Uuid, date: LocalDate): DailyLog? =
        logsBetween(userId, date, date).firstOrNull()

    /**
     * Replaces a day wholesale.
     *
     * The day sheet edits the day as a unit, so this deletes the symptoms and writes the
     * new set inside one transaction — a half-saved day would show symptoms the user had
     * just removed.
     */
    suspend fun saveLog(userId: Uuid, date: LocalDate, request: SaveDailyLogRequest): Unit = dbQuery {
        val timestamp = now().toOffsetDateTime()
        DailyLogs.upsert(DailyLogs.userId, DailyLogs.logDate) {
            it[DailyLogs.userId] = userId
            it[logDate] = date
            it[flow] = request.flow?.dbValue()
            it[mood] = request.mood?.dbValue()
            it[energy] = request.energy
            it[note] = request.note?.takeIf { text -> text.isNotBlank() }
            it[createdAt] = timestamp
            it[updatedAt] = timestamp
        }

        DailySymptoms.deleteWhere {
            (DailySymptoms.userId eq userId) and (DailySymptoms.logDate eq date)
        }
        request.symptoms.distinctBy { it.key }.forEach { symptom ->
            DailySymptoms.insert {
                it[DailySymptoms.userId] = userId
                it[logDate] = date
                it[symptomKey] = symptom.key
                it[severity] = symptom.severity.dbValue()
            }
        }
    }

    suspend fun deleteLog(userId: Uuid, date: LocalDate): Boolean = dbQuery {
        DailySymptoms.deleteWhere { (DailySymptoms.userId eq userId) and (DailySymptoms.logDate eq date) }
        DailyLogs.deleteWhere { (DailyLogs.userId eq userId) and (DailyLogs.logDate eq date) } > 0
    }

    // ---------------------------------------------------------------- catalogue

    /** A symptom with no stage rows is offered everywhere; the rest are scoped. */
    suspend fun symptomCatalogue(lifeStage: LifeStage?): List<SymptomDefinition> = dbQuery {
        val scopes = SymptomLifeStages.selectAll()
            .groupBy({ it[SymptomLifeStages.symptomKey] }) { it[SymptomLifeStages.lifeStage] }

        SymptomDefinitions.selectAll()
            .where { SymptomDefinitions.active eq true }
            .orderBy(SymptomDefinitions.sortOrder to SortOrder.ASC)
            .map { row ->
                val key = row[SymptomDefinitions.key]
                val stages = scopes[key].orEmpty()
                SymptomDefinition(
                    key = key,
                    label = row[SymptomDefinitions.label],
                    category = enumFromDb(row[SymptomDefinitions.category], SymptomCategory.OTHER),
                    lifeStages = stages.mapNotNull { enumFromDb<LifeStage>(it) },
                    sortOrder = row[SymptomDefinitions.sortOrder],
                )
            }
            .filter { definition ->
                lifeStage == null || definition.lifeStages.isEmpty() || lifeStage in definition.lifeStages
            }
    }

    suspend fun knownSymptomKeys(): Set<String> = dbQuery {
        SymptomDefinitions.selectAll()
            .where { SymptomDefinitions.active eq true }
            .map { it[SymptomDefinitions.key] }
            .toSet()
    }
}
