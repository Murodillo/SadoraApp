package uz.sadora.server.plugins

import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import uz.sadora.contract.AccountStatus
import uz.sadora.server.core.now
import uz.sadora.server.user.UserRepository

/**
 * Answers, for every authenticated request, whether the account may still act.
 *
 * Blocking and "delete my account" both revoke the refresh tokens, but an access token
 * already in a phone's memory stays valid for its whole life — fifteen minutes in which
 * a blocked account could keep writing health data, spending Gul and reading the chat.
 * Sign-in checked the status; nothing after it did. The JWT `validate` block now asks
 * here, and a blocked account gets a [BlockedPrincipal] that every route turns into the
 * same `account_blocked` refusal sign-in gives.
 *
 * The status is cached briefly so the check costs one primary-key read per user per
 * [ttl] rather than per request; an operator's block therefore lands within seconds
 * rather than minutes, and [forget] drops the entry the moment the status changes in
 * this process.
 */
class AccountGate(
    private val users: UserRepository,
    private val ttl: Duration = 30.seconds,
) {
    private data class Cached(val status: AccountStatus, val expiresAtMillis: Long)

    private val cache = ConcurrentHashMap<Uuid, Cached>()

    suspend fun statusOf(userId: Uuid): AccountStatus? {
        val nowMillis = now().toEpochMilliseconds()
        cache[userId]?.takeIf { it.expiresAtMillis > nowMillis }?.let { return it.status }
        val status = users.statusOf(userId) ?: return null
        cache[userId] = Cached(status, nowMillis + ttl.inWholeMilliseconds)
        return status
    }

    /** Called when a status changes, so the new answer is served at once. */
    fun forget(userId: Uuid) {
        cache.remove(userId)
    }

    /**
     * The refusal for an account that may not act, or null when it may. A deleted row
     * and an unknown id read as "may": the token then names nobody, which the routes
     * report as they always did.
     */
    suspend fun blockedMessage(userId: Uuid): String? = when (statusOf(userId)) {
        null, AccountStatus.ACTIVE -> null
        AccountStatus.BLOCKED -> "Hisob bloklangan"
        AccountStatus.DELETION_PENDING -> "Hisobni o'chirish so'rovi yuborilgan"
    }
}
