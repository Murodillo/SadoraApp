package uz.sadora.doctor.data

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import uz.sadora.contract.Ack
import uz.sadora.contract.Consultation
import uz.sadora.contract.ConsultationPatient
import uz.sadora.contract.ConsultationPayment
import uz.sadora.contract.ConsultationSession
import uz.sadora.contract.Conversation
import uz.sadora.contract.ConversationThread
import uz.sadora.contract.DoctorHours
import uz.sadora.contract.DoctorSettings
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.LifeStage
import uz.sadora.contract.PatientHistory
import uz.sadora.contract.PatientNote
import uz.sadora.contract.QuickReply

/**
 * Her working day against a mock server. What is pinned: the busy switch goes back when
 * the server refuses it, the price and week go out together, quick replies are kept in
 * step with the server, a patient's note and history belong to that patient, closing
 * sends her advice, and the push token goes out as the doctor app's.
 */
class WorkControllerTest {

    private val settings = DoctorSettings(priceMinor = 5_000_000, busy = false, commissionPercent = 20)

    @Test
    fun `the busy switch follows the server and goes back when it refuses`() = runTest {
        var refuse = false
        val recording = RecordingEngine { request ->
            when {
                request.method == HttpMethod.Get -> json(encode(settings))
                refuse -> json(errorBody(ErrorCodes.FORBIDDEN), HttpStatusCode.Forbidden)
                else -> json(encode(settings.copy(busy = true)))
            }
        }
        val work = testGraph(recording).workController()
        work.loadSettings(silent = false)

        assertTrue(work.setBusy(true))
        assertEquals(true, work.settings?.busy)
        assertEquals("""{"busy":true}""", recording.bodyOf(HttpMethod.Put, "/v1/doctor/settings"))

        refuse = true
        assertFalse(work.setBusy(false))
        assertEquals(true, work.settings?.busy)
    }

    @Test
    fun `the price and the week are saved together`() = runTest {
        val hours = listOf(DoctorHours(1, 540, 1080))
        val recording = RecordingEngine { json(encode(settings.copy(priceMinor = 10_000_000, hours = hours))) }
        val work = testGraph(recording).workController()

        assertTrue(work.saveWork(10_000_000, hours))

        val sent = recording.bodyOf(HttpMethod.Put, "/v1/doctor/settings").orEmpty()
        assertTrue("\"priceMinor\":10000000" in sent, sent)
        assertTrue("\"hours\":[{\"weekday\":1,\"startMinute\":540,\"endMinute\":1080}]" in sent, sent)
        assertEquals(hours, work.settings?.hours)
    }

    @Test
    fun `quick replies are added changed and deleted in step with the server`() = runTest {
        val first = QuickReply("r1", "Salom", "Assalomu alaykum!", position = 0)
        val recording = RecordingEngine { request ->
            when (request.method) {
                HttpMethod.Get -> json(encode(listOf(first)))
                HttpMethod.Post -> json(encode(QuickReply("r2", "Tahlil", "Tahlil yuboring", position = 1)), HttpStatusCode.Created)
                HttpMethod.Put -> json(encode(first.copy(body = "Salom!")))
                else -> json(encode(Ack()))
            }
        }
        val work = testGraph(recording).workController()
        work.loadQuickReplies()
        assertEquals(listOf("r1"), work.quickReplies.map { it.id })

        assertTrue(work.saveQuickReply(null, "Tahlil", "Tahlil yuboring"))
        assertEquals(listOf("r1", "r2"), work.quickReplies.map { it.id })
        // A new reply goes after the last one.
        assertTrue("\"position\":1" in recording.bodyOf(HttpMethod.Post, "/v1/doctor/quick-replies").orEmpty())

        assertTrue(work.saveQuickReply("r1", "Salom", "Salom!"))
        assertEquals("Salom!", work.quickReplies.first { it.id == "r1" }.body)
        assertEquals(1, recording.countOf("/v1/doctor/quick-replies/r1"))

        assertTrue(work.deleteQuickReply("r2"))
        assertEquals(listOf("r1"), work.quickReplies.map { it.id })
        assertTrue(recording.seen.any { it.method == HttpMethod.Delete && it.path == "/v1/doctor/quick-replies/r2" })
    }

    @Test
    fun `a patient's note and history belong to her consultation`() = runTest {
        val history = PatientHistory(
            patient = ConsultationPatient("Madina", 29, LifeStage.PREGNANCY),
            sessions = listOf(
                ConsultationSession(id = "s1", payment = ConsultationPayment.PAID, priceMinor = 5_000_000, recordMessageIds = listOf("m1")),
            ),
        )
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/doctor/patients/c-1/note" ->
                    if (request.method == HttpMethod.Get) json(encode(PatientNote("Qon bosimi past"))) else json(encode(PatientNote("Yangi")))
                "/v1/doctor/patients/c-1/history" -> json(encode(history))
                else -> json(errorBody(ErrorCodes.NOT_FOUND), HttpStatusCode.NotFound)
            }
        }
        val work = testGraph(recording).workController()

        work.openPatient("c-1")
        assertEquals("c-1", work.patientFor)
        assertEquals("Qon bosimi past", work.note?.body)
        assertEquals(listOf("m1"), work.history?.sessions?.single()?.recordMessageIds)

        assertTrue(work.saveNote("c-1", "Yangi"))
        assertEquals("""{"body":"Yangi"}""", recording.bodyOf(HttpMethod.Put, "/v1/doctor/patients/c-1/note"))
        assertEquals("Yangi", work.note?.body)

        // Another patient starts blank, not with the last one's note on screen.
        work.openPatient("c-2")
        assertEquals("c-2", work.patientFor)
        assertNull(work.note)
        assertNull(work.history)
    }

    @Test
    fun `closing a consultation sends her advice and an empty one is not sent`() = runTest {
        val thread = ConversationThread(
            Conversation(
                id = "c-1",
                alias = "Madina",
                tint = 1,
                lastMessageAt = TestNow,
                consultation = Consultation(openedAt = TestNow, expiresAt = TestNow, open = false, summary = "Suv ko'p iching"),
            ),
        )
        val recording = RecordingEngine { json(encode(thread)) }
        val doctors = testGraph(recording).doctorController()

        assertTrue(doctors.closeConsultation("c-1", "  Suv ko'p iching "))
        assertEquals("""{"summary":"Suv ko'p iching"}""", recording.bodyOf(HttpMethod.Post, "/v1/community/conversations/c-1/close"))
        assertEquals("Suv ko'p iching", doctors.openConversation?.conversation?.consultation?.summary)

        assertTrue(doctors.closeConsultation("c-1", "   "))
        assertEquals("{}", recording.bodyOf(HttpMethod.Post, "/v1/community/conversations/c-1/close"))
    }

    @Test
    fun `the push token goes out as the doctor app's device`() = runTest {
        val recording = RecordingEngine { json(encode(Ack())) }
        val graph = testGraph(recording)

        graph.repository.registerPushToken("fcm-token")

        val sent = recording.bodyOf(HttpMethod.Post, "/v1/me/devices").orEmpty()
        assertTrue("\"pushToken\":\"fcm-token\"" in sent, sent)
        assertTrue("\"app\":\"doctor\"" in sent, sent)
    }
}
