package org.example.project.data

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import org.example.project.model.AppState
import org.example.project.model.CommunityTopic
import org.example.project.model.ReportReason
import uz.sadora.contract.AiChatQuota
import uz.sadora.contract.AiChatReply
import uz.sadora.contract.CommunityIdentity
import uz.sadora.contract.CommunityPost
import uz.sadora.contract.CommunityTopic as WireTopic
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.LikeState
import uz.sadora.contract.Page
import uz.sadora.contract.SaveState

/**
 * The two controllers the secret chat and the AI chat hang off. What is pinned: the
 * feed replaces the samples, every edit produces the request it must, the allowance on
 * screen is the server's, and a refusal reads as Uzbek rather than as a status code.
 */
class CommunityAndAiControllerTest {

    private fun graph(recording: RecordingEngine) = SadoraGraph(
        tokenStorage = InMemoryTokenStorage(token = "refresh-0"),
        device = FixedDeviceIdentity(),
        environment = SadoraEnvironment("http://test.local"),
        engine = recording.build(),
    )

    private fun serverPost(id: String, liked: Boolean = false) = CommunityPost(
        id = id,
        topic = WireTopic.WELLBEING,
        alias = "Yorug' Tong",
        tint = 1,
        body = "Server post $id",
        createdAt = TestNow,
        likeCount = if (liked) 3 else 2,
        commentCount = 1,
        liked = liked,
    )

    @Test
    fun `loading replaces the sample feed with the server's and keeps her reactions`() = runTest {
        val recording = RecordingEngine { request ->
            when {
                request.url.encodedPath.endsWith("/community/me") -> json(encode(CommunityIdentity("Sokin Bulut", 2)))
                request.url.encodedPath.endsWith("/community/posts") ->
                    json(encode(Page(listOf(serverPost("s1", liked = true), serverPost("s2")), 2, 100, 0)))
                else -> json(errorBody(ErrorCodes.NOT_FOUND, "no"), HttpStatusCode.NotFound)
            }
        }
        val state = AppState()
        val controller = graph(recording).communityController(state)

        controller.load()

        assertEquals(listOf("s1", "s2"), state.communityPosts.map { it.id })
        assertEquals(listOf("s1"), state.likedPosts.toList())
        assertEquals("Sokin Bulut", state.communityAlias)
        assertTrue(controller.loaded)
        assertEquals(3, state.likeCount(state.communityPosts.first()), "the total is the server's, not doubled")
    }

    @Test
    fun `a like and a save leave through the sink as the right requests`() = runTest {
        val recording = RecordingEngine { request ->
            when {
                request.url.encodedPath.endsWith("/like") -> json(encode(LikeState(true, 1)))
                request.url.encodedPath.endsWith("/save") -> json(encode(SaveState(true)))
                request.url.encodedPath.endsWith("/community/posts") -> json(encode(Page(emptyList<CommunityPost>(), 0, 100, 0)))
                else -> json(encode(CommunityIdentity("Sokin Bulut", 2)))
            }
        }
        val state = AppState()
        val controller = graph(recording).communityController(state)
        state.communitySync = CommunitySyncBridge(controller, this)

        state.toggleLike("p1")
        state.toggleSaved("p1")
        coroutineContext.job.children.forEach { it.join() }

        assertEquals(1, recording.countOf("/v1/community/posts/p1/like"))
        assertEquals(1, recording.countOf("/v1/community/posts/p1/save"))
        assertTrue("p1" in state.likedPosts && "p1" in state.savedPosts)
    }

    @Test
    fun `a new post goes up with its room and a report with its reason`() = runTest {
        val recording = RecordingEngine { request ->
            when {
                request.method == HttpMethod.Post && request.url.encodedPath.endsWith("/community/posts") ->
                    json(encode(serverPost("new")), HttpStatusCode.Created)
                request.url.encodedPath.endsWith("/report") -> json("""{"ok":true}""")
                request.url.encodedPath.endsWith("/community/posts") -> json(encode(Page(listOf(serverPost("new")), 1, 100, 0)))
                else -> json(encode(CommunityIdentity("Sokin Bulut", 2)))
            }
        }
        val state = AppState()
        val controller = graph(recording).communityController(state)
        state.communitySync = CommunitySyncBridge(controller, this)

        state.createPost(CommunityTopic.Body, "  Savolim bor  ")
        state.reportPost("new", ReportReason.Misinformation, null)
        coroutineContext.job.children.forEach { it.join() }

        val postBody = recording.bodies[recording.paths.indexOf("/v1/community/posts")]
        assertTrue("\"topic\":\"body\"" in postBody, postBody)
        assertTrue("\"body\":\"Savolim bor\"" in postBody, postBody)
        val reportBody = recording.bodies[recording.paths.indexOf("/v1/community/posts/new/report")]
        assertTrue("\"reason\":\"misinformation\"" in reportBody, reportBody)
        assertEquals(listOf("new"), state.communityPosts.map { it.id }, "the feed was refreshed after posting")
    }

    @Test
    fun `with no backend the feed stays local and a post still appears`() = runTest {
        val state = AppState()
        val controller = CommunityController(null, state)
        val before = state.communityPosts.size

        controller.load()
        state.createPost(CommunityTopic.Cycle, "Oflayn post")

        assertEquals(before + 1, state.communityPosts.size)
        assertTrue(state.communityPosts.first().isMine)
        assertNull(controller.error)
    }

    // ---------------------------------------------------------------- AI

    @Test
    fun `the counter on screen is what the server said was left`() = runTest {
        val recording = RecordingEngine { request ->
            when {
                request.url.encodedPath.endsWith("/chat/quota") ->
                    json(encode(AiChatQuota(enabled = true, dailyLimit = 3, monthlyLimit = 30, usedToday = 1, usedThisMonth = 4)))
                else -> json(encode(AiChatReply("Javob", basedOn = "Sikl 6-kun", remainingToday = 1, remainingThisMonth = 25)))
            }
        }
        val ai = graph(recording).aiController(AppState())

        ai.loadQuota()
        assertEquals(2, ai.remainingToday)

        val answer = assertNotNull(ai.ask("Nega charchayapman?"))
        assertEquals("Javob", answer.text)
        assertEquals(1, ai.remainingToday, "moved by the reply, not by local counting")
        assertTrue(ai.canAsk)
        assertTrue("\"question\":\"Nega charchayapman?\"" in recording.bodies.last())
    }

    @Test
    fun `an exhausted allowance reads as the daily limit, not as an error code`() = runTest {
        val recording = RecordingEngine { request ->
            if (request.url.encodedPath.endsWith("/chat/quota")) {
                json(encode(AiChatQuota(enabled = true, dailyLimit = 3, usedToday = 3)))
            } else {
                json(
                    errorBody(ErrorCodes.LIMIT_REACHED, "Limit tugadi", mapOf("feature" to "ai_chat", "period" to "day")),
                    HttpStatusCode.TooManyRequests,
                )
            }
        }
        val ai = graph(recording).aiController(AppState())

        ai.loadQuota()
        assertEquals(0, ai.remainingToday)
        assertTrue(!ai.canAsk)

        assertNull(ai.ask("Yana"))
        assertEquals("Bugungi limit tugadi.", ai.error)
    }

    @Test
    fun `a closed section is named as such rather than as a paywall`() = runTest {
        val recording = RecordingEngine { _ ->
            json(errorBody(ErrorCodes.FEATURE_DISABLED, "Yopiq", mapOf("flag" to "ai_chat_enabled")), HttpStatusCode.Forbidden)
        }
        val ai = graph(recording).aiController(AppState())
        assertNull(ai.ask("Salom"))
        assertEquals("Bu bo'lim hozircha yopiq.", ai.error)
    }

    @Test
    fun `offline the local rules answer and nothing is counted`() = runTest {
        val ai = AiController(null, AppState())
        val answer = assertNotNull(ai.ask("Bugun nima yeyin?"))
        assertTrue("kkal" in answer.text, answer.text)
        assertNull(ai.remainingToday)
        assertTrue(ai.canAsk)
    }
}
