package org.example.project.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.serialization.encodeToString
import uz.sadora.contract.AccountStatus
import uz.sadora.contract.ApiError
import uz.sadora.contract.ApiErrorResponse
import uz.sadora.contract.AuthSession
import uz.sadora.contract.Entitlements
import uz.sadora.contract.FeatureEntitlement
import uz.sadora.contract.FeatureKeys
import uz.sadora.contract.Language
import uz.sadora.contract.LifeStage
import uz.sadora.contract.SubscriptionTier
import uz.sadora.contract.TokenPair
import uz.sadora.contract.UserProfile

/** A fixed instant so the fixtures do not depend on the clock. */
val TestNow: Instant = Instant.parse("2026-08-27T09:00:00Z")

fun testProfile(onboardingCompleted: Boolean = true): UserProfile = UserProfile(
    id = "11111111-1111-1111-1111-111111111111",
    phone = "+998901234567",
    name = "Malika",
    language = Language.UZ,
    timezone = "Asia/Tashkent",
    lifeStage = LifeStage.CYCLE,
    onboardingCompleted = onboardingCompleted,
    status = AccountStatus.ACTIVE,
    createdAt = TestNow,
)

fun testEntitlements(tier: SubscriptionTier = SubscriptionTier.FREE): Entitlements = Entitlements(
    tier = tier,
    features = listOf(
        FeatureEntitlement(FeatureKeys.AI_CHAT, enabled = true, dailyLimit = 3, usedToday = 0),
    ),
    evaluatedAt = TestNow,
)

fun testTokens(access: String, refresh: String): TokenPair = TokenPair(
    accessToken = access,
    refreshToken = refresh,
    accessExpiresAt = TestNow + 15.minutes,
    refreshExpiresAt = TestNow + 30.minutes,
)

fun testAuthSession(
    access: String = "access-1",
    refresh: String = "refresh-1",
    onboardingCompleted: Boolean = true,
    tier: SubscriptionTier = SubscriptionTier.FREE,
    isNewUser: Boolean = false,
): AuthSession = AuthSession(
    tokens = testTokens(access, refresh),
    user = testProfile(onboardingCompleted),
    entitlements = testEntitlements(tier),
    isNewUser = isNewUser,
)

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

fun MockRequestHandlerScope.json(
    value: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(ByteReadChannel(value), status, jsonHeaders)

typealias MockRequestHandlerScope = io.ktor.client.engine.mock.MockRequestHandleScope

inline fun <reified T> encode(value: T): String = SadoraJson.encodeToString(value)

fun errorBody(code: String, message: String, details: Map<String, String> = emptyMap()): String =
    SadoraJson.encodeToString(ApiErrorResponse(ApiError(code, message, details, "req-test")))

/** A response with no JSON envelope — what a proxy or load balancer sends. */
fun MockRequestHandlerScope.plain(body: String, status: HttpStatusCode): HttpResponseData =
    respond(ByteReadChannel(body), status, headersOf(HttpHeaders.ContentType, "text/html"))

/**
 * Records every path the client asked for, and every request body it sent, so tests can
 * assert both on call counts and on what actually went over the wire.
 */
class RecordingEngine(
    private val handler: suspend MockRequestHandlerScope.(HttpRequestData) -> HttpResponseData,
) {
    val paths = mutableListOf<String>()

    /** Serialized request bodies, in order. Empty string for a body-less request. */
    val bodies = mutableListOf<String>()

    fun build(): MockEngine = MockEngine { request ->
        paths.add(request.url.encodedPath)
        bodies.add((request.body as? TextContent)?.text.orEmpty())
        handler(request)
    }

    fun countOf(path: String): Int = paths.count { it == path }
}
