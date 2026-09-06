package uz.sadora.server.admin

import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.auth.JwtService
import uz.sadora.server.auth.PasswordHasher
import uz.sadora.server.auth.RequestContext
import uz.sadora.server.auth.TokenSubjectType
import uz.sadora.server.core.ForbiddenException
import uz.sadora.server.core.UnauthorizedException
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.AdminUsers
import uz.sadora.server.db.dbQuery
import uz.sadora.server.plugins.AdminRole

/**
 * Admin sign-in: email, password, and a TOTP code.
 *
 * Deliberately harsher than the user flow — five bad attempts lock the account for
 * fifteen minutes, every attempt is audited, and 2FA cannot be skipped once enabled.
 * There are five to ten of these accounts and they can see every subscription in the
 * product.
 */
class AdminAuthService(
    private val jwt: JwtService,
    private val audit: AuditService,
) {

    suspend fun signIn(request: AdminSignInRequest, context: RequestContext): AdminSession {
        val email = request.email.trim().lowercase()
        val row = dbQuery {
            AdminUsers.selectAll().where { AdminUsers.email eq email }.singleOrNull()
        }

        if (row == null) {
            PasswordHasher.verify(request.password, DUMMY_HASH)
            recordFailure(email, "unknown_account", context)
            throw UnauthorizedException(message = "Email yoki parol noto'g'ri")
        }

        val adminId = row[AdminUsers.id]
        val lockExpiry = row[AdminUsers.lockedUntil]?.toKotlinInstant()
        if (lockExpiry != null && lockExpiry > now()) {
            recordFailure(email, "locked", context)
            throw ForbiddenException(message = "Hisob vaqtincha bloklangan")
        }
        if (row[AdminUsers.status] != "active") {
            recordFailure(email, "inactive", context)
            throw ForbiddenException(message = "Hisob faol emas")
        }

        if (!PasswordHasher.verify(request.password, row[AdminUsers.passwordHash])) {
            registerFailedAttempt(adminId, row[AdminUsers.failedAttempts] + 1)
            recordFailure(email, "bad_password", context)
            throw UnauthorizedException(message = "Email yoki parol noto'g'ri")
        }

        if (row[AdminUsers.totpEnabled]) {
            val secret = row[AdminUsers.totpSecret]
            val code = request.totpCode
            if (secret == null || code == null || !Totp.verify(secret, code)) {
                registerFailedAttempt(adminId, row[AdminUsers.failedAttempts] + 1)
                recordFailure(email, "bad_totp", context)
                throw UnauthorizedException(message = "2FA kodi noto'g'ri")
            }
        }

        val role = AdminRole.entries
            .firstOrNull { it.name.equals(row[AdminUsers.role], ignoreCase = true) }
            ?: AdminRole.ANALYST

        dbQuery {
            AdminUsers.update({ AdminUsers.id eq adminId }) {
                it[failedAttempts] = 0
                it[AdminUsers.lockedUntil] = null
                it[lastLoginAt] = now().toOffsetDateTime()
            }
        }

        val token = jwt.issueAdminToken(adminId, role)
        audit.record(
            AuditEntry(
                actorType = ActorType.ADMIN,
                actorId = adminId,
                actorLabel = email,
                action = AuditActions.ADMIN_SIGNED_IN,
                entityType = "admin_user",
                entityId = adminId.toString(),
                metadata = mapOf("role" to role.name.lowercase()),
                ip = context.ip,
                userAgent = context.userAgent,
            ),
        )

        return AdminSession(
            accessToken = token.value,
            expiresAt = token.expiresAt,
            name = row[AdminUsers.name],
            email = email,
            role = role,
        )
    }

    private suspend fun registerFailedAttempt(adminId: Uuid, attempts: Int) {
        dbQuery {
            AdminUsers.update({ AdminUsers.id eq adminId }) {
                it[failedAttempts] = attempts
                if (attempts >= MAX_FAILED_ATTEMPTS) {
                    it[lockedUntil] = (now() + LOCKOUT).toOffsetDateTime()
                }
            }
        }
    }

    private suspend fun recordFailure(email: String, reason: String, context: RequestContext) {
        audit.record(
            AuditEntry(
                actorType = ActorType.ADMIN,
                actorLabel = email,
                action = AuditActions.ADMIN_SIGN_IN_FAILED,
                entityType = "admin_user",
                reason = reason,
                ip = context.ip,
                userAgent = context.userAgent,
            ),
        )
    }

    private companion object {
        const val MAX_FAILED_ATTEMPTS = 5
        val LOCKOUT = 15.minutes
        const val DUMMY_HASH =
            "\$2a\$12\$C6UzMDM.H6dfI/f/IKcEe.3Xxq0hEfLGqE.pB6oPLM2NLpQ2ZLZ0W"
    }
}
