package uz.sadora.server.wearable.whoop

import kotlin.time.Instant
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.HealthSampleInput

/**
 * WHOOP records to SADORA samples.
 *
 * Pure, so a night can be checked exactly. Three conventions run through it:
 *
 * A night lands on the morning she woke, so "last night" on the sleep screen is the
 * sleep that ended today — the sample's start is the sleep's *end*. A recovery is
 * scored on waking and belongs to the same morning. A cycle — WHOOP's physiological
 * day, not a menstrual one — starts when she wakes, so its strain lands on that day too.
 *
 * The external id is the record's id plus the field, so a re-sync updates a row rather
 * than doubling it, and a score that was PENDING and is SCORED now simply overwrites.
 * Only SCORED records produce samples: a pending score is not a number yet, and an
 * unscorable night has nothing to say.
 */
object WhoopMapper {

    private const val SCORED = "SCORED"
    private val provider = HealthProvider.WHOOP

    fun fromRecovery(recovery: WhoopRecovery): List<HealthSampleInput> {
        val score = recovery.score?.takeIf { recovery.scoreState == SCORED } ?: return emptyList()
        val at = recovery.createdAt
        val id = "recovery:${recovery.cycleId}"
        return listOfNotNull(
            score.recoveryScore?.let { sample(id, "recovery_score", it, at) },
            score.restingHeartRate?.let { sample(id, "resting_heart_rate", it, at) },
            score.hrvRmssdMilli?.let { sample(id, "hrv_rmssd_milli", it, at) },
            score.spo2Percentage?.let { sample(id, "spo2_percentage", it, at) },
            score.skinTempCelsius?.let { sample(id, "skin_temp_celsius", it, at) },
        )
    }

    fun fromSleep(sleep: WhoopSleep): List<HealthSampleInput> {
        // Naps are real sleep but not "the night"; folding one into the night's total
        // would make an afternoon on the sofa read as nine hours in bed.
        if (sleep.nap) return emptyList()
        val score = sleep.score?.takeIf { sleep.scoreState == SCORED } ?: return emptyList()
        val at = sleep.end
        val id = "sleep:${sleep.id}"
        val stages = score.stageSummary
        return listOfNotNull(
            // Time asleep, not time in bed: the app's "sleep" is what a doctor means by it.
            stages?.let { sample(id, "asleep_milli", (it.totalInBedTimeMilli - it.totalAwakeTimeMilli).coerceAtLeast(0).toDouble(), at, sleep.start) },
            stages?.let { sample(id, "total_slow_wave_sleep_time_milli", it.totalSlowWaveSleepTimeMilli.toDouble(), at, sleep.start) },
            stages?.let { sample(id, "total_rem_sleep_time_milli", it.totalRemSleepTimeMilli.toDouble(), at, sleep.start) },
            stages?.let { sample(id, "total_light_sleep_time_milli", it.totalLightSleepTimeMilli.toDouble(), at, sleep.start) },
            stages?.let { sample(id, "total_awake_time_milli", it.totalAwakeTimeMilli.toDouble(), at, sleep.start) },
            score.respiratoryRate?.let { sample(id, "respiratory_rate", it, at) },
            score.sleepPerformancePercentage?.let { sample(id, "sleep_performance_percentage", it, at) },
            score.sleepEfficiencyPercentage?.let { sample(id, "sleep_efficiency_percentage", it, at) },
        )
    }

    fun fromCycle(cycle: WhoopCycle): List<HealthSampleInput> {
        val score = cycle.score?.takeIf { cycle.scoreState == SCORED } ?: return emptyList()
        val at = cycle.start
        val id = "cycle:${cycle.id}"
        return listOfNotNull(
            score.strain?.let { sample(id, "strain", it, at, cycle.end) },
            score.kilojoule?.let { sample(id, "kilojoule", it, at, cycle.end) },
            score.averageHeartRate?.let { sample(id, "average_heart_rate", it, at, cycle.end) },
        )
    }

    fun fromBody(body: WhoopBody, at: Instant, externalUserId: String): List<HealthSampleInput> =
        listOfNotNull(
            body.weightKilogram?.let { sample("body:$externalUserId", "weight_kilogram", it, at) },
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
        sourceDevice = "WHOOP",
    )
}
