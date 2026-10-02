package uz.sadora.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import uz.sadora.app.data.api.CommunityApi
import uz.sadora.app.model.AppState
import uz.sadora.contract.CommunityComment
import uz.sadora.contract.CommunityIdentity
import uz.sadora.contract.CommunityPost
import uz.sadora.contract.CommunityTopic as WireTopic
import uz.sadora.contract.Conversation
import uz.sadora.contract.ConversationThread
import uz.sadora.contract.DirectMessage
import uz.sadora.contract.MessagePage
import uz.sadora.contract.Page

/**
 * The chat's long lists arrive a page at a time. What is pinned: scrolling up a thread
 * reads the lines before its first one and the poll keeps them; the threads list and
 * the feed read on from their last item and a refresh does not cut them back; and a
 * post's comments read on by offset.
 */
class PagingControllerTest {

    private fun graph(recording: RecordingEngine) = SadoraGraph(
        tokenStorage = InMemoryTokenStorage(token = "refresh-0"),
        device = FixedDeviceIdentity(),
        environment = SadoraEnvironment("http://test.local"),
        engine = recording.build(),
    )

    private fun line(id: String, minute: Int) = DirectMessage(id, "Line $id", TestNow + minute.minutes, isMine = false)

    private fun conversation(id: String, minutesAgo: Int) =
        Conversation(id = id, alias = "Alias $id", tint = 1, lastMessage = "…", lastMessageAt = TestNow - minutesAgo.minutes)

    @Test
    fun `scrolling up a thread reads the older page and the poll keeps it`() = runTest {
        var latest = listOf(line("m3", 3), line("m4", 4))
        val queries = mutableListOf<String>()
        val recording = RecordingEngine { request ->
            val path = request.url.encodedPath
            queries.add(request.url.encodedQuery)
            when {
                path.endsWith("/conversations/c1/messages") ->
                    json(encode(MessagePage(listOf(line("m1", 1), line("m2", 2)), hasMore = false)))
                path.endsWith("/conversations/c1") ->
                    json(encode(ConversationThread(conversation("c1", 0), latest, hasMore = true)))
                else -> json(encode(CommunityIdentity("Men", 0)))
            }
        }
        val controller = graph(recording).messagesController()

        controller.open("c1", "Alias c1")
        assertEquals(listOf("m3", "m4"), controller.messages.map { it.id })
        assertTrue(controller.hasOlder)

        controller.loadOlder()
        assertTrue(queries.any { it.contains("before=m3") }, "read upward from the first line on screen")
        assertEquals(listOf("m1", "m2", "m3", "m4"), controller.messages.map { it.id })
        assertFalse(controller.hasOlder)

        // A new line arrives; the poll re-reads the newest page only.
        latest = listOf(line("m4", 4), line("m5", 5))
        controller.refreshThread("c1")
        assertEquals(listOf("m1", "m2", "m3", "m4", "m5"), controller.messages.map { it.id })
        assertFalse(controller.hasOlder, "everything above is still on screen")
    }

    @Test
    fun `the threads list reads on from its oldest thread and a refresh keeps the pages`() = runTest {
        val page = CommunityApi.CONVERSATION_PAGE
        val first = (0 until page).map { conversation("c$it", it) }
        val second = listOf(conversation("old1", page + 5), conversation("old2", page + 6))
        val befores = mutableListOf<String?>()
        val recording = RecordingEngine { request ->
            val before = request.url.parameters["before"]
            befores.add(before)
            json(encode(if (before == null) first else second))
        }
        val controller = graph(recording).messagesController()

        controller.load()
        assertEquals(page, controller.conversations.size)
        assertTrue(controller.hasMoreConversations)

        controller.loadMoreConversations()
        assertEquals(first.last().lastMessageAt.toString(), befores.last(), "the cursor is the oldest thread's time")
        assertEquals(page + 2, controller.conversations.size)
        assertFalse(controller.hasMoreConversations, "a short page is the last")

        controller.load()
        assertEquals(page + 2, controller.conversations.size, "the refresh did not cut the list back")
        assertFalse(controller.hasMoreConversations)
    }

    private fun post(id: String, minutesAgo: Int, comments: Int = 0) = CommunityPost(
        id = id,
        topic = WireTopic.WELLBEING,
        alias = "Yorug' Tong",
        tint = 1,
        body = "Post $id",
        createdAt = TestNow - minutesAgo.minutes,
        likeCount = 0,
        commentCount = comments,
    )

    @Test
    fun `the feed reads its next page by offset and drops what it already holds`() = runTest {
        val page = CommunityApi.FEED_PAGE
        val all = (0 until page + 3).map { post("p$it", it) }
        val recording = RecordingEngine { request ->
            when {
                request.url.encodedPath.endsWith("/community/me") -> json(encode(CommunityIdentity("Men", 0)))
                else -> {
                    val offset = request.url.parameters["offset"]!!.toInt()
                    val limit = request.url.parameters["limit"]!!.toInt()
                    // Someone posted in between: the second page starts one earlier.
                    val from = if (offset == 0) 0 else offset - 1
                    val items = all.drop(from).take(limit)
                    json(encode(Page(items, all.size.toLong(), limit, from)))
                }
            }
        }
        val state = AppState()
        val controller = graph(recording).communityController(state)

        controller.load()
        assertEquals(page, state.communityPosts.size)
        assertTrue(controller.feedHasMore)

        controller.loadMoreFeed()
        assertEquals(all.map { it.id }, state.communityPosts.map { it.id }, "no post twice")
        assertFalse(controller.feedHasMore)

        controller.refreshFeed()
        assertEquals(all.size, state.communityPosts.size, "the refresh kept the second page")
    }

    @Test
    fun `a post's comments read on by offset`() = runTest {
        val page = CommunityApi.COMMENT_PAGE
        val all = (0 until page + 2).map {
            CommunityComment(id = "k$it", postId = "p0", alias = "A", tint = 0, body = "c$it", createdAt = TestNow)
        }
        val recording = RecordingEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/comments") -> {
                    val offset = request.url.parameters["offset"]!!.toInt()
                    val limit = request.url.parameters["limit"]!!.toInt()
                    json(encode(all.drop(offset).take(limit)))
                }
                path.endsWith("/community/me") -> json(encode(CommunityIdentity("Men", 0)))
                else -> json(encode(Page(listOf(post("p0", 0, comments = all.size)), 1, 30, 0)))
            }
        }
        val state = AppState()
        val controller = graph(recording).communityController(state)
        controller.load()

        controller.loadComments("p0")
        assertEquals(page, state.communityPosts.single().comments.size)
        assertTrue("p0" in controller.commentsWithMore)
        assertEquals(all.size, state.commentCountOf(state.communityPosts.single()), "the count is the post's, not the page's")

        controller.loadMoreComments("p0")
        assertEquals(all.map { it.id }, state.communityPosts.single().comments.map { it.id })
        assertFalse("p0" in controller.commentsWithMore)
    }
}
