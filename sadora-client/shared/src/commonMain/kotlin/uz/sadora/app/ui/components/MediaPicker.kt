package uz.sadora.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import uz.sadora.app.data.PickedMedia

/**
 * The system's ways into photos and videos for a message: the gallery, and the camera
 * for a photo or a video. What comes back is picked, not prepared — resizing a photo and
 * compressing a video happen behind the bubble once she sends ([PickedMedia.prepare]).
 *
 * The gallery needs no library permission on either platform: the system picker returns
 * only what she chose. The camera asks for the camera on first use.
 */
interface MediaPicker {
    val available: Boolean
    val canUseCamera: Boolean

    /** Up to [max] items; videos too when [videos]. */
    fun pickFromGallery(max: Int, videos: Boolean)
    fun takePhoto()

    /** The system camera, stopped at the server's longest video. */
    fun recordVideo()
}

@Composable
expect fun rememberMediaPicker(onPicked: (List<PickedMedia>) -> Unit): MediaPicker

/**
 * A video from a signed address, with the platform's own controls: play, pause, seek,
 * sound. The address carries its own permission, so the player needs no header.
 */
@Composable
expect fun VideoPlayer(url: String, modifier: Modifier = Modifier, onError: () -> Unit = {})

/** "Telefonga saqlash": into the phone's gallery, only when she asks. */
interface MediaSaver {
    suspend fun savePhoto(jpeg: ByteArray): Boolean

    /** Downloads the video from its signed address straight into the gallery. */
    suspend fun saveVideo(url: String): Boolean
}

@Composable
expect fun rememberMediaSaver(): MediaSaver
