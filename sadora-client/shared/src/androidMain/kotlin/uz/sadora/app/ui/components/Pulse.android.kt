package uz.sadora.app.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

@Composable
actual fun rememberPulse(): (Pulse) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        val play: (Pulse) -> Unit = { pulse ->
            val effect = when (pulse) {
                Pulse.Squeeze -> VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE)
                Pulse.Relax -> VibrationEffect.createWaveform(longArrayOf(0, 70, 90, 70), -1)
                Pulse.Done -> VibrationEffect.createWaveform(longArrayOf(0, 120, 90, 120, 90, 300), -1)
            }
            runCatching { vibrator?.takeIf { it.hasVibrator() }?.vibrate(effect) }
        }
        play
    }
}

@Composable
actual fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
