package uz.sadora.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * The posts she has had on screen go up in batches, each post once a session. A batch
 * that fails is tried once more and then let go: a view is not worth a queue on disk.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PostViewTrackerTest {

    @Test
    fun `posts seen close together go up as one batch after the wait`() = runTest {
        val sent = mutableListOf<List<String>>()
        val tracker = PostViewTracker(this) { sent += it; true }

        tracker.seen("a")
        advanceTimeBy(3.seconds)
        tracker.seen("b")
        assertEquals(emptyList(), sent, "nothing leaves before the wait is over")

        advanceUntilIdle()
        assertEquals(listOf(listOf("a", "b")), sent)
    }

    @Test
    fun `a post is reported once however often it comes back on screen`() = runTest {
        val sent = mutableListOf<List<String>>()
        val tracker = PostViewTracker(this) { sent += it; true }

        tracker.seen("a")
        tracker.seen("a")
        advanceUntilIdle()
        tracker.seen("a")
        advanceUntilIdle()

        assertEquals(listOf(listOf("a")), sent)
    }

    @Test
    fun `a full batch leaves at once and leaving the screen sends what is waiting`() = runTest {
        val sent = mutableListOf<List<String>>()
        val tracker = PostViewTracker(this) { sent += it; true }

        val full = (1..PostViewTracker.MAX_BATCH).map { "p$it" }
        full.forEach(tracker::seen)
        testScheduler.runCurrent()
        assertEquals(listOf(full), sent, "no wait once the batch is full")

        tracker.seen("late")
        tracker.flush()
        testScheduler.runCurrent()
        assertEquals(listOf(full, listOf("late")), sent)
    }

    @Test
    fun `a failed batch is tried once more and then dropped`() = runTest {
        val sent = mutableListOf<List<String>>()
        val tracker = PostViewTracker(this) { sent += it; false }

        tracker.seen("a")
        advanceUntilIdle()
        assertEquals(listOf(listOf("a"), listOf("a")), sent)

        tracker.seen("a")
        advanceUntilIdle()
        assertEquals(2, sent.size, "given up on, not queued for ever")
    }
}
