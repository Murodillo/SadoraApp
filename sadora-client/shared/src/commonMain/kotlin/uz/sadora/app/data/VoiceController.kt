package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import uz.sadora.app.data.api.VoiceApi
import uz.sadora.contract.SaveVoiceLogRequest
import uz.sadora.contract.VoiceLogDraft
import uz.sadora.contract.VoiceLogRequest
import uz.sadora.contract.VoiceOptions

/** Logging by voice: the allowance, the draft the server heard, and saving it. */
class VoiceController(private val api: VoiceApi?) {
    private val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error

    fun clearError() = calls.clearError()

    var options by mutableStateOf<VoiceOptions?>(null)
        private set
    /** Out of today's free notes, or Premium-only: the paywall's case. */
    var locked by mutableStateOf(false)
        private set

    suspend fun refresh() {
        val api = api ?: return
        calls.run(silent = true) { api.options() }?.let {
            options = it
            locked = it.allowance?.available == false
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun hear(bytes: ByteArray, mimeType: String): VoiceLogDraft? {
        val api = api ?: return null
        val draft = calls.run { api.hear(VoiceLogRequest(Base64.encode(bytes), mimeType)) }
        if (draft == null && calls.error.isAllowance()) {
            locked = true
            calls.clearError()
        }
        if (draft != null) refresh()
        return draft
    }

    suspend fun save(request: SaveVoiceLogRequest): Boolean {
        val api = api ?: return false
        return calls.run { api.save(request) } != null
    }
}
