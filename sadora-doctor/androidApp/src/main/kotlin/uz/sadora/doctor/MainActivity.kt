package uz.sadora.doctor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import uz.sadora.doctor.data.AndroidAppPrefs
import uz.sadora.doctor.data.AndroidDeviceIdentity
import uz.sadora.doctor.data.AndroidTokenStorage
import uz.sadora.doctor.data.DoctorGraph
import uz.sadora.doctor.data.SadoraEnvironment

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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(graph)
        }
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
