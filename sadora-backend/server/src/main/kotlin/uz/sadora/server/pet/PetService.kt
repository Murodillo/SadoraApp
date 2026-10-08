package uz.sadora.server.pet

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource
import kotlin.uuid.Uuid
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import uz.sadora.contract.CyclePhase
import uz.sadora.contract.FeatureKeys
import uz.sadora.contract.PetKind
import uz.sadora.contract.PetNudge
import uz.sadora.contract.PetNudgeAnswer
import uz.sadora.contract.PetNudgeRequest
import uz.sadora.contract.PetPose
import uz.sadora.contract.PetState
import uz.sadora.contract.PetTrigger
import uz.sadora.server.ai.AiModel
import uz.sadora.server.ai.AiService
import uz.sadora.server.ai.AiSource
import uz.sadora.server.ai.AiUsageEntry
import uz.sadora.server.ai.AiUsageRecorder
import uz.sadora.server.ai.ModelUnavailableException
import uz.sadora.server.cache.Cache
import uz.sadora.server.config.AiConfig
import uz.sadora.server.config.Environment
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.entitlement.EntitlementService
import uz.sadora.server.flags.FeatureFlagService
import uz.sadora.server.flags.FlagContext
import uz.sadora.server.health.HealthService
import uz.sadora.server.user.UserRecord
import uz.sadora.server.user.UserRepository

/**
 * The companion's side of things: which pet she has, and what it says after an action.
 *
 * Whether it speaks at all is decided here rather than on the phone, so two devices do
 * not double the chatter: each trigger speaks at most [perDay] times a day, and apart
 * from the few that must never wait ([URGENT]) the pet keeps [GAP] between lines. A
 * suppressed nudge is an empty answer, not an error — the app simply shows nothing.
 *
 * Most lines come from [PetPhrases]. The model writes one only where there is something
 * of hers to react to — the dish a scan recognised, the phase she is in — and only with
 * the AI-insights consent and the operator's model switch, exactly like the greeting.
 */
class PetService(
    private val users: UserRepository,
    private val pets: PetRepository,
    private val entitlements: EntitlementService?,
    private val flags: FeatureFlagService,
    private val environment: Environment,
    private val cache: Cache,
    private val config: AiConfig,
    private val health: HealthService,
    private val usage: AiUsageRecorder,
    private val model: AiModel? = null,
) {

    suspend fun state(userId: Uuid): PetState {
        val pet = pets.chosen(userId) ?: PetKind.DEFAULT
        val active = entitlements?.enabledAmong(listOf(userId), FeatureKeys.AI_PET)?.contains(userId) ?: true
        return PetState(pet = pet, active = active)
    }

    /** Her chosen pet when her plan includes it, for the reminders it sends in its voice. */
    suspend fun companionOf(userId: Uuid): PetKind? = state(userId).takeIf { it.active }?.pet

    /** Picking is free for everyone, so the picker can show all five before she subscribes. */
    suspend fun choose(userId: Uuid, pet: PetKind): PetState {
        pets.choose(userId, pet)
        return state(userId)
    }

    suspend fun nudge(userId: Uuid, request: PetNudgeRequest): PetNudgeAnswer {
        val user = users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")
        entitlements?.requireAvailable(userId, FeatureKeys.AI_PET, user.timezone)

        val trigger = request.trigger
        if (trigger !in URGENT && cache.get(gapKey(userId)) != null) return PetNudgeAnswer()
        val day = now().dayIn(user.timezone)
        val spoken = cache.increment("pet:said:$userId:${trigger.name}:$day", 26.hours)
        if (spoken > perDay(trigger)) return PetNudgeAnswer()

        val pet = pets.chosen(userId) ?: PetKind.DEFAULT
        val dish = request.detail?.let(::cleanDetail)
        val phase = if (trigger == PetTrigger.CYCLE_LOGGED && user.lifeStage.predictsCycle) {
            runCatching { health.status(userId).phase }.getOrNull()
        } else {
            null
        }

        val nudge = fromModel(user, pet, trigger, dish, phase) ?: fromRules(userId, user, pet, trigger, dish, phase)
            ?: return PetNudgeAnswer()
        cache.set(gapKey(userId), "1", GAP)
        return PetNudgeAnswer(nudge)
    }

    private suspend fun fromRules(
        userId: Uuid,
        user: UserRecord,
        pet: PetKind,
        trigger: PetTrigger,
        dish: String?,
        phase: CyclePhase?,
    ): PetNudge? {
        val line = if (trigger == PetTrigger.APP_OPEN) {
            // In turn rather than at random, so every feature gets its day.
            val tips = PetPhrases.tips(user.language)
            val turn = cache.increment("pet:tip:$userId", 60.days) - 1
            tips[(turn % tips.size).toInt()]
        } else {
            PetPhrases.lines(user.language, trigger, phase, dish).randomOrNull() ?: return null
        }
        val opener = if (line.pose == PetPose.HAPPY) PetPhrases.opener(user.language, pet) else ""
        return PetNudge(pet = pet, pose = line.pose, text = opener + line.text, action = line.action, source = SOURCE_RULES)
    }

    /**
     * A line the model writes, or null to fall back to the rules. A line that is too
     * long, empty or spills onto several rows is dropped rather than shown.
     */
    private suspend fun fromModel(
        user: UserRecord,
        pet: PetKind,
        trigger: PetTrigger,
        dish: String?,
        phase: CyclePhase?,
    ): PetNudge? {
        val prompt = when {
            trigger == PetTrigger.FOOD_SCANNED && dish != null -> PetPhrases.scanPrompt(dish)
            trigger == PetTrigger.CYCLE_LOGGED && phase != null -> PetPhrases.phasePrompt(phase)
            else -> return null
        }
        val model = model ?: return null
        if (config.apiKey == null) return null
        if (users.consentsOf(user.id)?.aiInsights != true) return null
        val switchedOn = flags.isEnabled(
            AiService.MODEL_FLAG,
            FlagContext(userId = user.id, environment = environment, language = user.language, lifeStage = user.lifeStage),
        )
        if (!switchedOn) return null

        val started = TimeSource.Monotonic.markNow()
        return try {
            val answer = withTimeout(config.timeout) {
                model.complete(
                    instruction = PetPhrases.instruction(user.language, pet),
                    prompt = prompt,
                    temperature = 0.9,
                    maxOutputTokens = MAX_TOKENS,
                )
            }
            usage.record(
                AiUsageEntry(
                    userId = user.id,
                    source = AiSource.MODEL,
                    model = answer.model,
                    promptTokens = answer.promptTokens,
                    completionTokens = answer.completionTokens,
                    costMicros = config.costMicros(answer.promptTokens, answer.completionTokens),
                    latencyMs = started.elapsedNow().inWholeMilliseconds.toInt(),
                    feature = FEATURE,
                ),
            )
            val text = answer.text.trim().trim('"', '«', '»', ' ')
            if (text.length !in MIN_LENGTH..MAX_LENGTH || '\n' in text) return null
            val pose = if (trigger == PetTrigger.FOOD_SCANNED) PetPose.HAPPY else PetPose.THINK
            PetNudge(pet = pet, pose = pose, text = text, source = SOURCE_MODEL)
        } catch (failure: Exception) {
            val code = when (failure) {
                is TimeoutCancellationException -> "timeout"
                is ModelUnavailableException -> failure.code
                else -> "error"
            }
            usage.record(
                AiUsageEntry(
                    userId = user.id,
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

    private fun gapKey(userId: Uuid) = "pet:gap:$userId"

    internal companion object {
        const val FEATURE = "pet"
        const val SOURCE_MODEL = "model"
        const val SOURCE_RULES = "rules"
        const val MAX_TOKENS = 160
        const val MIN_LENGTH = 8
        const val MAX_LENGTH = 180

        /** Quiet time between two lines, so a burst of logging is not a burst of bubbles. */
        val GAP = 10.minutes

        /** Never held back by [GAP]: a low mood and a new badge should be answered now. */
        val URGENT = setOf(PetTrigger.MOOD_LOW, PetTrigger.BADGE_EARNED)

        fun perDay(trigger: PetTrigger): Int = when (trigger) {
            PetTrigger.FOOD_SCANNED -> 2
            else -> 1
        }

        /**
         * The dish name as a single short line. It came from the scan, so it is the
         * model's own words, but it is still trimmed before it goes back into a prompt.
         */
        fun cleanDetail(raw: String): String? =
            raw.replace(Regex("[\\r\\n\"«»]"), " ").replace(Regex("\\s+"), " ").trim().take(40).ifBlank { null }
    }
}
