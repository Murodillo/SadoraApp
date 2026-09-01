package org.example.project.data

import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.example.project.model.AppState
import org.example.project.model.Goal
import org.example.project.model.LifeStage
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.OnboardingRequest
import uz.sadora.contract.SubscriptionTier

/**
 * The controller is what every screen calls, so these cover the things a screen would
 * otherwise get wrong: leaving the user mid-flow on a failure, or advancing past one.
 */
class SadoraControllerTest {

    private fun controller(
        state: AppState = AppState(),
        recording: RecordingEngine,
    ): Pair<SadoraController, AppState> {
        val graph = SadoraGraph(
            tokenStorage = InMemoryTokenStorage(token = "refresh-0"),
            device = FixedDeviceIdentity(),
            environment = SadoraEnvironment("http://test.local"),
            engine = recording.build(),
        )
        return SadoraController(graph.repository, state) to state
    }

    // ---------------------------------------------------------------- offline

    @Test
    fun `with no backend every action succeeds locally so the prototype still runs`() = runTest {
        val state = AppState()
        val offline = SadoraController(repository = null, state = state)

        assertTrue(offline.isOffline)
        assertTrue(offline.completeOnboarding())
        assertTrue(offline.saveProfile())
        assertTrue(offline.saveConsents())
        // With no backend a code cannot be checked, so the flow continues into onboarding.
        assertEquals(AuthDestination.Onboarding, offline.verifyOtp("challenge-1", "123456"))
        assertNull(offline.error)
    }

    // ---------------------------------------------------------------- onboarding

    @Test
    fun `onboarding sends what the user actually chose`() = runTest {
        val recording = RecordingEngine {
            json(SadoraJson.encodeToString(testProfile(onboardingCompleted = true)))
        }
        val (controller, state) = controller(recording = recording)
        state.name = "Nilufar"
        state.lifeStage = LifeStage.Pregnancy
        state.goals.clear()
        state.goals.add(Goal.SleepBetter)
        state.birthDate = "14.03.1994"
        state.heightCm = "164"
        state.weightKg = "58"

        assertTrue(controller.completeOnboarding())

        val sent = SadoraJson.decodeFromString<OnboardingRequest>(recording.bodies.last())
        assertEquals("Nilufar", sent.name)
        assertEquals(uz.sadora.contract.LifeStage.PREGNANCY, sent.lifeStage)
        assertEquals(listOf(uz.sadora.contract.Goal.SLEEP_BETTER), sent.goals)
        assertEquals(164, sent.heightCm)
        assertEquals("1994-03-14", sent.birthDate.toString())
        // Pregnancy does not predict a cycle, so no baseline is sent for it.
        assertNull(sent.cycle)
    }

    @Test
    fun `a failed onboarding submit reports false so the flow keeps the user on the step`() =
        runTest {
            val recording = RecordingEngine {
                json(
                    errorBody(ErrorCodes.VALIDATION_FAILED, "Xato", mapOf("name" to "Ism bo'sh")),
                    HttpStatusCode.BadRequest,
                )
            }
            val (controller, _) = controller(recording = recording)

            assertFalse(controller.completeOnboarding())
            assertEquals("Ism bo'sh", controller.error)
            assertFalse(controller.busy)
        }

    // ---------------------------------------------------------------- auth

    @Test
    fun `a user whose onboarding is unfinished is routed back into the flow`() = runTest {
        val recording = RecordingEngine {
            json(SadoraJson.encodeToString(testAuthSession(onboardingCompleted = false)))
        }
        val (controller, state) = controller(recording = recording)

        assertEquals(AuthDestination.Onboarding, controller.verifyOtp("challenge-1", "123456"))
        // The profile still lands, so the flow resumes with what the server already knows.
        assertEquals("Malika", state.name)
    }

    @Test
    fun `a completed account goes straight to the app`() = runTest {
        val recording = RecordingEngine {
            json(SadoraJson.encodeToString(testAuthSession(onboardingCompleted = true)))
        }
        val (controller, _) = controller(recording = recording)

        assertEquals(AuthDestination.Main, controller.verifyOtp("challenge-1", "123456"))
    }

    @Test
    fun `a wrong code returns null and leaves a readable message`() = runTest {
        val recording = RecordingEngine {
            json(errorBody(ErrorCodes.OTP_INVALID, "Kod noto'g'ri"), HttpStatusCode.BadRequest)
        }
        val (controller, _) = controller(recording = recording)

        assertNull(controller.verifyOtp("challenge-1", "000000"))
        assertEquals("Kod noto'g'ri", controller.error)
    }

    // ---------------------------------------------------------------- entitlements

    @Test
    fun `refreshing entitlements is what flips the app to Premium`() = runTest {
        // Entitlements is an authenticated call, so the client refreshes the session
        // first; the handler has to answer both requests.
        val recording = RecordingEngine { request ->
            if (request.url.encodedPath.endsWith("/auth/refresh")) {
                json(SadoraJson.encodeToString(testAuthSession()))
            } else {
                json(SadoraJson.encodeToString(testEntitlements(SubscriptionTier.PREMIUM)))
            }
        }
        val (controller, state) = controller(recording = recording)
        assertFalse(state.isPremium)

        controller.refreshEntitlements()

        assertTrue(state.isPremium)
    }

    @Test
    fun `a background entitlement refresh that fails does not raise a banner`() = runTest {
        val recording = RecordingEngine {
            json(errorBody(ErrorCodes.INTERNAL_ERROR, "boom"), HttpStatusCode.InternalServerError)
        }
        val (controller, _) = controller(recording = recording)

        controller.refreshEntitlements()

        assertNull(controller.error)
        assertFalse(controller.busy)
    }

    // ---------------------------------------------------------------- phone

    @Test
    fun `the phone field is normalised to E164 however it was typed`() {
        assertEquals("+998901234567", normalizePhone("90 123 45 67"))
        assertEquals("+998901234567", normalizePhone("+998 90 123 45 67"))
        assertEquals("+998901234567", normalizePhone("998901234567"))
    }

    // ---------------------------------------------------------------- dates

    @Test
    fun `a half-typed birth date is dropped rather than crashing the request carrying it`() {
        assertNotNull("14.03.1994".toWireDate())
        assertNull("14.03".toWireDate())
        assertNull("".toWireDate())
        assertNull("32.13.1994".toWireDate())
    }
}
