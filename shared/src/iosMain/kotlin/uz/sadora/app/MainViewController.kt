package uz.sadora.app

import androidx.compose.ui.window.ComposeUIViewController
import uz.sadora.app.data.IosDeviceIdentity
import uz.sadora.app.data.KeychainTokenStorage
import uz.sadora.app.data.SadoraEnvironment
import uz.sadora.app.data.SadoraGraph
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController

/**
 * The iOS entry point builds the data layer and hands it to the shared UI, mirroring
 * what `MainActivity` does on Android.
 */
fun MainViewController(): UIViewController {
    IosPush.follow(iosGraph)
    return ComposeUIViewController {
        App(iosGraph)
    }
}

private val iosGraph: SadoraGraph by lazy {
    val storage = KeychainTokenStorage()
    SadoraGraph(
        tokenStorage = storage,
        device = IosDeviceIdentity(storage),
        environment = if (isDebugBuild()) SadoraEnvironment.development() else releaseEnvironment(),
        appVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String,
    )
}

/**
 * `SadoraApiHost` is empty unless the archive was built with `SADORA_API_HOST=<host>`:
 * a TestFlight build aimed at staging while the production domain does not exist yet.
 * It is a host rather than a URL because an xcconfig reads `//` as a comment.
 */
private fun releaseEnvironment(): SadoraEnvironment =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("SadoraApiHost") as? String)
        ?.takeIf { it.isNotBlank() }
        ?.let { SadoraEnvironment(baseUrl = "https://$it") }
        ?: SadoraEnvironment.Production

/**
 * Fully qualified: this module already has an `uz.sadora.app.Platform` interface,
 * and the unqualified name would resolve to that one.
 */
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
private fun isDebugBuild(): Boolean = kotlin.native.Platform.isDebugBinary
