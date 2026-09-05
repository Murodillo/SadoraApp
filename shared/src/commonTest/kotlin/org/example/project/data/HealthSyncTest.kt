package org.example.project.data

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.example.project.model.AppState
import org.example.project.model.Mood
import org.example.project.model.PracticeKind
import uz.sadora.contract.CyclePhase
import uz.sadora.contract.CyclePrediction
import uz.sadora.contract.CycleStatus
import uz.sadora.contract.DoseStatus
import uz.sadora.contract.FoodRelation
import uz.sadora.contract.MedicationDay
import uz.sadora.contract.MedicationDose
import uz.sadora.contract.MindCheckIn
import uz.sadora.contract.MindPractice
import uz.sadora.contract.MindPracticeKind
import uz.sadora.contract.MindSummary
import uz.sadora.contract.PredictionConfidence
import uz.sadora.contract.PredictionReasons

/**
 * The sink is the only way a screen's edit reaches the server, so each new edit —
 * the Mind check-in, a finished practice, a skipped dose — is pinned to the request
 * it must produce.
 */
class HealthSyncTest {

    private val today = LocalDate.parse("2026-09-04")

    private fun status() = CycleStatus(
        cycleDay = 6,
        phase = CyclePhase.FOLLICULAR,
        prediction = CyclePrediction(PredictionConfidence.HIGH, PredictionReasons.SUFFICIENT),
        today = today,
    )

    private fun graph(recording: RecordingEngine) = SadoraGraph(
        tokenStorage = InMemoryTokenStorage(token = "refresh-0"),
        device = FixedDeviceIdentity(),
        environment = SadoraEnvironment("http://test.local"),
        engine = recording.build(),
    )

    private fun body(request: io.ktor.client.request.HttpRequestData): String =
        (request.body as TextContent).text

    /**
     * The sink launches its request and returns; the mock engine answers on a real
     * dispatcher, so the test waits for the launched job itself rather than for
     * virtual time.
     */
    private suspend fun kotlinx.coroutines.CoroutineScope.awaitLaunched() {
        coroutineContext.job.children.toList().forEach { it.join() }
    }

    @Test
    fun `a check-in goes up as one record with all three dials`() = runTest {
        var sent: String? = null
        var method: HttpMethod? = null
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/mind/check-in" -> {
                    sent = body(request)
                    method = request.method
                    json(encode(MindCheckIn()))
                }
                "/v1/mind/today" -> json(encode(MindSummary(today = today, checkIn = MindCheckIn())))
                "/v1/cycle/status" -> json(encode(status()))
                "/v1/days/2026-09-04" -> json(encode(uz.sadora.contract.DailyLog(date = today)))
                else -> json("{}", HttpStatusCode.NotFound)
            }
        }
        val state = AppState()
        val controller = graph(recording).healthController(state)
        controller.refreshCycle()
        state.sync = HealthSync(controller, this)

        state.setCheckIn(mood = Mood.Great, energy = 5, stress = 1)
        awaitLaunched()

        assertEquals(HttpMethod.Put, method)
        val json = sent ?: error("nothing was sent")
        assertTrue("\"mood\":\"great\"" in json, json)
        assertTrue("\"energy\":5" in json, json)
        assertTrue("\"stress\":1" in json, json)
    }

    @Test
    fun `a finished meditation is logged as a practice of that kind`() = runTest {
        var sent: String? = null
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/mind/practices" -> {
                    sent = body(request)
                    json(encode(MindPractice("p1", MindPracticeKind.MEDITATION, 600, TestNow)))
                }
                "/v1/mind/today" -> json(encode(MindSummary(today = today, checkIn = MindCheckIn())))
                else -> json("{}", HttpStatusCode.NotFound)
            }
        }
        val state = AppState()
        val controller = graph(recording).healthController(state)
        state.sync = HealthSync(controller, this)

        state.logPractice(PracticeKind.Meditation, 600)
        awaitLaunched()

        val json = sent ?: error("nothing was sent")
        assertTrue("\"kind\":\"meditation\"" in json, json)
        assertTrue("\"durationSeconds\":600" in json, json)
    }

    @Test
    fun `skipping a dose records it as skipped not taken`() = runTest {
        var sent: String? = null
        val day = MedicationDay(
            date = today,
            doses = listOf(
                MedicationDose(
                    medicationId = "med-1",
                    name = "Temir",
                    emoji = "🩸",
                    dosage = "30",
                    foodRelation = FoodRelation.AFTER,
                    dueOn = today,
                    dueAt = LocalTime.parse("20:00"),
                    status = DoseStatus.PENDING,
                ),
            ),
        )
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/meds/med-1/doses" -> {
                    sent = body(request)
                    json(encode(day.copy(doses = day.doses.map { it.copy(status = DoseStatus.SKIPPED) })))
                }
                "/v1/meds/today" -> json(encode(day))
                "/v1/meds" -> json("[]")
                "/v1/cycle/status" -> json(encode(status()))
                else -> json("{}", HttpStatusCode.NotFound)
            }
        }
        val state = AppState()
        val controller = graph(recording).healthController(state)
        controller.refreshMedications()
        state.sync = HealthSync(controller, this)

        // The store's id packs the course and the time together.
        state.markMedicationSkipped("med-1@20:00")
        awaitLaunched()

        val json = sent ?: error("nothing was sent")
        assertTrue("\"status\":\"skipped\"" in json, json)
        assertEquals(
            org.example.project.model.MedStatus.Skipped,
            state.medications.single().status,
        )
    }
}
