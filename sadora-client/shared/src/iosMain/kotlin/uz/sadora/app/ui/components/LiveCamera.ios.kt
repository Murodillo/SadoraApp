package uz.sadora.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetPhoto
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.fileDataRepresentation
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.posix.QOS_CLASS_USER_INITIATED

/**
 * `AVCaptureSession` with its preview layer in a `UIView` the page hosts.
 *
 * Info.plist carries `NSCameraUsageDescription`; without it iOS terminates the app the
 * moment access is requested. The simulator has no capture device, which arrives here
 * as [CameraAccess.Missing] — the gallery is how the scanner is exercised there.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun LiveCamera(
    shutter: CameraShutter,
    onAccess: (CameraAccess) -> Unit,
    onCaptured: (CapturedPhoto) -> Unit,
    modifier: Modifier,
) {
    val access = rememberUpdatedState(onAccess)
    val captured = rememberUpdatedState(onCaptured)

    var granted by remember {
        mutableStateOf(AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusAuthorized)
    }
    LaunchedEffect(Unit) {
        if (granted) return@LaunchedEffect
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusNotDetermined -> {
                access.value(CameraAccess.Starting)
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { ok ->
                    // The answer arrives on an arbitrary queue; Compose state is the main thread's.
                    dispatch_async(dispatch_get_main_queue()) {
                        granted = ok
                        if (!ok) access.value(CameraAccess.Denied)
                    }
                }
            }

            else -> access.value(CameraAccess.Denied)
        }
    }

    if (!granted) {
        Box(modifier)
        return
    }

    val camera = remember { IosCamera() }
    DisposableEffect(camera) {
        if (camera.start()) {
            shutter.onFire = { camera.capture { photo -> captured.value(photo) } }
            access.value(CameraAccess.Live)
        } else {
            access.value(CameraAccess.Missing)
        }
        onDispose {
            shutter.onFire = null
            camera.stop()
        }
    }

    UIKitView(factory = { camera.view }, modifier = modifier)
}

@Composable
actual fun rememberOpenAppSettings(): () -> Unit = remember {
    {
        NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { url ->
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
        }
        Unit
    }
}

/** The session, its one input and one output, and the view that shows it. */
@OptIn(ExperimentalForeignApi::class)
private class IosCamera {
    private val session = AVCaptureSession()
    private val output = AVCapturePhotoOutput()
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    val view: UIView = PreviewHost(previewLayer)

    /** Held here because AVFoundation keeps its delegate weakly until the photo is processed. */
    private var inFlight: AVCapturePhotoCaptureDelegateProtocol? = null

    /** False when there is no camera to open — the simulator, or a device that refuses. */
    fun start(): Boolean {
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return false
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, error = null) ?: return false
        if (!session.canAddInput(input) || !session.canAddOutput(output)) return false

        session.beginConfiguration()
        session.sessionPreset = AVCaptureSessionPresetPhoto
        session.addInput(input)
        session.addOutput(output)
        session.commitConfiguration()

        // startRunning blocks until the hardware is up; on the main queue that is a frozen page.
        dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0u)) {
            session.startRunning()
        }
        return true
    }

    fun stop() {
        dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0u)) {
            if (session.running) session.stopRunning()
        }
    }

    fun capture(onPhoto: (CapturedPhoto) -> Unit) {
        // One frame per tap, as on Android: a second scan is a second charge to the allowance.
        if (inFlight != null || !session.running) return
        val delegate = object : NSObject(), AVCapturePhotoCaptureDelegateProtocol {
            override fun captureOutput(
                output: AVCapturePhotoOutput,
                didFinishProcessingPhoto: AVCapturePhoto,
                error: NSError?,
            ) {
                val photo = didFinishProcessingPhoto.fileDataRepresentation()
                    ?.let { UIImage(data = it) }
                    ?.encode()
                dispatch_async(dispatch_get_main_queue()) {
                    inFlight = null
                    if (photo != null) onPhoto(photo)
                }
            }
        }
        inFlight = delegate
        output.capturePhotoWithSettings(AVCapturePhotoSettings.photoSettings(), delegate = delegate)
    }
}

/** A view whose only job is keeping the preview layer the size Compose gives it. */
@OptIn(ExperimentalForeignApi::class)
private class PreviewHost(private val previewLayer: AVCaptureVideoPreviewLayer) : UIView(frame = CGRectZero.readValue()) {
    init {
        backgroundColor = UIColor.blackColor
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        // Without the transaction the layer animates to each new size, a quarter second behind the page.
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.frame = bounds
        CATransaction.commit()
    }
}
