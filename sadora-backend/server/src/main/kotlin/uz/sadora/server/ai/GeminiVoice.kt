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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uz.sadora.contract.Language
import uz.sadora.server.config.AiConfig

/** What the model heard. Every field is untrusted until [uz.sadora.server.voice.VoiceService] checks it. */
@Serializable
data class VoiceHeard(
    val transcript: String = "",
    val understood: Boolean = true,
    val mood: String? = null,
    val symptoms: List<String> = emptyList(),
    val waterMl: Int? = null,
    val medications: List<String> = emptyList(),
    val period: String? = null,
)

data class VoiceAnswer(val heard: VoiceHeard, val model: String, val promptTokens: Int?, val completionTokens: Int?)

/** One option the model may pick from: a symptom key or a medicine id, with its name. */
data class VoiceChoice(val id: String, val name: String)

/** Hears a short recording and writes down what she logged. */
interface VoiceListener {
    val name: String
    suspend fun hear(audioBase64: String, mimeType: String, language: Language, symptoms: List<VoiceChoice>, medications: List<VoiceChoice>): VoiceAnswer
}

/**
 * Gemini, given the audio and the lists she can log from, asked for one JSON object.
 *
 * The lists are the whole vocabulary: a symptom not in hers is dropped, a medicine she does
 * not take is never "taken". The instruction is about what she said, never what it means.
 */
class GeminiVoice(
    private val client: HttpClient,
    private val config: AiConfig,
) : VoiceListener {

    override val name: String get() = config.model

    override suspend fun hear(
        audioBase64: String,
        mimeType: String,
        language: Language,
        symptoms: List<VoiceChoice>,
        medications: List<VoiceChoice>,
    ): VoiceAnswer {
        val apiKey = config.apiKey ?: throw ModelUnavailableException("no_key", "No API key configured")
        val response = runCatchingVision {
            client.post("${config.endpoint}/v1beta/models/${config.model}:generateContent") {
                header("x-goog-api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(
                    VisionRequest(
                        systemInstruction = VisionContent(listOf(VisionPart(text = instruction(language, symptoms, medications)))),
                        contents = listOf(VisionContent(listOf(VisionPart(text = PROMPT), VisionPart(inlineData = VisionBlob(mimeType, audioBase64.trim()))))),
                        generationConfig = VisionGenerationConfig(
                            temperature = 0.0,
                            maxOutputTokens = MAX_OUTPUT_TOKENS,
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
        val text = parsed.candidates.firstOrNull()?.content?.parts
            ?.filterNot { it.thought == true }
            ?.mapNotNull { it.text }
            ?.joinToString("")
            ?.trim()
            .orEmpty()
        if (text.isEmpty()) throw ModelUnavailableException(parsed.candidates.firstOrNull()?.finishReason?.lowercase() ?: "empty", "no text")
        val heard = runCatching { lenient.decodeFromString<VoiceHeard>(text) }
            .getOrElse { throw ModelUnavailableException("decode", "not the JSON asked for") }
        return VoiceAnswer(heard, config.model, parsed.usageMetadata?.promptTokenCount, parsed.usageMetadata?.candidatesTokenCount)
    }

    private fun instruction(language: Language, symptoms: List<VoiceChoice>, medications: List<VoiceChoice>) = """
        A woman speaks a short note about her day into a health diary app, usually in Uzbek
        (Latin or Cyrillic), sometimes Russian or mixed. Write down what she logged as one JSON
        object and nothing else.

        - transcript: what she said, word for word, in the language she spoke.
        - mood: "bad", "low", "ok", "good" or "great" only when she says how she feels; else null.
        - symptoms: keys from this list only, for what she says she has today:
          ${symptoms.joinToString("; ") { "${it.id} = ${it.name}" }}
        - waterMl: water she says she drank, in millilitres: a glass ("stakan") is 250, a bottle
          ("butilka", "shisha") is 500, a litre is 1000. Null when she did not mention water.
        - medications: ids from this list only, for medicines she says she has taken:
          ${medications.takeIf { it.isNotEmpty() }?.joinToString("; ") { "${it.id} = ${it.name}" } ?: "(none)"}
        - period: "started" when she says her period began today, "ended" when it finished; else null.
        - understood: false when the recording is silent, noise, or not about her day.
        - Never add anything she did not say. Never interpret, diagnose or advise.
        Her app language is ${language.displayName}.
    """.trimIndent()

    private companion object {
        const val PROMPT =
            "Write down what she logged. JSON: {\"transcript\":string,\"understood\":bool,\"mood\":string|null," +
                "\"symptoms\":[string],\"waterMl\":number|null,\"medications\":[string],\"period\":string|null}"
        const val MAX_OUTPUT_TOKENS = 1500
        val lenient = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    }
}
