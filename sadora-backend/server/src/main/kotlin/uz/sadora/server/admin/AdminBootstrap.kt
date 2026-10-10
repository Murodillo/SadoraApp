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

    /** [strict] outside a laptop: a password that sits in this repository is refused. */
    suspend fun run(strict: Boolean = false) {
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

        if (strict && password.lowercase() in AdminAuthService.KNOWN_DEFAULTS) {
            logger.error(
                "ADMIN_BOOTSTRAP_PASSWORD is a password published in the repository; the first " +
                    "admin account was not created. Set a random one.",
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
                // Off until she enrols an authenticator on the Security page; where 2FA
                // is mandatory, that page is all the panel shows her until then.
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
