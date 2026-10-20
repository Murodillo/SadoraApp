package uz.sadora.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlin.coroutines.resume
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAssetExportPreset1280x720
import platform.AVFoundation.AVAssetExportPreset960x540
import platform.AVFoundation.AVAssetExportSession
import platform.AVFoundation.AVAssetExportSessionStatusCompleted
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.currentItem
import platform.AVFoundation.duration
import platform.AVFoundation.metadata
import platform.AVFoundation.naturalSize
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.tracksWithMediaType
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSData
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import uz.sadora.app.data.OutgoingFiles
import uz.sadora.app.data.PickRef
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUUID
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.dataTaskWithURL
import platform.Foundation.downloadTaskWithURL
import platform.Foundation.fileHandleForReadingAtPath
import platform.Foundation.readDataOfLength
import platform.Foundation.seekToFileOffset
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHPhotoLibrary
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerCameraCaptureMode
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerMediaURL
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerQualityTypeHigh
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.memcpy
import uz.sadora.app.data.MediaBytes
import uz.sadora.app.data.MediaPrepareException
import uz.sadora.app.data.PickedMedia
import uz.sadora.app.data.PrepareProblem
import uz.sadora.app.data.PreparedMedia
import uz.sadora.contract.Limits
import uz.sadora.contract.MediaKind

/**
 * `PHPickerViewController` for the gallery — it needs no library permission, it hands
 * back only what she chose — and `UIImagePickerController` for the camera, photo or video.
 *
 * The delegates are held by the returned object: UIKit's delegate references are weak, and
 * a delegate only the presentation pointed at would be collected before she finished.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberMediaPicker(onPicked: (List<PickedMedia>) -> Unit): MediaPicker {
    val callback = rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()

    return remember {
        object : MediaPicker {
            override val available = true
            override val canUseCamera = UIImagePickerController.isSourceTypeAvailable(
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera,
            )

            private val galleryDelegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
                override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                    picker.dismissViewControllerAnimated(true, null)
                    val results = didFinishPicking.filterIsInstance<PHPickerResult>()
                    if (results.isEmpty()) return
                    scope.launch {
                        val picked = results.mapNotNull { load(it) }
                        if (picked.isNotEmpty()) callback.value(picked)
                    }
                }
            }

            private val cameraDelegate = object : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
                override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
                    picker.dismissViewControllerAnimated(true, null)
                    val video = didFinishPickingMediaWithInfo[UIImagePickerControllerMediaURL] as? NSURL
                    val photo = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
                    scope.launch {
                        val picked = when {
                            video != null -> copyToTemp(video, "mov")?.let { IosPickedVideo.from(it) }
                            photo != null -> IosPickedPhoto(photo)
                            else -> null
                        }
                        picked?.let { callback.value(listOf(it)) }
                    }
                }

                override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                    picker.dismissViewControllerAnimated(true, null)
                }
            }

            override fun pickFromGallery(max: Int, videos: Boolean) {
                val configuration = PHPickerConfiguration()
                configuration.selectionLimit = max.toLong()
                configuration.filter = if (videos) {
                    PHPickerFilter.anyFilterMatchingSubfilters(listOf(PHPickerFilter.imagesFilter, PHPickerFilter.videosFilter))
                } else {
                    PHPickerFilter.imagesFilter
                }
                val picker = PHPickerViewController(configuration = configuration)
                picker.delegate = galleryDelegate
                present(picker)
            }

            override fun takePhoto() = camera(video = false)

            override fun recordVideo() = camera(video = true)

            private fun camera(video: Boolean) {
                if (!canUseCamera) return
                val picker = UIImagePickerController()
                picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                if (video) {
                    picker.mediaTypes = listOf(MovieType)
                    picker.cameraCaptureMode = UIImagePickerControllerCameraCaptureMode.UIImagePickerControllerCameraCaptureModeVideo
                    picker.videoMaximumDuration = Limits.MEDIA_VIDEO_MAX_SECONDS.toDouble()
                    picker.videoQuality = UIImagePickerControllerQualityTypeHigh
                }
                picker.delegate = cameraDelegate
                present(picker)
            }
        }
    }
}

private fun present(controller: UIViewController) {
    UIApplication.sharedApplication.keyWindow?.rootViewController
        ?.presentViewController(controller, animated = true, completion = null)
}

private const val MovieType = "public.movie"
private const val ImageType = "public.image"

/**
 * One gallery result, copied out of the picker's reach: a video's file is the picker's only
 * while its callback runs, so it is copied to the app's temporary folder first.
 */
@OptIn(ExperimentalForeignApi::class)
private suspend fun load(result: PHPickerResult): PickedMedia? {
    val provider = result.itemProvider
    return when {
        provider.hasItemConformingToTypeIdentifier(MovieType) -> suspendCancellableCoroutine { continuation ->
            provider.loadFileRepresentationForTypeIdentifier(MovieType) { url, _ ->
                val copy = url?.let { copyToTemp(it, it.pathExtension ?: "mov") }
                continuation.resume(copy?.let { IosPickedVideo.from(it) })
            }
        }
        provider.hasItemConformingToTypeIdentifier(ImageType) -> suspendCancellableCoroutine { continuation ->
            provider.loadDataRepresentationForTypeIdentifier(ImageType) { data, _ ->
                continuation.resume(data?.let { UIImage(data = it) }?.let { IosPickedPhoto(it) })
            }
        }
        else -> null
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun copyToTemp(url: NSURL, extension: String): NSURL? {
    val target = NSURL.fileURLWithPath(NSTemporaryDirectory() + "media-" + NSUUID().UUIDString + "." + extension)
    return if (NSFileManager.defaultManager.copyItemAtURL(url, target, null)) target else null
}

/** A photo, drawn upright at the size it is sent and written as a JPEG with nothing but pixels. */
@OptIn(ExperimentalForeignApi::class)
private class IosPickedPhoto(
    private val image: UIImage,
    /** Where [keep] wrote it down for a restart; deleted with the pick. */
    private var keptPath: String? = null,
) : PickedMedia {
    override val kind = MediaKind.PHOTO
    override val durationMs: Long? = null

    /** The picker gave the picture in memory; a restart needs it on disk, at a size still worth resizing. */
    override suspend fun keep(): PickRef? = withContext(Dispatchers.Default) {
        keptPath?.let { return@withContext PickRef(kind, it, null) }
        val (bytes, _) = image.jpeg(KeptPhotoEdge, 0.92) ?: return@withContext null
        val path = outgoingPath("jpg")
        if (!bytes.writeTo(path)) return@withContext null
        keptPath = path
        PickRef(kind, path, null)
    }

    override suspend fun preview(): ByteArray? = withContext(Dispatchers.Default) { image.jpeg(PreviewEdge, 0.8)?.first }

    override suspend fun prepare(onProgress: (Float) -> Unit): PreparedMedia = withContext(Dispatchers.Default) {
        val (bytes, size) = image.jpeg(Limits.MEDIA_PHOTO_PX, 0.85) ?: throw MediaPrepareException(PrepareProblem.Unreadable)
        onProgress(1f)
        PreparedMedia(MediaKind.PHOTO, "image/jpeg", MediaBytes.of(bytes), size.first, size.second, null, null)
    }

    override fun discard() {
        keptPath?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
    }
}

/**
 * A video from the gallery or the camera, in a temporary file of the app's. It is exported
 * as MP4 at 720p — at 540p when it runs longer than a minute and a half, which is what
 * keeps three minutes under the server's 100 MB: AVAssetExportSession offers presets, not
 * a bitrate.
 */
@OptIn(ExperimentalForeignApi::class)
private class IosPickedVideo(private var source: NSURL, override val durationMs: Long?) : PickedMedia {
    override val kind = MediaKind.VIDEO
    private var output: NSURL? = null

    /** The copy in the temporary folder, which iOS may empty while the app is closed, moves into the app's own. */
    override suspend fun keep(): PickRef? = withContext(Dispatchers.Default) {
        val path = source.path ?: return@withContext null
        if (!path.startsWith(outgoingDir())) {
            val target = outgoingPath(source.pathExtension ?: "mov")
            if (!NSFileManager.defaultManager.moveItemAtPath(path, target, null)) return@withContext null
            source = NSURL.fileURLWithPath(target)
        }
        PickRef(kind, source.path!!, durationMs)
    }

    override suspend fun preview(): ByteArray? = withContext(Dispatchers.Default) { poster(source, PreviewEdge)?.first }

    override suspend fun prepare(onProgress: (Float) -> Unit): PreparedMedia {
        if ((durationMs ?: 0) > Limits.MEDIA_VIDEO_MAX_MS) throw MediaPrepareException(PrepareProblem.TooLong)
        val asset = AVURLAsset(uRL = source, options = null)
        val preset = if ((durationMs ?: 0) > LongVideoMs) AVAssetExportPreset960x540 else AVAssetExportPreset1280x720
        val session = AVAssetExportSession(asset = asset, presetName = preset) ?: throw MediaPrepareException(PrepareProblem.Unreadable)
        // In the app's own folder, not the temporary one: a restart sends this very file on from where it stopped.
        val out = NSURL.fileURLWithPath(outgoingPath("mp4"))
        output = out
        session.outputURL = out
        session.outputFileType = AVFileTypeMPEG4
        session.shouldOptimizeForNetworkUse = true
        // An empty list, not none: none copies the original's notes — where it was filmed,
        // the phone — into the export. The server blanks whatever another client leaves in.
        session.metadata = emptyList<Any>()
        val done = CompletableDeferred<Boolean>()
        session.exportAsynchronouslyWithCompletionHandler { done.complete(session.status == AVAssetExportSessionStatusCompleted) }
        try {
            coroutineScope {
                val ticker = launch {
                    while (isActive) {
                        onProgress(session.progress)
                        delay(250)
                    }
                }
                try {
                    if (!done.await()) throw MediaPrepareException(PrepareProblem.Unreadable)
                } finally {
                    ticker.cancel()
                }
            }
        } catch (cancelled: CancellationException) {
            session.cancelExport()
            throw cancelled
        }
        onProgress(1f)
        return withContext(Dispatchers.Default) {
            val path = out.path ?: throw MediaPrepareException(PrepareProblem.Unreadable)
            val bytes = FileBytes(path)
            if (bytes.size > Limits.MEDIA_VIDEO_MAX_BYTES) throw MediaPrepareException(PrepareProblem.TooLarge)
            val (width, height) = shownSize(out) ?: (0 to 0)
            PreparedMedia(MediaKind.VIDEO, "video/mp4", bytes, width, height, durationOf(out) ?: durationMs, poster(out, PosterEdge)?.first, path)
        }
    }

    override fun discard() {
        NSFileManager.defaultManager.removeItemAtURL(source, null)
        output?.let { NSFileManager.defaultManager.removeItemAtURL(it, null) }
    }

    companion object {
        fun from(url: NSURL): IosPickedVideo = IosPickedVideo(url, durationOf(url))
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun durationOf(url: NSURL): Long? {
    val seconds = CMTimeGetSeconds(AVURLAsset(uRL = url, options = null).duration)
    return if (seconds.isNaN() || seconds <= 0.0) null else (seconds * 1000).toLong()
}

/** The size a player shows: a phone held upright stores its picture sideways and says so. */
@OptIn(ExperimentalForeignApi::class)
private fun shownSize(url: NSURL): Pair<Int, Int>? {
    val track = AVURLAsset(uRL = url, options = null).tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack ?: return null
    val (width, height) = track.naturalSize.useContents { width.toInt() to height.toInt() }
    val sideways = track.preferredTransform.useContents { a == 0.0 && b != 0.0 }
    return if (sideways) height to width else width to height
}

@OptIn(ExperimentalForeignApi::class)
private fun poster(url: NSURL, edge: Int): Pair<ByteArray, Pair<Int, Int>>? {
    val generator = AVAssetImageGenerator(asset = AVURLAsset(uRL = url, options = null))
    generator.appliesPreferredTrackTransform = true
    generator.maximumSize = CGSizeMake(edge.toDouble(), edge.toDouble())
    val frame = generator.copyCGImageAtTime(CMTimeMakeWithSeconds(0.5, 600), null, null) ?: return null
    return UIImage(cGImage = frame).jpeg(edge, 0.8)
}

/** Scaled to fit [maxEdge] and written as a JPEG; drawing applies the image's orientation. */
@OptIn(ExperimentalForeignApi::class)
private fun UIImage.jpeg(maxEdge: Int, quality: Double): Pair<ByteArray, Pair<Int, Int>>? {
    val (width, height) = size.useContents { width to height }
    val longest = maxOf(width, height)
    val scale = if (longest <= 0.0) 1.0 else minOf(1.0, maxEdge.toDouble() / longest)
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(targetWidth.toDouble(), targetHeight.toDouble()), true, 1.0)
    drawInRect(CGRectMake(0.0, 0.0, targetWidth.toDouble(), targetHeight.toDouble()))
    val drawn = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    val data = UIImageJPEGRepresentation(drawn ?: return null, quality) ?: return null
    return data.toByteArray() to (targetWidth to targetHeight)
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val out = ByteArray(length.toInt())
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

/** A prepared video, read a piece at a time from its file. */
@OptIn(ExperimentalForeignApi::class)
private class FileBytes(private val path: String) : MediaBytes {
    override val size: Long = (NSFileManager.defaultManager.attributesOfItemAtPath(path, null)?.get("NSFileSize") as? Number)?.toLong() ?: 0L

    override suspend fun read(offset: Long, length: Int): ByteArray = withContext(Dispatchers.Default) {
        val handle = NSFileHandle.fileHandleForReadingAtPath(path) ?: error("no file")
        try {
            handle.seekToFileOffset(offset.toULong())
            handle.readDataOfLength(length.toULong()).toByteArray()
        } finally {
            handle.closeFile()
        }
    }

    override fun release() {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }
}

/**
 * Where photos and videos she was sending wait while the app is closed: Application Support,
 * marked so it never goes into an iCloud backup of the phone.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun outgoingDir(): String {
    val base = NSFileManager.defaultManager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).first() as NSURL
    val dir = base.URLByAppendingPathComponent("outgoing")!!
    if (!NSFileManager.defaultManager.fileExistsAtPath(dir.path!!)) {
        NSFileManager.defaultManager.createDirectoryAtURL(dir, withIntermediateDirectories = true, attributes = null, error = null)
        dir.setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)
    }
    return dir.path!!
}

private fun outgoingPath(extension: String): String = outgoingDir() + "/" + NSUUID().UUIDString + "." + extension

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.writeTo(path: String): Boolean =
    usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }.writeToFile(path, atomically = true)

/** The unsent albums' list and files, for [uz.sadora.app.data.MediaUploader]; built by MainViewController. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosOutgoingFiles : OutgoingFiles {
    private val manifest get() = outgoingDir() + "/" + ManifestName

    override suspend fun readManifest(): String? = withContext(Dispatchers.Default) {
        NSString.stringWithContentsOfFile(manifest, NSUTF8StringEncoding, null)
    }

    override suspend fun writeManifest(text: String?) = withContext(Dispatchers.Default) {
        if (text == null) {
            NSFileManager.defaultManager.removeItemAtPath(manifest, null)
        } else {
            // Atomically: a close mid-write leaves the old list, not half a new one.
            NSString.create(string = text).writeToFile(manifest, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        }
        Unit
    }

    override suspend fun write(bytes: ByteArray): String = withContext(Dispatchers.Default) {
        outgoingPath("bin").also { bytes.writeTo(it) }
    }

    override fun open(path: String): MediaBytes? =
        here(path).takeIf { NSFileManager.defaultManager.fileExistsAtPath(it) }?.let(::FileBytes)

    override fun delete(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(here(path), null)
    }

    override fun restore(ref: PickRef): PickedMedia? {
        val path = here(ref.source)
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return null
        return when (ref.kind) {
            MediaKind.PHOTO -> UIImage.imageWithContentsOfFile(path)?.let { IosPickedPhoto(it, keptPath = path) }
            MediaKind.VIDEO -> IosPickedVideo(NSURL.fileURLWithPath(path), ref.durationMs)
        }
    }

    override suspend fun sweep(keep: Set<String>) = withContext(Dispatchers.Default) {
        val dir = outgoingDir()
        val kept = keep.map(::nameOf).toSet()
        NSFileManager.defaultManager.contentsOfDirectoryAtPath(dir, null).orEmpty()
            .filterIsInstance<String>()
            .filter { it != ManifestName && it !in kept }
            .forEach { NSFileManager.defaultManager.removeItemAtPath("$dir/$it", null) }
    }

    /**
     * Where a saved [path] is now. An update moves the app's whole container, so a path
     * written before it points nowhere; every file here sits straight in [outgoingDir]
     * under a name of its own, and the name is what is looked up.
     */
    private fun here(path: String): String = outgoingDir() + "/" + nameOf(path)

    private fun nameOf(path: String): String = path.substringAfterLast('/')
}

private const val ManifestName = "albums.json"
private const val KeptPhotoEdge = 4096
private const val PreviewEdge = 480
private const val PosterEdge = 640
private const val LongVideoMs = 90_000L

/**
 * `AVPlayerViewController` with the system's own controls, on a signed address. Sound plays
 * with the ringer switched off, as it does in every video app.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoPlayer(url: String, modifier: Modifier, onError: () -> Unit) {
    val failed = rememberUpdatedState(onError)
    val player = remember(url) { AVPlayer(uRL = NSURL(string = url)) }
    DisposableEffect(player) {
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
        player.play()
        onDispose { player.pause() }
    }
    LaunchedEffect(player) {
        while (isActive) {
            if (player.currentItem?.status == AVPlayerItemStatusFailed) {
                failed.value()
                break
            }
            delay(500)
        }
    }
    UIKitViewController(
        factory = {
            AVPlayerViewController().apply {
                this.player = player
                showsPlaybackControls = true
            }
        },
        modifier = modifier,
    )
}

@Composable
actual fun rememberMediaSaver(): MediaSaver = remember { IosMediaSaver() }

/**
 * Into Photos, with add-only access: the app can put a picture in and never read the
 * library back. A video is downloaded to a temporary file first, then handed over.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosMediaSaver : MediaSaver {

    override suspend fun savePhoto(jpeg: ByteArray): Boolean {
        if (!authorized()) return false
        val image = jpeg.usePinned { UIImage(data = NSData.create(bytes = it.addressOf(0), length = jpeg.size.toULong())) }
        return change { PHAssetChangeRequest.creationRequestForAssetFromImage(image) }
    }

    override suspend fun saveVideo(url: String): Boolean {
        if (!authorized()) return false
        val file = download(url) ?: return false
        return try {
            change { PHAssetChangeRequest.creationRequestForAssetFromVideoAtFileURL(file) }
        } finally {
            NSFileManager.defaultManager.removeItemAtURL(file, null)
        }
    }

    private suspend fun authorized(): Boolean = suspendCancellableCoroutine { continuation ->
        PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { status ->
            continuation.resume(status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited)
        }
    }

    private suspend fun change(block: () -> Unit): Boolean = suspendCancellableCoroutine { continuation ->
        PHPhotoLibrary.sharedPhotoLibrary().performChanges(block) { ok, _ -> continuation.resume(ok) }
    }

    private suspend fun download(url: String): NSURL? = suspendCancellableCoroutine { continuation ->
        val remote = NSURL(string = url)
        val task = NSURLSession.sharedSession.downloadTaskWithURL(remote) { location, _, _ ->
            // The download's own file is deleted when this returns: move it somewhere ours.
            val kept = location?.let { copyToTemp(it, "mp4") }
            continuation.resume(kept)
        }
        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    }
}
