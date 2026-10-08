package uz.sadora.server.prescription

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.sadora.contract.PrescriptionItem
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.DoctorProfiles
import uz.sadora.server.db.Prescriptions
import uz.sadora.server.db.dbQuery

data class PrescriptionRecord(
    val id: Uuid,
    val messageId: Uuid,
    val conversationId: Uuid,
    val doctorId: Uuid,
    val patientId: Uuid,
    val items: List<PrescriptionItem>,
    val note: String?,
    val createdAt: Instant,
    val cancelledAt: Instant?,
    val cancelReason: String?,
    val addedAt: Instant?,
)

class PrescriptionRepository {

    suspend fun insert(record: PrescriptionRecord): Unit = dbQuery {
        Prescriptions.insert {
            it[id] = record.id
            it[messageId] = record.messageId
            it[conversationId] = record.conversationId
            it[doctorId] = record.doctorId
            it[patientId] = record.patientId
            it[items] = record.items
            it[note] = record.note
            it[createdAt] = record.createdAt.toOffsetDateTime()
        }
    }

    suspend fun byId(id: Uuid): PrescriptionRecord? = dbQuery {
        Prescriptions.selectAll().where { Prescriptions.id eq id }.singleOrNull()?.toRecord()
    }

    /** The prescriptions behind a page of `prescription` messages, by message id. */
    suspend fun byMessages(messageIds: Collection<Uuid>): Map<Uuid, PrescriptionRecord> = dbQuery {
        if (messageIds.isEmpty()) return@dbQuery emptyMap()
        Prescriptions.selectAll()
            .where { Prescriptions.messageId inList messageIds }
            .associate { it[Prescriptions.messageId] to it.toRecord() }
    }

    suspend fun ofConversation(conversationId: Uuid, limit: Int): List<PrescriptionRecord> = dbQuery {
        Prescriptions.selectAll()
            .where { Prescriptions.conversationId eq conversationId }
            .orderBy(Prescriptions.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toRecord() }
    }

    suspend fun ofPatient(patientId: Uuid, limit: Int): List<PrescriptionRecord> = dbQuery {
        Prescriptions.selectAll()
            .where { Prescriptions.patientId eq patientId }
            .orderBy(Prescriptions.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toRecord() }
    }

    /** Sets the cancellation once; false when it was already cancelled. */
    suspend fun cancel(id: Uuid, reason: String, at: Instant): Boolean = dbQuery {
        Prescriptions.update({ (Prescriptions.id eq id) and Prescriptions.cancelledAt.isNull() }) {
            it[cancelledAt] = at.toOffsetDateTime()
            it[cancelReason] = reason
        } > 0
    }

    /**
     * Claims the prescription for adding: one call wins, so two taps never add the
     * course twice, and a cancelled one is never added.
     */
    suspend fun markAdded(id: Uuid, at: Instant): Boolean = dbQuery {
        Prescriptions.update({
            (Prescriptions.id eq id) and Prescriptions.addedAt.isNull() and Prescriptions.cancelledAt.isNull()
        }) {
            it[addedAt] = at.toOffsetDateTime()
        } > 0
    }

    /** Releases the claim when adding the courses failed, so she can try again. */
    suspend fun clearAdded(id: Uuid): Unit = dbQuery {
        Prescriptions.update({ Prescriptions.id eq id }) { it[addedAt] = null }
    }

    /** The doctor behind each prescription, for "Dr. … retsepti" on a medication. */
    suspend fun prescriberNames(ids: Collection<Uuid>): Map<Uuid, String> = dbQuery {
        if (ids.isEmpty()) return@dbQuery emptyMap()
        (Prescriptions innerJoin DoctorProfiles)
            .select(Prescriptions.id, DoctorProfiles.fullName)
            .where { Prescriptions.id inList ids }
            .associate { it[Prescriptions.id] to it[DoctorProfiles.fullName] }
    }

    /** For the owner's panel: how many were written and how many patients added. */
    suspend fun counts(): Pair<Long, Long> = dbQuery {
        val written = Prescriptions.selectAll().count()
        val added = Prescriptions.selectAll().where { Prescriptions.addedAt.isNotNull() }.count()
        written to added
    }

    private fun ResultRow.toRecord() = PrescriptionRecord(
        id = this[Prescriptions.id],
        messageId = this[Prescriptions.messageId],
        conversationId = this[Prescriptions.conversationId],
        doctorId = this[Prescriptions.doctorId],
        patientId = this[Prescriptions.patientId],
        items = this[Prescriptions.items],
        note = this[Prescriptions.note],
        createdAt = this[Prescriptions.createdAt].toKotlinInstant(),
        cancelledAt = this[Prescriptions.cancelledAt]?.toKotlinInstant(),
        cancelReason = this[Prescriptions.cancelReason],
        addedAt = this[Prescriptions.addedAt]?.toKotlinInstant(),
    )
}
