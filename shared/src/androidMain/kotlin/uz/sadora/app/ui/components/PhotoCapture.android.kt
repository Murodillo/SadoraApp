package uz.sadora.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream

/**
 * The camera's own thumbnail preview and the system photo picker.
 *
 * `TakePicturePreview` hands back a bitmap directly, which avoids a FileProvider and a
 * writable path for one throwaway frame. It is a small image — that is the point: the
 * model only needs to see what the food is, and a 12-megapixel frame is several
 * megabytes of upload for no extra accuracy.
 *
 * Neither needs a runtime permission, and the manifest deliberately declares none:
 * ACTION_IMAGE_CAPTURE is served by the camera app, and `PickVisualMedia` returns only
 * the image the user picked. See the note in AndroidManifest.xml — declaring CAMERA is
 * what *creates* a permission problem here rather than solving one.
 */
@Composable
actual fun rememberPhotoCapture(onCaptured: (CapturedPhoto) -> Unit): PhotoCapture {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onCaptured)

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.encode()?.let { callback.value(it) }
    }
    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val bitmap = runCatching {
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
        bitmap?.encode()?.let { callback.value(it) }
    }

    return remember(camera, gallery) {
        object : PhotoCapture {
            override val available = true

            // A device with no camera app at all throws rather than returning nothing,
            // and a scanner that crashes is worse than one that quietly does nothing.
            override fun takePhoto() {
                runCatching { camera.launch(null) }
            }

            override fun pickFromGallery() {
                runCatching {
                    gallery.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
            }
        }
    }
}

/** Scaled to fit [MaxEdge] and encoded as JPEG, because that is what the endpoint takes. */
private fun Bitmap.encode(): CapturedPhoto? {
    val scale = MaxEdge.toFloat() / maxOf(width, height).coerceAtLeast(1)
    val scaled = if (scale >= 1f) this else Bitmap.createBitmap(
        this,
        0,
        0,
        width,
        height,
        Matrix().apply { postScale(scale, scale) },
        true,
    )
    val bytes = ByteArrayOutputStream()
    if (!scaled.compress(Bitmap.CompressFormat.JPEG, JpegQuality, bytes)) return null
    return CapturedPhoto(
        base64 = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP),
        mimeType = "image/jpeg",
    )
}

/** Long edge in pixels. Plenty for recognising a plate, and a fraction of the upload. */
private const val MaxEdge = 1024
private const val JpegQuality = 82
