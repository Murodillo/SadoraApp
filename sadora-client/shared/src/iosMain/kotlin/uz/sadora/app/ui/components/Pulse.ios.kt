package uz.sadora.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import platform.UIKit.UIApplication
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

@Composable
actual fun rememberPulse(): (Pulse) -> Unit = remember {
    val heavy = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
    val light = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    val done = UINotificationFeedbackGenerator()
    val play: (Pulse) -> Unit = { pulse ->
        when (pulse) {
            Pulse.Squeeze -> heavy.impactOccurred()
            Pulse.Relax -> {
                light.impactOccurred()
                // The second tap a beat later, so "let go" feels unlike "squeeze".
                platform.Foundation.NSTimer.scheduledTimerWithTimeInterval(0.15, repeats = false) { _ ->
                    light.impactOccurred()
                }
            }
            Pulse.Done -> done.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
        }
    }
    play
}

@Composable
actual fun KeepScreenOn() {
    DisposableEffect(Unit) {
        UIApplication.sharedApplication.idleTimerDisabled = true
        onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}
