package uz.sadora.server.wearable.huawei

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
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
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.HealthSampleInput
import uz.sadora.server.config.HuaweiConfig
import uz.sadora.server.wearable.CloudProviderClient
import uz.sadora.server.wearable.OAuthTokens
import uz.sadora.server.wearable.ProviderApiException
import uz.sadora.server.wearable.ProviderUnauthorizedException

/**
 * A Huawei watch or band, through the cloud side of Huawei Health Kit (REST v2).
 *
 * The watch syncs to the Huawei Health app, the app to Huawei's cloud, and this reads
 * the cloud under a HUAWEI ID grant. Three shapes come back: a day's statistics of one
 * data type (steps, pulse, SpO2, temperature…), the night as a sleep record, and HRV,
 * which has no statistics of its own and is read as the samples it is.
 *
 * Two things are Huawei's alone. It refuses a query that reaches back before the day
 * she granted access unless she also granted the history scope, so a window it refuses
 * is asked again for the last days only. And it answers in days of a time zone the
 * caller names, so the zone is configuration: [HuaweiConfig.timeZone].
 */
class HuaweiClient(
    private val client: HttpClient,
    private val config: HuaweiConfig,
) : CloudProviderClient {
    override val provider = HealthProvider.HUAWEI

    private val api = "${config.apiBaseUrl.trimEnd('/')}/healthkit/v2"
    private val zone: TimeZone = UtcOffset.Formats.FOUR_DIGITS.parse(config.timeZone).asTimeZone()

    override fun authorizeUrl(state: String): String = URLBuilder(config.authorizeUrl).apply {
        parameters.append("response_type", "code")
        parameters.append("client_id", config.clientId.orEmpty())
        parameters.append("redirect_uri", config.redirectUri)
        parameters.append("scope", (listOf("openid") + config.scopes.map { "$SCOPE_PREFIX/$it" }).joinToString(" "))
        parameters.append("state", state)
        // A refresh token comes only with offline access; the consent page is drawn for a phone.
        parameters.append("access_type", "offline")
        parameters.append("display", "touch")
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

    /** Huawei's refresh token is good for 180 days and does not come back new. */
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
            throw ProviderUnauthorizedException("huawei token endpoint said ${response.status.value}")
        }
        response.requireSuccess("token")
        return response.body()
    }

    /**
     * Her openId, which is what Huawei's notifications name — and Huawei only says it in
     * answer to a subscription. So this is also where her changes are subscribed to; with
     * no subscriber registered there is nothing to subscribe with and nothing to be named by.
     *
     * The events are asked for together first. One whose scope she did not grant may sink
     * the lot, so after a refusal they are asked for one at a time.
     */
    override suspend fun externalUserId(accessToken: String): String? {
        val subscriberId = config.subscriberId ?: return null
        suspend fun subscribe(events: List<HuaweiEventType>): String? = runCatching {
            client.post("$api/subscriptions") {
                huawei(accessToken)
                contentType(ContentType.Application.Json)
                setBody(HuaweiSubscriptionRequest(subscriberId, events))
            }.checked("subscriptions").body<JsonElement>().let(::openIdIn)
        }.getOrNull()
        return subscribe(EVENTS) ?: EVENTS.map { subscribe(listOf(it)) }.firstOrNull { it != null }
    }

    /** Cancels everything her HUAWEI ID granted this app; Huawei drops the subscriptions with it. */
    override suspend fun revoke(accessToken: String) {
        runCatching { client.delete("$api/consents/${config.clientId.orEmpty()}") { huawei(accessToken); parameter("deleteData", false) } }
    }

    override suspend fun pull(accessToken: String, from: Instant, to: Instant, externalUserId: String?): List<HealthSampleInput> {
        val end = to.toLocalDateTime(zone).date
        val recent = LocalDate.fromEpochDays(end.toEpochDays() - RECENT_DAYS)
        // Huawei answers for 31 days at most.
        var start = maxOf(from.toLocalDateTime(zone).date, LocalDate.fromEpochDays(end.toEpochDays() - MAX_DAYS))
        val samples = mutableListOf<HealthSampleInput>()
        val failures = mutableListOf<Throwable>()

        /** The whole window, or the last days of it when the whole reaches back further than she allowed. */
        suspend fun <T> windowed(ask: suspend (LocalDate) -> List<T>): List<T> = try {
            try {
                ask(start)
            } catch (e: ProviderApiException) {
                if (e.status != HttpStatusCode.Forbidden.value || start >= recent) throw e
                ask(recent).also { start = recent }
            }
        } catch (e: ProviderUnauthorizedException) {
            throw e
        } catch (e: Exception) {
            // A data type she did not grant, or her watch does not record; the rest still counts.
            failures += e
            emptyList()
        }

        DAILY_TYPES.forEach { type ->
            windowed { daily(type, accessToken, it, end) }.forEach { samples += HuaweiMapper.fromDay(it, zone) }
        }
        windowed { sleep(accessToken, it, to) }.forEach { samples += HuaweiMapper.fromSleep(it, zone) }
        windowed { hrv(accessToken, maxOf(it, LocalDate.fromEpochDays(end.toEpochDays() - HRV_DAYS)), to) }
            .let { samples += HuaweiMapper.fromHrv(it, zone) }

        // Everything refused is not a quiet day: the connection should say so.
        if (failures.size == DAILY_TYPES.size + 2) throw failures.first()
        return samples
    }

    /** One data type's statistics, a group per calendar day of [zone]. */
    private suspend fun daily(type: String, accessToken: String, start: LocalDate, end: LocalDate): List<HuaweiGroup> =
        client.post("$api/sampleSet:dailyPolymerize") {
            huawei(accessToken)
            contentType(ContentType.Application.Json)
            setBody(HuaweiDailyRequest(listOf(type), start.format(LocalDate.Formats.ISO_BASIC), end.format(LocalDate.Formats.ISO_BASIC), config.timeZone))
        }.checked(type).body<HuaweiGroups>().group

    private suspend fun sleep(accessToken: String, start: LocalDate, to: Instant): List<HuaweiHealthRecord> =
        client.get("$api/healthRecords") {
            huawei(accessToken)
            parameter("startTime", nanos(startOf(start)))
            parameter("endTime", nanos(to))
            parameter("dataType", SLEEP_RECORD)
        }.checked("sleep").body<HuaweiHealthRecords>().healthRecords

    private suspend fun hrv(accessToken: String, start: LocalDate, to: Instant): List<HuaweiSamplePoint> =
        client.post("$api/sampleSet:polymerize") {
            huawei(accessToken)
            contentType(ContentType.Application.Json)
            setBody(HuaweiDetailRequest(listOf(HuaweiDataType(HRV)), startOf(start).toEpochMilliseconds(), to.toEpochMilliseconds()))
        }.checked("hrv").body<HuaweiGroups>().group.flatMap { group -> group.sampleSet.flatMap { it.samplePoints } }

    private fun startOf(date: LocalDate): Instant = date.atStartOfDayIn(zone)

    /** The subscriptions come back as a bare list, each naming her; a wrapped list is read the same way. */
    private fun openIdIn(answer: JsonElement): String? {
        val records = answer as? JsonArray ?: (answer as? JsonObject)?.get("subscriptions") as? JsonArray ?: return null
        return records.firstNotNullOfOrNull { ((it as? JsonObject)?.get("openId") as? JsonPrimitive)?.content?.takeIf(String::isNotBlank) }
    }

    private fun nanos(instant: Instant): String = "${instant.toEpochMilliseconds()}000000"

    private fun HttpRequestBuilder.huawei(accessToken: String) {
        bearerAuth(accessToken)
        // Not required; Huawei routes by it and asks for it to be sent.
        header("x-client-id", config.clientId.orEmpty())
    }

    private suspend fun HttpResponse.checked(what: String): HttpResponse {
        if (status == HttpStatusCode.Unauthorized) throw ProviderUnauthorizedException("huawei health $what said 401")
        requireSuccess(what)
        return this
    }

    private suspend fun HttpResponse.requireSuccess(what: String) {
        if (!status.isSuccess()) {
            throw ProviderApiException(status.value, "huawei health $what said ${status.value}: ${bodyAsText().take(120)}")
        }
    }

    companion object {
        private const val SCOPE_PREFIX = "https://www.huawei.com/healthkit"

        /**
         * What is asked of her, as the last segment of each Health Kit scope. Every one has
         * to be applied for on the app's Health Service Kit card first: a scope Huawei has
         * not approved for the app breaks the consent page, which is why the list can be
         * narrowed from the environment. The last one lets the first pull reach back a
         * month before the day she connected.
         */
        val DEFAULT_SCOPES = listOf(
            "step.read", "distance.read", "calories.read", "heartrate.read", "sleep.read",
            "oxygensaturation.read", "bodytemperature.read", "heightweight.read", "pulmonary.read",
            "historydata.open.month",
        )

        /** Asked for by the name of the samples; answered as that type's statistics. */
        private val DAILY_TYPES = listOf(
            "com.huawei.continuous.steps.delta",
            "com.huawei.continuous.distance.delta",
            "com.huawei.continuous.calories.burnt",
            "com.huawei.instantaneous.heart_rate",
            "com.huawei.instantaneous.resting_heart_rate",
            "com.huawei.instantaneous.spo2",
            "com.huawei.instantaneous.skin.temperature",
            "com.huawei.instantaneous.body.temperature",
            "com.huawei.instantaneous.body_weight",
            "com.huawei.vo2max",
        )
        private const val SLEEP_RECORD = "com.huawei.health.record.sleep"
        private const val HRV = "com.huawei.heart_rate_variability"

        private fun updated(type: String, dataType: String) = HuaweiEventType(type, "UPDATE", "$type\$UPDATE\$$dataType")

        /** Her leaving, and the changes worth a pull. The statistics themselves cannot be subscribed to. */
        private val EVENTS = listOf(
            HuaweiEventType("USER_EVENT", "REVOKE_ALL", "USER_EVENT\$REVOKE_ALL"),
            HuaweiEventType("USER_EVENT", "DELETE_USER", "USER_EVENT\$DELETE_USER"),
            HuaweiEventType("USER_EVENT", "REFRESH_TOKEN_INVALID", "USER_EVENT\$REFRESH_TOKEN_INVALID"),
            updated("HEALTH_RECORD_EVENT", SLEEP_RECORD),
            updated("SAMPLESET_EVENT", "com.huawei.continuous.steps.total"),
            updated("SAMPLESET_EVENT", "com.huawei.instantaneous.resting_heart_rate"),
            updated("SAMPLESET_EVENT", "com.huawei.instantaneous.body_weight"),
        )

        private const val MAX_DAYS = 30
        private const val RECENT_DAYS = 1
        private const val HRV_DAYS = 7
    }
}

// ---------------------------------------------------------------- wire

@Serializable
data class HuaweiDailyRequest(val dataTypes: List<String>, val startDay: String, val endDay: String, val timeZone: String)

@Serializable
data class HuaweiDataType(val dataTypeName: String)

@Serializable
data class HuaweiDetailRequest(val polymerizeWith: List<HuaweiDataType>, val startTime: Long, val endTime: Long)

@Serializable
data class HuaweiGroups(val group: List<HuaweiGroup> = emptyList())

/** One day of a daily query, or the whole window of a detail query. Times are in milliseconds. */
@Serializable
data class HuaweiGroup(val startTime: Long = 0, val endTime: Long = 0, val sampleSet: List<HuaweiSampleSet> = emptyList())

@Serializable
data class HuaweiSampleSet(val samplePoints: List<HuaweiSamplePoint> = emptyList())

/** Times are in nanoseconds here, unlike the group around it. */
@Serializable
data class HuaweiSamplePoint(
    val startTime: Long = 0,
    val endTime: Long = 0,
    val dataTypeName: String? = null,
    val value: List<HuaweiField> = emptyList(),
)

/** One named value; which of the four is set depends on the field. */
@Serializable
data class HuaweiField(
    val fieldName: String,
    val integerValue: Long? = null,
    val longValue: Long? = null,
    val floatValue: Double? = null,
    val stringValue: String? = null,
) {
    val number: Double? get() = floatValue ?: integerValue?.toDouble() ?: longValue?.toDouble()
}

@Serializable
data class HuaweiHealthRecords(val healthRecords: List<HuaweiHealthRecord> = emptyList())

@Serializable
data class HuaweiHealthRecord(
    val startTime: Long = 0,
    val endTime: Long = 0,
    val dataTypeName: String? = null,
    val value: List<HuaweiField> = emptyList(),
)

@Serializable
data class HuaweiEventType(val type: String, val subType: String, val eventType: String)

@Serializable
data class HuaweiSubscriptionRequest(val subscriberId: String, val eventTypes: List<HuaweiEventType>)

/**
 * What Huawei posts when something of hers changed: who and what kind of event, never
 * the data. Signed with the subscriber's secret over `openId_eventType_eventTime`.
 */
@Serializable
data class HuaweiNotification(
    val openId: String? = null,
    val eventType: String? = null,
    val eventTime: Long? = null,
    val type: String? = null,
    val subType: String? = null,
) {
    /** She cancelled the grant, deleted her HUAWEI ID, or the refresh token died. */
    val isRevocation: Boolean get() = type == "USER_EVENT"

    /** New or changed data. A deletion is not pulled for: nothing of ours is deleted by a pull. */
    val isUpdate: Boolean get() = subType == "UPDATE" && (type == "SAMPLESET_EVENT" || type == "HEALTH_RECORD_EVENT")

    /**
     * Whether [signature] is `base64(HMAC-SHA256(base64-decoded secret, openId_eventType_eventTime))`.
     * The connectivity test the console runs may name nobody, so an absent field is tried
     * both as nothing and as the word a serializer would write for it.
     */
    fun signedWith(secret: String, signature: String): Boolean {
        val key = runCatching { Base64.getDecoder().decode(secret.trim()) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return false
        return listOf("", "null").any { absent ->
            val text = listOf(openId, eventType, eventTime?.toString()).joinToString("_") { it ?: absent }
            val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
            val expected = Base64.getEncoder().encodeToString(mac.doFinal(text.toByteArray(Charsets.UTF_8)))
            java.security.MessageDigest.isEqual(expected.toByteArray(), signature.trim().toByteArray())
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Null when [body] is not something Huawei would send. */
        fun parse(body: String): HuaweiNotification? =
            runCatching { json.decodeFromString<HuaweiNotification>(body) }.getOrNull()?.takeIf { it.eventType != null }
    }
}
