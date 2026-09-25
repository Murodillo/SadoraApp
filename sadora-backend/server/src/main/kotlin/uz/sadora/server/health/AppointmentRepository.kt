package uz.sadora.server.health

import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.sadora.contract.Appointment
import uz.sadora.contract.SaveAppointmentRequest
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.Appointments
import uz.sadora.server.db.dbQuery

/**
 * Appointments, scoped to their owner in every query.
 *
 * Ordered by the day they fall on rather than by when they were entered: the list is
 * read as a calendar, and an appointment added late for an earlier date belongs in its
 * place, not at the bottom.
 */
class AppointmentRepository {

    suspend fun list(userId: Uuid): List<Appointment> = dbQuery {
        Appointments.selectAll()
            .where { Appointments.userId eq userId }
            .orderBy(
                Appointments.scheduledOn to SortOrder.ASC,
                Appointments.scheduledAt to SortOrder.ASC,
            )
            .map { it.toAppointment() }
    }

    suspend fun byId(userId: Uuid, id: Uuid): Appointment? = dbQuery {
        Appointments.selectAll()
            .where { (Appointments.id eq id) and (Appointments.userId eq userId) }
            .singleOrNull()
            ?.toAppointment()
    }

    suspend fun add(userId: Uuid, request: SaveAppointmentRequest): Appointment = dbQuery {
        val id = Uuid.random()
        val timestamp = now()
        Appointments.insert {
            it[Appointments.id] = id
            it[Appointments.userId] = userId
            it[title] = request.title.trim()
            it[scheduledOn] = request.scheduledOn
            it[scheduledAt] = request.scheduledAt
            it[place] = request.place?.trim()?.takeIf(String::isNotEmpty)
            it[note] = request.note?.trim()?.takeIf(String::isNotEmpty)
            it[remindHoursBefore] = request.remindHoursBefore
            it[createdAt] = timestamp.toOffsetDateTime()
            it[updatedAt] = timestamp.toOffsetDateTime()
        }
        Appointment(
            id = id.toString(),
            title = request.title.trim(),
            scheduledOn = request.scheduledOn,
            scheduledAt = request.scheduledAt,
            place = request.place?.trim()?.takeIf(String::isNotEmpty),
            note = request.note?.trim()?.takeIf(String::isNotEmpty),
            remindHoursBefore = request.remindHoursBefore,
            createdAt = timestamp,
        )
    }

    suspend fun update(userId: Uuid, id: Uuid, request: SaveAppointmentRequest): Boolean = dbQuery {
        Appointments.update({ (Appointments.id eq id) and (Appointments.userId eq userId) }) {
            it[title] = request.title.trim()
            it[scheduledOn] = request.scheduledOn
            it[scheduledAt] = request.scheduledAt
            it[place] = request.place?.trim()?.takeIf(String::isNotEmpty)
            it[note] = request.note?.trim()?.takeIf(String::isNotEmpty)
            it[remindHoursBefore] = request.remindHoursBefore
            it[updatedAt] = now().toOffsetDateTime()
        } > 0
    }

    /** Marking one done is a timestamp, not a flag, so the list can say when. */
    suspend fun setCompleted(userId: Uuid, id: Uuid, done: Boolean): Boolean = dbQuery {
        Appointments.update({ (Appointments.id eq id) and (Appointments.userId eq userId) }) {
            it[completedAt] = if (done) now().toOffsetDateTime() else null
            it[updatedAt] = now().toOffsetDateTime()
        } > 0
    }

    suspend fun delete(userId: Uuid, id: Uuid): Boolean = dbQuery {
        Appointments.deleteWhere {
            (Appointments.id eq id) and (Appointments.userId eq userId)
        } > 0
    }

    private fun ResultRow.toAppointment() = Appointment(
        id = this[Appointments.id].toString(),
        title = this[Appointments.title],
        scheduledOn = this[Appointments.scheduledOn],
        scheduledAt = this[Appointments.scheduledAt],
        place = this[Appointments.place],
        note = this[Appointments.note],
        remindHoursBefore = this[Appointments.remindHoursBefore],
        completedAt = this[Appointments.completedAt]?.toKotlinInstant(),
        createdAt = this[Appointments.createdAt].toKotlinInstant(),
    )
}
