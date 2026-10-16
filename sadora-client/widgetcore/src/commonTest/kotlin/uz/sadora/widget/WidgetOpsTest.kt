package uz.sadora.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import uz.sadora.contract.Language
import uz.sadora.contract.LifeStage
import uz.sadora.contract.MedicationDay
import uz.sadora.contract.WaterState
import uz.sadora.contract.WidgetSummary

class WidgetOpsTest {
    private val day = LocalDate(2026, 10, 16)
    private val summary = WidgetSummary(day, LifeStage.CYCLE, water = WaterState(day, 500, 2000), medications = MedicationDay(day), premium = true, discreet = false, language = Language.UZ)

    private fun ops(store: WidgetStore, tokens: CompanionTokens) =
        WidgetOps(store, tokens, WidgetSync(object : WidgetApi {
            override suspend fun summary() = WidgetCall.Offline
            override suspend fun send(action: PendingAction) = WidgetCall.Offline
        }, store, { Instant.fromEpochSeconds(0) }, { "id" }), { day })

    @Test
    fun `the phone's picture arrives with a session, and the watch's own taps stay queued`() {
        val store = WidgetStore(MemoryKeyValues())
        val tokens = CompanionTokens(MemoryKeyValues())
        store.saveSnapshot(WidgetSnapshot(summary, Instant.fromEpochSeconds(0), listOf(PendingAction.Water(250, "w1", day))))
        val snapshot = WidgetStore.json.encodeToString(WidgetSnapshot.serializer(), WidgetSnapshot(summary.copy(water = WaterState(day, 750, 2000)), Instant.fromEpochSeconds(10)))
        val pair = """{"accessToken":"a","refreshToken":"r","accessExpiresAt":"2030-01-01T00:00:00Z","refreshExpiresAt":"2030-01-01T00:00:00Z"}"""

        ops(store, tokens).importFromPhone(pair, snapshot, "https://api.test")

        assertEquals("r", tokens.refresh())
        assertEquals("https://api.test", store.baseUrl())
        assertEquals(750, store.snapshot()?.summary?.water?.waterMl)
        assertEquals(1, store.snapshot()?.pending?.size)
    }

    @Test
    fun `the phone signing out empties the watch`() {
        val store = WidgetStore(MemoryKeyValues())
        val tokens = CompanionTokens(MemoryKeyValues()).apply { save("a", "r") }
        store.saveSnapshot(WidgetSnapshot(summary, Instant.fromEpochSeconds(0)))
        ops(store, tokens).importFromPhone(null, null, "")
        assertNull(store.snapshot())
        assertNull(tokens.refresh())
    }
}
