package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.MetricMapping
import uz.sadora.server.wearable.SampleNormalizer
import uz.sadora.server.wearable.oura.OuraActivity
import uz.sadora.server.wearable.oura.OuraMapper
import uz.sadora.server.wearable.oura.OuraPage
import uz.sadora.server.wearable.oura.OuraReadiness
import uz.sadora.server.wearable.oura.OuraSleep

/** Oura records become SADORA samples on the same terms as WHOOP's: the night is the morning's. */
class OuraMapperTest {

    private val tashkent = TimeZone.of("Asia/Tashkent")
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a sleep page from the API is read, and the night lands on the morning she woke`() {
        // Trimmed from the shape the API documents; the unknown fields must not trip it.
        val page = json.decodeFromString<OuraPage<OuraSleep>>(
            """
            {"data":[{"id":"s1","day":"2026-09-04","type":"long_sleep",
              "bedtime_start":"2026-09-03T23:00:00+05:00","bedtime_end":"2026-09-04T07:00:00+05:00",
              "total_sleep_duration":27000,"deep_sleep_duration":5400,"rem_sleep_duration":6000,
              "light_sleep_duration":15600,"awake_time":1800,"efficiency":93,"average_hrv":48,
              "lowest_heart_rate":52,"average_breath":14.5,"heart_rate":null,"low_battery_alert":false}],
             "next_token":null}
            """,
        )
        val samples = OuraMapper.fromSleep(page.data.single())
        val total = samples.first { it.metric == "total_sleep_duration" }

        val mapping = MetricMapping(HealthProvider.OURA, "total_sleep_duration", HealthMetric.SLEEP_DURATION, "s", 0.0166666667)
        val normalized = SampleNormalizer.normalize(total, mapOf((HealthProvider.OURA to "total_sleep_duration") to mapping), tashkent)!!
        assertEquals(450.0, normalized.value, 0.01)
        assertEquals("2026-09-04", normalized.localDate.toString())
        assertEquals(48.0, samples.first { it.metric == "average_hrv" }.value)
        assertEquals(52.0, samples.first { it.metric == "lowest_heart_rate" }.value)
    }

    @Test
    fun `a nap or a short rest is not the night`() {
        val nap = OuraSleep(
            id = "n1", day = "2026-09-04", type = "late_nap",
            bedtimeStart = "2026-09-04T14:00:00+05:00", bedtimeEnd = "2026-09-04T15:00:00+05:00",
            totalSleepDuration = 3000,
        )
        assertTrue(OuraMapper.fromSleep(nap).isEmpty())
        assertTrue(OuraMapper.fromSleep(nap.copy(type = "sleep")).isEmpty())
    }

    @Test
    fun `readiness and activity land on their day with stable ids`() {
        val readiness = OuraReadiness(id = "r1", day = "2026-09-04", score = 81, timestamp = "2026-09-04T00:00:00+05:00")
        val activity = OuraActivity(
            id = "a1", day = "2026-09-04", steps = 8421, activeCalories = 390,
            equivalentWalkingDistance = 6200, timestamp = "2026-09-04T04:00:00+05:00",
        )
        val score = OuraMapper.fromReadiness(readiness).single()
        assertEquals("readiness:r1:readiness_score", score.externalId)
        assertEquals(81.0, score.value)
        val steps = OuraMapper.fromActivity(activity).first { it.metric == "steps" }
        assertEquals(8421.0, steps.value)
        assertEquals("activity:a1:steps", steps.externalId)
    }
}
