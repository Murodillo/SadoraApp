package uz.sadora.server.ai

import kotlin.time.TimeSource
import kotlin.uuid.Uuid
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import uz.sadora.contract.Language
import uz.sadora.server.config.AiConfig

/** Who produced the answer that reached her. */
enum class AiSource { MODEL, RULES, FALLBACK }

data class GatewayAnswer(val text: String, val source: AiSource, val model: String?)

/**
 * The one place that decides whether a model answers, and the only place that records
 * what it cost.
 *
 * Three things can send a question to the rule engine instead: no API key, the operator's
 * `ai_model_enabled` switch, or the model failing — a timeout, a quota, a blocked or
 * empty candidate. All three end with an answer rather than an error, because the user
 * asked a question and the rule engine can still answer it; only the log knows the
 * difference, which is exactly where that difference belongs.
 *
 * Nothing about the question or the answer is recorded. The log holds tokens, cost,
 * latency and an outcome, and the table has no column for the text.
 */
class AiGateway(
    private val config: AiConfig,
    private val usage: AiUsageRecorder,
    private val model: AiModel? = null,
    private val rules: AiAnswerer = RuleBasedAnswerer,
) {

    /** True when a model could answer at all — the admin page reads this. */
    val modelConfigured: Boolean get() = model != null && config.apiKey != null

    /** The model that would answer, named even when the switch is currently off. */
    val modelName: String? get() = model?.name

    suspend fun answer(
        userId: Uuid,
        question: String,
        context: AiContext?,
        modelAllowed: Boolean,
        language: Language,
    ): GatewayAnswer {
        val model = model
        if (model == null || !modelAllowed || config.apiKey == null) {
            val text = rules.answer(question, context, language)
            usage.record(
                AiUsageEntry(
                    userId = userId,
                    source = AiSource.RULES,
                    model = null,
                    outcome = "ok",
                    errorCode = if (config.apiKey == null && modelAllowed) "no_key" else null,
                ),
            )
            return GatewayAnswer(text, AiSource.RULES, null)
        }

        val started = TimeSource.Monotonic.markNow()
        return try {
            val answer = withTimeout(config.timeout) { model.answer(question, context, language) }
            usage.record(
                AiUsageEntry(
                    userId = userId,
                    source = AiSource.MODEL,
                    model = answer.model,
                    promptTokens = answer.promptTokens,
                    completionTokens = answer.completionTokens,
                    costMicros = config.costMicros(answer.promptTokens, answer.completionTokens),
                    latencyMs = started.elapsedNow().inWholeMilliseconds.toInt(),
                    outcome = "ok",
                ),
            )
            GatewayAnswer(answer.text, AiSource.MODEL, answer.model)
        } catch (failure: Exception) {
            val code = when (failure) {
                is TimeoutCancellationException -> "timeout"
                is ModelUnavailableException -> failure.code
                else -> "error"
            }
            usage.record(
                AiUsageEntry(
                    userId = userId,
                    source = AiSource.FALLBACK,
                    model = model.name,
                    latencyMs = started.elapsedNow().inWholeMilliseconds.toInt(),
                    outcome = "error",
                    errorCode = code,
                ),
            )
            // She still gets an answer; the operator still sees the failure.
            GatewayAnswer(rules.answer(question, context, language), AiSource.FALLBACK, null)
        }
    }
}
