package uz.sadora.server.admin

import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.slf4j.LoggerFactory
import uz.sadora.server.auth.PasswordHasher
import uz.sadora.server.core.now
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.AdminUsers
import uz.sadora.server.db.dbQuery
import uz.sadora.server.plugins.AdminRole

/**
 * Creates the first Owner account so a fresh environment is reachable at all.
 *
 * Runs only when the table is empty and both variables are set — it can never overwrite
 * an existing account, and a deployment that forgets the variables gets a loud warning
 * rather than a silently guessable default password.
 */
object AdminBootstrap {

    private val logger = LoggerFactory.getLogger(AdminBootstrap::class.java)

    suspend fun run() {
        val email = System.getenv("ADMIN_BOOTSTRAP_EMAIL")?.trim()?.lowercase()
        val password = System.getenv("ADMIN_BOOTSTRAP_PASSWORD")

        val existing = dbQuery { AdminUsers.selectAll().limit(1).count() }
        if (existing > 0) return

        if (email.isNullOrBlank() || password.isNullOrBlank()) {
            logger.warn(
                "No admin accounts exist and ADMIN_BOOTSTRAP_EMAIL / ADMIN_BOOTSTRAP_PASSWORD " +
                    "are not set — the admin panel cannot be signed into yet.",
            )
            return
        }

        PasswordHasher.validate(password)
        dbQuery {
            AdminUsers.insert {
                it[id] = Uuid.random()
                it[AdminUsers.email] = email
                it[passwordHash] = PasswordHasher.hash(password)
                it[name] = "Owner"
                it[role] = AdminRole.OWNER.name.lowercase()
                // 2FA is switched on per account once the operator has enrolled an
                // authenticator; the enrolment screen is sprint 1 admin work.
                it[totpEnabled] = false
                it[status] = "active"
                it[failedAttempts] = 0
                it[createdAt] = now().toOffsetDateTime()
                it[updatedAt] = now().toOffsetDateTime()
            }
        }
        logger.info("Bootstrapped the first admin account: {}", email)
    }
}
