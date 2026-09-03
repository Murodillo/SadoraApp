package uz.sadora.server.wearable

import kotlin.uuid.Uuid
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import uz.sadora.contract.DailyHealth
import uz.sadora.contract.DailyHealthRange
import uz.sadora.contract.DailyMetric
import uz.sadora.contract.FeatureKeys
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.IngestResult
import uz.sadora.contract.IngestSamplesRequest
import uz.sadora.contract.MetricMapping
import uz.sadora.contract.ProviderStatus
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.core.resolveTimeZone
import uz.sadora.server.health.HealthAccess

/**
 * Ingest and daily aggregation for wearable data.
 *
 * The device does the reading — HealthKit and Health Connect are platform APIs and stay
 * native — and posts batches here. Everything provider-specific stops at the mapping
 * table, so nothing downstream knows or cares which watch a step count came from.
 */
class WearableService(
    private val repository: WearableRepository,
    private val access: HealthAccess,
) {

    suspend fun ingest(userId: Uuid, request: IngestSamplesRequest): IngestResult {
        val user = access.requireWritable(userId, FeatureKeys.WEARABLE_SYNC)
        if (request.samples.isEmpty()) {
            return IngestResult(accepted = 0, updated = 0)
        }
        if (request.samples.size > MAX_BATCH) {
            throw ValidationException("samples", "Bir marta eng ko'pi $MAX_BATCH namuna")
        }

        val zone = resolveTimeZone(request.timezone ?: user.timezone)
        val mappings = repository.mappings().associateBy { it.provider to it.providerMetric }
        val today = now().dayIn(user.timezone)

        var accepted = 0
        var updated = 0
        var rejected = 0
        val unmapped = linkedSetOf<String>()
        val touchedDays = linkedSetOf<LocalDate>()

        request.samples.forEach { input ->
            val normalized = SampleNormalizer.normalize(input, mappings, zone)
            if (normalized == null) {
                unmapped += "${input.provider.name.lowercase()}:${input.metric}"
                rejected++
                return@forEach
            }
            // A watch whose clock is wrong can report tomorrow; those samples would sit
            // in a day the user cannot see and quietly skew it when it arrives.
            if (normalized.localDate > today) {
                rejected++
                return@forEach
            }
            if (repository.upsertSample(userId, normalized)) accepted++ else updated++
            touchedDays += normalized.localDate
        }

        touchedDays.forEach { recomputeDay(userId, it) }

        return IngestResult(
            accepted = accepted,
            updated = updated,
            unmapped = unmapped.toList(),
            rejected = rejected,
            daysAffected = touchedDays.sorted(),
        )
    }

    /**
     * Rebuilds a day from its samples.
     *
     * Recomputed rather than adjusted, because a re-synced sample changes a value in
     * place — an incremented total would drift away from the samples with nothing to
     * catch it.
     */
    private suspend fun recomputeDay(userId: Uuid, date: LocalDate) {
        val samples = repository.samplesOn(userId, date)
        val metrics = samples.groupBy { it.metric }.mapNotNull { (metric, forMetric) ->
            val deduped = SampleNormalizer.deduplicate(forMetric)
            val value = SampleNormalizer.aggregate(metric, deduped) ?: return@mapNotNull null
            DailyMetric(
                metric = metric,
                value = value,
                unit = metric.canonicalUnit,
                sampleCount = deduped.size,
                providers = deduped.map { it.provider }.distinct(),
            )
        }
        repository.replaceAggregates(userId, date, metrics)
    }

    suspend fun daily(userId: Uuid, from: LocalDate, to: LocalDate): DailyHealthRange {
        access.requireUser(userId)
        if (from > to) throw ValidationException("from", "Boshlanish sanasi tugashdan keyin bo'lishi mumkin emas")
        val span = from.daysUntil(to)
        if (span > MAX_RANGE_DAYS) {
            throw ValidationException("to", "Eng ko'pi $MAX_RANGE_DAYS kunlik oraliq")
        }

        val aggregates = repository.aggregatesBetween(userId, from, to)
        val days = (0..span).map { offset ->
            val date = from.plus(offset, DateTimeUnit.DAY)
            DailyHealth(date, aggregates[date].orEmpty())
        }
        return DailyHealthRange(from, to, days)
    }

    suspend fun today(userId: Uuid): DailyHealth {
        val user = access.requireUser(userId)
        val today = now().dayIn(user.timezone)
        return daily(userId, today, today).days.first()
    }

    /** The Data Sources screen. */
    suspend fun sources(userId: Uuid): List<ProviderStatus> {
        access.requireUser(userId)
        return repository.providerStatus(userId)
    }

    suspend fun mappings(): List<MetricMapping> = repository.mappings()

    suspend fun saveMapping(mapping: MetricMapping): MetricMapping {
        if (mapping.providerMetric.isBlank()) {
            throw ValidationException("providerMetric", "Bo'sh bo'lishi mumkin emas")
        }
        if (!mapping.scale.isFinite() || mapping.scale == 0.0) {
            throw ValidationException("scale", "Nol yoki noaniq bo'lishi mumkin emas")
        }
        repository.saveMapping(mapping)
        return mapping
    }

    /** Whether a metric is even worth asking the device for. */
    fun knownMetrics(): List<HealthMetric> = HealthMetric.entries

    private companion object {
        const val MAX_BATCH = 2_000
        const val MAX_RANGE_DAYS = 400
    }
}
