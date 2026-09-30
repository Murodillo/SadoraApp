package uz.sadora.app.data

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import uz.sadora.app.model.MessageKind
import uz.sadora.contract.Ack
import uz.sadora.contract.Consultation
import uz.sadora.contract.Conversation
import uz.sadora.contract.ConversationThread
import uz.sadora.contract.DirectMessage
import uz.sadora.contract.DoctorAuthor
import uz.sadora.contract.DoctorSpecialty
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.MessageImage
import uz.sadora.contract.MessageKind as WireKind

/**
 * Messaging v2 as the app drives it: consultations with doctors, photos, the record,
 * ticks and "yozmoqda". What is pinned: the list asks for her personal threads only; a
 * consultation opens through the doctor's endpoint and becomes the thread on screen;
 * a photo goes up base64 and its bytes are fetched once; the record is offered only in
 * an open consultation; typing is throttled; and a send refused because the window shut
 * re-reads the thread so the screen can say so.
 */
class MessagingV2ControllerTest {

    private class TestClock(var now: Instant) : Clock {
        override fun now(): Instant = now
        fun advance(by: Duration) {
            now += by
        }
    }

    private fun graph(recording: RecordingEngine) = SadoraGraph(
        tokenStorage = InMemoryTokenStorage(token = "refresh-0"),
        device = FixedDeviceIdentity(),
        environment = SadoraEnvironment("http://test.local"),
        engine = recording.build(),
    )

    private val doctor = DoctorAuthor("d1", "Dr Nodira Karimova", DoctorSpecialty.GYNECOLOGIST)

    private fun consultation(open: Boolean = true, lines: List<DirectMessage> = emptyList(), typing: Boolean = false) =
        ConversationThread(
            conversation = Conversation(
                id = "k1",
                alias = doctor.fullName,
                tint = 0,
                lastMessageAt = TestNow,
                doctor = doctor,
                consultation = Consultation(
                    openedAt = TestNow - 1.hours,
                    expiresAt = if (open) TestNow + 23.hours else TestNow - 1.hours,
                    open = open,
                ),
            ),
            messages = lines,
            otherTyping = typing,
            otherReadAt = TestNow,
        )

    @Test
    fun `the list asks for her personal threads only`() = runTest {
        val queries = mutableListOf<String>()
        val recording = RecordingEngine { request ->
            queries.add(request.url.parameters["scope"].orEmpty())
            json(encode(listOf(consultation().conversation)))
        }
        val controller = graph(recording).messagesController()
        controller.load()
        assertEquals(listOf("personal"), queries)
        assertEquals(doctor, controller.conversations.single().doctor)
    }

    @Test
    fun `a consultation opens through the doctor's endpoint and becomes the thread on screen`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/doctors/d1/consultations" -> json(encode(consultation(typing = true)), HttpStatusCode.Created)
                else -> json(encode(emptyList<Conversation>()))
            }
        }
        val controller = graph(recording).messagesController()

        val opened = assertNotNull(controller.startConsultation("d1"))
        assertEquals("k1", opened.id)
        assertTrue(opened.isConsultation)
        assertEquals(opened, controller.current)
        assertTrue(controller.otherTyping)
        assertEquals(TestNow, controller.otherReadAt)
        assertEquals("k1", controller.conversations.single().id, "the list learns of it without a reload")
        assertEquals(1, recording.countOf("/v1/doctors/d1/consultations"))
    }

    @Test
    fun `a closed consultation is opened again from the thread by its doctor`() = runTest {
        var reopened = false
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/community/conversations/k1" -> json(encode(consultation(open = reopened)))
                "/v1/doctors/d1/consultations" -> {
                    reopened = true
                    json(encode(consultation(open = true)), HttpStatusCode.Created)
                }
                else -> json(encode(emptyList<Conversation>()))
            }
        }
        val controller = graph(recording).messagesController()
        controller.open("k1", doctor.fullName)
        assertFalse(assertNotNull(controller.current).canWrite)
        assertFalse(controller.attachRecord(), "no record into a shut consultation")
        assertEquals(0, recording.countOf("/v1/community/conversations/k1/messages"))

        assertTrue(controller.reopenConsultation())
        assertTrue(assertNotNull(controller.current).canWrite)
    }

    @Test
    fun `a photo goes up base64 and its bytes are fetched once`() = runTest {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)
        val recording = RecordingEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/messages/m9/image") -> respond(jpeg, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))
                path.endsWith("/k1/messages") -> json(
                    encode(DirectMessage("m1", "Toshma", TestNow, isMine = true, kind = WireKind.IMAGE, image = MessageImage(640, 480))),
                    HttpStatusCode.Created,
                )
                path.endsWith("/conversations/k1") -> json(encode(consultation()))
                else -> json(encode(emptyList<Conversation>()))
            }
        }
        val controller = graph(recording).messagesController()
        controller.open("k1", doctor.fullName)

        assertTrue(controller.sendImage("/9gBAgM=", "image/jpeg", caption = " Toshma "))
        val body = recording.bodies.last { it.contains("imageBase64") }
        assertTrue(body.contains("\"imageBase64\":\"/9gBAgM=\""))
        assertTrue(body.contains("\"body\":\"Toshma\""), "the caption is trimmed")
        val sent = controller.messages.single()
        assertEquals(MessageKind.Image, sent.kind)
        assertEquals(MessageKind.Image, controller.conversations.single().lastMessageKind)

        // Her own photo is drawn from what she sent, never downloaded back.
        assertContentEquals(jpeg, controller.imageBytes("k1", "m1"))
        assertEquals(0, recording.countOf("/v1/community/conversations/k1/messages/m1/image"))

        // Someone else's is fetched once, then served from memory.
        assertContentEquals(jpeg, controller.imageBytes("k1", "m9"))
        assertContentEquals(jpeg, controller.imageBytes("k1", "m9"))
        assertEquals(1, recording.countOf("/v1/community/conversations/k1/messages/m9/image"))
    }

    @Test
    fun `her record is attached only into an open consultation`() = runTest {
        val recording = RecordingEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/k1/messages") ->
                    json(encode(DirectMessage("m1", "", TestNow, isMine = true, kind = WireKind.RECORD)), HttpStatusCode.Created)
                path.endsWith("/conversations/k1") -> json(encode(consultation()))
                path.endsWith("/conversations/a1") -> json(
                    encode(ConversationThread(Conversation("a1", "Sokin Bulut", 1, lastMessageAt = TestNow))),
                )
                else -> json(encode(emptyList<Conversation>()))
            }
        }
        val controller = graph(recording).messagesController()

        controller.open("a1", "Sokin Bulut")
        assertFalse(controller.attachRecord(), "an alias thread never carries a record")

        controller.open("k1", doctor.fullName)
        assertTrue(controller.attachRecord())
        assertTrue(recording.bodies.any { it.contains("\"attachRecord\":true") })
        assertEquals(MessageKind.Record, controller.messages.single().kind)
        assertEquals(1, recording.countOf("/v1/community/conversations/k1/messages"))
    }

    @Test
    fun `typing is sent at most once every three seconds`() = runTest {
        val clock = TestClock(TestNow)
        val recording = RecordingEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/typing") -> json(encode(Ack()))
                path.endsWith("/conversations/k1") -> json(encode(consultation()))
                else -> json(encode(emptyList<Conversation>()))
            }
        }
        val controller = graph(recording).messagesController(clock)
        controller.open("k1", doctor.fullName)

        controller.typing()
        clock.advance(1.seconds)
        controller.typing()
        clock.advance(1.seconds)
        controller.typing()
        assertEquals(1, recording.countOf("/v1/community/conversations/k1/typing"))

        clock.advance(1.seconds)
        controller.typing()
        assertEquals(2, recording.countOf("/v1/community/conversations/k1/typing"))
    }

    @Test
    fun `a send refused because the window shut re-reads the thread`() = runTest {
        var shut = false
        val recording = RecordingEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/k1/messages") -> {
                    shut = true
                    json(errorBody(ErrorCodes.FORBIDDEN, "Konsultatsiya yopilgan — yangisini oching"), HttpStatusCode.Forbidden)
                }
                path.endsWith("/conversations/k1") -> json(encode(consultation(open = !shut)))
                else -> json(encode(emptyList<Conversation>()))
            }
        }
        val controller = graph(recording).messagesController()
        controller.open("k1", doctor.fullName)
        assertTrue(assertNotNull(controller.current).canWrite)

        assertFalse(controller.send("Salom"))
        assertTrue(controller.error is ApiFailure.Forbidden)
        assertFalse(assertNotNull(controller.current).canWrite, "the banner and the composer follow")
        assertEquals(2, recording.countOf("/v1/community/conversations/k1"))
    }

    @Test
    fun `leaving the thread forgets it and its typing`() = runTest {
        val recording = RecordingEngine { request ->
            when {
                request.url.encodedPath.endsWith("/conversations/k1") -> json(encode(consultation(typing = true)))
                else -> json(encode(emptyList<Conversation>()))
            }
        }
        val controller = graph(recording).messagesController()
        controller.open("k1", doctor.fullName)
        assertTrue(controller.otherTyping)
        controller.close()
        assertNull(controller.current)
        assertFalse(controller.otherTyping)
        assertNull(controller.otherReadAt)
    }
}
