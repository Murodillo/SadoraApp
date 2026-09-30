package uz.sadora.app.data.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.CervicalMucusRecord
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.IntermenstrualBleedingRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OvulationTestRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.SexualActivityRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WheelchairPushesRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Velocity
import androidx.health.connect.client.units.Volume
import kotlin.time.Duration.Companion.days
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
 * write, read in one place — every type the store holds, since 2026-09-30.
 *
 * Totals (steps, distance, energy, heart rate) are asked for as daily aggregates rather
 * than raw records. Two apps on one phone often both write steps, and Health Connect's
 * aggregation applies the user's own app priority to them — summing the raw records
 * would count every step twice.
 *
 * The client library needs Android 8, while the app installs on 7; every entry point
 * checks the version before a Health Connect class is touched.
 */
// Mindfulness sessions are still marked experimental in the 1.1.0 client; they are read
// only where the store reports the feature, and a change in the API breaks the build here
// rather than a reading at runtime.
@OptIn(androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi::class)
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
        if (client.feature(HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION)) {
            base += HealthPermission.getReadPermission(MindfulnessSessionRecord::class)
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

    override suspend fun hasFullAccess(): Boolean {
        if (availability() != HealthAvailability.AVAILABLE) return false
        return granted().containsAll(requestedPermissions())
    }

    override fun openPermissionSettings() {
        // Android 14 moved Health Connect into the system settings; before that it is an app.
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            "android.health.connect.action.MANAGE_HEALTH_PERMISSIONS"
        } else {
            "androidx.health.ACTION_MANAGE_HEALTH_PERMISSIONS"
        }
        val page = Intent(action).putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(page) }.onFailure {
            runCatching {
                context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    /** How many of the asked-for permissions are granted, of how many: what the gate shows. */
    override suspend fun grantedCount(): Pair<Int, Int> {
        val asked = requestedPermissions()
        return granted().count { it in asked } to asked.size
    }

    private suspend fun granted(): Set<String> =
        client?.let { runCatching { it.permissionController.getGrantedPermissions() }.getOrNull() }.orEmpty()

    override suspend fun read(samplesFrom: Instant, flowFrom: Instant, to: Instant, zone: TimeZone): HealthReading =
        withContext(Dispatchers.IO) {
            val client = client ?: return@withContext HealthReading(emptyList(), emptySet())
            val granted = granted()
            val reader = Reader(client, granted, ZoneId.of(zone.id), zone, client.feature(HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION))
            HealthReading(
                samples = reader.dailyTotals(samplesFrom, to) +
                    reader.measurements(samplesFrom, to) +
                    reader.mindfulness(samplesFrom, to) +
                    reader.sleep(samplesFrom, to),
                flowDays = reader.flowDays(flowFrom, to),
            ).also {
                // What a read found, for adb logcat: a store that holds nothing looks exactly
                // like one that failed to read, and only this line tells them apart.
                Log.i(LogTag, "read $samplesFrom..$to: ${it.samples.size} samples, ${it.flowDays.size} flow days, ${granted.size} permissions")
            }
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

    override suspend fun isWriterInstalled(writer: HealthProvider): Boolean {
        val pkg = WriterPackages[writer] ?: return false
        return runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
    }

    override suspend fun isWriterWriting(writer: HealthProvider): Boolean {
        val pkg = WriterPackages[writer] ?: return false
        val pm = context.packageManager
        return WriterSignals.any { pm.checkPermission(it, pkg) == android.content.pm.PackageManager.PERMISSION_GRANTED }
    }

    override fun openWriter(writer: HealthProvider) {
        val pkg = WriterPackages[writer] ?: return
        val installed = runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
        if (installed) {
            // Health Connect's own page for that app, where "Allow all" lets it write.
            // Android 14 moved it into the system; before that it lives in the HC app.
            val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                "android.health.connect.action.MANAGE_HEALTH_PERMISSIONS"
            } else {
                "androidx.health.ACTION_MANAGE_HEALTH_PERMISSIONS"
            }
            val page = Intent(action).putExtra(Intent.EXTRA_PACKAGE_NAME, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(page) }.isSuccess) return
        }
        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).setPackage("com.android.vending")
        runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure {
            val web = Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
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
        /** Mindfulness sessions exist only on a Health Connect new enough to have them. */
        private val mindfulness: Boolean,
    ) {
        private fun allowed(type: KClass<out Record>) = HealthPermission.getReadPermission(type) in granted

        /**
         * One aggregate per day and metric, each already in SADORA's unit. Asked for a year
         * at a time: a whole history in one request is thousands of buckets, more than
         * Health Connect answers in one go.
         */
        suspend fun dailyTotals(from: Instant, to: Instant): List<HealthSampleInput> {
            val metrics = buildMap<String, Pair<AggregateMetric<*>, (Any) -> Double?>> {
                fun total(type: KClass<out Record>, name: String, metric: AggregateMetric<*>, convert: (Any) -> Double?) {
                    if (allowed(type)) put(name, metric to convert)
                }
                total(StepsRecord::class, "Steps", StepsRecord.COUNT_TOTAL, ::count)
                total(DistanceRecord::class, "Distance", DistanceRecord.DISTANCE_TOTAL) { (it as? Length)?.inMeters }
                // Kilojoules for the one mapping that has always been in them (V10).
                total(ActiveCaloriesBurnedRecord::class, "ActiveCaloriesBurned", ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL) { (it as? Energy)?.inKilojoules }
                total(HeartRateRecord::class, "HeartRate", HeartRateRecord.BPM_AVG, ::count)
                total(FloorsClimbedRecord::class, "FloorsClimbed", FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL, ::count)
                total(ElevationGainedRecord::class, "ElevationGained", ElevationGainedRecord.ELEVATION_GAINED_TOTAL) { (it as? Length)?.inMeters }
                total(ExerciseSessionRecord::class, "ExerciseSession", ExerciseSessionRecord.EXERCISE_DURATION_TOTAL, ::minutes)
                total(WheelchairPushesRecord::class, "WheelchairPushes", WheelchairPushesRecord.COUNT_TOTAL, ::count)
                total(SpeedRecord::class, "Speed", SpeedRecord.SPEED_AVG) { (it as? Velocity)?.inMetersPerSecond }
                total(PowerRecord::class, "Power", PowerRecord.POWER_AVG) { (it as? androidx.health.connect.client.units.Power)?.inWatts }
                total(StepsCadenceRecord::class, "StepsCadence", StepsCadenceRecord.RATE_AVG, ::count)
                total(CyclingPedalingCadenceRecord::class, "CyclingPedalingCadence", CyclingPedalingCadenceRecord.RPM_AVG, ::count)
                total(HydrationRecord::class, "Hydration", HydrationRecord.VOLUME_TOTAL) { (it as? Volume)?.inMilliliters }
                total(NutritionRecord::class, "NutritionEnergy", NutritionRecord.ENERGY_TOTAL, ::kcal)
                total(NutritionRecord::class, "NutritionProtein", NutritionRecord.PROTEIN_TOTAL, ::grams)
                total(NutritionRecord::class, "NutritionCarbohydrates", NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL, ::grams)
                total(NutritionRecord::class, "NutritionFat", NutritionRecord.TOTAL_FAT_TOTAL, ::grams)
            }
            return yearsBetween(from, to).flatMap { (start, end) ->
                // All metrics in one request, and if the store refuses the set, one by one:
                // a single aggregate a phone's Health Connect does not support used to take
                // every other total of the year down with it.
                val groups = if (metrics.isEmpty()) {
                    emptyList()
                } else {
                    aggregate(metrics.values.map { it.first }.toSet(), start, end)
                        ?: metrics.values.flatMap { (metric, _) -> aggregate(setOf(metric), start, end).orEmpty() }
                }
                totalsOf(groups, metrics) + derived(start, end)
            }
        }

        private fun totalsOf(
            groups: List<androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod>,
            metrics: Map<String, Pair<AggregateMetric<*>, (Any) -> Double?>>,
        ): List<HealthSampleInput> =
            groups.flatMap { group ->
                val day = group.startTime.toLocalDate()
                val at = Instant.fromEpochMilliseconds(group.startTime.atZone(javaZone).toInstant().toEpochMilli())
                metrics.mapNotNull { (name, pair) ->
                    val raw = group.result[pair.first] ?: return@mapNotNull null
                    val value = pair.second(raw) ?: return@mapNotNull null
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

        /**
         * Basal and total energy, each asked for alone and kept only for days some app
         * actually wrote. Health Connect derives both from a default basal rate when nothing
         * was recorded, which put ten years of the same two made-up numbers on a phone that
         * never measured a calorie. A derived day comes back with no data origin.
         */
        private suspend fun derived(from: Instant, to: Instant): List<HealthSampleInput> {
            val wanted = buildMap<String, Pair<AggregateMetric<*>, (Any) -> Double?>> {
                if (allowed(BasalMetabolicRateRecord::class)) put("BasalMetabolicRate", BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL to ::kcal)
                if (allowed(TotalCaloriesBurnedRecord::class)) put("TotalCaloriesBurned", TotalCaloriesBurnedRecord.ENERGY_TOTAL to ::kcal)
            }
            return wanted.flatMap { (name, pair) ->
                val groups = aggregate(setOf(pair.first), from, to).orEmpty()
                    .filter { it.result.dataOrigins.isNotEmpty() }
                totalsOf(groups, mapOf(name to pair))
            }
        }

        private suspend fun aggregate(
            metrics: Set<AggregateMetric<*>>,
            from: Instant,
            to: Instant,
        ): List<androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod>? =
            runCatching {
                client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = metrics,
                        timeRangeFilter = TimeRangeFilter.between(from.local(), to.local()),
                        timeRangeSlicer = Period.ofDays(1),
                    ),
                )
            }.getOrElse { failure ->
                Log.w(LogTag, "daily totals $from..$to (${metrics.size} metrics) failed: ${failure.message}")
                null
            }

        /**
         * Mindful minutes, summed per day from the sessions themselves: Health Connect has an
         * aggregate for them, but not every phone's store answers it.
         */
        suspend fun mindfulness(from: Instant, to: Instant): List<HealthSampleInput> {
            if (!mindfulness) return emptyList()
            return records<MindfulnessSessionRecord>(from, to)
                .groupBy { it.startTime.kotlin().toLocalDateTime(zone).date }
                .map { (day, sessions) ->
                    HealthSampleInput(
                        provider = HealthProvider.HEALTH_CONNECT,
                        externalId = "day:$day:MindfulnessSession",
                        metric = "MindfulnessSession",
                        value = sessions.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMillis() } / 60_000.0,
                        startedAt = sessions.minOf { it.startTime }.kotlin(),
                    )
                }
        }

        /** [from]..[to] cut into spans of at most a year, oldest first. */
        private fun yearsBetween(from: Instant, to: Instant): List<Pair<Instant, Instant>> = buildList {
            var start = from
            while (start < to) {
                val end = minOf(start + 365.days, to)
                add(start to end)
                start = end
            }
        }

        private fun count(value: Any): Double? = (value as? Number)?.toDouble()
        private fun kcal(value: Any): Double? = (value as? Energy)?.inKilocalories
        private fun grams(value: Any): Double? = (value as? Mass)?.inGrams
        private fun minutes(value: Any): Double? = (value as? java.time.Duration)?.toMillis()?.div(60_000.0)

        suspend fun measurements(from: Instant, to: Instant): List<HealthSampleInput> = buildList {
            records<RestingHeartRateRecord>(from, to).forEach { add(sample(it, "RestingHeartRate", it.beatsPerMinute.toDouble(), it.time)) }
            records<HeartRateVariabilityRmssdRecord>(from, to).forEach { add(sample(it, "HeartRateVariabilityRmssd", it.heartRateVariabilityMillis, it.time)) }
            records<RespiratoryRateRecord>(from, to).forEach { add(sample(it, "RespiratoryRate", it.rate, it.time)) }
            records<BodyTemperatureRecord>(from, to).forEach { add(sample(it, "BodyTemperature", it.temperature.inCelsius, it.time)) }
            records<BasalBodyTemperatureRecord>(from, to).forEach { add(sample(it, "BasalBodyTemperature", it.temperature.inCelsius, it.time)) }
            records<OxygenSaturationRecord>(from, to).forEach { add(sample(it, "OxygenSaturation", it.percentage.value, it.time)) }
            records<WeightRecord>(from, to).forEach { add(sample(it, "Weight", it.weight.inKilograms, it.time)) }
            records<Vo2MaxRecord>(from, to).forEach { add(sample(it, "Vo2Max", it.vo2MillilitersPerMinuteKilogram, it.time)) }
            records<HeightRecord>(from, to).forEach { add(sample(it, "Height", it.height.inMeters * 100, it.time)) }
            records<BodyFatRecord>(from, to).forEach { add(sample(it, "BodyFat", it.percentage.value, it.time)) }
            records<LeanBodyMassRecord>(from, to).forEach { add(sample(it, "LeanBodyMass", it.mass.inKilograms, it.time)) }
            records<BodyWaterMassRecord>(from, to).forEach { add(sample(it, "BodyWaterMass", it.mass.inKilograms, it.time)) }
            records<BoneMassRecord>(from, to).forEach { add(sample(it, "BoneMass", it.mass.inKilograms, it.time)) }
            records<BloodGlucoseRecord>(from, to).forEach { add(sample(it, "BloodGlucose", it.level.inMillimolesPerLiter, it.time)) }
            // One reading is two numbers; the externalIds differ by the suffix.
            records<BloodPressureRecord>(from, to).forEach { record ->
                add(sample(record, "BloodPressureSystolic", record.systolic.inMillimetersOfMercury, record.time, "sys"))
                add(sample(record, "BloodPressureDiastolic", record.diastolic.inMillimetersOfMercury, record.time, "dia"))
            }
            // Cycle notes, as the codes the V29 mappings describe.
            records<OvulationTestRecord>(from, to).forEach { record ->
                val code = when (record.result) {
                    OvulationTestRecord.RESULT_POSITIVE -> 3.0
                    OvulationTestRecord.RESULT_HIGH -> 2.0
                    OvulationTestRecord.RESULT_NEGATIVE -> 1.0
                    else -> 0.0
                }
                add(sample(record, "OvulationTest", code, record.time))
            }
            records<CervicalMucusRecord>(from, to).forEach { record ->
                val code = when (record.appearance) {
                    CervicalMucusRecord.APPEARANCE_DRY -> 1.0
                    CervicalMucusRecord.APPEARANCE_STICKY -> 2.0
                    CervicalMucusRecord.APPEARANCE_CREAMY -> 3.0
                    CervicalMucusRecord.APPEARANCE_WATERY -> 4.0
                    CervicalMucusRecord.APPEARANCE_EGG_WHITE -> 5.0
                    CervicalMucusRecord.APPEARANCE_UNUSUAL -> 6.0
                    else -> null
                } ?: return@forEach
                add(sample(record, "CervicalMucus", code, record.time))
            }
            records<IntermenstrualBleedingRecord>(from, to).forEach { add(sample(it, "IntermenstrualBleeding", 1.0, it.time)) }
            records<SexualActivityRecord>(from, to).forEach { add(sample(it, "SexualActivity", 1.0, it.time)) }
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
            // An empty range — the history's older slices ask for no bleeding days — is not
            // a question Health Connect takes: it throws rather than answer nothing.
            if (!allowed(T::class) || from >= to) return emptyList()
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
                }.getOrElse { failure ->
                    Log.w(LogTag, "reading ${T::class.simpleName} failed", failure)
                    return all
                }
                all += response.records
                page = response.pageToken
            } while (page != null)
            return all
        }

        private fun sample(record: Record, metric: String, value: Double, at: java.time.Instant, part: String? = null) = HealthSampleInput(
            provider = HealthProvider.HEALTH_CONNECT,
            externalId = part?.let { "${record.metadata.id}:$it" } ?: record.metadata.id,
            metric = metric,
            value = value,
            startedAt = at.kotlin(),
            sourceDevice = record.metadata.dataOrigin.packageName,
        )

        private fun Instant.local(): LocalDateTime = LocalDateTime.ofInstant(java(), javaZone)
    }

    companion object {
        private const val LogTag = "SadoraHealth"
        const val ProviderPackage = "com.google.android.apps.healthdata"

        /** Apps that write into Health Connect and have their own tile on the devices screen. */
        val WriterPackages = mapOf(HealthProvider.SAMSUNG_HEALTH to "com.sec.android.app.shealth")

        /** Either one granted means the writer's sync is on; neither means nothing will come. */
        val WriterSignals = listOf("android.permission.health.WRITE_STEPS", "android.permission.health.WRITE_SLEEP")

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
            // Everything else Health Connect holds (2026-09-30). Skin temperature and
            // mindfulness are asked for separately, only where the store has them.
            TotalCaloriesBurnedRecord::class,
            BasalMetabolicRateRecord::class,
            FloorsClimbedRecord::class,
            ElevationGainedRecord::class,
            ExerciseSessionRecord::class,
            WheelchairPushesRecord::class,
            SpeedRecord::class,
            PowerRecord::class,
            StepsCadenceRecord::class,
            CyclingPedalingCadenceRecord::class,
            BloodGlucoseRecord::class,
            BloodPressureRecord::class,
            HeightRecord::class,
            BodyFatRecord::class,
            LeanBodyMassRecord::class,
            BodyWaterMassRecord::class,
            BoneMassRecord::class,
            HydrationRecord::class,
            NutritionRecord::class,
            OvulationTestRecord::class,
            CervicalMucusRecord::class,
            IntermenstrualBleedingRecord::class,
            SexualActivityRecord::class,
            Vo2MaxRecord::class,
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
