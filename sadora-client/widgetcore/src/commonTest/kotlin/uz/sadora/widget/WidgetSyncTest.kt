package uz.sadora.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import uz.sadora.contract.CyclePrediction
import uz.sadora.contract.CycleStatus
import uz.sadora.contract.DoseStatus
import uz.sadora.contract.Language
import uz.sadora.contract.LifeStage
import uz.sadora.contract.MedicationDay
import uz.sadora.contract.PredictionConfidence
import uz.sadora.contract.WaterState
import uz.sadora.contract.WidgetSummary

class WidgetSyncTest {
    private val day = LocalDate(2026, 10, 16)

    private fun summary(water: Int = 0) = WidgetSummary(
        today = day,
        lifeStage = LifeStage.CYCLE,
        cycle = CycleStatus(cycleDay = 3, prediction = CyclePrediction(PredictionConfidence.entries.first(), "t"), today = day),
        water = WaterState(day, water, 2000),
        medications = MedicationDay(day),
        premium = true,
        discreet = false,
        language = Language.UZ,
    )

    private class FakeApi(var server: WidgetSummary) : WidgetApi {
        var online = true
        var rejectNext = false
        val sent = mutableListOf<PendingAction>()
        val seenIds = mutableSetOf<String>()

        override suspend fun summary(): WidgetCall<WidgetSummary> =
            if (online) WidgetCall.Ok(server) else WidgetCall.Offline

        override suspend fun send(action: PendingAction): WidgetCall<Unit> {
            if (!online) return WidgetCall.Offline
            if (rejectNext) { rejectNext = false; return WidgetCall.Rejected }
            sent += action
            if (action is PendingAction.Water && seenIds.add(action.clientId)) {
                server = server.copy(water = server.water.copy(waterMl = server.water.waterMl + action.ml))
            }
            return WidgetCall.Ok(Unit)
        }
    }

    private class Clock(var at: Instant = Instant.fromEpochSeconds(1_800_000_000)) : () -> Instant {
        override fun invoke() = at
    }

    private fun store() = WidgetStore(MemoryKeyValues())

    @Test
    fun `a refresh saves what the server says`() = runTest {
        val store = store()
        val sync = WidgetSync(FakeApi(summary(water = 500)), store, Clock(), ids = { "id" })
        assertEquals(WidgetSync.Outcome.Updated, sync.refresh())
        assertEquals(500, store.snapshot()?.summary?.water?.waterMl)
    }

    @Test
    fun `a water tap counts at once and is sent once online`() = runTest {
        val store = store()
        val api = FakeApi(summary())
        val sync = WidgetSync(api, store, Clock(), ids = { "tap-1" })
        sync.refresh()

        api.online = false
        sync.addWater(250, day)
        val queued = store.snapshot()!!
        assertEquals(1, queued.pending.size, "kept for later")
        assertEquals(0, queued.summary.water.waterMl, "the summary is the server's; the queue is drawn on top")

        api.online = true
        assertEquals(WidgetSync.Outcome.Updated, sync.refresh())
        val after = store.snapshot()!!
        assertTrue(after.pending.isEmpty())
        assertEquals(250, after.summary.water.waterMl)
        assertEquals(1, api.sent.size)
    }

    @Test
    fun `offline leaves the queue and the old snapshot alone`() = runTest {
        val store = store()
        val api = FakeApi(summary(water = 750))
        val sync = WidgetSync(api, store, Clock(), ids = { "t" })
        sync.refresh()
        api.online = false
        sync.markDose("m1", day, LocalTime(8, 0), DoseStatus.TAKEN)
        assertEquals(WidgetSync.Outcome.Offline, sync.refresh())
        assertEquals(750, store.snapshot()!!.summary.water.waterMl)
        assertEquals(1, store.snapshot()!!.pending.size)
    }

    @Test
    fun `a tap the server refuses is dropped — not retried forever`() = runTest {
        val store = store()
        val api = FakeApi(summary())
        val sync = WidgetSync(api, store, Clock(), ids = { "t" })
        sync.refresh()
        api.rejectNext = true
        sync.markDose("gone", day, LocalTime(8, 0), DoseStatus.TAKEN)
        sync.refresh()
        assertTrue(store.snapshot()!!.pending.isEmpty())
    }

    @Test
    fun `marking the same dose twice keeps only the last answer`() = runTest {
        val store = store()
        val api = FakeApi(summary())
        val sync = WidgetSync(api, store, Clock(), ids = { "t" })
        sync.refresh()
        api.online = false
        sync.markDose("m1", day, LocalTime(8, 0), DoseStatus.TAKEN)
        sync.markDose("m1", day, LocalTime(8, 0), DoseStatus.SKIPPED)
        val pending = store.snapshot()!!.pending.single() as PendingAction.Dose
        assertEquals(DoseStatus.SKIPPED, pending.status)
    }

    @Test
    fun `signing out forgets her data and keeps how the widgets are set up`() = runTest {
        val store = store()
        WidgetSync(FakeApi(summary()), store, Clock(), ids = { "t" }).refresh()
        val config = WidgetConfig(WidgetSize.MEDIUM, listOf(WidgetBlock.WATER))
        store.putConfig("42", config)
        store.clear()
        assertNull(store.snapshot())
        assertEquals(config, store.config("42"), "a widget's blocks are not hers to hide")
    }

    @Test
    fun `a widget's settings are kept by its id`() {
        val store = store()
        val config = WidgetConfig(WidgetSize.LARGE, listOf(WidgetBlock.DOSES), waterStepMl = 330)
        store.putConfig("7", config)
        assertEquals(config, store.config("7"))
        store.removeConfig("7")
        assertNull(store.config("7"))
    }
}
