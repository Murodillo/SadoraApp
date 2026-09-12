package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uz.sadora.app.data.api.WearableApi
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.ProviderInfo
import uz.sadora.contract.ProviderKind
import uz.sadora.contract.SyncResult

/**
 * The devices screen's state: which providers exist, which are hers, and the connect
 * round trip.
 *
 * Connecting a cloud provider leaves the app — the consent page is the provider's, in
 * the browser — and comes back through a link. [connectStarted] remembers that we
 * went, so the return can be told apart from a cold launch; [returned] is what the
 * screen shows when the link brings her back.
 */
class WearableController(
    private val api: WearableApi?,
    private val analytics: Analytics = Analytics.None,
) {
    private val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error
    val isOffline: Boolean get() = api == null

    fun clearError() = calls.clearError()

    var providers by mutableStateOf<List<ProviderInfo>>(emptyList())
        private set

    /** The provider a connect flow was started for and not yet resolved. */
    var connectStarted by mutableStateOf<HealthProvider?>(null)
        private set

    /** The last return from a provider's page: true for connected, false for refused. */
    var returned by mutableStateOf<Boolean?>(null)
        private set

    /** The last manual sync's outcome, for the toast. */
    var lastSync by mutableStateOf<SyncResult?>(null)
        private set

    val connected: List<ProviderInfo> get() = providers.filter { it.connection != null }
    val available: List<ProviderInfo> get() = providers.filter { it.connection == null && it.available }
    val planned: List<ProviderInfo> get() = providers.filter { it.connection == null && !it.available }

    fun hasCloudConnection(): Boolean = connected.any { it.kind == ProviderKind.CLOUD }

    suspend fun load() {
        val api = api ?: return
        calls.run(silent = true) { api.providers() }?.let { providers = it }
    }

    /** Asks the server for the consent URL. The caller opens it; nothing here can. */
    suspend fun startConnect(provider: HealthProvider): String? {
        val api = api ?: return null
        val start = calls.run { api.connect(provider) } ?: return null
        connectStarted = provider
        returned = null
        analytics.event(AnalyticsEvents.DEVICE_CONNECT_STARTED, mapOf("provider" to provider.name.lowercase()))
        return start.authorizeUrl
    }

    /** The link brought her back. Reloads the list so the new connection is drawn. */
    suspend fun onReturned(provider: String, ok: Boolean) {
        returned = ok
        connectStarted = null
        if (ok) analytics.event(AnalyticsEvents.DEVICE_CONNECTED, mapOf("provider" to provider))
        load()
    }

    fun clearReturned() {
        returned = null
    }

    suspend fun disconnect(provider: HealthProvider): Boolean {
        val api = api ?: return false
        val ok = calls.run { api.disconnect(provider) } != null
        if (ok) {
            analytics.event(AnalyticsEvents.DEVICE_DISCONNECTED, mapOf("provider" to provider.name.lowercase()))
            load()
        }
        return ok
    }

    suspend fun syncNow(provider: HealthProvider): SyncResult? {
        val api = api ?: return null
        return calls.run { api.sync(provider) }?.also {
            lastSync = it
            load()
        }
    }
}
