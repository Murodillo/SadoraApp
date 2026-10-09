package uz.sadora.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import uz.sadora.app.model.AppState
import uz.sadora.app.model.CommunityTopic
import uz.sadora.app.model.MessageImageSize
import uz.sadora.app.model.MessageKind
import uz.sadora.app.i18n.StringsEn
import uz.sadora.app.i18n.StringsRu
import uz.sadora.app.i18n.StringsUz
import uz.sadora.contract.CommunityComment as WireComment
import uz.sadora.contract.CommunityPost as WirePost
import uz.sadora.contract.CommunityTopic as WireTopic
import uz.sadora.contract.Consultation as WireConsultation
import uz.sadora.contract.Conversation as WireConversation
import uz.sadora.contract.DirectMessage as WireMessage
import uz.sadora.contract.MessageKind as WireKind

/**
 * The store counts her own like on top of the post's, so the wire's total — which
 * already includes hers — must have it taken off on the way in, or a refresh doubles
 * every like she has given.
 */
class CommunityMappingTest {

    private fun wirePost(liked: Boolean, likeCount: Int) = WirePost(
        id = "p1",
        topic = WireTopic.CYCLE,
        alias = "Sokin Bulut",
        tint = 2,
        body = "Savol",
        createdAt = TestNow - 20.minutes,
        likeCount = likeCount,
        commentCount = 3,
        liked = liked,
        saved = true,
        isMine = false,
    )

    @Test
    fun `a worn frame survives the mapping and putting one on re-stamps her own posts and comments`() {
        val hers = wirePost(liked = false, likeCount = 0).copy(id = "mine", isMine = true).toAppPost()
        val theirs = wirePost(liked = false, likeCount = 0).copy(id = "other", frame = "sakura").toAppPost()
        assertEquals("sakura", theirs.frame)
        assertNull(hers.frame)

        val state = AppState()
        state.replaceCommunityFeed(listOf(hers, theirs), liked = emptySet(), saved = emptySet())
        state.addComment("other", "Rahmat")
        state.applyWornFrame("tulip")
        assertEquals("tulip", state.communityPosts.single { it.id == "mine" }.frame)
        assertEquals("sakura", state.communityPosts.single { it.id == "other" }.frame, "someone else's frame is hers to change")
        assertEquals("tulip", state.commentsOf(state.communityPosts.single { it.id == "other" }).last().frame)

        state.applyWornFrame(null)
        assertNull(state.communityPosts.single { it.id == "mine" }.frame)
    }

    @Test
    fun `a doctor's byline and answers survive the mapping and drive the doctors chip`() {
        val doctor = uz.sadora.contract.DoctorAuthor("d1", "Dr Nodira Karimova", uz.sadora.contract.DoctorSpecialty.GYNECOLOGIST)
        val byDoctor = wirePost(liked = false, likeCount = 0).copy(id = "p2", alias = doctor.fullName, doctor = doctor).toAppPost()
        val answered = wirePost(liked = false, likeCount = 0).copy(doctorAnswers = 2).toAppPost()
        assertEquals(doctor, byDoctor.doctor)
        assertEquals(2, answered.doctorAnswers)
        assertNull(answered.doctor)

        val state = AppState()
        state.replaceCommunityFeed(listOf(byDoctor, answered), liked = emptySet(), saved = emptySet())
        assertEquals(2, state.visiblePosts().size)
        state.communityDoctorsOnly = true
        assertEquals(listOf("p2"), state.visiblePosts().map { it.id })

        val comment = WireComment(
            id = "c1",
            postId = "p1",
            alias = doctor.fullName,
            tint = 0,
            body = "Javob",
            createdAt = TestNow,
            doctor = doctor,
        ).toAppComment()
        assertEquals(doctor, comment.doctor)
    }

    @Test
    fun `her own like comes off the server's count so the store can add it back`() {
        val post = wirePost(liked = true, likeCount = 5).toAppPost()
        assertEquals(4, post.likes)

        val state = AppState()
        state.replaceCommunityFeed(listOf(post), liked = setOf("p1"), saved = setOf("p1"))
        assertEquals(5, state.likeCount(post), "the screen shows the server's total")
        assertEquals(3, state.commentCountOf(post), "the count stands in until the comments load")
    }

    @Test
    fun `a post she has not liked keeps the server's count whole`() {
        assertEquals(5, wirePost(liked = false, likeCount = 5).toAppPost().likes)
    }

    @Test
    fun `topics map both ways and the All filter has no wire form`() {
        WireTopic.entries.forEach { topic ->
            assertEquals(topic, topic.toAppTopic().toWireTopic())
        }
        assertNull(CommunityTopic.All.toWireTopic())
    }

    @Test
    fun `age reads the way the feed writes it`() {
        val uz = StringsUz.dates
        assertEquals("hozir", uz.ago(TestNow - 30.seconds, TestNow))
        assertEquals("20 daqiqa oldin", uz.ago(TestNow - 20.minutes, TestNow))
        assertEquals("3 soat oldin", uz.ago(TestNow - 3.hours, TestNow))
        assertEquals("Kecha", uz.ago(TestNow - 30.hours, TestNow))
        assertEquals("5 kun oldin", uz.ago(TestNow - 5.days, TestNow))
    }

    /**
     * The post carries the instant, not a sentence, so the same post reads in whichever
     * language the screen is drawn in. Storing the words was what made a feed loaded in
     * Uzbek stay Uzbek after switching to Russian.
     */
    @Test
    fun `the same post ages in every language`() {
        val at = TestNow - 3.hours
        assertEquals("3 soat oldin", StringsUz.dates.ago(at, TestNow))
        assertEquals("3 ч. назад", StringsRu.dates.ago(at, TestNow))
        assertEquals("3 h ago", StringsEn.dates.ago(at, TestNow))
    }

    @Test
    fun `a loaded comment marked as hers renders as hers`() {
        val comment = WireComment("c1", "p1", "Iliq Shabnam", 1, "Javob", TestNow - 5.minutes, isMine = true)
            .toAppComment()
        assertEquals(true, comment.isMine)
        assertEquals("5 daqiqa oldin", StringsUz.dates.ago(comment.createdAt, TestNow))
    }

    /**
     * A consultation arrives as a conversation with a doctor, a window and the new
     * last-line fields; all of it has to survive onto the app's model, or the list draws
     * a doctor as an alias and an open window as a closed one.
     */
    @Test
    fun `a consultation keeps its doctor and window and the list's last line fields`() {
        val doctor = uz.sadora.contract.DoctorAuthor("d1", "Dr Nodira Karimova", uz.sadora.contract.DoctorSpecialty.GYNECOLOGIST)
        val wire = WireConversation(
            id = "c1",
            alias = doctor.fullName,
            tint = 0,
            lastMessage = "",
            lastMessageAt = TestNow,
            unread = 1,
            doctor = doctor,
            consultation = WireConsultation(openedAt = TestNow - 1.hours, expiresAt = TestNow + 23.hours, open = true),
            lastMessageKind = WireKind.RECORD,
            lastMessageRead = true,
        )
        val thread = wire.toAppConversation()
        assertEquals(doctor, thread.doctor)
        assertTrue(thread.isConsultation)
        assertTrue(thread.canWrite)
        assertEquals(MessageKind.Record, thread.lastMessageKind)
        assertTrue(thread.lastMessageRead)
        val window = assertNotNull(thread.consultation)
        assertEquals(23.hours, window.remaining(TestNow))
        assertFalse(window.closedByDoctor)
    }

    @Test
    fun `a shut window takes no messages and says who shut it`() {
        val expired = WireConsultation(openedAt = TestNow - 25.hours, expiresAt = TestNow - 1.hours, open = false).toAppWindow()
        val closed = WireConsultation(TestNow - 2.hours, TestNow + 22.hours, closedAt = TestNow - 5.minutes, open = false).toAppWindow()
        assertFalse(expired.closedByDoctor)
        assertTrue(closed.closedByDoctor)
        // The server's verdict wins over the clock: shut is shut even with time on the clock.
        assertEquals(kotlin.time.Duration.ZERO, closed.remaining(TestNow))

        val base = WireConversation("c1", "Dr X", 0, lastMessageAt = TestNow)
        assertFalse(base.copy(consultation = WireConsultation(TestNow, TestNow, open = false)).toAppConversation().canWrite)
        assertFalse(base.copy(blocked = true).toAppConversation().canWrite)
        val alias = base.toAppConversation()
        assertTrue(alias.canWrite, "an alias thread has no window to close")
        assertFalse(alias.isConsultation)
        assertEquals(MessageKind.Text, alias.lastMessageKind)
    }

    @Test
    fun `a photo keeps its size and ticks and a size on anything else is dropped`() {
        val photo = WireMessage(
            id = "m1",
            body = "Toshma",
            createdAt = TestNow,
            isMine = true,
            kind = WireKind.IMAGE,
            image = uz.sadora.contract.MessageImage(width = 1024, height = 768),
            read = true,
        ).toAppMessage()
        assertEquals(MessageKind.Image, photo.kind)
        assertEquals(MessageImageSize(1024, 768), photo.image)
        assertEquals(0.75f, assertNotNull(photo.image).aspect)
        assertTrue(photo.read)

        val record = WireMessage("m2", "", TestNow, isMine = true, kind = WireKind.RECORD, image = uz.sadora.contract.MessageImage(1, 1))
            .toAppMessage()
        assertEquals(MessageKind.Record, record.kind)
        assertNull(record.image)
        assertFalse(record.read)

        WireKind.entries.forEach { kind -> assertEquals(kind.name, kind.toAppKind().name.uppercase()) }
    }

    @Test
    fun `a prescription line carries its card, and a kind this release does not know reads as text`() {
        val prescription = uz.sadora.contract.Prescription(
            id = "rx1",
            conversationId = "c1",
            messageId = "m3",
            doctor = uz.sadora.contract.DoctorAuthor("d1", "Dr Karimova", uz.sadora.contract.DoctorSpecialty.GYNECOLOGIST),
            items = emptyList(),
            createdAt = TestNow,
        )
        val line = WireMessage("m3", "💊 Retsept", TestNow, isMine = false, kind = WireKind.PRESCRIPTION, prescription = prescription)
            .toAppMessage()
        assertEquals(MessageKind.Prescription, line.kind)
        assertEquals("rx1", line.prescription?.id)
        assertNull(WireMessage("m4", "x", TestNow, isMine = false, prescription = prescription).toAppMessage().prescription)

        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val future = json.decodeFromString<WireMessage>(
            """{"id":"m5","body":"Yangi tur","createdAt":"2026-10-09T09:00:00Z","isMine":false,"kind":"voice"}""",
        )
        assertEquals(WireKind.TEXT, future.kind)
        assertEquals("Yangi tur", future.toAppMessage().body)
    }

    @Test
    fun `a photo's shape is held to a range a bubble can draw`() {
        assertEquals(1.6f, MessageImageSize(100, 1000).aspect, "a tall strip does not become a tower")
        assertEquals(0.4f, MessageImageSize(1000, 100).aspect, "a panorama does not become a ribbon")
        assertEquals(1f, MessageImageSize(0, 0).aspect, "no size is a square, not a division by zero")
    }
}
