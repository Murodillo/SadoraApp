package uz.sadora.doctor.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Where the scanner's camera stands. The Scan tab draws a different thing for each. */
enum class CameraAccess {
    /** The permission dialog is up, or the session is still starting. */
    Starting,

    /** Frames are arriving and being read. */
    Live,

    /** She said no. Pasting the link still works, and the page says so. */
    Denied,

    /** No camera on this device, or it would not open — the simulator, a tablet without one. */
    Missing,
}

/**
 * A live camera preview that reads QR codes, filling [modifier]'s bounds.
 *
 * The client app's in-page camera, with the shutter replaced by a reader: every frame is
 * looked at, and the text of a code found in one goes to [onScanned] on the main thread.
 * The same code held in front of the lens is reported again every so often rather than
 * on every frame; the page decides what a repeat means. It asks for the camera
 * permission itself, the first time it is composed, and reports through [onAccess].
 */
@Composable
expect fun QrScanner(
    onAccess: (CameraAccess) -> Unit,
    onScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
)

/** Opens this app's page in the system settings — the only way back from a refused permission. */
@Composable
expect fun rememberOpenAppSettings(): () -> Unit

/** How long the same code is kept quiet before it is reported again. */
internal const val RepeatScanMillis = 2_500L
