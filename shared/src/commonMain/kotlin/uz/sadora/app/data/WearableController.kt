package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Instant
import uz.sadora.app.data.api.WearableApi
import uz.sadora.app.data.health.DeviceHealthSync
import uz.sadora.app.data.health.DeviceSyncOutcome
import uz.sadora.app.data.health.HealthAvailability
import uz.sadora.app.data.health.HealthPlatform
import uz.sadora.contract.ConnectionStatus
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.ProviderInfo
import uz.sadora.contract.ProviderKind
import uz.sadora.contract.ProviderUnavailable
import uz.sadora.contract.SyncResult
import uz.sadora.contract.WearableConnection

/**
 * The devices screen's state: which providers exist, which are hers, and the connect
 * round trip.
 *
 * Connecting a cloud provider leaves the app — the consent page is the provider's, in
 * the browser — and comes back through a link. [connectStarted] remembers that we
 * went, so the return can be told apart from a cold launch; [returned] is what the
 * screen shows when the link brings her back.
 *
 * The phone's own store (HealthKit, Health Connect) has no server-side connection: it is
 * switched on here, on this phone, and [listed] folds that local state into the
 * server's list so the screen draws one list either way.
 */
class WearableController(
    private val api: WearableApi?,
    private val analytics: Analytics = Analytics.None,
    private val device: DeviceHealthSync? = null,
    private val currentUserId: () -> String? = { null },
) {
    private val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error
    val isOffline: Boolean get() = api == null

    fun clearError() = calls.clearError()

    /** The server's list, as it sent it. */
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

    // ---------------------------------------------------------------- this phone

    val devicePlatform: HealthPlatform get() = device?.platform ?: HealthPlatform.None

    var deviceAvailability by mutableStateOf(HealthAvailability.UNSUPPORTED)
        private set
    var deviceEnabled by mutableStateOf(false)
        private set
    var deviceAccess by mutableStateOf(false)
        private set
    var deviceLastSync by mutableStateOf<Instant?>(null)
        private set
    var deviceSyncing by mutableStateOf(false)
        private set
    var lastDeviceSync by mutableStateOf<DeviceSyncOutcome?>(null)
        private set

    /** Health Connect is missing or too old — the fix is a Play Store visit, not a permission. */
    val deviceNeedsInstall: Boolean
        get() = deviceAvailability == HealthAvailability.NOT_INSTALLED ||
            deviceAvailability == HealthAvailability.UPDATE_REQUIRED

    /** The server's providers with this phone's store folded in. */
    val listed: List<ProviderInfo> get() = providers.map { it.onThisPhone() }

    val connected: List<ProviderInfo> get() = listed.filter { it.connection != null }
    val available: List<ProviderInfo> get() = listed.filter { it.connection == null && it.available }
    val planned: List<ProviderInfo> get() = listed.filter { it.connection == null && !it.available }

    fun hasCloudConnection(): Boolean = connected.any { it.kind == ProviderKind.CLOUD }

    private fun isThisPhone(provider: HealthProvider) = device?.platform?.provider == provider

    private fun ProviderInfo.onThisPhone(): ProviderInfo {
        if (kind != ProviderKind.ON_DEVICE) return this
        if (provider != HealthProvider.APPLE_HEALTH && provider != HealthProvider.HEALTH_CONNECT) return this
        if (!isThisPhone(provider)) {
            // An iPhone cannot read Health Connect and an Android phone cannot read
            // HealthKit, whatever the server says about either.
            val reason = if (provider == HealthProvider.APPLE_HEALTH) ProviderUnavailable.IOS_ONLY else ProviderUnavailable.ANDROID_ONLY
            return copy(available = false, unavailableReason = reason)
        }
        if (!available) return this
        if (deviceEnabled) {
            val status = if (deviceAccess) ConnectionStatus.ACTIVE else ConnectionStatus.EXPIRED
            return copy(
                connection = WearableConnection(
                    provider = provider,
                    status = status,
                    connectedAt = deviceLastSync ?: Instant.fromEpochMilliseconds(0),
                    lastSyncAt = deviceLastSync,
                ),
            )
        }
        if (deviceAvailability == HealthAvailability.UNSUPPORTED) {
            return copy(available = false, unavailableReason = UnsupportedReason)
        }
        return this
    }

    suspend fun load() {
        refreshDevice()
        val api = api ?: return
        calls.run(silent = true) { api.providers() }?.let { providers = it }
    }

    /** Re-reads the phone's side: after the permission screen, or back from the Play Store. */
    suspend fun refreshDevice() {
        val device = device ?: return
        val userId = currentUserId() ?: return
        deviceAvailability = device.platform.availability()
        deviceAccess = device.platform.hasAccess()
        val state = device.state(userId)
        deviceEnabled = state.enabled
        deviceLastSync = state.lastSyncAt
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

    /**
     * The phone's permission sheet has closed. Switches reading on and runs the first
     * sync when it may go ahead; null when it may not or nothing ran.
     */
    suspend fun onDeviceAccess(granted: Boolean): DeviceSyncOutcome? {
        val device = device ?: return null
        val userId = currentUserId() ?: return null
        if (!granted) {
            refreshDevice()
            return null
        }
        if (!device.state(userId).enabled) {
            device.enable(userId)
            device.platform.provider?.let { provider ->
                analytics.event(AnalyticsEvents.DEVICE_CONNECTED, mapOf("provider" to provider.name.lowercase()))
            }
        }
        refreshDevice()
        return syncDevice(force = true)
    }

    /** Reads the phone's store and posts it. [force] skips the quarter-hour rest between runs. */
    suspend fun syncDevice(force: Boolean): DeviceSyncOutcome? {
        val device = device ?: return null
        val userId = currentUserId() ?: return null
        if (deviceSyncing) return null
        deviceSyncing = true
        return try {
            device.sync(userId, force)?.also { lastDeviceSync = it }
        } finally {
            deviceSyncing = false
            refreshDevice()
        }
    }

    /** The link brought her back. Reloads the list so the new connection is drawn. */
    /**
     * Back from the provider's consent page. Success there is only half of it: the code
     * it brought is sent from here, signed in as her, and the connection exists once the
     * server accepts it.
     */
    suspend fun onReturned(provider: String, ok: Boolean, code: String? = null, state: String? = null) {
        connectStarted = null
        val known = HealthProvider.entries.firstOrNull { it.name.equals(provider, ignoreCase = true) }
        val completed = ok && code != null && state != null && known != null &&
            api?.let { calls.run { it.complete(known, state, code) } } != null
        returned = completed
        if (completed) analytics.event(AnalyticsEvents.DEVICE_CONNECTED, mapOf("provider" to provider))
        load()
    }

    fun clearReturned() {
        returned = null
    }

    suspend fun disconnect(provider: HealthProvider): Boolean {
        if (isThisPhone(provider)) {
            val userId = currentUserId() ?: return false
            device?.disable(userId)
            analytics.event(AnalyticsEvents.DEVICE_DISCONNECTED, mapOf("provider" to provider.name.lowercase()))
            refreshDevice()
            return true
        }
        val api = api ?: return false
        val ok = calls.run { api.disconnect(provider) } != null
        if (ok) {
            analytics.event(AnalyticsEvents.DEVICE_DISCONNECTED, mapOf("provider" to provider.name.lowercase()))
            load()
        }
        return ok
    }

    suspend fun syncNow(provider: HealthProvider): SyncResult? {
        if (isThisPhone(provider)) {
            val outcome = syncDevice(force = true) ?: return null
            if (outcome.failure != null) return null
            return SyncResult(provider, outcome.accepted, outcome.updated, outcome.daysAffected).also { lastSync = it }
        }
        val api = api ?: return null
        return calls.run { api.sync(provider) }?.also {
            lastSync = it
            load()
        }
    }

    companion object {
        /** A store this phone does not have at all. Worded by `DeviceStrings.unavailable`. */
        const val UnsupportedReason = "unsupported"
    }
}
