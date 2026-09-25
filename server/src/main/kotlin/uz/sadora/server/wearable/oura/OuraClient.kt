package uz.sadora.server.wearable.oura

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.HealthSampleInput
import uz.sadora.server.config.OuraConfig
import uz.sadora.server.wearable.CloudProviderClient
import uz.sadora.server.wearable.OAuthTokens
import uz.sadora.server.wearable.ProviderApiException
import uz.sadora.server.wearable.ProviderUnauthorizedException

/**
 * The Oura API, v2.
 *
 * Oura pages by day rather than by instant: every collection takes `start_date` and
 * `end_date` and a `next_token`. The window is widened by a day on each side so a night
 * that began before [pull]'s start is still fetched whole; the external ids keep the
 * overlap from doubling anything.
 */
class OuraClient(
    private val client: HttpClient,
    private val config: OuraConfig,
) : CloudProviderClient {
    override val provider = HealthProvider.OURA

    private val api = "${config.apiBaseUrl.trimEnd('/')}/v2/usercollection"
    private val oauth = "${config.apiBaseUrl.trimEnd('/')}/oauth"

    override fun authorizeUrl(state: String): String = URLBuilder(config.authorizeUrl).apply {
        parameters.append("response_type", "code")
        parameters.append("client_id", config.clientId.orEmpty())
        parameters.append("redirect_uri", config.redirectUri)
        parameters.append("scope", SCOPES.joinToString(" "))
        parameters.append("state", state)
    }.buildString()

    override suspend fun exchange(code: String): OAuthTokens = token(
        Parameters.build {
            append("grant_type", "authorization_code")
            append("code", code)
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
        val response = client.submitForm(url = "$oauth/token", formParameters = form)
        if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.BadRequest) {
            throw ProviderUnauthorizedException("oura token endpoint said ${response.status.value}")
        }
        response.requireSuccess("token")
        return response.body()
    }

    override suspend fun externalUserId(accessToken: String): String? =
        runCatching { personalInfo(accessToken).id }.getOrNull()

    override suspend fun revoke(accessToken: String) {
        runCatching { client.get("$oauth/revoke") { parameter("access_token", accessToken) } }
    }

    override suspend fun pull(accessToken: String, from: Instant, to: Instant, externalUserId: String?): List<HealthSampleInput> {
        val start = (from - 1.days).toLocalDateTime(TimeZone.UTC).date.toString()
        val end = (to + 1.days).toLocalDateTime(TimeZone.UTC).date.toString()
        val samples = mutableListOf<HealthSampleInput>()
        collect<OuraSleep>("sleep", accessToken, start, end).forEach { samples += OuraMapper.fromSleep(it) }
        collect<OuraReadiness>("daily_readiness", accessToken, start, end).forEach { samples += OuraMapper.fromReadiness(it) }
        collect<OuraActivity>("daily_activity", accessToken, start, end).forEach { samples += OuraMapper.fromActivity(it) }
        // A ring without SpO2 (Gen 2) answers this one with a 4xx; the rest still counts.
        runCatching { collect<OuraSpo2>("daily_spo2", accessToken, start, end) }
            .getOrElse { if (it is ProviderUnauthorizedException) throw it else emptyList() }
            .forEach { samples += OuraMapper.fromSpo2(it) }
        runCatching { personalInfo(accessToken) }.getOrNull()?.let { samples += OuraMapper.fromPersonalInfo(it, to) }
        return samples
    }

    private suspend fun personalInfo(accessToken: String): OuraPersonalInfo =
        client.get("$api/personal_info") { bearerAuth(accessToken) }.checked("personal_info").body()

    private suspend inline fun <reified T> collect(collection: String, accessToken: String, start: String, end: String): List<T> {
        val records = mutableListOf<T>()
        var next: String? = null
        var pages = 0
        do {
            val page: OuraPage<T> = client.get("$api/$collection") {
                bearerAuth(accessToken)
                parameter("start_date", start)
                parameter("end_date", end)
                next?.let { parameter("next_token", it) }
            }.checked(collection).body()
            records += page.data
            next = page.nextToken?.takeIf { it.isNotBlank() }
            pages++
        } while (next != null && pages < MAX_PAGES)
        return records
    }

    private suspend fun HttpResponse.checked(what: String): HttpResponse {
        if (status == HttpStatusCode.Unauthorized) throw ProviderUnauthorizedException("oura $what said 401")
        requireSuccess(what)
        return this
    }

    private suspend fun HttpResponse.requireSuccess(what: String) {
        if (!status.isSuccess()) {
            throw ProviderApiException(status.value, "oura $what said ${status.value}: ${bodyAsText().take(120)}")
        }
    }

    companion object {
        /** Sleep sessions, readiness and activity all sit under "daily". */
        val SCOPES = listOf("personal", "daily", "spo2")
        private const val MAX_PAGES = 20
    }
}

// ---------------------------------------------------------------- wire

@Serializable
data class OuraPage<T>(
    val data: List<T> = emptyList(),
    @SerialName("next_token") val nextToken: String? = null,
)

@Serializable
data class OuraPersonalInfo(
    val id: String,
    val weight: Double? = null,
)

/** One sleep period. Durations are seconds; the datetimes carry her offset. */
@Serializable
data class OuraSleep(
    val id: String,
    val day: String,
    val type: String? = null,
    @SerialName("bedtime_start") val bedtimeStart: String,
    @SerialName("bedtime_end") val bedtimeEnd: String,
    @SerialName("total_sleep_duration") val totalSleepDuration: Int? = null,
    @SerialName("deep_sleep_duration") val deepSleepDuration: Int? = null,
    @SerialName("rem_sleep_duration") val remSleepDuration: Int? = null,
    @SerialName("light_sleep_duration") val lightSleepDuration: Int? = null,
    @SerialName("awake_time") val awakeTime: Int? = null,
    val efficiency: Int? = null,
    @SerialName("average_hrv") val averageHrv: Int? = null,
    @SerialName("lowest_heart_rate") val lowestHeartRate: Int? = null,
    @SerialName("average_breath") val averageBreath: Double? = null,
)

@Serializable
data class OuraReadiness(
    val id: String,
    val day: String,
    val score: Int? = null,
    val timestamp: String,
)

@Serializable
data class OuraActivity(
    val id: String,
    val day: String,
    val steps: Int = 0,
    @SerialName("active_calories") val activeCalories: Int = 0,
    @SerialName("equivalent_walking_distance") val equivalentWalkingDistance: Int = 0,
    val timestamp: String,
)

@Serializable
data class OuraSpo2(
    val id: String,
    val day: String,
    @SerialName("spo2_percentage") val spo2Percentage: OuraSpo2Value? = null,
)

@Serializable
data class OuraSpo2Value(val average: Double? = null)
