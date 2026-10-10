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
        prefs.save(HealthSyncState(userId = userId, enabled = true, lastSyncAt = null, gatePassed = state(userId).gatePassed))
    }

    /** She answered the gate before the app; it is not asked again on this phone for her. */
    suspend fun passGate(userId: String) = mutex.withLock {
        prefs.save(state(userId).copy(gatePassed = true))
    }

    /** Stops reading. What was sent stays on the server, as the disconnect dialog says. */
    suspend fun disable(userId: String) = mutex.withLock {
        prefs.save(HealthSyncState(userId = userId, enabled = false, lastSyncAt = null, gatePassed = state(userId).gatePassed))
        platform.revokeAccess()
    }

    /**
     * Null when nothing ran: switched off, too soon, or no access.
     *
     * The first run after she switches it on brings her whole history, a year at a time
     * and oldest first, each year posted before the next is read — ten years of a watch
     * held in memory at once would be the phone's whole heap. [progress] hears the share
     * of years done. Later runs read only from a little before the last one.
     */
    suspend fun sync(
        userId: String,
        force: Boolean = false,
        progress: (Float) -> Unit = {},
    ): DeviceSyncOutcome? = mutex.withLock {
        val backend = backend ?: return null
        val saved = state(userId)
        if (!saved.enabled) return null
        val now = clock.now()
        val last = saved.lastSyncAt
        if (!force && last != null && now - last < MinInterval) return null
        if (platform.availability() != HealthAvailability.AVAILABLE || !platform.hasAccess()) return null

        val zone = zone()
        val spans = spans(last, now, zone)
        var accepted = 0
        var updated = 0
        var added = 0
        val days = mutableSetOf<LocalDate>()
        spans.forEachIndexed { index, span ->
            val reading = runCatching { platform.read(span.samplesFrom, span.flowFrom, span.to, zone) }
                .getOrElse { return DeviceSyncOutcome(accepted, updated, added, days.sorted()) }
            reading.samples.chunked(BatchSize).forEach { batch ->
                when (val result = backend.ingest(IngestSamplesRequest(batch, zone.id))) {
                    is ApiResult.Success -> {
                        accepted += result.value.accepted
                        updated += result.value.updated
                        days.addAll(result.value.daysAffected)
                    }
                    // The last sync time is not moved, so the next run reads this window again.
                    is ApiResult.Failure -> return DeviceSyncOutcome(accepted, updated, added, days.sorted(), result.failure)
                }
            }
            added += importPeriods(backend, reading.flowDays, now.toLocalDateTime(zone).date)
            progress((index + 1f) / spans.size)
        }
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

    internal data class Window(val samplesFrom: Instant, val flowFrom: Instant, val to: Instant)

    internal companion object {
        val MinInterval = 15.minutes

        /**
         * How far the first read looks back: all of it, in practice. Health Connect keeps
         * only what apps wrote into it, and hands over what is older than 30 days before
         * the grant only with the history permission, which the sheet asks for.
         */
        val HistoryWindow = (10 * 365).days

        /** Later reads go no further back than this. */
        val SampleWindow = 30.days

        /** How far before the last sync a read starts again, for a watch that synced late. */
        val Overlap = 2.days

        /** Later reads of the cycle look back only as far as could still be changing. */
        val FlowWindow = 45.days

        /** The first read's slices: a year each. */
        val Slice = 365.days

        /** Under the server's 2,000-per-request cap, with room for a long month. */
        const val BatchSize = 1_000

        /**
         * Always from the start of a day: totals go up one row per day, and a read that
         * began at noon would replace the day's total with its afternoon.
         */
        fun window(last: Instant?, now: Instant, zone: TimeZone): Window {
            val earliest = now - SampleWindow
            val from = last?.let { maxOf(it - Overlap, earliest) } ?: (now - HistoryWindow)
            val samplesFrom = from.toLocalDateTime(zone).date.atStartOfDayIn(zone)
            val flowFrom = if (last == null) now - HistoryWindow else now - FlowWindow
            return Window(samplesFrom, flowFrom, now)
        }

        /**
         * The first read cut into years, oldest first; any later read is one window.
         *
         * The bleeding days are read once, over the whole history, in the last slice: read
         * a year at a time, a period across New Year would be imported as two.
         */
        fun spans(last: Instant?, now: Instant, zone: TimeZone): List<Window> {
            val whole = window(last, now, zone)
            if (last != null) return listOf(whole)
            return buildList {
                var start = whole.samplesFrom
                while (start < now) {
                    val end = minOf(start + Slice, now)
                    // An empty flow range for every slice but the last.
                    add(Window(start, if (end < now) end else whole.flowFrom, end))
                    start = end
                }
            }
        }
    }
}
