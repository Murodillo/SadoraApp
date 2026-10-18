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
import uz.sadora.contract.LabPhotoUpload
import uz.sadora.server.config.AiConfig

/** What the model read off a lab sheet, before the service checks it. */
data class LabVisionAnswer(
    val sheet: LabVisionSheet,
    val model: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
)

/** A sheet as the model wrote it down. Every field is untrusted until [uz.sadora.server.lab.LabService] cleans it. */
@Serializable
data class LabVisionSheet(
    val isLabReport: Boolean = true,
    /** YYYY-MM-DD, or null when the sheet shows no date. */
    val takenOn: String? = null,
    val place: String? = null,
    val values: List<LabVisionRow> = emptyList(),
    val message: String? = null,
)

@Serializable
data class LabVisionRow(
    val code: String? = null,
    val name: String = "",
    val value: Double? = null,
    val valueText: String? = null,
    val unit: String? = null,
    val refLow: Double? = null,
    val refHigh: Double? = null,
    val refText: String? = null,
)

/** Reads the pages of a lab sheet. Separate from [FoodVision]: different pages, different answer. */
interface LabVision {
    val name: String
    suspend fun read(pages: List<LabPhotoUpload>, language: Language, codes: List<String>): LabVisionAnswer
}

/**
 * Gemini, asked to copy a lab sheet into one JSON object.
 *
 * The instruction is about transcription, not medicine: copy what is printed, the
 * number and the range exactly as they stand, and never fill a gap. A range the model
 * "knows" for haemoglobin is not the range this laboratory printed, and the flag the app
 * shows is only honest if it is the laboratory's.
 */
class GeminiLabVision(
    private val client: HttpClient,
    private val config: AiConfig,
) : LabVision {

    override val name: String get() = config.model

    override suspend fun read(pages: List<LabPhotoUpload>, language: Language, codes: List<String>): LabVisionAnswer {
        val apiKey = config.apiKey ?: throw ModelUnavailableException("no_key", "No API key configured")

        val response = runCatchingVision {
            client.post("${config.endpoint}/v1beta/models/${config.model}:generateContent") {
                header("x-goog-api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(
                    VisionRequest(
                        systemInstruction = VisionContent(listOf(VisionPart(text = instruction(language, codes)))),
                        contents = listOf(
                            VisionContent(
                                listOf(VisionPart(text = PROMPT)) +
                                    pages.map { VisionPart(inlineData = VisionBlob(it.mimeType, it.imageBase64.trim())) },
                            ),
                        ),
                        generationConfig = VisionGenerationConfig(
                            temperature = 0.0,
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

        val sheet = runCatching { lenient.decodeFromString<LabVisionSheet>(text) }
            .getOrElse { throw ModelUnavailableException("decode", "not the JSON asked for") }

        return LabVisionAnswer(
            sheet = sheet,
            model = config.model,
            promptTokens = parsed.usageMetadata?.promptTokenCount,
            completionTokens = parsed.usageMetadata?.candidatesTokenCount,
        )
    }

    private fun instruction(language: Language, codes: List<String>): String = """
        You copy a medical laboratory result sheet into JSON for a health app. The pages
        given are one sheet, in order. Sheets here are usually in Russian or Uzbek.

        Rules:
        - Answer with one JSON object and nothing else.
        - Set isLabReport to false when the pages are not a laboratory result (a
          prescription, a receipt, an ultrasound report, anything else), or are too blurred
          to read, and put one short sentence in ${language.displayName} in message saying so.
        - One entry in values per result row. Copy name, value, unit and range exactly as
          printed. Never invent or correct a value, a unit or a range; leave a field null
          when the sheet does not show it.
        - value is the number with a dot for decimals ("4,5" is 4.5). When the result is
          not a single number ("отрицательно", "1:160", "< 0.5"), leave value null and copy
          it into valueText.
        - refLow and refHigh are the printed reference range. "3.5 - 5.0" is both;
          "< 5.0" or "до 5" is refHigh only; "> 30" is refLow only. When the sheet prints
          several ranges for one row (by cycle phase, trimester, age), leave refLow and
          refHigh null and copy the ranges into refText.
        - code is one of these when the row is that marker, otherwise null:
          ${codes.joinToString(", ")}
        - takenOn is the date the sample was taken (or the result date if that is all
          there is) as YYYY-MM-DD, or null. place is the laboratory's name, or null.
        - Never interpret, diagnose or advise.
    """.trimIndent()

    private companion object {
        const val PROMPT =
            "Copy this lab result sheet. JSON: {\"isLabReport\":bool,\"takenOn\":string|null," +
                "\"place\":string|null,\"values\":[{\"code\":string|null,\"name\":string," +
                "\"value\":number|null,\"valueText\":string|null,\"unit\":string|null," +
                "\"refLow\":number|null,\"refHigh\":number|null,\"refText\":string|null}]," +
                "\"message\":string|null}"

        /** Sixty rows of a few dozen tokens each, and nothing more. */
        const val MaxOutputTokens = 6000
        val lenient = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    }
}
