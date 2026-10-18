package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uz.sadora.app.data.api.WrappedApi
import uz.sadora.contract.Wrapped
import uz.sadora.contract.WrappedIndex

/** Cycle Wrapped: the recaps she has, the one on screen, and the fresh one Today offers. */
class WrappedController(private val api: WrappedApi?) {
    private val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error

    fun clearError() = calls.clearError()

    var index by mutableStateOf<WrappedIndex?>(null)
        private set

    var current by mutableStateOf<Wrapped?>(null)
        private set

    suspend fun refresh() {
        val api = api ?: return
        calls.run(silent = true) { api.index() }?.let { index = it }
    }

    /** Loads a recap and marks it opened, so Today's card goes away. */
    suspend fun open(key: String) {
        val api = api ?: return
        if (current?.key != key) current = null
        calls.run { api.get(key) }?.let { current = it }
        if (index?.items?.any { it.key == key && it.fresh } == true) {
            calls.run(silent = true) { api.seen(key) }
            index = index?.let { idx -> idx.copy(items = idx.items.map { if (it.key == key) it.copy(fresh = false) else it }) }
        }
    }
}
