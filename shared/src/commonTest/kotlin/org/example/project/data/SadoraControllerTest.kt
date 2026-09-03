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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.example.project.model.AppState
import org.example.project.model.BirthControl
import org.example.project.model.ConceptionWindow
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
    fun `a cycle user sends the baseline the predictor anchors on`() = runTest {
        val recording = RecordingEngine {
            json(SadoraJson.encodeToString(testProfile(onboardingCompleted = true)))
        }
        val (controller, state) = controller(recording = recording)
        state.lifeStage = LifeStage.TryingToConceive
        state.togglePeriodDay(LocalDate(2026, 8, 20), LocalDate(2026, 9, 3))
        state.averageCycleLength = 30
        state.averagePeriodLength = 6
        state.cycleIsRegular = false
        state.conceptionWindow = ConceptionWindow.ThreeToSix
        state.birthControl = BirthControl.Pill

        assertTrue(controller.completeOnboarding())

        val sent = SadoraJson.decodeFromString<OnboardingRequest>(recording.bodies.last())
        val cycle = assertNotNull(sent.cycle)
        assertEquals(LocalDate(2026, 8, 20), cycle.lastPeriodStart)
        assertEquals(30, cycle.averageCycleLength)
        assertEquals(6, cycle.averagePeriodLength)
        assertFalse(cycle.cycleIsRegular)
        assertEquals(uz.sadora.contract.ConceptionWindow.THREE_TO_SIX_MONTHS, cycle.conceptionWindow)
        assertEquals(uz.sadora.contract.BirthControl.PILL, cycle.birthControl)
    }

    @Test
    fun `a pregnancy user sends the due date instead of a cycle baseline`() = runTest {
        val recording = RecordingEngine {
            json(SadoraJson.encodeToString(testProfile(onboardingCompleted = true)))
        }
        val (controller, state) = controller(recording = recording)
        state.lifeStage = LifeStage.Pregnancy
        state.dueDate = LocalDate(2027, 1, 12)

        assertTrue(controller.completeOnboarding())

        val sent = SadoraJson.decodeFromString<OnboardingRequest>(recording.bodies.last())
        assertNull(sent.cycle)
        assertEquals(LocalDate(2027, 1, 12), assertNotNull(sent.stage).dueDate)
    }

    @Test
    fun `the cycle day is derived locally so Today is right before the first sync`() {
        val state = AppState().apply {
            togglePeriodDay(LocalDate(2026, 9, 1), LocalDate(2026, 10, 1))
            averageCycleLength = 28
        }

        state.recomputeCycleDay(LocalDate(2026, 9, 10))
        assertEquals(10, state.cycleDay)

        // Past the end of one cycle it wraps rather than counting on forever.
        state.recomputeCycleDay(LocalDate(2026, 10, 1))
        assertEquals(3, state.cycleDay)
    }

    private fun cycleState(periodLength: Int = 5) =
        AppState().apply { averagePeriodLength = periodLength }

    /** Nothing in these tests is near today, so the future guard never bites. */
    private val far = LocalDate(2027, 1, 1)

    @Test
    fun `three marked periods give the average the server would measure`() {
        val state = cycleState()
        // 29 and 30 days apart.
        listOf(LocalDate(2026, 7, 1), LocalDate(2026, 7, 30), LocalDate(2026, 8, 29))
            .forEach { state.togglePeriodDay(it, far) }

        assertEquals(listOf(29, 30), state.observedCycleLengths())
        assertEquals(29, state.averageFromEnteredCycles())
        assertEquals(LocalDate(2026, 8, 29), state.lastPeriodStart)
    }

    @Test
    fun `marking a fourth period drops the oldest`() {
        val state = cycleState()
        listOf(
            LocalDate(2026, 6, 2),
            LocalDate(2026, 7, 1),
            LocalDate(2026, 7, 30),
            LocalDate(2026, 8, 29),
        ).forEach { state.togglePeriodDay(it, far) }

        assertEquals(3, state.recentPeriodStarts.size)
        assertEquals(LocalDate(2026, 7, 1), state.recentPeriodStarts.first())
    }

    @Test
    fun `one tap fills a typical period, and she can then edit it day by day`() {
        val state = cycleState(periodLength = 5)
        state.togglePeriodDay(LocalDate(2026, 8, 25), far)

        // The convenience: five days from one tap.
        assertEquals(
            listOf(LocalDate(2026, 8, 25)..LocalDate(2026, 8, 29)),
            state.periodRuns().map { it.first()..it.last() },
        )

        // Shorter: the last day comes off on its own, the rest stay.
        state.togglePeriodDay(LocalDate(2026, 8, 29), far)
        assertEquals(4, state.markedPeriodDays.size)
        assertTrue(state.isPeriodDay(LocalDate(2026, 8, 28)))

        // Longer: a day touching the run joins it rather than starting a new period.
        state.togglePeriodDay(LocalDate(2026, 8, 24), far)
        assertEquals(1, state.periodRuns().size)
        assertEquals(LocalDate(2026, 8, 24), state.lastPeriodStart)
    }

    @Test
    fun `she can cut a period back to the single day she is sure of`() {
        val state = cycleState(periodLength = 5)
        val start = LocalDate(2026, 8, 25)
        state.togglePeriodDay(start, far)

        (1..4).forEach { state.togglePeriodDay(start.plus(it, DateTimeUnit.DAY), far) }

        assertEquals(listOf(start), state.markedPeriodDays.toList())
        assertEquals(start, state.lastPeriodStart)
    }

    @Test
    fun `the fill-in never runs past today`() {
        val today = LocalDate(2026, 9, 3)
        val state = cycleState(periodLength = 5)

        state.togglePeriodDay(LocalDate(2026, 9, 1), today)

        assertEquals(
            listOf(LocalDate(2026, 9, 1), LocalDate(2026, 9, 2), today),
            state.markedPeriodDays.sorted(),
        )
    }

    @Test
    fun `an implausible gap is not averaged into the cycle length`() {
        val state = cycleState(periodLength = 1)
        // Four days apart is a mistap, not a cycle.
        state.togglePeriodDay(LocalDate(2026, 8, 25), far)
        state.togglePeriodDay(LocalDate(2026, 8, 29), far)

        assertEquals(2, state.recentPeriodStarts.size)
        assertTrue(state.observedCycleLengths().isEmpty())
        assertNull(state.averageFromEnteredCycles())
    }

    @Test
    fun `with no period answered the cycle day is left alone`() {
        val state = AppState().apply { cycleDay = 7 }

        state.recomputeCycleDay(LocalDate(2026, 9, 10))

        assertEquals(7, state.cycleDay)
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
    }

    @Test
    fun `signing up midway through onboarding keeps the answers given so far`() = runTest {
        // Sign-up comes after the profile questions, so the account created here has an
        // empty profile on the server while every answer lives only on the client.
        // Applying that profile would wipe the lot.
        val state = AppState().apply {
            name = "Nilufar"
            goals.add(Goal.SleepBetter)
        }
        val recording = RecordingEngine {
            json(SadoraJson.encodeToString(testAuthSession(onboardingCompleted = false)))
        }
        val (controller, _) = controller(state = state, recording = recording)

        assertEquals(AuthDestination.Onboarding, controller.verifyOtp("challenge-1", "123456"))
        assertEquals("Nilufar", state.name)
        assertEquals(listOf(Goal.SleepBetter), state.goals.toList())
    }

    @Test
    fun `a completed profile from the server replaces the local one`() = runTest {
        val state = AppState().apply { name = "Nilufar" }
        val recording = RecordingEngine {
            json(SadoraJson.encodeToString(testAuthSession(onboardingCompleted = true)))
        }
        val (controller, _) = controller(state = state, recording = recording)

        assertEquals(AuthDestination.Main, controller.verifyOtp("challenge-1", "123456"))
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
