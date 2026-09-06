package org.example.project.data

import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.example.project.model.AppState
import org.example.project.model.MedStatus
import uz.sadora.contract.CyclePhase
import uz.sadora.contract.CyclePrediction
import uz.sadora.contract.CycleStatus
import uz.sadora.contract.DailyLog
import uz.sadora.contract.DoseStatus
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.FoodRelation
import uz.sadora.contract.Medication
import uz.sadora.contract.MedicationDay
import uz.sadora.contract.MedicationDose
import uz.sadora.contract.MedicationSchedule
import uz.sadora.contract.MindSummary
import uz.sadora.contract.MoodLevel
import uz.sadora.contract.NutritionDay
import uz.sadora.contract.NutritionGoals
import uz.sadora.contract.NutritionTotals
import uz.sadora.contract.PredictionConfidence
import uz.sadora.contract.PredictionReasons
import uz.sadora.contract.SymptomCategory
import uz.sadora.contract.SymptomDefinition
import uz.sadora.contract.SymptomEntry
import uz.sadora.contract.SymptomSeverity

class HealthControllerTest {

    private val today = LocalDate.parse("2026-09-02")

    private fun status(cycleDay: Int? = 14) = CycleStatus(
        cycleDay = cycleDay,
        phase = CyclePhase.FERTILE,
        phaseIsPredicted = true,
        prediction = CyclePrediction(
            confidence = PredictionConfidence.HIGH,
            reason = PredictionReasons.SUFFICIENT,
            averageCycleLength = 29,
            averagePeriodLength = 6,
        ),
        today = today,
    )

    private fun nutrition(water: Int = 500, kcal: Int = 860) = NutritionDay(
        date = today,
        meals = emptyList(),
        totals = NutritionTotals(kcal = kcal, proteinG = 52, fatG = 30, carbsG = 90),
        goals = NutritionGoals(calorieGoal = 2000, waterGoalMl = 2500),
        waterMl = water,
    )

    private fun medicationDay(status: DoseStatus = DoseStatus.PENDING) = MedicationDay(
        date = today,
        doses = listOf(
            MedicationDose(
                medicationId = "med-1",
                name = "Temir",
                emoji = "🩸",
                dosage = "30",
                foodRelation = FoodRelation.AFTER,
                dueOn = today,
                dueAt = LocalTime.parse("08:00"),
                status = status,
            ),
        ),
    )

    private fun medication() = Medication(
        id = "med-1",
        name = "Temir",
        unit = "mg",
        schedule = MedicationSchedule(times = listOf(LocalTime.parse("08:00"))),
        startedOn = today,
        stockUnits = 18,
        stockDaysLeft = 18,
        createdAt = TestNow,
    )

    private val catalogue = listOf(
        SymptomDefinition("cramps", "Og'riq", SymptomCategory.PAIN),
        SymptomDefinition("fatigue", "Charchoq", SymptomCategory.ENERGY),
    )

    private fun graph(recording: RecordingEngine) = SadoraGraph(
        tokenStorage = InMemoryTokenStorage(token = "refresh-0"),
        device = FixedDeviceIdentity(),
        environment = SadoraEnvironment("http://test.local"),
        engine = recording.build(),
    )

    private fun defaultHandler(): RecordingEngine = RecordingEngine { request ->
        when (request.url.encodedPath) {
            "/v1/cycle/status" -> json(encode(status()))
            "/v1/cycle/calendar" -> json(encode(uz.sadora.contract.CycleCalendar(today, today, emptyList(), status().prediction)))
            "/v1/days/2026-09-02" -> json(encode(DailyLog(date = today, mood = MoodLevel.GOOD, symptoms = listOf(SymptomEntry("cramps")))))
            "/v1/symptoms" -> json(encode(catalogue))
            "/v1/mind/today" -> json(encode(MindSummary(today = today, checkIn = uz.sadora.contract.MindCheckIn())))
            "/v1/nutrition/today" -> json(encode(nutrition()))
            "/v1/meds/today" -> json(encode(medicationDay()))
            "/v1/meds" -> json(encode(listOf(medication())))
            "/v1/health-data/today" -> json(encode(uz.sadora.contract.DailyHealth(today)))
            else -> json("{}", HttpStatusCode.NotFound)
        }
    }

    @Test
    fun `loading fills the controller and the store the screens read`() = runTest {
        val state = AppState()
        val controller = graph(defaultHandler()).healthController(state)

        controller.loadAll()

        assertEquals(14, controller.cycle?.cycleDay)
        assertEquals(today, controller.selectedDate)
        assertEquals(2, controller.symptoms.size)

        // The screens read AppState, so that is what has to end up correct.
        assertEquals(14, state.cycleDay)
        assertEquals(29, state.averageCycleLength)
        assertEquals(500, state.waterMl)
        assertEquals(2500, state.waterGoalMl)
        assertEquals(860, state.caloriesEaten)
        assertEquals(2000, state.calorieGoal)
        assertEquals(listOf("Og'riq"), state.symptoms.toList())
        assertEquals(1, state.medications.size)
        assertEquals("Temir 30 mg", state.medications.single().name)
        assertEquals(MedStatus.Pending, state.medications.single().status)
        assertEquals(18, state.medications.single().stockDays)
    }

    /** A symptom the server does not offer has no key, so there is nothing to send. */
    @Test
    fun `an unknown symptom label resolves to nothing`() = runTest {
        val controller = graph(defaultHandler()).healthController(AppState())
        controller.loadSymptoms(null)
        val sync = HealthSync(controller, this)

        assertEquals("fatigue", sync.resolveSymptomKey("Charchoq"))
        assertEquals(null, sync.resolveSymptomKey("Mars sindromi"))
    }

    @Test
    fun `toggling a symptom sends the whole day rather than just the change`() = runTest {
        var savedKeys: List<String> = emptyList()
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/days/2026-09-02" ->
                    if (request.method == io.ktor.http.HttpMethod.Put) {
                        val body = (request.body as io.ktor.http.content.TextContent).text
                        savedKeys = Regex("\"key\":\"([a-z_]+)\"")
                            .findAll(body).map { it.groupValues[1] }.toList()
                        json(encode(DailyLog(date = today, symptoms = savedKeys.map { SymptomEntry(it) })))
                    } else {
                        json(encode(DailyLog(date = today, symptoms = listOf(SymptomEntry("cramps")))))
                    }

                "/v1/cycle/status" -> json(encode(status()))
                "/v1/symptoms" -> json(encode(catalogue))
                else -> json("{}", HttpStatusCode.NotFound)
            }
        }
        val controller = graph(recording).healthController(AppState())
        controller.refreshCycle()
        controller.loadSymptoms(null)
        controller.loadDay(today)

        assertTrue(controller.toggleSymptom(today, "fatigue"))

        // The server replaces the day wholesale, so the symptom already recorded has to
        // go back up with the new one — otherwise adding one would delete the other.
        assertEquals(listOf("cramps", "fatigue"), savedKeys)
    }

    @Test
    fun `water goes to the server and the store takes the server's total`() = runTest {
        var posted = 0
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/nutrition/water" -> {
                    posted++
                    json(encode(uz.sadora.contract.WaterState(today, 750, 2500)))
                }
                "/v1/nutrition/today" -> json(encode(nutrition(water = if (posted > 0) 750 else 500)))
                "/v1/cycle/status" -> json(encode(status()))
                else -> json("{}", HttpStatusCode.NotFound)
            }
        }
        val state = AppState()
        val controller = graph(recording).healthController(state)
        controller.refreshCycle()
        controller.refreshNutrition()
        assertEquals(500, state.waterMl)

        assertTrue(controller.addWater(250))
        assertEquals(1, posted)
        assertEquals(750, state.waterMl, "the server's total replaces the optimistic one")
    }

    @Test
    fun `recording a dose updates the day and the pack figure`() = runTest {
        var taken = false
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/meds/med-1/doses" -> {
                    taken = true
                    json(encode(medicationDay(DoseStatus.TAKEN)))
                }
                "/v1/meds" -> json(encode(listOf(medication().copy(stockUnits = 17, stockDaysLeft = 17))))
                "/v1/meds/today" -> json(encode(medicationDay()))
                "/v1/cycle/status" -> json(encode(status()))
                else -> json("{}", HttpStatusCode.NotFound)
            }
        }
        val state = AppState()
        val controller = graph(recording).healthController(state)
        controller.refreshMedications()

        assertTrue(
            controller.recordDose("med-1", today, LocalTime.parse("08:00"), DoseStatus.TAKEN),
        )
        assertTrue(taken)
        assertEquals(MedStatus.Taken, state.medications.single().status)
        assertEquals(17, state.medications.single().stockDays)
    }

    /** Consent is the one refusal the user can act on, so it must reach her in words. */
    @Test
    fun `a missing health consent surfaces as a readable message`() = runTest {
        val recording = RecordingEngine {
            json(
                errorBody(ErrorCodes.CONSENT_REQUIRED, "Rozilik kerak", mapOf("consent" to "store_health")),
                HttpStatusCode.Forbidden,
            )
        }
        val controller = graph(recording).healthController(AppState())

        assertTrue(!controller.addWater(250))
        val message = controller.error
        assertTrue(message != null && message.isNotBlank(), "the screen needs something to show")
    }

    @Test
    fun `with no backend every write reports success and nothing is sent`() = runTest {
        val controller = HealthController(null, null, null, null)
        assertTrue(controller.isOffline)
        assertTrue(controller.addWater(250))
        assertTrue(controller.saveCheckIn(MoodLevel.GOOD, 3, 2))
        assertIs<Nothing?>(controller.cycle)
    }
}
