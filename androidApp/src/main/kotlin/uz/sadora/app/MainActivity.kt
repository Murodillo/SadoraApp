package uz.sadora.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import uz.sadora.app.data.AndroidAppIcons
import uz.sadora.app.data.AndroidDeviceIdentity
import uz.sadora.app.data.AndroidTokenStorage
import uz.sadora.app.data.SadoraEnvironment
import uz.sadora.app.data.SadoraGraph

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
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(graph, inviteCode = intent?.let(::inviteCodeOf))
        }
    }

    /**
     * The code out of an invite link.
     *
     * Two shapes reach here: `https://sadora.uz/r/K7M2QP` from a shared link, and
     * `sadora://invite/K7M2QP` from a channel that does not turn URLs into links. Both
     * end with the code as the last path segment, so both are read the same way.
     *
     * Anything that is not a plausible code is dropped rather than passed on — a link
     * with a tracking suffix on it must not become an invite code the server has to
     * refuse.
     */
    private fun inviteCodeOf(intent: Intent): String? {
        if (intent.action != Intent.ACTION_VIEW) return null
        val segment = intent.data?.lastPathSegment ?: intent.data?.host ?: return null
        val code = segment.uppercase().filter(Char::isLetterOrDigit)
        return code.takeIf { it.length in 4..16 }
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
