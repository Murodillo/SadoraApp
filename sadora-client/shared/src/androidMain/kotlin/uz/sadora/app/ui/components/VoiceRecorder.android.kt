package uz.sadora.app.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.io.File

@Composable
actual fun rememberVoiceRecorder(): VoiceRecorder {
    val context = LocalContext.current
    val recorder = remember(context) { AndroidVoiceRecorder(context) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val callback = recorder.waiting ?: return@rememberLauncherForActivityResult
        recorder.waiting = null
        callback(granted && recorder.begin())
    }
    recorder.askPermission = { ask.launch(Manifest.permission.RECORD_AUDIO) }
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }
    return recorder
}

private class AndroidVoiceRecorder(private val context: Context) : VoiceRecorder {
    var waiting: ((Boolean) -> Unit)? = null
    var askPermission: () -> Unit = {}
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    override fun start(onStarted: (Boolean) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            onStarted(begin())
        } else {
            waiting = onStarted
            askPermission()
        }
    }

    fun begin(): Boolean = runCatching {
        val target = File(File(context.cacheDir, "voice").apply { mkdirs() }, "note.aac").apply { delete() }
        @Suppress("DEPRECATION")
        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        created.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            // ADTS: raw AAC frames with their own headers, which Gemini takes as audio/aac.
            setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioSamplingRate(16_000)
            setAudioEncodingBitRate(32_000)
            setOutputFile(target.path)
            prepare()
            start()
        }
        recorder = created
        file = target
        startedAt = System.currentTimeMillis()
        true
    }.getOrElse {
        release()
        false
    }

    override fun stop(): RecordedAudio? {
        val active = recorder ?: return null
        val stopped = runCatching { active.stop() }.isSuccess
        release()
        val target = file ?: return null
        file = null
        val bytes = if (stopped) runCatching { target.readBytes() }.getOrNull() else null
        target.delete()
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        return bytes?.takeIf { it.isNotEmpty() }?.let { RecordedAudio(it, "audio/aac", seconds) }
    }

    override fun cancel() {
        recorder?.let { runCatching { it.stop() } }
        release()
        file?.delete()
        file = null
    }

    override fun level(): Float = (recorder?.let { runCatching { it.maxAmplitude }.getOrDefault(0) } ?: 0) / 20_000f

    private fun release() {
        recorder?.let { runCatching { it.release() } }
        recorder = null
    }
}
