package uz.sadora.server

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.HealthSampleInput
import uz.sadora.contract.MetricMapping
import uz.sadora.server.wearable.NormalizedSample
import uz.sadora.server.wearable.SampleNormalizer

class SampleNormalizerTest {

    private val tashkent = TimeZone.of("Asia/Tashkent")

    private val mappings = listOf(
        MetricMapping(HealthProvider.APPLE_HEALTH, "HKQuantityTypeIdentifierStepCount", HealthMetric.STEPS, "count", 1.0),
        MetricMapping(HealthProvider.HEALTH_CONNECT, "ActiveCaloriesBurned", HealthMetric.ACTIVE_ENERGY, "kJ", 0.239005736),
        MetricMapping(HealthProvider.HEALTH_CONNECT, "SleepSession", HealthMetric.SLEEP_DURATION, "s", 1.0 / 60),
        MetricMapping(HealthProvider.HEALTH_CONNECT, "Steps", HealthMetric.STEPS, "count", 1.0),
        MetricMapping(HealthProvider.OURA, "steps", HealthMetric.STEPS, "count", 1.0, active = false),
    ).associateBy { it.provider to it.providerMetric }

    private fun input(
        provider: HealthProvider = HealthProvider.APPLE_HEALTH,
        metric: String = "HKQuantityTypeIdentifierStepCount",
        value: Double = 1000.0,
        startedAt: String = "2026-09-02T09:00:00Z",
        externalId: String = "sample-1",
    ) = HealthSampleInput(
        provider = provider,
        externalId = externalId,
        metric = metric,
        value = value,
        startedAt = Instant.parse(startedAt),
    )

    private fun sample(
        provider: HealthProvider,
        value: Double,
        metric: HealthMetric = HealthMetric.STEPS,
        startedAt: String = "2026-09-02T09:00:00Z",
    ) = NormalizedSample(
        provider = provider,
        externalId = "x",
        metric = metric,
        value = value,
        unit = metric.canonicalUnit,
        startedAt = Instant.parse(startedAt),
        endedAt = null,
        localDate = LocalDate.parse("2026-09-02"),
        sourceDevice = null,
    )

    // ---------------------------------------------------------------- mapping

    @Test
    fun `a provider metric name becomes a canonical one`() {
        val result = SampleNormalizer.normalize(input(), mappings, tashkent)!!
        assertEquals(HealthMetric.STEPS, result.metric)
        assertEquals("count", result.unit)
        assertEquals(1000.0, result.value)
    }

    @Test
    fun `kilojoules are converted to kilocalories`() {
        val result = SampleNormalizer.normalize(
            input(HealthProvider.HEALTH_CONNECT, "ActiveCaloriesBurned", value = 1000.0),
            mappings,
            tashkent,
        )!!
        assertEquals(HealthMetric.ACTIVE_ENERGY, result.metric)
        assertEquals("kcal", result.unit)
        assertTrue(abs(result.value - 239.0) < 0.5, "1000 kJ should be about 239 kcal, got ${result.value}")
    }

    @Test
    fun `sleep seconds become minutes`() {
        val result = SampleNormalizer.normalize(
            input(HealthProvider.HEALTH_CONNECT, "SleepSession", value = 27_000.0),
            mappings,
            tashkent,
        )!!
        assertEquals(450.0, result.value, "27000 s is 7h30m")
        assertEquals("min", result.unit)
    }

    /** Reported back rather than guessed at, so an operator knows to add a row. */
    @Test
    fun `an unmapped metric is refused rather than invented`() {
        assertNull(SampleNormalizer.normalize(input(metric = "HKSomethingNew"), mappings, tashkent))
    }

    @Test
    fun `a deactivated mapping stops accepting samples`() {
        assertNull(
            SampleNormalizer.normalize(input(HealthProvider.OURA, "steps"), mappings, tashkent),
        )
    }

    @Test
    fun `a non-finite value is refused`() {
        assertNull(SampleNormalizer.normalize(input(value = Double.NaN), mappings, tashkent))
    }

    /**
     * Tashkent is UTC+5, so a sample at 20:30 UTC belongs to the next local day. Deciding
     * this once at ingest is what stops the same sample landing on different days
     * depending on who runs the query.
     */
    @Test
    fun `the local day is decided by the device timezone`() {
        val late = SampleNormalizer.normalize(
            input(startedAt = "2026-09-02T20:30:00Z"),
            mappings,
            tashkent,
        )!!
        assertEquals(LocalDate.parse("2026-09-03"), late.localDate)

        val utc = SampleNormalizer.normalize(
            input(startedAt = "2026-09-02T20:30:00Z"),
            mappings,
            TimeZone.UTC,
        )!!
        assertEquals(LocalDate.parse("2026-09-02"), utc.localDate)
    }

    // ---------------------------------------------------------------- aggregation

    @Test
    fun `the reduction follows the metric, not the caller`() {
        val steps = listOf(sample(HealthProvider.APPLE_HEALTH, 1000.0), sample(HealthProvider.APPLE_HEALTH, 500.0))
        assertEquals(1500.0, SampleNormalizer.aggregate(HealthMetric.STEPS, steps))

        val heartRates = listOf(
            sample(HealthProvider.APPLE_HEALTH, 60.0, HealthMetric.HEART_RATE),
            sample(HealthProvider.APPLE_HEALTH, 80.0, HealthMetric.HEART_RATE),
        )
        assertEquals(70.0, SampleNormalizer.aggregate(HealthMetric.HEART_RATE, heartRates))
    }

    @Test
    fun `weight takes the most recent reading, not an average`() {
        val weights = listOf(
            sample(HealthProvider.MANUAL, 58.0, HealthMetric.WEIGHT, "2026-09-02T07:00:00Z"),
            sample(HealthProvider.MANUAL, 62.0, HealthMetric.WEIGHT, "2026-09-02T19:00:00Z"),
        )
        assertEquals(62.0, SampleNormalizer.aggregate(HealthMetric.WEIGHT, weights))
    }

    @Test
    fun `a day with no samples has no value rather than a zero`() {
        assertNull(SampleNormalizer.aggregate(HealthMetric.STEPS, emptyList()))
    }

    // ---------------------------------------------------------------- dedup

    /**
     * A phone and a watch both count steps. Adding them together doubles the day, which
     * is the single most visible way this layer can be wrong.
     */
    @Test
    fun `two providers reporting the same metric do not both count`() {
        val samples = listOf(
            sample(HealthProvider.APPLE_HEALTH, 8000.0),
            sample(HealthProvider.HEALTH_CONNECT, 7800.0),
        )
        val deduped = SampleNormalizer.deduplicate(samples)
        assertEquals(1, deduped.size)
        assertEquals(HealthProvider.APPLE_HEALTH, deduped.single().provider)
        assertEquals(8000.0, SampleNormalizer.aggregate(HealthMetric.STEPS, deduped))
    }

    @Test
    fun `a value the user entered herself outranks every device`() {
        val samples = listOf(
            sample(HealthProvider.APPLE_HEALTH, 8000.0),
            sample(HealthProvider.MANUAL, 5000.0),
        )
        assertEquals(HealthProvider.MANUAL, SampleNormalizer.deduplicate(samples).single().provider)
    }

    @Test
    fun `one provider's several samples all survive`() {
        val samples = listOf(
            sample(HealthProvider.APPLE_HEALTH, 3000.0),
            sample(HealthProvider.APPLE_HEALTH, 5000.0),
        )
        assertEquals(2, SampleNormalizer.deduplicate(samples).size)
    }
}
