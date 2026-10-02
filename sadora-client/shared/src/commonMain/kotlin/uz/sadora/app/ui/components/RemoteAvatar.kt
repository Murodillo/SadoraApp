package uz.sadora.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora

/**
 * Where a face's bytes come from: the session's photo controller, which fetches them
 * with her token and keeps them in memory by URL.
 *
 * A composition local rather than a parameter because faces are drawn deep inside cards,
 * bylines and sheet headers, and threading a loader through every one of them would put
 * it in thirty signatures. Absent in previews and tests, where every face is initials.
 */
fun interface PhotoSource {
    suspend fun bytes(photoUrl: String): ByteArray?
}

val LocalPhotoSource = staticCompositionLocalOf<PhotoSource?> { null }

/**
 * The picture behind [photoUrl], decoded, or null while it loads, when there is none,
 * and when it would not load or decode.
 *
 * A new URL keeps the old picture up until the new one is ready, so changing her photo
 * does not flash her initial in between.
 */
@Composable
fun rememberRemotePhoto(photoUrl: String?): ImageBitmap? {
    val source = LocalPhotoSource.current
    val photo by produceState<ImageBitmap?>(null, photoUrl, source) {
        if (photoUrl.isNullOrBlank() || source == null) {
            value = null
            return@produceState
        }
        val bytes = source.bytes(photoUrl)
        value = bytes?.let { withContext(Dispatchers.Default) { decodePhoto(it) } }
    }
    return photo
}

/**
 * A round face: the photo at [url] when there is one and it loads, [fallback] otherwise —
 * while it loads too, so a slow connection shows initials rather than a hole.
 *
 * The default fallback is plain initials; the doctor's avatar and hers pass their own.
 */
@Composable
fun RemoteAvatar(
    url: String?,
    initials: String,
    size: Dp,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit = { InitialsFace(initials) },
) {
    // The URL is checked again, not only the picture: a photo just removed is still the
    // remembered picture for the frame before the loader hears the URL is gone.
    val photo = rememberRemotePhoto(url).takeIf { showsPhoto(url, decoded = it != null) }
    Box(modifier.size(size).clip(Radius.chip), contentAlignment = Alignment.Center) {
        if (photo != null) {
            Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            fallback()
        }
    }
}

/** Whether a face is a photo: only with a URL, and only once its bytes decoded. */
internal fun showsPhoto(url: String?, decoded: Boolean): Boolean = !url.isNullOrBlank() && decoded

@Composable
private fun InitialsFace(initials: String) {
    val c = Sadora.colors
    Box(Modifier.fillMaxSize().background(c.surface2), contentAlignment = Alignment.Center) {
        Text(initials, style = Sadora.type.h3, color = c.muted)
    }
}

@OptIn(ExperimentalResourceApi::class)
private fun decodePhoto(bytes: ByteArray): ImageBitmap? = runCatching { bytes.decodeToImageBitmap() }.getOrNull()
