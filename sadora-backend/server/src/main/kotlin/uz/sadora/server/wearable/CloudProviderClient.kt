package uz.sadora.server.wearable

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.HealthSampleInput

/**
 * One cloud wearable, as [WearableConnectService] needs it: an OAuth grant and a pull.
 *
 * Each vendor's client speaks its own API and hands back [HealthSampleInput] in its own
 * metric names; the mapping table turns those into SADORA's metrics on the way in, the
 * same as for the phone's readers.
 */
interface CloudProviderClient {
    val provider: HealthProvider

    fun authorizeUrl(state: String): String

    suspend fun exchange(code: String): OAuthTokens

    suspend fun refresh(refreshToken: String): OAuthTokens

    /** The vendor's id for her, which webhooks name. Null when the vendor would not say. */
    suspend fun externalUserId(accessToken: String): String?

    /** Best effort: the vendor forgets the grant on its side too. */
    suspend fun revoke(accessToken: String)

    /** Everything between [from] and [to], already mapped to samples. */
    suspend fun pull(accessToken: String, from: Instant, to: Instant, externalUserId: String?): List<HealthSampleInput>
}

/** The token endpoint's answer. WHOOP and Oura both speak plain RFC 6749 here. */
@Serializable
data class OAuthTokens(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    val scope: String = "",
    @SerialName("token_type") val tokenType: String = "bearer",
)

/** The grant is gone — a refresh is the only recovery, and after that a reconnect. */
open class ProviderUnauthorizedException(message: String) : Exception(message)

/** Anything else the vendor refused: rate limit, outage, a scope we do not hold. */
open class ProviderApiException(val status: Int, message: String) : Exception(message)
