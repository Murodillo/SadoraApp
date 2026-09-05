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
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import uz.sadora.server.ai.AiContext
import uz.sadora.server.ai.GeminiAnswerer
import uz.sadora.server.ai.ModelUnavailableException
import uz.sadora.server.ai.RuleBasedAnswerer
import uz.sadora.server.config.AiConfig

/**
 * What the answerer does with what Gemini sends back.
 *
 * The gateway is tested elsewhere; this is the layer below it, where a reply is turned
 * into an answer — and where the two ways a 200 can still not be an answer live: a
 * candidate that is only the model thinking aloud, and a candidate blocked before it
 * wrote anything. Both must fail loudly enough for the gateway to fall back, because a
 * blank bubble is worse than a rule-written reply.
 */
class GeminiAnswererTest {

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

    private fun answerer(
        config: AiConfig = this.config,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): GeminiAnswerer {
        val engine = MockEngine { request ->
            lastRequest = request
            handler(request)
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return GeminiAnswerer(client, config)
    }

    private fun MockRequestHandleScope.json(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private suspend fun sentBody(): String =
        (lastRequest?.body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
            .bytes()
            .decodeToString()

    @Test
    fun `an answer keeps the model's text and carries the boundary line`() = runTest {
        val answerer = answerer {
            json(
                """
                {"candidates":[{"content":{"parts":[{"text":"Ko'proq uxlashga harakat qiling."}]}}],
                 "usageMetadata":{"promptTokenCount":120,"candidatesTokenCount":40}}
                """.trimIndent(),
            )
        }

        val answer = answerer.answer("Nega charchayapman?", AiContext(cycleDay = 5, sleepMinutes = 360))

        assertTrue(answer.text.startsWith("Ko'proq uxlashga harakat qiling."))
        assertTrue(RuleBasedAnswerer.DISCLAIMER in answer.text)
        assertEquals("gemini-3.6-flash", answer.model)
        assertEquals(120, answer.promptTokens)
        assertEquals(40, answer.completionTokens)
    }

    @Test
    fun `a disclaimer the model already wrote is not repeated`() = runTest {
        val answerer = answerer {
            json("""{"candidates":[{"content":{"parts":[{"text":"Javob.\n\n${RuleBasedAnswerer.DISCLAIMER}"}]}}]}""")
        }

        val answer = answerer.answer("Savol", null)

        assertEquals(1, answer.text.split(RuleBasedAnswerer.DISCLAIMER).size - 1)
    }

    @Test
    fun `a thought part is reasoning and never reaches her`() = runTest {
        val answerer = answerer {
            json(
                """
                {"candidates":[{"content":{"parts":[
                  {"thought":true,"text":"U charchagan, demak uyqu haqida yozaman"},
                  {"text":"Uyqungiz olti soat — bu kamlik qilishi mumkin."}
                ]}}]}
                """.trimIndent(),
            )
        }

        val answer = answerer.answer("Nega charchayapman?", null)

        assertFalse("demak uyqu haqida yozaman" in answer.text)
        assertTrue(answer.text.startsWith("Uyqungiz olti soat"))
    }

    @Test
    fun `a blocked candidate fails with its reason rather than answering empty`() = runTest {
        val answerer = answerer {
            json("""{"candidates":[{"finishReason":"SAFETY"}]}""")
        }

        val failure = assertFailsWith<ModelUnavailableException> { answerer.answer("Savol", null) }

        assertEquals("safety", failure.code)
    }

    @Test
    fun `an http failure is reported by its status, so the log names the quota`() = runTest {
        val answerer = answerer { respondError(HttpStatusCode.TooManyRequests, "quota exceeded") }

        val failure = assertFailsWith<ModelUnavailableException> { answerer.answer("Savol", null) }

        assertEquals("http_429", failure.code)
    }

    @Test
    fun `no key is a failure before the request, not a request without one`() = runTest {
        val answerer = answerer(config.copy(apiKey = null)) { json("""{"candidates":[]}""") }

        val failure = assertFailsWith<ModelUnavailableException> { answerer.answer("Savol", null) }

        assertEquals("no_key", failure.code)
        assertEquals(null, lastRequest)
    }

    @Test
    fun `the prompt carries her numbers and her question, and nothing else`() = runTest {
        val answerer = answerer {
            json("""{"candidates":[{"content":{"parts":[{"text":"Javob."}]}}]}""")
        }

        answerer.answer("Nega charchayapman?", AiContext(cycleDay = 5, sleepMinutes = 380, waterMl = 1200))

        val body = sentBody()
        assertTrue("Sikl 5-kun" in body)
        assertTrue("Nega charchayapman?" in body)
        // The summary is the whole of what leaves: no user id, no raw rows.
        assertFalse("userId" in body)
    }

    @Test
    fun `without her numbers the model is told to answer in general terms`() = runTest {
        val answerer = answerer {
            json("""{"candidates":[{"content":{"parts":[{"text":"Javob."}]}}]}""")
        }

        answerer.answer("Savol", AiContext())

        assertTrue("ma'lumotlari yo'q" in sentBody())
    }
}
