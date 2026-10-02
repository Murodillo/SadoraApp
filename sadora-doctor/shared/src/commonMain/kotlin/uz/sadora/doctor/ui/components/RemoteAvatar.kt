package uz.sadora.doctor.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import uz.sadora.doctor.data.RemoteImages
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora

/**
 * The app's photo loader: bearer-authenticated fetches, cached by URL. Provided once by
 * the root; null in a preview, where every avatar is its initials.
 */
val LocalRemoteImages = staticCompositionLocalOf<RemoteImages<ImageBitmap>?> { null }

/**
 * A round avatar: the photo at [url] once it has loaded, [initials] until then and
 * whenever there is no photo or it cannot be had.
 *
 * [url] is a `photoUrl` exactly as the server sent it. A photo already in memory is on
 * the first frame; one that has to be fetched fades in over the initials.
 */
@Composable
fun RemoteAvatar(
    url: String?,
    initials: String,
    size: Dp,
    modifier: Modifier = Modifier,
    background: Color = Sadora.colors.primary.copy(alpha = 0.18f),
    contentColor: Color = Sadora.colors.primary,
    border: BorderStroke? = null,
    /** Said by a screen reader; null when the name beside the avatar already says it. */
    contentDescription: String? = null,
) {
    val images = LocalRemoteImages.current
    val photo by produceState(url?.let { images?.cached(it) }, url, images) {
        value = if (url == null || images == null) null else images.cached(url) ?: images.load(url)
    }
    Box(
        modifier
            .size(size)
            .clip(Radius.chip)
            .background(background)
            .then(if (border != null) Modifier.border(border, Radius.chip) else Modifier)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(photo, animationSpec = tween(Motion.Standard), label = "avatar") { bitmap ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(initials, style = Sadora.type.h3, color = contentColor)
                }
            }
        }
    }
}
