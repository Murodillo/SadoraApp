package uz.sadora.server.partner

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.sadora.contract.PartnerLinkStatus
import uz.sadora.contract.PartnerPermissions
import uz.sadora.contract.PartnerRelation
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.PartnerLinks
import uz.sadora.server.db.Users
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

/** A live row of `partner_links`. Ended rows are never read back as one. */
data class PartnerLinkRecord(
    val id: Uuid,
    val ownerId: Uuid,
    val partnerId: Uuid?,
    val status: PartnerLinkStatus,
    val relation: PartnerRelation,
    val codeExpiresAt: Instant?,
    val permissions: PartnerPermissions,
    val createdAt: Instant,
    val acceptedAt: Instant?,
    val approvedAt: Instant?,
    val pausedAt: Instant?,
    val lastViewedAt: Instant?,
)

/** Someone an account follows, with the name the list shows. */
data class FollowingRecord(val link: PartnerLinkRecord, val ownerName: String)

class PartnerRepository {

    /** Her live row — invite, pending, active or paused — if she has one. */
    suspend fun liveOf(ownerId: Uuid): PartnerLinkRecord? = dbQuery {
        PartnerLinks.selectAll()
            .where { (PartnerLinks.ownerId eq ownerId) and (PartnerLinks.status neq ENDED) }
            .singleOrNull()
            ?.toRecord()
    }

    suspend fun byId(id: Uuid): PartnerLinkRecord? = dbQuery {
        PartnerLinks.selectAll()
            .where { (PartnerLinks.id eq id) and (PartnerLinks.status neq ENDED) }
            .singleOrNull()
            ?.toRecord()
    }

    /** The invite a code opens, while it is still an invite. Expiry is the caller's to judge. */
    suspend fun inviteByCodeHash(codeHash: String): PartnerLinkRecord? = dbQuery {
        PartnerLinks.selectAll()
            .where { (PartnerLinks.codeHash eq codeHash) and (PartnerLinks.status eq PartnerLinkStatus.INVITED.dbValue()) }
            .singleOrNull()
            ?.toRecord()
    }

    /** Everyone this account follows, newest first, with their names. */
    suspend fun followingOf(partnerId: Uuid): List<FollowingRecord> = dbQuery {
        // Joined on the owner explicitly: the table points at users twice.
        PartnerLinks.join(Users, JoinType.INNER, PartnerLinks.ownerId, Users.id)
            .selectAll()
            .where { (PartnerLinks.partnerId eq partnerId) and (PartnerLinks.status inList FOLLOWED_STATUSES) }
            .orderBy(PartnerLinks.createdAt to SortOrder.DESC)
            .map { FollowingRecord(it.toRecord(), it[Users.name]) }
    }

    suspend fun createInvite(
        ownerId: Uuid,
        relation: PartnerRelation,
        codeHash: String,
        expiresAt: Instant,
        at: Instant,
    ): PartnerLinkRecord = dbQuery {
        val id = Uuid.random()
        PartnerLinks.insert {
            it[PartnerLinks.id] = id
            it[PartnerLinks.ownerId] = ownerId
            it[status] = PartnerLinkStatus.INVITED.dbValue()
            it[PartnerLinks.relation] = relation.dbValue()
            it[PartnerLinks.codeHash] = codeHash
            it[codeExpiresAt] = expiresAt.toOffsetDateTime()
            it[createdAt] = at.toOffsetDateTime()
        }
        PartnerLinks.selectAll().where { PartnerLinks.id eq id }.single().toRecord()
    }

    /**
     * Ends a row. [by] says who: `owner`, `partner`, `superseded` or `account`.
     * False when it was already ended — two taps racing each other end it once.
     */
    suspend fun end(id: Uuid, by: String, at: Instant): Boolean = dbQuery {
        PartnerLinks.update({ (PartnerLinks.id eq id) and (PartnerLinks.status neq ENDED) }) {
            it[status] = ENDED
            it[codeHash] = null
            it[endedAt] = at.toOffsetDateTime()
            it[endedBy] = by
        } > 0
    }

    /**
     * The code was typed: the row now names the person and waits for her. Conditional on
     * still being an invite, so the same code typed on two phones at once is taken once.
     */
    suspend fun accept(id: Uuid, partnerId: Uuid, at: Instant): Boolean = dbQuery {
        PartnerLinks.update({ (PartnerLinks.id eq id) and (PartnerLinks.status eq PartnerLinkStatus.INVITED.dbValue()) }) {
            it[PartnerLinks.partnerId] = partnerId
            it[status] = PartnerLinkStatus.PENDING.dbValue()
            it[codeHash] = null
            it[acceptedAt] = at.toOffsetDateTime()
        } > 0
    }

    suspend fun approve(id: Uuid, at: Instant): Boolean = dbQuery {
        PartnerLinks.update({ (PartnerLinks.id eq id) and (PartnerLinks.status eq PartnerLinkStatus.PENDING.dbValue()) }) {
            it[status] = PartnerLinkStatus.ACTIVE.dbValue()
            it[approvedAt] = at.toOffsetDateTime()
        } > 0
    }

    suspend fun setPaused(id: Uuid, paused: Boolean, at: Instant): Boolean = dbQuery {
        val from = if (paused) PartnerLinkStatus.ACTIVE else PartnerLinkStatus.PAUSED
        PartnerLinks.update({ (PartnerLinks.id eq id) and (PartnerLinks.status eq from.dbValue()) }) {
            it[status] = (if (paused) PartnerLinkStatus.PAUSED else PartnerLinkStatus.ACTIVE).dbValue()
            it[pausedAt] = if (paused) at.toOffsetDateTime() else null
        } > 0
    }

    suspend fun savePermissions(id: Uuid, permissions: PartnerPermissions): Unit = dbQuery {
        PartnerLinks.update({ PartnerLinks.id eq id }) {
            it[permCycle] = permissions.cycle
            it[permFertile] = permissions.fertile
            it[permMood] = permissions.mood
            it[permSymptoms] = permissions.symptoms
            it[permPregnancy] = permissions.pregnancy
            it[permAppointments] = permissions.appointments
            it[permCare] = permissions.care
        }
    }

    suspend fun recordView(id: Uuid, at: Instant): Unit = dbQuery {
        PartnerLinks.update({ PartnerLinks.id eq id }) {
            it[viewCount] = viewCount + 1
            it[lastViewedAt] = at.toOffsetDateTime()
        }
    }

    /** Every active row whose owner is [ownerId]'s — for the alerts that follow her records. */
    suspend fun activeOf(ownerId: Uuid): PartnerLinkRecord? = dbQuery {
        PartnerLinks.selectAll()
            .where { (PartnerLinks.ownerId eq ownerId) and (PartnerLinks.status eq PartnerLinkStatus.ACTIVE.dbValue()) }
            .singleOrNull()
            ?.toRecord()
    }

    /** All active rows, for the daily alert pass. */
    suspend fun allActive(): List<PartnerLinkRecord> = dbQuery {
        PartnerLinks.selectAll()
            .where { PartnerLinks.status eq PartnerLinkStatus.ACTIVE.dbValue() }
            .map { it.toRecord() }
    }

    private fun ResultRow.toRecord() = PartnerLinkRecord(
        id = this[PartnerLinks.id],
        ownerId = this[PartnerLinks.ownerId],
        partnerId = this[PartnerLinks.partnerId],
        status = enumFromDb(this[PartnerLinks.status], PartnerLinkStatus.PAUSED),
        relation = enumFromDb(this[PartnerLinks.relation], PartnerRelation.OTHER),
        codeExpiresAt = this[PartnerLinks.codeExpiresAt]?.toKotlinInstant(),
        permissions = PartnerPermissions(
            cycle = this[PartnerLinks.permCycle],
            fertile = this[PartnerLinks.permFertile],
            mood = this[PartnerLinks.permMood],
            symptoms = this[PartnerLinks.permSymptoms],
            pregnancy = this[PartnerLinks.permPregnancy],
            appointments = this[PartnerLinks.permAppointments],
            care = this[PartnerLinks.permCare],
        ),
        createdAt = this[PartnerLinks.createdAt].toKotlinInstant(),
        acceptedAt = this[PartnerLinks.acceptedAt]?.toKotlinInstant(),
        approvedAt = this[PartnerLinks.approvedAt]?.toKotlinInstant(),
        pausedAt = this[PartnerLinks.pausedAt]?.toKotlinInstant(),
        lastViewedAt = this[PartnerLinks.lastViewedAt]?.toKotlinInstant(),
    )

    private companion object {
        const val ENDED = "ended"
        val FOLLOWED_STATUSES = listOf(PartnerLinkStatus.PENDING, PartnerLinkStatus.ACTIVE, PartnerLinkStatus.PAUSED)
            .map { it.dbValue() }
    }
}
