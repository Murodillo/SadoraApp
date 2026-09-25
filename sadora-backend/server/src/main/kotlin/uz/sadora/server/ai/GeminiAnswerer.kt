package uz.sadora.server.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uz.sadora.contract.Language
import uz.sadora.server.config.AiConfig

/** What a model produced, and what it cost to produce. */
data class ModelAnswer(
    val text: String,
    val model: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
)

/** Raised when the model could not answer; the gateway falls back to the rule engine. */
class ModelUnavailableException(val code: String, message: String) : Exception(message)

/**
 * Produces an answer with a model. The gateway decides whether it is called at all.
 */
interface AiModel {
    val name: String
    suspend fun answer(question: String, context: AiContext?, language: Language): ModelAnswer

    /**
     * A free-form completion, for the one caller that is not a health question: the
     * home screen's greeting.
     *
     * Kept separate from [answer] because [answer] carries the product's whole clinical
     * boundary — the instruction, the disclaimer appended by code — and a two-line
     * compliment needs none of it. A model that cannot do this says so and the caller
     * falls back to its own phrases.
     */
    suspend fun complete(
        instruction: String,
        prompt: String,
        temperature: Double,
        maxOutputTokens: Int,
    ): ModelAnswer = throw ModelUnavailableException("unsupported", "no completion support")
}

/**
 * Google Gemini over its REST API.
 *
 * The prompt carries the same numbers the rule engine reads and nothing else — no raw
 * health rows, no identity, no history — so what leaves the country is a handful of
 * figures and her question. The instruction is in the request rather than baked into a
 * fine-tune because it is a product rule that changes with the product, and it is worth
 * being able to read it here next to the thing it constrains.
 */
class GeminiAnswerer(
    private val client: HttpClient,
    private val config: AiConfig,
) : AiModel {

    override val name: String get() = config.model

    override suspend fun answer(question: String, context: AiContext?, language: Language): ModelAnswer {
        val phrases = AiPhrases.of(language)
        val answer = generate(
            instruction = phrases.instruction(),
            prompt = phrases.userTurn(question, context?.takeUnless { it.isEmpty }?.summary(phrases)),
            temperature = 0.4,
            maxOutputTokens = config.maxOutputTokens,
        )
        return answer.copy(text = withDisclaimer(answer.text, phrases))
    }

    override suspend fun complete(
        instruction: String,
        prompt: String,
        temperature: Double,
        maxOutputTokens: Int,
    ): ModelAnswer = generate(instruction, prompt, temperature, maxOutputTokens)

    /** One request to Gemini, with the response unwrapped and the failures named. */
    private suspend fun generate(
        instruction: String,
        prompt: String,
        temperature: Double,
        maxOutputTokens: Int,
    ): ModelAnswer {
        val apiKey = config.apiKey ?: throw ModelUnavailableException("no_key", "No API key configured")

        val response = runCatchingRequest {
            client.post("${config.endpoint}/v1beta/models/${config.model}:generateContent") {
                header("x-goog-api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(
                    GeminiRequest(
                        systemInstruction = GeminiContent(parts = listOf(GeminiPart(instruction))),
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt)))),
                        generationConfig = GeminiGenerationConfig(
                            temperature = temperature,
                            maxOutputTokens = maxOutputTokens,
                            // Thinking is billed and counted against maxOutputTokens, and
                            // for a four-sentence health answer it bought nothing: with it
                            // on, a reply took 16 seconds and spent 720 tokens thinking
                            // before writing 124. Off, the same question answers in three.
                            thinkingConfig = GeminiThinkingConfig("minimal"),
                        ),
                    ),
                )
            }
        }

        if (!response.status.isSuccess()) {
            // The body can carry a key or a quota message; the code is what is logged.
            throw ModelUnavailableException("http_${response.status.value}", response.bodyAsText().take(200))
        }

        val parsed = runCatching { response.body<GeminiResponse>() }
            .getOrElse { throw ModelUnavailableException("decode", it.message ?: "bad response") }

        // A thought part is the model reasoning aloud, not the answer; it must never be
        // rendered as one.
        val text = parsed.candidates
            .firstOrNull()
            ?.content
            ?.parts
            ?.filterNot { it.thought == true }
            ?.mapNotNull { it.text }
            ?.joinToString("")
            ?.trim()
            .orEmpty()

        // A blocked or empty candidate is not an answer; falling back is better than
        // showing her a blank bubble.
        if (text.isEmpty()) {
            throw ModelUnavailableException(
                parsed.candidates.firstOrNull()?.finishReason?.lowercase() ?: "empty",
                "no text in response",
            )
        }

        return ModelAnswer(
            text = text,
            model = config.model,
            promptTokens = parsed.usageMetadata?.promptTokenCount,
            completionTokens = parsed.usageMetadata?.candidatesTokenCount,
        )
    }

    /**
     * The disclaimer is appended here rather than asked for in the prompt.
     *
     * A model can forget an instruction; the boundary line is a product promise and must
     * be on every answer, so it is added by code that cannot forget — and in the language
     * the answer itself is written in.
     */
    private fun withDisclaimer(text: String, phrases: AiPhrases): String =
        if (phrases.disclaimer in text) text else "$text\n\n${phrases.disclaimer}"
}

/**
 * Like `runCatching`, minus the one thing `runCatching` gets wrong here: it catches
 * `CancellationException` too, which turned the gateway's timeout into a "transport"
 * failure and hid it in the log.
 */
private inline fun <T> runCatchingRequest(block: () -> T): T =
    try {
        block()
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        throw ModelUnavailableException("transport", failure.message ?: "request failed")
    }

// ---------------------------------------------------------------- wire types

@Serializable
private data class GeminiRequest(
    val systemInstruction: GeminiContent,
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig,
)

@Serializable
private data class GeminiContent(val parts: List<GeminiPart>)

@Serializable
private data class GeminiPart(val text: String? = null, val thought: Boolean? = null)

@Serializable
private data class GeminiGenerationConfig(
    val temperature: Double,
    val maxOutputTokens: Int,
    val thinkingConfig: GeminiThinkingConfig? = null,
)

@Serializable
private data class GeminiThinkingConfig(val thinkingLevel: String)

@Serializable
private data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val usageMetadata: GeminiUsage? = null,
)

@Serializable
private data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
)

@Serializable
private data class GeminiUsage(
    @SerialName("promptTokenCount") val promptTokenCount: Int? = null,
    @SerialName("candidatesTokenCount") val candidatesTokenCount: Int? = null,
)
