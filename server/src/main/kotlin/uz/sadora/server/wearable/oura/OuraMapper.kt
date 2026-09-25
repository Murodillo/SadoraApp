package uz.sadora.server.wearable.oura

import kotlin.time.Instant
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.HealthSampleInput

/**
 * Oura records to SADORA samples, under the same conventions as the WHOOP mapper.
 *
 * A night lands on the morning she woke: its samples start at `bedtime_end`. Only the
 * main sleep (`long_sleep`) counts as the night — naps and short rests are left out.
 * Readiness is Oura's own daily score, and it goes where WHOOP's recovery goes. The
 * temperature Oura reports is a deviation from her baseline, not a temperature, so it
 * is not taken.
 *
 * External ids are the record's id plus the field, so a re-sync overwrites.
 */
object OuraMapper {

    private val provider = HealthProvider.OURA
    private const val MAIN_SLEEP = "long_sleep"

    fun fromSleep(sleep: OuraSleep): List<HealthSampleInput> {
        if (sleep.type != MAIN_SLEEP) return emptyList()
        val start = Instant.parse(sleep.bedtimeStart)
        val at = Instant.parse(sleep.bedtimeEnd)
        val id = "sleep:${sleep.id}"
        return listOfNotNull(
            sleep.totalSleepDuration?.let { sample(id, "total_sleep_duration", it.toDouble(), at, start) },
            sleep.deepSleepDuration?.let { sample(id, "deep_sleep_duration", it.toDouble(), at, start) },
            sleep.remSleepDuration?.let { sample(id, "rem_sleep_duration", it.toDouble(), at, start) },
            sleep.lightSleepDuration?.let { sample(id, "light_sleep_duration", it.toDouble(), at, start) },
            sleep.awakeTime?.let { sample(id, "awake_time", it.toDouble(), at, start) },
            sleep.efficiency?.let { sample(id, "efficiency", it.toDouble(), at) },
            sleep.averageHrv?.let { sample(id, "average_hrv", it.toDouble(), at) },
            sleep.lowestHeartRate?.let { sample(id, "lowest_heart_rate", it.toDouble(), at) },
            sleep.averageBreath?.let { sample(id, "average_breath", it, at) },
        )
    }

    fun fromReadiness(readiness: OuraReadiness): List<HealthSampleInput> = listOfNotNull(
        readiness.score?.let { sample("readiness:${readiness.id}", "readiness_score", it.toDouble(), Instant.parse(readiness.timestamp)) },
    )

    fun fromActivity(activity: OuraActivity): List<HealthSampleInput> {
        val at = Instant.parse(activity.timestamp)
        val id = "activity:${activity.id}"
        return listOf(
            sample(id, "steps", activity.steps.toDouble(), at),
            sample(id, "active_calories", activity.activeCalories.toDouble(), at),
            sample(id, "equivalent_walking_distance", activity.equivalentWalkingDistance.toDouble(), at),
        )
    }

    /** SpO2 is a nightly average with a day but no time; noon UTC keeps it on that day for her. */
    fun fromSpo2(spo2: OuraSpo2): List<HealthSampleInput> = listOfNotNull(
        spo2.spo2Percentage?.average?.let { sample("spo2:${spo2.id}", "spo2_average", it, Instant.parse("${spo2.day}T12:00:00Z")) },
    )

    fun fromPersonalInfo(info: OuraPersonalInfo, at: Instant): List<HealthSampleInput> = listOfNotNull(
        info.weight?.let { sample("personal:${info.id}", "weight", it, at) },
    )

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
        sourceDevice = "Oura",
    )
}
