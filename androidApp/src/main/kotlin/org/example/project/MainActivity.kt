package org.example.project

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.example.project.data.AndroidDeviceIdentity
import org.example.project.data.AndroidTokenStorage
import org.example.project.data.SadoraEnvironment
import org.example.project.data.SadoraGraph

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
                SadoraEnvironment.development()
            } else {
                SadoraEnvironment.Production
            },
            appVersion = BuildConfig.VERSION_NAME,
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
