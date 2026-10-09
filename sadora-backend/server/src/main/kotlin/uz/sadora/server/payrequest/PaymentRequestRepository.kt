package uz.sadora.server.payrequest

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.sadora.contract.PaymentRequestKind
import uz.sadora.contract.PaymentRequestStatus
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.PaymentRequests
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

data class PaymentRequestRecord(
    val id: Uuid,
    val ownerId: Uuid,
    val kind: PaymentRequestKind,
    val planId: String?,
    val consultationSessionId: Uuid?,
    val doctorId: Uuid?,
    /** The legendary pet a PET request is for. */
    val pet: String? = null,
    /** The paid avatar frame a FRAME request is for. */
    val frame: String? = null,
    val amountMinor: Long,
    val note: String?,
    val partnerLinkId: Uuid?,
    val status: PaymentRequestStatus,
    val createdAt: Instant,
    val expiresAt: Instant,
    val remindedAt: Instant?,
    val closedAt: Instant?,
    val paidBy: Uuid?,
)

class PaymentRequestRepository {

    suspend fun create(
        ownerId: Uuid,
        kind: PaymentRequestKind,
        planId: String?,
        consultationSessionId: Uuid?,
        doctorId: Uuid?,
        amountMinor: Long,
        note: String?,
        partnerLinkId: Uuid?,
        webTokenHash: String,
        at: Instant,
        expiresAt: Instant,
        pet: String? = null,
        frame: String? = null,
    ): PaymentRequestRecord = dbQuery {
        val id = Uuid.random()
        PaymentRequests.insert {
            it[PaymentRequests.id] = id
            it[PaymentRequests.ownerId] = ownerId
            it[PaymentRequests.kind] = kind.dbValue()
            it[PaymentRequests.planId] = planId
            it[PaymentRequests.consultationSessionId] = consultationSessionId
            it[PaymentRequests.doctorId] = doctorId
            it[PaymentRequests.pet] = pet
            it[PaymentRequests.frame] = frame
            it[PaymentRequests.amountMinor] = amountMinor
            it[PaymentRequests.note] = note
            it[PaymentRequests.partnerLinkId] = partnerLinkId
            it[PaymentRequests.webTokenHash] = webTokenHash
            it[status] = PaymentRequestStatus.OPEN.dbValue()
            it[createdAt] = at.toOffsetDateTime()
            it[PaymentRequests.expiresAt] = expiresAt.toOffsetDateTime()
        }
        PaymentRequests.selectAll().where { PaymentRequests.id eq id }.single().toRecord()
    }

    suspend fun byId(id: Uuid): PaymentRequestRecord? = dbQuery {
        PaymentRequests.selectAll().where { PaymentRequests.id eq id }.singleOrNull()?.toRecord()
    }

    suspend fun byTokenHash(hash: String): PaymentRequestRecord? = dbQuery {
        PaymentRequests.selectAll().where { PaymentRequests.webTokenHash eq hash }.singleOrNull()?.toRecord()
    }

    /** Her newest request, open or closed. */
    suspend fun latestOf(ownerId: Uuid): PaymentRequestRecord? = dbQuery {
        PaymentRequests.selectAll()
            .where { PaymentRequests.ownerId eq ownerId }
            .orderBy(PaymentRequests.createdAt to SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.toRecord()
    }

    suspend fun openOf(ownerId: Uuid): PaymentRequestRecord? = dbQuery {
        PaymentRequests.selectAll()
            .where { (PaymentRequests.ownerId eq ownerId) and (PaymentRequests.status eq OPEN) }
            .singleOrNull()
            ?.toRecord()
    }

    /** Open requests sent along any of [linkIds]. */
    suspend fun openOnLinks(linkIds: Collection<Uuid>): List<PaymentRequestRecord> = dbQuery {
        if (linkIds.isEmpty()) return@dbQuery emptyList()
        PaymentRequests.selectAll()
            .where { (PaymentRequests.partnerLinkId inList linkIds) and (PaymentRequests.status eq OPEN) }
            .orderBy(PaymentRequests.createdAt to SortOrder.DESC)
            .map { it.toRecord() }
    }

    suspend fun rotateToken(id: Uuid, hash: String): Unit = dbQuery {
        PaymentRequests.update({ PaymentRequests.id eq id }) { it[webTokenHash] = hash }
    }

    /** Closes an open request; false when it was no longer open. */
    suspend fun close(id: Uuid, status: PaymentRequestStatus, at: Instant): Boolean = dbQuery {
        PaymentRequests.update({ (PaymentRequests.id eq id) and (PaymentRequests.status eq OPEN) }) {
            it[PaymentRequests.status] = status.dbValue()
            it[closedAt] = at.toOffsetDateTime()
        } > 0
    }

    /**
     * Money arrived: paid, whatever it had become meanwhile — a payment that lands after
     * she cancelled has still bought her the thing. False when it was already paid.
     */
    suspend fun markPaid(id: Uuid, paidBy: Uuid?, transactionId: Uuid, at: Instant): Boolean = dbQuery {
        PaymentRequests.update({ (PaymentRequests.id eq id) and (PaymentRequests.status neq PaymentRequestStatus.PAID.dbValue()) }) {
            it[status] = PaymentRequestStatus.PAID.dbValue()
            it[PaymentRequests.paidBy] = paidBy
            it[PaymentRequests.transactionId] = transactionId
            it[closedAt] = at.toOffsetDateTime()
        } > 0
    }

    suspend fun dueToExpire(at: Instant, limit: Int): List<PaymentRequestRecord> = dbQuery {
        PaymentRequests.selectAll()
            .where { (PaymentRequests.status eq OPEN) and (PaymentRequests.expiresAt lessEq at.toOffsetDateTime()) }
            .limit(limit)
            .map { it.toRecord() }
    }

    /** Open, pushed to someone, unanswered since [before], never reminded. */
    suspend fun dueForReminder(before: Instant, limit: Int): List<PaymentRequestRecord> = dbQuery {
        PaymentRequests.selectAll()
            .where {
                (PaymentRequests.status eq OPEN) and PaymentRequests.remindedAt.isNull() and
                    PaymentRequests.partnerLinkId.isNotNull() and (PaymentRequests.createdAt less before.toOffsetDateTime())
            }
            .limit(limit)
            .map { it.toRecord() }
    }

    /** Claims the one reminder; false when another pass took it. */
    suspend fun markReminded(id: Uuid, at: Instant): Boolean = dbQuery {
        PaymentRequests.update({ (PaymentRequests.id eq id) and PaymentRequests.remindedAt.isNull() }) {
            it[remindedAt] = at.toOffsetDateTime()
        } > 0
    }

    private fun ResultRow.toRecord() = PaymentRequestRecord(
        id = this[PaymentRequests.id],
        ownerId = this[PaymentRequests.ownerId],
        kind = enumFromDb(this[PaymentRequests.kind], PaymentRequestKind.PREMIUM),
        planId = this[PaymentRequests.planId],
        consultationSessionId = this[PaymentRequests.consultationSessionId],
        doctorId = this[PaymentRequests.doctorId],
        pet = this[PaymentRequests.pet],
        frame = this[PaymentRequests.frame],
        amountMinor = this[PaymentRequests.amountMinor],
        note = this[PaymentRequests.note],
        partnerLinkId = this[PaymentRequests.partnerLinkId],
        status = enumFromDb(this[PaymentRequests.status], PaymentRequestStatus.EXPIRED),
        createdAt = this[PaymentRequests.createdAt].toKotlinInstant(),
        expiresAt = this[PaymentRequests.expiresAt].toKotlinInstant(),
        remindedAt = this[PaymentRequests.remindedAt]?.toKotlinInstant(),
        closedAt = this[PaymentRequests.closedAt]?.toKotlinInstant(),
        paidBy = this[PaymentRequests.paidBy],
    )

    private companion object {
        val OPEN = PaymentRequestStatus.OPEN.dbValue()
    }
}
