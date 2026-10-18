package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.LocalTime
import uz.sadora.app.data.api.KegelApi
import uz.sadora.contract.KegelProgram
import uz.sadora.contract.KegelSessionResult
import uz.sadora.contract.KegelSettingsRequest
import uz.sadora.contract.KegelState
import uz.sadora.contract.LogKegelSessionRequest
import uz.sadora.contract.SaveKegelCheckRequest

/**
 * Kegel exercises: the server's state of her program, and the three things she does to it —
 * finish a session, change her reminders or program, take the leak check.
 */
class KegelController(private val api: KegelApi?) {
    private val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error

    fun clearError() = calls.clearError()

    var state by mutableStateOf<KegelState?>(null)
        private set

    /** The level she picked on the program page, for the session to play; null is her highest open one. */
    var selectedLevel by mutableStateOf<Int?>(null)

    /** The check was put off on this run of the app; it is offered again next time. */
    var checkSnoozed by mutableStateOf(false)
        private set

    fun snoozeCheck() {
        checkSnoozed = true
    }

    suspend fun refresh() {
        val api = api ?: return
        calls.run(silent = true) { api.state() }?.let { state = it }
    }

    /** The session she finished; null when it could not be saved (and [error] says why). */
    suspend fun finish(program: KegelProgram, level: Int, reps: Int, seconds: Int): KegelSessionResult? {
        val api = api ?: return null
        val result = calls.run { api.logSession(LogKegelSessionRequest(program, level, reps, seconds.coerceIn(1, 3600))) }
            ?: return null
        state = result.state
        return result
    }

    suspend fun saveSettings(program: KegelProgram?, reminders: List<LocalTime>): Boolean {
        val api = api ?: return false
        val updated = calls.run { api.saveSettings(KegelSettingsRequest(program, reminders)) } ?: return false
        state = updated
        return true
    }

    suspend fun saveCheck(request: SaveKegelCheckRequest): Boolean {
        val api = api ?: return false
        val updated = calls.run { api.saveCheck(request) } ?: return false
        state = updated
        return true
    }
}
