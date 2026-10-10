package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.MetricMapping
import uz.sadora.server.wearable.SampleNormalizer
import uz.sadora.server.wearable.fitbit.FitbitMapper
import uz.sadora.server.wearable.fitbit.GoogleHealthNotifications
import uz.sadora.server.wearable.fitbit.GoogleHealthPage
import uz.sadora.server.wearable.fitbit.GoogleHealthRollupPage

/** A Fitbit's records, as the Google Health API returns them, become SADORA samples. */
class FitbitMapperTest {

    private val tashkent = TimeZone.of("Asia/Tashkent")
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a main sleep lands on the morning she woke, and a nap is not the night`() {
        // The shape the v4 reference gives: 64-bit integers arrive as strings.
        val page = json.decodeFromString<GoogleHealthPage>(
            """
            {"dataPoints":[
              {"name":"users/me/dataTypes/sleep/dataPoints/1","dataSource":{"platform":"FITBIT"},
               "sleep":{"interval":{"startTime":"2026-09-03T18:00:00Z","startUtcOffset":"18000s","endTime":"2026-09-04T02:00:00Z",
                          "civilEndTime":{"date":{"year":2026,"month":9,"day":4},"time":{"hours":7}}},
                        "type":"STAGES","metadata":{"mainSleep":true,"processed":true},
                        "summary":{"minutesAsleep":"450","minutesAwake":"30","minutesInSleepPeriod":"480",
                                   "stagesSummary":[{"type":"DEEP","minutes":"90","count":"4"},{"type":"REM","minutes":"100","count":"5"},
                                                    {"type":"LIGHT","minutes":"260","count":"20"},{"type":"AWAKE","minutes":"30","count":"12"}]}}},
              {"name":"users/me/dataTypes/sleep/dataPoints/2",
               "sleep":{"interval":{"startTime":"2026-09-04T09:00:00Z","endTime":"2026-09-04T09:40:00Z"},
                        "metadata":{"mainSleep":false,"nap":true},"summary":{"minutesAsleep":"35"}}}],
             "nextPageToken":""}
            """,
        )
        val samples = page.dataPoints.flatMap(FitbitMapper::fromDataPoint)

        assertEquals(setOf("minutes_asleep", "minutes_deep", "minutes_rem", "minutes_light", "minutes_awake"), samples.map { it.metric }.toSet())
        val total = samples.first { it.metric == "minutes_asleep" }
        val mapping = MetricMapping(HealthProvider.FITBIT, "minutes_asleep", HealthMetric.SLEEP_DURATION, "min", 1.0)
        val normalized = SampleNormalizer.normalize(total, mapOf((HealthProvider.FITBIT to "minutes_asleep") to mapping), tashkent)!!
        assertEquals(450.0, normalized.value)
        assertEquals("2026-09-04", normalized.localDate.toString())
        assertEquals("sleep:2026-09-04:minutes_asleep", total.externalId)
        assertEquals(90.0, samples.first { it.metric == "minutes_deep" }.value)
    }

    @Test
    fun `day totals sit on her date, and a day the tracker was not worn is left out`() {
        val page = json.decodeFromString<GoogleHealthRollupPage>(
            """
            {"rollupDataPoints":[
              {"civilStartTime":{"date":{"year":2026,"month":9,"day":4},"time":{}},"civilEndTime":{"date":{"year":2026,"month":9,"day":5}},
               "steps":{"countSum":"9100"}},
              {"civilStartTime":{"date":{"year":2026,"month":9,"day":4}},"distance":{"millimetersSum":"6400500"}},
              {"civilStartTime":{"date":{"year":2026,"month":9,"day":4}},"activeEnergyBurned":{"kcalSum":410.5}},
              {"civilStartTime":{"date":{"year":2026,"month":9,"day":4}},"heartRate":{"beatsPerMinuteAvg":71.2,"beatsPerMinuteMin":48}},
              {"civilStartTime":{"date":{"year":2026,"month":9,"day":5}},"steps":{"countSum":"0"}}]}
            """,
        )
        val samples = page.rollupDataPoints.flatMap(FitbitMapper::fromRollup)

        assertEquals(listOf("steps", "distance_millimeters", "active_energy_kcal", "heart_rate_average"), samples.map { it.metric })
        val steps = samples.first()
        assertEquals(9100.0, steps.value)
        assertEquals("day:2026-09-04:steps", steps.externalId)
        val mapping = MetricMapping(HealthProvider.FITBIT, "distance_millimeters", HealthMetric.DISTANCE, "mm", 0.001)
        val distance = SampleNormalizer.normalize(samples[1], mapOf((HealthProvider.FITBIT to "distance_millimeters") to mapping), tashkent)!!
        assertEquals(6400.5, distance.value, 0.001)
        assertEquals("2026-09-04", distance.localDate.toString())
    }

    @Test
    fun `the nightly figures and the scale are read from their own records`() {
        val page = json.decodeFromString<GoogleHealthPage>(
            """
            {"dataPoints":[
              {"dailyRestingHeartRate":{"date":{"year":2026,"month":9,"day":4},"beatsPerMinute":"57"}},
              {"dailyHeartRateVariability":{"date":{"year":2026,"month":9,"day":4},"averageHeartRateVariabilityMilliseconds":48.5,"entropy":2.1}},
              {"dailyOxygenSaturation":{"date":{"year":2026,"month":9,"day":4},"averagePercentage":96.2,"lowerBoundPercentage":93}},
              {"dailyRespiratoryRate":{"date":{"year":2026,"month":9,"day":4},"breathsPerMinute":14.6}},
              {"dailyVo2Max":{"date":{"year":2026,"month":9,"day":4},"vo2Max":38.0,"estimated":true}},
              {"weight":{"sampleTime":{"physicalTime":"2026-09-04T03:10:00Z","utcOffset":"18000s"},"weightGrams":61500}},
              {"bodyFat":{"sampleTime":{"physicalTime":"2026-09-04T03:10:00Z"},"percentage":24.5}},
              {"dailyRestingHeartRate":{"beatsPerMinute":"60"}}]}
            """,
        )
        val byMetric = page.dataPoints.flatMap(FitbitMapper::fromDataPoint).associate { it.metric to it.value }

        assertEquals(
            mapOf(
                "resting_heart_rate" to 57.0, "hrv_average" to 48.5, "spo2_average" to 96.2, "breaths_per_minute" to 14.6,
                "vo2_max" to 38.0, "weight_grams" to 61500.0, "body_fat_percentage" to 24.5,
            ),
            byMetric,
        )
    }

    @Test
    fun `a notification names its users, and the handshake names nobody`() {
        val one = """{"data":{"version":"1","healthUserId":"u-1","operation":"UPSERT","dataType":"steps","intervals":[]}}"""
        val batch = """[$one,{"data":{"healthUserId":"u-2","operation":"DELETE","dataType":"sleep"}},$one]"""

        assertEquals(setOf("u-1"), GoogleHealthNotifications.users(one))
        assertEquals(setOf("u-1", "u-2"), GoogleHealthNotifications.users(batch))
        assertTrue(GoogleHealthNotifications.users("""{"type":"verification"}""")!!.isEmpty())
        assertNull(GoogleHealthNotifications.users("not json"))
    }
}
