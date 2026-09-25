package uz.sadora.server.doctor

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.sadora.contract.DoctorDocumentKind
import uz.sadora.contract.DoctorSpecialty
import uz.sadora.contract.DoctorStatus
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.DoctorDocuments
import uz.sadora.server.db.DoctorProfiles
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

data class DoctorRecord(
    val id: Uuid,
    val userId: Uuid,
    val fullName: String,
    val specialty: DoctorSpecialty,
    val workplace: String,
    val experienceYears: Int,
    val licenseNumber: String,
    val bio: String?,
    val status: DoctorStatus,
    val reviewNote: String?,
    val reviewedAt: Instant?,
    val verifiedAt: Instant?,
    val submittedAt: Instant,
)

/** The fields an application sets, whether it is the first or a resubmission. */
data class DoctorApplication(
    val fullName: String,
    val specialty: DoctorSpecialty,
    val workplace: String,
    val experienceYears: Int,
    val licenseNumber: String,
    val bio: String?,
)

data class DoctorDocument(
    val kind: DoctorDocumentKind,
    val mimeType: String,
    val bytes: ByteArray,
)

data class DoctorDocumentMeta(
    val id: Uuid,
    val kind: DoctorDocumentKind,
    val mimeType: String,
    val sizeBytes: Int,
    val createdAt: Instant,
)

/** Doctor profiles and the documents behind them. */
class DoctorRepository {

    suspend fun byUser(userId: Uuid): DoctorRecord? = dbQuery {
        DoctorProfiles.selectAll().where { DoctorProfiles.userId eq userId }.singleOrNull()?.toRecord()
    }

    suspend fun byId(id: Uuid): DoctorRecord? = dbQuery {
        DoctorProfiles.selectAll().where { DoctorProfiles.id eq id }.singleOrNull()?.toRecord()
    }

    suspend fun approved(): List<DoctorRecord> = dbQuery {
        DoctorProfiles.selectAll()
            .where { DoctorProfiles.status eq DoctorStatus.APPROVED.dbValue() }
            .orderBy(DoctorProfiles.verifiedAt to SortOrder.ASC)
            .map { it.toRecord() }
    }

    /**
     * Writes the application and replaces its documents in one transaction, and puts it
     * back in the queue. A resubmission clears the old note: the admin writes a new one.
     */
    suspend fun submit(userId: Uuid, application: DoctorApplication, documents: List<DoctorDocument>): DoctorRecord = dbQuery {
        val timestamp = now().toOffsetDateTime()
        val existing = DoctorProfiles.select(DoctorProfiles.id)
            .where { DoctorProfiles.userId eq userId }
            .singleOrNull()
            ?.get(DoctorProfiles.id)
        val id = existing ?: Uuid.random()
        if (existing == null) {
            DoctorProfiles.insert {
                it[DoctorProfiles.id] = id
                it[DoctorProfiles.userId] = userId
                it.fill(application)
                it[status] = DoctorStatus.PENDING.dbValue()
                it[submittedAt] = timestamp
                it[createdAt] = timestamp
                it[updatedAt] = timestamp
            }
        } else {
            DoctorProfiles.update({ DoctorProfiles.id eq id }) {
                it.fill(application)
                it[status] = DoctorStatus.PENDING.dbValue()
                it[reviewNote] = null
                it[submittedAt] = timestamp
                it[updatedAt] = timestamp
            }
            DoctorDocuments.deleteWhere { DoctorDocuments.doctorId eq id }
        }
        documents.forEach { document ->
            DoctorDocuments.insert {
                it[DoctorDocuments.id] = Uuid.random()
                it[doctorId] = id
                it[kind] = document.kind.dbValue()
                it[mimeType] = document.mimeType
                it[content] = document.bytes
                it[createdAt] = timestamp
            }
        }
        DoctorProfiles.selectAll().where { DoctorProfiles.id eq id }.single().toRecord()
    }

    private fun org.jetbrains.exposed.v1.core.statements.UpdateBuilder<*>.fill(application: DoctorApplication) {
        this[DoctorProfiles.fullName] = application.fullName
        this[DoctorProfiles.specialty] = application.specialty.dbValue()
        this[DoctorProfiles.workplace] = application.workplace
        this[DoctorProfiles.experienceYears] = application.experienceYears
        this[DoctorProfiles.licenseNumber] = application.licenseNumber
        this[DoctorProfiles.bio] = application.bio
    }

    suspend fun updateProfile(id: Uuid, workplace: String?, bio: String?, keepBio: Boolean): Unit = dbQuery {
        DoctorProfiles.update({ DoctorProfiles.id eq id }) {
            workplace?.let { value -> it[DoctorProfiles.workplace] = value }
            if (!keepBio) it[DoctorProfiles.bio] = bio
            it[updatedAt] = now().toOffsetDateTime()
        }
    }

    /** Moves a profile to [status]. The first approval stamps "verified since". */
    suspend fun setStatus(id: Uuid, status: DoctorStatus, note: String?, by: Uuid): Unit = dbQuery {
        val timestamp = now().toOffsetDateTime()
        val verified = DoctorProfiles.select(DoctorProfiles.verifiedAt)
            .where { DoctorProfiles.id eq id }
            .singleOrNull()
            ?.get(DoctorProfiles.verifiedAt)
        DoctorProfiles.update({ DoctorProfiles.id eq id }) {
            it[DoctorProfiles.status] = status.dbValue()
            it[reviewNote] = note
            it[reviewedBy] = by
            it[reviewedAt] = timestamp
            if (status == DoctorStatus.APPROVED && verified == null) it[verifiedAt] = timestamp
            it[updatedAt] = timestamp
        }
    }

    suspend fun list(status: DoctorStatus?, limit: Int, offset: Long): Pair<List<DoctorRecord>, Long> = dbQuery {
        var query = DoctorProfiles.selectAll()
        status?.let { query = query.andWhere { DoctorProfiles.status eq it.dbValue() } }
        val total = query.count()
        query.orderBy(DoctorProfiles.submittedAt to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map { it.toRecord() } to total
    }

    suspend fun countByStatus(): Map<DoctorStatus, Long> = dbQuery {
        val counter = DoctorProfiles.id.count()
        DoctorProfiles.select(DoctorProfiles.status, counter)
            .groupBy(DoctorProfiles.status)
            .associate { enumFromDb(it[DoctorProfiles.status], DoctorStatus.PENDING) to it[counter] }
    }

    suspend fun documentsOf(doctorId: Uuid): List<DoctorDocumentMeta> = dbQuery {
        DoctorDocuments.selectAll()
            .where { DoctorDocuments.doctorId eq doctorId }
            .orderBy(DoctorDocuments.createdAt to SortOrder.ASC)
            .map {
                DoctorDocumentMeta(
                    id = it[DoctorDocuments.id],
                    kind = enumFromDb(it[DoctorDocuments.kind], DoctorDocumentKind.OTHER),
                    mimeType = it[DoctorDocuments.mimeType],
                    sizeBytes = it[DoctorDocuments.content].size,
                    createdAt = it[DoctorDocuments.createdAt].toKotlinInstant(),
                )
            }
    }

    suspend fun documentCounts(doctorIds: Collection<Uuid>): Map<Uuid, Int> = dbQuery {
        if (doctorIds.isEmpty()) return@dbQuery emptyMap()
        val counter = DoctorDocuments.id.count()
        DoctorDocuments.select(DoctorDocuments.doctorId, counter)
            .where { DoctorDocuments.doctorId inList doctorIds.distinct() }
            .groupBy(DoctorDocuments.doctorId)
            .associate { it[DoctorDocuments.doctorId] to it[counter].toInt() }
    }

    suspend fun document(doctorId: Uuid, documentId: Uuid): DoctorDocument? = dbQuery {
        DoctorDocuments.selectAll()
            .where { (DoctorDocuments.id eq documentId) and (DoctorDocuments.doctorId eq doctorId) }
            .singleOrNull()
            ?.let {
                DoctorDocument(
                    kind = enumFromDb(it[DoctorDocuments.kind], DoctorDocumentKind.OTHER),
                    mimeType = it[DoctorDocuments.mimeType],
                    bytes = it[DoctorDocuments.content],
                )
            }
    }

    private fun ResultRow.toRecord() = DoctorRecord(
        id = this[DoctorProfiles.id],
        userId = this[DoctorProfiles.userId],
        fullName = this[DoctorProfiles.fullName],
        specialty = enumFromDb(this[DoctorProfiles.specialty], DoctorSpecialty.OTHER),
        workplace = this[DoctorProfiles.workplace],
        experienceYears = this[DoctorProfiles.experienceYears],
        licenseNumber = this[DoctorProfiles.licenseNumber],
        bio = this[DoctorProfiles.bio],
        status = enumFromDb(this[DoctorProfiles.status], DoctorStatus.PENDING),
        reviewNote = this[DoctorProfiles.reviewNote],
        reviewedAt = this[DoctorProfiles.reviewedAt]?.toKotlinInstant(),
        verifiedAt = this[DoctorProfiles.verifiedAt]?.toKotlinInstant(),
        submittedAt = this[DoctorProfiles.submittedAt].toKotlinInstant(),
    )
}
