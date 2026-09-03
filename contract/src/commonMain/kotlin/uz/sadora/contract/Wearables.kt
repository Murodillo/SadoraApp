package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where a sample came from.
 *
 * Only the two platform sources are wired for v1; the rest are listed because the
 * normalisation layer is meant to take them without a schema change, which is the whole
 * reason it exists as a layer.
 */
@Serializable
enum class HealthProvider {
    @SerialName("apple_health") APPLE_HEALTH,
    @SerialName("health_connect") HEALTH_CONNECT,
    @SerialName("oura") OURA,
    @SerialName("garmin") GARMIN,
    @SerialName("whoop") WHOOP,
    @SerialName("fitbit") FITBIT,
    @SerialName("samsung_health") SAMSUNG_HEALTH,
    @SerialName("manual") MANUAL,
}

/** How a day's samples are reduced to one number. */
@Serializable
enum class Aggregation {
    @SerialName("sum") SUM,
    @SerialName("average") AVERAGE,
    @SerialName("latest") LATEST,
    @SerialName("min") MIN,
    @SerialName("max") MAX,
}

/**
 * The metrics SADORA speaks in.
 *
 * Providers each have their own names and units; everything is converted to these on the
 * way in, so nothing downstream has to know whether a step count came from an iPhone or
 * a ring.
 */
@Serializable
enum class HealthMetric(val canonicalUnit: String, val aggregation: Aggregation) {
    @SerialName("steps") STEPS("count", Aggregation.SUM),
    @SerialName("active_energy") ACTIVE_ENERGY("kcal", Aggregation.SUM),
    @SerialName("distance") DISTANCE("m", Aggregation.SUM),
    @SerialName("heart_rate") HEART_RATE("bpm", Aggregation.AVERAGE),
    @SerialName("resting_heart_rate") RESTING_HEART_RATE("bpm", Aggregation.AVERAGE),
    @SerialName("hrv") HRV("ms", Aggregation.AVERAGE),
    @SerialName("respiratory_rate") RESPIRATORY_RATE("brpm", Aggregation.AVERAGE),
    @SerialName("body_temperature") BODY_TEMPERATURE("c", Aggregation.AVERAGE),
    @SerialName("sleep_duration") SLEEP_DURATION("min", Aggregation.SUM),
    @SerialName("sleep_deep") SLEEP_DEEP("min", Aggregation.SUM),
    @SerialName("sleep_rem") SLEEP_REM("min", Aggregation.SUM),
    @SerialName("weight") WEIGHT("kg", Aggregation.LATEST),
}

/**
 * One sample as the device reports it.
 *
 * [externalId] is the provider's own identifier and is what makes re-syncing safe: the
 * same sample sent twice updates one row instead of doubling a step count.
 */
@Serializable
data class HealthSampleInput(
    val provider: HealthProvider,
    val externalId: String,
    /** The provider's own metric name; mapped to a canonical metric server-side. */
    val metric: String,
    val value: Double,
    val unit: String? = null,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val sourceDevice: String? = null,
)

@Serializable
data class IngestSamplesRequest(
    val samples: List<HealthSampleInput>,
    /** The device's timezone, so samples land on the right calendar day. */
    val timezone: String? = null,
)

@Serializable
data class IngestResult(
    val accepted: Int,
    val updated: Int,
    /** Samples whose metric has no mapping. The app should stop sending these. */
    val unmapped: List<String> = emptyList(),
    val rejected: Int = 0,
    val daysAffected: List<LocalDate> = emptyList(),
)

/** One metric's value for one day, already reduced. */
@Serializable
data class DailyMetric(
    val metric: HealthMetric,
    val value: Double,
    val unit: String,
    val sampleCount: Int,
    val providers: List<HealthProvider> = emptyList(),
)

@Serializable
data class DailyHealth(
    val date: LocalDate,
    val metrics: List<DailyMetric> = emptyList(),
) {
    fun value(metric: HealthMetric): Double? = metrics.firstOrNull { it.metric == metric }?.value
}

@Serializable
data class DailyHealthRange(
    val from: LocalDate,
    val to: LocalDate,
    val days: List<DailyHealth> = emptyList(),
)

/** The Data Sources screen: what is connected and whether it is working. */
@Serializable
data class ProviderStatus(
    val provider: HealthProvider,
    val connected: Boolean,
    val lastSampleAt: Instant? = null,
    val sampleCount: Long = 0,
    val metrics: List<HealthMetric> = emptyList(),
)

/** Page 12 of the admin panel: provider metric name to canonical metric. */
@Serializable
data class MetricMapping(
    val provider: HealthProvider,
    val providerMetric: String,
    val metric: HealthMetric,
    val providerUnit: String? = null,
    /** Multiplier applied to reach the canonical unit — kJ to kcal is 0.239. */
    val scale: Double = 1.0,
    val active: Boolean = true,
)
