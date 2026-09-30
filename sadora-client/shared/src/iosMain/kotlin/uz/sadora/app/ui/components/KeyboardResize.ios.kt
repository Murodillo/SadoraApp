package uz.sadora.app.ui.components

import androidx.compose.runtime.Composable

/** iOS does not slide the page for the keyboard; `imePadding` alone places the composer. */
@Composable
actual fun ResizeForKeyboard() = Unit
