package uz.sadora.server.flags

import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import uz.sadora.contract.FeatureFlags
import uz.sadora.contract.Language
import uz.sadora.contract.LifeStage
import uz.sadora.contract.Platform
import uz.sadora.server.config.Environment
import uz.sadora.server.core.now
import uz.sadora.server.db.dbValue

/** Everything a rule can match on. Absent facts simply fail the rules that need them. */
data class FlagContext(
    val userId: Uuid,
    val environment: Environment,
    val language: Language? = null,
    val lifeStage: LifeStage? = null,
    val platform: Platform? = null,
    val country: String? = null,
    val cohort: String? = null,
)

/**
 * Evaluates flags for one user.
 *
 * Rules are tried in priority order and the first match decides; a flag with no matching
 * rule falls back to its default. Turning a flag's `enabled` off short-circuits every
 * rule — that is the kill switch the admin panel's AI page relies on.
 */
class FeatureFlagService(
    private val repository: FeatureFlagRepository,
) {
    private var cachedDefinitions: List<FlagDefinition> = emptyList()
    private var cachedRules: List<FlagRule> = emptyList()
    private var cacheExpiresAtMillis: Long = 0

    suspend fun evaluate(context: FlagContext): FeatureFlags {
        refreshIfStale()
        val rulesByFlag = cachedRules.groupBy { it.flagKey }
        val values = cachedDefinitions.associate { definition ->
            definition.key to evaluateOne(definition, rulesByFlag[definition.key].orEmpty(), context)
        }
        return FeatureFlags(
            flags = values,
            evaluatedAt = now(),
            ttlSeconds = CACHE_TTL.inWholeSeconds.toInt(),
        )
    }

    suspend fun isEnabled(key: String, context: FlagContext): Boolean =
        evaluate(context).isEnabled(key)

    fun invalidate() {
        cacheExpiresAtMillis = 0
    }

    private suspend fun refreshIfStale() {
        val currentMillis = now().toEpochMilliseconds()
        if (currentMillis < cacheExpiresAtMillis && cachedDefinitions.isNotEmpty()) return
        cachedDefinitions = repository.all()
        cachedRules = repository.rules()
        cacheExpiresAtMillis = currentMillis + CACHE_TTL.inWholeMilliseconds
    }

    private fun evaluateOne(
        definition: FlagDefinition,
        rules: List<FlagRule>,
        context: FlagContext,
    ): Boolean {
        if (!definition.enabled) return false
        val match = rules
            .sortedBy { it.priority }
            .firstOrNull { it.matches(context) }
            ?: return definition.defaultValue
        return match.value
    }

    private fun FlagRule.matches(context: FlagContext): Boolean {
        if (environment != null && !environment.equals(context.environment.name, ignoreCase = true)) return false
        if (language != null && !language.equals(context.language?.dbValue(), ignoreCase = true)) return false
        if (lifeStage != null && !lifeStage.equals(context.lifeStage?.dbValue(), ignoreCase = true)) return false
        if (platform != null && !platform.equals(context.platform?.dbValue(), ignoreCase = true)) return false
        if (country != null && !country.equals(context.country, ignoreCase = true)) return false
        if (cohort != null && !cohort.equals(context.cohort, ignoreCase = true)) return false
        return inRollout(context.userId, flagKey, rolloutPercentage)
    }

    companion object {
        private val CACHE_TTL = 300.seconds

        /**
         * Buckets a user into 0–99 from a hash of the flag key and her id.
         *
         * Stable in both directions that matter: the same user always lands in the same
         * bucket for a given flag, so widening a rollout from 5% to 20% only ever adds
         * users, and a user in one experiment is not correlated with another.
         */
        fun inRollout(userId: Uuid, flagKey: String, percentage: Int): Boolean {
            if (percentage >= 100) return true
            if (percentage <= 0) return false
            return bucketOf(userId, flagKey) < percentage
        }

        fun bucketOf(userId: Uuid, flagKey: String): Int {
            var hash = FNV_OFFSET_BASIS
            "$flagKey:$userId".forEach { character ->
                hash = hash xor character.code.toLong()
                hash *= FNV_PRIME
            }
            return (abs(hash % 100)).toInt()
        }

        private const val FNV_OFFSET_BASIS = -3750763034362895579L // 14695981039346656037 unsigned
        private const val FNV_PRIME = 1099511628211L
    }
}
