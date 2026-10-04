package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import uz.sadora.contract.DoctorSummary
import uz.sadora.contract.HotFlushTrigger
import uz.sadora.contract.Language
import uz.sadora.contract.LifeStage
import uz.sadora.contract.SharedPerson
import uz.sadora.contract.StageEvent
import uz.sadora.contract.StageEventKind
import uz.sadora.server.share.DoctorPage
import uz.sadora.server.share.StageRecordSummary

/**
 * The doctor reads the stage tools as a handful of numbers, each over the window its
 * question needs — and the two results that call for action are the ones she must see.
 */
class StageRecordSummaryTest {

    private val now = Instant.parse("2026-10-04T08:00:00Z")

    private fun event(kind: StageEventKind, ago: Duration, seconds: Int? = null, value: Int? = null, detail: String? = null) =
        StageEvent("e${ago.inWholeMinutes}$kind", kind, now - ago, seconds, value, detail, now - ago)

    @Test
    fun `nothing recorded is no section at all`() {
        assertNull(StageRecordSummary.of(emptyList(), now, "Asia/Tashkent"))
        // Only outside every window: still nothing.
        assertNull(StageRecordSummary.of(listOf(event(StageEventKind.FEEDING, 30.days, 600, detail = "left")), now, "Asia/Tashkent"))
    }

    @Test
    fun `feeds, contractions and hot flushes are counted over their own windows`() {
        val records = assertNotNull(
            StageRecordSummary.of(
                listOf(
                    event(StageEventKind.FEEDING, 1.hours, 600, detail = "left"),
                    event(StageEventKind.FEEDING, 4.hours, 1200, detail = "right"),
                    event(StageEventKind.FEEDING, 6.hours, value = 90, detail = "bottle"),
                    event(StageEventKind.FEEDING, 10.days, 600, detail = "left"),
                    event(StageEventKind.CONTRACTION, 10.minutes, 60),
                    event(StageEventKind.CONTRACTION, 15.minutes, 50),
                    event(StageEventKind.CONTRACTION, 20.minutes, 70),
                    event(StageEventKind.CONTRACTION, 2.days, 70),
                    event(StageEventKind.HOT_FLUSH, 1.days, value = 3, detail = "spicy_food"),
                    event(StageEventKind.HOT_FLUSH, 2.days, value = 1, detail = "spicy_food"),
                    event(StageEventKind.HOT_FLUSH, 3.days, value = 2, detail = "stress"),
                ),
                now,
                "Asia/Tashkent",
            ),
        )
        val feeding = assertNotNull(records.feeding)
        assertEquals(3, feeding.feeds)
        assertEquals(2, feeding.breastFeeds)
        assertEquals(15, feeding.averageBreastMinutes)
        assertEquals(90, feeding.bottleMl)

        val contractions = assertNotNull(records.contractions)
        assertEquals(3, contractions.count)
        assertEquals(60, contractions.averageDurationSeconds)
        assertEquals(300, contractions.averageIntervalSeconds)

        val flushes = assertNotNull(records.hotFlushes)
        assertEquals(3, flushes.count)
        assertEquals(1, flushes.strong)
        assertEquals(HotFlushTrigger.SPICY_FOOD, flushes.triggers.first().trigger)
    }

    @Test
    fun `a slow kick count and a self-harm answer are what the page warns about`() {
        val records = assertNotNull(
            StageRecordSummary.of(
                listOf(
                    event(StageEventKind.KICK_COUNT, 1.days, 1_800, value = 10),
                    event(StageEventKind.KICK_COUNT, 2.days, 9_000, value = 7),
                    event(StageEventKind.MOOD_SCREEN, 3.days, value = 17, detail = "1,1,1,1,1,1,1,1,1,1"),
                ),
                now,
                "Asia/Tashkent",
            ),
        )
        assertEquals(listOf(false, true), records.kickCounts.map { it.isSlow })
        val screen = records.moodScreens.single()
        assertTrue(screen.selfHarm)
        assertEquals(LocalDate(2026, 10, 1), screen.takenOn)

        val summary = DoctorSummary(
            generatedAt = now,
            language = Language.UZ,
            person = SharedPerson("Nilufar", lifeStage = LifeStage.POSTPARTUM, memberSince = LocalDate(2026, 9, 1)),
            stageRecords = records,
        )
        val ru = DoctorPage.render(summary, Language.RU)
        assertContains(ru, "Подсчёт шевелений")
        assertContains(ru, "Требуется срочная оценка")
        assertContains(ru, "17 / 30")
        assertFalse("Кормление" in ru, "no feeds, no feeding section")
    }
}
