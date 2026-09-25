package uz.sadora.server.wearable

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import uz.sadora.contract.DailyMetric
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.MetricMapping
import uz.sadora.contract.ProviderStatus
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.DailyHealthMetrics
import uz.sadora.server.db.HealthSamples
import uz.sadora.server.db.ProviderMetricMappings
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

class WearableRepository {

    // ---------------------------------------------------------------- mappings

    suspend fun mappings(): List<MetricMapping> = dbQuery {
        ProviderMetricMappings.selectAll()
            .orderBy(
                ProviderMetricMappings.provider to SortOrder.ASC,
                ProviderMetricMappings.providerMetric to SortOrder.ASC,
            )
            .mapNotNull { row ->
                val provider = enumFromDb<HealthProvider>(row[ProviderMetricMappings.provider])
                val metric = enumFromDb<HealthMetric>(row[ProviderMetricMappings.metric])
                if (provider == null || metric == null) return@mapNotNull null
                MetricMapping(
                    provider = provider,
                    providerMetric = row[ProviderMetricMappings.providerMetric],
                    metric = metric,
                    providerUnit = row[ProviderMetricMappings.providerUnit],
                    scale = row[ProviderMetricMappings.scale],
                    active = row[ProviderMetricMappings.active],
                )
            }
    }

    suspend fun saveMapping(mapping: MetricMapping): Unit = dbQuery {
        ProviderMetricMappings.upsert(
            ProviderMetricMappings.provider,
            ProviderMetricMappings.providerMetric,
        ) {
            it[provider] = mapping.provider.dbValue()
            it[providerMetric] = mapping.providerMetric
            it[metric] = mapping.metric.dbValue()
            it[providerUnit] = mapping.providerUnit
            it[scale] = mapping.scale
            it[active] = mapping.active
            it[updatedAt] = now().toOffsetDateTime()
        }
    }

    // ---------------------------------------------------------------- samples

    /** Returns true when the row was new, false when a re-sync updated an existing one. */
    suspend fun upsertSample(userId: Uuid, sample: NormalizedSample): Boolean = dbQuery {
        val existed = HealthSamples.selectAll()
            .where {
                (HealthSamples.userId eq userId) and
                    (HealthSamples.provider eq sample.provider.dbValue()) and
                    (HealthSamples.externalId eq sample.externalId)
            }
            .empty()
            .not()

        HealthSamples.upsert(
            HealthSamples.userId,
            HealthSamples.provider,
            HealthSamples.externalId,
        ) {
            it[id] = Uuid.random()
            it[HealthSamples.userId] = userId
            it[provider] = sample.provider.dbValue()
            it[externalId] = sample.externalId
            it[metric] = sample.metric.dbValue()
            it[value] = sample.value
            it[unit] = sample.unit
            it[startedAt] = sample.startedAt.toOffsetDateTime()
            it[endedAt] = sample.endedAt?.toOffsetDateTime()
            it[localDate] = sample.localDate
            it[sourceDevice] = sample.sourceDevice
            it[recordedAt] = now().toOffsetDateTime()
        }
        !existed
    }

    suspend fun samplesOn(userId: Uuid, date: LocalDate): List<NormalizedSample> = dbQuery {
        HealthSamples.selectAll()
            .where { (HealthSamples.userId eq userId) and (HealthSamples.localDate eq date) }
            .mapNotNull { row ->
                val provider = enumFromDb<HealthProvider>(row[HealthSamples.provider])
                val metric = enumFromDb<HealthMetric>(row[HealthSamples.metric])
                if (provider == null || metric == null) return@mapNotNull null
                NormalizedSample(
                    provider = provider,
                    externalId = row[HealthSamples.externalId],
                    metric = metric,
                    value = row[HealthSamples.value],
                    unit = row[HealthSamples.unit],
                    startedAt = row[HealthSamples.startedAt].toKotlinInstant(),
                    endedAt = row[HealthSamples.endedAt]?.toKotlinInstant(),
                    localDate = row[HealthSamples.localDate],
                    sourceDevice = row[HealthSamples.sourceDevice],
                )
            }
    }

    // ---------------------------------------------------------------- aggregates

    /**
     * Replaces a day's aggregates outright.
     *
     * Recomputed from the samples rather than incremented: a re-synced or corrected
     * sample would otherwise leave a total that no longer matches anything, and nothing
     * would ever notice.
     */
    suspend fun replaceAggregates(
        userId: Uuid,
        date: LocalDate,
        metrics: List<DailyMetric>,
    ): Unit = dbQuery {
        DailyHealthMetrics.deleteWhere {
            (DailyHealthMetrics.userId eq userId) and (DailyHealthMetrics.localDate eq date)
        }
        metrics.forEach { daily ->
            DailyHealthMetrics.upsert(
                DailyHealthMetrics.userId,
                DailyHealthMetrics.localDate,
                DailyHealthMetrics.metric,
            ) {
                it[DailyHealthMetrics.userId] = userId
                it[localDate] = date
                it[metric] = daily.metric.dbValue()
                it[value] = daily.value
                it[unit] = daily.unit
                it[sampleCount] = daily.sampleCount
                it[providers] = daily.providers.joinToString(",") { provider -> provider.dbValue() }
                it[updatedAt] = now().toOffsetDateTime()
            }
        }
    }

    suspend fun aggregatesBetween(
        userId: Uuid,
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, List<DailyMetric>> = dbQuery {
        DailyHealthMetrics.selectAll()
            .where {
                (DailyHealthMetrics.userId eq userId) and
                    (DailyHealthMetrics.localDate greaterEq from) and
                    (DailyHealthMetrics.localDate lessEq to)
            }
            .orderBy(DailyHealthMetrics.localDate to SortOrder.ASC)
            .groupBy({ it[DailyHealthMetrics.localDate] }) { row ->
                DailyMetric(
                    metric = enumFromDb(row[DailyHealthMetrics.metric], HealthMetric.STEPS),
                    value = row[DailyHealthMetrics.value],
                    unit = row[DailyHealthMetrics.unit],
                    sampleCount = row[DailyHealthMetrics.sampleCount],
                    providers = row[DailyHealthMetrics.providers]
                        .split(',')
                        .mapNotNull { enumFromDb<HealthProvider>(it.trim()) },
                )
            }
    }

    // ---------------------------------------------------------------- status

    /** The Data Sources screen, and the admin panel's provider page. */
    suspend fun providerStatus(userId: Uuid): List<ProviderStatus> = dbQuery {
        HealthSamples.selectAll()
            .where { HealthSamples.userId eq userId }
            .mapNotNull { row ->
                val provider = enumFromDb<HealthProvider>(row[HealthSamples.provider]) ?: return@mapNotNull null
                val metric = enumFromDb<HealthMetric>(row[HealthSamples.metric])
                Triple(provider, metric, row[HealthSamples.recordedAt].toKotlinInstant())
            }
            .groupBy { it.first }
            .map { (provider, rows) ->
                ProviderStatus(
                    provider = provider,
                    connected = true,
                    lastSampleAt = rows.maxOf { it.third },
                    sampleCount = rows.size.toLong(),
                    metrics = rows.mapNotNull { it.second }.distinct(),
                )
            }
            .sortedByDescending { it.lastSampleAt }
    }

    /** Across all users — the admin panel's provider health page. */
    suspend fun providerTotals(): Map<HealthProvider, Pair<Long, Instant?>> = dbQuery {
        HealthSamples.selectAll()
            .mapNotNull { row ->
                val provider = enumFromDb<HealthProvider>(row[HealthSamples.provider]) ?: return@mapNotNull null
                provider to row[HealthSamples.recordedAt].toKotlinInstant()
            }
            .groupBy({ it.first }) { it.second }
            .mapValues { (_, times) -> times.size.toLong() to times.maxOrNull() }
    }
}
