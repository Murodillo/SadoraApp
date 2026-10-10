package uz.sadora.server.wearable.fitbit

import kotlin.time.Instant
import kotlinx.serialization.json.JsonPrimitive
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.HealthSampleInput

/**
 * Google Health records of a Fitbit to SADORA samples, under the other mappers' conventions.
 *
 * A night lands on the morning she woke, and only her main sleep is the night. Day totals
 * and nightly figures carry her own calendar date, so they are placed at noon UTC of it,
 * which is that day wherever she lives. A day with a total of zero is a day the tracker
 * was not worn, and is left for her phone to answer. The sleep temperature Google reports
 * is a deviation from her baseline, not a temperature, so it is not taken.
 *
 * External ids are built from the date, so a day pulled again overwrites itself.
 */
object FitbitMapper {

    private val provider = HealthProvider.FITBIT

    fun fromRollup(rollup: GoogleHealthRollup): List<HealthSampleInput> {
        val date = rollup.civilStartTime?.date?.toLocalDate()?.toString() ?: return emptyList()
        val at = noonOf(date)
        val id = "day:$date"
        return listOfNotNull(
            rollup.steps?.countSum.positive()?.let { sample(id, "steps", it, at) },
            rollup.floors?.countSum.positive()?.let { sample(id, "floors", it, at) },
            rollup.distance?.millimetersSum.positive()?.let { sample(id, "distance_millimeters", it, at) },
            rollup.activeEnergyBurned?.kcalSum.positive()?.let { sample(id, "active_energy_kcal", it, at) },
            rollup.heartRate?.beatsPerMinuteAvg.positive()?.let { sample(id, "heart_rate_average", it, at) },
        )
    }

    fun fromDataPoint(point: GoogleHealthDataPoint): List<HealthSampleInput> = listOfNotNull(
        point.dailyRestingHeartRate?.let { daily(it.date, "resting_heart_rate", it.beatsPerMinute.positive()) },
        point.dailyHeartRateVariability?.let { daily(it.date, "hrv_average", it.averageHeartRateVariabilityMilliseconds) },
        point.dailyOxygenSaturation?.let { daily(it.date, "spo2_average", it.averagePercentage) },
        point.dailyRespiratoryRate?.let { daily(it.date, "breaths_per_minute", it.breathsPerMinute) },
        point.dailyVo2Max?.let { daily(it.date, "vo2_max", it.vo2Max) },
        point.weight?.let { measured(it.sampleTime, "weight_grams", it.weightGrams) },
        point.bodyFat?.let { measured(it.sampleTime, "body_fat_percentage", it.percentage) },
    ) + point.sleep?.let(::fromSleep).orEmpty()

    fun fromSleep(sleep: GoogleHealthSleep): List<HealthSampleInput> {
        if (sleep.metadata?.mainSleep != true || sleep.metadata.nap) return emptyList()
        val at = sleep.interval?.endTime?.let(::instant) ?: return emptyList()
        val start = sleep.interval.startTime?.let(::instant)
        val asleep = sleep.summary?.minutesAsleep.positive() ?: return emptyList()
        val id = "sleep:${sleep.interval.civilEndTime?.date?.toLocalDate() ?: at.toString().take(10)}"
        fun stage(type: String, metric: String) =
            sleep.summary?.stagesSummary?.firstOrNull { it.type == type }?.minutes.number()?.let { sample(id, metric, it, at, start) }
        return listOfNotNull(
            sample(id, "minutes_asleep", asleep, at, start),
            stage("DEEP", "minutes_deep"),
            stage("REM", "minutes_rem"),
            stage("LIGHT", "minutes_light"),
            sleep.summary?.minutesAwake.number()?.let { sample(id, "minutes_awake", it, at, start) },
        )
    }

    private fun daily(date: CivilDate?, metric: String, value: Double?): HealthSampleInput? {
        val day = date?.toLocalDate()?.toString() ?: return null
        return value?.takeIf { it > 0 }?.let { sample("day:$day", metric, it, noonOf(day)) }
    }

    private fun measured(time: SampleTime?, metric: String, value: Double?): HealthSampleInput? {
        val at = time?.physicalTime?.let(::instant) ?: return null
        return value?.takeIf { it > 0 }?.let { sample("at:${at.epochSeconds}", metric, it, at) }
    }

    private fun JsonPrimitive?.number(): Double? = this?.content?.toDoubleOrNull()

    private fun JsonPrimitive?.positive(): Double? = number()?.takeIf { it > 0 }

    private fun instant(text: String): Instant? = runCatching { Instant.parse(text) }.getOrNull()

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
        sourceDevice = "Fitbit",
    )
}
