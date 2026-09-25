package uz.sadora.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import uz.sadora.contract.Language
import uz.sadora.server.ai.GeminiFoodVision
import uz.sadora.server.ai.ModelUnavailableException
import uz.sadora.server.config.AiConfig

/**
 * What the scanner does with what the model sends back.
 *
 * A model asked for JSON usually returns JSON, and a diary entry cannot be built on
 * "usually" — so the interesting cases here are the ones where the reply is a 200 and
 * still not an answer: prose instead of an object, numbers that cannot be meant, and a
 * photograph of something that is not food at all.
 */
class GeminiFoodVisionTest {

    private val config = AiConfig(
        apiKey = "test-key",
        model = "gemini-3.6-flash",
        endpoint = "https://models.test",
        timeout = 5.seconds,
        maxOutputTokens = 800,
        inputCostPerMillionMicros = 100_000,
        outputCostPerMillionMicros = 400_000,
    )

    private var lastRequest: HttpRequestData? = null

    private fun vision(
        config: AiConfig = this.config,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): GeminiFoodVision {
        val engine = MockEngine { request ->
            lastRequest = request
            handler(request)
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return GeminiFoodVision(client, config)
    }

    private fun MockRequestHandleScope.json(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    /** Wraps [payload] the way Gemini wraps a JSON answer: as text inside a candidate. */
    private fun MockRequestHandleScope.answering(payload: String) = json(
        """{"candidates":[{"content":{"parts":[{"text":${Json.encodeToString(payload)}}]}}],
            "usageMetadata":{"promptTokenCount":900,"candidatesTokenCount":60}}""",
    )

    private suspend fun sentBody(): String =
        (lastRequest?.body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
            .bytes()
            .decodeToString()

    @Test
    fun `a recognised dish comes back with its estimate and what it cost`() = runTest {
        val vision = vision {
            answering(
                """{"isFood":true,"dish":"Osh","confidence":88,"kcal":620,"proteinG":24,
                    "fatG":28,"carbsG":66,"fibreG":4,"sugarG":3,"sodiumMg":780}""",
            )
        }

        val answer = vision.recognise("aGVsbG8=", "image/jpeg", Language.UZ)

        assertTrue(answer.result.isFood)
        assertEquals("Osh", answer.result.dish)
        assertEquals(620, answer.result.kcal)
        assertEquals(4, answer.result.fibreG)
        assertEquals(900, answer.promptTokens)
        assertEquals(60, answer.completionTokens)
    }

    @Test
    fun `the photo is sent inline, with the language the caller asked for`() = runTest {
        val vision = vision { answering("""{"isFood":true,"dish":"Salad","kcal":200}""") }

        vision.recognise("QUJD", "image/png", Language.RU)

        val body = sentBody()
        assertTrue("\"inline_data\"" in body, "the image must travel as an inline part")
        assertTrue("\"mime_type\":\"image/png\"" in body)
        assertTrue("QUJD" in body)
        assertTrue("Russian" in body, "the model is told which language to name the dish in")
    }

    /**
     * "That is not food" is a real answer, and the common one when a camera is pointed
     * at a table. Without it the model obligingly estimates the calories of a table.
     */
    @Test
    fun `a photo that is not food says so and carries no numbers`() = runTest {
        val vision = vision {
            answering(
                """{"isFood":false,"dish":"","confidence":0,"kcal":0,"proteinG":0,"fatG":0,
                    "carbsG":0,"message":"Rasmda ovqat ko'rinmadi."}""",
            )
        }

        val result = vision.recognise("aGVsbG8=", "image/jpeg", Language.UZ).result

        assertFalse(result.isFood)
        assertEquals(0, result.kcal)
        assertEquals("Rasmda ovqat ko'rinmadi.", result.message)
    }

    /** A named dish with no `isFood` at all is still a dish; a blank name is not. */
    @Test
    fun `a nameless answer is not treated as food`() = runTest {
        val vision = vision { answering("""{"isFood":true,"dish":"   ","kcal":300}""") }
        assertFalse(vision.recognise("aGVsbG8=", "image/jpeg", Language.EN).result.isFood)
    }

    /**
     * The numbers are clamped rather than trusted. A model that answers 90 000 kcal has
     * not made a large estimate, it has made a mistake, and the diary must not take it.
     */
    @Test
    fun `numbers that cannot be meant are clamped`() = runTest {
        val vision = vision {
            answering(
                """{"isFood":true,"dish":"X","confidence":410,"kcal":90000,"proteinG":-5,
                    "fatG":0,"carbsG":0,"sodiumMg":999999}""",
            )
        }

        val result = vision.recognise("aGVsbG8=", "image/jpeg", Language.EN).result

        assertEquals(100, result.confidence)
        assertEquals(5000, result.kcal)
        assertEquals(0, result.proteinG)
        assertEquals(20_000, result.sodiumMg)
    }

    @Test
    fun `prose instead of an object fails rather than being half-read`() = runTest {
        val vision = vision { answering("Bu osh, taxminan 600 kkal.") }
        val failure = assertFailsWith<ModelUnavailableException> {
            vision.recognise("aGVsbG8=", "image/jpeg", Language.UZ)
        }
        assertEquals("decode", failure.code)
    }

    @Test
    fun `a blocked candidate fails with the reason the model gave`() = runTest {
        val vision = vision { json("""{"candidates":[{"finishReason":"SAFETY"}]}""") }
        val failure = assertFailsWith<ModelUnavailableException> {
            vision.recognise("aGVsbG8=", "image/jpeg", Language.UZ)
        }
        assertEquals("safety", failure.code)
    }

    @Test
    fun `an http failure carries its status into the log`() = runTest {
        val vision = vision { respondError(HttpStatusCode.TooManyRequests) }
        val failure = assertFailsWith<ModelUnavailableException> {
            vision.recognise("aGVsbG8=", "image/jpeg", Language.UZ)
        }
        assertEquals("http_429", failure.code)
    }

    @Test
    fun `no key means the call is never made`() = runTest {
        val vision = vision(config.copy(apiKey = null)) { json("{}") }
        val failure = assertFailsWith<ModelUnavailableException> {
            vision.recognise("aGVsbG8=", "image/jpeg", Language.UZ)
        }
        assertEquals("no_key", failure.code)
        assertNull(lastRequest)
    }
}
