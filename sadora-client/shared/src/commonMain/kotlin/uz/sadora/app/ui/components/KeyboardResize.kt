package uz.sadora.app.ui.components

import androidx.compose.runtime.Composable

/**
 * While composed, the keyboard shrinks the window instead of sliding the whole page up.
 *
 * The app keeps Android's default for its forms, which slides the focused field into
 * view. A thread cannot live with that: the header and the last messages slid off the
 * top, and the composer's own `imePadding` then left a gap the height of the keyboard
 * between the field and the keys. The thread screens call this; everything else is
 * untouched, and leaving the screen puts the default back.
 */
@Composable
expect fun ResizeForKeyboard()
