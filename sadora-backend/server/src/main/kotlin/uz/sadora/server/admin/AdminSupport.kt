package uz.sadora.server.admin

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.notLike
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.auth.RequestContext
import uz.sadora.server.core.ForbiddenException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.AdminUsers
import uz.sadora.server.db.AuditLog
import uz.sadora.server.db.PartnerLinks
import uz.sadora.server.db.TelegramLinks
import uz.sadora.server.db.UserSupportNotes
import uz.sadora.server.db.Users
import uz.sadora.server.db.WearableConnections
import uz.sadora.server.db.dbQuery
import uz.sadora.server.plugins.AdminPrincipal
import uz.sadora.server.plugins.AdminRole

/**
 * What else hangs off an account, as support needs it to answer "why doesn't my partner see
 * it" or "my watch stopped": whether a link exists and how it stands, never who is on the
 * other end or what was shared.
 */
@Serializable
data class AdminConnectionsView(
    /** The Yaqinim she invited, by its state only. */
    val partner: AdminPartnerLinkView? = null,
    /** Accounts that have this one as their Yaqinim right now. */
    val partnerOf: Int = 0,
    val telegramLinkedAt: Instant? = null,
    val wearables: List<AdminWearableView> = emptyList(),
)

@Serializable
data class AdminPartnerLinkView(val status: String, val relation: String, val since: Instant)

@Serializable
data class AdminWearableView(
    val provider: String,
    val status: String,
    val lastSyncAt: Instant? = null,
    val lastError: String? = null,
)

@Serializable
data class AdminSupportNote(
    val id: String,
    val body: String,
    /** Null once the operator who wrote it no longer exists. */
    val authorName: String? = null,
    val createdAt: Instant,
    /** Written by the operator reading it — she may delete it; so may the Owner. */
    val mine: Boolean,
)

@Serializable
data class AddSupportNoteRequest(val body: String)

/**
 * The user card's support side: her connections, what happened on her account, and the
 * notes operators leave each other. Owner, Admin and Support reach all of it.
 */
class AdminSupportService(private val audit: AuditService) {

    suspend fun connections(userId: Uuid): AdminConnectionsView = dbQuery {
        // At most one link per inviter is not ended — the schema's partial unique index.
        val partner = PartnerLinks.selectAll()
            .where { (PartnerLinks.ownerId eq userId) and (PartnerLinks.status neq "ended") }
            .orderBy(PartnerLinks.createdAt to SortOrder.DESC)
            .firstOrNull()
            ?.let { row ->
                AdminPartnerLinkView(
                    status = row[PartnerLinks.status],
                    relation = row[PartnerLinks.relation],
                    since = (row[PartnerLinks.approvedAt] ?: row[PartnerLinks.acceptedAt] ?: row[PartnerLinks.createdAt])
                        .toKotlinInstant(),
                )
            }
        val partnerOf = PartnerLinks.selectAll()
            .where { (PartnerLinks.partnerId eq userId) and (PartnerLinks.status inList listOf("active", "paused")) }
            .count()
            .toInt()
        val telegram = TelegramLinks.selectAll()
            .where { TelegramLinks.userId eq userId }
            .singleOrNull()
            ?.get(TelegramLinks.linkedAt)
            ?.toKotlinInstant()
        val wearables = WearableConnections.selectAll()
            .where { WearableConnections.userId eq userId }
            .orderBy(WearableConnections.provider to SortOrder.ASC)
            .map { row ->
                AdminWearableView(
                    provider = row[WearableConnections.provider],
                    status = row[WearableConnections.status],
                    lastSyncAt = row[WearableConnections.lastSyncAt]?.toKotlinInstant(),
                    lastError = row[WearableConnections.lastError]?.take(200),
                )
            }
        AdminConnectionsView(partner, partnerOf, telegram, wearables)
    }

    /**
     * What she did and what was done to her account, newest first. Two indexed reads — her
     * as the actor, her account as the subject — merged here. Chat moderation is left out:
     * the moderation page never links an alias to an account, and a timeline of the same
     * minutes would. A non-Owner gets each event without its who, where and why, as on the
     * dashboard.
     */
    suspend fun timeline(userId: Uuid, reader: AdminPrincipal, limit: Int): List<AuditEntryView> {
        val rows = dbQuery {
            val asActor = AuditLog.selectAll()
                .where { (AuditLog.actorType eq ActorType.USER.name.lowercase()) and (AuditLog.actorId eq userId) and (AuditLog.action notLike "community.%") }
                .orderBy(AuditLog.createdAt to SortOrder.DESC)
                .limit(limit)
                .map { it.toAuditView() }
            val asSubject = AuditLog.selectAll()
                .where { (AuditLog.entityType eq "user") and (AuditLog.entityId eq userId.toString()) and (AuditLog.action notLike "community.%") }
                .orderBy(AuditLog.createdAt to SortOrder.DESC)
                .limit(limit)
                .map { it.toAuditView() }
            (asActor + asSubject).distinctBy { it.id }.sortedByDescending { it.createdAt }.take(limit)
        }
        return if (reader.role == AdminRole.OWNER) rows else rows.map { it.redacted() }
    }

    suspend fun notes(userId: Uuid, reader: AdminPrincipal): List<AdminSupportNote> = dbQuery {
        val names = AdminUsers.selectAll().associate { it[AdminUsers.id] to it[AdminUsers.name] }
        UserSupportNotes.selectAll()
            .where { UserSupportNotes.userId eq userId }
            .orderBy(UserSupportNotes.createdAt to SortOrder.DESC)
            .limit(MAX_NOTES)
            .map { row ->
                val author = row[UserSupportNotes.adminId]
                AdminSupportNote(
                    id = row[UserSupportNotes.id].toString(),
                    body = row[UserSupportNotes.body],
                    authorName = author?.let { names[it] },
                    createdAt = row[UserSupportNotes.createdAt].toKotlinInstant(),
                    mine = author == reader.adminId,
                )
            }
    }

    suspend fun addNote(userId: Uuid, request: AddSupportNoteRequest, author: AdminPrincipal, context: RequestContext): AdminSupportNote {
        val body = request.body.trim()
        if (body.isEmpty()) throw ValidationException("body", "Izohni yozing")
        if (body.length > MAX_LENGTH) throw ValidationException("body", "Izoh eng ko'pi $MAX_LENGTH belgi bo'lsin")
        val exists = dbQuery { Users.selectAll().where { Users.id eq userId }.count() > 0 }
        if (!exists) throw NotFoundException("Foydalanuvchi topilmadi")
        val id = Uuid.random()
        val createdAt = now()
        dbQuery {
            UserSupportNotes.insert {
                it[UserSupportNotes.id] = id
                it[UserSupportNotes.userId] = userId
                it[adminId] = author.adminId
                it[UserSupportNotes.body] = body
                it[UserSupportNotes.createdAt] = createdAt.toOffsetDateTime()
            }
        }
        // The note's words stay out of the log: the log is read by the Owner for who did
        // what, and a note is the operators' own working text.
        record(author, AuditActions.USER_NOTE_ADDED, userId, context)
        val name = dbQuery { AdminUsers.selectAll().where { AdminUsers.id eq author.adminId }.singleOrNull()?.get(AdminUsers.name) }
        return AdminSupportNote(id.toString(), body, name, createdAt, mine = true)
    }

    suspend fun deleteNote(userId: Uuid, noteId: Uuid, reader: AdminPrincipal, context: RequestContext) {
        val note = dbQuery {
            UserSupportNotes.selectAll()
                .where { (UserSupportNotes.id eq noteId) and (UserSupportNotes.userId eq userId) }
                .singleOrNull()
        } ?: throw NotFoundException("Izoh topilmadi")
        if (note[UserSupportNotes.adminId] != reader.adminId && reader.role != AdminRole.OWNER) {
            throw ForbiddenException(message = "Faqat o'z izohingizni o'chira olasiz")
        }
        dbQuery { UserSupportNotes.deleteWhere { UserSupportNotes.id eq noteId } }
        record(reader, AuditActions.USER_NOTE_DELETED, userId, context)
    }

    private suspend fun record(admin: AdminPrincipal, action: String, userId: Uuid, context: RequestContext) {
        audit.record(
            AuditEntry(
                actorType = ActorType.ADMIN,
                actorId = admin.adminId,
                actorLabel = admin.role.name.lowercase(),
                action = action,
                entityType = "user",
                entityId = userId.toString(),
                ip = context.ip,
                userAgent = context.userAgent,
            ),
        )
    }

    internal companion object {
        const val MAX_LENGTH = 1000
        const val MAX_NOTES = 100
    }
}

private fun org.jetbrains.exposed.v1.core.ResultRow.toAuditView() = AuditEntryView(
    id = this[AuditLog.id].toString(),
    actorType = this[AuditLog.actorType],
    actorId = this[AuditLog.actorId]?.toString(),
    actorLabel = this[AuditLog.actorLabel],
    action = this[AuditLog.action],
    entityType = this[AuditLog.entityType],
    entityId = this[AuditLog.entityId],
    reason = this[AuditLog.reason],
    metadata = this[AuditLog.metadata],
    ip = this[AuditLog.ip],
    createdAt = this[AuditLog.createdAt].toKotlinInstant(),
)
