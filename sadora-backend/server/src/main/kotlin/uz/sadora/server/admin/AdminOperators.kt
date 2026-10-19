package uz.sadora.server.admin

import java.security.SecureRandom
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.auth.PasswordHasher
import uz.sadora.server.auth.RequestContext
import uz.sadora.server.core.ConflictException
import uz.sadora.server.core.ForbiddenException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.AdminUsers
import uz.sadora.server.db.dbQuery
import uz.sadora.server.plugins.AdminPrincipal
import uz.sadora.server.plugins.AdminRole

/** One staff account as the Owner's operators page lists it. No hash, no 2FA secret. */
@Serializable
data class AdminOperatorView(
    val id: String,
    val email: String,
    val name: String,
    val role: AdminRole,
    val active: Boolean,
    val totpEnabled: Boolean,
    val passwordChangeRequired: Boolean,
    val lockedUntil: Instant? = null,
    val lastLoginAt: Instant? = null,
    val createdAt: Instant,
)

@Serializable
data class CreateOperatorRequest(val email: String, val name: String, val role: AdminRole)

@Serializable
data class UpdateOperatorRequest(val role: AdminRole)

@Serializable
data class OperatorStatusRequest(val active: Boolean)

/**
 * A temporary password, shown to the Owner once. Only its hash is kept, so the Owner hands
 * it over herself, and the operator must replace it before the panel opens for her.
 */
@Serializable
data class OperatorCredentials(val operator: AdminOperatorView, val temporaryPassword: String)

/**
 * The Owner's staff management: add an operator, change a role, switch an account off and
 * on, and hand out a fresh password or clear a lost authenticator.
 *
 * Every change takes effect on the operator's next request — the admin realm reads the
 * account on each call — and nobody can do any of it to herself: an Owner who demoted or
 * disabled herself would leave the panel with no Owner, and her own password and 2FA have
 * their own page that asks for proof.
 */
class AdminOperatorService(private val audit: AuditService) {

    suspend fun list(): List<AdminOperatorView> = dbQuery {
        AdminUsers.selectAll()
            .orderBy(AdminUsers.createdAt to SortOrder.ASC)
            .map { it.toOperator() }
    }

    suspend fun create(request: CreateOperatorRequest, owner: AdminPrincipal, context: RequestContext): OperatorCredentials {
        val email = request.email.trim().lowercase()
        if (email.length > 254 || !EMAIL.matches(email)) throw ValidationException("email", "Email noto'g'ri")
        val name = request.name.trim()
        if (name.isEmpty() || name.length > 80) throw ValidationException("name", "Ism 1–80 belgi bo'lsin")

        val exists = dbQuery { AdminUsers.selectAll().where { AdminUsers.email eq email }.count() > 0 }
        if (exists) throw ConflictException("Bu email bilan operator allaqachon bor")

        val password = temporaryPassword()
        val id = Uuid.random()
        dbQuery {
            AdminUsers.insert {
                it[AdminUsers.id] = id
                it[AdminUsers.email] = email
                it[passwordHash] = PasswordHasher.hash(password)
                it[AdminUsers.name] = name
                it[role] = request.role.name.lowercase()
                it[totpEnabled] = false
                it[status] = ACTIVE
                it[failedAttempts] = 0
                it[passwordChangeRequired] = true
                it[createdAt] = now().toOffsetDateTime()
                it[updatedAt] = now().toOffsetDateTime()
            }
        }
        record(owner, AuditActions.ADMIN_OPERATOR_CREATED, id, context, mapOf("role" to request.role.name.lowercase()))
        return OperatorCredentials(require(id), password)
    }

    suspend fun changeRole(id: Uuid, request: UpdateOperatorRequest, owner: AdminPrincipal, context: RequestContext): AdminOperatorView {
        refuseSelf(id, owner, "O'z rolingizni o'zgartira olmaysiz")
        val before = require(id)
        if (before.role == request.role) return before
        update(id) { it[role] = request.role.name.lowercase() }
        record(
            owner,
            AuditActions.ADMIN_OPERATOR_ROLE_CHANGED,
            id,
            context,
            mapOf("from" to before.role.name.lowercase(), "to" to request.role.name.lowercase()),
        )
        return require(id)
    }

    suspend fun setActive(id: Uuid, active: Boolean, owner: AdminPrincipal, context: RequestContext): AdminOperatorView {
        refuseSelf(id, owner, "O'z hisobingizni o'chira olmaysiz")
        val before = require(id)
        if (before.active == active) return before
        update(id) { it[status] = if (active) ACTIVE else DISABLED }
        record(owner, if (active) AuditActions.ADMIN_OPERATOR_ENABLED else AuditActions.ADMIN_OPERATOR_DISABLED, id, context)
        return require(id)
    }

    /** A new temporary password; also lifts a lockout, which is usually why she asked. */
    suspend fun resetPassword(id: Uuid, owner: AdminPrincipal, context: RequestContext): OperatorCredentials {
        refuseSelf(id, owner, "O'z parolingizni «Hisobim» sahifasida almashtiring")
        require(id)
        val password = temporaryPassword()
        val hash = PasswordHasher.hash(password)
        update(id) {
            it[passwordHash] = hash
            it[passwordChangeRequired] = true
            it[failedAttempts] = 0
            it[lockedUntil] = null
        }
        record(owner, AuditActions.ADMIN_OPERATOR_PASSWORD_RESET, id, context)
        return OperatorCredentials(require(id), password)
    }

    /** For a lost phone: 2FA goes off, and she enrols again from her own account page. */
    suspend fun resetTotp(id: Uuid, owner: AdminPrincipal, context: RequestContext): AdminOperatorView {
        refuseSelf(id, owner, "O'z 2FA'ingizni «Hisobim» sahifasida boshqaring")
        require(id)
        update(id) {
            it[totpEnabled] = false
            it[totpSecret] = null
            it[totpLastStep] = null
        }
        record(owner, AuditActions.ADMIN_OPERATOR_TOTP_RESET, id, context)
        return require(id)
    }

    private fun refuseSelf(id: Uuid, owner: AdminPrincipal, message: String) {
        if (id == owner.adminId) throw ForbiddenException(message = message)
    }

    private suspend fun require(id: Uuid): AdminOperatorView = dbQuery {
        AdminUsers.selectAll().where { AdminUsers.id eq id }.singleOrNull()?.toOperator()
    } ?: throw NotFoundException("Operator topilmadi")

    private suspend fun update(id: Uuid, change: AdminUsers.(org.jetbrains.exposed.v1.core.statements.UpdateStatement) -> Unit) {
        dbQuery {
            AdminUsers.update({ AdminUsers.id eq id }) {
                change(it)
                it[updatedAt] = now().toOffsetDateTime()
            }
        }
    }

    private suspend fun record(
        owner: AdminPrincipal,
        action: String,
        id: Uuid,
        context: RequestContext,
        metadata: Map<String, String> = emptyMap(),
    ) {
        audit.record(
            AuditEntry(
                actorType = ActorType.ADMIN,
                actorId = owner.adminId,
                actorLabel = owner.role.name.lowercase(),
                action = action,
                entityType = "admin_user",
                entityId = id.toString(),
                metadata = metadata,
                ip = context.ip,
                userAgent = context.userAgent,
            ),
        )
    }

    private fun ResultRow.toOperator() = AdminOperatorView(
        id = this[AdminUsers.id].toString(),
        email = this[AdminUsers.email],
        name = this[AdminUsers.name],
        role = AdminRole.entries.firstOrNull { it.name.equals(this[AdminUsers.role], ignoreCase = true) } ?: AdminRole.ANALYST,
        active = this[AdminUsers.status] == ACTIVE,
        totpEnabled = this[AdminUsers.totpEnabled],
        passwordChangeRequired = this[AdminUsers.passwordChangeRequired],
        lockedUntil = this[AdminUsers.lockedUntil]?.toKotlinInstant()?.takeIf { it > now() },
        lastLoginAt = this[AdminUsers.lastLoginAt]?.toKotlinInstant(),
        createdAt = this[AdminUsers.createdAt].toKotlinInstant(),
    )

    internal companion object {
        const val ACTIVE = "active"
        const val DISABLED = "disabled"
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        /** No 0/O, 1/l/I: it is read off one screen and typed into another. */
        private const val ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private val random = SecureRandom()

        /** Fourteen characters from 56, about 81 bits, with a letter and a digit as the hasher demands. */
        fun temporaryPassword(): String {
            while (true) {
                val candidate = String(CharArray(14) { ALPHABET[random.nextInt(ALPHABET.length)] })
                if (candidate.any { it.isDigit() } && candidate.any { it.isLetter() }) return candidate
            }
        }
    }
}
