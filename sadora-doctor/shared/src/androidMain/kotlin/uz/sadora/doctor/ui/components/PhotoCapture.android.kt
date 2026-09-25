package uz.sadora.doctor.ui.components

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
 * It needs no runtime permission: `PickVisualMedia` returns only the image she picked,
 * which is also why the doctor app declares no storage or camera permission at all.
 */
@Composable
actual fun rememberPhotoCapture(onCaptured: (CapturedPhoto) -> Unit): PhotoCapture {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onCaptured)

    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        context.decodeSampled(uri)?.let { (bitmap, rotation) -> bitmap.encode(rotation) }?.let { callback.value(it) }
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
 * Ultra is 800 MB as a bitmap, and the endpoint wants a 1024-pixel edge. `decodeStream`
 * also ignores EXIF, so a portrait photo reached the server lying on its side.
 */
private fun Context.decodeSampled(uri: Uri): Pair<Bitmap, Int>? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MaxEdge) sample *= 2

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
 * Scaled to fit [MaxEdge] and encoded as JPEG, because that is what the endpoint takes.
 *
 * [rotationDegrees] is what the photo's EXIF says it needs to stand upright.
 */
internal fun Bitmap.encode(rotationDegrees: Int = 0): CapturedPhoto? {
    val scale = minOf(1f, MaxEdge.toFloat() / maxOf(width, height).coerceAtLeast(1))
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

/** Long edge in pixels. Enough to read a diploma, and a fraction of the upload. */
private const val MaxEdge = 1024
private const val JpegQuality = 82
