package uz.sadora.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVLinearPCMBitDepthKey
import platform.AVFAudio.AVLinearPCMIsBigEndianKey
import platform.AVFAudio.AVLinearPCMIsFloatKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy
import kotlin.math.pow

@Composable
actual fun rememberVoiceRecorder(): VoiceRecorder {
    val recorder = remember { IosVoiceRecorder() }
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }
    return recorder
}

@OptIn(ExperimentalForeignApi::class)
private class IosVoiceRecorder : VoiceRecorder {
    private var recorder: AVAudioRecorder? = null
    private val path = NSTemporaryDirectory() + "sadora-note.wav"
    private var startedAt = 0.0

    override fun start(onStarted: (Boolean) -> Unit) {
        AVAudioSession.sharedInstance().requestRecordPermission { granted ->
            dispatch_async(dispatch_get_main_queue()) { onStarted(granted && begin()) }
        }
    }

    private fun begin(): Boolean {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayAndRecord, error = null)
        session.setActive(true, error = null)
        // Plain 16 kHz mono WAV: a minute is under 2 MB, and Gemini reads it as audio/wav.
        val settings = mapOf<Any?, Any?>(
            AVFormatIDKey to kAudioFormatLinearPCM,
            AVSampleRateKey to 16_000.0,
            AVNumberOfChannelsKey to 1,
            AVLinearPCMBitDepthKey to 16,
            AVLinearPCMIsFloatKey to false,
            AVLinearPCMIsBigEndianKey to false,
        )
        val created = AVAudioRecorder(uRL = NSURL.fileURLWithPath(path), settings = settings, error = null)
        created.meteringEnabled = true
        if (!created.record()) return false
        recorder = created
        startedAt = NSDate().timeIntervalSince1970
        return true
    }

    override fun stop(): RecordedAudio? {
        val active = recorder ?: return null
        active.stop()
        recorder = null
        val data = NSData.dataWithContentsOfFile(path)
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        val seconds = (NSDate().timeIntervalSince1970 - startedAt).toInt()
        return data?.toByteArray()?.takeIf { it.isNotEmpty() }?.let { RecordedAudio(it, "audio/wav", seconds) }
    }

    override fun cancel() {
        recorder?.stop()
        recorder = null
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    override fun level(): Float {
        val active = recorder ?: return 0f
        active.updateMeters()
        // Decibels from −60 (silence) to 0: into 0–1.
        val db = active.averagePowerForChannel(0u).toDouble()
        return 10.0.pow(db / 20.0).toFloat().coerceIn(0f, 1f)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}
