package uz.sadora.app.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * CameraX: a [PreviewView] in the page and one frame captured to memory.
 *
 * The frame never touches storage — `OnImageCapturedCallback` hands over the JPEG bytes,
 * they are resized and sent, and nothing is left in the gallery of a meal she only wanted
 * counted. The capture asks for roughly 1280 pixels rather than the sensor's full frame:
 * the endpoint takes a 1024-pixel edge, and a smaller capture is a faster shutter.
 *
 * The preview is a `TextureView` (COMPATIBLE) because the page clips it to a rounded card,
 * and a `SurfaceView` is composited outside the view hierarchy and ignores the clip.
 */
@Composable
actual fun LiveCamera(
    shutter: CameraShutter,
    onAccess: (CameraAccess) -> Unit,
    onCaptured: (CapturedPhoto) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val access = rememberUpdatedState(onAccess)
    val captured = rememberUpdatedState(onCaptured)

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
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        var inFlight = false

        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(CaptureSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                    )
                    .build(),
            )
            .build()

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
                cameras.bindToLifecycle(lifecycleOwner, lens, preview, imageCapture)

                shutter.onFire = fire@{
                    // One frame per tap: a second tap while the first is being read would
                    // send two scans and spend two of the day's allowance.
                    if (inFlight) return@fire
                    inFlight = true
                    imageCapture.takePicture(
                        main,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val rotation = image.imageInfo.rotationDegrees
                                val jpeg = image.use { it.jpegBytes() }
                                scope.launch {
                                    val photo = withContext(Dispatchers.Default) {
                                        runCatching {
                                            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.encode(rotation)
                                        }.getOrNull()
                                    }
                                    inFlight = false
                                    if (photo != null) captured.value(photo)
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                inFlight = false
                            }
                        },
                    )
                }
                access.value(CameraAccess.Live)
            } catch (_: Exception) {
                // Another app holding the camera, or a device that reports one it cannot open.
                access.value(CameraAccess.Missing)
            }
        }, main)

        onDispose {
            disposed = true
            shutter.onFire = null
            provider?.unbindAll()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
actual fun rememberOpenAppSettings(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            Unit
        }
    }
}

private fun Context.hasCamera(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** The JPEG the capture produced, copied out before the proxy is closed. */
private fun ImageProxy.jpegBytes(): ByteArray {
    val buffer = planes[0].buffer
    return ByteArray(buffer.remaining()).also { buffer.get(it) }
}

/** Landscape, as the sensor sees it; CameraX matches it to the device's turn. */
private val CaptureSize = Size(1280, 960)
