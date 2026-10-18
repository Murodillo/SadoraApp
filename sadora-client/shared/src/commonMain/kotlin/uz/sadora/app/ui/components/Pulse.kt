package uz.sadora.app.ui.components

import androidx.compose.runtime.Composable

/** The buzz a guided exercise gives at each phase, so she can follow it without looking. */
enum class Pulse {
    /** One long buzz: squeeze now. */
    Squeeze,
    /** Two short taps: let go. */
    Relax,
    /** A short flourish: the session is done. */
    Done,
}

/** Plays a [Pulse] on the phone's vibration motor; silent where there is none. */
@Composable
expect fun rememberPulse(): (Pulse) -> Unit

/** Keeps the screen from sleeping while this is on screen: a guided session must not stop halfway. */
@Composable
expect fun KeepScreenOn()
