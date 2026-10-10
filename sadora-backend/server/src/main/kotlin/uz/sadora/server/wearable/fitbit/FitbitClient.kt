package uz.sadora.server.wearable.fitbit

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.HealthSampleInput
import uz.sadora.server.config.FitbitConfig
import uz.sadora.server.wearable.CloudProviderClient
import uz.sadora.server.wearable.OAuthTokens
import uz.sadora.server.wearable.ProviderApiException
import uz.sadora.server.wearable.ProviderUnauthorizedException

/**
 * Fitbit, through the Google Health API (v4).
 *
 * Fitbit's own Web API is switched off on 2026-10-30; a Fitbit account is a Google
 * account now, and its data is read from `health.googleapis.com` under a Google OAuth
 * grant. Three kinds of reading come back in three shapes: the day's totals are asked
 * for as daily roll-ups, the nightly figures (resting pulse, HRV, SpO2, breathing) are
 * listed by date, and sleep and weight are listed as the records they are.
 *
 * Everything but sleep is asked of her Fitbit and Pixel devices only. Google Health also
 * holds what her phone wrote through Health Connect, and the app reads that itself.
 */
class FitbitClient(
    private val client: HttpClient,
    private val config: FitbitConfig,
) : CloudProviderClient {
    override val provider = HealthProvider.FITBIT

    private val api = "${config.apiBaseUrl.trimEnd('/')}/v4/users/me"

    override fun authorizeUrl(state: String): String = URLBuilder(config.authorizeUrl).apply {
        parameters.append("response_type", "code")
        parameters.append("client_id", config.clientId.orEmpty())
        parameters.append("redirect_uri", config.redirectUri)
        parameters.append("scope", SCOPES.joinToString(" "))
        parameters.append("state", state)
        // Google hands out a refresh token only for offline access, and only on a consent
        // it actually showed: without the prompt a reconnect would come back without one.
        parameters.append("access_type", "offline")
        parameters.append("prompt", "consent")
    }.buildString()

    override suspend fun exchange(code: String, state: String): OAuthTokens = token(
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
        val response = client.submitForm(url = config.tokenUrl, formParameters = form)
        if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.BadRequest) {
            throw ProviderUnauthorizedException("google token endpoint said ${response.status.value}")
        }
        response.requireSuccess("token")
        return response.body()
    }

    /** Google's id for her in the Health API, which is what its webhooks name. */
    override suspend fun externalUserId(accessToken: String): String? =
        runCatching { client.get("$api/identity") { bearerAuth(accessToken) }.checked("identity").body<GoogleHealthIdentity>().healthUserId }
            .getOrNull()

    override suspend fun revoke(accessToken: String) {
        runCatching { client.submitForm(url = config.revokeUrl, formParameters = Parameters.build { append("token", accessToken) }) }
    }

    override suspend fun pull(accessToken: String, from: Instant, to: Instant, externalUserId: String?): List<HealthSampleInput> {
        // A day wider on each side: the dates are hers, and the window is in UTC.
        val start = (from - 1.days).toLocalDateTime(TimeZone.UTC).date
        val end = (to + 2.days).toLocalDateTime(TimeZone.UTC).date
        val samples = mutableListOf<HealthSampleInput>()

        rollUp("steps", accessToken, start, end).forEach { samples += FitbitMapper.fromRollup(it) }
        ROLLUPS.forEach { type ->
            optional { rollUp(type, accessToken, start, end) }.forEach { samples += FitbitMapper.fromRollup(it) }
        }
        // The pulse roll-up answers for a fortnight at most.
        optional { rollUp("heart-rate", accessToken, maxOf(start, LocalDate.fromEpochDays(end.toEpochDays() - HEART_RATE_DAYS)), end) }
            .forEach { samples += FitbitMapper.fromRollup(it) }

        DAILIES.forEach { type ->
            val field = type.replace('-', '_')
            optional { list(type, accessToken, "$field.date >= \"$start\"", WEARABLES) }.forEach { samples += FitbitMapper.fromDataPoint(it) }
        }
        list("sleep", accessToken, "sleep.interval.civil_end_time >= \"$start\"", family = null, pageSize = SLEEP_PAGE)
            .forEach { samples += FitbitMapper.fromDataPoint(it) }
        SAMPLES.forEach { type ->
            val field = type.replace('-', '_')
            optional { list(type, accessToken, "$field.sample_time.physical_time >= \"${from - 1.days}\"", family = null) }
                .forEach { samples += FitbitMapper.fromDataPoint(it) }
        }
        return samples
    }

    private suspend fun rollUp(type: String, accessToken: String, start: LocalDate, end: LocalDate): List<GoogleHealthRollup> {
        val points = mutableListOf<GoogleHealthRollup>()
        var next: String? = null
        var pages = 0
        do {
            val page: GoogleHealthRollupPage = client.post("$api/dataTypes/$type/dataPoints:dailyRollUp") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(
                    GoogleHealthRollupRequest(
                        range = CivilRange(CivilDateTime(CivilDate.of(start)), CivilDateTime(CivilDate.of(end))),
                        dataSourceFamily = WEARABLES,
                        pageToken = next,
                    ),
                )
            }.checked(type).body()
            points += page.rollupDataPoints
            next = page.nextPageToken?.takeIf { it.isNotBlank() }
            pages++
        } while (next != null && pages < MAX_PAGES)
        return points
    }

    private suspend fun list(type: String, accessToken: String, filter: String, family: String?, pageSize: Int? = null): List<GoogleHealthDataPoint> {
        val points = mutableListOf<GoogleHealthDataPoint>()
        var next: String? = null
        var pages = 0
        do {
            val page: GoogleHealthPage = client.get("$api/dataTypes/$type/dataPoints") {
                bearerAuth(accessToken)
                parameter("filter", filter)
                family?.let { parameter("dataSourceFamily", it) }
                pageSize?.let { parameter("pageSize", it) }
                next?.let { parameter("pageToken", it) }
            }.checked(type).body()
            points += page.dataPoints
            next = page.nextPageToken?.takeIf { it.isNotBlank() }
            pages++
        } while (next != null && pages < MAX_PAGES)
        return points
    }

    /** A data type she did not grant, or her device does not record, is a 4xx; the rest still counts. */
    private inline fun <T> optional(block: () -> List<T>): List<T> =
        runCatching(block).getOrElse { if (it is ProviderUnauthorizedException) throw it else emptyList() }

    private suspend fun HttpResponse.checked(what: String): HttpResponse {
        if (status == HttpStatusCode.Unauthorized) throw ProviderUnauthorizedException("google health $what said 401")
        requireSuccess(what)
        return this
    }

    private suspend fun HttpResponse.requireSuccess(what: String) {
        if (!status.isSuccess()) {
            throw ProviderApiException(status.value, "google health $what said ${status.value}: ${bodyAsText().take(120)}")
        }
    }

    companion object {
        private const val SCOPE_PREFIX = "https://www.googleapis.com/auth/googlehealth"
        val SCOPES = listOf(
            "$SCOPE_PREFIX.activity_and_fitness.readonly",
            "$SCOPE_PREFIX.health_metrics_and_measurements.readonly",
            "$SCOPE_PREFIX.sleep.readonly",
        )

        /** Her Fitbit trackers and Pixel Watch, without what was typed in or came from the phone. */
        private const val WEARABLES = "users/me/dataSourceFamilies/google-wearables"
        private val ROLLUPS = listOf("distance", "floors", "active-energy-burned")
        private val DAILIES = listOf(
            "daily-resting-heart-rate", "daily-heart-rate-variability", "daily-oxygen-saturation",
            "daily-respiratory-rate", "daily-vo2-max",
        )
        private val SAMPLES = listOf("weight", "body-fat")
        private const val HEART_RATE_DAYS = 14
        private const val SLEEP_PAGE = 25
        private const val MAX_PAGES = 20
    }
}

// ---------------------------------------------------------------- wire

@Serializable
data class GoogleHealthIdentity(val healthUserId: String? = null, val legacyUserId: String? = null)

@Serializable
data class CivilDate(val year: Int = 0, val month: Int = 0, val day: Int = 0) {
    /** Null for the all-zero date an absent field decodes to. */
    fun toLocalDate(): LocalDate? = runCatching { LocalDate(year, month, day) }.getOrNull()

    companion object {
        fun of(date: LocalDate) = CivilDate(date.year, date.month.ordinal + 1, date.day)
    }
}

/** A date and a time of day with no zone; only the date is read, and midnight is what is sent. */
@Serializable
data class CivilDateTime(val date: CivilDate? = null)

@Serializable
data class CivilRange(val start: CivilDateTime, val end: CivilDateTime)

@Serializable
data class GoogleHealthRollupRequest(
    val range: CivilRange,
    val windowSizeDays: Int = 1,
    val dataSourceFamily: String? = null,
    val pageToken: String? = null,
)

@Serializable
data class GoogleHealthRollupPage(
    val rollupDataPoints: List<GoogleHealthRollup> = emptyList(),
    val nextPageToken: String? = null,
)

/**
 * One day's total of one data type. Google writes 64-bit integers as JSON strings and
 * the rest as numbers, so the values are kept as primitives and read as numbers either way.
 */
@Serializable
data class GoogleHealthRollup(
    val civilStartTime: CivilDateTime? = null,
    val steps: CountSum? = null,
    val floors: CountSum? = null,
    val distance: MillimetersSum? = null,
    val activeEnergyBurned: KcalSum? = null,
    val heartRate: HeartRateAverage? = null,
)

@Serializable
data class CountSum(val countSum: JsonPrimitive? = null)

@Serializable
data class MillimetersSum(val millimetersSum: JsonPrimitive? = null)

@Serializable
data class KcalSum(val kcalSum: JsonPrimitive? = null)

@Serializable
data class HeartRateAverage(val beatsPerMinuteAvg: JsonPrimitive? = null)

@Serializable
data class GoogleHealthPage(
    val dataPoints: List<GoogleHealthDataPoint> = emptyList(),
    val nextPageToken: String? = null,
)

/** One record; exactly one of the fields under the name is set, by the type that was asked for. */
@Serializable
data class GoogleHealthDataPoint(
    val name: String? = null,
    val sleep: GoogleHealthSleep? = null,
    val dailyRestingHeartRate: DailyBeats? = null,
    val dailyHeartRateVariability: DailyHrv? = null,
    val dailyOxygenSaturation: DailySpo2? = null,
    val dailyRespiratoryRate: DailyBreaths? = null,
    val dailyVo2Max: DailyVo2Max? = null,
    val weight: GoogleHealthWeight? = null,
    val bodyFat: GoogleHealthBodyFat? = null,
)

@Serializable
data class DailyBeats(val date: CivilDate? = null, val beatsPerMinute: JsonPrimitive? = null)

@Serializable
data class DailyHrv(val date: CivilDate? = null, val averageHeartRateVariabilityMilliseconds: Double? = null)

@Serializable
data class DailySpo2(val date: CivilDate? = null, val averagePercentage: Double? = null)

@Serializable
data class DailyBreaths(val date: CivilDate? = null, val breathsPerMinute: Double? = null)

@Serializable
data class DailyVo2Max(val date: CivilDate? = null, val vo2Max: Double? = null)

@Serializable
data class SampleTime(val physicalTime: String? = null)

@Serializable
data class GoogleHealthWeight(val sampleTime: SampleTime? = null, val weightGrams: Double? = null)

@Serializable
data class GoogleHealthBodyFat(val sampleTime: SampleTime? = null, val percentage: Double? = null)

@Serializable
data class GoogleHealthSleep(
    val interval: SleepInterval? = null,
    val metadata: SleepMetadata? = null,
    val summary: SleepSummary? = null,
)

@Serializable
data class SleepInterval(val startTime: String? = null, val endTime: String? = null, val civilEndTime: CivilDateTime? = null)

@Serializable
data class SleepMetadata(val mainSleep: Boolean = false, val nap: Boolean = false)

@Serializable
data class SleepSummary(
    val minutesAsleep: JsonPrimitive? = null,
    val minutesAwake: JsonPrimitive? = null,
    val stagesSummary: List<SleepStageSummary> = emptyList(),
)

/** `type` is AWAKE, LIGHT, DEEP or REM for a staged night; ASLEEP and RESTLESS for a classic one. */
@Serializable
data class SleepStageSummary(val type: String? = null, val minutes: JsonPrimitive? = null)

/**
 * What Google Health posts when her data changes: who, and which data type — not the
 * data, which is pulled afterwards. One notification or an array of up to 99; the
 * handshake that precedes a subscriber's creation is an object with only a `type`.
 */
object GoogleHealthNotifications {
    private val json = Json { ignoreUnknownKeys = true }

    /** The users named in [body]. Null when it is not something Google Health would send. */
    fun users(body: String): Set<String>? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null
        val items: List<JsonElement> = runCatching { root.jsonArray.toList() }.getOrElse { listOf(root) }
        return runCatching {
            items.mapNotNull { item -> (item.jsonObject["data"]?.jsonObject?.get("healthUserId") as? JsonPrimitive)?.content }.toSet()
        }.getOrNull()
    }
}
