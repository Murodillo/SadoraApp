package uz.sadora.doctor.data.netlog

import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import uz.sadora.doctor.data.SadoraEnvironment
import uz.sadora.doctor.data.createSadoraHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The network inspector sees what the app sent and what came back — and never the
 * secrets: a tester copies these calls into chats.
 */
class NetworkLogTest {

    @Test
    fun `a call is recorded with its body and the caller still reads the answer`() = runTest {
        val log = NetworkLog()
        val client = createSadoraHttpClient(
            SadoraEnvironment("http://test.local"),
            MockEngine { respond("""{"name":"Dr Karimova","patients":12}""", HttpStatusCode.OK, jsonHeaders) },
            log,
        )

        val body = client.post("v1/doctor/notes") { setBody(mapOf("note" to "light")) }.bodyAsText()

        assertEquals("""{"name":"Dr Karimova","patients":12}""", body)
        val call = log.calls.value.single()
        assertEquals("POST", call.method)
        assertEquals("http://test.local/v1/doctor/notes", call.url)
        assertEquals(200, call.status)
        assertTrue("\"light\"" in call.requestBody.orEmpty())
        assertTrue("\"Dr Karimova\"" in call.responseBody.orEmpty())
        assertNotNull(call.durationMs)
    }

    @Test
    fun `the bearer token and the tokens in a body are masked`() = runTest {
        val log = NetworkLog()
        val client = createSadoraHttpClient(
            SadoraEnvironment("http://test.local"),
            MockEngine { respond("""{"accessToken":"a.b.c","refreshToken":"r-1","user":{"id":"u1"}}""", HttpStatusCode.OK, jsonHeaders) },
            log,
        )

        client.post("v1/auth/verify") {
            header(HttpHeaders.Authorization, "Bearer secret-token")
            setBody(mapOf("phone" to "+998900000001", "code" to "123456"))
        }

        val call = log.calls.value.single()
        val auth = call.requestHeaders.first { it.first.equals(HttpHeaders.Authorization, ignoreCase = true) }
        assertEquals(NetworkLog.MASK, auth.second)
        assertFalse("123456" in call.requestBody.orEmpty())
        assertTrue("+998900000001" in call.requestBody.orEmpty())
        assertFalse("a.b.c" in call.responseBody.orEmpty())
        assertFalse("r-1" in call.responseBody.orEmpty())
        assertTrue("u1" in call.responseBody.orEmpty())
    }

    @Test
    fun `a code outside the sign-in routes is kept`() {
        val body = NetworkLog.maskBody("""{"code":"VALIDATION"}""", "http://test.local/v1/meals")
        assertTrue("VALIDATION" in body)
    }

    @Test
    fun `a photo streams past and shows only its size`() = runTest {
        val log = NetworkLog()
        val bytes = ByteArray(2048) { 7 }
        val client = createSadoraHttpClient(
            SadoraEnvironment("http://test.local"),
            MockEngine {
                respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Image.JPEG.toString()))
            },
            log,
        )

        val got = client.get("v1/photos/p1").body<ByteArray>()

        assertEquals(2048, got.size)
        assertEquals("<image/jpeg>", log.calls.value.single().responseBody)
    }

    @Test
    fun `a request that got no answer is kept with the reason`() = runTest {
        val log = NetworkLog()
        val client = createSadoraHttpClient(
            SadoraEnvironment("http://test.local"),
            MockEngine { throw IllegalStateException("Connection refused") },
            log,
        )

        runCatching { client.get("v1/me") }

        val call = log.calls.value.single()
        assertNull(call.status)
        assertEquals("Connection refused", call.error)
        assertFalse(call.inFlight)
    }

    @Test
    fun `a server error is recorded as an answer`() = runTest {
        val log = NetworkLog()
        val client = createSadoraHttpClient(
            SadoraEnvironment("http://test.local"),
            MockEngine { respondError(HttpStatusCode.BadGateway) },
            log,
        )

        client.get("v1/me")

        assertEquals(502, log.calls.value.single().status)
    }

    @Test
    fun `only the newest calls are kept`() = runTest {
        val log = NetworkLog(capacity = 3)
        val client = createSadoraHttpClient(SadoraEnvironment("http://test.local"), MockEngine { respond("{}", HttpStatusCode.OK, jsonHeaders) }, log)

        repeat(5) { client.get("v1/n/$it") }

        assertEquals(listOf("/v1/n/4", "/v1/n/3", "/v1/n/2"), log.calls.value.map { it.url.removePrefix("http://test.local") })
    }

    @Test
    fun `a production build has no inspector and staging has one`() {
        fun graph(environment: SadoraEnvironment) = uz.sadora.doctor.data.DoctorGraph(
            tokenStorage = uz.sadora.doctor.data.InMemoryTokenStorage(),
            device = uz.sadora.doctor.data.FixedDeviceIdentity(),
            environment = environment,
            engine = MockEngine { error("no request is expected") },
        )

        assertNull(graph(SadoraEnvironment.Production).networkLog)
        assertNull(graph(SadoraEnvironment("https://api.sadora.app/")).networkLog)
        assertNotNull(graph(SadoraEnvironment.Stage).networkLog)
    }

    @Test
    fun `production records nothing`() {
        assertFalse(SadoraEnvironment.Production.networkInspector)
        assertTrue(SadoraEnvironment.Stage.networkInspector)
        assertTrue(SadoraEnvironment("http://10.0.2.2:8080", verboseLogging = true).networkInspector)
    }
}

private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
