package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.datetime.TimeZone
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.MetricMapping
import uz.sadora.server.wearable.SampleNormalizer
import uz.sadora.server.wearable.garmin.GarminMapper
import uz.sadora.server.wearable.garmin.GarminPush
import uz.sadora.server.wearable.garmin.Pkce

/** Garmin's pushes become SADORA samples on the same terms as the other wearables'. */
class GarminMapperTest {

    private val tashkent = TimeZone.of("Asia/Tashkent")

    @Test
    fun `a pushed night lands on the morning she woke, under her Garmin id`() {
        // Trimmed from the shape Garmin documents; the unknown fields must not trip it.
        // 1788458400 is 2026-09-03 23:00 in Tashkent; seven and a half hours asleep, half an hour awake.
        val push = assertNotNull(
            GarminPush.parse(
                """
                {"sleeps":[{"userId":"g-1","summaryId":"x-1","calendarDate":"2026-09-04",
                  "startTimeInSeconds":1788458400,"startTimeOffsetInSeconds":18000,"durationInSeconds":27000,
                  "deepSleepDurationInSeconds":5400,"lightSleepDurationInSeconds":15600,"remSleepInSeconds":6000,
                  "awakeDurationInSeconds":1800,"validation":"ENHANCED_FINAL","overallSleepScore":{"value":84,"qualifierKey":"GOOD"}}]}
                """,
            ),
        )
        val samples = GarminMapper.fromPush(push).getValue("g-1")
        val total = samples.first { it.metric == "sleep_duration" }

        val mapping = MetricMapping(HealthProvider.GARMIN, "sleep_duration", HealthMetric.SLEEP_DURATION, "s", 0.0166666667)
        val normalized = SampleNormalizer.normalize(total, mapOf((HealthProvider.GARMIN to "sleep_duration") to mapping), tashkent)!!
        assertEquals(450.0, normalized.value, 0.01)
        assertEquals("2026-09-04", normalized.localDate.toString())
        assertEquals(84.0, samples.first { it.metric == "sleep_score" }.value)
        assertEquals(1800.0, samples.first { it.metric == "awake_duration" }.value)
    }

    @Test
    fun `a day resent as it grows overwrites itself instead of adding up`() {
        val morning = GarminPush.parse("""{"dailies":[{"userId":"g-1","summaryId":"a","calendarDate":"2026-09-04","steps":1200}]}""")!!
        val evening = GarminPush.parse(
            """{"dailies":[{"userId":"g-1","summaryId":"b","calendarDate":"2026-09-04","steps":9100,"distanceInMeters":6400.5,
               "activeKilocalories":410,"restingHeartRateInBeatsPerMinute":58,"averageStressLevel":31}]}""",
        )!!
        val first = GarminMapper.fromPush(morning).getValue("g-1").single()
        val later = GarminMapper.fromPush(evening).getValue("g-1")
        val steps = later.first { it.metric == "steps" }

        assertEquals(first.externalId, steps.externalId)
        assertEquals(9100.0, steps.value)
        assertEquals(setOf("steps", "distance_in_meters", "active_kilocalories", "resting_heart_rate"), later.map { it.metric }.toSet())

        val mapping = MetricMapping(HealthProvider.GARMIN, "steps", HealthMetric.STEPS, "count", 1.0)
        val normalized = SampleNormalizer.normalize(steps, mapOf((HealthProvider.GARMIN to "steps") to mapping), tashkent)!!
        assertEquals("2026-09-04", normalized.localDate.toString())
    }

    @Test
    fun `readings Garmin could not take are left out of the averages`() {
        val push = GarminPush.parse(
            """
            {"pulseox":[{"userId":"g-1","calendarDate":"2026-09-04","onDemand":false,"timeOffsetSpo2Values":{"0":96,"60":94,"120":0}},
                        {"userId":"g-1","calendarDate":"2026-09-04","onDemand":true,"timeOffsetSpo2Values":{"0":80}}],
             "allDayRespiration":[{"userId":"g-1","startTimeInSeconds":1788372000,"durationInSeconds":900,
                                   "timeOffsetEpochToBreaths":{"0":14.0,"60":16.0,"120":-1.0}}],
             "hrv":[{"userId":"g-1","calendarDate":"2026-09-04","lastNightAvg":52}],
             "bodyComps":[{"userId":"g-2","measurementTimeInSeconds":1788400000,"weightInGrams":61500}]}
            """,
        )!!
        val byUser = GarminMapper.fromPush(push)
        val hers = byUser.getValue("g-1")

        assertEquals(95.0, hers.single { it.metric == "spo2_average" }.value)
        assertEquals(15.0, hers.single { it.metric == "respiration_average" }.value)
        assertEquals(52.0, hers.single { it.metric == "hrv_last_night_avg" }.value)
        assertEquals(61500.0, byUser.getValue("g-2").single().value)
    }

    @Test
    fun `a ping and a withdrawn consent are told apart from data`() {
        val push = GarminPush.parse(
            """
            {"dailies":[{"userId":"g-1","callbackURL":"https://apis.garmin.com/wellness-api/rest/dailies?uploadStartTimeInSeconds=1"}],
             "deregistrations":[{"userId":"g-2"}],
             "userPermissionsChange":[{"userId":"g-3","permissions":["ACTIVITY_EXPORT"]},
                                      {"userId":"g-4","permissions":["ACTIVITY_EXPORT","HEALTH_EXPORT"]}]}
            """,
        )!!
        assertTrue(GarminMapper.fromPush(push).isEmpty())
        assertEquals(setOf("g-1"), push.pingedUsers())
        assertEquals(setOf("g-2", "g-3"), push.revokedUsers())
    }

    @Test
    fun `the PKCE verifier can be rebuilt from the state, and only with the secret`() {
        val verifier = Pkce.verifier("secret", "state-1")
        assertEquals(verifier, Pkce.verifier("secret", "state-1"))
        assertEquals(43, verifier.length)
        assertTrue(verifier.all { it.isLetterOrDigit() || it in "-._~" })
        assertNotEquals(verifier, Pkce.verifier("secret", "state-2"))
        assertNotEquals(verifier, Pkce.verifier("other", "state-1"))
        // RFC 7636, appendix B.
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }
}
