package uz.sadora.doctor.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import uz.sadora.contract.Conversation
import uz.sadora.contract.ConversationThread
import uz.sadora.contract.DirectMessage
import uz.sadora.contract.DoctorProfile
import uz.sadora.contract.DoctorSpecialty
import uz.sadora.contract.MessagePage
import uz.sadora.contract.Page

/**
 * A busy doctor's lists arrive a page at a time: scrolling up a consultation reads the
 * lines before its first one and the poll keeps them; the consultations list reads on
 * from its oldest one and its refresh does not fold it back; her own page reads her
 * older posts past the few it carries.
 */
class PagingTest {

    private fun line(id: String, minute: Int) = DirectMessage(id, "Line $id", TestNow + minute.minutes, isMine = false)

    private fun conversation(id: String, minutesAgo: Int) =
        Conversation(id = id, alias = "Bemor $id", tint = 0, lastMessageAt = TestNow - minutesAgo.minutes)

    @Test
    fun `scrolling up a consultation reads the page above and the poll keeps it`() = runTest {
        var latest = listOf(line("m3", 3), line("m4", 4))
        val befores = mutableListOf<String?>()
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/community/conversations/c1/messages" -> {
                    befores.add(request.url.parameters["before"])
                    json(encode(MessagePage(listOf(line("m1", 1), line("m2", 2)), hasMore = false)))
                }
                else -> json(encode(ConversationThread(conversation("c1", 0), latest, hasMore = true)))
            }
        }
        val doctors = testGraph(recording).doctorController()

        doctors.openConversation("c1")
        assertTrue(doctors.openConversation?.hasMore == true)

        doctors.loadOlderMessages("c1")
        assertEquals(listOf<String?>("m3"), befores)
        assertEquals(listOf("m1", "m2", "m3", "m4"), doctors.openConversation?.messages?.map { it.id })
        assertFalse(doctors.openConversation?.hasMore == true)

        latest = listOf(line("m4", 4), line("m5", 5))
        doctors.openConversation("c1", poll = true)
        assertEquals(listOf("m1", "m2", "m3", "m4", "m5"), doctors.openConversation?.messages?.map { it.id })
        assertFalse(doctors.openConversation?.hasMore == true)
    }

    @Test
    fun `the consultations list reads on from its oldest and a refresh keeps the pages`() = runTest {
        val page = CommunityApi.CONVERSATION_PAGE
        val first = (0 until page).map { conversation("c$it", it) }
        val second = listOf(conversation("old", page + 10))
        val recording = RecordingEngine { request ->
            json(encode(if (request.url.parameters["before"] == null) first else second))
        }
        val doctors = testGraph(recording).doctorController()

        doctors.loadConversations()
        assertTrue(doctors.hasMoreConversations)

        doctors.loadMoreConversations()
        assertEquals(page + 1, doctors.conversations.size)
        assertFalse(doctors.hasMoreConversations)

        doctors.loadConversations(silent = true)
        assertEquals(page + 1, doctors.conversations.size, "the refresh kept the older page")
    }

    @Test
    fun `her page reads her older posts past the few it carries and a refresh keeps them`() = runTest {
        val all = (0 until 24).map { testQuestion("p$it").copy(createdAt = TestNow - it.minutes) }
        val offsets = mutableListOf<String?>()
        val recording = RecordingEngine { request ->
            if (request.url.encodedPath == "/v1/doctors/doc-1/posts") {
                val offset = request.url.parameters["offset"]!!.toInt().also { offsets.add(it.toString()) }
                // She posted in between: the page starts one earlier, and the overlap is dropped.
                val items = all.drop(offset - 1).take(request.url.parameters["limit"]!!.toInt())
                json(encode(Page(items, all.size.toLong(), CommunityApi.PROFILE_POSTS_PAGE, offset - 1)))
            } else {
                json(encode(DoctorProfile("doc-1", "Dr. Nodira", DoctorSpecialty.GYNECOLOGIST, "Klinika", 9, verifiedSince = TestNow, postCount = all.size, posts = all.take(20))))
            }
        }
        val doctors = testGraph(recording).doctorController()

        doctors.loadProfile("doc-1")
        assertTrue(doctors.profilePostsHasMore)

        doctors.loadMoreProfilePosts()
        assertEquals(listOf<String?>("20"), offsets)
        assertEquals(all.map { it.id }, doctors.profilePosts.map { it.id }, "no post twice")
        assertFalse(doctors.profilePostsHasMore)

        doctors.loadProfile("doc-1")
        assertEquals(all.size, doctors.profilePosts.size, "the refresh kept the older posts")
        assertFalse(doctors.profilePostsHasMore)
    }
}
