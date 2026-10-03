package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Instant
import uz.sadora.app.data.api.StageEventApi
import uz.sadora.contract.FeedingSide
import uz.sadora.contract.LogStageEventRequest
import uz.sadora.contract.StageEvent
import uz.sadora.contract.StageEventKind

/**
 * The stage tools' records, one list per kind, newest first.
 *
 * Each screen reads its own kind on entering and adds to it as she taps. A saved event
 * goes to the top of its list from the server's answer, so what she sees is what was
 * stored — a feed the server refused does not linger on the screen as if it had not been.
 */
class StageEventsController(private val api: StageEventApi?) {
    private val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error

    fun clearError() = calls.clearError()

    var events by mutableStateOf<Map<StageEventKind, List<StageEvent>>>(emptyMap())
        private set

    // What is running right now. Held here rather than on a screen so a feed or a
    // contraction keeps counting while she looks at something else and comes back.

    /** The breast feed under way, and the side. */
    var feeding by mutableStateOf<Pair<FeedingSide, Instant>?>(null)

    /** The contraction under way. */
    var contractionStartedAt by mutableStateOf<Instant?>(null)

    /** The kick count under way, and how many so far. */
    var kicksStartedAt by mutableStateOf<Instant?>(null)
    var kicks by mutableStateOf(0)

    fun of(kind: StageEventKind): List<StageEvent> = events[kind].orEmpty()

    suspend fun load(kind: StageEventKind, days: Int = DEFAULT_DAYS) {
        val api = api ?: return
        calls.run(silent = true) { api.list(kind, days) }?.let { events = events + (kind to it) }
    }

    suspend fun log(request: LogStageEventRequest): StageEvent? {
        val api = api ?: return null
        val saved = calls.run { api.add(request) } ?: return null
        events = events + (saved.kind to (listOf(saved) + of(saved.kind)))
        return saved
    }

    suspend fun delete(event: StageEvent): Boolean {
        val api = api ?: return false
        calls.run { api.delete(event.id) } ?: return false
        events = events + (event.kind to of(event.kind).filterNot { it.id == event.id })
        return true
    }

    private companion object {
        const val DEFAULT_DAYS = 14
    }
}
