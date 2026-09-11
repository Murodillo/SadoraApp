package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uz.sadora.app.data.api.AiApi
import uz.sadora.app.model.AppState
import uz.sadora.app.model.localAnswerFor
import uz.sadora.contract.AiChatQuota

/** One answer, with what it was based on, for the chat screen to render. */
data class AiAnswer(val text: String, val basedOn: String)

/**
 * The chat's connection to SADORA AI.
 *
 * The server owns the allowance: every answer comes back with what is left, and that is
 * what the counter shows. With no backend the local rule engine answers instead, so the
 * prototype still has a chat, but it counts nothing — there is nothing to count against.
 */
class AiController(
    private val api: AiApi?,
    private val state: AppState,
) {
    val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error
    val isOffline: Boolean get() = api == null

    fun clearError() = calls.clearError()

    var quota by mutableStateOf<AiChatQuota?>(null)
        private set

    /**
     * The home screen's greeting line.
     *
     * Null until it loads, and the screen has its own line for that moment — a header
     * that popped in a second late would be worse than one that never moved.
     */
    var greeting by mutableStateOf<String?>(null)
        private set

    /**
     * Fetches a new greeting.
     *
     * Called on every entry to the home tab, which is the point: the server hands out a
     * different line each time, so returning to Today is what changes it.
     */
    suspend fun loadGreeting() {
        val api = api ?: return
        calls.run(silent = true) { api.greeting() }?.let { greeting = it.line }
    }

    /** Null until the quota has loaded; then the number the header shows. */
    val remainingToday: Int? get() = quota?.remainingToday

    val canAsk: Boolean
        get() = when {
            api == null -> true
            quota == null -> true
            else -> quota!!.enabled && quota!!.remainingToday != 0 && quota!!.remainingThisMonth != 0
        }

    suspend fun loadQuota() {
        val api = api ?: return
        calls.run(silent = true) { api.quota() }?.let { quota = it }
    }

    /** The answer, or null with [ApiCallState.error] set to something readable. */
    suspend fun ask(question: String): AiAnswer? {
        val api = api ?: return AiAnswer(localAnswerFor(question, state), "")
        val reply = calls.run { api.ask(question) } ?: return null
        quota = quota?.let { current ->
            current.copy(
                usedToday = current.dailyLimit?.let { limit -> limit - (reply.remainingToday ?: 0) } ?: current.usedToday + 1,
                usedThisMonth = current.monthlyLimit?.let { limit -> limit - (reply.remainingThisMonth ?: 0) }
                    ?: current.usedThisMonth + 1,
            )
        }
        return AiAnswer(reply.answer, reply.basedOn)
    }
}
