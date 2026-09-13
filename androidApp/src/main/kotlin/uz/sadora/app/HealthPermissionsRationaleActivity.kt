package uz.sadora.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/**
 * What Health Connect opens when she taps "privacy policy" on its permission screen.
 *
 * Health Connect refuses to show the permission sheet at all for an app that declares
 * no such screen, so this is required, not decoration. It hands over to the published
 * policy and closes — the policy is a web page, and a copy inside the app would drift.
 */
class HealthPermissionsRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))) }
        finish()
    }

    private companion object {
        const val PRIVACY_POLICY_URL = "https://murodillo.github.io/SadoraApp/privacy.html"
    }
}
