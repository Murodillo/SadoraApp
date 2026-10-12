package uz.sadora.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import java.io.IOException
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uz.sadora.server.auth.PlayMobileOtpSender
import uz.sadora.server.auth.SmsDeliveryException
import uz.sadora.server.config.PlayMobileConfig

/**
 * The shape Play Mobile's broker API insists on, and that every way the gateway can fail
 * surfaces as the one exception OtpService turns into "try again".
 */
class PlayMobileOtpSenderTest {
    // runBlocking, not runTest: the sender bounds each call with withTimeout, and virtual
    // time would expire it while the mock engine answers on a real thread.

    @Test
    fun `a code goes to the bare number with the text and the originator`(): Unit = runBlocking {
        val seen = mutableListOf<HttpRequestData>()
        val sender = sender(seen) { HttpStatusCode.OK to "Request is received" }

        sender.send("+998901234567", "482913")

        val request = seen.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/broker-api/send", request.url.encodedPath)
        val basic = Base64.getEncoder().encodeToString("sadora:p{*?~ss".toByteArray())
        assertEquals("Basic $basic", request.headers["Authorization"])
        assertTrue(request.body.contentType!!.match(ContentType.Application.Json))

        val message = message(request)
        assertEquals("998901234567", message["recipient"]!!.jsonPrimitive.content)
        val sms = message["sms"]!!.jsonObject
        assertEquals("3700", sms["originator"]!!.jsonPrimitive.content)
        assertEquals("Sadora: kirish kodingiz 482913.", sms["content"]!!.jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `every send carries its own message id that fits their limit`(): Unit = runBlocking {
        val seen = mutableListOf<HttpRequestData>()
        val sender = sender(seen) { HttpStatusCode.OK to "Request is received" }

        sender.send("+998901234567", "111111")
        sender.send("+998901234567", "111111")

        val ids = seen.map { message(it)["message-id"]!!.jsonPrimitive.content }
        assertNotEquals(ids[0], ids[1])
        assertTrue(ids.all { it.length in 1..40 })
    }

    @Test
    fun `a request Play Mobile will not take is a failed send`(): Unit = runBlocking {
        val sender = sender(mutableListOf()) {
            HttpStatusCode.BadRequest to """{"error-code":401,"error-description":"Originator is not specified"}"""
        }
        assertFailsWith<SmsDeliveryException> { sender.send("+998901234567", "555555") }
    }

    @Test
    fun `wrong credentials are a failed send`(): Unit = runBlocking {
        val sender = sender(mutableListOf()) { HttpStatusCode.Unauthorized to "" }
        assertFailsWith<SmsDeliveryException> { sender.send("+998901234567", "666666") }
    }

    @Test
    fun `an unreachable gateway is a failed send`(): Unit = runBlocking {
        val engine = MockEngine { throw IOException("connection refused") }
        val sender = PlayMobileOtpSender(HttpClient(engine), config, TEXT)
        assertFailsWith<SmsDeliveryException> { sender.send("+998901234567", "777777") }
    }

    // ---------------------------------------------------------------- harness

    private val config = PlayMobileConfig("sadora", "p{*?~ss", originator = "3700", baseUrl = "https://playmobile.test")

    private fun sender(
        seen: MutableList<HttpRequestData>,
        respondWith: () -> Pair<HttpStatusCode, String>,
    ): PlayMobileOtpSender {
        val engine = MockEngine { request ->
            seen += request
            val (status, body) = respondWith()
            respond(ByteReadChannel(body), status, headersOf("Content-Type", ContentType.Text.Plain.toString()))
        }
        return PlayMobileOtpSender(HttpClient(engine), config, TEXT)
    }

    private fun message(request: HttpRequestData): JsonObject =
        Json.parseToJsonElement((request.body as TextContent).text).jsonObject["messages"]!!.jsonArray.single().jsonObject

    private companion object {
        const val TEXT = "Sadora: kirish kodingiz {code}."
    }
}
