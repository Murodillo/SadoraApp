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
    @SerialName("sleep_light") SLEEP_LIGHT("min", Aggregation.SUM),
    @SerialName("sleep_awake") SLEEP_AWAKE("min", Aggregation.SUM),
    /** How much of the sleep she needed she got, as WHOOP scores it. 0–100. */
    @SerialName("sleep_performance") SLEEP_PERFORMANCE("percent", Aggregation.AVERAGE),
    /** Time asleep over time in bed. 0–100. */
    @SerialName("sleep_efficiency") SLEEP_EFFICIENCY("percent", Aggregation.AVERAGE),
    @SerialName("weight") WEIGHT("kg", Aggregation.LATEST),

    // ---- the recovery family, which the strap-style wearables (WHOOP, Oura) speak in.
    // None of these is a medical measure; each is the vendor's own summary of a night.

    /** The vendor's readiness score for the day, 0–100. One per day, so the average is the value. */
    @SerialName("recovery") RECOVERY("percent", Aggregation.AVERAGE),
    /** Cardiovascular load for the day on WHOOP's 0–21 scale. The day's total is its maximum. */
    @SerialName("strain") STRAIN("score", Aggregation.MAX),
    @SerialName("spo2") SPO2("percent", Aggregation.AVERAGE),
    /** Skin temperature, distinct from [BODY_TEMPERATURE]: a wrist reads cooler than a thermometer. */
    @SerialName("skin_temperature") SKIN_TEMPERATURE("c", Aggregation.AVERAGE),
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

/**
 * How a provider hands its data over.
 *
 * The two platform stores are read on the phone and posted as batches; the cloud
 * services are connected once with OAuth and pulled by the server. The screen that lists
 * providers needs to know which, because the button it draws is different.
 */
@Serializable
enum class ProviderKind {
    /** HealthKit, Health Connect — the phone reads them and posts samples. */
    @SerialName("on_device") ON_DEVICE,
    /** WHOOP, Oura, Garmin, Fitbit — an OAuth grant, then the server syncs. */
    @SerialName("cloud") CLOUD,
    @SerialName("manual") MANUAL,
}

/** Where a cloud connection stands. */
@Serializable
enum class ConnectionStatus {
    @SerialName("active") ACTIVE,
    /** The refresh token stopped working; she has to connect again. */
    @SerialName("expired") EXPIRED,
    /** The last sync failed for a reason other than authorisation. */
    @SerialName("error") ERROR,
}

/**
 * One provider as the connect screen lists it.
 *
 * [available] is the server saying whether it can take this provider *right now*: WHOOP
 * needs a client id in the environment, HealthKit needs an iPhone. A provider that is
 * listed but not available is drawn greyed with [unavailableReason], so the list stays
 * the same length on every phone and nothing looks missing.
 */
@Serializable
data class ProviderInfo(
    val provider: HealthProvider,
    val kind: ProviderKind,
    val available: Boolean,
    /** Stable key the app words: "not_configured", "ios_only", "android_only", "planned". */
    val unavailableReason: String? = null,
    /** What this provider can deliver, so the card can say what connecting it is for. */
    val metrics: List<HealthMetric> = emptyList(),
    val connection: WearableConnection? = null,
)

/** A cloud provider she has authorised. */
@Serializable
data class WearableConnection(
    val provider: HealthProvider,
    val status: ConnectionStatus,
    val connectedAt: Instant,
    val lastSyncAt: Instant? = null,
    /** The last failure, as a short stable key. Null while everything works. */
    val lastError: String? = null,
    val scopes: List<String> = emptyList(),
)

/** Where to send her to grant access. The state is kept server-side and checked on return. */
@Serializable
data class ConnectStart(
    val provider: HealthProvider,
    val authorizeUrl: String,
)

/** What a manual sync did. */
@Serializable
data class SyncResult(
    val provider: HealthProvider,
    val accepted: Int,
    val updated: Int,
    val daysAffected: List<LocalDate> = emptyList(),
)

/** Stable keys for [ProviderInfo.unavailableReason]. */
object ProviderUnavailable {
    const val NOT_CONFIGURED = "not_configured"
    const val IOS_ONLY = "ios_only"
    const val ANDROID_ONLY = "android_only"
    const val PLANNED = "planned"
}
