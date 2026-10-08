package uz.sadora.server.health

import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import uz.sadora.contract.DoseStatus
import uz.sadora.contract.FoodRelation
import uz.sadora.contract.SaveMedicationRequest
import uz.sadora.contract.ScheduleKind
import uz.sadora.contract.Weekday
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.MedicationIntakes
import uz.sadora.server.db.Medications
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

/** One recorded intake. Absence of a row means the dose is still pending. */
data class IntakeRecord(
    val medicationId: Uuid,
    val dueOn: LocalDate,
    val dueAt: LocalTime,
    val status: DoseStatus,
    val recordedAt: kotlin.time.Instant,
)

class MedicationRepository {

    suspend fun listOf(userId: Uuid, includeArchived: Boolean): List<MedicationRecord> = dbQuery {
        var query = Medications.selectAll().where { Medications.userId eq userId }
        if (!includeArchived) query = query.andWhere { Medications.active eq true }
        query.orderBy(Medications.createdAt to SortOrder.ASC).map { it.toRecord() }
    }

    /**
     * Every active course with reminders on, across all users, for the scheduler.
     *
     * A full scan is honest at this scale and would not be at a much larger one — the
     * shape that replaces it is a per-user next-due index, and the tick is the only
     * caller, so changing it later touches one query.
     */
    suspend fun withRemindersEnabled(): List<Pair<Uuid, MedicationRecord>> = dbQuery {
        Medications.selectAll()
            .where { (Medications.active eq true) and (Medications.remindersEnabled eq true) }
            .map { it[Medications.userId] to it.toRecord() }
    }

    suspend fun byId(userId: Uuid, id: Uuid): MedicationRecord? = dbQuery {
        Medications.selectAll()
            .where { (Medications.id eq id) and (Medications.userId eq userId) }
            .singleOrNull()
            ?.toRecord()
    }

    suspend fun add(
        userId: Uuid,
        request: SaveMedicationRequest,
        startedOn: LocalDate,
        prescriptionId: Uuid? = null,
    ): Uuid =
        dbQuery {
            val id = Uuid.random()
            val timestamp = now().toOffsetDateTime()
            Medications.insert {
                it[Medications.id] = id
                it[Medications.userId] = userId
                it[name] = request.name.trim()
                it[emoji] = request.emoji
                it[dosage] = request.dosage
                it[unit] = request.unit
                it[foodRelation] = request.foodRelation.dbValue()
                it[note] = request.note
                it[scheduleKind] = request.schedule.kind.dbValue()
                it[times] = request.schedule.times.joinToString(",") { time -> time.toString() }
                it[weekdays] = request.schedule.weekdays.joinToString(",") { day -> day.isoNumber.toString() }
                it[intervalDays] = request.schedule.intervalDays
                it[remindersEnabled] = request.remindersEnabled
                it[Medications.startedOn] = startedOn
                it[endedOn] = request.endedOn
                it[stockUnits] = request.stockUnits
                it[active] = true
                it[createdAt] = timestamp
                it[updatedAt] = timestamp
                it[Medications.prescriptionId] = prescriptionId
            }
            id
        }

    suspend fun update(userId: Uuid, id: Uuid, request: SaveMedicationRequest): Boolean = dbQuery {
        Medications.update({ (Medications.id eq id) and (Medications.userId eq userId) }) {
            it[name] = request.name.trim()
            it[emoji] = request.emoji
            it[dosage] = request.dosage
            it[unit] = request.unit
            it[foodRelation] = request.foodRelation.dbValue()
            it[note] = request.note
            it[scheduleKind] = request.schedule.kind.dbValue()
            it[times] = request.schedule.times.joinToString(",") { time -> time.toString() }
            it[weekdays] = request.schedule.weekdays.joinToString(",") { day -> day.isoNumber.toString() }
            it[intervalDays] = request.schedule.intervalDays
            it[remindersEnabled] = request.remindersEnabled
            request.startedOn?.let { started -> it[Medications.startedOn] = started }
            it[endedOn] = request.endedOn
            it[stockUnits] = request.stockUnits
            it[updatedAt] = now().toOffsetDateTime()
        } > 0
    }

    /**
     * Archives rather than deletes.
     *
     * The intake history is the point of the feature — "did I take it in March" must
     * still be answerable after she finishes a course.
     */
    suspend fun archive(userId: Uuid, id: Uuid, endedOn: LocalDate): Boolean = dbQuery {
        Medications.update({ (Medications.id eq id) and (Medications.userId eq userId) }) {
            it[active] = false
            it[Medications.endedOn] = endedOn
            it[updatedAt] = now().toOffsetDateTime()
        } > 0
    }

    /**
     * Stops every active course added from [prescriptionId], for a cancelled
     * prescription. One that has begun is archived like any other, its history kept; one
     * that has not begun yet has no history, and is removed — archived, it would still
     * have shown its first day's doses. Reminders stop because only active courses ring.
     */
    suspend fun archiveForPrescription(prescriptionId: Uuid, today: LocalDate): Int = dbQuery {
        val rows = Medications.selectAll()
            .where { (Medications.prescriptionId eq prescriptionId) and (Medications.active eq true) }
            .map { it[Medications.id] to it[Medications.startedOn] }
        rows.forEach { (id, startedOn) ->
            if (startedOn > today) {
                Medications.deleteWhere { Medications.id eq id }
            } else {
                Medications.update({ Medications.id eq id }) {
                    it[active] = false
                    it[endedOn] = today
                    it[updatedAt] = now().toOffsetDateTime()
                }
            }
        }
        rows.size
    }

    suspend fun adjustStock(userId: Uuid, id: Uuid, delta: Int): Int? = dbQuery {
        // The arithmetic is the database's: two taps on two doses used to both read 10
        // and both write 9. `delta` comes from the service, never from a request, and the
        // ids were parsed as UUIDs, so the statement carries nothing a caller typed.
        exec(
            "UPDATE medications SET stock_units = GREATEST(COALESCE(stock_units, 0) + ($delta), 0), " +
                "updated_at = now() WHERE id = '$id' AND user_id = '$userId'",
        )
        Medications.select(Medications.stockUnits)
            .where { (Medications.id eq id) and (Medications.userId eq userId) }
            .singleOrNull()
            ?.get(Medications.stockUnits)
    }

    // ---------------------------------------------------------------- intakes

    suspend fun intakesBetween(userId: Uuid, from: LocalDate, to: LocalDate): List<IntakeRecord> =
        dbQuery {
            MedicationIntakes.selectAll()
                .where {
                    (MedicationIntakes.userId eq userId) and
                        (MedicationIntakes.dueOn greaterEq from) and
                        (MedicationIntakes.dueOn lessEq to)
                }
                .map { it.toIntake() }
        }

    suspend fun intakesFor(
        userId: Uuid,
        medicationId: Uuid,
        from: LocalDate,
        to: LocalDate,
    ): List<IntakeRecord> = dbQuery {
        MedicationIntakes.selectAll()
            .where {
                (MedicationIntakes.userId eq userId) and
                    (MedicationIntakes.medicationId eq medicationId) and
                    (MedicationIntakes.dueOn greaterEq from) and
                    (MedicationIntakes.dueOn lessEq to)
            }
            .map { it.toIntake() }
    }

    /** Returns the status the dose had before this call, so stock can be reconciled. */
    suspend fun recordIntake(
        userId: Uuid,
        medicationId: Uuid,
        dueOn: LocalDate,
        dueAt: LocalTime,
        status: DoseStatus,
    ): DoseStatus = dbQuery {
        val previous = MedicationIntakes.selectAll()
            .where {
                (MedicationIntakes.userId eq userId) and
                    (MedicationIntakes.medicationId eq medicationId) and
                    (MedicationIntakes.dueOn eq dueOn) and
                    (MedicationIntakes.dueAt eq dueAt)
            }
            .singleOrNull()
            ?.let { enumFromDb(it[MedicationIntakes.status], DoseStatus.PENDING) }
            ?: DoseStatus.PENDING

        val timestamp = now().toOffsetDateTime()
        MedicationIntakes.upsert(
            MedicationIntakes.medicationId,
            MedicationIntakes.dueOn,
            MedicationIntakes.dueAt,
        ) {
            it[MedicationIntakes.medicationId] = medicationId
            it[MedicationIntakes.userId] = userId
            it[MedicationIntakes.dueOn] = dueOn
            it[MedicationIntakes.dueAt] = dueAt
            it[MedicationIntakes.status] = status.dbValue()
            it[recordedAt] = timestamp
        }
        previous
    }

    /** Undoes a record, putting the dose back to pending. */
    suspend fun clearIntake(userId: Uuid, medicationId: Uuid, dueOn: LocalDate, dueAt: LocalTime): Unit = dbQuery {
        MedicationIntakes.deleteWhere {
            (MedicationIntakes.userId eq userId) and
                (MedicationIntakes.medicationId eq medicationId) and
                (MedicationIntakes.dueOn eq dueOn) and
                (MedicationIntakes.dueAt eq dueAt)
        }
    }

    private fun ResultRow.toRecord() = MedicationRecord(
        id = this[Medications.id],
        name = this[Medications.name],
        emoji = this[Medications.emoji],
        dosage = this[Medications.dosage],
        unit = this[Medications.unit],
        foodRelation = enumFromDb(this[Medications.foodRelation], FoodRelation.ANY),
        note = this[Medications.note],
        scheduleKind = enumFromDb(this[Medications.scheduleKind], ScheduleKind.DAILY),
        times = this[Medications.times].parseTimes(),
        weekdays = this[Medications.weekdays].parseWeekdays(),
        intervalDays = this[Medications.intervalDays],
        remindersEnabled = this[Medications.remindersEnabled],
        startedOn = this[Medications.startedOn],
        endedOn = this[Medications.endedOn],
        stockUnits = this[Medications.stockUnits],
        active = this[Medications.active],
        createdAt = this[Medications.createdAt].toKotlinInstant(),
        prescriptionId = this[Medications.prescriptionId],
    )

    private fun ResultRow.toIntake() = IntakeRecord(
        medicationId = this[MedicationIntakes.medicationId],
        dueOn = this[MedicationIntakes.dueOn],
        dueAt = this[MedicationIntakes.dueAt],
        status = enumFromDb(this[MedicationIntakes.status], DoseStatus.PENDING),
        recordedAt = this[MedicationIntakes.recordedAt].toKotlinInstant(),
    )

    /** A malformed stored value is dropped rather than crashing the whole list. */
    private fun String.parseTimes(): List<LocalTime> =
        split(',').mapNotNull { part ->
            part.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        }

    private fun String.parseWeekdays(): List<Weekday> =
        split(',').mapNotNull { part ->
            part.trim().toIntOrNull()?.let { number -> Weekday.entries.firstOrNull { it.isoNumber == number } }
        }
}
