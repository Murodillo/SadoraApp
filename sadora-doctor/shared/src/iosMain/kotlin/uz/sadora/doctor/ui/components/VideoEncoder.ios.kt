package uz.sadora.doctor.ui.components

import kotlin.math.roundToInt
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitAll
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVLinearPCMBitDepthKey
import platform.AVFAudio.AVLinearPCMIsBigEndianKey
import platform.AVFAudio.AVLinearPCMIsFloatKey
import platform.AVFAudio.AVLinearPCMIsNonInterleaved
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFoundation.AVAsset
import platform.AVFoundation.AVAssetReader
import platform.AVFoundation.AVAssetReaderOutput
import platform.AVFoundation.AVAssetReaderStatusFailed
import platform.AVFoundation.AVAssetReaderTrackOutput
import platform.AVFoundation.AVAssetReaderVideoCompositionOutput
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVAssetWriter
import platform.AVFoundation.AVAssetWriterInput
import platform.AVFoundation.AVAssetWriterStatusCompleted
import platform.AVFoundation.AVAssetWriterStatusWriting
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMutableVideoComposition
import platform.AVFoundation.AVMutableVideoCompositionInstruction
import platform.AVFoundation.AVMutableVideoCompositionLayerInstruction
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.AVVideoAverageBitRateKey
import platform.AVFoundation.AVVideoCodecKey
import platform.AVFoundation.AVVideoCodecTypeH264
import platform.AVFoundation.AVVideoColorPrimariesKey
import platform.AVFoundation.AVVideoColorPrimaries_ITU_R_709_2
import platform.AVFoundation.AVVideoColorPropertiesKey
import platform.AVFoundation.AVVideoCompressionPropertiesKey
import platform.AVFoundation.AVVideoExpectedSourceFrameRateKey
import platform.AVFoundation.AVVideoHeightKey
import platform.AVFoundation.AVVideoMaxKeyFrameIntervalDurationKey
import platform.AVFoundation.AVVideoProfileLevelH264HighAutoLevel
import platform.AVFoundation.AVVideoProfileLevelKey
import platform.AVFoundation.AVVideoTransferFunctionKey
import platform.AVFoundation.AVVideoTransferFunction_ITU_R_709_2
import platform.AVFoundation.AVVideoWidthKey
import platform.AVFoundation.AVVideoYCbCrMatrixKey
import platform.AVFoundation.AVVideoYCbCrMatrix_ITU_R_709_2
import platform.AVFoundation.duration
import platform.AVFoundation.naturalSize
import platform.AVFoundation.nominalFrameRate
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.setColorPrimaries
import platform.AVFoundation.setColorTransferFunction
import platform.AVFoundation.setColorYCbCrMatrix
import platform.AVFoundation.tracksWithMediaType
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreGraphics.CGAffineTransformConcat
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.CoreGraphics.CGRectApplyAffineTransform
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMSampleBufferGetPresentationTimeStamp
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeRangeMake
import platform.CoreMedia.kCMTimeZero
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.darwin.dispatch_queue_create

/**
 * [source] re-encoded into [out] the way the Android app does it: H.264 at 720p and
 * 2.5 Mbit/s with AAC sound, so three minutes come to about 60 MB, well under the
 * server's 100 MB. AVAssetExportSession's presets take no bitrate and land near
 * 7–9 Mbit/s at 720p, which put a long video over the limit.
 *
 * The frames go through a video composition that turns them upright, scales them down and
 * converts a camera's HDR to ordinary colour, as an export would have. The camera's notes —
 * where, on which phone — are not copied. False when the file cannot be read or written;
 * a cancelled coroutine stops the work and leaves [out] to be deleted by the caller.
 */
@OptIn(ExperimentalForeignApi::class)
internal suspend fun encodeVideo(source: NSURL, out: NSURL, onProgress: (Float) -> Unit): Boolean {
    val asset = AVURLAsset(uRL = source, options = null)
    val track = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack ?: return false
    val reader = AVAssetReader(asset = asset, error = null)
    val writer = AVAssetWriter(uRL = out, fileType = AVFileTypeMPEG4, error = null)
    writer.metadata = emptyList<Any>()
    writer.shouldOptimizeForNetworkUse = true

    val fps = track.nominalFrameRate.roundToInt().takeIf { it in 1..120 } ?: 30
    val upright = uprightComposition(asset, track, fps)
    val frames = AVAssetReaderVideoCompositionOutput(
        videoTracks = listOf(track),
        videoSettings = mapOf(PixelFormatKey to NSNumber(unsignedInt = kCVPixelFormatType_32BGRA)),
    )
    frames.videoComposition = upright.composition
    val frameInput = AVAssetWriterInput(mediaType = AVMediaTypeVideo, outputSettings = frameSettings(upright.width, upright.height, fps))
    frameInput.expectsMediaDataInRealTime = false
    if (!reader.canAddOutput(frames) || !writer.canAddInput(frameInput)) return false
    reader.addOutput(frames)
    writer.addInput(frameInput)

    // A video without sound goes as it is; one whose sound the writer cannot take goes silent.
    val sound = (asset.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack)?.let { audio ->
        val samples = AVAssetReaderTrackOutput(track = audio, outputSettings = PcmSettings)
        val soundInput = AVAssetWriterInput(mediaType = AVMediaTypeAudio, outputSettings = AacSettings)
        soundInput.expectsMediaDataInRealTime = false
        if (reader.canAddOutput(samples) && writer.canAddInput(soundInput)) {
            reader.addOutput(samples)
            writer.addInput(soundInput)
            samples to soundInput
        } else {
            null
        }
    }

    if (!writer.startWriting()) return false
    if (!reader.startReading()) {
        writer.cancelWriting()
        return false
    }
    writer.startSessionAtSourceTime(kCMTimeZero.readValue())
    val seconds = CMTimeGetSeconds(asset.duration).takeIf { it > 0.0 } ?: 1.0
    // Written from the frames' queue only.
    var reported = 0f
    try {
        listOfNotNull(
            pump(frames, frameInput, writer, "frames") { at ->
                val fraction = (at / seconds).toFloat().coerceIn(0f, 0.99f)
                if (fraction - reported >= 0.01f) {
                    reported = fraction
                    onProgress(fraction)
                }
            },
            sound?.let { (samples, soundInput) -> pump(samples, soundInput, writer, "sound") },
        ).awaitAll()
        if (reader.status == AVAssetReaderStatusFailed || writer.status != AVAssetWriterStatusWriting) {
            writer.cancelWriting()
            return false
        }
        val finished = CompletableDeferred<Boolean>()
        writer.finishWritingWithCompletionHandler { finished.complete(writer.status == AVAssetWriterStatusCompleted) }
        return finished.await()
    } catch (cancelled: CancellationException) {
        reader.cancelReading()
        writer.cancelWriting()
        throw cancelled
    }
}

private class Upright(val width: Int, val height: Int, val composition: AVMutableVideoComposition)

/** The frames as a player shows them, fitted inside 1280×720 (either way up) on even sides, in Rec. 709. */
@OptIn(ExperimentalForeignApi::class)
private fun uprightComposition(asset: AVAsset, track: AVAssetTrack, fps: Int): Upright {
    val turn = track.preferredTransform
    val (naturalWidth, naturalHeight) = track.naturalSize.useContents { width to height }
    val box = CGRectApplyAffineTransform(CGRectMake(0.0, 0.0, naturalWidth, naturalHeight), turn)
    val (left, top) = box.useContents { origin.x to origin.y }
    val (shownWidth, shownHeight) = box.useContents { size.width to size.height }
    // Shifted back onto the frame: a turn written without its shift, as some apps write
    // it, would otherwise put the picture off the edge — players forgive that, a composition does not.
    val transform = CGAffineTransformConcat(turn, CGAffineTransformMakeTranslation(-left, -top))
    val scale = minOf(1.0, LongEdge / maxOf(shownWidth, shownHeight), ShortEdge / minOf(shownWidth, shownHeight))
    val width = even(shownWidth * scale)
    val height = even(shownHeight * scale)

    val layer = AVMutableVideoCompositionLayerInstruction.videoCompositionLayerInstructionWithAssetTrack(track)
    // Turned upright first, at full size, then scaled to exactly the even size.
    layer.setTransform(
        CGAffineTransformConcat(transform, CGAffineTransformMakeScale(width / shownWidth, height / shownHeight)),
        atTime = kCMTimeZero.readValue(),
    )
    val instruction = AVMutableVideoCompositionInstruction()
    instruction.setTimeRange(CMTimeRangeMake(kCMTimeZero.readValue(), asset.duration))
    instruction.setLayerInstructions(listOf(layer))

    val composition = AVMutableVideoComposition()
    composition.setRenderSize(CGSizeMake(width.toDouble(), height.toDouble()))
    composition.setFrameDuration(CMTimeMake(1, fps))
    composition.setInstructions(listOf(instruction))
    composition.setColorPrimaries(AVVideoColorPrimaries_ITU_R_709_2)
    composition.setColorTransferFunction(AVVideoTransferFunction_ITU_R_709_2)
    composition.setColorYCbCrMatrix(AVVideoYCbCrMatrix_ITU_R_709_2)
    return Upright(width, height, composition)
}

private fun frameSettings(width: Int, height: Int, fps: Int): Map<Any?, *> = mapOf(
    AVVideoCodecKey to AVVideoCodecTypeH264,
    AVVideoWidthKey to width,
    AVVideoHeightKey to height,
    AVVideoColorPropertiesKey to mapOf(
        AVVideoColorPrimariesKey to AVVideoColorPrimaries_ITU_R_709_2,
        AVVideoTransferFunctionKey to AVVideoTransferFunction_ITU_R_709_2,
        AVVideoYCbCrMatrixKey to AVVideoYCbCrMatrix_ITU_R_709_2,
    ),
    AVVideoCompressionPropertiesKey to mapOf(
        AVVideoAverageBitRateKey to VideoBitRate,
        AVVideoExpectedSourceFrameRateKey to fps,
        AVVideoMaxKeyFrameIntervalDurationKey to 2,
        AVVideoProfileLevelKey to AVVideoProfileLevelH264HighAutoLevel,
    ),
)

/**
 * Moves samples from [output] into [input] whenever the writer can take more, on a queue
 * of its own; completes once [output] has nothing left or the writer stopped.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun pump(
    output: AVAssetReaderOutput,
    input: AVAssetWriterInput,
    writer: AVAssetWriter,
    label: String,
    onSample: (Double) -> Unit = {},
): CompletableDeferred<Unit> {
    val done = CompletableDeferred<Unit>()
    input.requestMediaDataWhenReadyOnQueue(dispatch_queue_create("uz.sadora.encode.$label", null)) {
        while (!done.isCompleted && input.readyForMoreMediaData) {
            val more = autoreleasepool {
                val buffer = output.copyNextSampleBuffer()
                if (buffer == null || writer.status != AVAssetWriterStatusWriting) {
                    buffer?.let(::CFRelease)
                    false
                } else {
                    val appended = input.appendSampleBuffer(buffer)
                    onSample(CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(buffer)))
                    CFRelease(buffer)
                    appended
                }
            }
            if (!more) {
                if (writer.status == AVAssetWriterStatusWriting) input.markAsFinished()
                done.complete(Unit)
            }
        }
    }
    return done
}

private fun even(size: Double): Int = (size / 2).roundToInt().coerceAtLeast(1) * 2

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private val PixelFormatKey: String = CFBridgingRelease(CFRetain(kCVPixelBufferPixelFormatTypeKey)) as String

@OptIn(ExperimentalForeignApi::class)
private val PcmSettings: Map<Any?, *> = mapOf(
    AVFormatIDKey to NSNumber(unsignedInt = kAudioFormatLinearPCM),
    AVSampleRateKey to 44_100,
    AVNumberOfChannelsKey to 2,
    AVLinearPCMBitDepthKey to 16,
    AVLinearPCMIsFloatKey to false,
    AVLinearPCMIsBigEndianKey to false,
    AVLinearPCMIsNonInterleaved to false,
)

@OptIn(ExperimentalForeignApi::class)
private val AacSettings: Map<Any?, *> = mapOf(
    AVFormatIDKey to NSNumber(unsignedInt = kAudioFormatMPEG4AAC),
    AVSampleRateKey to 44_100,
    AVNumberOfChannelsKey to 2,
    AVEncoderBitRateKey to 128_000,
)

/** The same bitrate as the Android app's. */
private const val VideoBitRate = 2_500_000
private const val LongEdge = 1280.0
private const val ShortEdge = 720.0
