package org.example.project.data

import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.SubscriptionTier

class SadoraApiTest {

    private fun graph(recording: RecordingEngine): SadoraGraph = SadoraGraph(
        tokenStorage = InMemoryTokenStorage(token = "refresh-0"),
        device = FixedDeviceIdentity(),
        environment = SadoraEnvironment("http://test.local"),
        engine = recording.build(),
    )

    // ---------------------------------------------------------------- error mapping

    @Test
    fun `a premium-only feature maps to PremiumRequired rather than a generic failure`() = runTest {
        val recording = RecordingEngine {
            json(
                errorBody(
                    ErrorCodes.ENTITLEMENT_REQUIRED,
                    "Bu funksiya Premium obunada mavjud",
                    mapOf("feature" to "ai_insights"),
                ),
                HttpStatusCode.PaymentRequired,
            )
        }
        val failure = graph(recording).api.entitlements().failureOrNull
        val premium = assertIs<ApiFailure.PremiumRequired>(failure)
        assertEquals("ai_insights", premium.featureKey)
    }

    @Test
    fun `a spent allowance carries the feature and the period so the UI can word it`() = runTest {
        val recording = RecordingEngine {
            json(
                errorBody(
                    ErrorCodes.LIMIT_REACHED,
                    "Limit tugadi",
                    mapOf("feature" to "ai_chat", "period" to "month"),
                ),
                HttpStatusCode.TooManyRequests,
            )
        }
        val failure = graph(recording).api.entitlements().failureOrNull
        val limit = assertIs<ApiFailure.LimitReached>(failure)
        assertEquals("ai_chat", limit.featureKey)
        assertEquals("month", limit.period)
    }

    @Test
    fun `validation failures keep the per-field reasons`() = runTest {
        val recording = RecordingEngine {
            json(
                errorBody(
                    ErrorCodes.VALIDATION_FAILED,
                    "So'rov ma'lumotlari noto'g'ri",
                    mapOf("phone" to "O'zbekiston raqami formatida bo'lishi kerak"),
                ),
                HttpStatusCode.BadRequest,
            )
        }
        val failure = graph(recording).api.requestOtp("123").failureOrNull
        val validation = assertIs<ApiFailure.Validation>(failure)
        assertEquals(1, validation.fields.size)
        assertTrue(validation.fields.containsKey("phone"))
    }

    /** A proxy or gateway answers with HTML, not the API's error envelope. */
    @Test
    fun `a non-JSON error body still produces a usable failure`() = runTest {
        val recording = RecordingEngine {
            plain("<html>502 Bad Gateway</html>", HttpStatusCode.BadGateway)
        }
        val failure = graph(recording).api.entitlements().failureOrNull
        val unexpected = assertIs<ApiFailure.Unexpected>(failure)
        assertTrue("502" in unexpected.message, unexpected.message)
    }

    @Test
    fun `a transport error is reported as Network so the UI can offer a retry`() = runTest {
        val recording = RecordingEngine { throw RuntimeException("connection reset") }
        val failure = graph(recording).api.requestOtp("+998901234567").failureOrNull
        assertIs<ApiFailure.Network>(failure)
    }

    // ---------------------------------------------------------------- token refresh

    @Test
    fun `an expired access token is refreshed once and the call retried`() = runTest {
        var authorizedCalls = 0
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/auth/refresh" -> json(encode(testAuthSession(access = "access-2", refresh = "refresh-2")))
                else -> {
                    authorizedCalls++
                    if (authorizedCalls == 1) {
                        json(errorBody(ErrorCodes.TOKEN_EXPIRED, "Sessiya tugadi"), HttpStatusCode.Unauthorized)
                    } else {
                        json(encode(testEntitlements(SubscriptionTier.PREMIUM)))
                    }
                }
            }
        }
        val graph = graph(recording)
        val result = graph.api.entitlements()

        assertEquals(SubscriptionTier.PREMIUM, result.valueOrNull?.tier)
        assertEquals(1, recording.countOf("/v1/auth/refresh"))
        assertEquals("refresh-2", graph.session.currentRefreshToken())
    }

    /**
     * Several screens loading at once all fail on the same expired token. Refreshing per
     * request would spend the rotating refresh token several times over.
     */
    @Test
    fun `concurrent unauthorized calls trigger exactly one refresh`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/auth/refresh" -> {
                    delay(50)
                    json(encode(testAuthSession(access = "access-2", refresh = "refresh-2")))
                }

                else -> {
                    val authorization = request.headers["Authorization"]
                    if (authorization == "Bearer access-2") {
                        json(encode(testEntitlements()))
                    } else {
                        json(errorBody(ErrorCodes.TOKEN_EXPIRED, "Sessiya tugadi"), HttpStatusCode.Unauthorized)
                    }
                }
            }
        }
        val graph = graph(recording)

        val results = listOf(
            async { graph.api.entitlements() },
            async { graph.api.entitlements() },
            async { graph.api.entitlements() },
        ).awaitAll()

        assertTrue(results.all { it is ApiResult.Success }, "all three calls should succeed")
        assertEquals(1, recording.countOf("/v1/auth/refresh"))
    }

    @Test
    fun `a refresh that fails signs the user out and clears the stored token`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/auth/refresh" ->
                    json(errorBody(ErrorCodes.TOKEN_REVOKED, "Sessiya bekor qilindi"), HttpStatusCode.Unauthorized)

                else ->
                    json(errorBody(ErrorCodes.TOKEN_EXPIRED, "Sessiya tugadi"), HttpStatusCode.Unauthorized)
            }
        }
        val graph = graph(recording)
        val failure = graph.api.entitlements().failureOrNull

        assertIs<ApiFailure.Unauthorized>(failure)
        assertEquals(null, graph.session.currentRefreshToken())
        assertEquals(SessionState.SignedOut, graph.session.state.value)
    }
}
