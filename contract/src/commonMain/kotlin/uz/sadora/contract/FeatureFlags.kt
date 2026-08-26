package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * Flags already resolved for the calling user — the client never sees the rules, only the
 * verdict, so a percentage rollout cannot be reverse-engineered from the response.
 */
@Serializable
data class FeatureFlags(
    val flags: Map<String, Boolean>,
    val evaluatedAt: Instant,
    /** How long the client may cache this before asking again. */
    val ttlSeconds: Int = 300,
) {
    fun isEnabled(key: String): Boolean = flags[key] == true
}

/** Everything the client needs on cold start, in one request. */
@Serializable
data class Bootstrap(
    val user: UserProfile,
    val entitlements: Entitlements,
    val flags: FeatureFlags,
    val consents: Consents,
    val serverTime: Instant,
    /** Set when this build is below the supported floor and must update to continue. */
    val minimumAppVersion: String? = null,
)
