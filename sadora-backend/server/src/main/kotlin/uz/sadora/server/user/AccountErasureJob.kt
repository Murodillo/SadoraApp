package uz.sadora.server.user

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.core.now

/**
 * The second half of "delete my account".
 *
 * `DELETE /v1/users/me` marks the account and signs every device out at once, because
 * she should stop having access the moment she asks. This is what makes the promise
 * true afterwards: once the grace period has passed the row is deleted for real, and
 * the schema's cascades take her cycles, meals, journal, medications, posts, devices
 * and tokens with it.
 *
 * The grace period exists for one reason — a person who changes her mind, or asks
 * support to. It is not a soft delete kept "just in case": when the window closes the
 * data goes, and nothing in the product can bring it back.
 *
 * What survives is deliberately impersonal: the audit log keeps a line saying an account
 * was erased and when, and the AI cost log keeps the bill. Both hold the row with the
 * user reference set to null, so the history is answerable and the person is not in it.
 */
class AccountErasureJob(
    private val users: UserRepository,
    private val audit: AuditService,
    /** How long a request waits before it is carried out. */
    private val gracePeriod: Duration,
    private val tickInterval: Duration = 1.hours,
    /** Erased per tick, so one very large backlog cannot hold a connection all day. */
    private val batchSize: Int = 50,
) {
    private val logger = LoggerFactory.getLogger(AccountErasureJob::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        job = scope.launch {
            logger.info(
                "Account erasure job started, ticking every {} with a {} grace period",
                tickInterval,
                gracePeriod,
            )
            while (isActive) {
                // One failing tick must not stop the job: the next one picks up the same
                // rows, because nothing is marked as attempted.
                runCatching { runOnce() }
                    .onFailure { logger.error("Account erasure tick failed", it) }
                delay(tickInterval)
            }
        }
    }

    fun stop() {
        job?.cancel()
        scope.cancel()
    }

    /** Erases what is due and returns how many accounts went. Public so a test can tick it. */
    suspend fun runOnce(): Int {
        val due = users.findDueForErasure(now() - gracePeriod, batchSize)
        if (due.isEmpty()) return 0

        var erased = 0
        due.forEach { userId ->
            // The audit line is written first. If the delete then fails the log says an
            // erasure was attempted, which is recoverable; the other order can erase an
            // account and leave no record that it ever existed.
            audit.record(
                AuditEntry(
                    actorType = ActorType.SYSTEM,
                    action = AuditActions.USER_ERASED,
                    entityType = "user",
                    entityId = userId.toString(),
                    reason = "grace period elapsed",
                ),
            )
            if (users.erase(userId)) erased++
        }
        logger.info("Erased {} account(s) whose grace period had passed", erased)
        return erased
    }
}
