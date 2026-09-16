package uz.sadora.app.data

import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import uz.sadora.app.model.AppState
import uz.sadora.app.model.CommunityBadge
import uz.sadora.contract.BlockState
import uz.sadora.contract.CommunityBadge as WireBadge
import uz.sadora.contract.CommunityIdentity
import uz.sadora.contract.CommunityProfile
import uz.sadora.contract.Conversation
import uz.sadora.contract.ConversationThread
import uz.sadora.contract.DirectMessage

/**
 * Private messages and the alias profile, as the app drives them. What is pinned: the
 * first line to an alias opens the thread through the start call and later lines go
 * into it by id; opening a thread clears its unread count locally; a profile carries
 * its badges and a block re-reads it; and her bio edit goes up as a PUT on /me.
 */
class MessagesControllerTest {

    private fun graph(recording: RecordingEngine) = SadoraGraph(
        tokenStorage = InMemoryTokenStorage(token = "refresh-0"),
        device = FixedDeviceIdentity(),
        environment = SadoraEnvironment("http://test.local"),
        engine = recording.build(),
    )

    private fun thread(id: String, alias: String, vararg lines: DirectMessage) = ConversationThread(
        conversation = Conversation(id = id, alias = alias, tint = 1, lastMessage = lines.lastOrNull()?.body, lastMessageAt = TestNow, unread = 0),
        messages = lines.toList(),
    )

    @Test
    fun `the first line to an alias starts the thread and the next goes into it by id`() = runTest {
        val recording = RecordingEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/community/conversations") && request.method == HttpMethod.Post ->
                    json(encode(thread("c1", "Sokin Bulut", DirectMessage("m1", "Salom", TestNow, isMine = true))))
                path.endsWith("/community/conversations") ->
                    json(encode(listOf(Conversation("c1", "Sokin Bulut", 1, lastMessage = "Salom", lastMessageAt = TestNow, unread = 0))))
                path.endsWith("/conversations/c1/messages") -> json(encode(DirectMessage("m2", "Yana", TestNow, isMine = true)))
                path.endsWith("/conversations/c1") -> json(encode(thread("c1", "Sokin Bulut", DirectMessage("m1", "Salom", TestNow, isMine = true))))
                else -> json(encode(CommunityIdentity("Men", 0)))
            }
        }
        val controller = graph(recording).messagesController()

        controller.open(conversationId = null, alias = "Sokin Bulut")
        assertEquals("", assertNotNull(controller.current).id, "no thread on the server yet")

        assertTrue(controller.send("Salom"))
        assertEquals("c1", controller.current?.id)
        assertEquals(listOf("m1"), controller.messages.map { it.id })
        // The path is hit twice: the start, then the list reload; only one body is a start.
        assertEquals(1, recording.bodies.count { it.contains("\"alias\":\"Sokin Bulut\"") }, "one start")

        assertTrue(controller.send("Yana"))
        assertEquals(listOf("m1", "m2"), controller.messages.map { it.id })
        assertEquals(1, recording.countOf("/v1/community/conversations/c1/messages"))
        assertEquals("Yana", controller.conversations.single().lastMessage, "the list follows the thread")
    }

    @Test
    fun `opening a thread reads it and clears its unread count in the list`() = runTest {
        val recording = RecordingEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/community/conversations") ->
                    json(encode(listOf(Conversation("c1", "Sokin Bulut", 1, lastMessage = "Salom", lastMessageAt = TestNow, unread = 2, badges = listOf(WireBadge.HELPER)))))
                path.endsWith("/conversations/c1") ->
                    json(encode(thread("c1", "Sokin Bulut", DirectMessage("m1", "Salom", TestNow, isMine = false))))
                else -> json(encode(CommunityIdentity("Men", 0)))
            }
        }
        val controller = graph(recording).messagesController()

        controller.load()
        assertEquals(2, controller.unreadTotal)
        assertEquals(listOf(CommunityBadge.Helper), controller.conversations.single().badges)

        controller.open("c1", "Sokin Bulut")
        assertFalse(controller.messages.single().isMine)
        assertEquals(0, controller.unreadTotal)
        assertEquals(1, recording.countOf("/v1/community/conversations/c1"))
    }

    @Test
    fun `a profile carries its badges and a block re-reads it and a bio edit is a PUT on me`() = runTest {
        var blocked = false
        val recording = RecordingEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/profiles/Sokin%20Bulut/block") -> {
                    blocked = request.method == HttpMethod.Put
                    json(encode(BlockState(blocked)))
                }
                path.endsWith("/profiles/Sokin%20Bulut") -> json(
                    encode(
                        CommunityProfile(
                            alias = "Sokin Bulut", tint = 1, bio = "Ikki bola", badges = listOf(WireBadge.WRITER, WireBadge.LOVED),
                            postCount = 7, commentCount = 3, likesReceived = 51, memberSince = TestNow,
                            canMessage = !blocked, blocked = blocked,
                        ),
                    ),
                )
                path.endsWith("/community/me") && request.method == HttpMethod.Put ->
                    json(encode(CommunityIdentity("Men", 0, bio = "Yangi bio", dmOpen = false)))
                else -> json(encode(CommunityIdentity("Men", 0)))
            }
        }
        val state = AppState()
        val controller = graph(recording).communityController(state)

        controller.loadProfile("Sokin Bulut")
        val profile = assertNotNull(controller.profile)
        assertEquals(listOf(CommunityBadge.Writer, CommunityBadge.Loved), profile.badges)
        assertEquals(51, profile.likesReceived)
        assertTrue(profile.canMessage)

        assertTrue(controller.setBlocked("Sokin Bulut", blocked = true))
        assertTrue(assertNotNull(controller.profile).blocked)
        assertFalse(assertNotNull(controller.profile).canMessage)
        assertEquals(2, recording.countOf("/v1/community/profiles/Sokin%20Bulut"), "re-read after the block")

        assertTrue(controller.updateProfile(bio = "Yangi bio", dmOpen = false))
        assertEquals("Yangi bio", state.communityBio)
        assertFalse(state.communityDmOpen)
        assertTrue(recording.bodies.any { it.contains("\"bio\":\"Yangi bio\"") && it.contains("\"dmOpen\":false") })
    }
}
