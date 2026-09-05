package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import uz.sadora.server.ai.AiContext
import uz.sadora.server.ai.AiGateway
import uz.sadora.server.ai.AiModel
import uz.sadora.server.ai.AiSource
import uz.sadora.server.ai.AiUsageEntry
import uz.sadora.server.ai.AiUsageRecorder
import uz.sadora.server.ai.ModelAnswer
import uz.sadora.server.ai.ModelUnavailableException
import uz.sadora.server.ai.RuleBasedAnswerer
import uz.sadora.server.config.AiConfig

/**
 * The gateway's whole job is what happens when the model is not available, and what an
 * operator can see afterwards. A question always gets an answer — the difference between
 * a model answer and a rule answer belongs in the log, not in an error.
 */
class AiGatewayTest {

    private val userId = Uuid.random()

    private class Recorder : AiUsageRecorder {
        val entries = mutableListOf<AiUsageEntry>()
        override suspend fun record(entry: AiUsageEntry) {
            entries += entry
        }
    }

    private fun config(apiKey: String? = "key", timeoutSeconds: Int = 5) = AiConfig(
        apiKey = apiKey,
        model = "test-model",
        endpoint = "http://localhost",
        timeout = timeoutSeconds.seconds,
        maxOutputTokens = 800,
        inputCostPerMillionMicros = 100_000,
        outputCostPerMillionMicros = 400_000,
    )

    private fun model(block: suspend () -> ModelAnswer) = object : AiModel {
        override val name = "test-model"
        override suspend fun answer(question: String, context: AiContext?): ModelAnswer = block()
    }

    @Test
    fun `a model answer is passed through and its cost recorded`() = runTest {
        val recorder = Recorder()
        val gateway = AiGateway(
            config = config(),
            usage = recorder,
            model = model { ModelAnswer("Model javobi", "test-model", promptTokens = 100, completionTokens = 50) },
        )

        val answer = gateway.answer(userId, "Nega charchayapman?", null, modelAllowed = true)

        assertEquals("Model javobi", answer.text)
        assertEquals(AiSource.MODEL, answer.source)
        val entry = recorder.entries.single()
        assertEquals(AiSource.MODEL, entry.source)
        assertEquals("ok", entry.outcome)
        // 100 in at 0.1 micro each, 50 out at 0.4 — integers throughout, no rounding drift.
        assertEquals(30, entry.costMicros)
    }

    @Test
    fun `a failing model still answers her, and the failure is what gets logged`() = runTest {
        val recorder = Recorder()
        val gateway = AiGateway(
            config = config(),
            usage = recorder,
            model = model { throw ModelUnavailableException("http_429", "quota") },
        )

        val answer = gateway.answer(userId, "Nega charchayapman?", null, modelAllowed = true)

        assertTrue(RuleBasedAnswerer.DISCLAIMER in answer.text, "she gets a real answer, not an error")
        assertEquals(AiSource.FALLBACK, answer.source)
        val entry = recorder.entries.single()
        assertEquals("error", entry.outcome)
        assertEquals("http_429", entry.errorCode)
        assertEquals(0, entry.costMicros, "a failed call is not billed as if it produced tokens")
    }

    /** The bug this pins: runCatching swallowed the cancellation and logged "transport". */
    @Test
    fun `a slow model is recorded as a timeout, not as a transport error`() = runTest {
        val recorder = Recorder()
        val gateway = AiGateway(
            config = config(timeoutSeconds = 1),
            usage = recorder,
            model = model {
                delay(10.seconds)
                ModelAnswer("late", "test-model", null, null)
            },
        )

        val answer = gateway.answer(userId, "Savol", null, modelAllowed = true)

        assertEquals(AiSource.FALLBACK, answer.source)
        assertEquals("timeout", recorder.entries.single().errorCode)
    }

    @Test
    fun `the operator's switch sends the question to the rules without touching the model`() = runTest {
        val recorder = Recorder()
        var called = false
        val gateway = AiGateway(
            config = config(),
            usage = recorder,
            model = model {
                called = true
                ModelAnswer("Model javobi", "test-model", null, null)
            },
        )

        val answer = gateway.answer(userId, "Savol", null, modelAllowed = false)

        assertTrue(!called, "the switch is off, so nothing is spent finding that out")
        assertEquals(AiSource.RULES, answer.source)
        assertEquals(AiSource.RULES, recorder.entries.single().source)
        assertNull(recorder.entries.single().errorCode, "an operator's decision is not a failure")
    }

    @Test
    fun `without a key the rules answer, and the log says why once per call`() = runTest {
        val recorder = Recorder()
        val gateway = AiGateway(
            config = config(apiKey = null),
            usage = recorder,
            model = model { ModelAnswer("unreachable", "test-model", null, null) },
        )

        val answer = gateway.answer(userId, "Savol", null, modelAllowed = true)

        assertEquals(AiSource.RULES, answer.source)
        assertEquals("no_key", recorder.entries.single().errorCode)
        assertTrue(!gateway.modelConfigured)
    }

    @Test
    fun `an unknown token count costs nothing rather than being guessed at`() {
        val config = config()
        assertEquals(0, config.costMicros(null, null))
        assertEquals(10, config.costMicros(100, null))
        assertEquals(20, config.costMicros(null, 50))
    }

    @Test
    fun `context reaches the rules when the model is not used`() = runTest {
        val recorder = Recorder()
        val gateway = AiGateway(config = config(apiKey = null), usage = recorder, model = null)

        val answer = gateway.answer(
            userId,
            "Suv ichishim kerakmi?",
            AiContext(waterMl = 500, waterGoalMl = 2000),
            modelAllowed = true,
        )

        assertTrue("0,5" in answer.text || "suv" in answer.text.lowercase(), answer.text)
    }
}
