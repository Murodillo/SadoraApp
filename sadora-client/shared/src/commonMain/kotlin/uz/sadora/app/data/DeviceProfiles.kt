package uz.sadora.app.data

import kotlin.math.roundToInt
import uz.sadora.app.model.Fmt
import uz.sadora.contract.DailyHealth
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.HealthProvider

/**
 * How each device's day is drawn.
 *
 * The devices do not measure the same things, and they do not call what they share by
 * the same name: WHOOP leads with recovery and strain and counts no steps, Oura calls its
 * daily score readiness, Garmin and Huawei give a sleep score where WHOOP gives sleep
 * performance and Oura only efficiency, and an iPhone's HRV is SDNN where everyone
 * else's is RMSSD. A screen that drew one fixed set of tiles showed a WHOOP owner an
 * empty step count and a Garmin owner nothing of her SpO₂ or VO₂max.
 *
 * Every metric is labelled and formatted by the device that sent it, and the screens
 * order what is there by the profile of her [primary] device. Pure, so it can be tested.
 */
object DeviceProfiles {

    /**
     * Whose numbers the day is drawn in: the dedicated wearable before the phone, in the
     * same order the server uses to pick a winner between two devices, so the device
     * the screen speaks for is the one whose numbers are on it.
     */
    private val Priority = listOf(
        HealthProvider.OURA,
        HealthProvider.WHOOP,
        HealthProvider.GARMIN,
        HealthProvider.FITBIT,
        HealthProvider.HUAWEI,
        HealthProvider.APPLE_HEALTH,
        HealthProvider.HEALTH_CONNECT,
        HealthProvider.SAMSUNG_HEALTH,
        HealthProvider.MANUAL,
    )

    /** The device today's numbers are mostly from, or null when nothing came. */
    fun primary(day: DailyHealth?): HealthProvider? {
        val present = day?.metrics?.flatMap { it.providers }?.toSet().orEmpty()
        return Priority.firstOrNull { it in present }
    }

    /** Which device sent [metric] on [day]. */
    fun sourceOf(day: DailyHealth?, metric: HealthMetric): HealthProvider? =
        day?.metrics?.firstOrNull { it.metric == metric }?.providers?.firstOrNull()

    /**
     * The body signals each device is worth looking at for, in the order that device's
     * own app puts them. Steps and sleep have their own places on Today and are not here.
     */
    fun signals(provider: HealthProvider?): List<HealthMetric> = when (provider) {
        HealthProvider.WHOOP -> listOf(
            HealthMetric.RECOVERY, HealthMetric.STRAIN, HealthMetric.HRV, HealthMetric.RESTING_HEART_RATE,
            HealthMetric.SKIN_TEMPERATURE, HealthMetric.SPO2, HealthMetric.RESPIRATORY_RATE,
        )
        HealthProvider.OURA -> listOf(
            HealthMetric.RECOVERY, HealthMetric.HRV, HealthMetric.RESTING_HEART_RATE,
            HealthMetric.SPO2, HealthMetric.RESPIRATORY_RATE,
        )
        HealthProvider.GARMIN, HealthProvider.FITBIT -> listOf(
            HealthMetric.RESTING_HEART_RATE, HealthMetric.HRV, HealthMetric.SPO2,
            HealthMetric.RESPIRATORY_RATE, HealthMetric.VO2_MAX,
        )
        HealthProvider.HUAWEI -> listOf(
            HealthMetric.RESTING_HEART_RATE, HealthMetric.HRV, HealthMetric.SKIN_TEMPERATURE,
            HealthMetric.BODY_TEMPERATURE, HealthMetric.SPO2, HealthMetric.VO2_MAX,
        )
        // The phone's store: the temperatures matter most here, because a basal body
        // temperature she logs is the one reading that follows her cycle.
        else -> listOf(
            HealthMetric.RESTING_HEART_RATE, HealthMetric.HRV, HealthMetric.SKIN_TEMPERATURE,
            HealthMetric.BODY_TEMPERATURE, HealthMetric.SPO2, HealthMetric.RESPIRATORY_RATE,
        )
    }

    /** Everything that can appear as a body signal, for metrics a second device adds. */
    private val AllSignals = listOf(
        HealthMetric.RECOVERY, HealthMetric.STRAIN, HealthMetric.RESTING_HEART_RATE, HealthMetric.HRV,
        HealthMetric.SKIN_TEMPERATURE, HealthMetric.BODY_TEMPERATURE, HealthMetric.SPO2,
        HealthMetric.RESPIRATORY_RATE, HealthMetric.VO2_MAX,
    )

    /**
     * The body signals [day] has, in the order of its primary device and then anything a
     * second device added. Capped: the card is a glance, the doctor page has the rest.
     */
    fun bodySignals(day: DailyHealth?, limit: Int = MaxSignals): List<HealthMetric> {
        val present = day?.metrics?.map { it.metric }?.toSet().orEmpty()
        return (signals(primary(day)) + AllSignals).distinct().filter { it in present }.take(limit)
    }

    /**
     * Whether activity is told as strain rather than steps.
     *
     * A WHOOP counts no steps; its owner reads her day as strain. When the phone in her
     * pocket counts steps as well, those used to win the tile and her strain was hidden.
     */
    fun leadsWithStrain(primary: HealthProvider?): Boolean = primary == HealthProvider.WHOOP

    /**
     * The device's own score for the night: WHOOP's performance, Garmin's and Huawei's
     * score, or — where that is all there is, as on Oura — the efficiency.
     */
    fun sleepScore(day: DailyHealth?): HealthMetric? = listOf(HealthMetric.SLEEP_PERFORMANCE, HealthMetric.SLEEP_EFFICIENCY)
        .firstOrNull { metric -> day?.value(metric) != null }

    /**
     * Garmin and Huawei give a score out of a hundred, not a share: "82", not "82%".
     * WHOOP's performance and every efficiency are percentages.
     */
    fun isPercent(metric: HealthMetric, provider: HealthProvider?): Boolean = when (metric) {
        HealthMetric.SLEEP_PERFORMANCE -> provider != HealthProvider.GARMIN && provider != HealthProvider.HUAWEI
        HealthMetric.SLEEP_EFFICIENCY, HealthMetric.RECOVERY, HealthMetric.SPO2 -> true
        else -> false
    }

    /** One value as the tile shows it, unit included. */
    fun format(metric: HealthMetric, value: Double, provider: HealthProvider?): String = when (metric) {
        HealthMetric.HRV -> "${value.roundToInt()} ms"
        HealthMetric.SKIN_TEMPERATURE, HealthMetric.BODY_TEMPERATURE -> "${Fmt.oneDecimal(value.toFloat())} °C"
        HealthMetric.STRAIN, HealthMetric.RESPIRATORY_RATE, HealthMetric.VO2_MAX -> Fmt.oneDecimal(value.toFloat())
        else -> if (isPercent(metric, provider)) "${value.roundToInt()}%" else "${value.roundToInt()}"
    }

    /** How many decimals a change in [metric] is worth showing. */
    fun deltaDecimals(metric: HealthMetric): Int = when (metric) {
        HealthMetric.SKIN_TEMPERATURE, HealthMetric.BODY_TEMPERATURE, HealthMetric.STRAIN,
        HealthMetric.RESPIRATORY_RATE, HealthMetric.VO2_MAX -> 1
        else -> 0
    }

    private const val MaxSignals = 6
}
