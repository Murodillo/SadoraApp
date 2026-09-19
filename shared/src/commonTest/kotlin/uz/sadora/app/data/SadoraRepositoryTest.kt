package uz.sadora.app.data

import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import uz.sadora.contract.Bootstrap
import uz.sadora.contract.Consents
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.FeatureFlags
import uz.sadora.contract.SubscriptionTier

class SadoraRepositoryTest {

    private fun graph(recording: RecordingEngine, storedToken: String? = "refresh-0") = SadoraGraph(
        tokenStorage = InMemoryTokenStorage(token = storedToken),
        device = FixedDeviceIdentity(),
        environment = SadoraEnvironment("http://test.local"),
        engine = recording.build(),
    )

    private fun bootstrap(tier: SubscriptionTier = SubscriptionTier.PREMIUM) = Bootstrap(
        user = testProfile(),
        entitlements = testEntitlements(tier),
        flags = FeatureFlags(mapOf("ai_chat_enabled" to true), TestNow),
        consents = Consents(true, true, false, false, "2026-08-01", TestNow),
        serverTime = TestNow,
    )

    @Test
    fun `with no stored token resume signs out without calling the server`() = runTest {
        val recording = RecordingEngine { json(encode(testEntitlements())) }
        val graph = graph(recording, storedToken = null)

        assertEquals(SessionState.SignedOut, graph.repository.resume())
        assertTrue(recording.paths.isEmpty(), "resume should not have called ${recording.paths}")
    }

    @Test
    fun `resume restores the session and the entitlements from bootstrap`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/auth/refresh" -> json(encode(testAuthSession(access = "access-2", refresh = "refresh-2")))
                "/v1/bootstrap" -> json(encode(bootstrap()))
                else -> json("{}", HttpStatusCode.NotFound)
            }
        }
        val graph = graph(recording)
        val state = assertIs<SessionState.SignedIn>(graph.repository.resume())

        assertEquals(SubscriptionTier.PREMIUM, state.entitlements.tier)
        assertEquals(state, graph.session.state.value)
    }

    /**
     * A rejected stored token is not an error the user can act on — she has simply been
     * signed out, and should see the sign-in screen rather than a message about a token.
     */
    @Test
    fun `a stored token the server rejects results in a plain signed-out state`() = runTest {
        val recording = RecordingEngine {
            json(errorBody(ErrorCodes.TOKEN_REVOKED, "Sessiya bekor qilindi"), HttpStatusCode.Unauthorized)
        }
        val graph = graph(recording)

        assertEquals(SessionState.SignedOut, graph.repository.resume())
        assertEquals(null, graph.session.currentRefreshToken())
    }

    /**
     * Opening the app with no connection is not the server rejecting her token. She
     * stays signed in, on the profile the last launch saw, and keeps her token.
     */
    @Test
    fun `resume offline opens on the cached profile and keeps the token`() = runTest {
        val recording = RecordingEngine { throw RuntimeException("network down") }
        val graph = graph(recording)
        graph.session.updateProfile(testProfile(), testEntitlements(SubscriptionTier.PREMIUM))

        val state = assertIs<SessionState.SignedIn>(graph.repository.resume())

        assertEquals("Malika", state.user.name)
        assertEquals(SubscriptionTier.PREMIUM, state.entitlements.tier)
        assertEquals("refresh-0", graph.session.currentRefreshToken())
    }

    @Test
    fun `resume offline with no cached profile signs out but keeps the token`() = runTest {
        val recording = RecordingEngine { throw RuntimeException("network down") }
        val graph = graph(recording)

        assertEquals(SessionState.SignedOut, graph.repository.resume())
        assertEquals("refresh-0", graph.session.currentRefreshToken())
    }

    @Test
    fun `a server error on refresh does not end the session`() = runTest {
        val recording = RecordingEngine {
            json(errorBody("internal_error", "Xatolik"), HttpStatusCode.InternalServerError)
        }
        val graph = graph(recording)
        graph.session.updateProfile(testProfile(), testEntitlements())

        assertIs<SessionState.SignedIn>(graph.repository.resume())
        assertEquals("refresh-0", graph.session.currentRefreshToken())
    }

    /** A 401 whose refresh then cannot reach the server leaves her signed in. */
    @Test
    fun `a failed refresh on a 401 keeps the session when the network is down`() = runTest {
        var online = true
        val recording = RecordingEngine { request ->
            when {
                !online -> throw RuntimeException("network down")
                request.url.encodedPath == "/v1/auth/refresh" -> {
                    online = false
                    throw RuntimeException("network down")
                }
                else -> json(errorBody(ErrorCodes.TOKEN_REVOKED, "Muddati tugagan"), HttpStatusCode.Unauthorized)
            }
        }
        val graph = graph(recording)
        graph.session.updateProfile(testProfile(), testEntitlements())

        val result = graph.repository.refreshEntitlements()

        assertIs<ApiResult.Failure>(result)
        assertEquals("refresh-0", graph.session.currentRefreshToken())
        assertIs<SessionState.SignedIn>(graph.session.state.value)
    }

    @Test
    fun `sign out removes the cached profile too`() = runTest {
        val recording = RecordingEngine { throw RuntimeException("network down") }
        val graph = graph(recording)
        graph.session.updateProfile(testProfile(), testEntitlements())

        graph.repository.signOut()

        assertEquals(null, graph.session.cachedSession())
    }

    /** A valid session plus a failing bootstrap is a network problem, not a sign-out. */
    @Test
    fun `resume keeps the session when only bootstrap fails`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/auth/refresh" -> json(encode(testAuthSession()))
                else -> throw RuntimeException("network down")
            }
        }
        val state = assertIs<SessionState.SignedIn>(graph(recording).repository.resume())
        assertEquals("Malika", state.user.name)
    }

    @Test
    fun `a user who has not finished onboarding is flagged for it`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/auth/refresh" ->
                    json(encode(testAuthSession(onboardingCompleted = false)))

                "/v1/bootstrap" ->
                    json(encode(bootstrap().copy(user = testProfile(onboardingCompleted = false))))

                else -> json("{}", HttpStatusCode.NotFound)
            }
        }
        val state = assertIs<SessionState.SignedIn>(graph(recording).repository.resume())
        assertTrue(state.needsOnboarding)
    }

    @Test
    fun `verifying an OTP stores the tokens and publishes the session`() = runTest {
        val recording = RecordingEngine { json(encode(testAuthSession(isNewUser = true))) }
        val graph = graph(recording, storedToken = null)

        val result = graph.repository.verifyOtp("challenge-1", "123456")

        assertTrue(result is ApiResult.Success)
        assertEquals("refresh-1", graph.session.currentRefreshToken())
        assertIs<SessionState.SignedIn>(graph.session.state.value)
    }

    /**
     * Signing out on a plane must still sign the user out. The refresh token left behind
     * expires on its own.
     */
    @Test
    fun `sign out clears the local session even when the server call fails`() = runTest {
        val recording = RecordingEngine { throw RuntimeException("network down") }
        val graph = graph(recording)

        graph.repository.signOut()

        assertEquals(null, graph.session.currentRefreshToken())
        assertEquals(SessionState.SignedOut, graph.session.state.value)
    }

    @Test
    fun `refreshing entitlements updates the published session in place`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/auth/refresh" -> json(encode(testAuthSession()))
                "/v1/bootstrap" -> json(encode(bootstrap(SubscriptionTier.FREE)))
                else -> json(encode(testEntitlements(SubscriptionTier.PREMIUM)))
            }
        }
        val graph = graph(recording)
        graph.repository.resume()
        assertEquals(
            SubscriptionTier.FREE,
            (graph.session.state.value as SessionState.SignedIn).entitlements.tier,
        )

        graph.repository.refreshEntitlements()

        assertEquals(
            SubscriptionTier.PREMIUM,
            (graph.session.state.value as SessionState.SignedIn).entitlements.tier,
        )
    }
}
