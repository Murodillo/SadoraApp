package uz.sadora.app.data

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Collects the posts that were on her screen and reports them a batch at a time.
 *
 * A feed scrolls past a post every second or two, and a request for each would cost
 * more than the number is worth. So an id waits for [FLUSH_AFTER], or until the batch
 * is full, or until the screen is left. A post is reported once a session: the server
 * counts a reader once anyway, and asking again is traffic for nothing.
 *
 * A batch that fails is tried once more with the next one and then let go. Nothing is
 * kept on disk; a view lost to a dead connection is not worth a queue.
 *
 * Not thread-safe: called from the UI, and [scope] is the UI's.
 */
class PostViewTracker(
    private val scope: CoroutineScope,
    /** Sends one batch; false when it did not arrive. */
    private val send: suspend (List<String>) -> Boolean,
) {
    private val noted = mutableSetOf<String>()
    private val retried = mutableSetOf<String>()
    private val pending = mutableListOf<String>()
    private var timer: Job? = null

    fun seen(postId: String) {
        if (!noted.add(postId)) return
        pending += postId
        if (pending.size >= MAX_BATCH) flush() else flushLater()
    }

    /** Sends what is waiting now, without the wait. */
    fun flush() {
        timer?.cancel()
        timer = null
        if (pending.isEmpty()) return
        val batch = pending.toList()
        pending.clear()
        scope.launch {
            if (send(batch)) return@launch
            val again = batch.filter(retried::add)
            if (again.isEmpty()) return@launch
            pending.addAll(0, again)
            flushLater()
        }
    }

    private fun flushLater() {
        if (timer != null) return
        timer = scope.launch {
            delay(FLUSH_AFTER)
            timer = null
            flush()
        }
    }

    companion object {
        val FLUSH_AFTER = 10.seconds
        const val MAX_BATCH = 20
    }
}
