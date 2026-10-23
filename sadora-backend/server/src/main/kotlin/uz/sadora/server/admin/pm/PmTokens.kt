package uz.sadora.server.admin.pm

import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditService
import uz.sadora.server.auth.RequestContext
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

/**
 * Long-lived keys for tools/pm, which Claude sessions call from the Mac.
 *
 * The panel's own token is short and cannot be refreshed — right for a person with an
 * authenticator, useless for a command-line tool. These live until revoked instead, and
 * pay for it by opening nothing but /admin/pm: they are a separate authentication
 * provider, mounted on that route alone. Each acts as the operator who made it, read fresh
 * on every call, so a disabled or demoted operator's tokens stop with her.
 */
class PmTokenService(private val audit: AuditService, private val requireTotp: Boolean) {

    suspend fun list(actor: AdminPrincipal): List<PmTokenView> = dbQuery {
        PmTokens.selectAll()
            .apply { if (actor.role !in PM_LEAD_ROLES) where { PmTokens.adminId eq actor.adminId } }
            .orderBy(PmTokens.createdAt to SortOrder.DESC)
            .map { it.toView() }
    }

    suspend fun create(request: CreatePmTokenRequest, actor: AdminPrincipal, context: RequestContext): PmTokenCreated {
        refuseToken(actor)
        val name = request.name.trim()
        if (name.isEmpty() || name.length > 80) throw ValidationException("name", "Nomi 1–80 belgi bo'lsin")
        val token = PREFIX + Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(ByteArray(32).also(random::nextBytes))
        val id = Uuid.random()
        val view = dbQuery {
            PmTokens.insert {
                it[PmTokens.id] = id
                it[adminId] = actor.adminId
                it[PmTokens.name] = name
                it[tokenHash] = hash(token)
                it[createdAt] = now().toOffsetDateTime()
            }
            PmTokens.selectAll().where { PmTokens.id eq id }.single().toView()
        }
        audit.recordPm(actor, AuditActions.PM_TOKEN_CREATED, "pm_token", id.toString(), context, mapOf("name" to name))
        return PmTokenCreated(token, view)
    }

    /** Her own, or anyone's for the Owner and Admin. Someone else's reads as not there. */
    suspend fun revoke(id: Uuid, actor: AdminPrincipal, context: RequestContext) {
        refuseToken(actor)
        val revoked = dbQuery {
            val row = PmTokens.selectAll().where { PmTokens.id eq id }.singleOrNull()
            if (row == null || (actor.role !in PM_LEAD_ROLES && row[PmTokens.adminId] != actor.adminId)) {
                throw NotFoundException("Token topilmadi")
            }
            PmTokens.update({ (PmTokens.id eq id) and PmTokens.revokedAt.isNull() }) { it[revokedAt] = now().toOffsetDateTime() } > 0
        }
        if (revoked) audit.recordPm(actor, AuditActions.PM_TOKEN_REVOKED, "pm_token", id.toString(), context)
    }

    /** The operator behind a presented token, or null: unknown, revoked, or she can no longer reach the section. */
    suspend fun principal(token: String): AdminPrincipal? {
        if (!token.startsWith(PREFIX)) return null
        val digest = hash(token)
        return dbQuery {
            val row = PmTokens.selectAll().where { (PmTokens.tokenHash eq digest) and PmTokens.revokedAt.isNull() }.singleOrNull()
                ?: return@dbQuery null
            val admin = AdminUsers.selectAll().where { AdminUsers.id eq row[PmTokens.adminId] }.singleOrNull() ?: return@dbQuery null
            val role = AdminRole.entries.firstOrNull { it.name.equals(admin[AdminUsers.role], true) }
            if (admin[AdminUsers.status] != "active" || role == null || role !in PM_ROLES) return@dbQuery null
            // A write per call would be a write per CLI command; once a minute says enough.
            val last = row[PmTokens.lastUsedAt]?.toKotlinInstant()
            if (last == null || now() - last > 1.minutes) {
                PmTokens.update({ PmTokens.id eq row[PmTokens.id] }) { it[lastUsedAt] = now().toOffsetDateTime() }
            }
            AdminPrincipal(
                adminId = admin[AdminUsers.id],
                role = role,
                // Where 2FA is mandatory, an operator who has turned hers off is held back here too.
                totpSetupRequired = requireTotp && !admin[AdminUsers.totpEnabled],
                passwordChangeRequired = admin[AdminUsers.passwordChangeRequired],
                viaToken = true,
            )
        }
    }

    private fun refuseToken(actor: AdminPrincipal) {
        if (actor.viaToken) throw ForbiddenException(message = "Tokenlar faqat panelda boshqariladi")
    }

    private fun ResultRow.toView() = PmTokenView(
        id = this[PmTokens.id].toString(),
        name = this[PmTokens.name],
        adminId = this[PmTokens.adminId].toString(),
        createdAt = this[PmTokens.createdAt].toKotlinInstant(),
        lastUsedAt = this[PmTokens.lastUsedAt]?.toKotlinInstant(),
        revoked = this[PmTokens.revokedAt] != null,
    )

    internal companion object {
        const val PREFIX = "sadpm_"
        private val random = SecureRandom()

        fun hash(token: String): String =
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
