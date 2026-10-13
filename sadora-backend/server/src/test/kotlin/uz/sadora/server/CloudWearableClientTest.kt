package uz.sadora.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import uz.sadora.server.config.FitbitConfig
import uz.sadora.server.config.GarminConfig
import uz.sadora.server.config.HuaweiConfig
import uz.sadora.server.wearable.fitbit.FitbitClient
import uz.sadora.server.wearable.garmin.GarminClient
import uz.sadora.server.wearable.garmin.Pkce
import uz.sadora.server.wearable.huawei.HuaweiClient

/** What the Garmin, Fitbit and Huawei clients actually put on the wire, against a stand-in for the vendor. */
class CloudWearableClientTest {

    private class Recorded(val request: HttpRequestData, val body: String)

    private fun client(seen: MutableList<Recorded>, answer: (HttpRequestData) -> Pair<HttpStatusCode, String>) =
        HttpClient(
            MockEngine { request ->
                seen += Recorded(request, (request.body as? FormDataContent)?.formData?.toString() ?: String(request.body.toByteArray()))
                val (status, body) = answer(request)
                respond(body, status, headersOf("Content-Type", "application/json"))
            },
        ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

    private val garmin = GarminConfig(
        clientId = "gid", clientSecret = "gsecret", redirectUri = "https://api.test/v1/wearables/garmin/callback",
        authorizeUrl = "https://connect.garmin.test/oauth2Confirm", tokenUrl = "https://connectapi.garmin.test/token",
        apiBaseUrl = "https://apis.garmin.test", webhookToken = null,
    )
    private val fitbit = FitbitConfig(
        clientId = "fid", clientSecret = "fsecret", redirectUri = "https://api.test/v1/wearables/fitbit/callback",
        authorizeUrl = "https://accounts.google.test/auth", tokenUrl = "https://oauth2.google.test/token",
        revokeUrl = "https://oauth2.google.test/revoke", apiBaseUrl = "https://health.google.test", webhookSecret = null,
    )
    private val huawei = HuaweiConfig(
        clientId = "hid", clientSecret = "hsecret", redirectUri = "https://api.test/v1/wearables/huawei/callback",
        authorizeUrl = "https://oauth.huawei.test/authorize", tokenUrl = "https://oauth.huawei.test/token",
        apiBaseUrl = "https://health.huawei.test", scopes = HuaweiClient.DEFAULT_SCOPES, timeZone = "+0500",
        subscriberId = "sub-1", subscriberSecret = null,
    )
    private val to = Instant.parse("2026-09-04T10:00:00Z")

    @Test
    fun `Garmin's consent link carries the challenge of the verifier the exchange then sends`() = runBlocking {
        val seen = mutableListOf<Recorded>()
        val client = GarminClient(
            client(seen) { HttpStatusCode.OK to """{"access_token":"a","refresh_token":"r","expires_in":86400,"token_type":"bearer","scope":"PARTNER_READ"}""" },
            garmin,
        )
        val link = Url(client.authorizeUrl("state-1"))
        assertEquals("S256", link.parameters["code_challenge_method"])
        assertEquals("state-1", link.parameters["state"])

        val tokens = client.exchange("the-code", "state-1")
        assertEquals("r", tokens.refreshToken)
        val form = (seen.single().request.body as FormDataContent).formData
        assertEquals("authorization_code", form["grant_type"])
        assertEquals(link.parameters["code_challenge"], Pkce.challenge(form["code_verifier"]!!))
        assertEquals(garmin.redirectUri, form["redirect_uri"])
    }

    @Test
    fun `Garmin's first pull asks for a backfill and walks the last days in day slices`() = runBlocking {
        val seen = mutableListOf<Recorded>()
        val client = GarminClient(
            client(seen) { request ->
                when {
                    "/backfill/" in request.url.encodedPath -> HttpStatusCode.Accepted to ""
                    request.url.encodedPath.endsWith("/dailies") -> HttpStatusCode.OK to """[{"calendarDate":"2026-09-04","steps":4200,"summaryId":"x"}]"""
                    // A summary type the app was not granted must not sink the rest.
                    request.url.encodedPath.endsWith("/hrv") -> HttpStatusCode.Forbidden to "{}"
                    else -> HttpStatusCode.OK to "[]"
                }
            },
            garmin,
        )
        val samples = client.pull("token", to - 30.days, to, externalUserId = "g-1")

        // The same day came back in both slices; it is one sample twice, not two days.
        assertEquals(setOf("daily:2026-09-04:steps"), samples.map { it.externalId }.toSet())
        assertEquals(4200.0, samples.first().value)
        val backfills = seen.filter { "/backfill/" in it.request.url.encodedPath }
        assertEquals(7, backfills.size)
        assertEquals((to - 30.days).epochSeconds.toString(), backfills.first().request.url.parameters["summaryStartTimeInSeconds"])
        val slices = seen.filter { it.request.url.encodedPath.endsWith("/rest/sleeps") }
        assertEquals(2, slices.size)
        slices.forEach {
            val start = it.request.url.parameters["uploadStartTimeInSeconds"]!!.toLong()
            assertEquals(86_400, it.request.url.parameters["uploadEndTimeInSeconds"]!!.toLong() - start)
            assertEquals("Bearer token", it.request.headers["Authorization"])
        }
    }

    @Test
    fun `Fitbit is asked through Google Health - offline consent, daily roll-ups, dated lists`() = runBlocking {
        val seen = mutableListOf<Recorded>()
        val client = FitbitClient(
            client(seen) { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/identity") -> HttpStatusCode.OK to """{"name":"users/me/identity","healthUserId":"h-1","legacyUserId":"ABC123"}"""
                    path.endsWith("/steps/dataPoints:dailyRollUp") ->
                        HttpStatusCode.OK to """{"rollupDataPoints":[{"civilStartTime":{"date":{"year":2026,"month":9,"day":4}},"steps":{"countSum":"9100"}}]}"""
                    path.endsWith("/daily-resting-heart-rate/dataPoints") ->
                        HttpStatusCode.OK to """{"dataPoints":[{"dailyRestingHeartRate":{"date":{"year":2026,"month":9,"day":4},"beatsPerMinute":"57"}}]}"""
                    path.endsWith("/daily-vo2-max/dataPoints") -> HttpStatusCode.Forbidden to "{}"
                    path.endsWith(":dailyRollUp") -> HttpStatusCode.OK to "{}"
                    else -> HttpStatusCode.OK to """{"dataPoints":[]}"""
                }
            },
            fitbit,
        )
        val link = Url(client.authorizeUrl("state-1"))
        assertEquals("offline", link.parameters["access_type"])
        assertEquals("consent", link.parameters["prompt"])
        assertTrue(link.parameters["scope"]!!.contains("googlehealth.sleep.readonly"))
        assertEquals("h-1", client.externalUserId("token"))

        seen.clear()
        val samples = client.pull("token", to - 2.days, to, externalUserId = "h-1")
        assertEquals(mapOf("steps" to 9100.0, "resting_heart_rate" to 57.0), samples.associate { it.metric to it.value })

        val rollup = seen.first { it.request.url.encodedPath.endsWith("/steps/dataPoints:dailyRollUp") }
        assertEquals(HttpMethod.Post, rollup.request.method)
        assertTrue(""""start":{"date":{"year":2026,"month":9,"day":1}}""" in rollup.body, rollup.body)
        assertTrue(""""end":{"date":{"year":2026,"month":9,"day":6}}""" in rollup.body, rollup.body)
        assertTrue("google-wearables" in rollup.body)
        assertEquals(5, seen.count { it.request.url.encodedPath.endsWith(":dailyRollUp") })

        val resting = seen.first { it.request.url.encodedPath.endsWith("/daily-resting-heart-rate/dataPoints") }
        assertEquals("""daily_resting_heart_rate.date >= "2026-09-01"""", resting.request.url.parameters["filter"])
        val sleep = seen.first { it.request.url.encodedPath.endsWith("/sleep/dataPoints") }
        assertEquals("""sleep.interval.civil_end_time >= "2026-09-01"""", sleep.request.url.parameters["filter"])
        assertEquals(null, sleep.request.url.parameters["dataSourceFamily"])
        assertEquals("25", sleep.request.url.parameters["pageSize"])
        val weight = seen.first { it.request.url.encodedPath.endsWith("/weight/dataPoints") }
        assertEquals("""weight.sample_time.physical_time >= "2026-09-01T10:00:00Z"""", weight.request.url.parameters["filter"])
    }

    @Test
    fun `Huawei is asked in her own days, and a window it refuses is asked again for the last days`() = runBlocking {
        val seen = mutableListOf<Recorded>()
        val client = HuaweiClient(
            client(seen) { request ->
                val path = request.url.encodedPath
                val body = seen.last().body
                when {
                    path.endsWith("/subscriptions") && body.count { it == '$' } > 2 -> HttpStatusCode.Forbidden to "{}"
                    path.endsWith("/subscriptions") -> HttpStatusCode.OK to """[{"openId":"open-1","subscriptionId":"s"}]"""
                    // No history scope: only the last days are answered.
                    path.endsWith(":dailyPolymerize") && "\"startDay\":\"20260903\"" !in body -> HttpStatusCode.Forbidden to "{}"
                    path.endsWith(":dailyPolymerize") && "steps.delta" in body -> HttpStatusCode.OK to
                        """{"group":[{"startTime":1788462000000,"endTime":1788548399999,"sampleSet":[{"samplePoints":[
                            {"dataTypeName":"com.huawei.continuous.steps.total","value":[{"fieldName":"steps","integerValue":9000}]}]}]}]}"""
                    path.endsWith(":dailyPolymerize") && "vo2max" in body -> HttpStatusCode.Forbidden to "{}"
                    path.endsWith(":dailyPolymerize") -> HttpStatusCode.OK to """{"group":[]}"""
                    path.endsWith("/healthRecords") -> HttpStatusCode.OK to """{"healthRecords":[]}"""
                    else -> HttpStatusCode.OK to """{"group":[]}"""
                }
            },
            huawei,
        )
        val link = Url(client.authorizeUrl("state-1"))
        assertEquals("offline", link.parameters["access_type"])
        assertEquals("hid", link.parameters["client_id"])
        val scopes = link.parameters["scope"]!!.split(" ")
        assertEquals("openid", scopes.first())
        assertTrue("https://www.huawei.com/healthkit/sleep.read" in scopes)
        assertTrue("https://www.huawei.com/healthkit/historydata.open.month" in scopes)

        // The events together were refused, so they are asked for one by one; any answer names her.
        assertEquals("open-1", client.externalUserId("token"))
        assertTrue(seen.all { it.request.headers["x-client-id"] == "hid" && "\"subscriberId\":\"sub-1\"" in it.body })

        seen.clear()
        val samples = client.pull("token", to - 30.days, to, externalUserId = "open-1")
        assertEquals(listOf("day:2026-09-04:steps" to 9000.0), samples.map { it.externalId to it.value })

        val dailies = seen.filter { it.request.url.encodedPath.endsWith(":dailyPolymerize") }
        // The first type is asked twice (the month, then the last days); the other nine once.
        assertEquals(11, dailies.size)
        assertTrue(""""startDay":"20260805","endDay":"20260904","timeZone":"+0500"""" in dailies.first().body, dailies.first().body)
        assertTrue(dailies.drop(1).all { """"startDay":"20260903"""" in it.body })
        assertEquals("Bearer token", dailies.first().request.headers["Authorization"])

        val sleep = seen.single { it.request.url.encodedPath.endsWith("/healthRecords") }
        assertEquals("com.huawei.health.record.sleep", sleep.request.url.parameters["dataType"])
        // Midnight of 3 September in Tashkent, in nanoseconds.
        assertEquals("1788375600000000000", sleep.request.url.parameters["startTime"])
        assertEquals("${to.toEpochMilliseconds()}000000", sleep.request.url.parameters["endTime"])
        val hrv = seen.single { it.request.url.encodedPath.endsWith("/sampleSet:polymerize") }
        assertTrue("com.huawei.heart_rate_variability" in hrv.body && "\"startTime\":1788375600000" in hrv.body, hrv.body)
    }
}
