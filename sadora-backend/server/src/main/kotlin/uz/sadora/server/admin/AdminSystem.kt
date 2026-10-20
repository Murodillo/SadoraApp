package uz.sadora.server.admin

import kotlin.time.Instant
import kotlin.time.TimeSource
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import uz.sadora.contract.NotificationStatus
import uz.sadora.server.api.SERVER_RELEASE
import uz.sadora.server.config.AppConfig
import uz.sadora.server.core.DEFAULT_TIMEZONE
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.core.resolveTimeZone
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.AiUsageLog
import uz.sadora.server.db.NotificationOutbox
import uz.sadora.server.db.PaymentTransactions
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.notify.TARGET_CLIENT
import uz.sadora.server.notify.TARGET_DOCTOR
import uz.sadora.server.ops.OpsCounters
import uz.sadora.server.ops.OpsServices

@Serializable
data class SystemHealth(
    val environment: String,
    val version: String,
    /** The commit the running image was built from; "local" on a laptop. */
    val release: String,
    val startedAt: Instant,
    val database: DatabaseHealth,
    val services: List<ServiceHealth>,
    val generatedAt: Instant,
    /** The disk message photos and videos live on, and their backup. */
    val media: MediaHealth? = null,
)

/**
 * [status] is `ok`, `warn` from [uz.sadora.server.media.MediaService.DISK_WARN] of the disk,
 * and `full` from the pause share (95% on a server), where new uploads wait; with the
 * guard off (a laptop) it never says `full`.
 * Without [keyConfigured] no upload is taken at all. [lastBackupAt] is written by the
 * nightly backup; null means it has never reported.
 */
@Serializable
data class MediaHealth(
    val keyConfigured: Boolean,
    val status: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val mediaBytes: Long,
    val usedPercent: Int,
    val lastBackupAt: Instant? = null,
)

@Serializable
data class DatabaseHealth(val ok: Boolean, val latencyMs: Long? = null, val schemaVersion: String? = null)

/**
 * One outside service today. [status] is `off` when its keys are not set, `error` when its
 * latest attempt failed, `warn` when something failed today but it has worked since, and
 * `ok` otherwise. A count is null where the service cannot be counted, never a made-up 0.
 */
@Serializable
data class ServiceHealth(
    val key: String,
    val configured: Boolean,
    val status: String,
    val okToday: Long? = null,
    val failedToday: Long? = null,
    val lastOkAt: Instant? = null,
    val lastErrorAt: Instant? = null,
    val lastError: String? = null,
)

/**
 * The Owner's "is anything broken" page: every outside service the product pays for or
 * depends on, read from the rows the product writes anyway (the push outbox, the AI log,
 * the payments) and, where there are none, from the ops counters.
 */
class SystemHealthService(
    private val config: AppConfig,
    private val version: String,
    private val startedAt: Instant = now(),
    /** The media store's facts; null in tests without it. */
    private val media: (suspend () -> uz.sadora.server.media.MediaHealthFacts)? = null,
) {

    suspend fun health(): SystemHealth {
        // "Today" is the Tashkent day, as everywhere else in the panel.
        val today = now().dayIn(DEFAULT_TIMEZONE)
        val since = today.atStartOfDayIn(resolveTimeZone(DEFAULT_TIMEZONE))
        return SystemHealth(
            environment = config.environment.name.lowercase(),
            version = version,
            release = SERVER_RELEASE,
            startedAt = startedAt,
            database = database(),
            services = listOf(
                counted(OpsServices.SMS, config.otp.playMobile.isConfigured && config.otp.fixedCode == null, today),
                push(since),
                ai(since),
                counted(OpsServices.TELEGRAM, config.telegram.isConfigured, today),
                payments("payme", config.billing.payme.isConfigured, since),
                payments("click", config.billing.click.isConfigured, since),
                payments("google_play", config.billing.googlePlay.isConfigured, since),
                // App Store receipts are checked against Apple's public endpoint; nothing to configure.
                payments("app_store", true, since),
            ),
            generatedAt = now(),
            media = media?.let { facts -> runCatching { mediaHealth(facts()) }.getOrNull() },
        )
    }

    private fun mediaHealth(facts: uz.sadora.server.media.MediaHealthFacts): MediaHealth {
        val used = facts.disk.usedFraction
        return MediaHealth(
            keyConfigured = facts.keyConfigured,
            status = when {
                // "full" means uploads are waiting, which only a guard that is on can make true.
                facts.pauseAt < 1.0 && used >= facts.pauseAt -> "full"
                used >= uz.sadora.server.media.MediaService.DISK_WARN -> "warn"
                else -> "ok"
            },
            totalBytes = facts.disk.totalBytes,
            freeBytes = facts.disk.freeBytes,
            mediaBytes = facts.disk.mediaBytes,
            usedPercent = (used * 100).toInt(),
            lastBackupAt = facts.lastBackupAt,
        )
    }

    private suspend fun database(): DatabaseHealth = runCatching {
        val started = TimeSource.Monotonic.markNow()
        val schema = dbQuery {
            TransactionManager.current().exec(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
            ) { rows -> if (rows.next()) rows.getString(1) else null }
        }
        DatabaseHealth(ok = true, latencyMs = started.elapsedNow().inWholeMilliseconds, schemaVersion = schema)
    }.getOrElse { DatabaseHealth(ok = false) }

    private suspend fun counted(key: String, configured: Boolean, today: kotlinx.datetime.LocalDate): ServiceHealth = dbQuery {
        val row = OpsCounters.selectAll()
            .where { (OpsCounters.service eq key) and (OpsCounters.day eq today) }
            .singleOrNull()
        // The last good moment may be from an earlier day; look past today for it.
        val lastOk = OpsCounters.select(OpsCounters.lastOkAt.max())
            .where { OpsCounters.service eq key }
            .singleOrNull()?.get(OpsCounters.lastOkAt.max())?.toKotlinInstant()
        service(
            key = key,
            configured = configured,
            okToday = (row?.get(OpsCounters.ok) ?: 0).toLong(),
            failedToday = (row?.get(OpsCounters.failed) ?: 0).toLong(),
            lastOkAt = lastOk,
            lastErrorAt = row?.get(OpsCounters.lastErrorAt)?.toKotlinInstant(),
            lastError = row?.get(OpsCounters.lastError),
        )
    }

    /** Phone pushes only; the bot's own messages are the Telegram row's business. */
    private suspend fun push(since: Instant): ServiceHealth = dbQuery {
        val phones = listOf(TARGET_CLIENT, TARGET_DOCTOR)
        val fromStart = since.toOffsetDateTime()
        val sent = NotificationOutbox.selectAll()
            .where {
                (NotificationOutbox.targetApp inList phones) and
                    (NotificationOutbox.status eq NotificationStatus.SENT.dbValue()) and
                    (NotificationOutbox.sentAt greaterEq fromStart)
            }
            .count()
        val failed = NotificationOutbox.selectAll()
            .where {
                (NotificationOutbox.targetApp inList phones) and
                    (NotificationOutbox.status eq NotificationStatus.FAILED.dbValue()) and
                    (NotificationOutbox.createdAt greaterEq fromStart)
            }
        val lastFailure = failed.copy()
            .orderBy(NotificationOutbox.createdAt, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
        val lastSent = NotificationOutbox.select(NotificationOutbox.sentAt.max())
            .where { NotificationOutbox.targetApp inList phones }
            .singleOrNull()?.get(NotificationOutbox.sentAt.max())?.toKotlinInstant()
        service(
            key = "push",
            configured = config.push.isConfigured,
            okToday = sent,
            failedToday = failed.count(),
            lastOkAt = lastSent,
            lastErrorAt = lastFailure?.get(NotificationOutbox.createdAt)?.toKotlinInstant(),
            lastError = lastFailure?.get(NotificationOutbox.suppressedReason),
        )
    }

    /** The model's answers: an answer the rules gave because the model failed is an error. */
    private suspend fun ai(since: Instant): ServiceHealth = dbQuery {
        val fromStart = since.toOffsetDateTime()
        val answered = AiUsageLog.selectAll()
            .where { (AiUsageLog.answerSource eq "model") and (AiUsageLog.outcome eq "ok") and (AiUsageLog.createdAt greaterEq fromStart) }
            .count()
        val failures = AiUsageLog.selectAll()
            .where { (AiUsageLog.outcome eq "error") and (AiUsageLog.createdAt greaterEq fromStart) }
        val lastFailure = failures.copy()
            .orderBy(AiUsageLog.createdAt, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
        val lastAnswer = AiUsageLog.select(AiUsageLog.createdAt.max())
            .where { (AiUsageLog.answerSource eq "model") and (AiUsageLog.outcome eq "ok") }
            .singleOrNull()?.get(AiUsageLog.createdAt.max())?.toKotlinInstant()
        service(
            key = "ai",
            configured = config.ai.apiKey != null,
            okToday = answered,
            failedToday = failures.count(),
            lastOkAt = lastAnswer,
            lastErrorAt = lastFailure?.get(AiUsageLog.createdAt)?.toKotlinInstant(),
            lastError = lastFailure?.get(AiUsageLog.errorCode),
        )
    }

    /**
     * Money taken today and the last time any came in. A cancelled checkout is someone who
     * changed her mind, not a fault, so payments are never counted as failures here.
     */
    private suspend fun payments(provider: String, configured: Boolean, since: Instant): ServiceHealth = dbQuery {
        val paidToday = PaymentTransactions.selectAll()
            .where {
                (PaymentTransactions.provider eq provider) and
                    (PaymentTransactions.state eq "paid") and
                    (PaymentTransactions.paidAt greaterEq since.toOffsetDateTime())
            }
            .count()
        val lastPaid = PaymentTransactions.select(PaymentTransactions.paidAt.max())
            .where { PaymentTransactions.provider eq provider }
            .singleOrNull()?.get(PaymentTransactions.paidAt.max())?.toKotlinInstant()
        service(provider, configured, okToday = paidToday, failedToday = null, lastOkAt = lastPaid)
    }

    internal companion object {
        fun service(
            key: String,
            configured: Boolean,
            okToday: Long?,
            failedToday: Long?,
            lastOkAt: Instant?,
            lastErrorAt: Instant? = null,
            lastError: String? = null,
        ) = ServiceHealth(
            key = key,
            configured = configured,
            status = statusOf(configured, failedToday ?: 0, lastOkAt, lastErrorAt),
            okToday = okToday,
            failedToday = failedToday,
            lastOkAt = lastOkAt,
            lastErrorAt = lastErrorAt,
            lastError = lastError,
        )

        fun statusOf(configured: Boolean, failedToday: Long, lastOkAt: Instant?, lastErrorAt: Instant?): String = when {
            !configured -> "off"
            failedToday > 0 && lastErrorAt != null && (lastOkAt == null || lastErrorAt > lastOkAt) -> "error"
            failedToday > 0 -> "warn"
            else -> "ok"
        }
    }
}
