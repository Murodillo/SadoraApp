package org.example.project

import androidx.compose.ui.window.ComposeUIViewController
import org.example.project.data.IosDeviceIdentity
import org.example.project.data.KeychainTokenStorage
import org.example.project.data.SadoraEnvironment
import org.example.project.data.SadoraGraph
import platform.Foundation.NSBundle

/**
 * The iOS entry point builds the data layer and hands it to the shared UI, mirroring
 * what `MainActivity` does on Android.
 */
fun MainViewController() = ComposeUIViewController {
    App(iosGraph)
}

private val iosGraph: SadoraGraph by lazy {
    val storage = KeychainTokenStorage()
    SadoraGraph(
        tokenStorage = storage,
        device = IosDeviceIdentity(storage),
        environment = if (isDebugBuild()) SadoraEnvironment.development() else SadoraEnvironment.Production,
        appVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String,
    )
}

/**
 * Fully qualified: this module already has an `org.example.project.Platform` interface,
 * and the unqualified name would resolve to that one.
 */
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
private fun isDebugBuild(): Boolean = kotlin.native.Platform.isDebugBinary
