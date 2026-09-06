package org.example.project.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.example.project.model.AppState
import org.example.project.model.CommunityTopic
import org.example.project.model.Fmt
import uz.sadora.contract.CommunityComment as WireComment
import uz.sadora.contract.CommunityPost as WirePost
import uz.sadora.contract.CommunityTopic as WireTopic

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
    fun `her own like comes off the server's count so the store can add it back`() {
        val post = wirePost(liked = true, likeCount = 5).toAppPost(now = TestNow)
        assertEquals(4, post.likes)

        val state = AppState()
        state.replaceCommunityFeed(listOf(post), liked = setOf("p1"), saved = setOf("p1"))
        assertEquals(5, state.likeCount(post), "the screen shows the server's total")
        assertEquals(3, state.commentCountOf(post), "the count stands in until the comments load")
    }

    @Test
    fun `a post she has not liked keeps the server's count whole`() {
        assertEquals(5, wirePost(liked = false, likeCount = 5).toAppPost(now = TestNow).likes)
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
        assertEquals("hozir", Fmt.ago(TestNow - 30.seconds, TestNow))
        assertEquals("20 daqiqa oldin", Fmt.ago(TestNow - 20.minutes, TestNow))
        assertEquals("3 soat oldin", Fmt.ago(TestNow - 3.hours, TestNow))
        assertEquals("kecha", Fmt.ago(TestNow - 30.hours, TestNow))
        assertEquals("5 kun oldin", Fmt.ago(TestNow - 5.days, TestNow))
    }

    @Test
    fun `a loaded comment marked as hers renders as hers`() {
        val comment = WireComment("c1", "p1", "Iliq Shabnam", 1, "Javob", TestNow - 5.minutes, isMine = true)
            .toAppComment(now = TestNow)
        assertEquals(true, comment.isMine)
        assertEquals("5 daqiqa oldin", comment.ago)
    }
}
