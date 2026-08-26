package uz.sadora.server.audit

import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.jdbc.insert
import uz.sadora.server.core.now
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.AuditLog
import uz.sadora.server.db.dbQuery

enum class ActorType { USER, ADMIN, SYSTEM }

/**
 * Actions worth answering "who did this, and why" about later.
 *
 * Anything that touches another person's account, money, or the rules the product runs
 * by belongs here. Ordinary reads do not — an audit log nobody can scan is not an audit
 * log.
 */
object AuditActions {
    const val USER_SIGNED_IN = "user.signed_in"
    const val USER_SIGNED_UP = "user.signed_up"
    const val USER_SIGNED_OUT = "user.signed_out"
    const val USER_ONBOARDED = "user.onboarded"
    const val USER_PROFILE_UPDATED = "user.profile_updated"
    const val USER_CONSENT_CHANGED = "user.consent_changed"
    const val USER_DELETION_REQUESTED = "user.deletion_requested"
    const val USER_BLOCKED = "user.blocked"
    const val USER_UNBLOCKED = "user.unblocked"
    const val REFRESH_TOKEN_REUSED = "security.refresh_token_reused"
    const val ADMIN_SIGNED_IN = "admin.signed_in"
    const val ADMIN_SIGN_IN_FAILED = "admin.sign_in_failed"
    const val ENTITLEMENT_DEFINITION_UPDATED = "entitlement.definition_updated"
    const val ENTITLEMENT_OVERRIDE_SET = "entitlement.override_set"
    const val ENTITLEMENT_OVERRIDE_CLEARED = "entitlement.override_cleared"
    const val SUBSCRIPTION_GRANTED = "subscription.granted"
    const val FEATURE_FLAG_UPDATED = "flag.updated"
    const val FEATURE_FLAG_RULE_ADDED = "flag.rule_added"
    const val FEATURE_FLAG_RULE_REMOVED = "flag.rule_removed"
}

data class AuditEntry(
    val actorType: ActorType,
    val actorId: Uuid? = null,
    val actorLabel: String? = null,
    val action: String,
    val entityType: String? = null,
    val entityId: String? = null,
    val reason: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val ip: String? = null,
    val userAgent: String? = null,
)

class AuditService {

    suspend fun record(entry: AuditEntry) {
        dbQuery {
            AuditLog.insert {
                it[id] = Uuid.random()
                it[actorType] = entry.actorType.name.lowercase()
                it[actorId] = entry.actorId
                it[actorLabel] = entry.actorLabel
                it[action] = entry.action
                it[entityType] = entry.entityType
                it[entityId] = entry.entityId
                it[reason] = entry.reason
                it[metadata] = entry.metadata
                it[ip] = entry.ip
                it[userAgent] = entry.userAgent
                it[createdAt] = now().toOffsetDateTime()
            }
        }
    }
}
