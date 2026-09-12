package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uz.sadora.app.data.api.NotificationApi
import uz.sadora.contract.NotificationCategory
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
