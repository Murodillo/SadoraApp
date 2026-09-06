package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a notification is for.
 *
 * The categories are separate so a user can keep her medication reminders while turning
 * everything else off — the setting people actually want, and the one a single on/off
 * switch cannot give them.
 */
@Serializable
enum class NotificationCategory {
    @SerialName("med_reminder") MED_REMINDER,
    @SerialName("cycle") CYCLE,
    @SerialName("daily_check_in") DAILY_CHECK_IN,
    @SerialName("water") WATER,
    @SerialName("insight") INSIGHT,
    @SerialName("system") SYSTEM;

    /**
     * Categories that ignore the frequency caps and the quiet hours.
     *
     * A medication reminder at the time she chose is not marketing; suppressing it to
     * stay under a promotional budget would be the app failing at its job. Everything
     * else is capped.
     */
    val isEssential: Boolean
        get() = this == MED_REMINDER || this == SYSTEM
}

@Serializable
enum class NotificationStatus {
    @SerialName("queued") QUEUED,
    @SerialName("sent") SENT,
    @SerialName("failed") FAILED,
    @SerialName("suppressed") SUPPRESSED,
}

/** Why a notification was not delivered. Stable keys; the admin panel words them. */
object SuppressionReasons {
    const val NOTIFICATIONS_OFF = "notifications_off"
    const val CATEGORY_OFF = "category_off"
    const val QUIET_HOURS = "quiet_hours"
    const val DAILY_CAP = "daily_cap"
    const val WEEKLY_CAP = "weekly_cap"
    const val NO_DEVICE = "no_device"
}

/**
 * Per-user delivery preferences.
 *
 * [quietFrom] and [quietUntil] are in the user's own timezone and may wrap midnight —
 * 22:00 to 07:00 is the common case and the one that breaks naive comparisons.
 */
@Serializable
data class NotificationSettings(
    val enabled: Boolean = true,
    val categories: Map<NotificationCategory, Boolean> = emptyMap(),
    val quietFrom: LocalTime? = null,
    val quietUntil: LocalTime? = null,
) {
    fun isCategoryEnabled(category: NotificationCategory): Boolean =
        categories[category] ?: true
}

@Serializable
data class UpdateNotificationSettingsRequest(
    val enabled: Boolean? = null,
    val categories: Map<NotificationCategory, Boolean>? = null,
    val quietFrom: LocalTime? = null,
    val quietUntil: LocalTime? = null,
    /** Clears the quiet window; a null [quietFrom] alone means "unchanged". */
    val clearQuietHours: Boolean = false,
)

@Serializable
data class NotificationMessage(
    val id: String,
    val category: NotificationCategory,
    val title: String,
    val body: String,
    val scheduledFor: Instant,
    val status: NotificationStatus,
    val sentAt: Instant? = null,
    val suppressedReason: String? = null,
)

/** Global caps, set from the admin panel rather than compiled in. */
@Serializable
data class FrequencyCaps(
    val maxPerDay: Int = 6,
    val maxPerWeek: Int = 25,
)
