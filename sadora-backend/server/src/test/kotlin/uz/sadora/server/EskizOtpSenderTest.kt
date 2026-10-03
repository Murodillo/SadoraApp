package uz.sadora.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import uz.sadora.server.auth.EskizOtpSender
import uz.sadora.server.auth.SmsDeliveryException
import uz.sadora.server.config.EskizConfig

/**
 * What a real Eskiz account cannot be relied on to show: the token is fetched once and
 * kept, a refused token is replaced once rather than forever, and every way the gateway
 * can fail surfaces as the one exception OtpService turns into "try again".
 */
class EskizOtpSenderTest {
    // runBlocking, not runTest: the sender bounds each call with withTimeout, and virtual
    // time would expire it while the mock engine answers on a real thread.

    @Test
    fun `a code goes to the bare number with the approved text and the sender name`(): Unit = runBlocking {
        val seen = mutableListOf<HttpRequestData>()
        val sender = sender(seen) { HttpStatusCode.OK to SENT }

        sender.send("+998901234567", "482913")

        assertEquals(listOf("/api/auth/login", "/api/message/sms/send"), seen.map { it.url.encodedPath })
        val login = form(seen[0])
        assertEquals("ops@sadora.uz", login["email"])
        val sms = seen[1]
        assertEquals("Bearer token-1", sms.headers["Authorization"])
        val fields = form(sms)
        assertEquals("998901234567", fields["mobile_phone"])
        assertEquals("Sadora: kirish kodingiz 482913.", fields["message"])
        assertEquals("4546", fields["from"])
    }

    @Test
    fun `the token is kept between codes`(): Unit = runBlocking {
        val seen = mutableListOf<HttpRequestData>()
        val sender = sender(seen) { HttpStatusCode.OK to SENT }

        sender.send("+998901234567", "111111")
        sender.send("+998935554433", "222222")

        assertEquals(1, seen.count { it.url.encodedPath == "/api/auth/login" })
    }

    @Test
    fun `an expired token is replaced once and the same code goes out`(): Unit = runBlocking {
        val seen = mutableListOf<HttpRequestData>()
        var sends = 0
        val sender = sender(seen) {
            sends++
            if (sends == 1) HttpStatusCode.Unauthorized to """{"message":"Expired"}""" else HttpStatusCode.OK to SENT
        }

        sender.send("+998901234567", "333333")

        assertEquals(
            listOf("/api/auth/login", "/api/message/sms/send", "/api/auth/login", "/api/message/sms/send"),
            seen.map { it.url.encodedPath },
        )
        assertEquals("Bearer token-2", seen.last().headers["Authorization"])
        assertEquals("Sadora: kirish kodingiz 333333.", form(seen.last())["message"])
    }

    @Test
    fun `a token refused twice is a failed send, not a loop`(): Unit = runBlocking {
        val seen = mutableListOf<HttpRequestData>()
        val sender = sender(seen) { HttpStatusCode.Unauthorized to """{"message":"Expired"}""" }

        assertFailsWith<SmsDeliveryException> { sender.send("+998901234567", "444444") }
        assertEquals(4, seen.size)
    }

    @Test
    fun `a text Eskiz will not send is a failed send`(): Unit = runBlocking {
        val sender = sender(mutableListOf()) {
            HttpStatusCode.BadRequest to """{"message":"Этот смс текст еще не прошел модерацию"}"""
        }
        assertFailsWith<SmsDeliveryException> { sender.send("+998901234567", "555555") }
    }

    @Test
    fun `wrong credentials are a failed send`(): Unit = runBlocking {
        val sender = sender(mutableListOf(), loginStatus = HttpStatusCode.Unauthorized) { HttpStatusCode.OK to SENT }
        assertFailsWith<SmsDeliveryException> { sender.send("+998901234567", "666666") }
    }

    @Test
    fun `an unreachable gateway is a failed send`(): Unit = runBlocking {
        val engine = MockEngine { throw IOException("connection refused") }
        val sender = EskizOtpSender(client(engine), config, TEXT)
        assertFailsWith<SmsDeliveryException> { sender.send("+998901234567", "777777") }
    }

    // ---------------------------------------------------------------- harness

    private val config = EskizConfig("ops@sadora.uz", "secret", from = "4546", baseUrl = "https://eskiz.test")

    private fun sender(
        seen: MutableList<HttpRequestData>,
        loginStatus: HttpStatusCode = HttpStatusCode.OK,
        respondWith: () -> Pair<HttpStatusCode, String>,
    ): EskizOtpSender {
        var logins = 0
        val engine = MockEngine { request ->
            seen += request
            val (status, body) = if (request.url.encodedPath == "/api/auth/login") {
                logins++
                loginStatus to """{"message":"token_generated","data":{"token":"token-$logins"},"token_type":"bearer"}"""
            } else {
                respondWith()
            }
            respond(ByteReadChannel(body), status, headersOf("Content-Type", ContentType.Application.Json.toString()))
        }
        return EskizOtpSender(client(engine), config, TEXT)
    }

    private fun client(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private fun form(request: HttpRequestData): Map<String, String?> =
        (request.body as FormDataContent).formData.let { data -> data.names().associateWith { data[it] } }

    private companion object {
        const val TEXT = "Sadora: kirish kodingiz {code}."
        const val SENT = """{"id":"a1b2","message":"Waiting for SMS provider","status":"waiting"}"""
    }
}
