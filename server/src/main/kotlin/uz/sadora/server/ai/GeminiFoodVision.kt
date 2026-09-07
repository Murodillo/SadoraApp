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
import kotlinx.serialization.json.Json
import uz.sadora.contract.Language
import uz.sadora.contract.FoodScanResult
import uz.sadora.server.config.AiConfig

/** A recognised photo and what the call cost. */
data class VisionAnswer(
    val result: FoodScanResult,
    val model: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
)

/** Reads a photo of a meal. Separate from [AiModel] because the inputs differ. */
interface FoodVision {
    val name: String
    suspend fun recognise(imageBase64: String, mimeType: String, language: Language): VisionAnswer
}

/**
 * Gemini, asked for one JSON object rather than prose.
 *
 * The estimate is deliberately asked for "as photographed" — a portion, not a hundred
 * grams — because that is the number that goes in the diary, and converting a per-100g
 * figure by eye is exactly the guess the screen is trying to save her.
 *
 * `isFood` is a first-class answer. A camera pointed at a table has to be able to say
 * "that is not food"; without it the model obligingly estimates the calories of a table.
 */
class GeminiFoodVision(
    private val client: HttpClient,
    private val config: AiConfig,
) : FoodVision {

    override val name: String get() = config.model

    override suspend fun recognise(
        imageBase64: String,
        mimeType: String,
        language: Language,
    ): VisionAnswer {
        val apiKey = config.apiKey ?: throw ModelUnavailableException("no_key", "No API key configured")

        val response = runCatchingVision {
            client.post("${config.endpoint}/v1beta/models/${config.model}:generateContent") {
                header("x-goog-api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(
                    VisionRequest(
                        systemInstruction = VisionContent(listOf(VisionPart(text = instruction(language)))),
                        contents = listOf(
                            VisionContent(
                                listOf(
                                    VisionPart(text = prompt(language)),
                                    VisionPart(inlineData = VisionBlob(mimeType, imageBase64)),
                                ),
                            ),
                        ),
                        generationConfig = VisionGenerationConfig(
                            temperature = 0.2,
                            maxOutputTokens = MaxOutputTokens,
                            responseMimeType = "application/json",
                            thinkingConfig = VisionThinkingConfig("minimal"),
                        ),
                    ),
                )
            }
        }

        if (!response.status.isSuccess()) {
            throw ModelUnavailableException("http_${response.status.value}", response.bodyAsText().take(200))
        }

        val parsed = runCatching { response.body<VisionResponse>() }
            .getOrElse { throw ModelUnavailableException("decode", it.message ?: "bad response") }

        val text = parsed.candidates.firstOrNull()
            ?.content
            ?.parts
            ?.filterNot { it.thought == true }
            ?.mapNotNull { it.text }
            ?.joinToString("")
            ?.trim()
            .orEmpty()

        if (text.isEmpty()) {
            throw ModelUnavailableException(
                parsed.candidates.firstOrNull()?.finishReason?.lowercase() ?: "empty",
                "no text in response",
            )
        }

        val raw = runCatching { lenient.decodeFromString<VisionJson>(text) }
            .getOrElse { throw ModelUnavailableException("decode", "not the JSON asked for") }

        return VisionAnswer(
            result = raw.toResult(),
            model = config.model,
            promptTokens = parsed.usageMetadata?.promptTokenCount,
            completionTokens = parsed.usageMetadata?.candidatesTokenCount,
        )
    }

    private fun instruction(language: Language): String = """
        You estimate the nutrition of a photographed meal for a health app.

        Rules:
        - Answer with one JSON object and nothing else.
        - Set isFood to false when the photo is not food or drink, and put a short
          sentence in message saying so. Leave the numbers at zero in that case.
        - Estimate the portion as photographed, not per 100 g.
        - confidence is your own 0-100 confidence in the identification.
        - Name the dish in ${language.displayName}, the way it would be said on a menu.
          Uzbek and Central Asian dishes keep their own names.
        - Never guess a brand, never diagnose, never advise.
    """.trimIndent()

    private fun prompt(language: Language): String =
        "Identify this meal and estimate its nutrition. Reply in ${language.displayName} " +
            "with JSON: {\"isFood\":bool,\"dish\":string,\"confidence\":int,\"kcal\":int," +
            "\"proteinG\":int,\"fatG\":int,\"carbsG\":int,\"fibreG\":int,\"sugarG\":int," +
            "\"sodiumMg\":int,\"message\":string}"

    private companion object {
        /** Enough for one small JSON object, and no room for an essay. */
        const val MaxOutputTokens = 400
        val lenient = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}

/** How the language is named to the model. */
private val Language.displayName: String
    get() = when (this) {
        Language.UZ -> "Uzbek"
        Language.RU -> "Russian"
        Language.EN -> "English"
    }

private inline fun <T> runCatchingVision(block: () -> T): T =
    try {
        block()
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        throw ModelUnavailableException("transport", failure.message ?: "request failed")
    }

// ---------------------------------------------------------------- wire types

@Serializable
private data class VisionRequest(
    val systemInstruction: VisionContent,
    val contents: List<VisionContent>,
    val generationConfig: VisionGenerationConfig,
)

@Serializable
private data class VisionContent(val parts: List<VisionPart>)

@Serializable
private data class VisionPart(
    val text: String? = null,
    @SerialName("inline_data") val inlineData: VisionBlob? = null,
    val thought: Boolean? = null,
)

@Serializable
private data class VisionBlob(
    @SerialName("mime_type") val mimeType: String,
    val data: String,
)

@Serializable
private data class VisionGenerationConfig(
    val temperature: Double,
    val maxOutputTokens: Int,
    val responseMimeType: String,
    val thinkingConfig: VisionThinkingConfig? = null,
)

@Serializable
private data class VisionThinkingConfig(val thinkingLevel: String)

@Serializable
private data class VisionResponse(
    val candidates: List<VisionCandidate> = emptyList(),
    val usageMetadata: VisionUsage? = null,
)

@Serializable
private data class VisionCandidate(
    val content: VisionContent? = null,
    val finishReason: String? = null,
)

@Serializable
private data class VisionUsage(
    val promptTokenCount: Int? = null,
    val candidatesTokenCount: Int? = null,
)

/**
 * What the model returned, before it is trusted.
 *
 * Every field is optional and every number is clamped: a model asked for JSON usually
 * returns JSON, and "usually" is not something a diary entry can be built on.
 */
@Serializable
private data class VisionJson(
    val isFood: Boolean = true,
    val dish: String = "",
    val confidence: Int = 0,
    val kcal: Int = 0,
    val proteinG: Int = 0,
    val fatG: Int = 0,
    val carbsG: Int = 0,
    val fibreG: Int? = null,
    val sugarG: Int? = null,
    val sodiumMg: Int? = null,
    val message: String? = null,
) {
    fun toResult() = FoodScanResult(
        isFood = isFood && dish.isNotBlank(),
        dish = dish.trim().take(120),
        confidence = confidence.coerceIn(0, 100),
        kcal = kcal.coerceIn(0, 5000),
        proteinG = proteinG.coerceIn(0, 500),
        fatG = fatG.coerceIn(0, 500),
        carbsG = carbsG.coerceIn(0, 1000),
        fibreG = fibreG?.coerceIn(0, 200),
        sugarG = sugarG?.coerceIn(0, 500),
        sodiumMg = sodiumMg?.coerceIn(0, 20_000),
        message = message?.trim()?.take(200)?.takeIf { it.isNotEmpty() },
    )
}
