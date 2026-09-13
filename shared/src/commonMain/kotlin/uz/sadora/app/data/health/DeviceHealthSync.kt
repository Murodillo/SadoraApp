package uz.sadora.app.data.health

import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import uz.sadora.app.data.ApiFailure
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.api.CycleApi
import uz.sadora.app.data.api.WearableApi
import uz.sadora.contract.IngestResult
import uz.sadora.contract.IngestSamplesRequest
import uz.sadora.contract.LogPeriodRequest
import uz.sadora.contract.PeriodEntry

/** The three server calls a sync makes, so a test can stand in for the network. */
interface DeviceHealthBackend {
    suspend fun ingest(request: IngestSamplesRequest): ApiResult<IngestResult>
    suspend fun periods(): ApiResult<List<PeriodEntry>>
    suspend fun logPeriod(request: LogPeriodRequest): ApiResult<PeriodEntry>
}

class ApiDeviceHealthBackend(
    private val wearables: WearableApi,
    private val cycle: CycleApi,
) : DeviceHealthBackend {
    override suspend fun ingest(request: IngestSamplesRequest) = wearables.ingest(request)
    override suspend fun periods() = cycle.periods()
    override suspend fun logPeriod(request: LogPeriodRequest) = cycle.logPeriod(request)
}

/** What one sync did. [failure] set means nothing past the failed call happened. */
data class DeviceSyncOutcome(
    val accepted: Int = 0,
    val updated: Int = 0,
    val periodsAdded: Int = 0,
    val daysAffected: List<LocalDate> = emptyList(),
    val failure: ApiFailure? = null,
) {
    val changedMetrics: Boolean get() = accepted + updated > 0
}

/**
 * Reads the phone's health store and posts what it found.
 *
 * Runs whenever the app comes to the foreground; the server makes a repeat harmless
 * (a sample it already has is updated, not added), so the only reasons to hold back are
 * the battery and the data plan — hence [MinInterval] unless she asks.
 *
 * Each run re-reads from a little before the last one: a watch that syncs to the phone
 * hours late writes samples into a window that was already read.
 */
class DeviceHealthSync(
    val platform: HealthPlatform,
    private val prefs: HealthSyncPrefs,
    private val backend: DeviceHealthBackend?,
    private val clock: Clock = Clock.System,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    private val mutex = Mutex()

    /** Her state, or a fresh one when the stored state belongs to another account. */
    suspend fun state(userId: String): HealthSyncState =
        prefs.load().takeIf { it.userId == userId } ?: HealthSyncState(userId = userId)

    suspend fun enable(userId: String) = mutex.withLock {
        prefs.save(HealthSyncState(userId = userId, enabled = true, lastSyncAt = null))
    }

    /** Stops reading. What was sent stays on the server, as the disconnect dialog says. */
    suspend fun disable(userId: String) = mutex.withLock {
        prefs.save(HealthSyncState(userId = userId, enabled = false, lastSyncAt = null))
        platform.revokeAccess()
    }

    /** Null when nothing ran: switched off, too soon, or no access. */
    suspend fun sync(userId: String, force: Boolean = false): DeviceSyncOutcome? = mutex.withLock {
        val backend = backend ?: return null
        val saved = state(userId)
        if (!saved.enabled) return null
        val now = clock.now()
        val last = saved.lastSyncAt
        if (!force && last != null && now - last < MinInterval) return null
        if (platform.availability() != HealthAvailability.AVAILABLE || !platform.hasAccess()) return null

        val zone = zone()
        val window = window(last, now, zone)
        val reading = runCatching { platform.read(window.samplesFrom, window.flowFrom, now, zone) }
            .getOrElse { return DeviceSyncOutcome() }

        var accepted = 0
        var updated = 0
        val days = mutableSetOf<LocalDate>()
        reading.samples.chunked(BatchSize).forEach { batch ->
            when (val result = backend.ingest(IngestSamplesRequest(batch, zone.id))) {
                is ApiResult.Success -> {
                    accepted += result.value.accepted
                    updated += result.value.updated
                    days.addAll(result.value.daysAffected)
                }
                // The last sync time is not moved, so the next run reads this window again.
                is ApiResult.Failure -> return DeviceSyncOutcome(accepted, updated, failure = result.failure)
            }
        }

        val added = importPeriods(backend, reading.flowDays, now.toLocalDateTime(zone).date)
        prefs.save(saved.copy(lastSyncAt = now))
        DeviceSyncOutcome(accepted, updated, added, days.sorted())
    }

    private suspend fun importPeriods(backend: DeviceHealthBackend, flowDays: Set<LocalDate>, today: LocalDate): Int {
        if (flowDays.isEmpty()) return 0
        val known = backend.periods().valueOrNull?.toMutableList() ?: return 0
        var added = 0
        PeriodImport.missing(PeriodImport.spans(flowDays), known, today).forEach { span ->
            val ongoing = PeriodImport.isOngoing(span, known, today)
            val request = LogPeriodRequest(startedOn = span.start, endedOn = if (ongoing) null else span.endInclusive)
            backend.logPeriod(request).onSuccess { entry ->
                known += entry
                added++
            }
        }
        return added
    }

    internal data class Window(val samplesFrom: Instant, val flowFrom: Instant)

    internal companion object {
        val MinInterval = 15.minutes

        /** The first read, and the furthest back any read goes. */
        val SampleWindow = 30.days

        /** How far before the last sync a read starts again, for a watch that synced late. */
        val Overlap = 2.days

        /** A first import brings several cycles; later ones only what could still be changing. */
        val FirstFlowWindow = 180.days
        val FlowWindow = 45.days

        /** Under the server's 2,000-per-request cap, with room for a long month. */
        const val BatchSize = 1_000

        /**
         * Always from the start of a day: totals go up one row per day, and a read that
         * began at noon would replace the day's total with its afternoon.
         */
        fun window(last: Instant?, now: Instant, zone: TimeZone): Window {
            val earliest = now - SampleWindow
            val from = last?.let { maxOf(it - Overlap, earliest) } ?: earliest
            val samplesFrom = from.toLocalDateTime(zone).date.atStartOfDayIn(zone)
            val flowFrom = if (last == null) now - FirstFlowWindow else now - FlowWindow
            return Window(samplesFrom, flowFrom)
        }
    }
}
