package uz.sadora.doctor.ui.components

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import androidx.media3.ui.PlayerView
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.sadora.doctor.data.MediaBytes
import uz.sadora.doctor.data.MediaPrepareException
import uz.sadora.doctor.data.PickedMedia
import uz.sadora.doctor.data.PrepareProblem
import uz.sadora.doctor.data.PreparedMedia
import uz.sadora.contract.Limits
import uz.sadora.contract.MediaKind

/**
 * The system photo picker for the gallery — no permission, it returns only what she chose —
 * and the system camera app for a new photo or video, which writes into a cache file the
 * app hands it through its `<package>.photos` FileProvider. The camera permission is asked
 * on first use, because the app declares it for the Scan tab and Android then requires it
 * for the camera intent too. The camera app records the sound itself, so the app asks for
 * no microphone.
 */
@Composable
actual fun rememberMediaPicker(onPicked: (List<PickedMedia>) -> Unit): MediaPicker {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()
    val state = remember { CaptureState() }

    fun deliver(uris: List<Uri>, owned: File? = null) {
        if (uris.isEmpty()) return
        scope.launch {
            val picked = withContext(Dispatchers.IO) { uris.mapNotNull { AndroidPickedMedia.from(context, it, owned) } }
            if (picked.isNotEmpty()) callback.value(picked) else owned?.delete()
        }
    }

    val single = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { deliver(listOf(it)) }
    }
    val multiple = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(Limits.MESSAGE_MEDIA_MAX)) { uris ->
        deliver(uris)
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = state.pending.also { state.pending = null }
        if (taken && file != null && file.length() > 0) deliver(listOf(Uri.fromFile(file)), owned = file) else file?.delete()
    }
    val captureVideo = rememberLauncherForActivityResult(CaptureVideoUpTo(Limits.MEDIA_VIDEO_MAX_SECONDS)) { taken ->
        val file = state.pending.also { state.pending = null }
        if (taken && file != null && file.length() > 0) deliver(listOf(Uri.fromFile(file)), owned = file) else file?.delete()
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val next = state.afterPermission.also { state.afterPermission = null }
        if (granted) next?.invoke()
    }

    fun withCamera(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            state.afterPermission = action
            runCatching { cameraPermission.launch(Manifest.permission.CAMERA) }
        }
    }

    fun captureFile(extension: String): Pair<File, Uri> {
        val file = File(File(context.cacheDir, "capture").apply { mkdirs() }, "${UUID.randomUUID()}.$extension")
        return file to FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
    }

    return remember(single, multiple, takePicture, captureVideo) {
        object : MediaPicker {
            override val available = true
            override val canUseCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

            override fun pickFromGallery(max: Int, videos: Boolean) {
                val type = if (videos) ActivityResultContracts.PickVisualMedia.ImageAndVideo else ActivityResultContracts.PickVisualMedia.ImageOnly
                runCatching {
                    if (max <= 1) single.launch(PickVisualMediaRequest(type)) else multiple.launch(PickVisualMediaRequest(type))
                }
            }

            override fun takePhoto() = withCamera {
                val (file, uri) = captureFile("jpg")
                state.pending = file
                runCatching { takePicture.launch(uri) }.onFailure { file.delete() }
            }

            override fun recordVideo() = withCamera {
                val (file, uri) = captureFile("mp4")
                state.pending = file
                runCatching { captureVideo.launch(uri) }.onFailure { file.delete() }
            }
        }
    }
}

/** What survives between handing the camera a file and its answer. */
private class CaptureState {
    var pending: File? = null
    var afterPermission: (() -> Unit)? = null
}

/** The camera app, told to stop at the longest video a message carries. */
private class CaptureVideoUpTo(private val seconds: Int) : ActivityResultContracts.CaptureVideo() {
    override fun createIntent(context: Context, input: Uri): Intent =
        super.createIntent(context, input)
            .putExtra(MediaStore.EXTRA_DURATION_LIMIT, seconds)
            .putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
}

/**
 * A photo or video she picked. A photo is decoded no larger than it is sent and written
 * as a JPEG without its camera notes; a video is compressed to 720p H.264 at a bitrate
 * that keeps three minutes near 60 MB, and its poster frame taken from the result.
 */
private class AndroidPickedMedia(
    private val context: Context,
    private val uri: Uri,
    override val kind: MediaKind,
    override val durationMs: Long?,
    /** A file the app made (a camera capture), deleted with the pick. */
    private val owned: File?,
) : PickedMedia {
    private var output: File? = null

    override suspend fun preview(): ByteArray? = withContext(Dispatchers.IO) {
        when (kind) {
            MediaKind.PHOTO -> context.decodeSampled(uri, PreviewEdge)?.let { (bitmap, rotation) -> bitmap.jpeg(rotation, PreviewEdge, PreviewQuality)?.bytes }
            MediaKind.VIDEO -> frame(uri)?.jpeg(0, PreviewEdge, PreviewQuality)?.bytes
        }
    }

    override suspend fun prepare(onProgress: (Float) -> Unit): PreparedMedia = when (kind) {
        MediaKind.PHOTO -> withContext(Dispatchers.IO) {
            val (bitmap, rotation) = context.decodeSampled(uri, Limits.MEDIA_PHOTO_PX) ?: throw MediaPrepareException(PrepareProblem.Unreadable)
            val photo = bitmap.jpeg(rotation, Limits.MEDIA_PHOTO_PX, PhotoQuality) ?: throw MediaPrepareException(PrepareProblem.Unreadable)
            onProgress(1f)
            PreparedMedia(MediaKind.PHOTO, "image/jpeg", MediaBytes.of(photo.bytes), photo.width, photo.height, null, null)
        }
        MediaKind.VIDEO -> prepareVideo(onProgress)
    }

    @OptIn(UnstableApi::class)
    private suspend fun prepareVideo(onProgress: (Float) -> Unit): PreparedMedia {
        if ((durationMs ?: 0) > Limits.MEDIA_VIDEO_MAX_MS) throw MediaPrepareException(PrepareProblem.TooLong)
        val source = withContext(Dispatchers.IO) { size(uri) } ?: throw MediaPrepareException(PrepareProblem.Unreadable)
        val out = File(File(context.cacheDir, "media-out").apply { mkdirs() }, "${UUID.randomUUID()}.mp4")
        output = out
        withContext(Dispatchers.Main) {
            val done = CompletableDeferred<Unit>()
            val encoders = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(VideoBitrate).build())
                .build()
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoders)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        done.complete(Unit)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        done.completeExceptionally(MediaPrepareException(PrepareProblem.Unreadable))
                    }
                })
                .build()
            // Only ever smaller: a video already 720p or under keeps its size.
            val shortSide = minOf(source.first, source.second)
            val effects = if (shortSide > TargetShortSide) Effects(emptyList(), listOf(Presentation.createForShortSide(TargetShortSide))) else Effects.EMPTY
            transformer.start(EditedMediaItem.Builder(MediaItem.fromUri(uri)).setEffects(effects).build(), out.absolutePath)
            try {
                coroutineScope {
                    val ticker = launch {
                        val holder = ProgressHolder()
                        while (isActive) {
                            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                            delay(250)
                        }
                    }
                    try {
                        done.await()
                    } finally {
                        ticker.cancel()
                    }
                }
            } catch (cancelled: CancellationException) {
                transformer.cancel()
                throw cancelled
            }
        }
        onProgress(1f)
        return withContext(Dispatchers.IO) {
            if (out.length() > Limits.MEDIA_VIDEO_MAX_BYTES) throw MediaPrepareException(PrepareProblem.TooLarge)
            val outUri = Uri.fromFile(out)
            val (width, height) = size(outUri) ?: source
            val duration = metadata(outUri) { it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() } ?: durationMs
            val poster = frame(outUri)?.jpeg(0, PosterEdge, PosterQuality)?.bytes
            PreparedMedia(MediaKind.VIDEO, "video/mp4", FileBytes(out), width, height, duration, poster)
        }
    }

    /** The video's size as it is shown: a phone held upright stores its picture sideways. */
    private fun size(of: Uri): Pair<Int, Int>? = metadata(of) { retriever ->
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return@metadata null
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return@metadata null
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rotation % 180 == 90) height to width else width to height
    }

    private fun frame(of: Uri): Bitmap? = metadata(of) { it.getFrameAtTime(PosterAtMicros, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) }

    private fun <T> metadata(of: Uri, read: (MediaMetadataRetriever) -> T?): T? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, of)
            read(retriever)
        } finally {
            retriever.release()
        }
    }.getOrNull()

    override fun discard() {
        owned?.delete()
        output?.delete()
    }

    companion object {
        fun from(context: Context, uri: Uri, owned: File?): AndroidPickedMedia? {
            val type = context.contentResolver.getType(uri)
                ?: owned?.extension?.let { if (it == "mp4") "video/mp4" else "image/jpeg" }
                ?: return null
            return when {
                type.startsWith("video/") -> {
                    val duration = runCatching {
                        MediaMetadataRetriever().let { retriever ->
                            try {
                                retriever.setDataSource(context, uri)
                                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                            } finally {
                                retriever.release()
                            }
                        }
                    }.getOrNull()
                    AndroidPickedMedia(context, uri, MediaKind.VIDEO, duration, owned)
                }
                type.startsWith("image/") -> AndroidPickedMedia(context, uri, MediaKind.PHOTO, null, owned)
                else -> null
            }
        }

        private const val PreviewEdge = 480
        private const val PreviewQuality = 80
        private const val PhotoQuality = 85
        private const val PosterEdge = 640
        private const val PosterQuality = 80
        private const val PosterAtMicros = 500_000L
        private const val TargetShortSide = 720
        /** 2.5 Mbit/s: three minutes of 720p near 60 MB with its sound. */
        private const val VideoBitrate = 2_500_000
    }
}

/** A prepared video, read a piece at a time from its file and deleted when the album is done. */
private class FileBytes(private val file: File) : MediaBytes {
    override val size: Long = file.length()

    override suspend fun read(offset: Long, length: Int): ByteArray = withContext(Dispatchers.IO) {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            ByteArray(length).also { raf.readFully(it) }
        }
    }

    override fun release() {
        file.delete()
    }
}

private class Jpeg(val bytes: ByteArray, val width: Int, val height: Int)

/** Scaled to fit [maxEdge], turned [rotationDegrees], and written as a JPEG with nothing but pixels. */
private fun Bitmap.jpeg(rotationDegrees: Int, maxEdge: Int, quality: Int): Jpeg? {
    val scale = minOf(1f, maxEdge.toFloat() / maxOf(width, height).coerceAtLeast(1))
    val shaped = if (scale >= 1f && rotationDegrees == 0) this else Bitmap.createBitmap(
        this, 0, 0, width, height,
        Matrix().apply {
            postScale(scale, scale)
            postRotate(rotationDegrees.toFloat())
        },
        true,
    )
    val out = ByteArrayOutputStream()
    if (!shaped.compress(Bitmap.CompressFormat.JPEG, quality, out)) return null
    return Jpeg(out.toByteArray(), shaped.width, shaped.height)
}

/**
 * ExoPlayer with the platform's own controls, streaming the signed address by range. Built
 * per address and released when the page leaves the screen.
 */
@Composable
actual fun VideoPlayer(url: String, modifier: Modifier, onError: () -> Unit) {
    val context = LocalContext.current
    val failed = rememberUpdatedState(onError)
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = failed.value()
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        update = { it.player = player },
        modifier = modifier,
    )
}

@Composable
actual fun rememberMediaSaver(): MediaSaver {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidMediaSaver(context) }
}

/**
 * Into the gallery through MediaStore, under Pictures/SADORA and Movies/SADORA. From
 * Android 10 that needs no permission; older phones answer false, and the viewer says
 * it could not save.
 */
private class AndroidMediaSaver(private val context: Context) : MediaSaver {

    override suspend fun savePhoto(jpeg: ByteArray): Boolean = withContext(Dispatchers.IO) {
        insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/jpeg", "SADORA_${System.currentTimeMillis()}.jpg", "Pictures/SADORA") {
            it.write(jpeg)
        }
    }

    override suspend fun saveVideo(url: String): Boolean = withContext(Dispatchers.IO) {
        insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/mp4", "SADORA_${System.currentTimeMillis()}.mp4", "Movies/SADORA") { out ->
            URL(url).openStream().use { it.copyTo(out) }
        }
    }

    private fun insert(collection: Uri, mime: String, name: String, folder: String, write: (java.io.OutputStream) -> Unit): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val item = resolver.insert(collection, values) ?: return false
        return runCatching {
            resolver.openOutputStream(item)?.use(write) ?: error("no stream")
            resolver.update(item, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            true
        }.getOrElse {
            resolver.delete(item, null, null)
            false
        }
    }
}
