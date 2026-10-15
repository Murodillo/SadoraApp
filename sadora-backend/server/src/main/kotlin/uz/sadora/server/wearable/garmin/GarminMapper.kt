package uz.sadora.server.wearable.garmin

import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.HealthSampleInput

/**
 * Garmin summaries to SADORA samples, under the same conventions as the other mappers.
 *
 * A night lands on the morning she woke. Garmin stamps every summary that belongs to a
 * day with her own calendar date, so those are placed at noon UTC of that date, which
 * is that day wherever she lives. Stress and Body Battery have no metric here, and the
 * skin temperature Garmin reports is a deviation from her baseline, so none is taken.
 *
 * Garmin resends a summary as it grows and gives the new version a new id, so external
 * ids are built from the date rather than the id: the day's later steps overwrite the
 * earlier ones instead of being added to them.
 */
object GarminMapper {

    private val provider = HealthProvider.GARMIN

    /** Every sample in [push], by the Garmin user it belongs to. */
    fun fromPush(push: GarminPush): Map<String?, List<HealthSampleInput>> {
        val byUser = mutableMapOf<String?, MutableList<HealthSampleInput>>()
        fun add(userId: String?, samples: List<HealthSampleInput>) {
            if (samples.isNotEmpty()) byUser.getOrPut(userId) { mutableListOf() } += samples
        }
        push.dailies.forEach { add(it.userId, fromDaily(it)) }
        push.sleeps.forEach { add(it.userId, fromSleep(it)) }
        push.hrv.forEach { add(it.userId, fromHrv(it)) }
        push.pulseox.forEach { add(it.userId, fromPulseOx(it)) }
        push.allDayRespiration.forEach { add(it.userId, fromRespiration(it)) }
        push.bodyComps.forEach { add(it.userId, fromBodyComp(it)) }
        push.userMetrics.forEach { add(it.userId, fromUserMetrics(it)) }
        return byUser
    }

    fun fromDaily(daily: GarminDaily): List<HealthSampleInput> {
        val date = daily.calendarDate ?: return emptyList()
        val at = noonOf(date)
        val id = "daily:$date"
        return listOfNotNull(
            // Zero is a day the watch was not worn, left for her phone to answer: Garmin
            // outranks the phone, so a zero here would wipe out the phone's real count.
            daily.steps?.takeIf { it > 0 }?.let { sample(id, "steps", it.toDouble(), at) },
            daily.distanceInMeters?.takeIf { it > 0 }?.let { sample(id, "distance_in_meters", it, at) },
            daily.activeKilocalories?.takeIf { it > 0 }?.let { sample(id, "active_kilocalories", it.toDouble(), at) },
            daily.floorsClimbed?.takeIf { it > 0 }?.let { sample(id, "floors_climbed", it.toDouble(), at) },
            daily.restingHeartRateInBeatsPerMinute?.let { sample(id, "resting_heart_rate", it.toDouble(), at) },
            daily.averageHeartRateInBeatsPerMinute?.let { sample(id, "average_heart_rate", it.toDouble(), at) },
        )
    }

    fun fromSleep(sleep: GarminSleep): List<HealthSampleInput> {
        val started = sleep.startTimeInSeconds ?: return emptyList()
        val asleep = sleep.durationInSeconds?.takeIf { it > 0 } ?: return emptyList()
        val start = Instant.fromEpochSeconds(started)
        val at = start + (asleep + (sleep.awakeDurationInSeconds ?: 0)).seconds
        val id = "sleep:${sleep.calendarDate ?: started}"
        return listOfNotNull(
            sample(id, "sleep_duration", asleep.toDouble(), at, start),
            sleep.deepSleepDurationInSeconds?.let { sample(id, "deep_sleep_duration", it.toDouble(), at, start) },
            sleep.remSleepInSeconds?.let { sample(id, "rem_sleep_duration", it.toDouble(), at, start) },
            sleep.lightSleepDurationInSeconds?.let { sample(id, "light_sleep_duration", it.toDouble(), at, start) },
            sleep.awakeDurationInSeconds?.let { sample(id, "awake_duration", it.toDouble(), at, start) },
            sleep.overallSleepScore?.value?.let { sample(id, "sleep_score", it.toDouble(), at) },
        )
    }

    fun fromHrv(hrv: GarminHrv): List<HealthSampleInput> {
        val date = hrv.calendarDate ?: return emptyList()
        return listOfNotNull(hrv.lastNightAvg?.let { sample("hrv:$date", "hrv_last_night_avg", it.toDouble(), noonOf(date)) })
    }

    /** The night's readings as one average. A spot check she took by hand is not the night. */
    fun fromPulseOx(pulseOx: GarminPulseOx): List<HealthSampleInput> {
        val date = pulseOx.calendarDate ?: return emptyList()
        if (pulseOx.onDemand) return emptyList()
        val readings = pulseOx.timeOffsetSpo2Values.values.filter { it > 0 }
        if (readings.isEmpty()) return emptyList()
        return listOf(sample("spo2:$date", "spo2_average", readings.average(), noonOf(date)))
    }

    /** Garmin marks a moment it could not read with a zero or a negative; those are left out. */
    fun fromRespiration(respiration: GarminRespiration): List<HealthSampleInput> {
        val started = respiration.startTimeInSeconds ?: return emptyList()
        val breaths = respiration.timeOffsetEpochToBreaths.values.filter { it > 0 }
        if (breaths.isEmpty()) return emptyList()
        val start = Instant.fromEpochSeconds(started)
        val end = respiration.durationInSeconds?.let { start + it.seconds }
        return listOf(sample("respiration:$started", "respiration_average", breaths.average(), start, end))
    }

    fun fromBodyComp(body: GarminBodyComp): List<HealthSampleInput> {
        val measured = body.measurementTimeInSeconds ?: return emptyList()
        val at = Instant.fromEpochSeconds(measured)
        val id = "body:$measured"
        return listOfNotNull(
            body.weightInGrams?.let { sample(id, "weight_in_grams", it.toDouble(), at) },
            body.bodyFatInPercent?.let { sample(id, "body_fat_in_percent", it, at) },
        )
    }

    fun fromUserMetrics(metrics: GarminUserMetrics): List<HealthSampleInput> {
        val date = metrics.calendarDate ?: return emptyList()
        return listOfNotNull(metrics.vo2Max?.let { sample("metrics:$date", "vo2_max", it, noonOf(date)) })
    }

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
        sourceDevice = "Garmin",
    )
}
