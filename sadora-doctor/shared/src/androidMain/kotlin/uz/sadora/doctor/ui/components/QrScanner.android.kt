package uz.sadora.doctor.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/**
 * CameraX: a [PreviewView] in the page, and an analysis stream whose frames ZXing reads.
 *
 * Only the luminance plane is looked at — a QR code is black and white — and only the
 * latest frame: while one is being read the camera drops the rest rather than queueing
 * them, so the reader never falls behind the lens. Nothing is stored.
 *
 * The preview is a `TextureView` (COMPATIBLE) because the page clips it to a rounded
 * card, and a `SurfaceView` is composited outside the view hierarchy and ignores the clip.
 */
@Composable
actual fun QrScanner(
    onAccess: (CameraAccess) -> Unit,
    onScanned: (String) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val access = rememberUpdatedState(onAccess)
    val scanned = rememberUpdatedState(onScanned)

    var granted by remember { mutableStateOf(context.hasCamera()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) access.value(CameraAccess.Denied)
    }
    LaunchedEffect(Unit) {
        if (!granted) {
            access.value(CameraAccess.Starting)
            ask.launch(Manifest.permission.CAMERA)
        }
    }
    // Back from the settings page with the switch turned on: the preview starts by itself.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!granted && context.hasCamera()) granted = true
    }

    if (!granted) {
        Box(modifier)
        return
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val main = ContextCompat.getMainExecutor(context)
        val reading = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false

        val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }
        var lastText: String? = null
        var lastAt = 0L

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(AnalysisSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                    )
                    .build(),
            )
            .build()
        analysis.setAnalyzer(reading) { image ->
            val text = image.use { reader.readQr(it) } ?: return@setAnalyzer
            val at = SystemClock.elapsedRealtime()
            if (text == lastText && at - lastAt < RepeatScanMillis) return@setAnalyzer
            lastText = text
            lastAt = at
            main.execute { if (!disposed) scanned.value(text) }
        }

        future.addListener({
            if (disposed) return@addListener
            try {
                val cameras = future.get().also { provider = it }
                val lens = listOf(CameraSelector.DEFAULT_BACK_CAMERA, CameraSelector.DEFAULT_FRONT_CAMERA)
                    .firstOrNull { cameras.hasCamera(it) }
                if (lens == null) {
                    access.value(CameraAccess.Missing)
                    return@addListener
                }
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                cameras.unbindAll()
                cameras.bindToLifecycle(lifecycleOwner, lens, preview, analysis)
                access.value(CameraAccess.Live)
            } catch (_: Exception) {
                // Another app holding the camera, or a device that reports one it cannot open.
                access.value(CameraAccess.Missing)
            }
        }, main)

        onDispose {
            disposed = true
            analysis.clearAnalyzer()
            provider?.unbindAll()
            reading.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
actual fun rememberOpenAppSettings(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        val open: () -> Unit = {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        open
    }
}

private fun Context.hasCamera(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/**
 * The text of the QR code in [image], or null when there is none. The Y plane is handed
 * over as it lies in memory, row stride and all; the crop is the picture's real width.
 */
private fun MultiFormatReader.readQr(image: ImageProxy): String? {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val rowStride = plane.rowStride
    val rows = bytes.size / rowStride + if (bytes.size % rowStride >= image.width) 1 else 0
    if (rows < image.height) return null
    val source = PlanarYUVLuminanceSource(bytes, rowStride, image.height, 0, 0, image.width, image.height, false)
    return try {
        decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: ReaderException) {
        null
    } finally {
        reset()
    }
}

/** Enough pixels for a phone-screen QR code held at arm's length, few enough to read fast. */
private val AnalysisSize = Size(1280, 720)
