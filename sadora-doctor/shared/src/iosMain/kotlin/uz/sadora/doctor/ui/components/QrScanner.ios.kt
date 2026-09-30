package uz.sadora.doctor.ui.components

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
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSince1970
import platform.QuartzCore.CATransaction
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.posix.QOS_CLASS_USER_INITIATED

/**
 * `AVCaptureSession` with a metadata output that reads QR codes — the system's own
 * reader, so nothing is added to the app for it.
 *
 * Info.plist carries `NSCameraUsageDescription`; without it iOS terminates the app the
 * moment access is requested. The simulator has no capture device, which arrives here as
 * [CameraAccess.Missing] — pasting the link is how the Scan tab is exercised there.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun QrScanner(
    onAccess: (CameraAccess) -> Unit,
    onScanned: (String) -> Unit,
    modifier: Modifier,
) {
    val access = rememberUpdatedState(onAccess)
    val scanned = rememberUpdatedState(onScanned)

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

    val camera = remember { IosQrCamera { scanned.value(it) } }
    DisposableEffect(camera) {
        access.value(if (camera.start()) CameraAccess.Live else CameraAccess.Missing)
        onDispose { camera.stop() }
    }

    UIKitView(factory = { camera.view }, modifier = modifier)
}

@Composable
actual fun rememberOpenAppSettings(): () -> Unit = remember {
    val open: () -> Unit = {
        NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { url ->
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
        }
    }
    open
}

/** The session, its one input and the metadata output, and the view that shows it. */
@OptIn(ExperimentalForeignApi::class)
private class IosQrCamera(private val onText: (String) -> Unit) {
    private val session = AVCaptureSession()
    private val output = AVCaptureMetadataOutput()
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    val view: UIView = PreviewHost(previewLayer)

    private var lastText: String? = null
    private var lastAt = 0.0

    /** Held here because AVFoundation keeps its delegate weakly. Called on the main queue. */
    private val delegate = object : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
        override fun captureOutput(
            output: AVCaptureOutput,
            didOutputMetadataObjects: List<*>,
            fromConnection: AVCaptureConnection,
        ) {
            val text = didOutputMetadataObjects
                .filterIsInstance<AVMetadataMachineReadableCodeObject>()
                .firstNotNullOfOrNull { it.stringValue }
                ?: return
            val at = NSDate().timeIntervalSince1970 * 1000
            if (text == lastText && at - lastAt < RepeatScanMillis) return
            lastText = text
            lastAt = at
            onText(text)
        }
    }

    /** False when there is no camera to open — the simulator, or a device that refuses. */
    fun start(): Boolean {
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return false
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, error = null) ?: return false
        if (!session.canAddInput(input) || !session.canAddOutput(output)) return false

        session.beginConfiguration()
        session.addInput(input)
        session.addOutput(output)
        // The types can only be set once the output is on a session that offers them.
        output.setMetadataObjectsDelegate(delegate, queue = dispatch_get_main_queue())
        output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
        session.commitConfiguration()

        // startRunning blocks until the hardware is up; on the main queue that is a frozen page.
        dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0u)) {
            session.startRunning()
        }
        return true
    }

    fun stop() {
        output.setMetadataObjectsDelegate(null, queue = null)
        dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0u)) {
            if (session.running) session.stopRunning()
        }
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
