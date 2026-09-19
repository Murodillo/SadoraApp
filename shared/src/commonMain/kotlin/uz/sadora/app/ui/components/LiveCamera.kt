package uz.sadora.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Where the page's own camera stands. The flow draws a different thing for each. */
enum class CameraAccess {
    /** The permission dialog is up, or the session is still starting. */
    Starting,

    /** Frames are arriving; the shutter works. */
    Live,

    /** She said no. The gallery and manual entry still work, and the page says so. */
    Denied,

    /** No camera on this device, or it would not open — the simulator, a tablet without one. */
    Missing,
}

/**
 * The shutter of a [LiveCamera], held by the page that draws the button.
 *
 * The preview is a platform view and the button is shared Compose, so the button cannot
 * reach into the view; it fires this instead, and the platform side binds [onFire] while
 * its session is live. A tap before that, or after the view is gone, does nothing.
 */
class CameraShutter {
    internal var onFire: (() -> Unit)? = null

    fun fire() {
        onFire?.invoke()
    }
}

/**
 * A live camera preview drawn inside the page, filling [modifier]'s bounds.
 *
 * This replaced handing the shot to the system camera app: that left the page for another
 * app's screen and came back with a thumbnail, which is a different product from pointing
 * the phone at a plate and tapping once. It asks for the camera permission itself, the
 * first time it is composed, and reports the answer through [onAccess].
 *
 * [onCaptured] receives the frame already resized and JPEG-encoded, exactly as
 * [rememberPhotoCapture] delivers a gallery pick, so both roads join before the upload.
 */
@Composable
expect fun LiveCamera(
    shutter: CameraShutter,
    onAccess: (CameraAccess) -> Unit,
    onCaptured: (CapturedPhoto) -> Unit,
    modifier: Modifier = Modifier,
)

/** Opens this app's page in the system settings — the only way back from a refused permission. */
@Composable
expect fun rememberOpenAppSettings(): () -> Unit
