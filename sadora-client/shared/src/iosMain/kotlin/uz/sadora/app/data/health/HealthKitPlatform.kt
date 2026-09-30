package uz.sadora.app.data.health

import kotlin.coroutines.resume
import kotlin.time.Instant
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSUserDefaults
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.HealthKit.HKCategorySample
import platform.HealthKit.HKCategoryType
import platform.HealthKit.HKCategoryTypeIdentifierMenstrualFlow
import platform.HealthKit.HKCategoryTypeIdentifierSleepAnalysis
import platform.HealthKit.HKCategoryValueMenstrualFlowNone
import platform.HealthKit.HKCategoryValueSleepAnalysisAsleepCore
import platform.HealthKit.HKCategoryValueSleepAnalysisAsleepDeep
import platform.HealthKit.HKCategoryValueSleepAnalysisAsleepREM
import platform.HealthKit.HKCategoryValueSleepAnalysisAsleepUnspecified
import platform.HealthKit.HKCategoryValueSleepAnalysisAwake
import platform.HealthKit.HKHealthStore
import platform.HealthKit.HKMetricPrefixKilo
import platform.HealthKit.HKMetricPrefixMilli
import platform.HealthKit.HKObjectQueryNoLimit
import platform.HealthKit.HKObjectType
import platform.HealthKit.HKQuantitySample
import platform.HealthKit.HKQuantityType
import platform.HealthKit.HKQuantityTypeIdentifierActiveEnergyBurned
import platform.HealthKit.HKQuantityTypeIdentifierAppleSleepingWristTemperature
import platform.HealthKit.HKQuantityTypeIdentifierBasalBodyTemperature
import platform.HealthKit.HKQuantityTypeIdentifierBodyMass
import platform.HealthKit.HKQuantityTypeIdentifierBodyTemperature
import platform.HealthKit.HKQuantityTypeIdentifierDistanceWalkingRunning
import platform.HealthKit.HKQuantityTypeIdentifierHeartRate
import platform.HealthKit.HKQuantityTypeIdentifierHeartRateVariabilitySDNN
import platform.HealthKit.HKQuantityTypeIdentifierOxygenSaturation
import platform.HealthKit.HKQuantityTypeIdentifierRespiratoryRate
import platform.HealthKit.HKQuantityTypeIdentifierRestingHeartRate
import platform.HealthKit.HKQuantityTypeIdentifierStepCount
import platform.HealthKit.HKQuery
import platform.HealthKit.HKQueryOptionStrictStartDate
import platform.HealthKit.HKSampleQuery
import platform.HealthKit.HKSampleType
import platform.HealthKit.HKStatistics
import platform.HealthKit.HKStatisticsCollectionQuery
import platform.HealthKit.HKStatisticsOptionCumulativeSum
import platform.HealthKit.HKStatisticsOptionDiscreteAverage
import platform.HealthKit.HKStatisticsOptions
import platform.HealthKit.HKUnit
// Class methods HealthKit declares in ObjC categories arrive as extensions to import.
import platform.HealthKit.countUnit
import platform.HealthKit.degreeCelsiusUnit
import platform.HealthKit.gramUnitWithMetricPrefix
import platform.HealthKit.kilocalorieUnit
import platform.HealthKit.meterUnit
import platform.HealthKit.minuteUnit
import platform.HealthKit.percentUnit
import platform.HealthKit.predicateForSamplesWithStartDate
import platform.HealthKit.secondUnitWithMetricPrefix
import platform.HealthKit.unitDividedByUnit
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.HealthSampleInput

/**
 * HealthKit: the iPhone, the Apple Watch, and every app that writes to Health.
 *
 * Totals are daily statistics rather than raw samples: the phone and the watch both
 * record steps, and HealthKit's statistics query merges the sources the way the Health
 * app does, where adding samples would count the same walk twice.
 *
 * Metric names are HealthKit's own identifiers wherever one exists, which is what the
 * server's mapping table is keyed on.
 */
@OptIn(ExperimentalForeignApi::class)
class HealthKitPlatform : HealthPlatform {

    private val store = HKHealthStore()

    override val provider: HealthProvider = HealthProvider.APPLE_HEALTH

    override suspend fun availability(): HealthAvailability =
        if (HKHealthStore.isHealthDataAvailable()) HealthAvailability.AVAILABLE else HealthAvailability.UNSUPPORTED

    override suspend fun hasAccess(): Boolean =
        availability() == HealthAvailability.AVAILABLE && NSUserDefaults.standardUserDefaults.boolForKey(AskedKey)

    /** Shows the Health sheet. True once it has been answered, whatever the answer. */
    suspend fun requestAccess(): Boolean {
        if (availability() != HealthAvailability.AVAILABLE) return false
        val answered = suspendCancellableCoroutine { continuation ->
            store.requestAuthorizationToShareTypes(null, readTypes()) { success, _ ->
                continuation.resume(success)
            }
        }
        if (answered) NSUserDefaults.standardUserDefaults.setBool(true, AskedKey)
        return answered
    }

    private fun readTypes(): Set<HKObjectType> = buildSet {
        (DailySums.keys + DailyAverages.keys + Measurements.map { it.identifier }).forEach { identifier ->
            HKQuantityType.quantityTypeForIdentifier(identifier)?.let(::add)
        }
        HKCategoryType.categoryTypeForIdentifier(HKCategoryTypeIdentifierSleepAnalysis)?.let(::add)
        HKCategoryType.categoryTypeForIdentifier(HKCategoryTypeIdentifierMenstrualFlow)?.let(::add)
    }

    override suspend fun read(samplesFrom: Instant, flowFrom: Instant, to: Instant, zone: TimeZone): HealthReading {
        val samples = buildList {
            DailySums.forEach { (identifier, unit) -> addAll(daily(identifier, unit(), HKStatisticsOptionCumulativeSum, samplesFrom, to, zone)) }
            DailyAverages.forEach { (identifier, unit) -> addAll(daily(identifier, unit(), HKStatisticsOptionDiscreteAverage, samplesFrom, to, zone)) }
            Measurements.forEach { addAll(measurements(it, samplesFrom, to)) }
            addAll(sleep(samplesFrom, to, zone))
        }
        return HealthReading(samples, flowDays(flowFrom, to, zone))
    }

    /** HealthKit has no way for an app to give its permissions back; she does that in Settings. */
    override suspend fun revokeAccess() = Unit

    override fun openStore() = Unit

    // ------------------------------------------------------------------ queries

    private suspend fun daily(
        identifier: String,
        unit: HKUnit,
        options: HKStatisticsOptions,
        from: Instant,
        to: Instant,
        zone: TimeZone,
    ): List<HealthSampleInput> {
        val type = HKQuantityType.quantityTypeForIdentifier(identifier) ?: return emptyList()
        val anchor = from.toLocalDateTime(zone).date.atStartOfDayIn(zone).ns()
        val interval = NSDateComponents().apply { day = 1 }
        val predicate = HKQuery.predicateForSamplesWithStartDate(from.ns(), to.ns(), HKQueryOptionStrictStartDate)

        val stats: List<HKStatistics> = suspendCancellableCoroutine { continuation ->
            val query = HKStatisticsCollectionQuery(type, predicate, options, anchor, interval)
            query.initialResultsHandler = { _, collection, _ ->
                val collected = mutableListOf<HKStatistics>()
                collection?.enumerateStatisticsFromDate(from.ns(), to.ns()) { statistics, _ ->
                    statistics?.let(collected::add)
                }
                continuation.resume(collected)
            }
            store.executeQuery(query)
        }

        return stats.mapNotNull { day ->
            val quantity = if (options == HKStatisticsOptionCumulativeSum) day.sumQuantity() else day.averageQuantity()
            val value = quantity?.doubleValueForUnit(unit) ?: return@mapNotNull null
            val start = day.startDate.instant()
            HealthSampleInput(
                provider = HealthProvider.APPLE_HEALTH,
                // One row per day, so re-reading today replaces the morning's total.
                externalId = "day:${start.toLocalDateTime(zone).date}:$identifier",
                metric = identifier,
                value = value,
                startedAt = start,
            )
        }
    }

    private suspend fun samplesOf(type: HKSampleType, from: Instant, to: Instant): List<Any?> {
        val predicate = HKQuery.predicateForSamplesWithStartDate(from.ns(), to.ns(), HKQueryOptionStrictStartDate)
        return suspendCancellableCoroutine { continuation ->
            val query = HKSampleQuery(type, predicate, HKObjectQueryNoLimit, null) { _, results, _ ->
                continuation.resume(results.orEmpty())
            }
            store.executeQuery(query)
        }
    }

    private suspend fun measurements(measurement: Measurement, from: Instant, to: Instant): List<HealthSampleInput> {
        val type = HKQuantityType.quantityTypeForIdentifier(measurement.identifier) ?: return emptyList()
        return samplesOf(type, from, to).filterIsInstance<HKQuantitySample>().map { sample ->
            HealthSampleInput(
                provider = HealthProvider.APPLE_HEALTH,
                externalId = sample.UUID.UUIDString,
                metric = measurement.identifier,
                value = sample.quantity.doubleValueForUnit(measurement.unit()) * measurement.factor,
                startedAt = sample.startDate.instant(),
                sourceDevice = sample.sourceRevision.source.name,
            )
        }
    }

    private suspend fun sleep(from: Instant, to: Instant, zone: TimeZone): List<HealthSampleInput> {
        val type = HKCategoryType.categoryTypeForIdentifier(HKCategoryTypeIdentifierSleepAnalysis) ?: return emptyList()
        val segments = samplesOf(type, from, to).filterIsInstance<HKCategorySample>().mapNotNull { sample ->
            val stage = when (sample.value) {
                HKCategoryValueSleepAnalysisAsleepUnspecified -> SleepStage.ASLEEP
                HKCategoryValueSleepAnalysisAsleepCore -> SleepStage.LIGHT
                HKCategoryValueSleepAnalysisAsleepDeep -> SleepStage.DEEP
                HKCategoryValueSleepAnalysisAsleepREM -> SleepStage.REM
                HKCategoryValueSleepAnalysisAwake -> SleepStage.AWAKE
                else -> SleepStage.IN_BED
            }
            SleepSegment(
                source = sample.sourceRevision.source.bundleIdentifier.orEmpty(),
                start = sample.startDate.instant(),
                end = sample.endDate.instant(),
                stage = stage,
            )
        }
        return SleepNights.samples(
            SleepNights.from(segments, zone),
            HealthProvider.APPLE_HEALTH,
            SleepMetricNames(
                asleep = HKCategoryTypeIdentifierSleepAnalysis!!,
                deep = "HKCategoryValueSleepAnalysisAsleepDeep",
                rem = "HKCategoryValueSleepAnalysisAsleepREM",
                light = "HKCategoryValueSleepAnalysisAsleepCore",
                awake = "HKCategoryValueSleepAnalysisAwake",
            ),
        )
    }

    private suspend fun flowDays(from: Instant, to: Instant, zone: TimeZone): Set<LocalDate> {
        val type = HKCategoryType.categoryTypeForIdentifier(HKCategoryTypeIdentifierMenstrualFlow) ?: return emptySet()
        return samplesOf(type, from, to)
            .filterIsInstance<HKCategorySample>()
            // "None" is a day she logged as clear, which is the opposite of a period day.
            .filter { it.value != HKCategoryValueMenstrualFlowNone }
            .map { it.startDate.instant().toLocalDateTime(zone).date }
            .toSet()
    }

    /** A discrete reading sent as it is. [factor] converts HealthKit's fraction to a percentage. */
    private class Measurement(val identifier: String, val unit: () -> HKUnit, val factor: Double = 1.0)

    private companion object {
        const val AskedKey = "sadora.healthkit.asked"

        val bpm: () -> HKUnit = { HKUnit.countUnit().unitDividedByUnit(HKUnit.minuteUnit()) }

        // The identifier constants are typed nullable by the bindings but always set.
        val DailySums: Map<String, () -> HKUnit> = mapOf(
            HKQuantityTypeIdentifierStepCount!! to { HKUnit.countUnit() },
            HKQuantityTypeIdentifierDistanceWalkingRunning!! to { HKUnit.meterUnit() },
            HKQuantityTypeIdentifierActiveEnergyBurned!! to { HKUnit.kilocalorieUnit() },
        )

        val DailyAverages: Map<String, () -> HKUnit> = mapOf(
            HKQuantityTypeIdentifierHeartRate!! to bpm,
        )

        val Measurements: List<Measurement> = listOf(
            Measurement(HKQuantityTypeIdentifierRestingHeartRate!!, bpm),
            Measurement(HKQuantityTypeIdentifierHeartRateVariabilitySDNN!!, { HKUnit.secondUnitWithMetricPrefix(HKMetricPrefixMilli) }),
            Measurement(HKQuantityTypeIdentifierRespiratoryRate!!, bpm),
            Measurement(HKQuantityTypeIdentifierOxygenSaturation!!, { HKUnit.percentUnit() }, factor = 100.0),
            Measurement(HKQuantityTypeIdentifierBodyTemperature!!, { HKUnit.degreeCelsiusUnit() }),
            Measurement(HKQuantityTypeIdentifierBasalBodyTemperature!!, { HKUnit.degreeCelsiusUnit() }),
            Measurement(HKQuantityTypeIdentifierAppleSleepingWristTemperature!!, { HKUnit.degreeCelsiusUnit() }),
            Measurement(HKQuantityTypeIdentifierBodyMass!!, { HKUnit.gramUnitWithMetricPrefix(HKMetricPrefixKilo) }),
        )
    }
}

private fun Instant.ns(): NSDate = NSDate.dateWithTimeIntervalSince1970(toEpochMilliseconds() / 1000.0)

private fun NSDate.instant(): Instant = Instant.fromEpochMilliseconds((timeIntervalSince1970 * 1000).toLong())

/** `NSUserDefaults`: the sync state is not a secret, so it stays out of the keychain. */
class IosHealthSyncPrefs : HealthSyncPrefs {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun load(): HealthSyncState = HealthSyncState(
        userId = defaults.stringForKey(KeyUser),
        enabled = defaults.boolForKey(KeyEnabled),
        lastSyncAt = defaults.doubleForKey(KeyLastSync).takeIf { it > 0 }?.let { Instant.fromEpochMilliseconds(it.toLong()) },
    )

    override suspend fun save(state: HealthSyncState) {
        if (state.userId != null) defaults.setObject(state.userId, KeyUser) else defaults.removeObjectForKey(KeyUser)
        defaults.setBool(state.enabled, KeyEnabled)
        defaults.setDouble(state.lastSyncAt?.toEpochMilliseconds()?.toDouble() ?: 0.0, KeyLastSync)
    }

    private companion object {
        const val KeyUser = "sadora.health.user"
        const val KeyEnabled = "sadora.health.enabled"
        const val KeyLastSync = "sadora.health.lastSyncMs"
    }
}
