package uz.sadora.server.wearable.garmin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.HealthSampleInput
import uz.sadora.server.config.GarminConfig
import uz.sadora.server.wearable.CloudProviderClient
import uz.sadora.server.wearable.OAuthTokens
import uz.sadora.server.wearable.ProviderApiException
import uz.sadora.server.wearable.ProviderUnauthorizedException

/**
 * The Garmin Health API, behind Garmin's OAuth 2.0 with PKCE.
 *
 * Garmin is push-first: it posts every new summary to the webhook, and that is how most
 * of her data arrives (see [GarminPush]). What is here is the rest — the grant, and a
 * pull that keeps the floor under the webhook. The pull endpoints ask by *upload* time,
 * a day at most per call, so a window is walked in day slices; history from before the
 * grant is not uploaded again, so the first sync asks Garmin to backfill it, and that
 * arrives through the webhook too.
 */
class GarminClient(
    private val client: HttpClient,
    private val config: GarminConfig,
) : CloudProviderClient {
    override val provider = HealthProvider.GARMIN

    private val api = "${config.apiBaseUrl.trimEnd('/')}/wellness-api/rest"

    /** Asked by upload time, so a rescored night is a new upload and needs no reaching back. */
    override val resyncOverlap: Duration = 1.hours

    override fun authorizeUrl(state: String): String = URLBuilder(config.authorizeUrl).apply {
        parameters.append("response_type", "code")
        parameters.append("client_id", config.clientId.orEmpty())
        parameters.append("code_challenge", Pkce.challenge(Pkce.verifier(config.clientSecret.orEmpty(), state)))
        parameters.append("code_challenge_method", "S256")
        parameters.append("redirect_uri", config.redirectUri)
        parameters.append("state", state)
    }.buildString()

    override suspend fun exchange(code: String, state: String): OAuthTokens = token(
        Parameters.build {
            append("grant_type", "authorization_code")
            append("code", code)
            append("code_verifier", Pkce.verifier(config.clientSecret.orEmpty(), state))
            append("client_id", config.clientId.orEmpty())
            append("client_secret", config.clientSecret.orEmpty())
            append("redirect_uri", config.redirectUri)
        },
    )

    override suspend fun refresh(refreshToken: String): OAuthTokens = token(
        Parameters.build {
            append("grant_type", "refresh_token")
            append("refresh_token", refreshToken)
            append("client_id", config.clientId.orEmpty())
            append("client_secret", config.clientSecret.orEmpty())
        },
    )

    private suspend fun token(form: Parameters): OAuthTokens {
        val response = client.submitForm(url = config.tokenUrl, formParameters = form)
        if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.BadRequest) {
            throw ProviderUnauthorizedException("garmin token endpoint said ${response.status.value}")
        }
        response.requireSuccess("token")
        return response.body()
    }

    override suspend fun externalUserId(accessToken: String): String? =
        runCatching { client.get("$api/user/id") { bearerAuth(accessToken) }.checked("user/id").body<GarminUserId>().userId }.getOrNull()

    /** Garmin requires this call wherever the app offers a disconnect of its own. */
    override suspend fun revoke(accessToken: String) {
        runCatching { client.delete("$api/user/registration") { bearerAuth(accessToken) } }
    }

    override suspend fun pull(accessToken: String, from: Instant, to: Instant, externalUserId: String?): List<HealthSampleInput> {
        if (to - from > MAX_PULL_WINDOW) backfill(accessToken, from, to)
        val samples = mutableListOf<HealthSampleInput>()
        var start = maxOf(from, to - MAX_PULL_WINDOW)
        while (start < to) {
            val end = minOf(start + 1.days, to)
            samples += GarminMapper.fromPush(
                GarminPush(
                    dailies = slice("dailies", accessToken, start, end),
                    sleeps = slice("sleeps", accessToken, start, end),
                    hrv = optional { slice("hrv", accessToken, start, end) },
                    pulseox = optional { slice("pulseOx", accessToken, start, end) },
                    allDayRespiration = optional { slice("respiration", accessToken, start, end) },
                    bodyComps = optional { slice("bodyComps", accessToken, start, end) },
                    userMetrics = optional { slice("userMetrics", accessToken, start, end) },
                ),
            ).values.flatten()
            start = end
        }
        return samples
    }

    /**
     * Asks Garmin to send what she recorded between [from] and [to]. The answer is a 202
     * and nothing else; the summaries come to the webhook when Garmin has gathered them.
     * A range already asked for is a 409, which is as good as a yes.
     */
    private suspend fun backfill(accessToken: String, from: Instant, to: Instant) {
        BACKFILL_TYPES.forEach { type ->
            val response = runCatching {
                client.get("$api/backfill/$type") {
                    bearerAuth(accessToken)
                    parameter("summaryStartTimeInSeconds", from.epochSeconds)
                    parameter("summaryEndTimeInSeconds", to.epochSeconds)
                }
            }.getOrNull()
            if (response?.status == HttpStatusCode.Unauthorized) throw ProviderUnauthorizedException("garmin backfill said 401")
        }
    }

    private suspend inline fun <reified T> slice(type: String, accessToken: String, start: Instant, end: Instant): List<T> =
        client.get("$api/$type") {
            bearerAuth(accessToken)
            parameter("uploadStartTimeInSeconds", start.epochSeconds)
            parameter("uploadEndTimeInSeconds", end.epochSeconds)
        }.checked(type).body()

    /** A summary type the app was not granted answers with a 4xx; the rest still counts. */
    private inline fun <T> optional(block: () -> List<T>): List<T> =
        runCatching(block).getOrElse { if (it is ProviderUnauthorizedException) throw it else emptyList() }

    private suspend fun HttpResponse.checked(what: String): HttpResponse {
        if (status == HttpStatusCode.Unauthorized) throw ProviderUnauthorizedException("garmin $what said 401")
        requireSuccess(what)
        return this
    }

    private suspend fun HttpResponse.requireSuccess(what: String) {
        if (!status.isSuccess()) {
            throw ProviderApiException(status.value, "garmin $what said ${status.value}: ${bodyAsText().take(120)}")
        }
    }

    companion object {
        /** Each slice is one call per summary type, against a budget every user shares. */
        private val MAX_PULL_WINDOW = 2.days
        private val BACKFILL_TYPES = listOf("dailies", "sleeps", "hrv", "pulseOx", "respiration", "bodyComps", "userMetrics")
    }
}

/**
 * PKCE without a column to keep the verifier in: it is derived from the state and the
 * client secret, so the server that built the challenge can rebuild the verifier on the
 * way back and nobody who only saw the state in a URL can.
 */
object Pkce {
    private val base64Url = Base64.getUrlEncoder().withoutPadding()

    /** 43 characters of base64url, the shortest RFC 7636 allows. */
    fun verifier(secret: String, state: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return base64Url.encodeToString(mac.doFinal("pkce:$state".toByteArray(Charsets.UTF_8)))
    }

    fun challenge(verifier: String): String =
        base64Url.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
}

// ---------------------------------------------------------------- wire

@Serializable
data class GarminUserId(val userId: String)

/**
 * What Garmin posts to the webhook, and what the pull endpoints return a list of.
 *
 * One body holds any of the summary types, each a list, each entry naming its user. In
 * "ping" mode an entry carries only a `callbackURL` to fetch from; those are answered
 * with a pull instead (see [pingedUsers]). Every field is optional because a watch
 * reports only what it measures.
 */
@Serializable
data class GarminPush(
    val dailies: List<GarminDaily> = emptyList(),
    val sleeps: List<GarminSleep> = emptyList(),
    val hrv: List<GarminHrv> = emptyList(),
    val pulseox: List<GarminPulseOx> = emptyList(),
    val allDayRespiration: List<GarminRespiration> = emptyList(),
    val bodyComps: List<GarminBodyComp> = emptyList(),
    val userMetrics: List<GarminUserMetrics> = emptyList(),
    val deregistrations: List<GarminUserRef> = emptyList(),
    val userPermissionsChange: List<GarminPermissions> = emptyList(),
) {
    /** Users Garmin only pinged about: the data is still on its side, to be pulled. */
    fun pingedUsers(): Set<String> =
        (dailies.map { it.userId to it.callbackURL } + sleeps.map { it.userId to it.callbackURL } +
            hrv.map { it.userId to it.callbackURL } + pulseox.map { it.userId to it.callbackURL } +
            allDayRespiration.map { it.userId to it.callbackURL } + bodyComps.map { it.userId to it.callbackURL } +
            userMetrics.map { it.userId to it.callbackURL })
            .filter { it.second != null }.mapNotNull { it.first }.toSet()

    /** Users whose consent no longer covers health data, by leaving or by narrowing it. */
    fun revokedUsers(): Set<String> =
        (deregistrations.map { it.userId } +
            userPermissionsChange.filter { HEALTH_EXPORT !in it.permissions }.map { it.userId }).filterNotNull().toSet()

    companion object {
        const val HEALTH_EXPORT = "HEALTH_EXPORT"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(body: String): GarminPush? = runCatching { json.decodeFromString<GarminPush>(body) }.getOrNull()
    }
}

@Serializable
data class GarminUserRef(val userId: String? = null)

@Serializable
data class GarminPermissions(val userId: String? = null, val permissions: List<String> = emptyList())

/** One day so far. Garmin resends it as the day grows, under her calendar date. */
@Serializable
data class GarminDaily(
    val userId: String? = null,
    val callbackURL: String? = null,
    val calendarDate: String? = null,
    val steps: Int? = null,
    val distanceInMeters: Double? = null,
    val activeKilocalories: Int? = null,
    val floorsClimbed: Int? = null,
    val restingHeartRateInBeatsPerMinute: Int? = null,
    val averageHeartRateInBeatsPerMinute: Int? = null,
)

/** One night. `durationInSeconds` is time asleep; the awake time is counted beside it. */
@Serializable
data class GarminSleep(
    val userId: String? = null,
    val callbackURL: String? = null,
    val calendarDate: String? = null,
    val startTimeInSeconds: Long? = null,
    val durationInSeconds: Int? = null,
    val deepSleepDurationInSeconds: Int? = null,
    val lightSleepDurationInSeconds: Int? = null,
    val remSleepInSeconds: Int? = null,
    val awakeDurationInSeconds: Int? = null,
    val overallSleepScore: GarminScore? = null,
)

@Serializable
data class GarminScore(val value: Int? = null)

@Serializable
data class GarminHrv(
    val userId: String? = null,
    val callbackURL: String? = null,
    val calendarDate: String? = null,
    val lastNightAvg: Int? = null,
)

/** Readings by seconds from the start. `onDemand` is one she took by hand. */
@Serializable
data class GarminPulseOx(
    val userId: String? = null,
    val callbackURL: String? = null,
    val calendarDate: String? = null,
    val onDemand: Boolean = false,
    val timeOffsetSpo2Values: Map<String, Double> = emptyMap(),
)

@Serializable
data class GarminRespiration(
    val userId: String? = null,
    val callbackURL: String? = null,
    val startTimeInSeconds: Long? = null,
    val durationInSeconds: Int? = null,
    val timeOffsetEpochToBreaths: Map<String, Double> = emptyMap(),
)

@Serializable
data class GarminBodyComp(
    val userId: String? = null,
    val callbackURL: String? = null,
    val measurementTimeInSeconds: Long? = null,
    val weightInGrams: Int? = null,
    val bodyFatInPercent: Double? = null,
)

@Serializable
data class GarminUserMetrics(
    val userId: String? = null,
    val callbackURL: String? = null,
    val calendarDate: String? = null,
    val vo2Max: Double? = null,
)
