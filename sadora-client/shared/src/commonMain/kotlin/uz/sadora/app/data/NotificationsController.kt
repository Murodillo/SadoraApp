package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uz.sadora.app.data.api.NotificationApi
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.NotificationMessage
import uz.sadora.contract.NotificationStatus
import uz.sadora.contract.NotificationSettings
import uz.sadora.contract.UpdateNotificationSettingsRequest

/**
 * The notification settings screen's state.
 *
 * The four switches used to write to a local flag that nothing read, so turning water
 * reminders off did nothing at all. They now edit the server's settings — the same ones
 * the scheduler consults before it sends — and each flip is saved as it happens, because
 * a settings screen with a Save button is one where the switch she flipped last is the
 * one that did not stick.
 */
class NotificationsController(private val api: NotificationApi?) {
    private val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error

    var settings by mutableStateOf(NotificationSettings())
        private set

    /** What actually reached her phone, newest first — the bell's list. */
    var sent by mutableStateOf<List<NotificationMessage>>(emptyList())
        private set

    /** Older ones than the last read are still on the server. */
    var sentHasMore by mutableStateOf(false)
        private set

    /**
     * Every row read so far in the server's order, suppressed ones included: the cursor
     * for the next page is the last of these, not the last one shown.
     */
    private var history by mutableStateOf<List<NotificationMessage>>(emptyList())
    private var loadingSent = false

    /** How many rows have been read, shown or not — the key that asks for the next page. */
    val historyLoaded: Int get() = history.size

    /** Reads the newest page. Pages she has already scrolled to stay under it. */
    suspend fun loadSent() {
        val api = api ?: return
        calls.run(silent = true) { api.history() }?.let { latest ->
            val full = latest.size >= NotificationApi.HISTORY_PAGE
            val ids = latest.mapTo(HashSet()) { it.id }
            val edge = history.indexOfFirst { it.id == latest.lastOrNull()?.id }
            val tail = if (full && edge >= 0) history.drop(edge + 1).filter { it.id !in ids } else emptyList()
            showHistory(latest + tail)
            sentHasMore = full && (tail.isEmpty() || sentHasMore)
        }
    }

    /** The next page, below the oldest row read. */
    suspend fun loadMoreSent() {
        val api = api ?: return
        val oldest = history.lastOrNull() ?: return
        if (!sentHasMore || loadingSent) return
        loadingSent = true
        try {
            val page = calls.run(silent = true) { api.history(beforeId = oldest.id) } ?: return
            val known = history.mapTo(HashSet()) { it.id }
            showHistory(history + page.filter { it.id !in known })
            sentHasMore = page.size >= NotificationApi.HISTORY_PAGE
        } finally {
            loadingSent = false
        }
    }

    private fun showHistory(rows: List<NotificationMessage>) {
        history = rows
        // Suppressed and failed ones never arrived; showing them would be news to her.
        sent = rows.filter { it.status == NotificationStatus.SENT }
            .sortedByDescending { it.sentAt ?: it.scheduledFor }
    }

    suspend fun load() {
        val api = api ?: return
        calls.run(silent = true) { api.settings() }?.let { settings = it }
    }

    fun isEnabled(category: NotificationCategory): Boolean =
        settings.enabled && settings.isCategoryEnabled(category)

    suspend fun setCategory(category: NotificationCategory, enabled: Boolean) {
        // Optimistic: the switch moves at once, and the server's answer settles it.
        settings = settings.copy(categories = settings.categories + (category to enabled))
        val api = api ?: return
        calls.run { api.updateSettings(UpdateNotificationSettingsRequest(categories = mapOf(category to enabled))) }
            ?.let { settings = it }
    }

    suspend fun setAll(enabled: Boolean) {
        settings = settings.copy(enabled = enabled)
        val api = api ?: return
        calls.run { api.updateSettings(UpdateNotificationSettingsRequest(enabled = enabled)) }?.let { settings = it }
    }
}
