package uz.sadora.server.ops

import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory
import uz.sadora.server.auth.OtpSender
import uz.sadora.server.core.DEFAULT_TIMEZONE
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.dbQuery

/** V67: a service's good and bad outcomes, per Tashkent day. */
object OpsCounters : Table("ops_counters") {
    val service = text("service")
    val day = date("day")
    val ok = integer("ok")
    val failed = integer("failed")
    val lastOkAt = timestampWithTimeZone("last_ok_at").nullable()
    val lastErrorAt = timestampWithTimeZone("last_error_at").nullable()
    val lastError = text("last_error").nullable()

    override val primaryKey = PrimaryKey(service, day)
}

/** The services whose outcomes only this recorder knows. */
object OpsServices {
    const val SMS = "sms"
    const val TELEGRAM = "telegram"
}

/**
 * Counts what the health page cannot read off the product's own rows. A write that fails
 * is logged and dropped: the count is for the operator, and a failure to keep it must never
 * turn into a failed sign-in.
 */
class OpsRecorder {
    private val logger = LoggerFactory.getLogger(OpsRecorder::class.java)
    private val lastAlive = ConcurrentHashMap<String, Instant>()

    suspend fun succeeded(service: String) = write(service) { at ->
        upsertRow(service, at, ok = 1, failed = 0, error = null)
    }

    suspend fun failed(service: String, error: String) = write(service) { at ->
        upsertRow(service, at, ok = 0, failed = 1, error = error.take(300))
    }

    /**
     * "Still working" for a loop that succeeds every few seconds, like the bot's long poll:
     * stamps the time at most once a minute and counts nothing, so the row says when it last
     * worked rather than how many polls a day took.
     */
    suspend fun alive(service: String) {
        val at = now()
        val previous = lastAlive[service]
        if (previous != null && at - previous < ALIVE_EVERY) return
        lastAlive[service] = at
        write(service) { upsertRow(service, at, ok = 0, failed = 0, error = null) }
    }

    private suspend fun write(service: String, block: (Instant) -> Unit) {
        runCatching { dbQuery { block(now()) } }
            .onFailure { logger.warn("Could not record {} outcome", service, it) }
    }

    private fun upsertRow(service: String, at: Instant, ok: Int, failed: Int, error: String?) {
        val stamp = at.toOffsetDateTime()
        val okStamp = if (failed == 0) stamp else null
        OpsCounters.upsert(
            OpsCounters.service,
            OpsCounters.day,
            onUpdate = {
                if (ok > 0) it[OpsCounters.ok] = OpsCounters.ok + ok
                if (failed > 0) {
                    it[OpsCounters.failed] = OpsCounters.failed + failed
                    it[OpsCounters.lastErrorAt] = stamp
                    it[OpsCounters.lastError] = error
                } else {
                    it[OpsCounters.lastOkAt] = stamp
                }
            },
        ) {
            it[OpsCounters.service] = service
            it[day] = at.dayIn(DEFAULT_TIMEZONE)
            it[OpsCounters.ok] = ok
            it[OpsCounters.failed] = failed
            it[lastOkAt] = okStamp
            it[lastErrorAt] = if (failed > 0) stamp else null
            it[lastError] = error
        }
    }

    private companion object {
        val ALIVE_EVERY = 1.minutes
    }
}

/** The SMS gateway with its outcomes counted: one per code handed over, or refused. */
class MonitoredOtpSender(private val inner: OtpSender, private val ops: OpsRecorder) : OtpSender {
    override suspend fun send(phone: String, code: String) {
        try {
            inner.send(phone, code)
        } catch (failure: Exception) {
            ops.failed(OpsServices.SMS, failure.message ?: failure::class.simpleName.orEmpty())
            throw failure
        }
        ops.succeeded(OpsServices.SMS)
    }
}
