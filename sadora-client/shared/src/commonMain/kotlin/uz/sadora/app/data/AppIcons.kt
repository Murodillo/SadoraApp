package uz.sadora.app.data

import uz.sadora.contract.AppIconMood

/**
 * Changes the launcher icon to match the streak.
 *
 * Android does this by enabling one `activity-alias` at a time — there is no API for
 * retinting an icon — and the system rebuilds the home screen shortly afterwards. iOS
 * has `setAlternateIconName`, which needs the icons declared in the app's Info.plist;
 * that is not wired yet, so the iOS implementation is deliberately a no-op rather than
 * a call that would throw.
 *
 * Every implementation must be idempotent and cheap: it is called after every check-in,
 * and doing the work when nothing changed would flicker the home screen once a day for
 * no reason.
 */
interface AppIcons {
    fun apply(mood: AppIconMood)

    /** The stand-in for a platform that cannot change its icon, and for tests. */
    object None : AppIcons {
        override fun apply(mood: AppIconMood) = Unit
    }
}
