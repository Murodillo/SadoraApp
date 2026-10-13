package uz.sadora.server.wearable.huawei

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.HealthSampleInput

/**
 * Huawei Health Kit's answers to SADORA samples, under the other mappers' conventions.
 *
 * A day's statistics carry her own calendar date, so they are placed at noon UTC of it,
 * which is that day wherever she lives. A night lands on the morning she woke, and a nap
 * is not the night. A day with a total of zero is a day the watch was not worn, and is
 * left for her phone to answer. HRV comes as the night's samples and is kept as the
 * day's average. Both temperatures Huawei reports are readings in degrees, not
 * deviations from a baseline, so both are taken.
 *
 * External ids are built from the date, so a day pulled again overwrites itself.
 */
object HuaweiMapper {

    private val provider = HealthProvider.HUAWEI

    /** Statistics type and field, to the name the mapping table knows it by. */
    private val DAILY = mapOf(
        ("com.huawei.continuous.steps.total" to "steps") to "steps",
        ("com.huawei.continuous.distance.total" to "distance") to "distance_meters",
        ("com.huawei.continuous.calories.burnt.total" to "calories_total") to "active_calories",
        ("com.huawei.continuous.heart_rate.statistics" to "avg") to "heart_rate_average",
        ("com.huawei.continuous.resting_heart_rate.statistics" to "avg") to "resting_heart_rate",
        ("com.huawei.continuous.spo2.statistics" to "saturation_avg") to "spo2_average",
        ("com.huawei.continuous.skin.temperature.statistics" to "avg") to "skin_temperature",
        ("com.huawei.continuous.body.temperature.statistics" to "avg") to "body_temperature",
        ("com.huawei.continuous.body_weight.statistics" to "last") to "weight_kg",
        ("com.huawei.continuous.body_weight.statistics" to "avg_body_fat_rate") to "body_fat_rate",
        ("com.huawei.vo2max.statistics" to "last") to "vo2_max",
    )

    fun fromDay(group: HuaweiGroup, zone: TimeZone): List<HealthSampleInput> {
        val date = dateOf(Instant.fromEpochMilliseconds(group.startTime), zone)
        val at = noonOf(date)
        return group.sampleSet.flatMap { it.samplePoints }.flatMap { point ->
            point.value.mapNotNull { field ->
                val metric = DAILY[point.dataTypeName to field.fieldName] ?: return@mapNotNull null
                field.number?.takeIf { it > 0 }?.let { sample("day:$date", metric, it, at) }
            }
        }
    }

    fun fromSleep(record: HuaweiHealthRecord, zone: TimeZone): List<HealthSampleInput> {
        val fields = record.value.associate { it.fieldName to it.number }
        if (fields["sleep_type"] == NAP) return emptyList()
        val asleep = fields["all_sleep_time"]?.takeIf { it > 0 } ?: return emptyList()
        // The record's own bounds are in nanoseconds; the two fields inside it in milliseconds.
        val at = fields["wakeup_time"]?.takeIf { it > 0 }?.let { Instant.fromEpochMilliseconds(it.toLong()) }
            ?: Instant.fromEpochMilliseconds(record.endTime / NANOS_PER_MILLI)
        val start = fields["fall_asleep_time"]?.takeIf { it > 0 }?.let { Instant.fromEpochMilliseconds(it.toLong()) }
        val id = "sleep:${dateOf(at, zone)}"
        fun part(field: String, metric: String) = fields[field]?.let { sample(id, metric, it, at, start) }
        return listOfNotNull(
            sample(id, "minutes_asleep", asleep, at, start),
            part("deep_sleep_time", "minutes_deep"),
            part("dream_time", "minutes_rem"),
            part("light_sleep_time", "minutes_light"),
            part("awake_time", "minutes_awake"),
            fields["sleep_score"]?.takeIf { it > 0 }?.let { sample(id, "sleep_score", it, at, start) },
        )
    }

    fun fromHrv(points: List<HuaweiSamplePoint>, zone: TimeZone): List<HealthSampleInput> = points
        .mapNotNull { point ->
            val value = point.value.firstOrNull { it.fieldName == "heartRateVariabilityRMSSD" }?.number?.takeIf { it > 0 }
            value?.let { dateOf(Instant.fromEpochMilliseconds(point.startTime / NANOS_PER_MILLI), zone) to it }
        }
        .groupBy({ it.first }, { it.second })
        .map { (date, values) -> sample("day:$date", "hrv_rmssd", values.average(), noonOf(date)) }

    private const val NAP = 3.0
    private const val NANOS_PER_MILLI = 1_000_000

    private fun dateOf(instant: Instant, zone: TimeZone): String = instant.toLocalDateTime(zone).date.toString()

    private fun noonOf(date: String) = Instant.parse("${date}T12:00:00Z")

    private fun sample(
        id: String,
        metric: String,
        value: Double,
        startedAt: Instant,
        endedAt: Instant? = null,
    ) = HealthSampleInput(
        provider = provider,
        externalId = "$id:$metric",
        metric = metric,
        value = value,
        startedAt = startedAt,
        endedAt = endedAt,
        sourceDevice = "Huawei",
    )
}
