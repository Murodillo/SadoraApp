package uz.sadora.doctor.data

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import uz.sadora.contract.Consultation
import uz.sadora.contract.Conversation
import uz.sadora.contract.ConversationThread
import uz.sadora.contract.DirectMessage
import uz.sadora.contract.DoctorSummary
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.Language
import uz.sadora.contract.LifeStage
import uz.sadora.contract.MessageKind
import uz.sadora.contract.Page
import uz.sadora.contract.SharedPerson

/**
 * What the five tabs read from the controller: a QR code's token becomes a patient's
 * record, or a plain "gone"; the Messages dot counts what is unread and a thread opened
 * clears its share; a message sent moves its conversation to the top; the Chat filter
 * is what the server is asked for.
 */
class TabsControllerTest {

    private val token = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_abcde"

    @Test
    fun `a patient link is read from the url a QR code holds`() {
        assertEquals(token, patientTokenOf("https://api.sadora.app/share/$token"))
        assertEquals(token, patientTokenOf("  https://some-tunnel.trycloudflare.com/share/$token?lang=ru#top "))
        assertEquals(token, patientTokenOf("http://localhost:8080/share/$token/json"))
        assertEquals(token, patientTokenOf(token))
        assertNull(patientTokenOf("https://example.com/"))
        assertNull(patientTokenOf("https://api.sadora.app/share/short"))
        assertNull(patientTokenOf("https://api.sadora.app/share/has spaces in it and more text"))
        assertNull(patientTokenOf(""))
    }

    @Test
    fun `a scanned code opens her record and joins the recent list`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/share/$token/json" -> json(encode(summary("Malika Rahimova", age = 29)))
                else -> json(errorBody(ErrorCodes.NOT_FOUND), HttpStatusCode.NotFound)
            }
        }
        val doctors = testGraph(recording).doctorController()

        doctors.openPatient(token, Language.RU)

        assertEquals("Malika Rahimova", doctors.patientFor(token)?.person?.name)
        assertNull(doctors.patientFor("another-token-another-token"))
        assertEquals(listOf(token), doctors.recentPatients.map { it.token })
        assertEquals(29, doctors.recentPatients.single().age)
        assertTrue(recording.seen.any { it.path == "/share/$token/json" })

        doctors.reset()
        assertTrue(doctors.recentPatients.isEmpty())
        assertNull(doctors.patientFor(token))
    }

    @Test
    fun `an expired link reads as gone and not as a server fault`() = runTest {
        // The public route answers an unknown token with a bare body, not the API's envelope.
        val recording = RecordingEngine { json("{\"ok\":false}", HttpStatusCode.NotFound) }
        val doctors = testGraph(recording).doctorController()

        doctors.openPatient(token, Language.UZ)

        assertIs<ApiFailure.NotFound>(doctors.patientCalls.error)
        assertNull(doctors.patientFor(token))
        assertTrue(doctors.recentPatients.isEmpty())
    }

    @Test
    fun `the unread count is the conversations' and an opened thread clears its share`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/community/conversations" -> json(encode(listOf(conversation("c1", unread = 2), conversation("c2", unread = 1))))
                "/v1/community/conversations/c1" -> json(encode(ConversationThread(conversation("c1", unread = 0), listOf(message("m1")))))
                else -> json(errorBody(ErrorCodes.NOT_FOUND), HttpStatusCode.NotFound)
            }
        }
        val doctors = testGraph(recording).doctorController()

        doctors.loadConversations()
        assertEquals(3, doctors.unreadMessages)

        doctors.openConversation("c1")
        assertEquals("c1", doctors.openConversation?.conversation?.id)
        assertEquals(1, doctors.unreadMessages)
    }

    @Test
    fun `a message sent moves its conversation to the top with its text`() = runTest {
        val sent = message("m2", body = "Ertaga 10:00 da kutaman", mine = true, at = TestNow + kotlin.time.Duration.parse("2h"))
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/community/conversations" -> json(encode(listOf(conversation("c1"), conversation("c2", at = TestNow - kotlin.time.Duration.parse("1h")))))
                "/v1/community/conversations/c2" -> json(encode(ConversationThread(conversation("c2"), emptyList())))
                "/v1/community/conversations/c2/messages" -> json(encode(sent), HttpStatusCode.Created)
                else -> json(errorBody(ErrorCodes.NOT_FOUND), HttpStatusCode.NotFound)
            }
        }
        val doctors = testGraph(recording).doctorController()
        doctors.loadConversations()
        doctors.openConversation("c2")

        assertTrue(doctors.sendMessage("c2", sent.body))

        assertEquals(listOf("c2", "c1"), doctors.conversations.map { it.id })
        assertEquals(sent.body, doctors.conversations.first().lastMessage)
        assertEquals(listOf("m2"), doctors.openConversation?.messages?.map { it.id })
        assertTrue("\"body\":\"${sent.body}\"" in recording.bodyOf(HttpMethod.Post, "/v1/community/conversations/c2/messages").orEmpty())
    }

    @Test
    fun `the doctors filter is what the feed asks the server for`() = runTest {
        val queries = mutableListOf<String?>()
        val recording = RecordingEngine { request ->
            queries.add(request.url.parameters["doctors"])
            json(encode(Page(items = listOf(testQuestion("p1")), total = 1, limit = 50, offset = 0)))
        }
        val doctors = testGraph(recording).doctorController()

        doctors.loadFeed()
        doctors.loadFeed(doctorsOnly = true)

        assertEquals<List<String?>>(listOf("false", "true"), queries)
        assertTrue(doctors.feedDoctorsOnly)
        assertEquals(listOf("p1"), doctors.feed.map { it.id })
    }

    private fun summary(name: String, age: Int?) = DoctorSummary(
        generatedAt = TestNow,
        language = Language.RU,
        person = SharedPerson(name = name, age = age, lifeStage = LifeStage.CYCLE, memberSince = LocalDate(2026, 3, 1)),
    )

    private fun conversation(id: String, unread: Int = 0, at: kotlin.time.Instant = TestNow) = Conversation(
        id = id,
        alias = "Sokin Bulut $id",
        tint = 1,
        lastMessage = "Salom",
        lastMessageAt = at,
        unread = unread,
    )

    private fun message(id: String, body: String = "Salom", mine: Boolean = false, at: kotlin.time.Instant = TestNow) =
        DirectMessage(id = id, body = body, createdAt = at, isMine = mine)

    @Test
    fun `the doctor app asks only for the consultations she holds`() = runTest {
        val queries = mutableListOf<String?>()
        val recording = RecordingEngine { request ->
            queries.add(request.url.parameters["scope"])
            json(encode(listOf(conversation("c1"))))
        }
        testGraph(recording).doctorController().loadConversations()
        assertEquals<List<String?>>(listOf("patients"), queries)
    }

    @Test
    fun `a photo goes as base64 and is fetched once however often the thread is read`() = runTest {
        val sent = DirectMessage(id = "m9", body = "", createdAt = TestNow, isMine = true, kind = MessageKind.IMAGE)
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/community/conversations/c1/messages" -> json(encode(sent), HttpStatusCode.Created)
                "/v1/community/conversations/c1/messages/m9/image" ->
                    respond(ByteReadChannel(byteArrayOf(1, 2, 3)), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))
                else -> json(errorBody(ErrorCodes.NOT_FOUND), HttpStatusCode.NotFound)
            }
        }
        val doctors = testGraph(recording).doctorController()

        assertTrue(doctors.sendImage("c1", CapturedPhotoData("AQID", "image/jpeg")))
        val body = recording.bodyOf(HttpMethod.Post, "/v1/community/conversations/c1/messages").orEmpty()
        assertTrue("\"imageBase64\":\"AQID\"" in body, body)

        assertEquals(listOf<Byte>(1, 2, 3), doctors.image("c1", "m9")?.toList())
        doctors.image("c1", "m9")
        assertEquals(1, recording.countOf("/v1/community/conversations/c1/messages/m9/image"))
    }

    @Test
    fun `closing a consultation is the server's word and typing is sent at most every three seconds`() = runTest {
        val closed = ConversationThread(
            conversation("c1").copy(consultation = Consultation(TestNow, TestNow, closedAt = TestNow, open = false)),
        )
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/community/conversations/c1/close" -> json(encode(closed))
                "/v1/community/conversations/c1/typing" -> json(encode(uz.sadora.contract.Ack()))
                else -> json(errorBody(ErrorCodes.NOT_FOUND), HttpStatusCode.NotFound)
            }
        }
        val doctors = testGraph(recording).doctorController()

        assertTrue(doctors.closeConsultation("c1"))
        assertEquals(false, doctors.openConversation?.conversation?.consultation?.open)

        doctors.typing("c1", nowMillis = 10_000)
        doctors.typing("c1", nowMillis = 11_000)
        doctors.typing("c1", nowMillis = 13_500)
        assertEquals(2, recording.countOf("/v1/community/conversations/c1/typing"))
    }
}
