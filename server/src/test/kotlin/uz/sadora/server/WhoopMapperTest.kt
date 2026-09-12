package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.MetricMapping
import uz.sadora.server.wearable.SampleNormalizer
import uz.sadora.server.wearable.whoop.WhoopCycle
import uz.sadora.server.wearable.whoop.WhoopCycleScore
import uz.sadora.server.wearable.whoop.WhoopMapper
import uz.sadora.server.wearable.whoop.WhoopRecovery
import uz.sadora.server.wearable.whoop.WhoopRecoveryScore
import uz.sadora.server.wearable.whoop.WhoopSleep
import uz.sadora.server.wearable.whoop.WhoopSleepScore
import uz.sadora.server.wearable.whoop.WhoopStageSummary

/**
 * WHOOP records become SADORA samples the way the mapping table expects them.
 *
 * The one convention worth a test: a night lands on the morning she woke. A sleep that
 * began at 23:00 on the 3rd and ended at 07:00 on the 4th is the 4th's sleep — which is
 * the day the app calls "last night" when she opens it that morning.
 */
class WhoopMapperTest {

    private val tashkent = TimeZone.of("Asia/Tashkent")

    @Test
    fun `a night lands on the morning she woke, in minutes asleep`() {
        val sleep = WhoopSleep(
            id = "8b6f",
            userId = 1,
            start = Instant.parse("2026-09-03T18:00:00Z"), // 23:00 Tashkent
            end = Instant.parse("2026-09-04T02:00:00Z"), // 07:00 Tashkent
            scoreState = "SCORED",
            score = WhoopSleepScore(
                stageSummary = WhoopStageSummary(
                    totalInBedTimeMilli = 8 * 60 * 60_000L,
                    totalAwakeTimeMilli = 30 * 60_000L,
                    totalSlowWaveSleepTimeMilli = 90 * 60_000L,
                    totalRemSleepTimeMilli = 100 * 60_000L,
                    totalLightSleepTimeMilli = 260 * 60_000L,
                ),
                respiratoryRate = 15.2,
                sleepPerformancePercentage = 88.0,
                sleepEfficiencyPercentage = 93.5,
            ),
        )
        val samples = WhoopMapper.fromSleep(sleep)
        val asleep = samples.first { it.metric == "asleep_milli" }
        assertEquals((7 * 60 + 30) * 60_000.0, asleep.value)
        assertEquals(4, asleep.startedAt.toLocalDateTime(tashkent).day, "the 4th, the morning she woke")

        // Through the same mapping the migration seeds: milliseconds become minutes.
        val mapping = MetricMapping(HealthProvider.WHOOP, "asleep_milli", HealthMetric.SLEEP_DURATION, "ms", 0.0000166666667)
        val normalized = SampleNormalizer.normalize(asleep, mapOf((HealthProvider.WHOOP to "asleep_milli") to mapping), tashkent)!!
        assertEquals(450.0, normalized.value, 0.01)
        assertEquals("2026-09-04", normalized.localDate.toString())
    }

    @Test
    fun `a nap is not the night, and a pending score is not a number`() {
        val base = WhoopSleep(
            id = "n1", userId = 1,
            start = Instant.parse("2026-09-04T09:00:00Z"), end = Instant.parse("2026-09-04T10:00:00Z"),
            scoreState = "SCORED", score = WhoopSleepScore(stageSummary = WhoopStageSummary(totalInBedTimeMilli = 3_600_000)),
        )
        assertTrue(WhoopMapper.fromSleep(base.copy(nap = true)).isEmpty())
        assertTrue(WhoopMapper.fromSleep(base.copy(scoreState = "PENDING_SCORE")).isEmpty())
        assertTrue(WhoopMapper.fromSleep(base.copy(scoreState = "UNSCORABLE", score = null)).isEmpty())
    }

    @Test
    fun `a recovery yields one sample per field it has, all on one external id`() {
        val recovery = WhoopRecovery(
            cycleId = 42, sleepId = "s", userId = 1,
            createdAt = Instant.parse("2026-09-04T02:10:00Z"), updatedAt = Instant.parse("2026-09-04T02:10:00Z"),
            scoreState = "SCORED",
            score = WhoopRecoveryScore(recoveryScore = 71.0, restingHeartRate = 56.0, hrvRmssdMilli = 64.5, spo2Percentage = null, skinTempCelsius = 33.9),
        )
        val samples = WhoopMapper.fromRecovery(recovery)
        assertEquals(setOf("recovery_score", "resting_heart_rate", "hrv_rmssd_milli", "skin_temp_celsius"), samples.map { it.metric }.toSet())
        assertTrue(samples.all { it.externalId.startsWith("recovery:42:") })
        assertTrue(samples.all { it.provider == HealthProvider.WHOOP })
    }

    @Test
    fun `an ongoing cycle still reports its strain so far, keyed to overwrite later`() {
        val cycle = WhoopCycle(
            id = 7, userId = 1, start = Instant.parse("2026-09-04T02:00:00Z"), end = null,
            scoreState = "SCORED", score = WhoopCycleScore(strain = 9.4, kilojoule = 5200.0, averageHeartRate = 68.0),
        )
        val samples = WhoopMapper.fromCycle(cycle)
        assertEquals(3, samples.size)
        assertEquals("cycle:7:strain", samples.first { it.metric == "strain" }.externalId)
    }
}
