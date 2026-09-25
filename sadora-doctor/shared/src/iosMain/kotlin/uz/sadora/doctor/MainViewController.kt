package uz.sadora.doctor

import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController
import uz.sadora.doctor.data.DoctorGraph
import uz.sadora.doctor.data.IosAppPrefs
import uz.sadora.doctor.data.IosDeviceIdentity
import uz.sadora.doctor.data.KeychainTokenStorage
import uz.sadora.doctor.data.SadoraEnvironment

/**
 * The iOS entry point builds the data layer and hands it to the shared UI, mirroring
 * what `MainActivity` does on Android. Called from `ContentView.swift`.
 */
fun MainViewController(): UIViewController = ComposeUIViewController { App(iosGraph) }

private val iosGraph: DoctorGraph by lazy {
    val storage = KeychainTokenStorage()
    DoctorGraph(
        tokenStorage = storage,
        device = IosDeviceIdentity(storage),
        environment = if (isDebugBuild()) debugEnvironment() else releaseEnvironment(),
        appVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String,
        prefs = IosAppPrefs(),
    )
}

/**
 * `SadoraDevHost` is empty unless the build was made with `SADORA_DEV_HOST=<host>`, so the
 * ordinary debug build points at the simulator's own loopback. A build installed on a
 * phone cannot reach that, and names the development machine instead — `macbook.local`,
 * or a whole `https://` URL for a tunnel. The same knob as sadora-client's.
 */
private fun debugEnvironment(): SadoraEnvironment =
    bundleString("SadoraDevHost")
        ?.let { SadoraEnvironment.development(it) }
        ?: SadoraEnvironment.development()

/**
 * `SadoraApiHost` is empty unless the archive was built with `SADORA_API_HOST=<host>`: a
 * TestFlight build aimed at staging. A host rather than a URL because an xcconfig reads
 * `//` as a comment.
 */
private fun releaseEnvironment(): SadoraEnvironment =
    bundleString("SadoraApiHost")
        ?.let { SadoraEnvironment(baseUrl = "https://$it") }
        ?: SadoraEnvironment.Production

/** An Info.plist string, or null when the build left the setting empty. */
private fun bundleString(key: String): String? =
    (NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String)?.takeIf { it.isNotBlank() }

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
private fun isDebugBuild(): Boolean = kotlin.native.Platform.isDebugBinary
