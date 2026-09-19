package uz.sadora.app.i18n

import uz.sadora.contract.HealthProvider

/**
 * The three sections added in the September 2026 pass: the QR code for a doctor, the
 * Premium tab, and the devices screen. Their own file for the same reason
 * [RewardStrings] has one — each is a whole section, and the main file is long.
 */

/** "Shifokorga ko'rsatish" — the QR code and the page behind it. */
interface ShareStrings {
    val title: String
    val subtitle: String
    /** The one-paragraph explanation above the button, before a link exists. */
    val intro: String
    val create: String
    val creating: String
    val regenerate: String
    val revoke: String
    val revoked: String
    val copyLink: String
    val linkCopied: String
    val shareLink: String
    /** The message the share sheet sends. */
    fun shareMessage(link: String): String
    val showToDoctor: String
    val validFor: String
    fun hours(count: Int): String
    fun days(count: Int): String
    fun expiresAt(at: String): String
    val expired: String
    fun viewedTimes(count: Int): String
    val neverViewed: String
    fun lastViewed(ago: String): String
    val includesTitle: String
    val includes: List<String>
    val excludesTitle: String
    val excludes: List<String>
    val privacyNote: String
    val offline: String
    val failed: String
}

/** The Premium tab: what it is, what it opens, and how to get it. */
interface PremiumStrings {
    val tab: String
    val title: String
    val activeTitle: String
    val activeBody: String
    val inactiveTitle: String
    val inactiveBody: String
    val benefitsTitle: String
    val benefitAiTitle: String
    val benefitAiBody: String
    val benefitScannerTitle: String
    val benefitScannerBody: String
    val benefitInsightsTitle: String
    val benefitInsightsBody: String
    val benefitLibraryTitle: String
    val benefitLibraryBody: String
    val benefitDevicesTitle: String
    val benefitDevicesBody: String
    val compareTitle: String
    val seePlans: String
    val manage: String
    /** "Gul bilan olish" — the second way in. */
    fun buyWithCoins(coinName: String): String
    /** "Oyiga 49 000 so'mdan" — the cheapest plan per month, under the main button. */
    fun fromPerMonth(sum: String): String
    /** "300 gul = 30 kun · sizda 1 250 gul" — what the coin route costs, and what she has. */
    fun coinsFor(cost: String, days: Int, balance: String): String
    val faqTitle: String
    val faq: List<Pair<String, String>>
    val freeStays: String
}

/** "Qurilmalar" — the providers, the connect flow, and what each one is for. */
interface DeviceStrings {
    val title: String
    val subtitle: String
    val connectedSection: String
    val availableSection: String
    val plannedSection: String
    fun provider(provider: HealthProvider): String
    fun providerTagline(provider: HealthProvider): String
    val connect: String
    val connecting: String
    val disconnect: String
    val disconnectConfirmTitle: String
    val disconnectConfirmBody: String
    val syncNow: String
    val syncing: String
    val synced: String
    fun lastSync(ago: String): String
    val neverSynced: String
    val statusActive: String
    val statusExpired: String
    val statusError: String
    val reconnect: String
    fun unavailable(reason: String): String
    val givesTitle: String
    val usedInTitle: String
    fun usedIn(provider: HealthProvider): List<String>
    val openBrowserNote: String
    val returnedOk: String
    val returnedError: String
    val noStepsNote: String
    val manualTitle: String
    val manualBody: String
    val note: String

    // ---- the phone's own store: HealthKit, Health Connect
    /** Under the connect button: what the sheet will ask and when data arrives. */
    fun onDeviceNote(provider: HealthProvider): String
    val installHealthConnect: String
    val healthConnectMissing: String
    fun deviceConnected(name: String): String
    val accessDenied: String
    fun periodsImported(count: Int): String
    /** HealthKit gives an app no way to change its own permissions, so she is told where. */
    val appleHealthManage: String
}
