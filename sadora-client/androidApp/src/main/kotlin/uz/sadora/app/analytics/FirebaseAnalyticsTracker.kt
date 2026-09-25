package uz.sadora.app.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import uz.sadora.app.data.Analytics

/**
 * Firebase Analytics, behind the shared [Analytics] interface.
 *
 * Collection is off in the manifest and stays off until [setEnabled] is called with her
 * consent; the ad-related consent types are denied outright, because the app shows no
 * ads and has no business feeding an advertising profile. A build without
 * google-services.json has no Firebase app, and then every call is a no-op rather than
 * a crash — the same rule push follows.
 */
class FirebaseAnalyticsTracker(context: Context) : Analytics {

    private val firebase: FirebaseAnalytics? =
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseAnalytics.getInstance(context)

    override fun setEnabled(enabled: Boolean) {
        val analytics = firebase ?: return
        analytics.setAnalyticsCollectionEnabled(enabled)
        analytics.setConsent(
            mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to
                    if (enabled) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED,
            ),
        )
    }

    override fun screen(name: String) {
        val analytics = firebase ?: return
        analytics.logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, name)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity")
            },
        )
    }

    override fun event(name: String, params: Map<String, String>) {
        val analytics = firebase ?: return
        analytics.logEvent(name, Bundle().apply { params.forEach { (key, value) -> putString(key, value) } })
    }
}
