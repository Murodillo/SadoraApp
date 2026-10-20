package uz.sadora.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
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
 * The system photo picker.
 *
 * It needs no runtime permission: `PickVisualMedia` returns only the image the user
 * picked. The camera is not here — it is [LiveCamera], which draws its preview in the page.
 */
@Composable
actual fun rememberPhotoCapture(maxEdge: Int, onCaptured: (CapturedPhoto) -> Unit): PhotoCapture {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onCaptured)

    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        context.decodeSampled(uri, maxEdge)?.let { (bitmap, rotation) -> bitmap.encode(rotation, maxEdge) }?.let { callback.value(it) }
    }

    return remember(gallery) {
        object : PhotoCapture {
            override val available = true

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

/**
 * The picked image, decoded no larger than it needs to be, with the turn its EXIF asks for.
 *
 * Decoding at full size first is what this avoids: a 200-megapixel frame from an S23
 * Ultra is 800 MB as a bitmap, and the endpoint wants a [maxEdge]-pixel edge. `decodeStream`
 * also ignores EXIF, so a portrait photo reached the model lying on its side.
 */
internal fun Context.decodeSampled(uri: Uri, maxEdge: Int): Pair<Bitmap, Int>? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2

    val bitmap = contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val orientation = contentResolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }
    val rotation = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
    bitmap to rotation
}.getOrNull()

/**
 * Scaled to fit [maxEdge] and encoded as JPEG, because that is what the endpoint takes.
 *
 * [rotationDegrees] is what the sensor says the frame needs to stand upright; a camera
 * frame arrives sideways and a gallery pick does not.
 */
internal fun Bitmap.encode(rotationDegrees: Int = 0, maxEdge: Int = PhotoEdge.Default): CapturedPhoto? {
    val scale = minOf(1f, maxEdge.toFloat() / maxOf(width, height).coerceAtLeast(1))
    val scaled = if (scale >= 1f && rotationDegrees == 0) this else Bitmap.createBitmap(
        this,
        0,
        0,
        width,
        height,
        Matrix().apply {
            postScale(scale, scale)
            postRotate(rotationDegrees.toFloat())
        },
        true,
    )
    val bytes = ByteArrayOutputStream()
    if (!scaled.compress(Bitmap.CompressFormat.JPEG, JpegQuality, bytes)) return null
    return CapturedPhoto(
        base64 = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP),
        mimeType = "image/jpeg",
    )
}

private const val JpegQuality = 82
