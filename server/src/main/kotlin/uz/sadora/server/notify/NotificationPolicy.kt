package uz.sadora.server.notify

import kotlinx.datetime.LocalTime
import uz.sadora.contract.FrequencyCaps
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.NotificationSettings
import uz.sadora.contract.SuppressionReasons

/** What the scheduler should do with one candidate notification. */
sealed interface DeliveryDecision {
    data object Send : DeliveryDecision
    data class Suppress(val reason: String) : DeliveryDecision
}

/**
 * Decides whether a notification may be delivered.
 *
 * Pure, so every combination can be tested exactly — the failure mode here is silent in
 * both directions: a suppressed medication reminder is a missed dose, and a cap that
 * does not hold is an app people mute.
 */
object NotificationPolicy {

    fun decide(
        category: NotificationCategory,
        localTime: LocalTime,
        settings: NotificationSettings,
        sentToday: Int,
        sentThisWeek: Int,
        caps: FrequencyCaps,
        hasDevice: Boolean,
    ): DeliveryDecision {
        if (!hasDevice) return DeliveryDecision.Suppress(SuppressionReasons.NO_DEVICE)

        // The master switch and the per-category switch are the user's own decision, so
        // they outrank even an essential category.
        if (!settings.enabled) return DeliveryDecision.Suppress(SuppressionReasons.NOTIFICATIONS_OFF)
        if (!settings.isCategoryEnabled(category)) {
            return DeliveryDecision.Suppress(SuppressionReasons.CATEGORY_OFF)
        }

        // A medication reminder at the time she set is not promotional traffic: holding
        // it back to stay under a budget would be the app failing at the thing she asked
        // it to do.
        if (category.isEssential) return DeliveryDecision.Send

        if (isQuiet(localTime, settings.quietFrom, settings.quietUntil)) {
            return DeliveryDecision.Suppress(SuppressionReasons.QUIET_HOURS)
        }
        if (sentToday >= caps.maxPerDay) return DeliveryDecision.Suppress(SuppressionReasons.DAILY_CAP)
        if (sentThisWeek >= caps.maxPerWeek) return DeliveryDecision.Suppress(SuppressionReasons.WEEKLY_CAP)

        return DeliveryDecision.Send
    }

    /**
     * Whether a local time falls inside the quiet window.
     *
     * The window normally wraps midnight — 22:00 to 07:00 — which a plain `in` range
     * gets backwards, so the two cases are handled separately. A window whose ends are
     * equal is treated as no window rather than as the whole day.
     */
    fun isQuiet(time: LocalTime, from: LocalTime?, until: LocalTime?): Boolean {
        if (from == null || until == null) return false
        if (from == until) return false
        return if (from < until) time >= from && time < until else time >= from || time < until
    }
}
