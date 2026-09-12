package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Clock
import uz.sadora.app.data.api.ShareApi
import uz.sadora.contract.DoctorSummary
import uz.sadora.contract.ProfileShare

/**
 * The QR code screen's state.
 *
 * The link only ever exists on the response that created it — the server keeps a hash
 * — so [current] is the one copy, held here for as long as the app runs. Reopening the
 * screen shows the share's status (views, expiry) from the list; making a new link is
 * how she gets a code again, and it retires the old one.
 */
class ShareController(
    private val api: ShareApi?,
    private val analytics: Analytics = Analytics.None,
) {
    private val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error
    val isOffline: Boolean get() = api == null

    fun clearError() = calls.clearError()

    /** The latest share, with its URL when this app made it. */
    var current by mutableStateOf<ProfileShare?>(null)
        private set

    /** Her export, once fetched — the settings screen hands it to the share sheet. */
    var export by mutableStateOf<DoctorSummary?>(null)
        private set

    suspend fun refresh() {
        val api = api ?: return
        calls.run(silent = true) { api.list() }?.let { shares ->
            val newest = shares.firstOrNull() ?: return@let
            // Keep the URL we hold if it is the same share; the list never carries one.
            current = if (current?.id == newest.id) newest.copy(url = current?.url) else newest.copy(url = null)
        }
    }

    suspend fun create(ttlHours: Int): ProfileShare? {
        val api = api ?: return null
        val created = calls.run { api.create(ttlHours) } ?: return null
        current = created
        analytics.event(AnalyticsEvents.SHARE_CREATED, mapOf("ttl_hours" to ttlHours.toString()))
        return created
    }

    suspend fun revoke(): Boolean {
        val api = api ?: return false
        val share = current ?: return false
        val ok = calls.run { api.revoke(share.id) } != null
        if (ok) {
            current = share.copy(url = null, revokedAt = Clock.System.now())
            analytics.event(AnalyticsEvents.SHARE_REVOKED)
        }
        return ok
    }

    suspend fun loadExport(language: String): DoctorSummary? {
        val api = api ?: return null
        return calls.run { api.export(language) }?.also { export = it }
    }
}
