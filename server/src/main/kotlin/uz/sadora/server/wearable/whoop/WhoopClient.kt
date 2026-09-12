package uz.sadora.server.wearable.whoop

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
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import uz.sadora.server.config.WhoopConfig

/**
 * The WHOOP developer API, v2.
 *
 * A thin client: it speaks OAuth and pages through the three collections the sync needs,
 * and it turns a 401 into [WhoopUnauthorizedException] so the caller can refresh once and
 * then give up. Nothing here knows about SADORA's metrics — that is the mapper's job.
 *
 * Every date is UTC on the wire; `timezone_offset` on each record is what places a
 * night on her calendar, and the mapper reads it.
 */
class WhoopClient(
    private val client: HttpClient,
    private val config: WhoopConfig,
) {
    private val api = "${config.apiBaseUrl.trimEnd('/')}/developer/v2"
    private val oauth = "${config.apiBaseUrl.trimEnd('/')}/oauth/oauth2"

    val scopes: List<String> = SCOPES

    fun authorizeUrl(state: String): String = URLBuilder("$oauth/auth").apply {
        parameters.append("response_type", "code")
        parameters.append("client_id", config.clientId.orEmpty())
        parameters.append("redirect_uri", config.redirectUri)
        parameters.append("scope", SCOPES.joinToString(" "))
        parameters.append("state", state)
    }.buildString()

    suspend fun exchange(code: String): WhoopTokens = token(
        Parameters.build {
            append("grant_type", "authorization_code")
            append("code", code)
            append("client_id", config.clientId.orEmpty())
            append("client_secret", config.clientSecret.orEmpty())
            append("redirect_uri", config.redirectUri)
        },
    )

    suspend fun refresh(refreshToken: String): WhoopTokens = token(
        Parameters.build {
            append("grant_type", "refresh_token")
            append("refresh_token", refreshToken)
            append("client_id", config.clientId.orEmpty())
            append("client_secret", config.clientSecret.orEmpty())
            append("scope", "offline")
        },
    )

    private suspend fun token(form: Parameters): WhoopTokens {
        val response = client.submitForm(url = "$oauth/token", formParameters = form)
        if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.BadRequest) {
            throw WhoopUnauthorizedException("token endpoint said ${response.status.value}")
        }
        response.requireSuccess("token")
        return response.body()
    }

    suspend fun profile(accessToken: String): WhoopProfile =
        client.get("$api/user/profile/basic") { bearerAuth(accessToken) }.checked("profile").body()

    suspend fun body(accessToken: String): WhoopBody? =
        client.get("$api/user/measurement/body") { bearerAuth(accessToken) }
            .takeIf { it.status.isSuccess() }?.body()

    /** Best effort: WHOOP forgets the grant on its side too, and stops the webhooks. */
    suspend fun revoke(accessToken: String) {
        runCatching { client.delete("$api/user/access") { bearerAuth(accessToken) } }
    }

    suspend fun recoveries(accessToken: String, from: Instant, to: Instant): List<WhoopRecovery> =
        collect("$api/recovery", accessToken, from, to)

    suspend fun sleeps(accessToken: String, from: Instant, to: Instant): List<WhoopSleep> =
        collect("$api/activity/sleep", accessToken, from, to)

    suspend fun cycles(accessToken: String, from: Instant, to: Instant): List<WhoopCycle> =
        collect("$api/cycle", accessToken, from, to)

    /**
     * Walks a paginated collection to the end. Twenty-five is WHOOP's page ceiling; a
     * thirty-day first sync is a handful of pages, a daily one is a single page.
     */
    private suspend inline fun <reified T> collect(url: String, accessToken: String, from: Instant, to: Instant): List<T> {
        val records = mutableListOf<T>()
        var next: String? = null
        var pages = 0
        do {
            val page: Page<T> = client.get(url) {
                bearerAuth(accessToken)
                parameter("start", from.toString())
                parameter("end", to.toString())
                parameter("limit", PAGE_SIZE)
                next?.let { parameter("nextToken", it) }
            }.checked("collection").body()
            records += page.records
            next = page.nextToken?.takeIf { it.isNotBlank() }
            pages++
        } while (next != null && pages < MAX_PAGES)
        return records
    }

    private suspend fun HttpResponse.checked(what: String): HttpResponse {
        if (status == HttpStatusCode.Unauthorized) throw WhoopUnauthorizedException("$what said 401")
        requireSuccess(what)
        return this
    }

    private suspend fun HttpResponse.requireSuccess(what: String) {
        if (!status.isSuccess()) {
            throw WhoopApiException(status.value, "$what said ${status.value}: ${bodyAsText().take(120)}")
        }
    }

    companion object {
        val SCOPES = listOf(
            "offline",
            "read:recovery",
            "read:sleep",
            "read:cycles",
            "read:workout",
            "read:profile",
            "read:body_measurement",
        )
        private const val PAGE_SIZE = 25
        private const val MAX_PAGES = 40
    }
}

/** The grant is gone — a refresh is the only recovery, and after that a reconnect. */
class WhoopUnauthorizedException(message: String) : Exception(message)

/** Anything else WHOOP refused: rate limit, outage, a scope we do not hold. */
class WhoopApiException(val status: Int, message: String) : Exception(message)

// ---------------------------------------------------------------- wire

@Serializable
data class WhoopTokens(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    val scope: String = "",
    @SerialName("token_type") val tokenType: String = "bearer",
)

@Serializable
data class WhoopProfile(
    @SerialName("user_id") val userId: Long,
    val email: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
)

@Serializable
data class WhoopBody(
    @SerialName("height_meter") val heightMeter: Double? = null,
    @SerialName("weight_kilogram") val weightKilogram: Double? = null,
    @SerialName("max_heart_rate") val maxHeartRate: Int? = null,
)

@Serializable
data class Page<T>(
    val records: List<T> = emptyList(),
    @SerialName("next_token") val nextToken: String? = null,
)

@Serializable
data class WhoopRecovery(
    @SerialName("cycle_id") val cycleId: Long,
    @SerialName("sleep_id") val sleepId: String,
    @SerialName("user_id") val userId: Long,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("updated_at") val updatedAt: Instant,
    @SerialName("score_state") val scoreState: String,
    val score: WhoopRecoveryScore? = null,
)

@Serializable
data class WhoopRecoveryScore(
    @SerialName("user_calibrating") val userCalibrating: Boolean = false,
    @SerialName("recovery_score") val recoveryScore: Double? = null,
    @SerialName("resting_heart_rate") val restingHeartRate: Double? = null,
    @SerialName("hrv_rmssd_milli") val hrvRmssdMilli: Double? = null,
    @SerialName("spo2_percentage") val spo2Percentage: Double? = null,
    @SerialName("skin_temp_celsius") val skinTempCelsius: Double? = null,
)

@Serializable
data class WhoopSleep(
    val id: String,
    @SerialName("cycle_id") val cycleId: Long? = null,
    @SerialName("user_id") val userId: Long,
    val start: Instant,
    val end: Instant,
    @SerialName("timezone_offset") val timezoneOffset: String = "Z",
    val nap: Boolean = false,
    @SerialName("score_state") val scoreState: String,
    val score: WhoopSleepScore? = null,
)

@Serializable
data class WhoopSleepScore(
    @SerialName("stage_summary") val stageSummary: WhoopStageSummary? = null,
    @SerialName("sleep_needed") val sleepNeeded: JsonObject? = null,
    @SerialName("respiratory_rate") val respiratoryRate: Double? = null,
    @SerialName("sleep_performance_percentage") val sleepPerformancePercentage: Double? = null,
    @SerialName("sleep_consistency_percentage") val sleepConsistencyPercentage: Double? = null,
    @SerialName("sleep_efficiency_percentage") val sleepEfficiencyPercentage: Double? = null,
)

@Serializable
data class WhoopStageSummary(
    @SerialName("total_in_bed_time_milli") val totalInBedTimeMilli: Long = 0,
    @SerialName("total_awake_time_milli") val totalAwakeTimeMilli: Long = 0,
    @SerialName("total_no_data_time_milli") val totalNoDataTimeMilli: Long = 0,
    @SerialName("total_light_sleep_time_milli") val totalLightSleepTimeMilli: Long = 0,
    @SerialName("total_slow_wave_sleep_time_milli") val totalSlowWaveSleepTimeMilli: Long = 0,
    @SerialName("total_rem_sleep_time_milli") val totalRemSleepTimeMilli: Long = 0,
    @SerialName("sleep_cycle_count") val sleepCycleCount: Int = 0,
    @SerialName("disturbance_count") val disturbanceCount: Int = 0,
)

@Serializable
data class WhoopCycle(
    val id: Long,
    @SerialName("user_id") val userId: Long,
    val start: Instant,
    val end: Instant? = null,
    @SerialName("timezone_offset") val timezoneOffset: String = "Z",
    @SerialName("score_state") val scoreState: String,
    val score: WhoopCycleScore? = null,
)

@Serializable
data class WhoopCycleScore(
    val strain: Double? = null,
    val kilojoule: Double? = null,
    @SerialName("average_heart_rate") val averageHeartRate: Double? = null,
    @SerialName("max_heart_rate") val maxHeartRate: Double? = null,
)

/** A webhook body. Carries no data — the resource is fetched afterwards. */
@Serializable
data class WhoopWebhookEvent(
    @SerialName("user_id") val userId: Long,
    val id: String,
    val type: String,
    @SerialName("trace_id") val traceId: String? = null,
)
