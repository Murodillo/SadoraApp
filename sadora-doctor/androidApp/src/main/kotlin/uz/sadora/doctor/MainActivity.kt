package uz.sadora.doctor

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
import uz.sadora.doctor.data.AndroidAppPrefs
import uz.sadora.doctor.data.AndroidDeviceIdentity
import uz.sadora.doctor.data.AndroidTokenStorage
import uz.sadora.doctor.data.DoctorGraph
import uz.sadora.doctor.data.SadoraEnvironment
import uz.sadora.doctor.data.SessionState
import uz.sadora.doctor.nav.PushLinks
import uz.sadora.doctor.push.PushNotifications
import uz.sadora.doctor.push.PushRegistration

class MainActivity : ComponentActivity() {

    /**
     * The data layer is built here, where a `Context` is available, and handed to the
     * UI. Nothing in `commonMain` reaches for a singleton, so a preview can pass none.
     */
    private val graph: DoctorGraph by lazy {
        DoctorGraph(
            tokenStorage = AndroidTokenStorage(applicationContext),
            device = AndroidDeviceIdentity(applicationContext),
            environment = if (BuildConfig.DEBUG) {
                // DEV_HOST is empty unless the build passed -Psadora.devHost, so the
                // ordinary debug build still points at the emulator's host loopback.
                BuildConfig.DEV_HOST.takeIf { it.isNotEmpty() }
                    ?.let(SadoraEnvironment::development)
                    ?: SadoraEnvironment.development()
            } else {
                // API_URL is empty unless the build passed -Psadora.apiUrl.
                BuildConfig.API_URL.takeIf { it.isNotEmpty() }
                    ?.let { SadoraEnvironment(baseUrl = it.trimEnd('/')) }
                    ?: SadoraEnvironment.Production
            },
            appVersion = BuildConfig.VERSION_NAME,
            prefs = AndroidAppPrefs(applicationContext),
        )
    }

    private val push by lazy { PushRegistration(applicationContext, graph.repository) }

    /**
     * The answer is not acted on: a refusal only means patients' messages wait inside the
     * app, and Android itself stops showing the prompt after she has declined it twice.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        PushNotifications.ensureChannel(this)
        followSessionForPush()

        // A tapped push that started the app. Not again on a rotation's re-creation: the
        // intent still carries the link, and she has already been taken there.
        if (savedInstanceState == null) offerPushLink(intent)

        setContent {
            App(graph)
        }
    }

    /** A push tapped while the app was already running. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        offerPushLink(intent)
    }

    /**
     * The conversation the push is about, handed to the shared code, which opens it once
     * the tabs are there. The extra is taken off the intent so it is offered only once.
     */
    private fun offerPushLink(intent: Intent?) {
        val link = intent?.getStringExtra(PushNotifications.EXTRA_LINK) ?: return
        intent.removeExtra(PushNotifications.EXTRA_LINK)
        PushLinks.offer(link)
    }

    /**
     * Sends the push token whenever someone is signed in — a stored session at start, a
     * fresh sign-in, a switch of account — and asks for the notification permission once
     * she is signed in, which is when there is anyone to write to her.
     */
    private fun followSessionForPush() {
        val signedIn = graph.session.state.filterIsInstance<SessionState.SignedIn>()
        lifecycleScope.launch {
            // Every profile edit emits a new SignedIn; only a different user is news.
            signedIn.distinctUntilChangedBy { it.user.id }.collect { push.register() }
        }
        lifecycleScope.launch {
            signedIn.first()
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
