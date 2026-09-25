package uz.sadora.server.ai

import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource
import kotlin.uuid.Uuid
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import uz.sadora.contract.AiGreeting
import uz.sadora.contract.HealthMetric
import uz.sadora.server.cache.Cache
import uz.sadora.server.config.AiConfig
import uz.sadora.server.config.Environment
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.now
import uz.sadora.server.core.resolveTimeZone
import uz.sadora.server.flags.FeatureFlagService
import uz.sadora.server.flags.FlagContext
import uz.sadora.server.health.HealthService
import uz.sadora.server.health.MindService
import uz.sadora.server.health.NutritionService
import uz.sadora.server.rewards.RewardsRepository
import uz.sadora.server.user.UserRepository
import uz.sadora.server.wearable.WearableService

/**
 * The line under her name on the home screen.
 *
 * It has to be different on every open, and it must not cost a model call on every open.
 * Those two requirements are met by asking the model for a *batch*: several lines for
 * the moment she is in, cached against a coarse signature of that moment, and handed out
 * one at a time. When the batch runs out — or there is no key, or the operator's switch
 * is off, or the model failed — [GreetingPhrases] writes the line instead, and it varies
 * too, so nothing about the fallback reads as a degraded mode.
 *
 * The cost of the whole feature is therefore a handful of calls a day per active user at
 * worst, and zero when the switch is off.
 *
 * Her data reaches the prompt only with the AI-insights consent, exactly as the chat's
 * does — and even then only as bands and booleans, never as raw numbers.
 */
class GreetingService(
    private val users: UserRepository,
    private val flags: FeatureFlagService,
    private val environment: Environment,
    private val cache: Cache,
    private val config: AiConfig,
    private val health: HealthService,
    private val nutrition: NutritionService,
    private val mind: MindService,
    private val wearables: WearableService,
    private val rewards: RewardsRepository,
    private val usage: AiUsageRecorder,
    private val model: AiModel? = null,
) {

    suspend fun greeting(userId: Uuid): AiGreeting {
        val user = users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")
        val zone = resolveTimeZone(user.timezone)
        val localNow = now().toLocalDateTime(zone)
        val context = contextFor(userId, user.name, localNow)

        val key = cacheKey(userId, localNow.date.toString(), context.signature())
        cached(key)?.let { return AiGreeting(line = it, source = SOURCE_MODEL) }

        val modelAllowed = model != null && config.apiKey != null && flags.isEnabled(
            AiService.MODEL_FLAG,
            FlagContext(
                userId = userId,
                environment = environment,
                language = user.language,
                lifeStage = user.lifeStage,
            ),
        )

        if (modelAllowed) {
            generate(userId, context, user.language)?.let { lines ->
                store(key, lines)
                cached(key)?.let { return AiGreeting(line = it, source = SOURCE_MODEL) }
            }
        }

        return AiGreeting(line = ruleLine(userId, user.language, context), source = SOURCE_RULES)
    }

    /**
     * A line from the rule pools, never the one she just saw.
     *
     * Picking at random is not enough on its own: with a pool of four night lines, two
     * opens a minute apart land on the same one often enough to look broken. The last
     * line is remembered for a few minutes and excluded, which is the cheapest way to
     * guarantee the thing the feature promises — that it changes.
     */
    private suspend fun ruleLine(
        userId: Uuid,
        language: uz.sadora.contract.Language,
        context: GreetingContext,
    ): String {
        val pool = GreetingPhrases.pool(language, context)
        if (pool.isEmpty()) return GreetingPhrases.line(language, context, now().toEpochMilliseconds())

        val key = "greeting:last:$userId"
        val previous = cache.get(key)
        // Falls back to the whole pool when excluding the last line would empty it.
        val choice = pool.filterNot { it == previous }.ifEmpty { pool }.random()
        cache.set(key, choice, TTL)
        return choice
    }

    /**
     * Asks the model for [BATCH] lines and keeps the ones that look like greetings.
     *
     * Anything the model numbered, quoted or padded into a paragraph is trimmed or
     * dropped: a line that does not fit on one row under her name is not a greeting, and
     * a bad batch falls back rather than being shown.
     */
    private suspend fun generate(
        userId: Uuid,
        context: GreetingContext,
        language: uz.sadora.contract.Language,
    ): List<String>? {
        val model = model ?: return null
        val started = TimeSource.Monotonic.markNow()
        return try {
            val answer = withTimeout(config.timeout) {
                model.complete(
                    instruction = GreetingPhrases.instruction(language),
                    prompt = GreetingPhrases.prompt(language, context, BATCH),
                    // Warmer than the chat's 0.4: this is the one place in the app where
                    // the same input should not produce the same output.
                    temperature = 1.0,
                    maxOutputTokens = MAX_TOKENS,
                )
            }
            val lines = answer.text
                .lines()
                .map { it.trim().trimStart('-', '*', '•', ' ').trimStart(*Digits).trim(' ', '.', ')', '"', '«', '»') }
                .filter { it.length in MIN_LENGTH..MAX_LENGTH }
                .distinct()
                .take(BATCH)

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
                    feature = FEATURE,
                ),
            )
            lines.takeIf { it.isNotEmpty() }
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
                    feature = FEATURE,
                ),
            )
            null
        }
    }

    /**
     * Reads the moment she is in.
     *
     * Each source is read on its own and dropped if it fails: a wearable layer that is
     * down should cost the greeting its knowledge of last night, not the greeting.
     */
    private suspend fun contextFor(userId: Uuid, name: String, localNow: LocalDateTime): GreetingContext {
        val consented = users.consentsOf(userId)?.aiInsights == true
        val streak = runCatching { rewards.streak(userId, localNow.date).current }.getOrDefault(0)
        val part = when (localNow.hour) {
            in 5..11 -> DayPart.MORNING
            in 12..16 -> DayPart.AFTERNOON
            in 17..21 -> DayPart.EVENING
            else -> DayPart.NIGHT
        }

        if (!consented) {
            // Without consent the greeting knows the clock and nothing else about her.
            return GreetingContext(name = name, part = part, streak = streak)
        }

        val cycle = runCatching { health.status(userId) }.getOrNull()
        val day = runCatching { nutrition.day(userId, null) }.getOrNull()
        val daily = runCatching { wearables.today(userId) }.getOrNull()
        val checkIn = runCatching { mind.summary(userId).checkIn }.getOrNull()
        val sleepMinutes = daily?.value(HealthMetric.SLEEP_DURATION)?.toInt()

        return GreetingContext(
            name = name,
            part = part,
            streak = streak,
            phase = cycle?.phase,
            cycleDay = cycle?.cycleDay,
            sleptWell = sleepMinutes?.let { it >= GOOD_SLEEP_MINUTES },
            feelingGood = checkIn?.mood?.let { it.score >= GOOD_MOOD },
            waterGoalMet = day != null && day.goals.waterGoalMl > 0 && day.waterMl >= day.goals.waterGoalMl,
            isNewAccount = streak <= 1 && cycle == null && day?.meals.isNullOrEmpty(),
        )
    }

    // ---------------------------------------------------------------- the batch cache

    /** Pops the next line off the cached batch, or null when there is none left. */
    private suspend fun cached(key: String): String? {
        val stored = cache.get(key)?.takeIf { it.isNotBlank() } ?: return null
        val lines = stored.split(SEPARATOR).filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        val rest = lines.drop(1)
        if (rest.isEmpty()) cache.delete(key) else store(key, rest)
        return lines.first()
    }

    private suspend fun store(key: String, lines: List<String>) {
        cache.set(key, lines.joinToString(SEPARATOR), TTL)
    }

    private fun cacheKey(userId: Uuid, day: String, signature: String): String =
        "greeting:$userId:$day:$signature"

    private companion object {
        const val SOURCE_MODEL = "model"
        const val SOURCE_RULES = "rules"

        /** What the AI usage log calls these calls, so they are separable from the chat. */
        const val FEATURE = "greeting"

        /** Lines per model call. Enough for a day of opens without being a paragraph. */
        const val BATCH = 6
        const val MAX_TOKENS = 256
        const val MIN_LENGTH = 8
        const val MAX_LENGTH = 64

        /** A batch is for the moment it was written for; four hours outlives a day part. */
        val TTL = 4.hours
        const val SEPARATOR = "\n"

        const val GOOD_SLEEP_MINUTES = 420
        const val GOOD_MOOD = 4

        val Digits = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', ')')
    }
}
