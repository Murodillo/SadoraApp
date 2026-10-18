package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uz.sadora.app.data.api.TelegramApi
import uz.sadora.contract.TelegramStatus

/** Whether her Telegram is linked, and the three things she can do about it. */
class TelegramController(private val api: TelegramApi?) {
    private val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error

    fun clearError() = calls.clearError()

    var status by mutableStateOf<TelegramStatus?>(null)
        private set

    suspend fun refresh() {
        val api = api ?: return
        calls.run(silent = true) { api.status() }?.let { status = it }
    }

    /** The t.me link to open, or null when the server has no bot yet. */
    suspend fun link(): String? {
        val api = api ?: return null
        return calls.run { api.link() }?.url
    }

    suspend fun setDetailed(detailed: Boolean) {
        val api = api ?: return
        calls.run { api.setDetailed(detailed) }?.let { status = it }
    }

    suspend fun unlink(): Boolean {
        val api = api ?: return false
        val updated = calls.run { api.unlink() } ?: return false
        status = updated
        return true
    }
}
