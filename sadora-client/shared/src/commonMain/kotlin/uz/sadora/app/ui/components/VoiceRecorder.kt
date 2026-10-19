package uz.sadora.app.ui.components

import androidx.compose.runtime.Composable

class RecordedAudio(val bytes: ByteArray, val mimeType: String, val seconds: Int)

/**
 * The microphone, for a short note. The file lives in the app's cache only while it records
 * and is deleted the moment [stop] hands its bytes over.
 */
interface VoiceRecorder {
    /** Asks for the microphone when needed; [onStarted] says whether recording began. */
    fun start(onStarted: (Boolean) -> Unit)
    fun stop(): RecordedAudio?
    fun cancel()
    /** 0–1, how loud right now: for the circle to breathe with her voice. */
    fun level(): Float
}

@Composable
expect fun rememberVoiceRecorder(): VoiceRecorder
