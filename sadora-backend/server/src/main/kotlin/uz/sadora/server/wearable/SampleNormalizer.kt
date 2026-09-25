package uz.sadora.server.wearable

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.contract.Aggregation
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.HealthSampleInput
import uz.sadora.contract.MetricMapping

/** A sample once it speaks SADORA's metrics and units. */
data class NormalizedSample(
    val provider: HealthProvider,
    val externalId: String,
    val metric: HealthMetric,
    val value: Double,
    val unit: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val localDate: LocalDate,
    val sourceDevice: String?,
)

/**
 * Turns provider samples into canonical ones.
 *
 * Everything a provider is allowed to differ about — the metric's name, its unit, its
 * scale — is data in `provider_metric_mappings`, not code here. A provider renaming a
 * field is a row change; a new provider is a set of rows. That is the whole reason this
 * is a layer rather than two if-branches for HealthKit and Health Connect.
 *
 * Pure and clock-free so every conversion can be tested exactly.
 */
object SampleNormalizer {

    /**
     * An unmapped metric is reported back rather than dropped silently or guessed at:
     * the app should stop sending it, and an operator should see that it needs a row.
     */
    fun normalize(
        input: HealthSampleInput,
        mappings: Map<Pair<HealthProvider, String>, MetricMapping>,
        timezone: TimeZone,
    ): NormalizedSample? {
        val mapping = mappings[input.provider to input.metric]?.takeIf { it.active } ?: return null
        if (!input.value.isFinite()) return null

        return NormalizedSample(
            provider = input.provider,
            externalId = input.externalId,
            metric = mapping.metric,
            value = input.value * mapping.scale,
            unit = mapping.metric.canonicalUnit,
            startedAt = input.startedAt,
            endedAt = input.endedAt,
            // The day is decided here, once, from the device's timezone. Leaving it to
            // query time would make a 23:50 sample land on a different day depending on
            // who asked and from where.
            localDate = input.startedAt.toLocalDateTime(timezone).date,
            sourceDevice = input.sourceDevice,
        )
    }

    /**
     * Whether a value could have come from a body.
     *
     * A corrupt sample — a negative sleep, a heart rate of ten thousand, a weight of
     * 1e308 — used to be stored and summed like any other, and the Sleep screen and the
     * doctor page then showed the nonsense as fact. The bounds are deliberately wide:
     * they refuse the impossible, not the unusual.
     */
    fun isPlausible(metric: HealthMetric, value: Double): Boolean {
        if (!value.isFinite() || value < 0) return false
        return when (metric) {
            HealthMetric.STEPS -> value <= 200_000
            HealthMetric.ACTIVE_ENERGY -> value <= 20_000
            HealthMetric.DISTANCE -> value <= 500_000
            HealthMetric.HEART_RATE, HealthMetric.RESTING_HEART_RATE -> value in 20.0..300.0
            HealthMetric.HRV -> value <= 1_000
            HealthMetric.RESPIRATORY_RATE -> value in 2.0..80.0
            HealthMetric.BODY_TEMPERATURE, HealthMetric.SKIN_TEMPERATURE -> value in 25.0..45.0
            HealthMetric.SLEEP_DURATION, HealthMetric.SLEEP_DEEP, HealthMetric.SLEEP_REM,
            HealthMetric.SLEEP_LIGHT, HealthMetric.SLEEP_AWAKE -> value <= 24 * 60
            HealthMetric.SLEEP_PERFORMANCE, HealthMetric.SLEEP_EFFICIENCY,
            HealthMetric.RECOVERY, HealthMetric.SPO2 -> value <= 100
            HealthMetric.WEIGHT -> value in 2.0..400.0
            HealthMetric.STRAIN -> value <= 21
        }
    }

    /**
     * Reduces a day's samples for one metric to a single figure.
     *
     * The reduction follows the metric, not the caller: steps sum, heart rate averages,
     * weight takes the most recent. Summing a heart rate would produce a number that
     * looks like data and means nothing.
     */
    fun aggregate(metric: HealthMetric, samples: List<NormalizedSample>): Double? {
        if (samples.isEmpty()) return null
        return when (metric.aggregation) {
            Aggregation.SUM -> samples.sumOf { it.value }
            Aggregation.AVERAGE -> samples.sumOf { it.value } / samples.size
            Aggregation.LATEST -> samples.maxBy { it.startedAt }.value
            Aggregation.MIN -> samples.minOf { it.value }
            Aggregation.MAX -> samples.maxOf { it.value }
        }
    }

    /**
     * Drops samples that two providers both reported.
     *
     * A phone and a watch on the same wrist both count steps, and adding them together
     * doubles the day. When several providers cover one metric, the highest-priority one
     * wins outright for that day rather than being blended — a blended step count is
     * neither device's number and matches nothing the user can check.
     */
    fun deduplicate(
        samples: List<NormalizedSample>,
        priority: List<HealthProvider> = DEFAULT_PRIORITY,
    ): List<NormalizedSample> {
        if (samples.isEmpty()) return samples
        val byProvider = samples.groupBy { it.provider }
        if (byProvider.size == 1) return samples

        val winner = priority.firstOrNull { it in byProvider } ?: byProvider.keys.first()
        return byProvider.getValue(winner)
    }

    /**
     * Which source to believe when several report the same metric.
     *
     * A dedicated wearable measures more directly than a phone in a pocket, so the rings
     * and watches come first; a value the user typed herself outranks all of them,
     * because she is correcting something.
     */
    val DEFAULT_PRIORITY: List<HealthProvider> = listOf(
        HealthProvider.MANUAL,
        HealthProvider.OURA,
        HealthProvider.WHOOP,
        HealthProvider.GARMIN,
        HealthProvider.FITBIT,
        HealthProvider.APPLE_HEALTH,
        HealthProvider.HEALTH_CONNECT,
        HealthProvider.SAMSUNG_HEALTH,
    )
}
