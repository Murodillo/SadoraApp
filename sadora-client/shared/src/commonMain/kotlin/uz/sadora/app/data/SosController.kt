package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uz.sadora.app.data.api.SosApi
import uz.sadora.contract.SaveSosContactsRequest
import uz.sadora.contract.SosAlertRequest
import uz.sadora.contract.SosAlertResult
import uz.sadora.contract.SosContact
import uz.sadora.contract.SosState

/** SOS: who gets the alert, and sending it. */
class SosController(private val api: SosApi?) {
    private val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error

    fun clearError() = calls.clearError()

    var state by mutableStateOf<SosState?>(null)
        private set

    suspend fun refresh() {
        val api = api ?: return
        calls.run(silent = true) { api.state() }?.let { state = it }
    }

    suspend fun saveContacts(contacts: List<SosContact>): Boolean {
        val api = api ?: return false
        val updated = calls.run { api.saveContacts(SaveSosContactsRequest(contacts)) } ?: return false
        state = updated
        return true
    }

    suspend fun alert(request: SosAlertRequest): SosAlertResult? {
        val api = api ?: return null
        return calls.run { api.alert(request) }
    }
}
