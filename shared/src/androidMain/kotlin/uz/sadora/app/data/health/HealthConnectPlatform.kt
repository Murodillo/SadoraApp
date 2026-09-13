package uz.sadora.app.data.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.HealthSampleInput

/**
 * Health Connect: what Samsung Health, Mi Fitness, Zepp, Fitbit and the Pixel Watch
 * write, read in one place.
 *
 * Totals (steps, distance, energy, heart rate) are asked for as daily aggregates rather
 * than raw records. Two apps on one phone often both write steps, and Health Connect's
 * aggregation applies the user's own app priority to them — summing the raw records
 * would count every step twice.
 *
 * The client library needs Android 8, while the app installs on 7; every entry point
 * checks the version before a Health Connect class is touched.
 */
class HealthConnectPlatform(context: Context) : HealthPlatform {

    private val context = context.applicationContext

    override val provider: HealthProvider = HealthProvider.HEALTH_CONNECT

    private val client: HealthConnectClient? by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@lazy null
        runCatching { HealthConnectClient.getOrCreate(this.context) }.getOrNull()
    }

    override suspend fun availability(): HealthAvailability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return HealthAvailability.UNSUPPORTED
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UPDATE_REQUIRED
            // Android 9–13 without the app: installable. Older than 9, or a device
            // without Play, the listing will say so itself.
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                HealthAvailability.NOT_INSTALLED
            } else {
                HealthAvailability.UNSUPPORTED
            }
        }
    }

    /** Every permission the sheet asks for, in the order Health Connect lists them. */
    suspend fun requestedPermissions(): Set<String> {
        val base = RecordTypes.map { HealthPermission.getReadPermission(it) }.toMutableSet()
        val client = client ?: return base
        if (client.feature(HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE)) {
            base += HealthPermission.getReadPermission(SkinTemperatureRecord::class)
        }
        // Without it Health Connect hands over only the 30 days before the first grant,
        // which is not enough cycles to import.
        if (client.feature(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY)) {
            base += HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
        }
        return base
    }

    override suspend fun hasAccess(): Boolean {
        if (availability() != HealthAvailability.AVAILABLE) return false
        return granted().any { it in requestedPermissions() }
    }

    private suspend fun granted(): Set<String> =
        client?.let { runCatching { it.permissionController.getGrantedPermissions() }.getOrNull() }.orEmpty()

    override suspend fun read(samplesFrom: Instant, flowFrom: Instant, to: Instant, zone: TimeZone): HealthReading =
        withContext(Dispatchers.IO) {
            val client = client ?: return@withContext HealthReading(emptyList(), emptySet())
            val granted = granted()
            val reader = Reader(client, granted, ZoneId.of(zone.id), zone)
            HealthReading(
                samples = reader.dailyTotals(samplesFrom, to) +
                    reader.measurements(samplesFrom, to) +
                    reader.sleep(samplesFrom, to),
                flowDays = reader.flowDays(flowFrom, to),
            )
        }

    override suspend fun revokeAccess() {
        client?.let { runCatching { it.permissionController.revokeAllPermissions() } }
    }

    override fun openStore() {
        val listing = Uri.parse("market://details?id=$ProviderPackage&url=healthconnect%3A%2F%2Fonboarding")
        val intent = Intent(Intent.ACTION_VIEW, listing)
            .setPackage("com.android.vending")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            val web = Uri.parse("https://play.google.com/store/apps/details?id=$ProviderPackage")
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, web).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    private fun HealthConnectClient.feature(feature: Int): Boolean =
        runCatching { features.getFeatureStatus(feature) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE }
            .getOrDefault(false)

    /** One read, with the permissions she granted. A type she declined is skipped, not an error. */
    private class Reader(
        private val client: HealthConnectClient,
        private val granted: Set<String>,
        private val javaZone: ZoneId,
        private val zone: TimeZone,
    ) {
        private fun allowed(type: KClass<out Record>) = HealthPermission.getReadPermission(type) in granted

        suspend fun dailyTotals(from: Instant, to: Instant): List<HealthSampleInput> {
            val metrics = buildMap<String, AggregateMetric<*>> {
                if (allowed(StepsRecord::class)) put("Steps", StepsRecord.COUNT_TOTAL)
                if (allowed(DistanceRecord::class)) put("Distance", DistanceRecord.DISTANCE_TOTAL)
                if (allowed(ActiveCaloriesBurnedRecord::class)) put("ActiveCaloriesBurned", ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
                if (allowed(HeartRateRecord::class)) put("HeartRate", HeartRateRecord.BPM_AVG)
            }
            if (metrics.isEmpty()) return emptyList()

            val groups = runCatching {
                client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = metrics.values.toSet(),
                        timeRangeFilter = TimeRangeFilter.between(from.local(), to.local()),
                        timeRangeSlicer = Period.ofDays(1),
                    ),
                )
            }.getOrElse { return emptyList() }

            return groups.flatMap { group ->
                val day = group.startTime.toLocalDate()
                val at = Instant.fromEpochMilliseconds(group.startTime.atZone(javaZone).toInstant().toEpochMilli())
                metrics.mapNotNull { (name, metric) ->
                    val value = when (val raw = group.result[metric]) {
                        is Long -> raw.toDouble()
                        is androidx.health.connect.client.units.Length -> raw.inMeters
                        is androidx.health.connect.client.units.Energy -> raw.inKilojoules
                        else -> null
                    } ?: return@mapNotNull null
                    HealthSampleInput(
                        provider = HealthProvider.HEALTH_CONNECT,
                        // One row per day: re-reading today replaces the morning's total
                        // with the evening's instead of adding them.
                        externalId = "day:$day:$name",
                        metric = name,
                        value = value,
                        startedAt = at,
                    )
                }
            }
        }

        suspend fun measurements(from: Instant, to: Instant): List<HealthSampleInput> = buildList {
            records<RestingHeartRateRecord>(from, to).forEach { add(sample(it, "RestingHeartRate", it.beatsPerMinute.toDouble(), it.time)) }
            records<HeartRateVariabilityRmssdRecord>(from, to).forEach { add(sample(it, "HeartRateVariabilityRmssd", it.heartRateVariabilityMillis, it.time)) }
            records<RespiratoryRateRecord>(from, to).forEach { add(sample(it, "RespiratoryRate", it.rate, it.time)) }
            records<BodyTemperatureRecord>(from, to).forEach { add(sample(it, "BodyTemperature", it.temperature.inCelsius, it.time)) }
            records<BasalBodyTemperatureRecord>(from, to).forEach { add(sample(it, "BasalBodyTemperature", it.temperature.inCelsius, it.time)) }
            records<OxygenSaturationRecord>(from, to).forEach { add(sample(it, "OxygenSaturation", it.percentage.value, it.time)) }
            records<WeightRecord>(from, to).forEach { add(sample(it, "Weight", it.weight.inKilograms, it.time)) }
            // A ring or watch records a night as deltas from her own baseline. Only a
            // night with the baseline gives a temperature; a delta alone is not one.
            records<SkinTemperatureRecord>(from, to).forEach { record ->
                val baseline = record.baseline?.inCelsius ?: return@forEach
                if (record.deltas.isEmpty()) return@forEach
                val mean = record.deltas.map { it.delta.inCelsius }.average()
                add(sample(record, "SkinTemperature", baseline + mean, record.endTime))
            }
        }

        suspend fun sleep(from: Instant, to: Instant): List<HealthSampleInput> {
            val segments = records<SleepSessionRecord>(from, to).flatMap { session ->
                val source = session.metadata.dataOrigin.packageName
                if (session.stages.isEmpty()) {
                    listOf(SleepSegment(source, session.startTime.kotlin(), session.endTime.kotlin(), SleepStage.ASLEEP))
                } else {
                    session.stages.mapNotNull { stage ->
                        val kind = when (stage.stage) {
                            SleepSessionRecord.STAGE_TYPE_SLEEPING -> SleepStage.ASLEEP
                            SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStage.LIGHT
                            SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStage.DEEP
                            SleepSessionRecord.STAGE_TYPE_REM -> SleepStage.REM
                            SleepSessionRecord.STAGE_TYPE_AWAKE,
                            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> SleepStage.AWAKE
                            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> null
                            else -> SleepStage.IN_BED
                        } ?: return@mapNotNull null
                        SleepSegment(source, stage.startTime.kotlin(), stage.endTime.kotlin(), kind)
                    }
                }
            }
            return SleepNights.samples(
                SleepNights.from(segments, zone),
                HealthProvider.HEALTH_CONNECT,
                SleepMetricNames(
                    asleep = "SleepSession",
                    deep = "SleepDeep",
                    rem = "SleepRem",
                    light = "SleepLight",
                    awake = "SleepAwake",
                ),
            )
        }

        suspend fun flowDays(from: Instant, to: Instant): Set<LocalDate> = buildSet {
            records<MenstruationFlowRecord>(from, to).forEach { add(it.time.kotlin().toLocalDateTime(zone).date) }
            records<MenstruationPeriodRecord>(from, to).forEach { period ->
                var day = period.startTime.kotlin().toLocalDateTime(zone).date
                val last = period.endTime.kotlin().toLocalDateTime(zone).date
                while (day <= last) {
                    add(day)
                    day = kotlinx.datetime.LocalDate.fromEpochDays(day.toEpochDays() + 1)
                }
            }
        }

        private suspend inline fun <reified T : Record> records(from: Instant, to: Instant): List<T> {
            if (!allowed(T::class)) return emptyList()
            val all = mutableListOf<T>()
            var page: String? = null
            do {
                val response = runCatching {
                    client.readRecords(
                        ReadRecordsRequest(
                            recordType = T::class,
                            timeRangeFilter = TimeRangeFilter.between(from.java(), to.java()),
                            pageToken = page,
                        ),
                    )
                }.getOrElse { return all }
                all += response.records
                page = response.pageToken
            } while (page != null)
            return all
        }

        private fun sample(record: Record, metric: String, value: Double, at: java.time.Instant) = HealthSampleInput(
            provider = HealthProvider.HEALTH_CONNECT,
            externalId = record.metadata.id,
            metric = metric,
            value = value,
            startedAt = at.kotlin(),
            sourceDevice = record.metadata.dataOrigin.packageName,
        )

        private fun Instant.local(): LocalDateTime = LocalDateTime.ofInstant(java(), javaZone)
    }

    companion object {
        const val ProviderPackage = "com.google.android.apps.healthdata"

        /** Must match the `android.permission.health.READ_*` list in AndroidManifest.xml. */
        private val RecordTypes: List<KClass<out Record>> = listOf(
            StepsRecord::class,
            DistanceRecord::class,
            ActiveCaloriesBurnedRecord::class,
            HeartRateRecord::class,
            RestingHeartRateRecord::class,
            HeartRateVariabilityRmssdRecord::class,
            RespiratoryRateRecord::class,
            OxygenSaturationRecord::class,
            BodyTemperatureRecord::class,
            BasalBodyTemperatureRecord::class,
            WeightRecord::class,
            SleepSessionRecord::class,
            MenstruationFlowRecord::class,
            MenstruationPeriodRecord::class,
        )
    }
}

private fun Instant.java(): java.time.Instant = java.time.Instant.ofEpochMilli(toEpochMilliseconds())

private fun java.time.Instant.kotlin(): Instant = Instant.fromEpochMilliseconds(toEpochMilli())

/** Remembers the sync state in ordinary preferences; nothing here is secret. */
class AndroidHealthSyncPrefs(context: Context) : HealthSyncPrefs {

    private val preferences = context.getSharedPreferences("sadora_health_sync", Context.MODE_PRIVATE)

    override suspend fun load(): HealthSyncState = withContext(Dispatchers.IO) {
        HealthSyncState(
            userId = preferences.getString(KeyUser, null),
            enabled = preferences.getBoolean(KeyEnabled, false),
            lastSyncAt = preferences.getLong(KeyLastSync, 0L).takeIf { it > 0 }?.let(Instant::fromEpochMilliseconds),
        )
    }

    override suspend fun save(state: HealthSyncState) = withContext(Dispatchers.IO) {
        preferences.edit()
            .putString(KeyUser, state.userId)
            .putBoolean(KeyEnabled, state.enabled)
            .putLong(KeyLastSync, state.lastSyncAt?.toEpochMilliseconds() ?: 0L)
            .apply()
    }

    private companion object {
        const val KeyUser = "user_id"
        const val KeyEnabled = "enabled"
        const val KeyLastSync = "last_sync_ms"
    }
}
