package uz.sadora.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import uz.sadora.app.data.health.DeviceHealthBackend
import uz.sadora.app.data.health.DeviceHealthSync
import uz.sadora.app.data.health.HealthAvailability
import uz.sadora.app.data.health.HealthPlatform
import uz.sadora.app.data.health.HealthReading
import uz.sadora.app.data.health.HealthSyncState
import uz.sadora.app.data.health.InMemoryHealthSyncPrefs
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.HealthSampleInput
import uz.sadora.contract.IngestResult
import uz.sadora.contract.IngestSamplesRequest
import uz.sadora.contract.LogPeriodRequest
import uz.sadora.contract.PeriodEntry

class DeviceHealthSyncTest {

    private val zone = TimeZone.of("Asia/Tashkent")

    /** 14:00 in Tashkent. */
    private var now = Instant.parse("2026-09-13T09:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = this@DeviceHealthSyncTest.now
    }

    private class FakePlatform(
        var samples: List<HealthSampleInput> = emptyList(),
        var flowDays: Set<LocalDate> = emptySet(),
        var access: Boolean = true,
    ) : HealthPlatform {
        val reads = mutableListOf<Pair<Instant, Instant>>()
        var revoked = false
        override val provider = HealthProvider.HEALTH_CONNECT
        override suspend fun availability() = HealthAvailability.AVAILABLE
        override suspend fun hasAccess() = access
        override suspend fun read(samplesFrom: Instant, flowFrom: Instant, to: Instant, zone: TimeZone): HealthReading {
            reads += samplesFrom to flowFrom
            return HealthReading(samples, flowDays)
        }
        override suspend fun revokeAccess() {
            revoked = true
        }
        override fun openStore() = Unit
    }

    private class FakeBackend : DeviceHealthBackend {
        val batches = mutableListOf<IngestSamplesRequest>()
        val periods = mutableListOf<PeriodEntry>()
        var failure: ApiFailure? = null

        override suspend fun ingest(request: IngestSamplesRequest): ApiResult<IngestResult> {
            failure?.let { return ApiResult.Failure(it) }
            batches += request
            return ApiResult.Success(IngestResult(accepted = request.samples.size, updated = 0))
        }

        override suspend fun periods(): ApiResult<List<PeriodEntry>> = ApiResult.Success(periods.toList())

        override suspend fun logPeriod(request: LogPeriodRequest): ApiResult<PeriodEntry> {
            val entry = PeriodEntry("p${periods.size}", request.startedOn, request.endedOn, Instant.fromEpochMilliseconds(0))
            periods += entry
            return ApiResult.Success(entry)
        }
    }

    private val platform = FakePlatform()
    private val backend = FakeBackend()
    private val prefs = InMemoryHealthSyncPrefs(HealthSyncState(userId = "u1", enabled = true))
    private val sync = DeviceHealthSync(platform, prefs, backend, clock) { zone }

    private fun steps(count: Int) = (1..count).map {
        HealthSampleInput(HealthProvider.HEALTH_CONNECT, "id$it", "Steps", 1.0, startedAt = now)
    }

    @Test
    fun `nothing is read until she switches it on`() = runTest {
        prefs.state = HealthSyncState(userId = "u1", enabled = false)
        assertNull(sync.sync("u1"))
        assertTrue(platform.reads.isEmpty())
    }

    @Test
    fun `another account on the same phone starts switched off`() = runTest {
        assertNull(sync.sync("u2"))
        assertFalse(sync.state("u2").enabled)
    }

    @Test
    fun `without access nothing is read`() = runTest {
        platform.access = false
        assertNull(sync.sync("u1"))
        assertTrue(platform.reads.isEmpty())
    }

    @Test
    fun `the first sync reads a month from midnight and half a year of periods and then remembers`() = runTest {
        platform.samples = steps(3)
        platform.flowDays = (10..14).map { LocalDate(2026, 8, it) }.toSet()

        val outcome = assertNotNull(sync.sync("u1"))

        val (samplesFrom, flowFrom) = platform.reads.single()
        assertEquals(LocalDate(2026, 8, 14).atStartOfDayIn(zone), samplesFrom, "a whole day, so its total is not cut")
        assertEquals(now - 180.days, flowFrom)
        assertEquals("Asia/Tashkent", backend.batches.single().timezone)
        assertEquals(3, outcome.accepted)
        assertEquals(1, outcome.periodsAdded)
        assertEquals(LocalDate(2026, 8, 14), backend.periods.single().endedOn)
        assertEquals(now, prefs.state.lastSyncAt)
    }

    @Test
    fun `a sync within a quarter hour is skipped unless she asks`() = runTest {
        sync.sync("u1")
        now += 5.minutes
        assertNull(sync.sync("u1"))
        assertNotNull(sync.sync("u1", force = true))
        assertEquals(2, platform.reads.size)
    }

    @Test
    fun `the next sync starts two days before the last one`() = runTest {
        prefs.state = prefs.state.copy(lastSyncAt = now - 1.days)
        sync.sync("u1")
        assertEquals(LocalDate(2026, 9, 10).atStartOfDayIn(zone), platform.reads.single().first)
    }

    @Test
    fun `a failed upload keeps the window for next time`() = runTest {
        platform.samples = steps(2)
        backend.failure = ApiFailure.Network("offline")

        val outcome = assertNotNull(sync.sync("u1"))

        assertNotNull(outcome.failure)
        assertNull(prefs.state.lastSyncAt)
    }

    @Test
    fun `a month of samples goes up in batches the server takes`() = runTest {
        platform.samples = steps(2_500)
        sync.sync("u1")
        assertEquals(listOf(1_000, 1_000, 500), backend.batches.map { it.samples.size })
    }

    @Test
    fun `a period she already logged is not imported again`() = runTest {
        backend.periods += PeriodEntry("mine", LocalDate(2026, 8, 11), LocalDate(2026, 8, 15), Instant.fromEpochMilliseconds(0))
        platform.flowDays = (10..14).map { LocalDate(2026, 8, it) }.toSet()

        val outcome = assertNotNull(sync.sync("u1"))

        assertEquals(0, outcome.periodsAdded)
        assertEquals(listOf("mine"), backend.periods.map { it.id })
    }

    @Test
    fun `switching off gives the permissions back and forgets the last sync`() = runTest {
        sync.sync("u1")
        sync.disable("u1")
        assertTrue(platform.revoked)
        assertFalse(prefs.state.enabled)
        assertNull(prefs.state.lastSyncAt)
    }
}
