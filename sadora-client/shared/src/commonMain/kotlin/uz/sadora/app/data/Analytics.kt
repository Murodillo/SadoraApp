package uz.sadora.app.data

/**
 * Product analytics, behind an interface the shared code owns.
 *
 * Firebase is an Android and iOS SDK, not a Kotlin Multiplatform one, so each platform
 * hands in its own implementation the way it hands in token storage. The shared code
 * decides *what* is worth recording and never touches an SDK; a test or a preview gets
 * [None] and records nothing.
 *
 * Two rules. Nothing here is sent until she has agreed to it — [setEnabled] follows the
 * analytics consent, off by default, and the platform keeps collection off until it is
 * called. And no event carries a health value: an event says "she opened the sleep
 * screen", never "she slept six hours". The record of what she did is hers; the record
 * of which screens are used is the product's.
 */
interface Analytics {
    /** Follows the analytics consent. Off until called with true. */
    fun setEnabled(enabled: Boolean)

    /** A screen came into view. [name] is the route or tab name, stable across releases. */
    fun screen(name: String)

    /** Something happened. Parameters are small, stable keys — a provider name, a tab, a count. */
    fun event(name: String, params: Map<String, String> = emptyMap())

    object None : Analytics {
        override fun setEnabled(enabled: Boolean) = Unit
        override fun screen(name: String) = Unit
        override fun event(name: String, params: Map<String, String>) = Unit
    }
}

/**
 * The events the app records, as constants so a rename is one edit and a dashboard
 * can be built against a list rather than a grep.
 */
object AnalyticsEvents {
    const val ONBOARDING_COMPLETED = "onboarding_completed"
    const val SIGNED_IN = "signed_in"
    const val STREAK_DAY = "streak_day"
    const val COINS_EARNED = "coins_earned"
    const val SHOP_REDEEMED = "shop_redeemed"
    const val REFERRAL_SHARED = "referral_shared"
    const val PAYWALL_OPENED = "paywall_opened"
    const val CHECKOUT_STARTED = "checkout_started"
    const val PREMIUM_GRANTED = "premium_granted"
    const val SHARE_CREATED = "share_created"
    const val SHARE_REVOKED = "share_revoked"
    const val DEVICE_CONNECT_STARTED = "device_connect_started"
    const val DEVICE_CONNECTED = "device_connected"
    const val DEVICE_DISCONNECTED = "device_disconnected"
    const val FOOD_SCANNED = "food_scanned"
    const val AI_QUESTION = "ai_question"
    const val JOURNAL_SAVED = "journal_saved"
    const val WATER_ADDED = "water_added"
    const val SLEEP_ENTERED = "sleep_entered"
    const val LANGUAGE_CHANGED = "language_changed"
    const val THEME_CHANGED = "theme_changed"
}
