package uz.sadora.doctor.data

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.Language
import uz.sadora.contract.OtpChallenge
import uz.sadora.contract.UserProfile

/**
 * Signing in and out against the same endpoints the client app uses: the number goes
 * out as +998…, a development server's code is filled in, a verified code stores the
 * session, and only the server saying no ends one.
 */
class AuthTest {

    private val challenge = OtpChallenge(
        challengeId = "ch-1",
        expiresAt = TestNow,
        resendAfterSeconds = 60,
        attemptsLeft = 5,
        devCode = "123456",
    )

    @Test
    fun `requesting a code sends the full number in her language and fills in a dev code`() = runTest {
        val recording = RecordingEngine { json(encode(challenge)) }
        val auth = testGraph(recording, storedToken = null).authController()

        auth.updatePhone("90 123 45 67")
        assertTrue(auth.phoneReady)
        assertTrue(auth.requestCode(Language.RU))

        assertEquals("ch-1", auth.challenge?.challengeId)
        assertEquals("123456", auth.code)
        assertTrue(auth.codeReady)
        val sent = recording.bodyOf(HttpMethod.Post, "/v1/auth/otp/request").orEmpty()
        assertTrue("\"phone\":\"+998901234567\"" in sent, sent)
        assertTrue("\"language\":\"ru\"" in sent, sent)
    }

    @Test
    fun `a verified code stores the session and signs her in`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/auth/otp/request" -> json(encode(challenge.copy(devCode = null)))
                "/v1/auth/otp/verify" -> json(encode(testAuthSession()))
                else -> json(errorBody(ErrorCodes.NOT_FOUND), HttpStatusCode.NotFound)
            }
        }
        val graph = testGraph(recording, storedToken = null)
        val auth = graph.authController()
        auth.updatePhone("901234567")
        auth.requestCode(Language.UZ)
        assertEquals("", auth.code)
        auth.updateCode("12a3456789")
        assertEquals("123456", auth.code)

        assertTrue(auth.verify())

        val state = assertIs<SessionState.SignedIn>(graph.session.state.value)
        assertEquals("+998901234567", state.user.phone)
        assertEquals("refresh-1", graph.tokenStorage.readRefreshToken())
        val sent = recording.bodyOf(HttpMethod.Post, "/v1/auth/otp/verify").orEmpty()
        assertTrue("\"challengeId\":\"ch-1\"" in sent, sent)
        assertTrue("\"platform\":\"android\"" in sent, sent)
    }

    @Test
    fun `a wrong code says so and keeps her on the code step`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/auth/otp/request" -> json(encode(challenge))
                else -> json(errorBody(ErrorCodes.OTP_INVALID, "Kod noto'g'ri"), HttpStatusCode.BadRequest)
            }
        }
        val graph = testGraph(recording, storedToken = null)
        val auth = graph.authController()
        auth.updatePhone("901234567")
        auth.requestCode(Language.UZ)

        assertFalse(auth.verify())

        assertIs<ApiFailure.Otp>(auth.error)
        assertNotNull(auth.challenge)
        assertNull(graph.tokenStorage.readRefreshToken())
        // Changing the number goes back to the first step and drops the failure with it.
        auth.changeNumber()
        assertNull(auth.challenge)
        assertNull(auth.error)
        assertEquals("901234567", auth.phone)
    }

    @Test
    fun `a stored session is resumed with a refresh`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/auth/refresh" -> json(encode(testAuthSession(refresh = "refresh-2")))
                else -> json(errorBody(ErrorCodes.NOT_FOUND), HttpStatusCode.NotFound)
            }
        }
        val graph = testGraph(recording)

        assertIs<SessionState.SignedIn>(graph.repository.resume())
        assertEquals("refresh-2", graph.tokenStorage.readRefreshToken())
    }

    @Test
    fun `a revoked token signs her out and is forgotten`() = runTest {
        val recording = RecordingEngine { json(errorBody(ErrorCodes.TOKEN_REVOKED), HttpStatusCode.Unauthorized) }
        val graph = testGraph(recording)

        assertEquals(SessionState.SignedOut, graph.repository.resume())
        assertNull(graph.tokenStorage.readRefreshToken())
    }

    @Test
    fun `no connection at launch keeps the session and opens on the last profile`() = runTest {
        val storage = InMemoryTokenStorage(token = "refresh-0")
        storage.writeSessionSnapshot(SadoraJson.encodeToString(UserProfile.serializer(), testProfile()))
        val recording = RecordingEngine { error("no route to host") }
        val graph = DoctorGraph(
            tokenStorage = storage,
            device = FixedDeviceIdentity(),
            environment = SadoraEnvironment("http://test.local"),
            engine = recording.build(),
        )

        val resumed = assertIs<SessionState.SignedIn>(graph.repository.resume())

        assertEquals("Nodira", resumed.user.name)
        assertEquals("refresh-0", storage.readRefreshToken())
    }

    @Test
    fun `signing out clears the session even when the server cannot be reached`() = runTest {
        val recording = RecordingEngine { error("offline") }
        val graph = testGraph(recording)
        val auth = graph.authController()

        auth.signOut()

        assertEquals(SessionState.SignedOut, graph.session.state.value)
        assertNull(graph.tokenStorage.readRefreshToken())
        assertEquals("", auth.phone)
    }
}
