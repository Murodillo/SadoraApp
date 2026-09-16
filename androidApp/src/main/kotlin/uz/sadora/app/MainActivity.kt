package uz.sadora.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uz.sadora.app.data.AndroidAppIcons
import uz.sadora.app.data.AndroidDeviceIdentity
import uz.sadora.app.data.AndroidTokenStorage
import uz.sadora.app.data.SadoraEnvironment
import uz.sadora.app.data.SadoraGraph
import uz.sadora.app.data.SessionState
import uz.sadora.app.data.health.AndroidHealthSyncPrefs
import uz.sadora.app.data.health.HealthConnectPlatform
import uz.sadora.app.push.PushNotifications
import uz.sadora.app.push.PushRegistration
import uz.sadora.app.analytics.FirebaseAnalyticsTracker
import uz.sadora.app.nav.AppLinks

class MainActivity : ComponentActivity() {

    /**
     * The data layer is built here, where a `Context` is available, and handed to the
     * UI. Nothing in `commonMain` reaches for a singleton, so a test or a preview can
     * pass a different graph — or none at all.
     */
    private val graph: SadoraGraph by lazy {
        SadoraGraph(
            tokenStorage = AndroidTokenStorage(applicationContext),
            device = AndroidDeviceIdentity(applicationContext),
            environment = if (BuildConfig.DEBUG) {
                // DEV_HOST is empty unless the build passed -Psadora.devHost, so the
                // ordinary debug build still points at the emulator's host loopback.
                BuildConfig.DEV_HOST.takeIf { it.isNotEmpty() }
                    ?.let(SadoraEnvironment::development)
                    ?: SadoraEnvironment.development()
            } else {
                // API_URL is empty unless the build passed -Psadora.apiUrl: a store test
                // build aimed at staging while the production domain does not exist yet.
                BuildConfig.API_URL.takeIf { it.isNotEmpty() }
                    ?.let { SadoraEnvironment(baseUrl = it.trimEnd('/')) }
                    ?: SadoraEnvironment.Production
            },
            appVersion = BuildConfig.VERSION_NAME,
            // The launcher icon follows the streak, which needs a Context to switch the
            // manifest's aliases — so it is built here alongside the token storage.
            icons = AndroidAppIcons(applicationContext),
            analytics = FirebaseAnalyticsTracker(applicationContext),
            healthPlatform = HealthConnectPlatform(applicationContext),
            healthPrefs = AndroidHealthSyncPrefs(applicationContext),
        )
    }

    private val push by lazy { PushRegistration(applicationContext, graph.repository) }

    /**
     * The answer is not acted on: a refusal only means reminders stay inside the app, and
     * Android itself stops showing the prompt after she has declined it twice.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidAppIcons.watch(application)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        PushNotifications.ensureChannel(this)
        followSessionForPush()

        // The link the app was opened with, if any. Offered to the shared code rather
        // than parsed here, so an invite and a wearable return are read by one rule.
        intent?.dataString?.let(AppLinks::offer)

        setContent {
            App(graph)
        }
    }

    /** A link tapped while the app is already open — the browser sending her back. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) intent.dataString?.let(AppLinks::offer)
    }

    /**
     * Sends the push token whenever someone is signed in — a stored session at start, a
     * fresh sign-in, a switch of account — and asks for the notification permission once
     * she is past onboarding, where the reminders it exists for are.
     */
    private fun followSessionForPush() {
        val signedIn = graph.session.state.filterIsInstance<SessionState.SignedIn>()
        lifecycleScope.launch {
            // Every profile edit emits a new SignedIn; only a different user is news.
            signedIn.distinctUntilChangedBy { it.user.id }.collect { push.register() }
        }
        lifecycleScope.launch {
            signedIn.first { !it.needsOnboarding }
            askForNotifications()
        }
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onDestroy() {
        graph.close()
        super.onDestroy()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
