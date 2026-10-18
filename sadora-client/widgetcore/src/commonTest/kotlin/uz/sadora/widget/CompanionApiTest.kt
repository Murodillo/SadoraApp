package uz.sadora.widget

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class CompanionApiTest {
    private val summaryJson = """
        {"today":"2026-10-16","lifeStage":"cycle","water":{"date":"2026-10-16","waterMl":0,"waterGoalMl":2000},
         "medications":{"date":"2026-10-16"},"premium":true,"discreet":false,"language":"uz"}
    """.trimIndent()

    private val sessionJson = """
        {"tokens":{"accessToken":"new-access","refreshToken":"new-refresh","accessExpiresAt":"2030-01-01T00:00:00Z","refreshExpiresAt":"2030-01-01T00:00:00Z"},
         "user":{"id":"u","name":"T","language":"uz","timezone":"Asia/Tashkent","lifeStage":"cycle","onboardingCompleted":true,"createdAt":"2026-01-01T00:00:00Z"},
         "entitlements":{"tier":"free","features":[],"evaluatedAt":"2026-01-01T00:00:00Z"},"isNewUser":false}
    """.trimIndent()

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf("Content-Type", "application/json"))

    private fun api(secrets: CompanionTokens, handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData): CompanionApi =
        CompanionApi({ "https://api.test/" }, secrets, HttpClient(MockEngine { handler(it) }))

    @Test
    fun `an expired access token is refreshed once and the call retried`() = runTest {
        val secrets = CompanionTokens(MemoryKeyValues()).apply { save("old-access", "old-refresh") }
        val seen = mutableListOf<String>()
        val api = api(secrets) { request ->
            seen += "${request.url.encodedPath} ${request.headers["Authorization"].orEmpty()}"
            when {
                request.url.encodedPath.endsWith("/v1/auth/refresh") -> {
                    assertTrue("old-refresh" in (request.body as TextContent).text)
                    json(sessionJson)
                }
                request.headers["Authorization"] == "Bearer old-access" -> json("{}", HttpStatusCode.Unauthorized)
                else -> json(summaryJson)
            }
        }
        val answer = assertIs<WidgetCall.Ok<*>>(api.summary())
        assertEquals(
            listOf("/v1/widget/summary Bearer old-access", "/v1/auth/refresh ", "/v1/widget/summary Bearer new-access"),
            seen,
        )
        assertEquals("new-refresh", secrets.refresh())
        assertTrue(answer.value is uz.sadora.contract.WidgetSummary)
    }

    @Test
    fun `a refresh token the server no longer knows signs the companion out`() = runTest {
        val secrets = CompanionTokens(MemoryKeyValues()).apply { save("a", "r") }
        val api = api(secrets) { json("{}", HttpStatusCode.Unauthorized) }
        assertEquals(WidgetCall.SignedOut, api.summary())
        assertNull(secrets.refresh())
    }

    @Test
    fun `no connection is offline — and a refused tap is rejected`() = runTest {
        val secrets = CompanionTokens(MemoryKeyValues()).apply { save("a", "r") }
        val offline = api(secrets) { throw io.ktor.utils.io.errors.IOException("no route") }
        assertEquals(WidgetCall.Offline, offline.summary())
        val refused = api(secrets) { json("""{"code":"not_found","message":"x"}""", HttpStatusCode.NotFound) }
        assertEquals(WidgetCall.Rejected, refused.send(PendingAction.Water(250, "c", LocalDate(2026, 10, 16))))
    }

    @Test
    fun `a water tap is posted with its id — a dose to its medicine`() = runTest {
        val secrets = CompanionTokens(MemoryKeyValues()).apply { save("a", "r") }
        val bodies = mutableListOf<String>()
        val api = api(secrets) { request ->
            bodies += "${request.url.encodedPath} ${(request.body as TextContent).text}"
            json("{}")
        }
        api.send(PendingAction.Water(250, "tap-9", LocalDate(2026, 10, 16)))
        api.send(PendingAction.Dose("m1", LocalDate(2026, 10, 16), kotlinx.datetime.LocalTime(8, 0), uz.sadora.contract.DoseStatus.TAKEN))
        assertTrue(bodies[0].startsWith("/v1/nutrition/water ") && "\"clientId\":\"tap-9\"" in bodies[0], bodies[0])
        assertTrue(bodies[1].startsWith("/v1/meds/m1/doses ") && "\"status\":\"taken\"" in bodies[1], bodies[1])
    }

    @Test
    fun `without a session there is nothing to ask`() = runTest {
        val api = api(CompanionTokens(MemoryKeyValues())) { error("no request expected") }
        assertEquals(WidgetCall.SignedOut, api.summary())
    }
}
