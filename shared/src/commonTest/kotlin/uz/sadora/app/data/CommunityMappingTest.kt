package uz.sadora.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import uz.sadora.app.model.AppState
import uz.sadora.app.model.CommunityTopic
import uz.sadora.app.i18n.StringsEn
import uz.sadora.app.i18n.StringsRu
import uz.sadora.app.i18n.StringsUz
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
}
