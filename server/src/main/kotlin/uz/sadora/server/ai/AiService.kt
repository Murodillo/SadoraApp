package uz.sadora.server.ai

import kotlin.uuid.Uuid
import uz.sadora.contract.AiChatQuota
import uz.sadora.contract.AiChatReply
import uz.sadora.contract.AiChatRequest
import uz.sadora.contract.FeatureKeys
import uz.sadora.contract.HealthMetric
import uz.sadora.server.config.Environment
import uz.sadora.server.core.FeatureDisabledException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.entitlement.EntitlementService
import uz.sadora.server.flags.FeatureFlagService
import uz.sadora.server.flags.FlagContext
import uz.sadora.server.health.HealthService
import uz.sadora.server.health.NutritionService
import uz.sadora.server.user.UserRepository
import uz.sadora.server.wearable.WearableService

/**
 * SADORA AI, behind the three gates the product promises: the operator's kill switch,
 * her tier's daily and monthly allowance, and her consent to have her data read.
 *
 * The allowance is spent before the answer is produced, in one call, so a caller that
 * got an answer has always been counted. Nothing is stored: the app keeps the
 * conversation for the session and the server keeps only the usage counter, which is
 * what the admin panel's AI column reads.
 */
class AiService(
    private val users: UserRepository,
    private val entitlements: EntitlementService,
    private val flags: FeatureFlagService,
    private val environment: Environment,
    private val health: HealthService,
    private val nutrition: NutritionService,
    private val wearables: WearableService,
    private val gateway: AiGateway,
    private val usage: AiUsageRepository,
) {

    suspend fun quota(userId: Uuid): AiChatQuota {
        val user = users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")
        val feature = entitlements.resolve(userId, user.timezone).feature(FeatureKeys.AI_CHAT)
        val open = feature?.enabled == true && flagOn(userId, user)
        return AiChatQuota(
            enabled = open,
            dailyLimit = feature?.dailyLimit,
            monthlyLimit = feature?.monthlyLimit,
            usedToday = feature?.usedToday ?: 0,
            usedThisMonth = feature?.usedThisMonth ?: 0,
        )
    }

    suspend fun chat(userId: Uuid, request: AiChatRequest): AiChatReply {
        val user = users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")
        val question = request.question.trim()
        if (question.isEmpty()) throw ValidationException("question", "Bo'sh bo'lishi mumkin emas")
        if (question.length > MAX_QUESTION_LENGTH) {
            throw ValidationException("question", "Eng ko'pi $MAX_QUESTION_LENGTH belgi")
        }
        if (!flagOn(userId, user)) throw FeatureDisabledException(CHAT_FLAG)

        // Spends the allowance, or refuses with `limit_reached` / `entitlement_required`.
        entitlements.consume(userId, FeatureKeys.AI_CHAT, user.timezone)

        // Her data reaches the answer only with the consent the privacy screen collects.
        val consented = users.consentsOf(userId)?.aiInsights == true
        val context = if (consented) contextFor(userId) else null

        // The operator's second switch: with the model off the chat stays open and the
        // rule engine answers, which is a different decision from closing the chat.
        val modelAllowed = flags.isEnabled(
            MODEL_FLAG,
            FlagContext(userId = userId, environment = environment, language = user.language, lifeStage = user.lifeStage),
        )
        val answer = gateway.answer(userId, question, context, modelAllowed, user.language).text

        val feature = entitlements.resolve(userId, user.timezone).feature(FeatureKeys.AI_CHAT)
        return AiChatReply(
            answer = answer,
            basedOn = context?.takeUnless { it.isEmpty }?.summary(AiPhrases.of(user.language)).orEmpty(),
            remainingToday = feature?.remainingToday,
            remainingThisMonth = feature?.remainingThisMonth,
        )
    }

    /**
     * Today's numbers, each source read on its own and dropped if it fails: a wearable
     * layer that is down should cost the answer its step count, not the whole reply.
     */
    private suspend fun contextFor(userId: Uuid): AiContext {
        val cycle = runCatching { health.status(userId) }.getOrNull()
        val day = runCatching { nutrition.day(userId, null) }.getOrNull()
        val daily = runCatching { wearables.today(userId) }.getOrNull()
        return AiContext(
            cycleDay = cycle?.cycleDay,
            phase = cycle?.phase,
            daysUntilNextPeriod = cycle?.daysUntilNextPeriod,
            sleepMinutes = daily?.value(HealthMetric.SLEEP_DURATION)?.toInt(),
            steps = daily?.value(HealthMetric.STEPS)?.toInt(),
            waterMl = day?.waterMl,
            waterGoalMl = day?.goals?.waterGoalMl,
            kcal = day?.totals?.kcal,
            kcalGoal = day?.goals?.calorieGoal,
        )
    }

    private suspend fun flagOn(userId: Uuid, user: uz.sadora.server.user.UserRecord): Boolean =
        flags.isEnabled(
            CHAT_FLAG,
            FlagContext(userId = userId, environment = environment, language = user.language, lifeStage = user.lifeStage),
        )

    /**
     * The AI cost page's numbers, with the configuration folded in.
     *
     * [modelEnabled] is passed rather than evaluated here because the flag is per-user
     * and this is an operator-level question: the route resolves it once for the panel.
     */
    suspend fun usageReport(days: Int, modelEnabled: Boolean): AiUsageReport =
        usage.report(days).copy(
            modelConfigured = gateway.modelConfigured,
            modelEnabled = modelEnabled,
            model = gateway.modelName,
        )

    companion object {
        const val CHAT_FLAG = "ai_chat_enabled"
        const val MODEL_FLAG = "ai_model_enabled"
        const val MAX_QUESTION_LENGTH = 1000
    }
}
